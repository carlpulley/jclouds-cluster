// Copyright (C) 2014  Carl Pulley
// 
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
// 
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
// 
// You should have received a copy of the GNU General Public License
// along with this program. If not, see <http://www.gnu.org/licenses/>.

package cakesolutions

package example

import akka.actor._
import akka.cluster.ClusterEvent.MemberExited
import akka.cluster.ClusterEvent.MemberUp
import akka.http.Http
import akka.http.model.ContentTypes._
import akka.http.model.HttpEntity.Chunk
import akka.http.model.HttpEntity.Chunked
import akka.http.model.HttpEntity.LastChunk
import akka.http.model.HttpMethods._
import akka.http.model.HttpRequest
import akka.http.model.HttpResponse
import akka.http.model.Uri
import akka.io.IO
import akka.pattern.ask
import akka.stream.actor.ActorSubscriber
import akka.stream.actor.ActorSubscriberMessage.OnNext
import akka.stream.actor.ActorPublisher
import akka.stream.actor.logging.{ ActorPublisher => LoggingActorPublisher }
import akka.stream.actor.WatermarkRequestStrategy
import akka.stream.FlowMaterializer
import akka.stream.MaterializerSettings
import akka.stream.scaladsl.Flow
import akka.util.ByteString
import ClusterMessages._
import scala.async.Async.async
import scala.async.Async.await
import scala.concurrent.duration._
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Random
import scala.util.Success

trait HttpClient 
  extends Configuration 
  with Serializer {
    
  this: Actor with ActorLogging =>

  import context.dispatcher

  val materializerSettings = MaterializerSettings(config.getConfig("client.materializer"))
  implicit val materializer = FlowMaterializer(materializerSettings)

  val host = config.getString("controller.host")
  val port = config.getInt("controller.port")

  def connection = 
    (IO(Http)(context.system) ? Http.Connect(host, port = port)).mapTo[Http.OutgoingConnection]

  def sendRequest(request: HttpRequest): Future[HttpResponse] = 
    connection.flatMap {
      case connect: Http.OutgoingConnection =>
        Flow(List(request -> 'NoContext))
          .produceTo(connect.processor)
        Flow(connect.processor)
          .map(_._1)
          .toFuture()
    }

  val httpConsumer =
    sendRequest(HttpRequest(GET, uri = Uri("/messages"))).map {
      case response @ HttpResponse(_, _, Chunked(_, chunks), _) =>
        log.info(s"Received: $response")
        Flow(chunks)
          .mapConcat {
            case Chunk(data, _) =>
              val msg = deserialize(data.toArray[Byte]).get
              log.info(s"Received via GET /messages: $msg")
              List(msg)

            case LastChunk(_, _) =>
              log.error("GET /messages closed")
              List.empty
          }
          .produceTo(ActorSubscriber(self))
    }

  val httpProducer =
    sendRequest(HttpRequest(PUT, uri = Uri("/messages"), entity = Chunked.fromData(`application/octet-stream`, ActorPublisher[ByteString](self))))

}

class ClientActor 
  extends ActorLogging 
  with ActorSubscriber
  with LoggingActorPublisher[ByteString]
  with HttpClient 
  with Configuration 
  with Serializer {

  import context.dispatcher

  override val requestStrategy = WatermarkRequestStrategy(config.getInt("client.watermark"))

  var addresses = Map.empty[Address, ActorSelection]

  val consumerSubscription: Cancellable = context.system.scheduler.schedule(0.seconds, config.getDuration("client.reconnect", SECONDS).seconds) {
    httpConsumer.onComplete {
      case Success(_) =>
        log.info("Successfully connected consumer to Controller")
        consumerSubscription.cancel()
        context.become(processingMessages orElse clusterMessages)

      case Failure(exn) =>
        log.error(s"Failed to connect consumer to Controller: $exn")
    }
  }

  val producerSubscription: Cancellable = context.system.scheduler.schedule(0.seconds, config.getDuration("client.reconnect", SECONDS).seconds) {
    httpProducer.onComplete {
      case Success(_) =>
        log.info("Successfully connected producer to Controller")
        producerSubscription.cancel()

      case Failure(exn) =>
        log.error(s"Failed to connect producer to Controller: $exn")
    }
  }

  override def postStop() = {
    consumerSubscription.cancel()
    producerSubscription.cancel()
  }

  def processingMessages: Receive = {
    case ping: Ping if (addresses.size > 0 && isActive && totalDemand > 0) =>
      log.info(s"Received: $ping - [Total Demand] ${Console.YELLOW}$totalDemand${Console.RESET}")
      val node = addresses.values.toList(Random.nextInt(addresses.size))
      onNext(ByteString(serialize((ping, node))))

    case ping: Ping if (addresses.size == 0) =>
      log.warning(s"No workers - ignoring: $ping")

    case ping: Ping =>
      log.warning(s"No demand - ignoring: $ping")

    case OnNext(ping: Ping) if (addresses.size > 0 && isActive && totalDemand > 0) =>
      log.info(s"[Internal] Received: $ping - [Total Demand] ${Console.YELLOW}$totalDemand${Console.RESET}")
      val node = addresses.values.toList(Random.nextInt(addresses.size))
      onNext(ByteString(serialize((ping, node))))

    case OnNext(ping: Ping) if (addresses.size == 0) =>
      log.warning(s"[Internal] No workers - ignoring: $ping")

    case OnNext(ping: Ping) =>
      log.warning(s"[Internal] No demand - ignoring: $ping")

    case OnNext(pong: Pong) =>
      log.info(s"${Console.RED}${pong.toString}${Console.RESET}")
  }

  def clusterMessages: Receive = {
    case OnNext(msg @ MemberUp(member)) if (member.roles.contains("worker")) =>
      log.info(s"Received: $msg with roles: ${member.roles} - [Total Demand] ${Console.YELLOW}$totalDemand${Console.RESET}")
      val node = member.address
      val act = context.actorSelection(RootActorPath(node) / "user" / "worker")
      addresses = addresses + (node -> act)

    case OnNext(msg @ MemberUp(member)) =>
      log.warning(s"Member has no worker role - ignoring: $msg with roles: ${member.roles}")

    case OnNext(msg @ MemberExited(member)) =>
      log.info(s"Received: $msg")
      addresses = addresses - member.address
  }

  // Until we successfully connect to the controller, we do nothing
  def receive = Actor.emptyBehavior
}

object ClientNode {

  var client: Option[ActorRef] = None
  var controller: Option[DeltacloudProvisioner] = None
  var machines = Map.empty[String, DeltacloudProvisioner]

}

trait ClientNode extends Configuration {

  import system.dispatcher
  import ClientNode._

  implicit val system = ActorSystem(config.getString("akka.system"))

  object Connection {
    def open(): Unit = {
      require(controller.nonEmpty && client.isEmpty)

      // Here we connect via the gateways external IP address (this is port forwarded to the controller's private or internal IP address)
      client = Some(system.actorOf(Props[ClientActor], "Client"))
    }

    def close(): Unit = {
      require(client.nonEmpty)

      client.get ! PoisonPill
      client = None
    }
  }

  val Provision = new provision.Common with provision.ControllerNode with provision.WorkerNode

  def shutdown: Future[Unit] = {
    async {
      await(shutdownWorkers)

      controller.map(_.shutdown.map {
        case _ =>
          controller = None
          system.shutdown
      })
    }
  }

  def shutdownWorkers: Future[Unit] = {
    async {
      await(Future.sequence(machines.values.map(_.shutdown)))

      machines = Map.empty[String, DeltacloudProvisioner]
    }
  }

}

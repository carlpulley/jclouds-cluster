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

import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Cancellable
import akka.actor.Props
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.CurrentClusterState
import akka.cluster.ClusterEvent.MemberExited
import akka.cluster.ClusterEvent.MemberUp
import akka.event.LoggingReceive
import akka.http.Http
import akka.http.model.ContentTypes._
import akka.http.model.HttpEntity
import akka.http.model.HttpEntity.Chunk
import akka.http.model.HttpEntity.Chunked
import akka.http.model.HttpEntity.ChunkStreamPart
import akka.http.model.HttpMethods._
import akka.http.model.HttpRequest
import akka.http.model.HttpResponse
import akka.http.model.StatusCodes
import akka.http.model.Uri
import akka.io.IO
import akka.kernel.Bootable
import akka.pattern.ask
import akka.stream.actor.ActorConsumer
import akka.stream.actor.ActorConsumer.OnNext
import akka.stream.actor.ActorProducer
import akka.stream.FlowMaterializer
import akka.stream.MaterializerSettings
import akka.stream.scaladsl.Flow
import ClusterMessages._
import scala.async.Async.async
import scala.async.Async.await
import scala.concurrent.duration._
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success

trait HttpServer extends Configuration with Serializer {
  this: ActorProducer[ChunkStreamPart] with ActorLogging =>

  import context.dispatcher

  val materializer = FlowMaterializer(MaterializerSettings())
  val host = Cluster(context.system).selfAddress.host.getOrElse("localhost")
  val port = config.getInt("controller.port")

  val bindingFuture = IO(Http)(context.system) ? Http.Bind(interface = host, port = port)

  val requestHandler: HttpRequest => Future[HttpResponse] = {
    case msg @ HttpRequest(PUT, Uri.Path("/ping"), hdrs, entity, _) if (hdrs.exists(_.name == "Worker")) =>
      log.info(s"Received: $msg")
      async {
        val path = hdrs.find(_.name == "Worker").get.value
        val data = await(entity.toStrict(timeout.duration, materializer)).data
        serialization.deserialize(data.toArray[Byte], classOf[Ping]) match {
          case Success(ping) =>
            context.actorSelection(path) ! OnNext(ping)
            HttpResponse()
          case Failure(exn) =>
            log.error(s"Failed to deserialize Ping message: $exn")
            HttpResponse(status = StatusCodes.UnprocessableEntity)
        }
      }

    case msg @ HttpRequest(GET, Uri.Path("/messages"), _, _, _) =>
      log.info(s"Received: $msg")
      Future {
        HttpResponse(entity = Chunked(`application/octet-stream`, ActorProducer[ChunkStreamPart](self)))
      }
  }

  bindingFuture foreach {
    case Http.ServerBinding(localAddress, connectionStream) =>
      Flow(connectionStream).foreach {
        case Http.IncomingConnection(_, requestProducer, responseConsumer) =>
          Flow(requestProducer).mapFuture(requestHandler).produceTo(materializer, responseConsumer)
      }.consume(materializer)
  }
}

class ControllerActor extends ActorLogging with ActorConsumer with ActorProducer[ChunkStreamPart] with HttpServer with Configuration with Serializer {
  override val requestStrategy = ActorConsumer.WatermarkRequestStrategy(config.getInt("client.watermark"))

  import context.dispatcher

  val cluster = Cluster(context.system)
  var membershipScheduler: Option[Cancellable] = None

  // Ensure that any lost cluster membership information (from back pressure controls) can be recovered
  def registerOnMemberUp {
    val updateDuration = config.getDuration("controller.update", SECONDS).seconds
    membershipScheduler = Some(context.system.scheduler.schedule(0.seconds, updateDuration) {
      cluster.sendCurrentClusterState(self)
    })
  }

  override def postStop() = {
    membershipScheduler.map(_.cancel)
    membershipScheduler = None
  }

  // Message instances get serialized and produced into the HTTP message chunking flow
  def processingMessages: Receive = LoggingReceive {
    case OnNext(msg: Message) =>
      log.info(s"Received: $msg")
      onNext(Chunk(serialize(msg)))
  }

  // Cluster events are only produced into HTTP message chunking flow if consumer demand allows
  def clusterMessages: Receive = LoggingReceive {
    case state: CurrentClusterState =>
      log.info(s"Received: $state")
      for (msg <- state.members) {
        self ! MemberUp(msg)
      }

    case msg: MemberUp if (isActive && totalDemand > 0) =>
      log.info(s"Received: $msg")
      onNext(Chunk(serialize(msg)))

    case msg: MemberUp =>
      log.warning(s"Ignoring: $msg")

    case msg: MemberExited if (isActive && totalDemand > 0) =>
      log.info(s"Received: $msg")
      onNext(Chunk(serialize(msg)))

    case msg: MemberExited =>
      log.warning(s"Ignoring: $msg")
  }

  def receive = 
    processingMessages orElse
      clusterMessages
}

class ControllerNode extends Bootable with Configuration {
  implicit val system = ActorSystem(config.getString("akka.system"))

  val cluster = Cluster(system)
  val joinAddress = cluster.selfAddress
  // We first ensure that we've joined our own cluster!
  cluster.join(joinAddress)

  var controller: Option[ActorRef] = None

  def startup = {
    controller = Some(system.actorOf(Props[ControllerActor]))
    // Ensure client is subscribed for member up and exited events (i.e. allow introduction and removal of worker nodes)
    cluster.subscribe(controller.get, classOf[MemberUp], classOf[MemberExited])
  }

  def shutdown = {
    controller = None
    system.shutdown
  }
}

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

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.ActorSelection
import akka.actor.ActorSystem
import akka.actor.Cancellable
import akka.actor.Props
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.CurrentClusterState
import akka.cluster.ClusterEvent.MemberExited
import akka.cluster.ClusterEvent.MemberUp
import akka.cluster.MemberStatus
import akka.http.Http
import akka.http.model.ContentTypes._
import akka.http.model.HttpEntity.Chunk
import akka.http.model.HttpEntity.Chunked
import akka.http.model.HttpEntity.ChunkStreamPart
import akka.http.model.HttpEntity.LastChunk
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
import akka.stream.scaladsl.Duct
import akka.stream.scaladsl.Flow
import ClusterMessages._
import org.reactivestreams.api.Consumer
import org.reactivestreams.api.Producer
import scala.async.Async.async
import scala.async.Async.await
import scala.concurrent.duration._
import scala.concurrent.Future

trait ControllerWorkflow 
  extends Configuration 
  with Serializer {
    
  this: Actor with ActorLogging =>

  import context.dispatcher

  def chunkedFlow: Duct[ChunkStreamPart, (Ping, ActorRef)] =
    Duct[ChunkStreamPart]
      .mapFuture {
        case Chunk(data, _) =>
          val (ping, target) = serialization.deserialize(data.toArray[Byte], classOf[(Ping, ActorSelection)]).get
          log.info(s"Received via PUT /messages: $ping destined for $target")
          async {
            val targetRef = await(target.resolveOne())

            Some((ping, targetRef))
          }

        case LastChunk(_, _) =>
          log.error("PUT /messages closed")
          Future { None }
      }
      .filter(_.nonEmpty)
      .map(_.get)

  val worker = context.actorOf(Props[WorkerController])

  def workerFlow: Consumer[(Ping, ActorRef)] =
    ActorConsumer(worker)

  def resultFlow: Producer[Message] =
    ActorProducer(worker)
}

trait HttpServer 
  extends ControllerWorkflow 
  with Configuration 
  with Serializer {

  this: Actor with ActorLogging =>

  import context.dispatcher

  val cluster = Cluster(context.system)
  val materializer = FlowMaterializer(
    MaterializerSettings(
      initialFanOutBufferSize = config.getInt("controller.materializer.initialFanOutBufferSize"),
      maxFanOutBufferSize     = config.getInt("controller.materializer.maxFanOutBufferSize"),
      initialInputBufferSize  = config.getInt("controller.materializer.initialInputBufferSize"),
      maximumInputBufferSize  = config.getInt("controller.materializer.maximumInputBufferSize")
    )
  )
  val host = Cluster(context.system).selfAddress.host.getOrElse("localhost")
  val port = config.getInt("controller.port")

  val bindingFuture = IO(Http)(context.system) ? Http.Bind(interface = host, port = port)

  def requestHandler(request: HttpRequest): HttpResponse = request match {
    case HttpRequest(PUT, Uri.Path("/messages"), _, Chunked(_, chunks), _) =>
      log.info(s"Received: $request")

      val (chunkConsumer, msgProducer) = chunkedFlow.build(materializer)

      Flow(chunks)
        .produceTo(materializer, chunkConsumer)
      Flow(msgProducer)
        .produceTo(materializer, workerFlow)
      Flow(resultFlow)
        .produceTo(materializer, ActorConsumer[Message](self))

      HttpResponse()

    case HttpRequest(GET, Uri.Path("/messages"), _, _, _) =>
      log.info(s"Received: $request")
      HttpResponse(entity = Chunked(`application/octet-stream`, ActorProducer[ChunkStreamPart](self)))

    case _ =>
      log.error(s"Unexpected request: $request")
      HttpResponse(status = StatusCodes.Forbidden)
  }

  bindingFuture foreach {
    case Http.ServerBinding(localAddress, connectionStream) =>
      Flow(connectionStream).foreach {
        case Http.IncomingConnection(_, requestProducer, responseConsumer) =>
          Flow(requestProducer)
            .map(requestHandler)
            .produceTo(materializer, responseConsumer)
      }.consume(materializer)
  }
}

class ControllerActor 
  extends ActorLogging 
  with ActorConsumer 
  with ActorProducer[ChunkStreamPart] 
  with HttpServer 
  with Configuration 
  with Serializer {

  import context.dispatcher

  override val requestStrategy = ActorConsumer.WatermarkRequestStrategy(config.getInt("controller.watermark"))

  var membershipScheduler: Option[Cancellable] = None

  // Ensure that any lost cluster membership information (from back pressure controls) can be recovered
  cluster registerOnMemberUp {
    val updateDuration = config.getDuration("controller.update", SECONDS).seconds
    membershipScheduler = Some(context.system.scheduler.schedule(0.seconds, updateDuration) {
      cluster.sendCurrentClusterState(self)
    })
  }

  override def postStop() = {
    membershipScheduler.map(_.cancel)
    membershipScheduler = None
  }

  def processingMessages: Receive = {
    case OnNext(msg: Message) if (isActive && totalDemand > 0) =>
      log.info(s"Received response: $msg")
      onNext(Chunk(serialize(msg)))

    case OnNext(msg: Message) if (!isActive || totalDemand == 0) =>
      log.warning(s"No demand - requeuing: $msg")
      self ! OnNext(msg)
  }

  // Cluster events are only produced into HTTP message chunking flow if consumer demand allows
  def clusterMessages: Receive = {
    case state: CurrentClusterState =>
      log.info(s"Received: $state")
      for (mem <- state.members.filter(_.status == MemberStatus.Up)) {
        self ! MemberUp(mem)
      }
      for (mem <- state.members.filter(m => List(MemberStatus.Exiting, MemberStatus.Removed).contains(m.status))) {
        self ! MemberExited(mem)
      }

    case msg: MemberUp if (isActive && totalDemand > 0) =>
      log.info(s"Received: $msg")
      onNext(Chunk(serialize(msg)))

    case msg: MemberUp =>
      log.warning(s"No demand - ignoring: $msg")

    case msg: MemberExited if (isActive && totalDemand > 0) =>
      log.info(s"Received: $msg")
      onNext(Chunk(serialize(msg)))

    case msg: MemberExited =>
      log.warning(s"No demand - ignoring: $msg")
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

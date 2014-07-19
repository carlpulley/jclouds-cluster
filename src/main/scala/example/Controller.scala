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

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.MemberExited
import akka.cluster.ClusterEvent.MemberUp
import akka.http.Http
import akka.http.model.ContentTypes._
import akka.http.model.HttpEntity
import akka.http.model.HttpEntity.Chunk
import akka.http.model.HttpEntity.Chunked
import akka.http.model.HttpEntity.ChunkStreamPart
import akka.http.model.HttpMethods._
import akka.http.model.HttpRequest
import akka.http.model.HttpResponse
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

trait HttpServer extends Configuration with Serializer {
  this: ActorProducer[ChunkStreamPart] =>

  import context.dispatcher

  val materializer = FlowMaterializer(MaterializerSettings())
  val host = Cluster(context.system).selfAddress.host.getOrElse("localhost")
  val port = config.getInt("controller.port")

  val bindingFuture = IO(Http)(context.system) ? Http.Bind(interface = host, port = port)

  val requestHandler: HttpRequest => HttpResponse = {
    case HttpRequest(PUT, Uri.Path("/ping"), hdrs, HttpEntity.Strict(_, data), _) if (hdrs.exists(_.name == "Worker")) =>
      val path = hdrs.find(_.name == "Worker").get.value
      context.actorSelection(path) ! OnNext(serialization.deserialize(data.toArray[Byte], classOf[Ping]))
      HttpResponse()

    case HttpRequest(GET, Uri.Path("/messages"), _, _, _) =>
      HttpResponse(entity = Chunked(`application/octet-stream`, ActorProducer[ChunkStreamPart](self)))
  }

  bindingFuture foreach {
    case Http.ServerBinding(localAddress, connectionStream) =>
      Flow(connectionStream).foreach {
        case Http.IncomingConnection(_, requestProducer, responseConsumer) =>
          Flow(requestProducer).map(requestHandler).produceTo(materializer, responseConsumer)
      }.consume(materializer)
  }
}

class ControllerActor extends ActorConsumer with ActorProducer[ChunkStreamPart] with HttpServer with Configuration with Serializer {
  override val requestStrategy = ActorConsumer.WatermarkRequestStrategy(config.getInt("client.watermark"))

  // Message instances get serialized and produced into the HTTP message chunking flow
  def processingMessages: Receive = {
    case OnNext(msg: Message) =>
      onNext(Chunk(serialize(msg)))
  }

  // Cluster events are only produced into HTTP message chunking flow if consumer demand allows
  def clusterMessages: Receive = {
    case msg: MemberUp if (isActive && totalDemand > 0) =>
      onNext(Chunk(serialize(msg)))

    case msg: MemberExited if (isActive && totalDemand > 0) =>
      onNext(Chunk(serialize(msg)))
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

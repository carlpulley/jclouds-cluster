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

package cakesolutions.example

package controller

import akka.actor._
import akka.cluster.ClusterEvent.MemberEvent
import akka.http.model.HttpEntity.{Chunk, ChunkStreamPart}
import akka.stream.MaterializerSettings
import akka.stream.scaladsl2._
import cakesolutions.example.ClusterMessages.{Message, Ping}
import scala.async.Async._

trait Workflows
  extends Configuration
  with Serializer {

  this: Actor with ActorLogging =>

  import FlowGraphImplicits._
  import context.dispatcher

  val materializerSettings = MaterializerSettings(config.getConfig("controller.materializer"))
  implicit val materializer = FlowMaterializer(materializerSettings)

  // We assume that messages can fit within Chunk instances
  def toChunk[T <: AnyRef](msg: T): ChunkStreamPart =
    Chunk(serialize(msg))

  // We assume that Chunk instances contain whole message instances
  def fromChunk(chunk: ChunkStreamPart) = {
    require(chunk.isInstanceOf[Chunk])

    val (ping, target) = serialization.deserialize(chunk.asInstanceOf[Chunk].data.toArray[Byte], classOf[(Ping, ActorSelection)]).get
    log.info(s"Received via PUT /messages: $ping destined for $target")
    async {
      val targetRef = await(target.resolveOne())
      (ping, targetRef)
    }
  }

  // The main controller workflow graph
  val clientIn  = SubscriberSource[ChunkStreamPart]
  val clientOut = PublisherSink[ChunkStreamPart]

  val clusterIn = SubscriberSource[MemberEvent]

  val workerIn  = SubscriberSource[Message]
  val workerOut = PublisherSink[(Ping, ActorRef)]

  val controllerRequest =
    FlowFrom[ChunkStreamPart]
      .filter(_.isInstanceOf[Chunk])
      .mapFuture(fromChunk)
      .withSource(clientIn)
      .withSink(workerOut)
      .run()

  val controllerResponse = FlowGraph { implicit b =>
    val mergeNode = Merge[ChunkStreamPart]

    workerIn  ~> FlowFrom[Message].map(toChunk)     ~> mergeNode ~> clientOut
    clusterIn ~> FlowFrom[MemberEvent].map(toChunk) ~> mergeNode
  }.run()

}

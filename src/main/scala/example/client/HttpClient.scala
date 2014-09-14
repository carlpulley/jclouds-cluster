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

package client

import akka.actor.{ActorLogging, Actor}
import akka.http.Http
import akka.http.model.ContentTypes._
import akka.http.model.HttpEntity.{Chunk, Chunked, ChunkStreamPart}
import akka.http.model.HttpMethods._
import akka.http.model.{Uri, HttpResponse, HttpRequest}
import akka.io.IO
import akka.pattern.ask
import akka.stream.actor.{ActorPublisher, ActorSubscriber}
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl2.FlowFrom
import akka.stream.scaladsl2.FlowMaterializer
import akka.stream.{ FlowMaterializer => OldFlowMaterializer, MaterializerSettings }
import akka.util.ByteString

import scala.concurrent.Future

trait HttpClient
  extends Configuration
  with Serializer {

  this: Actor with ActorLogging =>

  import context.dispatcher

  val materializerSettings = MaterializerSettings(config.getConfig("client.materializer"))
  implicit val oldMaterializer = OldFlowMaterializer(materializerSettings) // FIXME: a depreciated setting
  implicit val materializer = FlowMaterializer(materializerSettings)

  val host = config.getString("controller.host")
  val port = config.getInt("controller.port")

  def fromChunk(chunk: ChunkStreamPart) = {
    require(chunk.isInstanceOf[Chunk])

    val msg = deserialize(chunk.asInstanceOf[Chunk].data.toArray[Byte]).get
    log.info(s"Received via GET /messages: $msg")
    msg
  }

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
        FlowFrom(chunks)
          .filter(_.isInstanceOf[Chunk])
          .map(fromChunk)
          .publishTo(ActorSubscriber(self))
    }

  val httpProducer =
    sendRequest(HttpRequest(PUT, uri = Uri("/messages"), entity = Chunked.fromData(`application/octet-stream`, ActorPublisher[ByteString](self))))

}

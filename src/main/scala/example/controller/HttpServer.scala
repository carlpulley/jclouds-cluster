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

import akka.actor.{ActorLogging, Actor, Props}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.MemberEvent
import akka.http.Http
import akka.http.model.ContentTypes._
import akka.http.model.HttpEntity.Chunked
import akka.http.model.HttpMethods._
import akka.http.model.{StatusCodes, Uri, HttpResponse, HttpRequest}
import akka.io.IO
import akka.pattern.ask
import akka.stream.actor.{ActorPublisher, ActorSubscriber}
import akka.stream.scaladsl2.{ForeachSink, FlowFrom}

class HttpServer
  extends Workflows
  with Actor
  with ActorLogging
  with Configuration {

  import ClusterMessages._
  import context.dispatcher

  val cluster = Cluster(context.system)
  val host = cluster.selfAddress.host.getOrElse("localhost")
  val port = config.getInt("controller.port")

  // Wire the worker controller actor (a publisher/subscriber) into the controller workflow
  val worker = context.actorOf(Props[Worker])
  FlowFrom(workerOut.publisher(controllerRequest)).publishTo(ActorSubscriber(worker))
  FlowFrom(ActorPublisher[Message](worker)).publishTo(workerIn.subscriber(controllerResponse))

  // Wire the cluster listening actor (a publisher) into the controller workflow
  val notification = context.actorOf(Props[ClusterListener])
  FlowFrom(ActorPublisher[MemberEvent](notification)).publishTo(clusterIn.subscriber(controllerResponse))

  // Handles incoming HTTP requests and wires them into our workflow
  def requestHandler(request: HttpRequest): HttpResponse = request match {
    case HttpRequest(PUT, Uri.Path("/messages"), _, Chunked(_, chunks), _) =>
      log.info(s"Received: $request")
      FlowFrom(chunks).publishTo(clientIn.subscriber(controllerRequest))
      HttpResponse()

    case HttpRequest(GET, Uri.Path("/messages"), _, _, _) =>
      log.info(s"Received: $request")
      HttpResponse(entity = Chunked(`application/octet-stream`, clientOut.publisher(controllerResponse)))

    case _ =>
      log.error(s"Unexpected request: $request")
      HttpResponse(status = StatusCodes.Forbidden)
  }

  // Each HTTP request is handled (via the handler) and the HTTP response is returned (to the client)
  def responseSink(handler: HttpRequest => HttpResponse) =
    ForeachSink((connection: Http.IncomingConnection) => connection match {
      case Http.IncomingConnection(_, requestProducer, responseConsumer) =>
        FlowFrom(requestProducer)
          .map(handler)
          .publishTo(responseConsumer)
    })

  override def preStart() = {
    // HTTP server listens on TCP socket host:port and handles each (pipelined) HTTP connection
    (IO(Http)(context.system) ? Http.Bind(interface = host, port = port)) foreach {
      case Http.ServerBinding(localAddress, connectionStream) =>
        FlowFrom(connectionStream).withSink(responseSink(requestHandler)).run()
    }
  }

  def receive = Actor.emptyBehavior
}

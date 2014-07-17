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

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Address
import akka.http.Http
import akka.http.model
import akka.io.IO
import akka.pattern.ask
import akka.stream.FlowMaterializer
import akka.stream.MaterializerSettings
import akka.stream.scaladsl.Flow
import akka.util.Timeout
import cakesolutions.api.deltacloud
import cakesolutions.api.deltacloud.Instance
import cakesolutions.api.deltacloud.Realm
import com.typesafe.config.ConfigFactory
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.io.Source
import scala.util.Failure
import scala.util.Success
import spray.http._
import spray.client.pipelining._

// Until Spray and Akka Http are better integrated, we need the following shim code
object SprayHttpShim {
  def toModelHttpHeader(hdr: HttpHeader): model.HttpHeader = {
    model.headers.RawHeader(hdr.name, hdr.value)
  }

  def toSprayHttpHeader(hdr: model.HttpHeader): HttpHeader = {
    HttpHeaders.RawHeader(hdr.name, hdr.value)
  }

  def toSprayContentType(typ: model.ContentType): ContentType = typ match {
    case model.ContentTypes.NoContentType => ContentTypes.NoContentType
    case model.ContentType(model.MediaTypes.`application/xml`, _) => ContentType(MediaTypes.`application/xml`)
    case model.ContentType(model.MediaTypes.`text/html`, _) => ContentType(MediaTypes.`text/html`)
  }

  def toModelHttpProtocol(proto: HttpProtocol): model.HttpProtocol = proto match {
    case HttpProtocols.`HTTP/1.0` => model.HttpProtocols.`HTTP/1.0`
    case HttpProtocols.`HTTP/1.1` => model.HttpProtocols.`HTTP/1.1`
  }

  def toSprayHttpProtocol(proto: model.HttpProtocol): HttpProtocol = proto match {
    case model.HttpProtocols.`HTTP/1.0` => HttpProtocols.`HTTP/1.0`
    case model.HttpProtocols.`HTTP/1.1` => HttpProtocols.`HTTP/1.1`
  }

  def toModelHttpEntity(entity: HttpEntity): model.HttpEntity.Regular = {
    model.HttpEntity(entity.data.toByteArray)
  }

  // We only expect Strict or Default entities here
  def toSprayHttpEntity(entity: model.HttpEntity)(implicit timeout: FiniteDuration, materializer: FlowMaterializer, ec: ExecutionContext): Future[HttpEntity] = entity match {
    case entity: model.HttpEntity.Strict =>
      for (body <- entity.toStrict(timeout, materializer))
        yield HttpEntity(toSprayContentType(entity.contentType), body.data.toArray[Byte])

    case entity: model.HttpEntity.Default =>
      for (body <- entity.toStrict(timeout, materializer))
        yield HttpEntity(toSprayContentType(entity.contentType), body.data.toArray[Byte])

    case _ =>
      throw new RuntimeException(s"Unexpected entity type: $entity")
  }

  def toModelHttpRequest(request: HttpRequest): model.HttpRequest = {
    require(model.HttpMethods.getForKey(request.method.name).nonEmpty)

    model.HttpRequest(
      model.HttpMethods.getForKey(request.method.name).get, 
      model.Uri(request.uri.toString), 
      request.headers.map(toModelHttpHeader), 
      toModelHttpEntity(request.entity), 
      toModelHttpProtocol(request.protocol)
    )
  }

  def toSprayHttpResponse(response: model.HttpResponse)(implicit timeout: FiniteDuration, materializer: FlowMaterializer, ec: ExecutionContext): Future[HttpResponse] = {
    for (entity <- toSprayHttpEntity(response.entity)(timeout, materializer, ec))
      yield HttpResponse(
        response.status.intValue, 
        entity, 
        response.headers.map(toSprayHttpHeader).toList, 
        toSprayHttpProtocol(response.protocol)
      )
  }
}

class DeltacloudProvisioner(val label: String, joinAddress: Option[Address] = None)(implicit system: ActorSystem) {
  import SprayHttpShim._
  import system.dispatcher

  val config       = ConfigFactory.load()
  val host         = config.getString("deltacloud.host")
  val port         = config.getInt("deltacloud.port")
  val driver       = config.getString("deltacloud.driver")
  val materializer = FlowMaterializer(MaterializerSettings())

  implicit val timeout = Timeout(config.getInt("deltacloud.timeout").minutes)

  implicit val deltacloudHttpClient = (request: HttpRequest) =>
    (IO(Http) ? Http.Connect(host, port = port)).flatMap {
      case connect: Http.OutgoingConnection =>
        Flow(List(toModelHttpRequest(request) -> 'NoContext)).produceTo(materializer, connect.processor)
        Flow(connect.processor).map(_._1).toFuture(materializer)
    }.mapTo[model.HttpResponse].flatMap(toSprayHttpResponse(_)(timeout.duration, materializer, dispatcher))

  var node: Option[Instance] = None

  def bootstrap(user_data: String): Future[Unit] = {
    if (node.isEmpty) {
      for {
        realms <- Realm.index(state = Some("available"))
        vm <- Instance.create(
          image_id = config.getString(s"deltacloud.$driver.image"),
          keyname = Some(config.getString(s"deltacloud.$driver.keyname")),
          realm_id = Some(realms.head.id),
          hwp_id = Some(config.getString(s"deltacloud.$driver.hwp")),
          user_data = Some(user_data)
        )
      } yield {
        node = Some(vm)
      }
    } else {
      Future { () }
    }
  }

  // As we can't guarantee that the stored created instance (in node) has the current IP address 
  // information, this method allows a secondary lookup
  def private_addresses: Future[List[deltacloud.Address]] = {
    require(node.nonEmpty)

    Instance.show(node.get.id).map(vm => vm.private_addresses)
  }

  def shutdown: Future[Unit] = { 
    node match {
      case Some(n) =>
        for {
          _ <- Instance.stop(n.id)
          _ <- Instance.destroy(n.id)
        } yield {
          node = None
          ()
        }
  
      case None =>
        Future { () }
    }
  }
}

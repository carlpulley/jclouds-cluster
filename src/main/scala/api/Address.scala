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

package api

package deltacloud

import akka.http.model.ContentType
import akka.http.model.FormData
import akka.http.model.HttpEntity
import akka.http.model.HttpEntity.Strict
import akka.http.model.HttpMethods._
import akka.http.model.HttpRequest
import akka.http.model.HttpResponse
import akka.http.model.MediaTypes._
import akka.http.model.Uri
import akka.http.model.Uri.Query
import akka.stream.FlowMaterializer
import akka.util.ByteString
import akka.util.Timeout
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import xml.NodeSeq
import xml.XML

case class Address(
  ip: String,
  typ: String,
  instance_id: Option[String]
)

object Address {

  def xmlToAddress(data: NodeSeq): Address = {
    val ip = data.text
    val typ = (data \ "@type").text
    val instance_id = (data \ "instance").text
  
    Address(ip, typ, if (instance_id.isEmpty) None else Some(instance_id))
  }

  def strictToAddress(dataStr: Strict): Address = {
    val data = XML.loadString(dataStr.data.utf8String)
    xmlToAddress(data)
  }

  def strictToAddressList(dataStr: Strict): List[Address] = {
    val data = XML.loadString(dataStr.data.utf8String)
    (data \ "address").map(xmlToAddress).toList
  }

  def index(id: Option[String] = None)(implicit ec: ExecutionContext, pipeline: HttpRequest => Future[HttpResponse], timeout: Timeout, materializer: FlowMaterializer) = 
    pipeline(HttpRequest(GET, uri = Uri("/api/addresses").copy(query = Query(Map(
    "id" -> id
    ).flatMap(kv => kv._2.map(v => (kv._1 -> v))))))).flatMap(_.entity.toStrict(timeout.duration, materializer).map(strictToAddressList))

  def show(id: String)(implicit ec: ExecutionContext, pipeline: HttpRequest => Future[HttpResponse], timeout: Timeout, materializer: FlowMaterializer) = 
    pipeline(HttpRequest(GET, uri = Uri(s"/api/addresses/$id"))).flatMap(_.entity.toStrict(timeout.duration, materializer).map(strictToAddress))

  def create()(implicit ec: ExecutionContext, pipeline: HttpRequest => Future[HttpResponse], timeout: Timeout, materializer: FlowMaterializer) = 
    pipeline(HttpRequest(POST, uri = Uri("/api/addresses"))).flatMap(_.entity.toStrict(timeout.duration, materializer).map(strictToAddress))

  def destroy(id: String)(implicit pipeline: HttpRequest => Future[HttpResponse]) = 
    pipeline(HttpRequest(DELETE, uri = Uri(s"/api/addresses/$id")))

  def associate(id: String, instance_id: String)(implicit pipeline: HttpRequest => Future[HttpResponse]) = 
    pipeline(HttpRequest(POST, uri = Uri(s"/api/addresses/$id/associate"), entity = Strict(ContentType(`application/x-www-form-urlencoded`), ByteString(s"instance_id=${instance_id}"))))

  def disassociate(id: String)(implicit pipeline: HttpRequest => Future[HttpResponse]) = 
    pipeline(HttpRequest(POST, uri = Uri(s"/api/addresses/$id/disassociate")))

}

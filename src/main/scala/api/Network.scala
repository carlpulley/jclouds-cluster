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
import scala.xml.NodeSeq
import scala.xml.XML

case class Network(
  name: String,
  subnet_ids: List[String],
  address_blocks: List[String],
  state: String
)

object Network {

  def xmlToNetwork(data: NodeSeq): Network = {
    val name = (data \ "name").text
    val subnet_ids = (data \ "subnets" \ "subnet").map(n => (n \ "@id").text).toList
    val address_blocks = (data \ "address_blocks" \ "address_block").map(_.text).toList
    val state = (data \ "state").text
  
    Network(name, subnet_ids, address_blocks, state)
  }

  def strictToNetwork(dataStr: Strict): Network = {
    val data = XML.loadString(dataStr.data.utf8String)
    xmlToNetwork(data)
  }

  def strictToNetworkList(dataStr: Strict): List[Network] = {
    val data = XML.loadString(dataStr.data.utf8String)
    (data \ "network").map(xmlToNetwork).toList
  }

  def index()(implicit ec: ExecutionContext, pipeline: HttpRequest => Future[HttpResponse]) = 
    pipeline(HttpRequest(GET, uri = Uri("/api/networks")))

  def show(id: String)(implicit ec: ExecutionContext, pipeline: HttpRequest => Future[HttpResponse]) = 
    pipeline(HttpRequest(GET, uri = Uri(s"/api/networks/$id")))

  def create(
    address_block: Option[String] = None,
    name: Option[String] = None
  )(implicit ec: ExecutionContext, pipeline: HttpRequest => Future[HttpResponse], timeout: Timeout, materializer: FlowMaterializer) = 
    pipeline(HttpRequest(POST, uri = Uri("/api/networks"), entity = Strict(ContentType(`application/x-www-form-urlencoded`), ByteString(Map(
        "address_block" -> address_block,
        "name" -> name
    ).flatMap(kv => kv._2.map(v => (s"${kv._1}=${v}"))).mkString("&"))))).flatMap(_.entity.toStrict(timeout.duration, materializer).map(strictToNetwork))

  def destroy(id: String)(implicit pipeline: HttpRequest => Future[HttpResponse]) = 
    pipeline(HttpRequest(DELETE, uri = Uri(s"/api/networks/$id")))

}

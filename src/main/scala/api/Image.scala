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

case class Image(
  id: String,
  owner_id: String,
  architecture: String,
  state: String,
  hardware_profile_ids: List[String],
  root_type: String
)

object Image {

  def xmlToImage(data: NodeSeq): Image = {
    val id = (data \ "@id").text
    val owner_id = (data \ "owner_id").text
    val architecture = (data \ "architecture").text
    val state = (data \ "state").text
    val hardware_profile_ids = (data \ "hardware_profiles" \ "hardware_profile").map(n => (n \ "@id").text).toList
    val root_type = (data \ "root_type").text

    Image(id, owner_id, architecture, state, hardware_profile_ids, root_type)
  }

  def strictToImage(dataStr: Strict): Image = {
    val data = XML.loadString(dataStr.data.utf8String)
    xmlToImage(data)
  }

  def strictToImageList(dataStr: Strict): List[Image] = {
    val data = XML.loadString(dataStr.data.utf8String)
    (data \ "image").map(xmlToImage).toList
  }

  def index(
    id: Option[String] = None,
    owner_id: Option[String] = None,
    architecture: Option[String] = None
  )(implicit ec: ExecutionContext, pipeline: HttpRequest => Future[HttpResponse], timeout: Timeout, materializer: FlowMaterializer) = 
    pipeline(HttpRequest(GET, uri = Uri("/api/images").copy(query = Query(Map(
      "id" -> id,
      "architecture" -> architecture,
      "owner_id" -> owner_id
    ).flatMap(kv => kv._2.map(v => (kv._1 -> v))))))).flatMap(_.entity.toStrict(timeout.duration).map(strictToImageList).toFuture)

  def show(id: String)(implicit ec: ExecutionContext, pipeline: HttpRequest => Future[HttpResponse]) = 
    pipeline(HttpRequest(GET, uri = Uri(s"/api/images/$id")))

  def create(
    instance_id: String,
    name: Option[String] = None,
    description: Option[String] = None
  )(implicit ec: ExecutionContext, pipeline: HttpRequest => Future[HttpResponse], timeout: Timeout, materializer: FlowMaterializer) = 
    pipeline(HttpRequest(POST, uri = Uri("/api/images"), entity = Strict(ContentType(`application/x-www-form-urlencoded`), ByteString(Map(
        "instance_id" -> Some(instance_id), 
        "name" -> name, 
        "description" -> description
    ).flatMap(kv => kv._2.map(v => (s"${kv._1}=${v}"))).mkString("&"))))).flatMap(_.entity.toStrict(timeout.duration).map(strictToImage).toFuture)

  def destroy(id: String)(implicit pipeline: HttpRequest => Future[HttpResponse]) = 
    pipeline(HttpRequest(DELETE, uri = Uri(s"/api/images/$id")))

}

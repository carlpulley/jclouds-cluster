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

case class Capacity(
  unit: String,
  size: String
)

case class StorageVolume(
  id: String,
  capacity: Capacity,
  realm_id: String,
  state: String
)

object StorageVolume {

  def xmlToCapacity(data: NodeSeq): Capacity = {
    val unit = (data \ "@unit").text
    val size = data.text

    Capacity(unit, size)
  }

  def xmlToStorageVolume(data: NodeSeq): StorageVolume = {
    val id = (data \ "@id").text
    val capacity = xmlToCapacity(data \ "capacity")
    val realm_id = (data \ "realm_id").text
    val state = (data \ "state").text
  
    StorageVolume(id, capacity, realm_id, state)
  }

  def strictToStorageVolume(dataStr: Strict): StorageVolume = {
    val data = XML.loadString(dataStr.data.utf8String)
    xmlToStorageVolume(data)
  }

  def strictToStorageVolumeList(dataStr: Strict): List[StorageVolume] = {
    val data = XML.loadString(dataStr.data.utf8String)
    (data \ "storage_volume").map(xmlToStorageVolume).toList
  }

  def show(id: String)(implicit ec: ExecutionContext, pipeline: HttpRequest => Future[HttpResponse]) = 
    pipeline(HttpRequest(GET, uri = Uri(s"/api/storage_volumes/$id")))

  def index(
    id: Option[String] = None, 
    state: Option[String] = None
  )(implicit ec: ExecutionContext, pipeline: HttpRequest => Future[HttpResponse], timeout: Timeout, materializer: FlowMaterializer) = 
    pipeline(HttpRequest(GET, uri = Uri("/api/storage_volumes").copy(query = Query(Map(
      "id" -> id,
      "state" -> state
    ).flatMap(kv => kv._2.map(v => (kv._1 -> v))))))).flatMap(_.entity.toStrict(timeout.duration, materializer).map(strictToStorageVolumeList))

  def create(
    snapshot_id: Option[String] = None,
    capacity: Option[String] = None,
    realm_id: Option[String] = None,
    name: Option[String] = None,
    description: Option[String] = None
  )(implicit ec: ExecutionContext, pipeline: HttpRequest => Future[HttpResponse], timeout: Timeout, materializer: FlowMaterializer) = 
    pipeline(HttpRequest(POST, uri = Uri("/api/storage_volumes"), entity = Strict(ContentType(`application/x-www-form-urlencoded`), ByteString(Map(
        "snapshot_id" -> snapshot_id, 
        "capacity" -> capacity, 
        "realm_id" -> realm_id, 
        "name" -> name, 
        "description" -> description
    ).flatMap(kv => kv._2.map(v => (s"${kv._1}=${v}"))).mkString("&"))))).flatMap(_.entity.toStrict(timeout.duration, materializer).map(strictToStorageVolume))

  def destroy(id: String)(implicit pipeline: HttpRequest => Future[HttpResponse]) = 
    pipeline(HttpRequest(DELETE, uri = Uri(s"/api/storage_volumes/$id")))

  def attach(
    id: String,
    instance_id: String,
    device: Option[String] = None
  )(implicit ec: ExecutionContext, pipeline: HttpRequest => Future[HttpResponse], timeout: Timeout, materializer: FlowMaterializer) = 
    pipeline(HttpRequest(POST, uri = Uri(s"/api/storage_volumes/$id/attach"), entity = Strict(ContentType(`application/x-www-form-urlencoded`), ByteString(Map(
        "instance_id" -> Some(instance_id), 
        "device" -> device
    ).flatMap(kv => kv._2.map(v => (s"${kv._1}=${v}"))).mkString("&"))))).flatMap(_.entity.toStrict(timeout.duration, materializer).map(strictToStorageVolume))

  def detach(id: String)(implicit pipeline: HttpRequest => Future[HttpResponse]) = 
    pipeline(HttpRequest(POST, uri = Uri(s"/api/storage_volumes/$id/detach")))

}

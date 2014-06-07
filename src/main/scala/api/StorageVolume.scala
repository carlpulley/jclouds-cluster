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

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import spray.http._
import spray.http.MediaTypes._
import spray.http.Uri.Query
import spray.httpx.TransformerAux.aux2
import spray.httpx.unmarshalling._
import spray.client.pipelining._
import xml.NodeSeq

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

  implicit val unmarshalStorageVolume = 
    Unmarshaller.delegate[NodeSeq, StorageVolume](`text/xml`, `application/xml`, `text/html`, `application/xhtml+xml`)(xmlToStorageVolume)

  implicit val unmarshalStorageVolumes = 
    Unmarshaller.delegate[NodeSeq, List[StorageVolume]](`text/xml`, `application/xml`, `text/html`, `application/xhtml+xml`) { data => 
      (data \ "storage_volume").map(xmlToStorageVolume).toList
    }

  def show(id: String)(implicit ec: ExecutionContext, pipeline: HttpRequest => Future[HttpResponse]) = 
    (pipeline ~> unmarshal[StorageVolume])(aux2)(Get(s"/api/storage_volumes/$id"))

  def index(
    id: Option[String] = None, 
    state: Option[String] = None
  )(implicit ec: ExecutionContext, pipeline: HttpRequest => Future[HttpResponse]) = 
    (pipeline ~> unmarshal[List[StorageVolume]])(aux2)(Get(Uri("/api/storage_volumes").copy(query = Query(Map(
      "id" -> id,
      "state" -> state
    ).flatMap(kv => kv._2.map(v => (kv._1 -> v)))))))

  def create(
    snapshot_id: Option[String] = None,
    capacity: Option[String] = None,
    realm_id: Option[String] = None,
    name: Option[String] = None,
    description: Option[String] = None
  )(implicit ec: ExecutionContext, pipeline: HttpRequest => Future[HttpResponse]) = 
    (pipeline ~> unmarshal[StorageVolume])(aux2)(Post("/api/storage_volumes", FormData(Seq(
        "snapshot_id" -> snapshot_id, 
        "capacity" -> capacity, 
        "realm_id" -> realm_id, 
        "name" -> name, 
        "description" -> description
    ).flatMap(kv => kv._2.map(v => (kv._1 -> v))))))

  def destroy(id: String)(implicit pipeline: HttpRequest => Future[HttpResponse]) = 
    pipeline(Delete(s"/api/storage_volumes/$id"))

  def attach(
    id: String,
    instance_id: String,
    device: Option[String] = None
  )(implicit pipeline: HttpRequest => Future[HttpResponse]) = 
    pipeline(Post(s"/api/storage_volumes/$id/attach", FormData(Seq(
        "instance_id" -> Some(instance_id), 
        "device" -> device
    ).flatMap(kv => kv._2.map(v => (kv._1 -> v))))))

  def detach(id: String)(implicit pipeline: HttpRequest => Future[HttpResponse]) = 
    pipeline(Post(s"/api/storage_volumes/$id/detach"))

}

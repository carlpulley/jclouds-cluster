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

case class StorageSnapshot(
  id: String,
  storage_volume: String
)

object StorageSnapshot {

  def xmlToStorageSnapshot(data: NodeSeq): StorageSnapshot = {
    val id = (data \ "@id").text
    val storage_volume = (data \ "storage_volume" \ "@id").text
  
    StorageSnapshot(id, storage_volume)
  }

  def strictToStorageSnapshot(dataStr: Strict): StorageSnapshot = {
    val data = XML.loadString(dataStr.data.utf8String)
    xmlToStorageSnapshot(data)
  }

  def strictToStorageSnapshotList(dataStr: Strict): List[StorageSnapshot] = {
    val data = XML.loadString(dataStr.data.utf8String)
    (data \ "storage_snapshot").map(xmlToStorageSnapshot).toList
  }

  def show(id: String)(implicit ec: ExecutionContext, pipeline: HttpRequest => Future[HttpResponse]) = 
    pipeline(HttpRequest(GET, uri = Uri(s"/api/storage_snapshots/$id")))

  def index(id: Option[String] = None)(implicit ec: ExecutionContext, pipeline: HttpRequest => Future[HttpResponse], timeout: Timeout, materializer: FlowMaterializer) = 
    pipeline(HttpRequest(GET, uri = Uri("/api/storage_snapshots").copy(query = Query(Map(
      "id" -> id
    ).flatMap(kv => kv._2.map(v => (kv._1 -> v))))))).flatMap(_.entity.toStrict(timeout.duration, materializer).map(strictToStorageSnapshotList))

  def create(
    volume_id: String,
    name: Option[String] = None,
    description: Option[String] = None
  )(implicit ec: ExecutionContext, pipeline: HttpRequest => Future[HttpResponse], timeout: Timeout, materializer: FlowMaterializer) = 
    pipeline(HttpRequest(POST, uri = Uri("/api/storage_snapshots"), entity = Strict(ContentType(`application/x-www-form-urlencoded`), ByteString(Map(
        "volume_id" -> Some(volume_id), 
        "name" -> name, 
        "description" -> description
    ).flatMap(kv => kv._2.map(v => (s"${kv._1}=${v}"))).mkString("&"))))).flatMap(_.entity.toStrict(timeout.duration, materializer).map(strictToStorageSnapshot))

  def destroy(id: String)(implicit pipeline: HttpRequest => Future[HttpResponse]) = 
    pipeline(HttpRequest(DELETE, uri = Uri(s"/api/storage_snapshots/$id")))

}

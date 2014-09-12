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

case class Bucket(
  id: String,
  size: Option[String],
  blob_ids: List[String]
)

object Bucket {

  def xmlToBucket(data: NodeSeq): Bucket = {
    val id = (data \ "@id").text
    val size = (data \ "size").text
    val blob_ids = (data \ "blob").map(n => (n \ "@id").text).toList
  
    Bucket(id, if (size.isEmpty) None else Some(size), blob_ids)
  }

  def strictToBucket(dataStr: Strict): Bucket = {
    val data = XML.loadString(dataStr.data.utf8String)
    xmlToBucket(data)
  }

  def strictToBucketList(dataStr: Strict): List[Bucket] = {
    val data = XML.loadString(dataStr.data.utf8String)
    (data \ "bucket").map(xmlToBucket).toList
  }

  def show(id: String)(implicit ec: ExecutionContext, pipeline: HttpRequest => Future[HttpResponse], timeout: Timeout, materializer: FlowMaterializer): Future[Bucket] = 
    pipeline(HttpRequest(GET, uri = Uri(s"/api/buckets/$id"))).flatMap(_.entity.toStrict(timeout.duration).map(strictToBucket).toFuture)

  def index(id: Option[String] = None)(implicit ec: ExecutionContext, pipeline: HttpRequest => Future[HttpResponse], timeout: Timeout, materializer: FlowMaterializer) = 
    pipeline(HttpRequest(GET, uri = Uri("/api/buckets").copy(query = Query(Map(
      "id" -> id
    ).flatMap(kv => kv._2.map(v => (kv._1 -> v))))))).flatMap(_.entity.toStrict(timeout.duration).map(strictToBucketList).toFuture)

  def create(name: String)(implicit ec: ExecutionContext, pipeline: HttpRequest => Future[HttpResponse], timeout: Timeout, materializer: FlowMaterializer) = 
    pipeline(HttpRequest(POST, uri = Uri("/api/buckets"), entity = Strict(ContentType(`application/x-www-form-urlencoded`), ByteString(Map("name" -> name).flatMap(kv => kv._2.map(v => (s"${kv._1}=${v}"))).mkString("&"))))).flatMap(_.entity.toStrict(timeout.duration).map(strictToBucket).toFuture)

  def destroy(id: String)(implicit pipeline: HttpRequest => Future[HttpResponse]) = 
    pipeline(HttpRequest(DELETE, uri = Uri(s"/api/buckets/$id")))

}
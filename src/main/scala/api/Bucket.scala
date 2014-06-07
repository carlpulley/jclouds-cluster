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

  implicit val unmarshalBucket = 
    Unmarshaller.delegate[NodeSeq, Bucket](`text/xml`, `application/xml`, `text/html`, `application/xhtml+xml`)(xmlToBucket)

  implicit val unmarshalBuckets = 
    Unmarshaller.delegate[NodeSeq, List[Bucket]](`text/xml`, `application/xml`, `text/html`, `application/xhtml+xml`) { data => 
      (data \ "bucket").map(xmlToBucket(_)).toList
    }

  def show(id: String)(implicit ec: ExecutionContext, pipeline: HttpRequest => Future[HttpResponse]): Future[Bucket] = 
    (pipeline ~> unmarshal[Bucket])(aux2)(Get(s"/api/buckets/$id"))

  def index(id: Option[String] = None)(implicit ec: ExecutionContext, pipeline: HttpRequest => Future[HttpResponse]) = 
    (pipeline ~> unmarshal[List[Bucket]])(aux2)(Get(Uri("/api/buckets").copy(query = Query(Map(
      "id" -> id
    ).flatMap(kv => kv._2.map(v => (kv._1 -> v)))))))

  def create(name: String)(implicit ec: ExecutionContext, pipeline: HttpRequest => Future[HttpResponse]) = 
    (pipeline ~> unmarshal[Bucket])(aux2)(Post("/api/buckets", FormData(Map("name" -> name))))

  def destroy(id: String)(implicit pipeline: HttpRequest => Future[HttpResponse]) = 
    pipeline(Delete(s"/api/buckets/$id"))

}
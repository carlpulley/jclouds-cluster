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

  implicit val unmarshalImage = 
    Unmarshaller.delegate[NodeSeq, Image](`text/xml`, `application/xml`, `text/html`, `application/xhtml+xml`)(xmlToImage)

  implicit val unmarshalImages = 
    Unmarshaller.delegate[NodeSeq, List[Image]](`text/xml`, `application/xml`, `text/html`, `application/xhtml+xml`) { data => 
      (data \ "image").map(xmlToImage(_)).toList
    }

  def index(
    id: Option[String] = None,
    owner_id: Option[String] = None,
    architecture: Option[String] = None
  )(implicit ec: ExecutionContext, pipeline: HttpRequest => Future[HttpResponse]) = 
    (pipeline ~> unmarshal[List[Image]])(aux2)(Get(Uri("/api/images").copy(query = Query(Map(
      "id" -> id,
      "architecture" -> architecture,
      "owner_id" -> owner_id
    ).flatMap(kv => kv._2.map(v => (kv._1 -> v)))))))

  def show(id: String)(implicit ec: ExecutionContext, pipeline: HttpRequest => Future[HttpResponse]) = 
    (pipeline ~> unmarshal[Image])(aux2)(Get(s"/api/images/$id"))

  def create(
    instance_id: String,
    name: Option[String] = None,
    description: Option[String] = None
  )(implicit ec: ExecutionContext, pipeline: HttpRequest => Future[HttpResponse]) = 
    (pipeline ~> unmarshal[Image])(aux2)(Post("/api/images", FormData(Seq(
        "instance_id" -> Some(instance_id), 
        "name" -> name, 
        "description" -> description
    ).flatMap(kv => kv._2.map(v => (kv._1 -> v))))))

  def destroy(id: String)(implicit pipeline: HttpRequest => Future[HttpResponse]) = 
    pipeline(Delete(s"/api/images/$id"))

}
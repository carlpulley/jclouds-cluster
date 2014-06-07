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

case class Key(
  id: String,
  fingerprint: String,
  state: String
)

object Key {

  def xmlToKey(data: NodeSeq): Key = {
    val id = (data \ "@id").text
    val fingerprint = (data \ "fingerprint").text
    val state = (data \ "state").text
  
    Key(id, fingerprint, state)
  }

  implicit val unmarshalKey = 
    Unmarshaller.delegate[NodeSeq, Key](`text/xml`, `application/xml`, `text/html`, `application/xhtml+xml`)(xmlToKey)

  implicit val unmarshalKeys = 
    Unmarshaller.delegate[NodeSeq, List[Key]](`text/xml`, `application/xml`, `text/html`, `application/xhtml+xml`) { data => 
      (data \ "key").map(xmlToKey(_)).toList
    }

  def index(id: Option[String] = None)(implicit ec: ExecutionContext, pipeline: HttpRequest => Future[HttpResponse]) = 
    (pipeline ~> unmarshal[List[Key]])(aux2)(Get(Uri("/api/keys").copy(query = Query(Map(
      "id" -> id
    ).flatMap(kv => kv._2.map(v => (kv._1 -> v)))))))

  def show(id: String)(implicit ec: ExecutionContext, pipeline: HttpRequest => Future[HttpResponse]) = 
    (pipeline ~> unmarshal[Key])(aux2)(Get(s"/api/keys/$id"))

  def create(
    name: String,
    public_key: Option[String] = None
  )(implicit ec: ExecutionContext, pipeline: HttpRequest => Future[HttpResponse]) = 
    (pipeline ~> unmarshal[Key])(aux2)(Post("/api/keys", FormData(Seq(
        "name" -> Some(name), 
        "public_key" -> public_key
    ).flatMap(kv => kv._2.map(v => (kv._1 -> v))))))

  def destroy(id: String)(implicit pipeline: HttpRequest => Future[HttpResponse]) = 
    pipeline(Delete(s"/api/keys/$id"))

}

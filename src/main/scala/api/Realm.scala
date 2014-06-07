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

case class Realm(
  id: String,
  state: String
)

object Realm {

  def xmlToRealm(data: NodeSeq): Realm = {
    val id = (data \ "@id").text
    val state = (data \ "state").text
  
    Realm(id, state)
  }

  implicit val unmarshalRealm = 
    Unmarshaller.delegate[NodeSeq, Realm](`text/xml`, `application/xml`, `text/html`, `application/xhtml+xml`)(xmlToRealm)

  implicit val unmarshalRealms = 
    Unmarshaller.delegate[NodeSeq, List[Realm]](`text/xml`, `application/xml`, `text/html`, `application/xhtml+xml`) { data => 
      (data \ "realm").map(xmlToRealm).toList
    }

  def index(
    id: Option[String] = None, 
    state: Option[String] = None
  )(implicit ec: ExecutionContext, pipeline: HttpRequest => Future[HttpResponse]) = 
    (pipeline ~> unmarshal[List[Realm]])(aux2)(Get(Uri("/api/realms").copy(query = Query(Map(
      "id" -> id,
      "state" -> state
    ).flatMap(kv => kv._2.map(v => (kv._1 -> v)))))))

  def show(id: String)(implicit ec: ExecutionContext, pipeline: HttpRequest => Future[HttpResponse]) = 
    (pipeline ~> unmarshal[Realm])(aux2)(Get(s"/api/realms/$id"))

}

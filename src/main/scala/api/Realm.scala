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
import xml.NodeSeq
import xml.XML

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

  def strictToRealm(dataStr: Strict): Realm = {
    val data = XML.loadString(dataStr.data.utf8String)
    xmlToRealm(data)
  }

  def strictToRealmList(dataStr: Strict): List[Realm] = {
    val data = XML.loadString(dataStr.data.utf8String)
    (data \ "realm").map(xmlToRealm).toList
  }

  def index(
    id: Option[String] = None, 
    state: Option[String] = None
  )(implicit ec: ExecutionContext, pipeline: HttpRequest => Future[HttpResponse], timeout: Timeout, materializer: FlowMaterializer) = 
    pipeline(HttpRequest(GET, uri = Uri("/api/realms").copy(query = Query(Map(
      "id" -> id,
      "state" -> state
    ).flatMap(kv => kv._2.map(v => (kv._1 -> v))))))).flatMap(_.entity.toStrict(timeout.duration, materializer).map(strictToRealmList))

  def show(id: String)(implicit ec: ExecutionContext, pipeline: HttpRequest => Future[HttpResponse], timeout: Timeout, materializer: FlowMaterializer) = 
    pipeline(HttpRequest(GET, uri = Uri(s"/api/realms/$id"))).flatMap(_.entity.toStrict(timeout.duration, materializer).map(strictToRealm))

}

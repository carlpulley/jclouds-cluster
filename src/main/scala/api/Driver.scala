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

case class Driver(
  id: String,
  providers: List[String]
)

object Driver {

  def xmlToDriver(data: NodeSeq): Driver = {
    val id = (data \ "@id").text
    val providers = (data \ "provider").map(n => (n \ "@id").text).toList
  
    Driver(id, providers)
  }

  def strictToDriver(dataStr: Strict): Driver = {
    val data = XML.loadString(dataStr.data.utf8String)
    xmlToDriver(data)
  }

  def strictToDriverList(dataStr: Strict): List[Driver] = {
    val data = XML.loadString(dataStr.data.utf8String)
    (data \ "driver").map(xmlToDriver).toList
  }

  def show(id: String)(implicit ec: ExecutionContext, pipeline: HttpRequest => Future[HttpResponse]) = 
    pipeline(HttpRequest(GET, uri = Uri(s"/api/drivers/$id")))

  def index(
    id: Option[String] = None, 
    state: Option[String] = None
  )(implicit ec: ExecutionContext, pipeline: HttpRequest => Future[HttpResponse], timeout: Timeout, materializer: FlowMaterializer) = 
    pipeline(HttpRequest(GET, uri = Uri("/api/drivers").copy(query = Query(Map(
      "id" -> id,
      "state" -> state
    ).flatMap(kv => kv._2.map(v => (kv._1 -> v))))))).flatMap(_.entity.toStrict(timeout.duration).map(strictToDriverList).toFuture)

}
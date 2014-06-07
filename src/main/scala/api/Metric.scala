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

case class Metric(
  entity: String,
  properties: List[Property]
)

object Metric {
  import Property.xmlToProperty

  def xmlToMetric(data: NodeSeq): Metric = {
    val entity = (data \ "entity").text
    val properties = (data \ "properties" \ "property").map(xmlToProperty).toList
  
    Metric(entity, properties)
  }

  implicit val unmarshalMetric = 
    Unmarshaller.delegate[NodeSeq, Metric](`text/xml`, `application/xml`, `text/html`, `application/xhtml+xml`)(xmlToMetric)

  implicit val unmarshalMetrics = 
    Unmarshaller.delegate[NodeSeq, List[Metric]](`text/xml`, `application/xml`, `text/html`, `application/xhtml+xml`) { data => 
      (data \ "metric").map(xmlToMetric).toList
    }

  def index(id: Option[String] = None)(implicit ec: ExecutionContext, pipeline: HttpRequest => Future[HttpResponse]) = 
    (pipeline ~> unmarshal[List[Metric]])(aux2)(Get(Uri("/api/metrics").copy(query = Query(Map(
      "id" -> id
    ).flatMap(kv => kv._2.map(v => (kv._1 -> v)))))))

  def show(id: String)(implicit ec: ExecutionContext, pipeline: HttpRequest => Future[HttpResponse]) = 
    (pipeline ~> unmarshal[Metric])(aux2)(Get(s"/api/metrics/$id"))

}

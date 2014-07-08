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

case class Address(
  ip: String,
  typ: String,
  instance_id: Option[String]
)

object Address {

  def xmlToAddress(data: NodeSeq): Address = {
    val ip = data.text
    val typ = (data \ "@type").text
    val instance_id = (data \ "instance").text
  
    Address(ip, typ, if (instance_id.isEmpty) None else Some(instance_id))
  }

  implicit val unmarshalAddress = 
    Unmarshaller.delegate[NodeSeq, Address](`text/xml`, `application/xml`, `text/html`, `application/xhtml+xml`)(xmlToAddress)

  implicit val unmarshalAddresses = 
    Unmarshaller.delegate[NodeSeq, List[Address]](`text/xml`, `application/xml`, `text/html`, `application/xhtml+xml`) { data => 
      (data \ "address").map(xmlToAddress).toList
    }

  def index(id: Option[String] = None)(implicit ec: ExecutionContext, pipeline: HttpRequest => Future[HttpResponse]) = 
    (pipeline ~> unmarshal[List[Address]])(aux2)(Get(Uri("/api/addresses").copy(query = Query(Map(
    "id" -> id
    ).flatMap(kv => kv._2.map(v => (kv._1 -> v)))))))

  def show(id: String)(implicit ec: ExecutionContext, pipeline: HttpRequest => Future[HttpResponse]) = 
    (pipeline ~> unmarshal[Address])(aux2)(Get(s"/api/addresses/$id"))

  def create()(implicit ec: ExecutionContext, pipeline: HttpRequest => Future[HttpResponse]) = 
    (pipeline ~> unmarshal[Address])(aux2)(Post("/api/addresses"))

  def destroy(id: String)(implicit pipeline: HttpRequest => Future[HttpResponse]) = 
    pipeline(Delete(s"/api/addresses/$id"))

  def associate(id: String, instance_id: String)(implicit pipeline: HttpRequest => Future[HttpResponse]) = 
    pipeline(Post(s"/api/addresses/$id/associate", FormData(Map("instance_id" -> instance_id))))

  def disassociate(id: String)(implicit pipeline: HttpRequest => Future[HttpResponse]) = 
    pipeline(Post(s"/api/addresses/$id/disassociate"))

}

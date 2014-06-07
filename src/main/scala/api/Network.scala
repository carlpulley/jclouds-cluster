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

case class Network(
  name: String,
  subnet_ids: List[String],
  address_blocks: List[String],
  state: String
)

object Network {

  def xmlToNetwork(data: NodeSeq): Network = {
    val name = (data \ "name").text
    val subnet_ids = (data \ "subnets" \ "subnet").map(n => (n \ "@id").text).toList
    val address_blocks = (data \ "address_blocks" \ "address_block").map(_.text).toList
    val state = (data \ "state").text
  
    Network(name, subnet_ids, address_blocks, state)
  }

  implicit val unmarshalNetwork = 
    Unmarshaller.delegate[NodeSeq, Network](`text/xml`, `application/xml`, `text/html`, `application/xhtml+xml`)(xmlToNetwork)

  implicit val unmarshalNetworks = 
    Unmarshaller.delegate[NodeSeq, List[Network]](`text/xml`, `application/xml`, `text/html`, `application/xhtml+xml`) { data => 
      (data \ "network").map(xmlToNetwork).toList
    }

  def index()(implicit ec: ExecutionContext, pipeline: HttpRequest => Future[HttpResponse]) = 
    (pipeline ~> unmarshal[List[Network]])(aux2)(Get(Uri("/api/networks")))

  def show(id: String)(implicit ec: ExecutionContext, pipeline: HttpRequest => Future[HttpResponse]) = 
    (pipeline ~> unmarshal[Network])(aux2)(Get(s"/api/networks/$id"))

  def create(
    address_block: Option[String] = None,
    name: Option[String] = None
  )(implicit ec: ExecutionContext, pipeline: HttpRequest => Future[HttpResponse]) = 
    (pipeline ~> unmarshal[Network])(aux2)(Post("/api/networks", FormData(Seq(
        "address_block" -> address_block,
        "name" -> name
    ).flatMap(kv => kv._2.map(v => (kv._1 -> v))))))

  def destroy(id: String)(implicit pipeline: HttpRequest => Future[HttpResponse]) = 
    pipeline(Delete(s"/api/networks/$id"))

}

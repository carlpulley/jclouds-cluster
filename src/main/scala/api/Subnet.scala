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

case class Subnet(
  name: String,
  network_id: String,
  address_block: String,
  state: String,
  typ: String
)

object Subnet {

  def xmlToSubnet(data: NodeSeq): Subnet = {
    val name = (data \ "name").text
    val network_id = (data \ "network" \ "@id").text
    val address_block = (data \ "address_block").text
    val state = (data \ "state").text
    val typ = (data \ "type").text
  
    Subnet(name, network_id, address_block, state, typ)
  }

  implicit val unmarshalSubnet = 
    Unmarshaller.delegate[NodeSeq, Subnet](`text/xml`, `application/xml`, `text/html`, `application/xhtml+xml`)(xmlToSubnet)

  implicit val unmarshalSubnets = 
    Unmarshaller.delegate[NodeSeq, List[Subnet]](`text/xml`, `application/xml`, `text/html`, `application/xhtml+xml`) { data => 
      (data \ "subnet").map(xmlToSubnet).toList
    }

  def index()(implicit ec: ExecutionContext, pipeline: HttpRequest => Future[HttpResponse]) = 
    (pipeline ~> unmarshal[List[Subnet]])(aux2)(Get(Uri("/api/subnets")))

  def show(id: String)(implicit ec: ExecutionContext, pipeline: HttpRequest => Future[HttpResponse]) = 
    (pipeline ~> unmarshal[Subnet])(aux2)(Get(s"/api/subnets/$id"))

  def create(
    network_id: String,
    address_block: String,
    name: Option[String] = None
  )(implicit ec: ExecutionContext, pipeline: HttpRequest => Future[HttpResponse]) = 
    (pipeline ~> unmarshal[Subnet])(aux2)(Post("/api/subnets", FormData(Seq(
        "network_id" -> Some(network_id), 
        "address_block" -> Some(address_block), 
        "name" -> name
    ).flatMap(kv => kv._2.map(v => (kv._1 -> v))))))

  def destroy(id: String)(implicit pipeline: HttpRequest => Future[HttpResponse]) = 
    pipeline(Delete(s"/api/subnets/$id"))

}

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

case class NetworkInterface(
  id: String,
  network_ids: List[String],
  instance_ids:List[String],
  ip_address: String
)

object NetworkInterface {

  def xmlToNetworkInterface(data: NodeSeq): NetworkInterface = {
    val id = (data \ "@id").text
    val network_ids = (data \ "networks" \ "network").map(n => (n \ "@id").text).toList
    val instance_ids = (data \ "instances" \ "instance").map(n => (n \ "@id").text).toList
    val ip_address = (data \ "ip_address").text
  
    NetworkInterface(id, network_ids, instance_ids, ip_address)
  }

  implicit val unmarshalNetworkInterface = 
    Unmarshaller.delegate[NodeSeq, NetworkInterface](`text/xml`, `application/xml`, `text/html`, `application/xhtml+xml`)(xmlToNetworkInterface)

  implicit val unmarshalNetworkInterfaces = 
    Unmarshaller.delegate[NodeSeq, List[NetworkInterface]](`text/xml`, `application/xml`, `text/html`, `application/xhtml+xml`) { data => 
      (data \ "network_interface").map(xmlToNetworkInterface).toList
    }

  def index()(implicit ec: ExecutionContext, pipeline: HttpRequest => Future[HttpResponse]) = 
    (pipeline ~> unmarshal[List[NetworkInterface]])(aux2)(Get(Uri("/api/network_interfaces")))

  def show(id: String)(implicit ec: ExecutionContext, pipeline: HttpRequest => Future[HttpResponse]) = 
    (pipeline ~> unmarshal[NetworkInterface])(aux2)(Get(s"/api/network_interfaces/$id"))

  def create(
    instance: String,
    network: String,
    name: Option[String] = None
  )(implicit ec: ExecutionContext, pipeline: HttpRequest => Future[HttpResponse]) = 
    (pipeline ~> unmarshal[NetworkInterface])(aux2)(Post("/api/network_interfaces", FormData(Seq(
        "instance" -> Some(instance), 
        "network" -> Some(network),
        "name" -> name
    ).flatMap(kv => kv._2.map(v => (kv._1 -> v))))))

  def destroy(id: String)(implicit pipeline: HttpRequest => Future[HttpResponse]) = 
    pipeline(Delete(s"/api/network_interfaces/$id"))

}

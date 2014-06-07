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

case class Listener(
  protocol: String,
  load_balancer_port: Int,
  instance_port: Int
)

case class LoadBalancer(
  realm_id: String,
  public_addresses: List[String],
  listeners: List[Listener],
  instances: List[String]
)

object LoadBalancer {

  def xmlToListener(data: NodeSeq): Listener = {
    val protocol = (data \ "@protocol").text
    val load_balancer_port = (data \ "load_balancer_port").text.toInt
    val instance_port = (data \ "instance_port").text.toInt

    Listener(protocol, load_balancer_port, instance_port)
  }

  def xmlToLoadBalancer(data: NodeSeq): LoadBalancer = {
    val realm_id = (data \ "realm" \ "@id").text
    val public_addresses = (data \ "public_addresses" \ "address").map(_.text).toList
    val listeners = (data \ "listeners" \ "listener").map(xmlToListener).toList
    val instances = (data \ "instances" \ "instance").map(n => (n \ "@id").text).toList
  
    LoadBalancer(realm_id, public_addresses, listeners, instances)
  }

  implicit val unmarshalLoadBalancer = 
    Unmarshaller.delegate[NodeSeq, LoadBalancer](`text/xml`, `application/xml`, `text/html`, `application/xhtml+xml`)(xmlToLoadBalancer)

  implicit val unmarshalLoadBalancers = 
    Unmarshaller.delegate[NodeSeq, List[LoadBalancer]](`text/xml`, `application/xml`, `text/html`, `application/xhtml+xml`) { data => 
      (data \ "load_balancer").map(xmlToLoadBalancer).toList
    }

  def index(id: Option[String] = None)(implicit ec: ExecutionContext, pipeline: HttpRequest => Future[HttpResponse]) = 
    (pipeline ~> unmarshal[List[LoadBalancer]])(aux2)(Get(Uri("/api/load_balancers").copy(query = Query(Map(
      "id" -> id
    ).flatMap(kv => kv._2.map(v => (kv._1 -> v)))))))

  def show(id: String)(implicit ec: ExecutionContext, pipeline: HttpRequest => Future[HttpResponse]) = 
    (pipeline ~> unmarshal[LoadBalancer])(aux2)(Get(s"/api/load_balancers/$id"))

  def create(
    name: String,
    realm_id: String,
    listener_protocol: String,
    listener_balancer_port: Int,
    listener_instance_port: Int
  )(implicit ec: ExecutionContext, pipeline: HttpRequest => Future[HttpResponse]) = 
    (pipeline ~> unmarshal[Address])(aux2)(Post("/api/load_balancers", FormData(Seq(
        "name" -> name, 
        "realm_id" -> realm_id,
        "listener_protocol" -> listener_protocol,
        "listener_balancer_port" -> listener_balancer_port.toString,
        "listener_instance_port" -> listener_instance_port.toString
    ))))

  def destroy(id: String)(implicit pipeline: HttpRequest => Future[HttpResponse]) = 
    pipeline(Delete(s"/api/load_balancers/$id"))

  def register(id: String, instance_id: String)(implicit pipeline: HttpRequest => Future[HttpResponse]) = 
    pipeline(Post(s"/api/load_balancers/$id/register", FormData(Map("instance_id" -> instance_id))))

  def unregister(id: String, instance_id: String)(implicit pipeline: HttpRequest => Future[HttpResponse]) = 
    pipeline(Post(s"/api/load_balancers/$id/unregister", FormData(Map("instance_id" -> instance_id))))

}

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

  def strictToLoadBalancer(dataStr: Strict): LoadBalancer = {
    val data = XML.loadString(dataStr.data.utf8String)
    xmlToLoadBalancer(data)
  }

  def strictToLoadBalancerList(dataStr: Strict): List[LoadBalancer] = {
    val data = XML.loadString(dataStr.data.utf8String)
    (data \ "load_balancer").map(xmlToLoadBalancer).toList
  }

  def index(id: Option[String] = None)(implicit ec: ExecutionContext, pipeline: HttpRequest => Future[HttpResponse], timeout: Timeout, materializer: FlowMaterializer) = 
    pipeline(HttpRequest(GET, uri = Uri("/api/load_balancers").copy(query = Query(Map(
      "id" -> id
    ).flatMap(kv => kv._2.map(v => (kv._1 -> v))))))).flatMap(_.entity.toStrict(timeout.duration).map(strictToLoadBalancerList).toFuture)

  def show(id: String)(implicit ec: ExecutionContext, pipeline: HttpRequest => Future[HttpResponse]) = 
    pipeline(HttpRequest(GET, uri = Uri(s"/api/load_balancers/$id")))

  def create(
    name: String,
    realm_id: String,
    listener_protocol: String,
    listener_balancer_port: Int,
    listener_instance_port: Int
  )(implicit ec: ExecutionContext, pipeline: HttpRequest => Future[HttpResponse], timeout: Timeout, materializer: FlowMaterializer) = 
    pipeline(HttpRequest(POST, uri = Uri("/api/load_balancers"), entity = Strict(ContentType(`application/x-www-form-urlencoded`), ByteString(Map(
        "name" -> name, 
        "realm_id" -> realm_id,
        "listener_protocol" -> listener_protocol,
        "listener_balancer_port" -> listener_balancer_port.toString,
        "listener_instance_port" -> listener_instance_port.toString
    ).flatMap(kv => kv._2.map(v => (s"${kv._1}=${v}"))).mkString("&"))))).flatMap(_.entity.toStrict(timeout.duration).map(strictToLoadBalancer).toFuture)

  def destroy(id: String)(implicit pipeline: HttpRequest => Future[HttpResponse]) = 
    pipeline(HttpRequest(DELETE, uri = Uri(s"/api/load_balancers/$id")))

  def register(id: String, instance_id: String)(implicit ec: ExecutionContext, pipeline: HttpRequest => Future[HttpResponse], timeout: Timeout, materializer: FlowMaterializer) = 
    pipeline(HttpRequest(POST, uri = Uri(s"/api/load_balancers/$id/register"), entity = Strict(ContentType(`application/x-www-form-urlencoded`), ByteString(Map(
      "instance_id" -> instance_id
    ).flatMap(kv => kv._2.map(v => (s"${kv._1}=${v}"))).mkString("&"))))).flatMap(_.entity.toStrict(timeout.duration).map(strictToLoadBalancer).toFuture)

  def unregister(id: String, instance_id: String)(implicit ec: ExecutionContext, pipeline: HttpRequest => Future[HttpResponse], timeout: Timeout, materializer: FlowMaterializer) = 
    pipeline(HttpRequest(POST, uri = Uri(s"/api/load_balancers/$id/unregister"), entity = Strict(ContentType(`application/x-www-form-urlencoded`), ByteString(Map(
      "instance_id" -> instance_id
    ).flatMap(kv => kv._2.map(v => (s"${kv._1}=${v}"))).mkString("&"))))).flatMap(_.entity.toStrict(timeout.duration).map(strictToLoadBalancer).toFuture)

}

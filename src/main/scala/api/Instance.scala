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

case class Instance(
  id: String,
  realm_id: String,
  owner_id: String,
  image_id: String,
  hardware_profile_id: String,
  actions: List[String],
  state: String,
  public_addresses: List[Address],
  private_addresses: List[Address]
)

object Instance {
  //import Address.unmarshalAddress
  import Address.xmlToAddress
  
  def xmlToInstance(data: NodeSeq): Instance = {
    val id = (data \ "@id").text
    val state = (data \ "state").text
    val owner_id = (data \ "owner_id").text
    val realm_id = (data \ "realm" \ "@id").text
    val image_id = (data \ "image" \ "@id").text
    val hardware_profile_id = (data \ "hardware_profile" \ "@id").text
    val public_addresses = (data \ "public_addresses" \ "address").map(xmlToAddress).toList
    val private_addresses = (data \ "private_addresses" \ "address").map(xmlToAddress).toList
    val actions = (data \ "actions" \ "link" \ "@rel").map(_.text).toList
  
    Instance(id, realm_id, owner_id, image_id, hardware_profile_id, actions, state, public_addresses, private_addresses)
  }

  def strictToInstance(dataStr: Strict): Instance = {
    val data = XML.loadString(dataStr.data.utf8String)
    xmlToInstance(data)
  }

  def strictToInstanceList(dataStr: Strict): List[Instance] = {
    val data = XML.loadString(dataStr.data.utf8String)
    (data \ "instance").map(xmlToInstance).toList
  }

  def show(id: String)(implicit ec: ExecutionContext, pipeline: HttpRequest => Future[HttpResponse], timeout: Timeout, materializer: FlowMaterializer) = 
    pipeline(HttpRequest(GET, uri = Uri(s"/api/instances/$id"))).flatMap(_.entity.toStrict(timeout.duration, materializer).map(strictToInstance))

  def index(
    id: Option[String] = None, 
    state: Option[String] = None, 
    realm_id: Option[String] = None
  )(implicit ec: ExecutionContext, pipeline: HttpRequest => Future[HttpResponse], timeout: Timeout, materializer: FlowMaterializer) = 
    pipeline(HttpRequest(GET, uri = Uri("/api/instances").copy(query = Query(Map(
      "id" -> id,
      "state" -> state,
      "realm_id" -> realm_id
    ).flatMap(kv => kv._2.map(v => (kv._1 -> v))))))).flatMap(_.entity.toStrict(timeout.duration, materializer).map(strictToInstanceList))

  def create(
    image_id: String,
    metric: Option[String] = None,
    name: Option[String] = None,
    keyname: Option[String] = None,
    realm_id: Option[String] = None,
    hwp_id: Option[String] = None,
    user_data: Option[String] = None,
    user_files: Option[String] = None,
    user_iso: Option[String] = None,
    firewalls: List[String] = List.empty[String],
    password: Option[String] = None,
    load_balancer_id: Option[String] = None,
    instance_count: Option[Int] = None,
    snapshot_id: Option[String] = None,
    device_name: Option[String] = None,
    sandbox: Option[String] = None
  )(implicit ec: ExecutionContext, pipeline: HttpRequest => Future[HttpResponse], timeout: Timeout, materializer: FlowMaterializer): Future[Instance] = 
    pipeline(HttpRequest(POST, uri = Uri("/api/instances"), entity = Strict(ContentType(`application/x-www-form-urlencoded`), ByteString(Map(
        "image_id" -> Some(image_id), 
        "metric" -> metric, 
        "name" -> name, 
        "keyname" -> keyname, 
        "realm_id" -> realm_id, 
        "hwp_id" -> hwp_id, 
        "user_data" -> user_data.map(ud => new sun.misc.BASE64Encoder().encode(ud.getBytes())), 
        "user_files" -> user_files, 
        "user_iso" -> user_iso, 
        "firewalls" -> (if (firewalls.isEmpty) None else Some(firewalls.toString)),
        "password" -> password, 
        "load_balancer_id" -> load_balancer_id, 
        "instance_count" -> Some(instance_count.getOrElse(1).toString), 
        "snapshot_id" -> snapshot_id, 
        "device_name" -> device_name, 
        "sandbox" -> sandbox
    ).flatMap(kv => kv._2.map(v => (s"${kv._1}=${v}"))).mkString("&"))))).flatMap(_.entity.toStrict(timeout.duration, materializer).map(strictToInstance))

  def reboot(id: String)(implicit pipeline: HttpRequest => Future[HttpResponse]) = 
    pipeline(HttpRequest(POST, uri = Uri(s"/api/instances/$id/reboot")))

  def start(id: String)(implicit pipeline: HttpRequest => Future[HttpResponse]) = 
    pipeline(HttpRequest(POST, uri = Uri(s"/api/instances/$id/start")))

  def stop(id: String)(implicit pipeline: HttpRequest => Future[HttpResponse]) = 
    pipeline(HttpRequest(POST, uri = Uri(s"/api/instances/$id/stop")))

  def destroy(id: String)(implicit pipeline: HttpRequest => Future[HttpResponse]) = 
    pipeline(HttpRequest(DELETE, uri = Uri(s"/api/instances/$id")))

  def run(
    id: String,
    cmd: String,
    private_key: Option[String] = None,
    password: Option[String] = None,
    ip: Option[String] = None,
    port: Int = 22
  )(implicit pipeline: HttpRequest => Future[HttpResponse]) = 
    pipeline(HttpRequest(POST, uri = Uri(s"/api/instances/$id/run"), entity = Strict(ContentType(`application/x-www-form-urlencoded`), ByteString(Map(
        "cmd" -> Some(cmd), 
        "private_key" -> private_key, 
        "password" -> password, 
        "ip" -> ip, 
        "port" -> Some(port.toString)
    ).flatMap(kv => kv._2.map(v => (s"${kv._1}=${v}"))).mkString("&")))))

}

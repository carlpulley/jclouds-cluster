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
  import Address.unmarshalAddress
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
  
    Instance(id, realm_id, owner_id, image_id, hardware_profile_id, actions, state, public_addresses, private_addresses )
  }

  implicit val unmarshalInstance = 
    Unmarshaller.delegate[NodeSeq, Instance](`text/xml`, `application/xml`, `text/html`, `application/xhtml+xml`)(xmlToInstance)
  
  implicit val unmarshalInstances = 
    Unmarshaller.delegate[NodeSeq, List[Instance]](`text/xml`, `application/xml`, `text/html`, `application/xhtml+xml`) {   data =>
      (data \ "instance").map(xmlToInstance).toList
    }

  def show(id: String)(implicit ec: ExecutionContext, pipeline: HttpRequest => Future[HttpResponse]) = 
    (pipeline ~> unmarshal[Instance])(aux2)(Get(s"/api/instances/$id"))

  def index(
    id: Option[String] = None, 
    state: Option[String] = None, 
    realm_id: Option[String] = None
  )(implicit ec: ExecutionContext, pipeline: HttpRequest => Future[HttpResponse]) = 
    (pipeline ~> unmarshal[List[Instance]])(aux2)(Get(Uri("/api/instances").copy(query = Query(Map(
      "id" -> id,
      "state" -> state,
      "realm_id" -> realm_id
    ).flatMap(kv => kv._2.map(v => (kv._1 -> v)))))))

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
  )(implicit ec: ExecutionContext, pipeline: HttpRequest => Future[HttpResponse]): Future[Instance] = 
    (pipeline ~> unmarshal[Instance])(aux2)(Post("/api/instances", FormData(Seq(
        "image_id" -> Some(image_id), 
        "metric" -> metric, 
        "name" -> name, 
        "keyname" -> keyname, 
        "realm_id" -> realm_id, 
        "hwp_id" -> hwp_id, 
        "user_data" -> user_data.map(ud => new sun.misc.BASE64Encoder().encode(ud.getBytes())), 
        "user_files" -> user_files, 
        "user_iso" -> user_iso, 
        "firewalls" -> Some(firewalls.toString),
        "password" -> password, 
        "load_balancer_id" -> load_balancer_id, 
        "instance_count" -> Some(instance_count.getOrElse(1).toString), 
        "snapshot_id" -> snapshot_id, 
        "device_name" -> device_name, 
        "sandbox" -> sandbox
    ).flatMap(kv => kv._2.map(v => (kv._1 -> v))))))

  def reboot(id: String)(implicit pipeline: HttpRequest => Future[HttpResponse]) = 
    pipeline(Post(s"/api/instances/$id/reboot"))

  def start(id: String)(implicit pipeline: HttpRequest => Future[HttpResponse]) = 
    pipeline(Post(s"/api/instances/$id/start"))

  def stop(id: String)(implicit pipeline: HttpRequest => Future[HttpResponse]) = 
    pipeline(Post(s"/api/instances/$id/stop"))

  def destroy(id: String)(implicit pipeline: HttpRequest => Future[HttpResponse]) = 
    pipeline(Delete(s"/api/instances/$id"))

  def run(
    id: String,
    cmd: String,
    private_key: Option[String] = None,
    password: Option[String] = None,
    ip: Option[String] = None,
    port: Int = 22
  )(implicit pipeline: HttpRequest => Future[HttpResponse]) = 
    pipeline(Post(s"/api/instances/$id/run", FormData(Seq(
        "cmd" -> Some(cmd), 
        "private_key" -> private_key, 
        "password" -> password, 
        "ip" -> ip, 
        "port" -> Some(port.toString)
    ).flatMap(kv => kv._2.map(v => (kv._1 -> v))))))

}
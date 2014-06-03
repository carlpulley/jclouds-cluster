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

import akka.actor.ActorRefFactory
import com.typesafe.config.ConfigFactory
import scala.concurrent.Future
import spray.http._
import spray.http.Uri.Query
import spray.client.pipelining._

trait Classic {

  val config = ConfigFactory.load()

  implicit val actorFactory: ActorRefFactory
  implicit val dispatcher = actorFactory.dispatcher

  val pipeline: HttpRequest => Future[HttpResponse] = sendReceive

  object Instances {
    def show(id: String) = pipeline(Get(s"/instances/$id"))

    def index(realm_id: Option[String] = None) = pipeline(Get(Uri("/instances").copy(query = Query(Map(
      "realm_id" -> realm_id
    ).flatMap(kv => kv._2.map(v => (kv._1 -> v)))))))

    def create(
      metric: Option[String] = None,
      name: Option[String] = None,
      keyname: Option[String] = None,
      image_id: String,
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
    ) = pipeline(Post("/instances", FormData(Seq(
          "metric" -> metric, 
          "name" -> name, 
          "keyname" -> keyname, 
          "image_id" -> Some(image_id), 
          "realm_id" -> realm_id, 
          "hwp_id" -> hwp_id, 
          "user_data" -> user_data, 
          "user_files" -> user_files, 
          "user_iso" -> user_iso, 
          //"firewalls" -> firewalls, // FIXME:
          "password" -> password, 
          "load_balancer_id" -> load_balancer_id, 
          "instance_count" -> Some(instance_count.getOrElse(1).toString), 
          "snapshot_id" -> snapshot_id, 
          "device_name" -> device_name, 
          "sandbox" -> sandbox
    ).flatMap(kv => kv._2.map(v => (kv._1 -> v))))))

    def reboot(id: String) = pipeline(Post(s"/instances/$id/reboot"))

    def start(id: String) = pipeline(Post(s"/instances/$id/start"))

    def stop(id: String) = pipeline(Post(s"/instances/$id/stop"))

    def destroy(id: String) = pipeline(Delete(s"/instances/$id"))

    def run(
      id: String,
      cmd: String,
      private_key: Option[String] = None,
      password: Option[String] = None,
      ip: Option[String] = None,
      port: Int = 22
    ) = pipeline(Post(s"/instances/$id/run", FormData(Seq(
          "cmd" -> Some(cmd), 
          "private_key" -> private_key, 
          "password" -> password, 
          "ip" -> ip, 
          "port" -> Some(port.toString)
    ).flatMap(kv => kv._2.map(v => (kv._1 -> v))))))
  }

}
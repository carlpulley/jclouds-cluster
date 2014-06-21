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

import akka.actor.ActorSystem
import akka.actor.Address
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import cakesolutions.api.deltacloud.Instance
import cakesolutions.api.deltacloud.Realm
import com.typesafe.config.ConfigFactory
import scala.concurrent.duration._
import scala.concurrent.Future
import scala.io.Source
import scala.util.Failure
import scala.util.Success
import spray.can.Http
import spray.http._
import spray.client.pipelining._

class DeltacloudProvisioner(val label: String, joinAddress: Address)(implicit system: ActorSystem) {
  import system.dispatcher

  val config = ConfigFactory.load()
  val host = config.getString("deltacloud.host")
  val port = config.getInt("deltacloud.port")

  implicit val timeout = Timeout(config.getInt("deltacloud.timeout").minutes)

  implicit val pipeline = (request: HttpRequest) => 
    (IO(Http) ? (request, Http.HostConnectorSetup(host, port = port))).mapTo[HttpResponse]

  var node: Option[Instance] = None

  def bootstrap(action: Instance => Unit): Future[Unit] = {
    if (node.isEmpty) {
      val driver = config.getString("deltacloud.driver")
      val password = 
        try { 
          Some(config.getString(s"deltacloud.$driver.password"))
        } catch { 
          case _: Throwable => None
        }
      val default_user_password = password.map(pw =>
        s"""password: "$pw"
        |chpasswd: { expire: False }
        |"""
      ).getOrElse("")
      val ssh_keyname = config.getString(s"deltacloud.$driver.keyname")
      // This is a single line file, so YAML indentation is not impacted
      val ssh_key = 
        try { 
          Some(Source.fromFile(s"${config.getString("user.home")}/.ssh/$ssh_keyname.pub").mkString) 
        } catch {
          case _: Throwable => None
        }
      val ssh_authorized_keys = ssh_key.map(key => 
        s"""ssh_authorized_keys:
        |  - $key
        |"""
      ).getOrElse("")
      val chef_url = config.getString("deltacloud.chef.url")
      val chef_client = config.getString("deltacloud.chef.validation.client_name")
      // We need to take care here that our indentation is preserved in our YAML configuration
      val chef_validator = Source.fromFile(config.getString("deltacloud.chef.validation.pem")).getLines.mkString("\n|      ")

      for {
        realms <- Realm.index(state = Some("available"))
        vm <- Instance.create(
          image_id = config.getString(s"deltacloud.$driver.image"),
          keyname = Some(config.getString(s"deltacloud.$driver.keyname")),
          realm_id = Some(realms.head.id),
          hwp_id = Some(config.getString(s"deltacloud.$driver.hwp")),
          user_data = Some(s"""#cloud-config
            |
            |hostname: $label
            |
            |$default_user_password
            |$ssh_authorized_keys
            |
            |apt-upgrade: true
            |
            |chef:
            |  install_type: "packages"
            |  force_install: false
            |  
            |  server_url: "$chef_url"
            |  validation_name: "$chef_client"
            |  validation_key: |
            |      $chef_validator
            |  
            |  # A run list for a first boot json
            |  run_list:
            |   - "recipe[apt]"
            |   - "recipe[java]"
            |   - "recipe[cluster@0.1.9]"
            |  
            |  # Initial attributes used by the cookbooks
            |  initial_attributes:
            |     java:
            |       install_flavor: "oracle"
            |       jdk_version: 7
            |       oracle:
            |         accept_oracle_download_terms: true
            |     cluster:
            |       role: "$label"
            |       seedNode: "${joinAddress.toString}"
            |  
            |# Capture all subprocess output into a logfile
            |output: {all: '| tee -a /var/log/cloud-init-output.log'}
            |""".stripMargin)
        )
      } yield {
        node = Some(vm)
        action(vm)
      }
    } else {
      Future { () }
    }
  }

  def shutdown: Future[Unit] = { 
    node match {
      case Some(n) =>
        for {
          _ <- Instance.stop(n.id)
          _ <- Instance.destroy(n.id)
        } yield {
          node = None
          ()
        }
  
      case None =>
        Future { () }
    }
  }
}

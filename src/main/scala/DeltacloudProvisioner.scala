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
import scala.io.Source
import spray.can.Http
import spray.http._
import spray.client.pipelining._

class DeltacloudProvisioner(label: String, joinAddress: Address)(implicit system: ActorSystem) {
  import system.dispatcher

  val config = ConfigFactory.load()
  val host = config.getString("deltacloud.host")
  val port = config.getInt("deltacloud.port")

  implicit val timeout = Timeout(config.getInt("deltacloud.timeout").seconds)

  implicit val pipeline = (request: HttpRequest) => 
    (IO(Http) ? (request, Http.HostConnectorSetup(host, port = port))).mapTo[HttpResponse]

  var node: Option[Instance] = None

  def bootstrap(action: Instance => Unit): Unit = {
    if (node.isEmpty) {
      val driver = config.getString("deltacloud.driver")
      val chef_client = config.getString("deltacloud.chef.validation.client_name")
      val chef_validator = Scala.fromPath(config.getString("deltacloud.chef.validation.pem")).mkString

      for {
        realms <- Realm.index(state = Some("available"))
        vm <- Instance.create(
          image_id = config.getString(s"deltacloud.$driver.image"),
          keyname = Some(config.getString(s"deltacloud.$driver.keyname")),
          realm_id = Some(realms.head.id),
          hwp_id = Some(config.getString(s"deltacloud.$driver.hwp")),
          user_data = Some(s"""#!/bin/bash
            |
            |curl -L https://www.opscode.com/chef/install.sh -o /tmp/chef-install.sh
            |bash /tmp/chef-install.sh
            |mkdir /etc/chef
            |cat >/etc/chef/$chef_client <<PEM
            |$chef_validator
            |PEM
            |cat > /etc/chef/first-boot.json <<JSON
            |{
            | "run_list": [
            |   "recipe[apt]",
            |   "recipe[java]",
            |   "recipe[cluster]"
            | ],
            | "override_attributes": {
            |   "java": { "jdk_version": "7" },
            |   "cluster": { "role": "$label", "seedNode": "${joinAddress.toString}" }
            | }
            |}
            |JSON
            |/usr/bin/chef-client -j /etc/chef/first-boot.json
            |""".stripMargin)
        )
      } yield {
        node = Some(vm)
        action(vm)
      }
    }
  }

  def shutdown: Unit = {
    node.map(n => Instance.stop(n.id))
    // TODO: need to schedule a destroy action at a slightly latter point in time (do we need to check and report action failure?)
  }
}

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

package example

package provision

import akka.actor.ActorSystem
import akka.contrib.jul.JavaLogging
import scala.io.Source

class Common(implicit val system: ActorSystem) extends Configuration with JavaLogging {

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

  val common_config = s"""
      |$default_user_password
      |$ssh_authorized_keys
      |
      |apt-upgrade: true
      |  
      |# Capture all subprocess output into a logfile
      |output: {all: '| tee -a /var/log/cloud-init-output.log'}
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
      |   - "recipe[cluster@0.1.10]"
      |  
      |  # Initial attributes used by the cookbooks
      |  initial_attributes:
      |     java:
      |       jdk_version: 7
  """

}

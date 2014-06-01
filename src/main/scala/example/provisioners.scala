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

import akka.actor.Address
import net.liftweb.json.JsonDSL._

// Simply run:
//  - the 'bootstrap' method to provision a cloud vendors Ubuntu instance
//  - the 'suspend' and 'resume' methods allow the instance to be saved and resumed (vendor dependent)
//  - the 'reboot' method reboots the instance
//  - and the 'shutdown' method to terminate that instance

sealed trait Provisioner
case object Rackspace extends Provisioner
case object Amazon extends Provisioner

class RackspaceProvisioner(role: String, seedNode: Address) extends vendor.Rackspace.Ubuntu("13.10") {
  template_builder
    .minRam(2048)

  chef_runlist
    .addRecipe("java")
    .addRecipe("cluster")

  chef_attributes += ("java" -> ("jdk_version" -> "7"))
  chef_attributes += ("cluster" -> ("role" -> role) ~ ("seedNode" -> seedNode.toString))

  ports += 2552
}

// Since Ubuntu 12.04, we use image ID rather than OS version (see https://cloud-images.ubuntu.com/locator/ec2/)
class AmazonProvisioner(role: String, seedNode: Address) extends vendor.AwsEc2.Ubuntu("") {
  template_builder
    .imageId("us-east-1/ami-84df3cec")
    .minRam(2048)

  chef_runlist
    .addRecipe("java")
    .addRecipe("cluster")

  chef_attributes += ("java" -> ("jdk_version" -> "7"))
  chef_attributes += ("cluster" -> ("role" -> role) ~ ("seedNode" -> seedNode.toString))

  ports += 2552
}

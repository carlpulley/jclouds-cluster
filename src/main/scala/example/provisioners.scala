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
//  - the 'bootstrap' method to provision a cloud providers Ubuntu instance
//  - the 'suspend' and 'resume' methods allow the instance to be saved and resumed (provider dependent)
//  - the 'reboot' method reboots the instance
//  - and the 'shutdown' method to terminate that instance

sealed trait Provisioner
case object Rackspace extends Provisioner
case object Amazon extends Provisioner

class RackspaceProvisioner(role: String, seedNode: Address) extends provider.Rackspace.Ubuntu("12.04") {
  template_builder
    .minRam(2048)

  chef_runlist
    .addRecipe("java")
    .addRecipe("cluster")

  chef_attributes += ("cluster" -> ("role" -> role))
  chef_attributes += ("cluster" -> ("seedNode" -> seedNode.toString))

  ports += 2552
}

class AmazonProvisioner(role: String, seedNode: Address) extends provider.AwsEc2.Ubuntu("12.04") {
  template_builder
    .minRam(2048)

  chef_runlist
    .addRecipe("java")
    .addRecipe("cluster")

  chef_attributes += ("cluster" -> ("role" -> role))
  chef_attributes += ("cluster" -> ("seedNode" -> seedNode.toString))

  ports += 2552
}

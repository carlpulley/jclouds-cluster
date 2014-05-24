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

package cakesolutions.example

import akka.actor.ActorSystem
import akka.actor.AddressFromURIString
import akka.actor.Deploy
import akka.actor.Props
import akka.actor.RemoteScope
import akka.kernel.Bootable

class ClusterApplication extends Bootable {
  implicit val system = ActorSystem("JCloudsClustering")

  val controller = system.actorOf(Props[ClusterNode], name = "controller")
  val rackspace = RackspaceProvisioner.bootstrap
  val amazon = AmazonProvisioner.bootstrap

  def startup = {
    val rackspace_address = AddressFromURIString(s"akka.tcp://JCloudsClustering@${rackspace.getPublicAddresses().head}:2552")
    val amazon_address = AddressFromURIString(s"akka.tcp://JCloudsClustering@${amazon.getPublicAddresses().head}:2552")
    system.actorOf(Props[ClusterNode].withDeploy(Deploy(scope = RemoteScope(rackspace_address))), name = "rackspace")
    system.actorOf(Props[ClusterNode].withDeploy(Deploy(scope = RemoteScope(amazon_address))), name = "amazon")
  }

  def shutdown = {
    system.shutdown
    RackspaceProvisioner.shutdown
    AmazonProvisioner.shutdown
  }
}

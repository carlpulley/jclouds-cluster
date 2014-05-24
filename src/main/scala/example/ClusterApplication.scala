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

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.AddressFromURIString
import akka.actor.Deploy
import akka.actor.Props
import akka.kernel.Bootable
import akka.remote.RemoteScope
import scala.collection.JavaConverters._

class ClusterApplication extends Bootable {
  import ClusterMessages._

  implicit val system = ActorSystem("JCloudsClustering")

  val nodes = Map(
    "rackspace" -> RackspaceProvisioner.bootstrap,
    "amazon" -> AmazonProvisioner.bootstrap
  )

  val addresses = for((label, metadata) <- nodes) yield {
    val address = AddressFromURIString(s"akka.tcp://JCloudsClustering@${metadata.getPublicAddresses().asScala.head}:2552")
    system.actorOf(Props[ClusterNode].withDeploy(Deploy(scope = RemoteScope(address))), name = label)
  }

  val controller = system.actorOf(Props[ClusterNode], name = "controller")

  def startup = {
    // Wire up and build our cluster
    for (node <- addresses.toSeq :+ controller) {
      node ! Controller(List(controller.path.address))
    }
  }

  def shutdown = {
    system.shutdown
    RackspaceProvisioner.shutdown
    AmazonProvisioner.shutdown
  }
}

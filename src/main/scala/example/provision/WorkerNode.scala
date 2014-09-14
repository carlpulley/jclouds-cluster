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

import akka.actor.Address
import scala.async.Async.async
import scala.async.Async.await
import scala.concurrent.Future

trait WorkerNode extends Common {

  import system.dispatcher
  import client.ClientNode._

  def workerNode(label: String): Future[Unit] = {
    require(controller.nonEmpty)

    async {
      // Here, workers connect to the controller via its private or internal IP address
      val private_addresses = await(controller.get.private_addresses)

      workerNode(label, private_addresses.head.ip)
    }
  }

  private def workerNode(label: String, ipAddress: String): Future[Unit] = {
    val joinAddress = Address("akka.tcp", system.name, ipAddress, 2552)
    val node = new DeltacloudProvisioner(label, Some(joinAddress))

    machines = machines + (label -> node)

    node.bootstrap(s"""#cloud-config
    |
    |hostname: $label
    |
    $common_config
    |     cluster:
    |       role: "worker"
    |       mainClass: "cakesolutions.example.WorkerNode"
    |       seedNode: "${joinAddress.toString}"
    |""".stripMargin
    ).recover {
      case exn =>
        log.error(s"Failed to provision the '$label' node: $exn")
        machines = machines - label
    }
  }

}

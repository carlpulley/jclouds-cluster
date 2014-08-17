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

import scala.concurrent.Future

trait ControllerNode extends Common {

  import system.dispatcher
  import ClientNode._

  val controllerNode: Future[Unit] = {
    val node = new DeltacloudProvisioner("controller")

    controller = Some(node)

    node.bootstrap(s"""#cloud-config
    |
    |hostname: controller
    |
    $common_config
    |     cluster:
    |       role: "controller"
    |       mainClass: "cakesolutions.example.ControllerNode"
    |""".stripMargin
    ).recover {
      case exn =>
        log.error(s"Failed to provision the 'controller' node: $exn")
        controller = None
        exn.printStackTrace()
    }
  }

}

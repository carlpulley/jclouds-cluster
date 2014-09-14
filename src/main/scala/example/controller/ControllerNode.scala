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

package controller

import akka.actor.{Props, ActorRef, ActorSystem}
import akka.cluster.Cluster
import akka.kernel.Bootable

class ControllerNode
  extends Bootable
  with Configuration {

  implicit val system = ActorSystem(config.getString("akka.system"))

  val cluster = Cluster(system)
  val joinAddress = cluster.selfAddress
  // We first ensure that we've joined our own cluster!
  cluster.join(joinAddress)

  var server: Option[ActorRef] = None

  def startup = {
    server = Some(system.actorOf(Props[HttpServer]))
  }

  def shutdown = {
    server.map(system.stop)
    server = None
    system.shutdown
  }

}

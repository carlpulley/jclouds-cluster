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

package client

import akka.actor.{Props, ActorSystem, ActorRef, PoisonPill}
import scala.async.Async._
import scala.concurrent.Future

object ClientNode {

  var client: Option[ActorRef] = None
  var controller: Option[DeltacloudProvisioner] = None
  var machines = Map.empty[String, DeltacloudProvisioner]

}

trait ClientNode extends Configuration {

  import system.dispatcher
  import ClientNode._

  implicit val system = ActorSystem(config.getString("akka.system"))

  object Connection {
    def open(): Unit = {
      require(controller.nonEmpty && client.isEmpty)

      // Here we connect via the gateways external IP address (this is port forwarded to the controller's private or internal IP address)
      client = Some(system.actorOf(Props[ClientActor], "Client"))
    }

    def close(): Unit = {
      require(client.nonEmpty)

      client.get ! PoisonPill
      client = None
    }
  }

  val Provision = new provision.Common with provision.ControllerNode with provision.WorkerNode

  def shutdown: Future[Unit] = {
    async {
      await(shutdownWorkers)

      controller.map(_.shutdown.map {
        case _ =>
          controller = None
          system.shutdown
      })
    }
  }

  def shutdownWorkers: Future[Unit] = {
    async {
      await(Future.sequence(machines.values.map(_.shutdown)))

      machines = Map.empty[String, DeltacloudProvisioner]
    }
  }

}

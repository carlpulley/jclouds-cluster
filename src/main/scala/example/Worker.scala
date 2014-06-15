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

import akka.actor.Actor
import akka.actor.ActorSystem
import akka.cluster.Cluster
import akka.kernel.Bootable
import ClusterMessages._
import com.typesafe.config.ConfigFactory
import scala.util.Random

class WorkerActor extends Actor {
  val config = ConfigFactory.load()

  def receive: Receive = {
    case Ping(msg, tag) =>
      val route = s"$tag-${self.path.name}"

      if (Random.nextInt(config.getInt("worker.die")) == 1) {
        sender ! Pong(s"$route says $msg")
      } else {
        sender ! Ping(msg, route)
      }
  }
}

class WorkerNode extends Bootable {
  val config = ConfigFactory.load()

  implicit val system = ActorSystem(config.getString("akka.system"))

  val cluster = Cluster(system)

  def startup = {
    // We simply listen for remote actor creation events by default here
  }

  def shutdown = {
    system.shutdown
  }
}

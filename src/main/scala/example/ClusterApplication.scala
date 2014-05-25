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

import akka.actor.ActorDSL._
import akka.actor.ActorLogging
import akka.actor.ActorSystem
import akka.actor.AddressFromURIString
import akka.actor.Deploy
import akka.actor.Props
import akka.cluster.Cluster
import akka.remote.RemoteScope
import com.typesafe.config.ConfigFactory
import scala.util.Random

object ClusterApplication {
  import ClusterMessages._

  val config = ConfigFactory.load()
  
  val systemName = config.getString("akka.system")

  implicit val system = ActorSystem(systemName)

  val nodes = Map(
    "red" -> config.getInt("node.red.port"),
    "green" -> config.getInt("node.green.port"),
    "blue" -> config.getInt("node.blue.port")
  )

  val addresses = for((label, port) <- nodes) yield {
    val address = AddressFromURIString(s"akka.tcp://${systemName}@127.0.0.1:${port}")
    system.actorOf(Props[ClusterNode].withDeploy(Deploy(scope = RemoteScope(address))), name = label)
  }

  // Ideally, we should subscribe to cluster MemberEvent's here - i.e. maintain valid member up status
  val source = actor(new Act with ActorLogging {
    become {
      case msg @ Ping(_, _) =>
        assert(addresses.nonEmpty)

        addresses.toList(Random.nextInt(addresses.size)) ! msg

      case Pong(msg) =>
        log.info(msg)
    }
  })

  def ping(msg: String): Unit = {
    source ! Ping(msg)
  }

  def startup = {
    // Wire up and build our cluster
    val joinAddress = AddressFromURIString(s"akka.tcp://${systemName}@127.0.0.1:${nodes.values.head}")
    for (node <- addresses) {
      node ! Controller(joinAddress)
    }
  }

  def shutdown = {
    // Other cluster JVMs should (hopefully) be killed off by our wrapper script!!
    system.shutdown
  }
}

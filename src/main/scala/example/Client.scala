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
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Address
import akka.actor.AddressFromURIString
import akka.actor.Deploy
import akka.actor.Props
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.CurrentClusterState
import akka.cluster.ClusterEvent.MemberUp
import akka.cluster.MemberStatus
import akka.remote.RemoteScope
import com.typesafe.config.ConfigFactory
import scala.sys.process._
import scala.util.Random

class ClientNode(nodes: Map[String, Int]) {
  import ClusterMessages._

  val config = ConfigFactory.load()
  
  val systemName = config.getString("akka.system")

  implicit val system = ActorSystem(systemName)

  val cluster = Cluster(system)
  val joinAddress = cluster.selfAddress
  // We first ensure that we've joined our cluster!
  cluster.join(joinAddress)

  var processes = Set.empty[Process]
  var addresses = Map.empty[Address, ActorRef]

  val client = actor(new Act with ActorLogging {
    become {
      case msg @ Ping(_, _) if (addresses.nonEmpty) =>
        addresses.values.toList(Random.nextInt(addresses.size)) ! msg
  
      case Pong(msg) =>
        log.info(msg)
   
      case state: CurrentClusterState =>
        // For demo purposes, log currently known up members
        log.info(state.members.filter(_.status == MemberStatus.Up).toString)

      case MemberUp(member) if (member.roles.nonEmpty) =>
        // Convention: (head) role is used to label the nodes (single) actor
        val node = member.address
        val act = system.actorOf(Props[WorkerActor].withDeploy(Deploy(scope = RemoteScope(node))), name = member.roles.head)
    
        addresses = addresses + (node -> act)
    }
  })

  // Ensure client is subscribed for member up events (i.e. allow introduction of new nodes for pinging)
  cluster.subscribe(client, classOf[MemberUp])

  // Finally, provision our required nodes
  for((label, port) <- nodes) {
    provisionNode(label, port)
  }

  // Here we shell-out to provision out cluster nodes
  def provisionNode(label: String, port: Int): Unit = {
    val AKKA_HOME = "pwd".!!.stripLineEnd + "/target/dist"
    val jarFiles = s"ls ${AKKA_HOME}/lib/".!!.split("\n").map(AKKA_HOME + "/lib/" + _).mkString(":")
    val proc = Process(s"""java -Xms256M -Xmx1024M -XX:+UseParallelGC -classpath "${AKKA_HOME}/config:${jarFiles}" -Dakka.home=${AKKA_HOME} -Dakka.remote.netty.tcp.port=${port} -Dakka.cluster.roles.1=${label} -Dakka.cluster.seed-nodes.1=${joinAddress} akka.kernel.Main cakesolutions.example.WorkerNode""").run

    processes = processes + proc
  }

  def shutdown = {
    for(proc <- processes) {
      proc.destroy
    }
    system.shutdown
  }
}

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
import akka.cluster.ClusterEvent.MemberExited
import akka.cluster.ClusterEvent.MemberUp
import akka.cluster.MemberStatus
import akka.contrib.jul.JavaLogging
import akka.remote.RemoteScope
import cakesolutions.api.deltacloud.Instance
import com.typesafe.config.ConfigFactory
import scala.concurrent.Future
import scala.sys.process._
import scala.util.Random

trait Client[Label] {
  import ClusterMessages._

  val config = ConfigFactory.load()
  
  val systemName = config.getString("akka.system")

  implicit val system = ActorSystem(systemName)

  val cluster = Cluster(system)
  val joinAddress = cluster.selfAddress
  // We first ensure that we've joined our cluster!
  cluster.join(joinAddress)

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

      case MemberExited(member) =>
        addresses = addresses - member.address
    }
  })

  // Ensure client is subscribed for member up events (i.e. allow introduction of new nodes for pinging)
  cluster.subscribe(client, classOf[MemberUp], classOf[MemberExited])

  // Defines how we provision our cluster nodes
  def provisionNode(label: Label): Future[Unit]

  def shutdown: Future[Unit]
}

class ClientNode(nodes: Set[String]) extends Client[String] with JavaLogging {
  import system.dispatcher

  var machines = Map.empty[String, DeltacloudProvisioner]

  // Finally, provision our required nodes
  Future.sequence(nodes.toSeq.map(provisionNode)).onFailure {
    case exn =>
      log.error(s"Failed to provision all the nodes in ${nodes}: $exn")
  }

  // Here we provision our cluster nodes
  def provisionNode(label: String): Future[Unit] = {
    val node = new DeltacloudProvisioner(label, joinAddress)
    machines = machines + (label -> node)
    node.bootstrap.recover {
      case exn =>
        machines = machines - label
        log.error(s"Failed to provision the '$label' node: $exn")
        // As we are called from our constructor, ensure that errors ripple upwards
        throw exn
    }
  }

  def shutdown: Future[Unit] = {
    Future.sequence(machines.values.map(_.shutdown)).map { 
      case _ =>
        machines = Map.empty[String, DeltacloudProvisioner]
        system.shutdown
    }.recover {
      case exn =>
        log.error(s"Failed to shutdown all the nodes in ${machines.values.map(_.label)}: $exn")
    }
  }
}

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

import akka.actor.Actor
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
import akka.cluster.Member
import akka.cluster.MemberStatus
import akka.contrib.jul.JavaLogging
import akka.io.IO
import akka.pattern.ask
import akka.remote.RemoteScope
import akka.util.Timeout
import cakesolutions.api.deltacloud.Instance
import com.typesafe.config.ConfigFactory
import java.io.ByteArrayInputStream
import java.io.ObjectInputStream
import scala.concurrent.Future
import scala.sys.process._
import scala.concurrent.duration._
import scala.util.Random
import spray.can.Http
import spray.client.pipelining._
import spray.http._
import spray.httpx.SprayJsonSupport
import spray.httpx.TransformerAux.aux2
import spray.routing.HttpService

class ClientActor(controllerHost: String) extends Actor with ActorLogging with HttpService with SprayJsonSupport {
  import ClusterMessages._
  import ClusterMessageFormats._
  import context.dispatcher

  val config = ConfigFactory.load()

  implicit val timeout = Timeout(config.getInt("client.timeout").minutes)

  val controller = (request: HttpRequest) => 
    (IO(Http)(context.system) ? (request, Http.HostConnectorSetup(controllerHost, port = 2552))).mapTo[HttpResponse]

  def actorRefFactory = context

  override def preStart() = {
    val updateInterval = config.getInt("client.update").minutes

    context.system.scheduler.schedule(0.minutes, updateInterval, self, GetMessages)
  }

  def processingMessages: Receive = {
    case pong: Pong =>
      log.info(pong.toString)

    case msg: Message =>
      log.error(s"Didn't expect to receive: $msg")
  }

  def clusterMessages: Receive = {
    case ProvisionNode(label) =>
      controller(Put(s"/cluster/provision/$label"))

    case ShutdownNode(label) =>
      controller(Delete(s"/cluster/shutdown/$label"))

    case GetMembers =>
      (controller ~> unmarshal[Array[Byte]])(aux2)(Get("/cluster/members")).map(bytes => {
        val in = new ObjectInputStream(new ByteArrayInputStream(bytes))
        in.readObject().asInstanceOf[List[Member]]
      }) // TODO: work with result
  }

  def endpointMessages: Receive = {
    case GetMessages =>
      (for {
         queuedMsgs <- (controller ~> unmarshal[List[Message]])(aux2)(Get("/controller/messages"))
       } yield for {
         msg <- queuedMsgs
       } yield (self ! msg)
      ).onFailure {
        case exn: Throwable =>
          log.error(s"Failed to query controller: $exn")
      }
  }

  def receive = 
    processingMessages orElse
      clusterMessages orElse
      endpointMessages
}

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

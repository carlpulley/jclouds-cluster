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
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Address
import akka.actor.Deploy
import akka.actor.Props
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.CurrentClusterState
import akka.cluster.ClusterEvent.MemberExited
import akka.cluster.ClusterEvent.MemberUp
import akka.cluster.Member
import akka.cluster.MemberStatus
import akka.io.IO
import akka.kernel.Bootable
import akka.pattern.ask
import akka.remote.RemoteScope
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import java.io.ByteArrayOutputStream
import java.io.ObjectOutputStream
import scala.concurrent.duration._
import scala.reflect.ClassTag
import scala.util.Failure
import scala.util.Random
import scala.util.Success
import spray.can.Http
import spray.client.pipelining._
import spray.http._
import spray.httpx.SprayJsonSupport
import spray.routing.HttpService

trait MessageInterface {
  import ClusterMessages.Message

  var messages = List.empty[Message]
}

trait ControllerService extends HttpService with SprayJsonSupport {
  self: Actor with ActorLogging with MessageInterface =>

  import ClusterMessages._
  import ClusterMessageFormats._
  import context.dispatcher

  val config = ConfigFactory.load()

  implicit val timeout = Timeout(config.getInt("deltacloud.timeout").minutes)

  val Label = "([a-z0-9]+)".r
  val joinAddress = Cluster(context.system).selfAddress
  // The controller node has already joined our cluster, so we do not need to do this here!

  var machines = Map.empty[String, DeltacloudProvisioner]

  def actorRefFactory = context

  val route =
    path("controller") {
      path("messages") {
        get {
          complete {
            (self ? GetMessages).mapTo[List[Message]]
          }
        }
      }
    } ~
    path("cluster") {
      path("members") {
        get {
          complete {
            // Here we serialize Member's as byte arrays
            (self ? CurrentClusterState).mapTo[List[Member]].map(mem => {
              val byteArray = new ByteArrayOutputStream(1024)
              val out = new ObjectOutputStream(byteArray)
              out.writeObject(mem)
              byteArray.toByteArray
            })
          }
        }
      } ~
      path("provision" / Label) { label =>
        put {
          complete {
            self ! ProvisionNode(label)
            ""
          }
        }
      } ~ 
      path("shutdown" / Label) { label =>
        delete {
          complete {
            self ! ShutdownNode(label)
            ""
          }
        }
      }
    }

  def endpointMessages: Receive = {
    case GetMessages =>
      sender ! messages
      messages = List.empty[Message]

    case ProvisionNode(label) =>
      val node = new DeltacloudProvisioner(label, joinAddress)(context.system)
      machines = machines + (label -> node)

      node.bootstrap.onFailure {
        case exn: Throwable =>
          log.error(s"Failed to provision the '$label' node: $exn")
          machines = machines - label
      }

    case ShutdownNode(label) =>
      machines(label).shutdown.onComplete {
        case Success(_) =>
          machines = machines - label

        case Failure(exn) =>
          log.error(s"Failed to shutdown the '$label' node: $exn")
      }
  }
}

class ControllerActor extends Actor with ActorLogging with ControllerService with MessageInterface {
  import ClusterMessages._

  var addresses = Map.empty[Address, ActorRef]

  def processingMessages: Receive = {
    case msg @ Ping(_, _) if (addresses.nonEmpty) =>
      addresses.values.toList(Random.nextInt(addresses.size)) ! msg

    case msg: Pong =>
      // Queue message for sending back to the client
      messages = messages :+ msg
  }

  def clusterMessages: Receive = {
    case state: CurrentClusterState =>
      sender ! state.members.filter(_.status == MemberStatus.Up)

    case MemberUp(member) if (member.roles.nonEmpty) =>
      // Convention: (head) role is used to label the nodes (single) actor
      val node = member.address
      val act = context.system.actorOf(Props[WorkerActor].withDeploy(Deploy(scope = RemoteScope(node))), name = member.roles.head)
    
      addresses = addresses + (node -> act)

    case MemberExited(member) =>
      addresses = addresses - member.address
  }

  def receive = 
    processingMessages orElse
      clusterMessages orElse
      endpointMessages orElse
      runRoute(route)
}

class ControllerNode extends Bootable {
  val config = ConfigFactory.load()

  implicit val system = ActorSystem(config.getString("akka.system"))

  val cluster = Cluster(system)
  val joinAddress = cluster.selfAddress
  // We first ensure that we've joined our own cluster!
  cluster.join(joinAddress)

  var controller: Option[ActorRef] = None

  def startup = {
    controller = Some(system.actorOf(Props[ControllerActor]))
  }

  def shutdown = {
    controller = None
    system.shutdown
  }
}

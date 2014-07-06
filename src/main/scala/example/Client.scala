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
import akka.actor.ActorPath
import akka.actor.ActorRef
import akka.actor.ActorSelection
import akka.actor.ActorSystem
import akka.actor.Address
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.MemberExited
import akka.cluster.ClusterEvent.MemberUp
import akka.contrib.jul.JavaLogging
import akka.http.Http
import akka.http.model.headers.RawHeader
import akka.http.model.HttpEntity
import akka.http.model.HttpEntity.Chunk
import akka.http.model.HttpEntity.Chunked
import akka.http.model.HttpEntity.LastChunk
import akka.http.model.HttpMethods._
import akka.http.model.HttpRequest
import akka.http.model.HttpResponse
import akka.http.model.Uri
import akka.io.IO
import akka.pattern.ask
import akka.stream.FlowMaterializer
import akka.stream.MaterializerSettings
import akka.stream.scaladsl.Flow
import scala.concurrent.Future
import scala.io.Source
import scala.util.Random

trait HttpClient extends Configuration with Serializer {
  this: Actor =>

  import context.dispatcher

  val materializer = FlowMaterializer(MaterializerSettings())

  // FIXME: following can fail
  def connection(host: String) = 
    (IO(Http)(context.system) ? Http.Connect(host, port = config.getInt("client.port"))).mapTo[Http.OutgoingConnection]

  def sendRequest(request: HttpRequest, host: String): Future[HttpResponse] = 
    connection(host).flatMap {
      case connect: Http.OutgoingConnection =>
        Flow(List(request -> 'NoContext)).produceTo(materializer, connect.processor)
        Flow(connect.processor).map(_._1).toFuture(materializer)
    }

  def httpProducer(host: String, target: ActorRef) = 
    sendRequest(HttpRequest(GET, uri = Uri("/messages")), host).map {
      case HttpResponse(_, _, Chunked(_, chunks), _) =>
        Flow(chunks).foreach {
          case Chunk(data, _) =>
            target ! deserialize(data.toArray[Byte]).get

          case LastChunk(_, _) =>
            // Intentionally do nothing
        }.consume(materializer)
    }
}

class ClientActor(controllerHost: String) extends Actor with ActorLogging with HttpClient with Configuration with Serializer {
  import ClusterMessages._
  import context.dispatcher

  // HTTP flow messages are materialised to actor messages from the controller
  val httpFlow = httpProducer(controllerHost, self)

  var addresses = Map.empty[Address, ActorSelection]

  def processingMessages: Receive = {
    case ping: Ping =>
      val node = addresses.values.toList(Random.nextInt(addresses.size))
      sendRequest(HttpRequest(PUT, uri = Uri("/ping"), entity = HttpEntity(serialize(ping)), headers = List(RawHeader("Worker", node.toSerializationFormat))), controllerHost)

    case pong: Pong =>
      log.info(pong.toString)
  }

  def clusterMessages: Receive = {
    case MemberUp(member) if (member.roles.nonEmpty) =>
      // Convention: (head) role is used to label the nodes (single) actor
      val node = member.address
      val act = context.actorSelection(ActorPath.fromString("/user/worker").toStringWithAddress(node))
    
      addresses = addresses + (node -> act)

    case MemberExited(member) =>
      addresses = addresses - member.address
  }

  def receive = 
    processingMessages orElse
      clusterMessages
}

class ClientNode extends Configuration with JavaLogging {
  val systemName = config.getString("akka.system")

  implicit val system = ActorSystem(systemName)

  import system.dispatcher

  var controller: Option[DeltacloudProvisioner] = None
  var machines = Map.empty[String, DeltacloudProvisioner]

  val driver = config.getString("deltacloud.driver")
  val password = 
    try { 
      Some(config.getString(s"deltacloud.$driver.password"))
    } catch { 
      case _: Throwable => None
    }
  val default_user_password = password.map(pw =>
    s"""password: "$pw"
    |chpasswd: { expire: False }
    |"""
  ).getOrElse("")
  val ssh_keyname = config.getString(s"deltacloud.$driver.keyname")
  // This is a single line file, so YAML indentation is not impacted
  val ssh_key = 
    try { 
      Some(Source.fromFile(s"${config.getString("user.home")}/.ssh/$ssh_keyname.pub").mkString) 
    } catch {
      case _: Throwable => None
    }
  val ssh_authorized_keys = ssh_key.map(key => 
    s"""ssh_authorized_keys:
    |  - $key
    |"""
  ).getOrElse("")
  val chef_url = config.getString("deltacloud.chef.url")
  val chef_client = config.getString("deltacloud.chef.validation.client_name")
  // We need to take care here that our indentation is preserved in our YAML configuration
  val chef_validator = Source.fromFile(config.getString("deltacloud.chef.validation.pem")).getLines.mkString("\n|      ")

  val common_config = s"""
      |$default_user_password
      |$ssh_authorized_keys
      |
      |apt-upgrade: true
      |  
      |# Capture all subprocess output into a logfile
      |output: {all: '| tee -a /var/log/cloud-init-output.log'}
      |
      |chef:
      |  install_type: "packages"
      |  force_install: false
      |  
      |  server_url: "$chef_url"
      |  validation_name: "$chef_client"
      |  validation_key: |
      |      $chef_validator
      |  
      |  # A run list for a first boot json
      |  run_list:
      |   - "recipe[apt]"
      |   - "recipe[java]"
      |   - "recipe[cluster@0.1.10]"
      |  
      |  # Initial attributes used by the cookbooks
      |  initial_attributes:
      |     java:
      |       install_flavor: "oracle"
      |       jdk_version: 7
      |       oracle:
      |         accept_oracle_download_terms: true
  """

  // Here we provision our cluster controller node
  val provisionControllerNode: Future[Unit] = {
    val node = new DeltacloudProvisioner("controller")

    controller = Some(node)

    node.bootstrap(s"""#cloud-config
      |
      |hostname: controller
      |
      $common_config
      |     cluster:
      |       role: "controller"
      |""".stripMargin
    ).recover {
      case exn =>
        controller = None
        log.error(s"Failed to provision the controller node: $exn")
        // As we are called from our constructor, ensure that errors ripple upwards
        throw exn
    }
  }

  // Here we provision our cluster worker nodes
  def provisionWorkerNode(label: String): Future[Unit] = {
    require(controller.nonEmpty)
    require(controller.get.node.nonEmpty)
    require(controller.get.node.get.private_addresses.nonEmpty)

    val ipAddress = controller.get.node.get.private_addresses.head.ip
    val joinAddress = Address("akka.tcp", systemName, ipAddress, 2552)
    val node = new DeltacloudProvisioner(label, Some(joinAddress))

    machines = machines + (label -> node)

    node.bootstrap(s"""#cloud-config
      |
      |hostname: $label
      |
      $common_config
      |     cluster:
      |       role: "worker"
      |       seedNode: "${joinAddress.toString}"
      |""".stripMargin
    ).recover {
      case exn =>
        machines = machines - label
        log.error(s"Failed to provision the '$label' node: $exn")
    }
  }

  def shutdown: Future[Unit] = {
    shutdownWorkers.map {
      case _ =>
        controller.map(_.shutdown.map {
          case _ =>
            controller = None
            system.shutdown
        }.recover {
          case exn =>
            log.error(s"Failed to shutdown the controller node: $exn")
        }
      )
    }
  }

  def shutdownWorkers: Future[Unit] = {
    Future.sequence(machines.values.map(_.shutdown)).map { 
      case _ =>
        machines = Map.empty[String, DeltacloudProvisioner]
    }.recover {
      case exn =>
        log.error(s"Failed to shutdown all the nodes in ${machines.values.map(_.label)}: $exn")
    }
  }
}

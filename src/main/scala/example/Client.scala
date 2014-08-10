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

import akka.actor._
import akka.cluster.ClusterEvent.MemberExited
import akka.cluster.ClusterEvent.MemberUp
import akka.contrib.jul.JavaLogging
import akka.http.Http
import akka.http.model.ContentTypes._
import akka.http.model.HttpEntity.Chunk
import akka.http.model.HttpEntity.Chunked
import akka.http.model.HttpEntity.LastChunk
import akka.http.model.HttpMethods._
import akka.http.model.HttpRequest
import akka.http.model.HttpResponse
import akka.http.model.Uri
import akka.io.IO
import akka.pattern.ask
import akka.stream.actor.ActorConsumer
import akka.stream.actor.ActorConsumer.OnNext
import akka.stream.actor.ActorProducer
import akka.stream.FlowMaterializer
import akka.stream.MaterializerSettings
import akka.stream.scaladsl.Flow
import akka.util.ByteString
import ClusterMessages._
import scala.async.Async.async
import scala.async.Async.await
import scala.concurrent.duration._
import scala.concurrent.Future
import scala.io.Source
import scala.util.Failure
import scala.util.Random
import scala.util.Success

trait HttpClient 
  extends Configuration 
  with Serializer {
    
  this: Actor with ActorLogging =>

  import context.dispatcher

  val materializer = FlowMaterializer(
    MaterializerSettings(
      initialFanOutBufferSize = config.getInt("client.materializer.initialFanOutBufferSize"),
      maxFanOutBufferSize     = config.getInt("client.materializer.maxFanOutBufferSize"),
      initialInputBufferSize  = config.getInt("client.materializer.initialInputBufferSize"),
      maximumInputBufferSize  = config.getInt("client.materializer.maximumInputBufferSize")
    )
  )

  val host = config.getString("controller.host")
  val port = config.getInt("controller.port")

  def connection = 
    (IO(Http)(context.system) ? Http.Connect(host, port = port)).mapTo[Http.OutgoingConnection]

  def sendRequest(request: HttpRequest): Future[HttpResponse] = 
    connection.flatMap {
      case connect: Http.OutgoingConnection =>
        Flow(List(request -> 'NoContext))
          .produceTo(materializer, connect.processor)
        Flow(connect.processor)
          .map(_._1)
          .toFuture(materializer)
    }

  val httpConsumer =
    sendRequest(HttpRequest(GET, uri = Uri("/messages"))).map {
      case response @ HttpResponse(_, _, Chunked(_, chunks), _) =>
        log.info(s"Received: $response")
        Flow(chunks)
          .mapConcat {
            case Chunk(data, _) =>
              val msg = deserialize(data.toArray[Byte]).get
              log.info(s"Received via GET /messages: $msg")
              List(msg)

            case LastChunk(_, _) =>
              log.error("GET /messages closed")
              List.empty
          }
          .produceTo(materializer, ActorConsumer(self))
    }

  val httpProducer =
    sendRequest(HttpRequest(PUT, uri = Uri("/messages"), entity = Chunked(`application/octet-stream`, ActorProducer[ByteString](self), materializer)))

}

class ClientActor 
  extends ActorLogging 
  with ActorConsumer 
  with ActorProducer[ByteString] 
  with HttpClient 
  with Configuration 
  with Serializer {

  import context.dispatcher

  override val requestStrategy = ActorConsumer.WatermarkRequestStrategy(config.getInt("client.watermark"))

  var addresses = Map.empty[Address, ActorSelection]

  val consumerSubscription: Cancellable = context.system.scheduler.schedule(0.seconds, config.getDuration("client.reconnect", SECONDS).seconds) {
    httpConsumer.onComplete {
      case Success(_) =>
        log.info("Successfully connected consumer to Controller")
        consumerSubscription.cancel()
        context.become(processingMessages orElse clusterMessages)

      case Failure(exn) =>
        log.error(s"Failed to connect consumer to Controller: $exn")
    }
  }

  val producerSubscription: Cancellable = context.system.scheduler.schedule(0.seconds, config.getDuration("client.reconnect", SECONDS).seconds) {
    httpProducer.onComplete {
      case Success(_) =>
        log.info("Successfully connected producer to Controller")
        producerSubscription.cancel()

      case Failure(exn) =>
        log.error(s"Failed to connect producer to Controller: $exn")
    }
  }

  override def postStop() = {
    consumerSubscription.cancel()
    producerSubscription.cancel()
  }

  def processingMessages: Receive = {
    case ping: Ping if (addresses.size > 0 && isActive && totalDemand > 0) =>
      log.info(s"Received: $ping")
      val node = addresses.values.toList(Random.nextInt(addresses.size))
      onNext(ByteString(serialize((ping, node))))

    case ping: Ping if (addresses.size == 0) =>
      log.warning(s"No workers - ignoring: $ping")

    case ping: Ping =>
      log.warning(s"No demand - ignoring: $ping")

    case OnNext(ping: Ping) if (addresses.size > 0 && isActive && totalDemand > 0) =>
      log.info(s"[Internal] Received: $ping")
      val node = addresses.values.toList(Random.nextInt(addresses.size))
      onNext(ByteString(serialize((ping, node))))

    case OnNext(ping: Ping) if (addresses.size == 0) =>
      log.warning(s"[Internal] No workers - ignoring: $ping")

    case OnNext(ping: Ping) =>
      log.warning(s"[Internal] No demand - ignoring: $ping")

    case OnNext(pong: Pong) =>
      log.info(s"${Console.RED}${pong.toString}${Console.RESET}")
  }

  def clusterMessages: Receive = {
    case OnNext(msg @ MemberUp(member)) if (member.roles.contains("worker")) =>
      log.info(s"Received: $msg with roles: ${member.roles}")
      val node = member.address
      val act = context.actorSelection(RootActorPath(node) / "user" / "worker")
      addresses = addresses + (node -> act)

    case OnNext(msg @ MemberUp(member)) =>
      log.warning(s"Member has no worker role - ignoring: $msg with roles: ${member.roles}")

    case OnNext(msg @ MemberExited(member)) =>
      log.info(s"Received: $msg")
      addresses = addresses - member.address
  }

  // Until we successfully connect to the controller, we do nothing
  def receive = Actor.emptyBehavior
}

class ClientNode extends Configuration with JavaLogging {
  val systemName = config.getString("akka.system")

  implicit val system = ActorSystem(systemName)

  import system.dispatcher

  var client: Option[ActorRef] = None
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
      |       jdk_version: 7
  """

  object Connection {
    def open(): Unit = {
      require(controller.nonEmpty)

      // Here we connect via the gateways external IP address (this is port forwarded to the controller's private or internal IP address)
      client = Some(system.actorOf(Props[ClientActor], "Client"))
    }

    def close(): Unit = ??? // TODO:
  }

  object Provision {
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

    def workerNode(label: String): Future[Unit] = {
      require(controller.nonEmpty)

      async {
        // Here, workers connect to the controller via its private or internal IP address
        val private_addresses = await(controller.get.private_addresses)

        workerNode(label, private_addresses.head.ip)
      }
    }

    private def workerNode(label: String, ipAddress: String): Future[Unit] = {
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
        |       mainClass: "cakesolutions.example.WorkerNode"
        |       seedNode: "${joinAddress.toString}"
        |""".stripMargin
        ).recover {
          case exn =>
            log.error(s"Failed to provision the '$label' node: $exn")
            machines = machines - label
        }
      }
  }

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

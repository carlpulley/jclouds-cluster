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

import akka.actor._
import akka.cluster.ClusterEvent.{MemberExited, MemberUp}
import akka.stream.actor.ActorSubscriberMessage.OnNext
import akka.stream.actor.{WatermarkRequestStrategy, ActorSubscriber}
import akka.stream.actor.logging.ActorPublisher
import akka.util.ByteString
import cakesolutions.example.ClusterMessages.{Pong, Ping}
import scala.concurrent.duration._
import scala.util.{Random, Failure, Success}

class ClientActor
  extends ActorLogging
  with ActorSubscriber
  with ActorPublisher[ByteString]
  with HttpClient
  with Configuration
  with Serializer {

  import context.dispatcher

  override val requestStrategy = WatermarkRequestStrategy(config.getInt("client.watermark"))

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
      log.info(s"Received: $ping - [Total Demand] ${Console.YELLOW}$totalDemand${Console.RESET}")
      val node = addresses.values.toList(Random.nextInt(addresses.size))
      onNext(ByteString(serialize((ping, node))))

    case ping: Ping if (addresses.size == 0) =>
      log.warning(s"No workers - ignoring: $ping")

    case ping: Ping =>
      log.warning(s"No demand - ignoring: $ping")

    case OnNext(ping: Ping) if (addresses.size > 0 && isActive && totalDemand > 0) =>
      log.info(s"[Internal] Received: $ping - [Total Demand] ${Console.YELLOW}$totalDemand${Console.RESET}")
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
      log.info(s"Received: $msg with roles: ${member.roles} - [Total Demand] ${Console.YELLOW}$totalDemand${Console.RESET}")
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
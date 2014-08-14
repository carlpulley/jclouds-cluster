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
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.cluster.Cluster
import akka.kernel.Bootable
import akka.stream.actor.ActorConsumer
import akka.stream.actor.ActorConsumer.OnNext
import akka.stream.actor.ActorProducer
import ClusterMessages._
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import scala.util.Random

class WorkerController extends ActorConsumer with ActorProducer[Message] with ActorLogging {

  override val requestStrategy = ActorConsumer.ZeroRequestStrategy

  request(1)

  def receive = {
    case OnNext((ping: Ping, worker: ActorRef)) =>
      log.info(s"Sending $ping to $worker")
      worker ! ping

    case msg: Message if (isActive && totalDemand > 0) =>
      log.info(s"Producing $msg to stream")
      onNext(msg)
      request(1)

    case msg: Message if (isActive && totalDemand == 0) =>
      log.warning(s"No demand - requeuing $msg")
      self ! msg
  }

}

class WorkerActor extends Actor with ActorLogging with Configuration {

  val hostname = InetAddress.getLocalHost().getHostName()

  def receive: Receive = {
    case ping @ Ping(msg, tag) =>
      log.info(s"Received: $ping from $sender")
      val route = s"$tag-$hostname"

      // Workload simulation
      Thread.sleep(config.getDuration("worker.workload", TimeUnit.MILLISECONDS))

      if (Random.nextInt(config.getInt("worker.die")) == 1) {
        sender() ! Pong(s"$route says $msg")
      } else {
        sender() ! Ping(msg, route)
      }
  }

}

class WorkerNode extends Bootable with Configuration {

  implicit val system = ActorSystem(config.getString("akka.system"))

  val cluster = Cluster(system)

  var worker: Option[ActorRef] = None

  def startup = {
    worker = Some(system.actorOf(Props[WorkerActor], "worker"))
  }

  def shutdown = {
    worker = None
    system.shutdown
  }

}

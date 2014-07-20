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

import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.cluster.Cluster
import akka.event.LoggingReceive
import akka.kernel.Bootable
import akka.stream.actor.ActorConsumer
import akka.stream.actor.ActorConsumer.OnNext
import akka.stream.actor.ActorProducer
import ClusterMessages._
import scala.util.Random

class WorkerActor extends ActorLogging with ActorConsumer with ActorProducer[Message] with Configuration {
  override val requestStrategy = ActorConsumer.WatermarkRequestStrategy(config.getInt("worker.watermark"))

  def receive: Receive = LoggingReceive {
    case OnNext(ping @ Ping(msg, tag)) =>
      log.info(s"Received: $ping")
      val role = Cluster(context.system).selfRoles.head
      val route = s"$tag-$role"

      if (Random.nextInt(config.getInt("worker.die")) == 1) {
        sender ! OnNext(Pong(s"$route says $msg"))
      } else {
        sender ! OnNext(Ping(msg, route))
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

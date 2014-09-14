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

package cakesolutions.example.worker

import java.net.InetAddress
import java.util.concurrent.TimeUnit

import akka.actor.{ActorLogging, Actor}
import cakesolutions.example.ClusterMessages.{Pong, Ping}
import cakesolutions.example.Configuration
import scala.util.Random

class WorkerActor
  extends Actor
  with ActorLogging
  with Configuration {

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

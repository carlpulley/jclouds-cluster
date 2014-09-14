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

package test

import akka.actor.ActorSystem
import akka.actor.Cancellable
import akka.event.Logging
import scala.concurrent.duration._

trait MessageInjector {
  this: Configuration =>

  import ClusterMessages._
  import client.ClientNode._

  implicit val system: ActorSystem

  import system.dispatcher
  
  private val clientSpeed = config.getDuration("client.speed", MILLISECONDS).milliseconds
  private val log = Logging.getLogger(system, this.getClass)

  def fastProducer(toSend: Int, batch: Int = 2, sent: Int = 0): Cancellable = system.scheduler.scheduleOnce(clientSpeed) {
    require(client.nonEmpty && toSend > 0 && sent >= 0 && batch > 0)

    for (n <- (1 to batch)) {
      if (sent + n <= toSend) {
        client.get ! Ping(s"Message ${sent + n}")
      }
    }

    if (sent + batch < toSend) {
      log.info(s"${Console.GREEN}Sleeping for $clientSpeed${Console.WHITE}")
      fastProducer(toSend, batch, sent + batch)
    }
  }

}

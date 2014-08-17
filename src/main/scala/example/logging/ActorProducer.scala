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

package akka.stream.actor.logging

import akka.actor.ActorLogging
import akka.stream.actor.{ ActorProducer => OrigActorProducer }

trait ActorProducer[T] extends OrigActorProducer[T] with ActorLogging {
  protected[akka] override def aroundReceive(receive: Receive, msg: Any): Unit = msg match {
    case OrigActorProducer.Request(n) =>
      log.info(s"Received Request(${Console.YELLOW}$n${Console.WHITE}) - [Total Demand] ${Console.YELLOW}$totalDemand${Console.WHITE}")
      super.aroundReceive(receive, msg)

    case _ =>
      super.aroundReceive(receive, msg)
  }
}

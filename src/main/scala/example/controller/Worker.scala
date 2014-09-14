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

package cakesolutions.example.controller

import akka.actor.{ActorRef, ActorLogging}
import akka.stream.actor.ActorSubscriberMessage.OnNext
import akka.stream.actor.{ZeroRequestStrategy, ActorSubscriber}
import akka.stream.actor.logging.ActorPublisher
import cakesolutions.example.ClusterMessages.{Ping, Message}

class Worker
  extends ActorSubscriber
  with ActorPublisher[Message]
  with ActorLogging {

  override val requestStrategy = ZeroRequestStrategy

  request(1)

  def receive = {
    case OnNext((ping: Ping, worker: ActorRef)) =>
      log.info(s"Sending $ping to $worker - [Total Demand] ${Console.YELLOW}$totalDemand${Console.RESET}")
      worker ! ping

    case msg: Message if (isActive && totalDemand > 0) =>
      log.info(s"Producing $msg to stream - [Total Demand] ${Console.YELLOW}$totalDemand${Console.RESET}")
      onNext(msg)
      request(1)

    case msg: Message if (isActive && totalDemand == 0) =>
      log.warning(s"No demand - requeuing $msg")
      self ! msg
  }

}

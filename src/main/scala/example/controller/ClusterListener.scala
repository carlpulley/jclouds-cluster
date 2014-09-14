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

package controller

import akka.actor.{Cancellable, ActorLogging}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.{CurrentClusterState, MemberExited, MemberUp, MemberEvent}
import akka.cluster.MemberStatus
import akka.stream.actor.logging.ActorPublisher
import scala.concurrent.duration._

class ClusterListener
  extends ActorLogging
  with ActorPublisher[MemberEvent]
  with Configuration {

  import context.dispatcher

  val cluster = Cluster(context.system)

  var membershipScheduler: Option[Cancellable] = None

  override def preStart() = {
    // Ensure we are subscribed for member up and exited events (i.e. allow introduction and removal of worker nodes)
    cluster.subscribe(self, classOf[MemberUp], classOf[MemberExited])

    // Ensure that any (lost/unseen) cluster membership information can be recovered
    cluster registerOnMemberUp {
      val updateDuration = config.getDuration("controller.update", SECONDS).seconds
      membershipScheduler = Some(context.system.scheduler.schedule(0.seconds, updateDuration) {
        cluster.sendCurrentClusterState(self)
      })
    }
  }

  override def postStop() = {
    cluster.unsubscribe(self)
    membershipScheduler.map(_.cancel)
    membershipScheduler = None
  }

  def receive = {
    case state: CurrentClusterState =>
      log.info(s"Received: $state")
      for (mem <- state.members.filter(_.status == MemberStatus.Up)) {
        self ! MemberUp(mem)
      }
      for (mem <- state.members.filter(m => List(MemberStatus.Exiting, MemberStatus.Removed).contains(m.status))) {
        self ! MemberExited(mem)
      }

    case msg: MemberUp if (isActive && totalDemand > 0) =>
      log.info(s"Received: $msg - [Total Demand] ${Console.YELLOW}$totalDemand${Console.RESET}")
      onNext(msg)

    case msg: MemberUp =>
      log.warning(s"No demand - ignoring: $msg")

    case msg: MemberExited if (isActive && totalDemand > 0) =>
      log.info(s"Received: $msg - [Total Demand] ${Console.YELLOW}$totalDemand${Console.RESET}")
      onNext(msg)

    case msg: MemberExited =>
      log.warning(s"No demand - ignoring: $msg")
  }
}

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
import akka.cluster.Cluster
import akka.cluster.MemberStatus
import akka.event.LoggingReceive
import scala.util.Random

class ClusterNode extends Actor {
  import ClusterMessages._

  val cluster = Cluster(context.system)

  def receive = LoggingReceive {
    case Ping(msg, tag) =>
      val route = s"$tag-${self.path.name}"

      if (Random.nextBoolean) {
        // We process the message...
        sender ! Pong(s"$route says $msg")
      } else {
        // We pass the message onto another (random) node for processing
        val nodes = cluser.state.members.filter(_.status == MemberStatus.Up)
        context.actorSelection(nodes(Random.nextInt(nodes.size)).address).tell(Ping(msg, route), sender)
      }
  }
}

class ClusterNodeApplication extends Bootable {
  implicit val system = ActorSystem("JCloudsClustering")

  def startup = {
    // We simply listen for remote actor creation events by default here
  }

  def shutdown = {
    system.shutdown
  }
}

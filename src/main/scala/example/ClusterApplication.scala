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

import akka.actor.ActorDSL._
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.AddressFromURIString
import akka.actor.Deploy
import akka.actor.Props
import akka.cluster.Cluster
import akka.cluster.MemberStatus
import akka.remote.RemoteScope
import com.typesafe.config.ConfigFactory
import scala.sys.process._
import scala.util.Random

class ClusterApplication(nodes: Map[String, Int]) {
  import ClusterMessages._

  val config = ConfigFactory.load()
  
  val systemName = config.getString("akka.system")

  implicit val system = ActorSystem(systemName)

  val cluster = Cluster(system)
  val joinAddress = cluster.selfAddress
  // We first ensure that we've joined our cluster!
  cluster.join(joinAddress)

  // Here we shell-out to provision out cluster nodes
  val processes = for((label, port) <- nodes) yield {
    val AKKA_HOME = "pwd".!!.stripLineEnd + "/target/dist"
    val jarFiles = s"ls $AKKA_HOME/lib/".!!.split("\n").map(AKKA_HOME + "/lib/" + _).mkString(":")

    Process(s"""java -Xms256M -Xmx1024M -XX:+UseParallelGC -classpath "$AKKA_HOME/config:$jarFiles" -Dakka.home=$AKKA_HOME -Dakka.remote.netty.tcp.port=$port akka.kernel.Main cakesolutions.example.ClusterNodeApplication""").run
  }

  def startup: ActorRef = {
    // Wire up and build our cluster
    val addresses = for((label, port) <- nodes) yield {
      val address = AddressFromURIString(s"akka.tcp://${systemName}@127.0.0.1:${port}")
      system.actorOf(Props[ClusterNode].withDeploy(Deploy(scope = RemoteScope(address))), name = label)
    }
  
    for (node <- addresses) {
      node ! Client(joinAddress)
    }
  
    // Ideally, we should subscribe to cluster MemberEvent's here - i.e. maintain valid member up status
    actor(new Act with ActorLogging {
      become {
        case msg @ Ping(_, _) =>
          assert(addresses.nonEmpty)
  
          addresses.toList(Random.nextInt(addresses.size)) ! msg
  
        case Pong(msg) =>
          log.info(msg)
      }
    })
  }

  def shutdown = {
    for(proc <- processes) {
      proc.destroy
    }
    system.shutdown
  }
}

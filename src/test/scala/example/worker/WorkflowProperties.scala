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

package worker

import akka.actor.ActorDSL._
import akka.actor.ActorSystem
import akka.actor.Props
import akka.stream.actor.ActorPublisher
import akka.stream.actor.ActorSubscriber
import akka.stream.MaterializerSettings
import akka.stream.scaladsl2.FlowFrom
import akka.stream.scaladsl2.FlowMaterializer
import akka.stream.testkit.StreamTestKit
import org.scalacheck._
import org.scalacheck.Prop

object WorkflowProperties
  extends Properties("WorkerWorkflow")
  with Generators
  with Configuration {

  import ClusterMessages._

  val materializerSettings = MaterializerSettings(config.getConfig("client.materializer"))

  implicit val system = ActorSystem("TestSystem")
  implicit val materializer = FlowMaterializer(materializerSettings)

  property("Message Conservation: return message flow from worker controller to worker controller") = {
    Prop.forAllNoShrink(PingListGenerator) { msgs =>
      val workerProbe = StreamTestKit.SubscriberProbe[Message]()
      val worker = system.actorOf(Props[controller.Worker], "worker")
      val mockWorker = actor(new Act {
        become {
          case msg =>
            sender() ! msg
        }
      })

      FlowFrom(msgs.map((_, mockWorker)))
        .publishTo(ActorSubscriber(worker))

      FlowFrom(ActorPublisher[Message](worker))
        .publishTo(workerProbe)

      val sub = workerProbe.expectSubscription()
      sub.request(msgs.length)
      for (msg <- msgs) {
        workerProbe.expectNext(msg)
      }

      system.stop(worker)
      system.stop(mockWorker)

      true
    }
  }

}

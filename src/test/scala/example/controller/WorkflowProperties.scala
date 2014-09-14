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

import akka.actor._
import akka.http.model.HttpEntity.{ChunkStreamPart, Chunk}
import akka.stream.MaterializerSettings
import akka.stream.scaladsl2.FlowFrom
import akka.stream.scaladsl2.FlowMaterializer
import akka.stream.testkit.StreamTestKit
import akka.testkit.TestActorRef
import akka.testkit.TestProbe
import akka.util.ByteString
import org.scalacheck._
import org.scalacheck.Prop
import org.scalacheck.Prop.BooleanOperators

object WorkflowProperties
  extends Properties("ControllerWorkflow")
  with Generators
  with Configuration {

  import ClusterMessages._

  val materializerSettings = MaterializerSettings(config.getConfig("client.materializer"))

  implicit val system = ActorSystem("TestSystem")
  implicit val materializer = FlowMaterializer(materializerSettings)

  property("Message Conservation: message request flow from HTTP stream to worker") = {
    Prop.forAllNoShrink (PingListGenerator) { testMsgs =>
      val worker = TestProbe()
      val workerPath = system.actorSelection(worker.ref.path)
      val workflowActor = TestActorRef(new Actor with ActorLogging with controller.Workflows with Serializer {
        def receive = Actor.emptyBehavior
      }).underlyingActor
      val serialization = workflowActor.serialization

      val workflow = workflowActor.controllerRequest
      val workerProbe = StreamTestKit.SubscriberProbe[(Ping, ActorRef)]()

      FlowFrom(testMsgs.map(msg => Chunk(ByteString(workflowActor.serialize((msg, workerPath)))).asInstanceOf[ChunkStreamPart]))
        .publishTo(workflowActor.clientIn.subscriber(workflow))

      FlowFrom(workflowActor.workerOut.publisher(workflow))
        .publishTo(workerProbe)

      val sub = workerProbe.expectSubscription()
      sub.request(testMsgs.length)
      for (msg <- testMsgs) {
        workerProbe.expectNext((msg, worker.ref))
      }
      workerProbe.expectComplete()

      true
    }
  }

  property("Message Conservation: message response flow from worker to HTTP stream") = {
    Prop.forAllNoShrink (MessageListGenerator, MemberUpExitedGenerator) { (testWorkerMsgs, testClusterMsgs) =>
      (testWorkerMsgs.length + testClusterMsgs.length > 0) ==> {
        val workflowActor = TestActorRef(new Actor with ActorLogging with controller.Workflows with Serializer {
          def receive = Actor.emptyBehavior
        }).underlyingActor
        val serialization = workflowActor.serialization

        val workflow = workflowActor.controllerResponse
        val clientProbe = StreamTestKit.SubscriberProbe[Chunk]()
        val expectedResult =
          for {msg <- testWorkerMsgs ++ testClusterMsgs} yield Chunk(serialization.serialize(msg).get)

        FlowFrom(testWorkerMsgs)
          .publishTo(workflowActor.workerIn.subscriber(workflow))

        FlowFrom(testClusterMsgs)
          .publishTo(workflowActor.clusterIn.subscriber(workflow))

        FlowFrom(workflowActor.clientOut.publisher(workflow))
          .map(_.asInstanceOf[Chunk])
          .publishTo(clientProbe)

        val sub = clientProbe.expectSubscription()
        sub.request(testWorkerMsgs.length + testClusterMsgs.length)
        val actualResult = clientProbe.receiveN(testWorkerMsgs.length + testClusterMsgs.length)

        actualResult.toSet == expectedResult.toSet

        true
      }
    }
  }

}

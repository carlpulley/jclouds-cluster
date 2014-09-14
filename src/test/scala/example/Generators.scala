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

import akka.cluster.ClusterEvent.{MemberEvent, MemberExited, MemberUp}
import akka.cluster.{MemberStatus, ClusterTestKit}
import org.scalacheck.Gen

trait Generators {

  import ClusterMessages._

  val PingGenerator =
    for {
      msg <- Gen.alphaStr
      tag <- Gen.alphaStr
    } yield Ping(msg, tag)

  val PongGenerator =
    for {
      reply <- Gen.alphaStr
    } yield Pong(reply)

  val MessageGenerator =
    Gen.frequency[Message](
      3 -> PingGenerator,
      1 -> PongGenerator
    )

  val PingListGenerator =
    Gen.nonEmptyContainerOf[List,Ping](PingGenerator)

  val MessageListGenerator =
    Gen.containerOf[List, Message](MessageGenerator)

  val MemberUpGenerator =
    for {
      member <- ClusterTestKit.MemberGenerator(MemberStatus.Up)
    } yield MemberUp(member)

  val MemberExitedGenerator =
    for {
      member <- ClusterTestKit.MemberGenerator(MemberStatus.Exiting)
    } yield MemberExited(member)

  val MemberUpExitedGenerator =
    Gen.containerOf[List, MemberEvent](Gen.frequency(
      3 -> MemberUpGenerator,
      1 -> MemberExitedGenerator
    ))

}

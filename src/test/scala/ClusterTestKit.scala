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

package akka.cluster

import akka.actor.Address
import akka.cluster.MemberStatus._
import cakesolutions.example.Configuration
import org.scalacheck._

object ClusterTestKit extends Configuration {

  implicit lazy val arbitraryMemberStatus = Arbitrary[MemberStatus](Gen.oneOf(Joining, Up, Leaving, Exiting, Down, Removed))

  val AddressGenerator =
    for {
      protocol <- Gen.alphaStr
      system   <- Gen.alphaStr
      host     <- Gen.alphaStr
      port     <- Gen.choose(0, 65535)
    } yield Address(protocol, system, host, port)

  val UniqueAddressGenerator =
    for {
      address <- AddressGenerator
      uid <- Arbitrary.arbitrary[Int]
    } yield UniqueAddress(address, uid)

  def MemberGenerator(status: MemberStatus) =
    for {
      uniqueAddress <- UniqueAddressGenerator
      upNumber      <- Arbitrary.arbitrary[Int]
      roles         <- Gen.containerOf[Set, String](Gen.alphaStr)
    } yield new Member(uniqueAddress, upNumber, status, roles)

}

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

package cakesolutions

package api

package deltacloud

import akka.actor.ActorSystem
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import scala.concurrent.duration._
import scala.concurrent.Future
import spray.can.Http
import spray.http._
import spray.client.pipelining._

trait Classic {

  implicit val system: ActorSystem

  import system.dispatcher

  val config = ConfigFactory.load()

  implicit val timeout = Timeout(config.getInt("deltacloud.timeout").seconds)

  val deltacloudPipeline: Future[SendReceive] =
    for (
      Http.HostConnectorInfo(connector, _) <- IO(Http) ? Http.HostConnectorSetup(config.getString("deltacloud.host"), port = config.getInt("deltacloud.port"))
    ) yield sendReceive(connector)
  val pipeline = (request: HttpRequest) => deltacloudPipeline.flatMap(_(request))

  object Addresses extends methods.Address(pipeline)

  object Buckets extends methods.Bucket(pipeline)

  object Driver extends methods.Driver(pipeline)

  object Firewalls extends methods.Firewall(pipeline)

  object HardwareProfiles extends methods.HardwareProfile(pipeline)

  object Images extends methods.Image(pipeline)

  object InstanceStates extends methods.InstanceState(pipeline)

  object Instances extends methods.Instance(pipeline)

  object Keys extends methods.Key(pipeline)

  object LoadBalancers extends methods.LoadBalancer(pipeline)

  object Metrics extends methods.Metric(pipeline)

  object NetworkInterfaces extends methods.NetworkInterface(pipeline)

  object Networks extends methods.Network(pipeline)

  object Realms extends methods.Realm(pipeline)

  object StorageSnapshots extends methods.StorageSnapshot(pipeline)

  object StorageVolumes extends methods.StorageVolume(pipeline)

  object Subnets extends methods.Subnet(pipeline)

}

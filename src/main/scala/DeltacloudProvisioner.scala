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

import akka.actor.ActorSystem
import akka.actor.Address
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import cakesolutions.api.deltacloud
import cakesolutions.api.deltacloud.Instance
import cakesolutions.api.deltacloud.Realm
import com.typesafe.config.ConfigFactory
import scala.concurrent.duration._
import scala.concurrent.Future
import scala.io.Source
import scala.util.Failure
import scala.util.Success
import spray.can.Http
import spray.http._
import spray.client.pipelining._

class DeltacloudProvisioner(val label: String, joinAddress: Option[Address] = None)(implicit system: ActorSystem) {
  import system.dispatcher

  val config = ConfigFactory.load()
  val host = config.getString("deltacloud.host")
  val port = config.getInt("deltacloud.port")
  val driver = config.getString("deltacloud.driver")

  implicit val timeout = Timeout(config.getInt("deltacloud.timeout").minutes)

  implicit val deltacloudHttpClient = (request: HttpRequest) => 
    (IO(Http) ? (request, Http.HostConnectorSetup(host, port = port))).mapTo[HttpResponse]

  var node: Option[Instance] = None

  def bootstrap(user_data: String): Future[Unit] = {
    if (node.isEmpty) {
      for {
        realms <- Realm.index(state = Some("available"))
        vm <- Instance.create(
          image_id = config.getString(s"deltacloud.$driver.image"),
          keyname = Some(config.getString(s"deltacloud.$driver.keyname")),
          realm_id = Some(realms.head.id),
          hwp_id = Some(config.getString(s"deltacloud.$driver.hwp")),
          user_data = Some(user_data)
        )
      } yield {
        node = Some(vm)
      }
    } else {
      Future { () }
    }
  }

  // As we can't guarantee that the stored created instance (in node) has the current IP address 
  // information, this method allows a secondary lookup
  def private_addresses: Future[List[deltacloud.Address]] = {
    require(node.nonEmpty)

    Instance.show(node.get.id).map(vm => vm.private_addresses)
  }

  def shutdown: Future[Unit] = { 
    node match {
      case Some(n) =>
        for {
          _ <- Instance.stop(n.id)
          _ <- Instance.destroy(n.id)
        } yield {
          node = None
          ()
        }
  
      case None =>
        Future { () }
    }
  }
}

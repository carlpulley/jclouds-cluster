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
import akka.http.Http
import akka.http.model.HttpRequest
import akka.http.model.HttpResponse
import akka.io.IO
import akka.pattern.ask
import akka.stream.FlowMaterializer
import akka.stream.MaterializerSettings
import akka.stream.scaladsl.Flow
import akka.util.Timeout
import cakesolutions.api.deltacloud
import cakesolutions.api.deltacloud.Instance
import cakesolutions.api.deltacloud.Realm
import com.typesafe.config.ConfigFactory
import scala.concurrent.duration._
import scala.concurrent.Future

class DeltacloudProvisioner(val label: String, joinAddress: Option[Address] = None)(implicit val system: ActorSystem) {

  import system.dispatcher

  val config       = ConfigFactory.load()
  val host         = config.getString("deltacloud.host")
  val port         = config.getInt("deltacloud.port")
  val driver       = config.getString("deltacloud.driver")
  
  implicit val materializer = FlowMaterializer(MaterializerSettings())

  implicit val timeout = Timeout(config.getInt("deltacloud.timeout").minutes)

  implicit val deltacloudHttpClient = (request: HttpRequest) =>
    (IO(Http) ? Http.Connect(host, port = port)).flatMap {
      case connect: Http.OutgoingConnection =>
        Flow(List(request -> 'NoContext)).produceTo(connect.processor)
        Flow(connect.processor).map(_._1).toFuture()
    }.mapTo[HttpResponse]

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

  def public_addresses: Future[List[deltacloud.Address]] = {
    require(node.nonEmpty)

    Instance.show(node.get.id).map(vm => vm.public_addresses)
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

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

package methods

import scala.concurrent.Future
import spray.http._
import spray.http.Uri.Query
import spray.client.pipelining._

class Firewall(pipeline: HttpRequest => Future[HttpResponse]) {
  def index(id: Option[String] = None) = pipeline(Get(Uri("/api/firewalls").copy(query = Query(Map(
    "id" -> id
  ).flatMap(kv => kv._2.map(v => (kv._1 -> v)))))))

  def show(id: String) = pipeline(Get(s"/api/firewalls/$id"))

  def create(
    name: String, 
    description: Option[String] = None
  ) = pipeline(Post("/api/firewalls", FormData(Seq(
        "name" -> Some(name), 
        "description" -> description
  ).flatMap(kv => kv._2.map(v => (kv._1 -> v))))))

  def destroy(id: String) = pipeline(Delete(s"/api/firewalls/$id"))

  def new_rule(
    id: String, 
    protocol: String, 
    port_from: Int, 
    port_to: Int
  ) = pipeline(Post(s"/api/firewalls/$id/rules", FormData(Map(
    "protocol" -> protocol,
    "port_from" -> port_from.toString,
    "port_to" -> port_to.toString
  ))))
}

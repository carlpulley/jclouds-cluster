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

class LoadBalancer(pipeline: HttpRequest => Future[HttpResponse]) {
  def index() = pipeline(Get(Uri("/api/load_balancers")))

  def show(id: String) = pipeline(Get(s"/api/load_balancers/$id"))

  def create(
    name: String,
    realm_id: String,
    listener_protocol: String,
    listener_balancer_port: Int,
    listener_instance_port: Int
  ) = pipeline(Post("/api/load_balancers", FormData(Seq(
        "name" -> name, 
        "realm_id" -> realm_id,
        "listener_protocol" -> listener_protocol,
        "listener_balancer_port" -> listener_balancer_port.toString,
        "listener_instance_port" -> listener_instance_port.toString
  ))))

  def destroy(id: String) = pipeline(Delete(s"/api/load_balancers/$id"))

  def register(id: String, instance_id: String) = pipeline(Post(s"/api/load_balancers/$id/register", FormData(Map("instance_id" -> instance_id))))

  def unregister(id: String, instance_id: String) = pipeline(Post(s"/api/load_balancers/$id/unregister", FormData(Map("instance_id" -> instance_id))))
}

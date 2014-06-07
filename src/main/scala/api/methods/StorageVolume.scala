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

class StorageVolume(pipeline: HttpRequest => Future[HttpResponse]) {
  def show(id: String) = pipeline(Get(s"/api/storage_volumes/$id"))

  def index(
    id: Option[String] = None, 
    state: Option[String] = None
  ) = pipeline(Get(Uri("/api/storage_volumes").copy(query = Query(Map(
    "id" -> id,
    "state" -> state
  ).flatMap(kv => kv._2.map(v => (kv._1 -> v)))))))

  def create(
    snapshot_id: Option[String] = None,
    capacity: Option[String] = None,
    realm_id: Option[String] = None,
    name: Option[String] = None,
    description: Option[String] = None
  ) = pipeline(Post("/api/storage_volumes", FormData(Seq(
        "snapshot_id" -> snapshot_id, 
        "capacity" -> capacity, 
        "realm_id" -> realm_id, 
        "name" -> name, 
        "description" -> description
  ).flatMap(kv => kv._2.map(v => (kv._1 -> v))))))

  def destroy(id: String) = pipeline(Delete(s"/api/storage_volumes/$id"))

  def attach(
    id: String,
    instance_id: String,
    device: Option[String] = None
  ) = pipeline(Post(s"/api/storage_volumes/$id/attach", FormData(Seq(
        "instance_id" -> Some(instance_id), 
        "device" -> device
  ).flatMap(kv => kv._2.map(v => (kv._1 -> v))))))

  def detach(id: String) = pipeline(Post(s"/api/storage_volumes/$id/detach"))
}

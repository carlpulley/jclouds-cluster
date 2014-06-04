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

class StorageSnapshot(pipeline: HttpRequest => Future[HttpResponse]) {
  def show(id: String) = pipeline(Get(s"/api/storage_snapshots/$id"))

  def index() = pipeline(Get(Uri("/api/storage_snapshots")))

  def create(
    volume_id: String,
    name: Option[String] = None,
    description: Option[String] = None
  ) = pipeline(Post("/api/storage_snapshots", FormData(Seq(
        "volume_id" -> Some(volume_id), 
        "name" -> name, 
        "description" -> description
  ).flatMap(kv => kv._2.map(v => (kv._1 -> v))))))

  def destroy(id: String) = pipeline(Delete(s"/api/storage_snapshots/$id"))
}

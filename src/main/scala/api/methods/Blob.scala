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

class Blob(pipeline: HttpRequest => Future[HttpResponse]) {
  def show(id: String, blob_id: String) = pipeline(Get(s"/api/buckets/$id/$blob_id"))

  def create(
    id: String,
    blob_id: String,
    blob_data: String // FIXME: type Hash??
  ) = pipeline(Post("/api/buckets", FormData(Seq(
        "id" -> id, 
        "blob_id" -> blob_id,
        "blob_data" -> blob_data
  ))))

  def destroy(id: String, blob_id: String) = pipeline(Delete(s"/api/buckets/$id/$blob_id"))

  def stream(id: String, blob_id: String) = pipeline(Put(s"/api/buckets/$id/$blob_id/stream"))

  def metadata(id: String, blob_id: String) = pipeline(Head(s"/api/buckets/$id/$blob_id/metadata"))

  def update(id: String, blob_id: String) = pipeline(Post(s"/api/buckets/$id/$blob_id"))

  def content(id: String, blob_id: String) = pipeline(Head(s"/api/buckets/$id/$blob_id/content"))
}
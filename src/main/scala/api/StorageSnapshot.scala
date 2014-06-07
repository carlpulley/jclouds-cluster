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

import scala.concurrent.Future
import spray.http._
import spray.http.Uri.Query
import spray.client.pipelining._
import xml.NodeSeq

object StorageSnapshot {
  def show(id: String)(implicit pipeline: HttpRequest => Future[HttpResponse]) = pipeline(Get(s"/api/storage_snapshots/$id"))

  def index(id: Option[String] = None)(implicit pipeline: HttpRequest => Future[HttpResponse]) = pipeline(Get(Uri("/api/storage_snapshots").copy(query = Query(Map(
    "id" -> id
  ).flatMap(kv => kv._2.map(v => (kv._1 -> v)))))))

  def create(
    volume_id: String,
    name: Option[String] = None,
    description: Option[String] = None
  )(implicit pipeline: HttpRequest => Future[HttpResponse]) = pipeline(Post("/api/storage_snapshots", FormData(Seq(
        "volume_id" -> Some(volume_id), 
        "name" -> name, 
        "description" -> description
  ).flatMap(kv => kv._2.map(v => (kv._1 -> v))))))

  def destroy(id: String)(implicit pipeline: HttpRequest => Future[HttpResponse]) = pipeline(Delete(s"/api/storage_snapshots/$id"))
}
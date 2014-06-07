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
import xml.NodeSeq

object Realm {
  def index(
    id: Option[String] = None, 
    state: Option[String] = None
  )(implicit pipeline: HttpRequest => Future[HttpResponse]) = pipeline(Get(Uri("/api/realms").copy(query = Query(Map(
    "id" -> id,
    "state" -> state
  ).flatMap(kv => kv._2.map(v => (kv._1 -> v)))))))

  def show(id: String)(implicit pipeline: HttpRequest => Future[HttpResponse]) = pipeline(Get(s"/api/realms/$id"))
}
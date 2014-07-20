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

import akka.http.model.ContentType
import akka.http.model.FormData
import akka.http.model.HttpEntity
import akka.http.model.HttpEntity.Strict
import akka.http.model.HttpMethods._
import akka.http.model.HttpRequest
import akka.http.model.HttpResponse
import akka.http.model.MediaTypes._
import akka.http.model.Uri
import akka.http.model.Uri.Query
import akka.stream.FlowMaterializer
import akka.util.ByteString
import akka.util.Timeout
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.xml.NodeSeq
import scala.xml.XML

case class Transition(
  to: String,
  action: Option[String],
  auto: Option[Boolean]
)

case class InstanceState(
  name: String,
  transitions: List[Transition]
)

object InstanceState {

  def xmlToTransition(data: NodeSeq): Transition = {
    val action = (data \ "@action").text
    val auto = (data \ "@auto").text
    val to = (data \ "@to").text
  
    Transition(to, if (action.isEmpty) None else Some(action), if (auto.isEmpty) None else Some(auto.toBoolean))
  }

  def xmlToInstanceState(data: NodeSeq): InstanceState = {
    val name = (data \ "@name").text
    val transitions = (data \ "transition").map(xmlToTransition).toList
  
    InstanceState(name, transitions)
  }

  def strictToInstanceState(dataStr: Strict): InstanceState = {
    val data = XML.loadString(dataStr.data.utf8String)
    xmlToInstanceState(data)
  }

  def strictToInstanceStateList(dataStr: Strict): List[InstanceState] = {
    val data = XML.loadString(dataStr.data.utf8String)
    (data \ "state").map(xmlToInstanceState).toList
  }

  def index()(implicit ec: ExecutionContext, pipeline: HttpRequest => Future[HttpResponse]) = 
    pipeline(HttpRequest(GET, uri = Uri("/api/instance_states")))

  def show(name: String)(implicit ec: ExecutionContext, pipeline: HttpRequest => Future[HttpResponse]) = 
    pipeline(HttpRequest(GET, uri = Uri(s"/api/instance_states/$name")))

}

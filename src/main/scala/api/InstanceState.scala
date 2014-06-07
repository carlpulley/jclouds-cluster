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

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import spray.http._
import spray.http.MediaTypes._
import spray.http.Uri.Query
import spray.httpx.TransformerAux.aux2
import spray.httpx.unmarshalling._
import spray.client.pipelining._
import xml.NodeSeq

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

  implicit val unmarshalInstanceState = 
    Unmarshaller.delegate[NodeSeq, InstanceState](`text/xml`, `application/xml`, `text/html`, `application/xhtml+xml`)(xmlToInstanceState)

  implicit val unmarshalInstanceStates = 
    Unmarshaller.delegate[NodeSeq, List[InstanceState]](`text/xml`, `application/xml`, `text/html`, `application/xhtml+xml`) { data => 
      (data \ "state").map(xmlToInstanceState(_)).toList
    }

  def index()(implicit ec: ExecutionContext, pipeline: HttpRequest => Future[HttpResponse]) = 
    (pipeline ~> unmarshal[List[InstanceState]])(aux2)(Get(Uri("/api/instance_states")))

  def show(name: String)(implicit ec: ExecutionContext, pipeline: HttpRequest => Future[HttpResponse]) = 
    (pipeline ~> unmarshal[InstanceState])(aux2)(Get(Uri(s"/api/instance_states/$name")))

}

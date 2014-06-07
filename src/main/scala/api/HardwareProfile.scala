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

sealed trait Property
case class Fixed(name: String, unit: Option[String], value: String) extends Property
case class Enum(name: String, unit: Option[String], values: List[String]) extends Property
case class Range(name: String, unit: Option[String], first: String, last: String, default: Option[String] = None) extends Property

case class HardwareProfile(
  id: String,
  properties: List[Property]
)

object HardwareProfile {

  def xmlToProperty(data: NodeSeq): Property = (data \ "@kind").text match {
    case "fixed" =>
      val name = (data \ "@name").text
      val unit = (data \ "@unit").text
      val value = (data \ "@value").text

      Fixed(name, if (unit.isEmpty) None else Some(unit), value)

    case "enum" =>
      val name = (data \ "@name").text
      val unit = (data \ "@unit").text
      val values = (data \ "enum" \ "entry").map(n => (n \ "@value").text).toList

      Enum(name, if (unit.isEmpty) None else Some(unit), values)

    case "range" =>
      val name = (data \ "@name").text
      val unit = (data \ "@unit").text
      val first = (data \ "@first").text
      val last = (data \ "@last").text
      val default = (data \ "@default").text

      Range(name, if (unit.isEmpty) None else Some(unit), first, last, if (default.isEmpty) None else Some(default))
  }

  def xmlToHardwareProfile(data: NodeSeq): HardwareProfile = {
    val id = (data \ "id").text
    val properties = (data \ "property").map(xmlToProperty).toList
  
    HardwareProfile(id, properties)
  }

  implicit val unmarshalHardwareProfile = 
    Unmarshaller.delegate[NodeSeq, HardwareProfile](`text/xml`, `application/xml`, `text/html`, `application/xhtml+xml`)(xmlToHardwareProfile)

  implicit val unmarshalHardwareProfiles = 
    Unmarshaller.delegate[NodeSeq, List[HardwareProfile]](`text/xml`, `application/xml`, `text/html`, `application/xhtml+xml`) { data => 
      (data \ "hardware_profile").map(xmlToHardwareProfile(_)).toList
    }

  def index(id: Option[String] = None)(implicit ec: ExecutionContext, pipeline: HttpRequest => Future[HttpResponse]) = 
    (pipeline ~> unmarshal[List[HardwareProfile]])(aux2)(Get(Uri("/api/hardware_profiles").copy(query = Query(Map(
      "id" -> id
    ).flatMap(kv => kv._2.map(v => (kv._1 -> v)))))))

  def show(id: String)(implicit ec: ExecutionContext, pipeline: HttpRequest => Future[HttpResponse]) = 
    (pipeline ~> unmarshal[HardwareProfile])(aux2)(Get(s"/api/hardware_profiles/$id"))

}

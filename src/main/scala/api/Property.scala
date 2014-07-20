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

import scala.xml.NodeSeq

sealed trait Property
case class Fixed(name: String, unit: Option[String], value: String) extends Property
case class Enum(name: String, unit: Option[String], values: List[String]) extends Property
case class Range(name: String, unit: Option[String], first: String, last: String, default: Option[String] = None) extends Property

object Property {

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

}

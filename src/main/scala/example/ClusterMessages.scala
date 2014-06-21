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

package cakesolutions.example

import akka.actor.Address
import spray.json._
import spray.json.DefaultJsonProtocol

object ClusterMessages {
  sealed trait Message
  case class Ping(msg: String, tag: String = "") extends Message
  case class Pong(reply: String) extends Message

  case class ProvisionNode(label: String)
  case class ShutdownNode(label: String)
  case object GetMessages
}

object ClusterMessageFormats extends DefaultJsonProtocol {
  import ClusterMessages._

  implicit object MessageFormat extends RootJsonFormat[Message] {
    val PingFormat = jsonFormat2(Ping)
    val PongFormat = jsonFormat1(Pong)

    def write(obj: Message) = obj match {
      case ping: Ping => PingFormat.write(ping)
      case pong: Pong => PongFormat.write(pong)
    }

    def read(json: JsValue): Message = json.asJsObject.fields("kind") match {
      case JsString("Ping") => PingFormat.read(json)
      case JsString("Pong") => PongFormat.read(json)
      case msg => sys.error(s"Unexpected JSON Format: $msg")
    }
  }
}

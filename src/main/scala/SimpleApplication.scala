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

import akka.actor.ActorSystem
import akka.kernel.Bootable
import com.typesafe.config.ConfigFactory

class SimpleApplication extends Bootable {
  
  val configFactory = ConfigFactory.load("application.conf").withFallback(ConfigFactory.parseMap(Map("akka.remote.server.port" -> port)))
  implicit val system = ActorSystem(name, configFactory)
  
  Config.load("application.conf")
 
  def startup = {
    // Intentionally empty
  }

  def shutdown = {
    system.shutdown
  }

}

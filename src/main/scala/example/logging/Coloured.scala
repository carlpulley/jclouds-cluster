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

package logging

import akka.event.Logging._

class Coloured extends DefaultLogger {

  private val errorFormat = s"[${Console.BOLD}${Console.RED}ERROR${Console.RESET}] [${Console.GREEN}%s${Console.RESET}] [${Console.GREEN}%s${Console.RESET}] ${Console.RED}%s%s${Console.RESET}"
  private val errorFormatWithoutCause = s"[${Console.BOLD}${Console.RED}ERROR${Console.RESET}] [${Console.GREEN}%s${Console.RESET}] [${Console.GREEN}%s${Console.RESET}] ${Console.RED}%s${Console.RESET}"
  private val warningFormat = s"[${Console.BOLD}${Console.MAGENTA}WARN${Console.RESET}] [${Console.GREEN}%s${Console.RESET}] [${Console.GREEN}%s${Console.RESET}] ${Console.MAGENTA}%s${Console.RESET}"
  private val infoFormat = s"[${Console.BOLD}${Console.GREEN}INFO${Console.RESET}] [${Console.GREEN}%s${Console.RESET}] [${Console.GREEN}%s${Console.RESET}] ${Console.WHITE}%s${Console.RESET}"
  private val debugFormat = s"[${Console.BOLD}${Console.BLUE}DEBUG${Console.RESET}] [${Console.GREEN}%s${Console.RESET}] [${Console.GREEN}%s${Console.RESET}] ${Console.BLUE}%s${Console.RESET}"

  override def error(event: Error): Unit = {
    val f = if (event.cause == Error.NoCause) errorFormatWithoutCause else errorFormat
    Console.println(f.format(
      timestamp(event),
      event.logSource,
      event.message,
      stackTraceFor(event.cause)))
  }

  override def warning(event: Warning): Unit = {
    Console.println(warningFormat.format(
      timestamp(event),
      event.logSource,
      event.message))
  }

  override def info(event: Info): Unit = {
    Console.println(infoFormat.format(
      timestamp(event),
      event.logSource,
      event.message))
  }

  override def debug(event: Debug): Unit = {
    Console.println(debugFormat.format(
      timestamp(event),
      event.logSource,
      event.message))
  }

}

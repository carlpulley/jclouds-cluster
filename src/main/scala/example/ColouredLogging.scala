package cakesolutions.example

import akka.event.Logging._

class ColouredLogging extends DefaultLogger {

  private val errorFormat = s"[${Console.BOLD}${Console.RED}ERROR${Console.RESET}] [${Console.GREEN}%s${Console.RESET}] [${Console.GREEN}%s${Console.RESET}] [${Console.GREEN}%s${Console.RESET}] ${Console.RED}%s%s${Console.RESET}"
  private val errorFormatWithoutCause = s"[${Console.BOLD}${Console.RED}ERROR${Console.RESET}] [${Console.GREEN}%s${Console.RESET}] [${Console.GREEN}%s${Console.RESET}] [${Console.GREEN}%s${Console.RESET}] ${Console.RED}%s${Console.RESET}"
  private val warningFormat = s"[${Console.BOLD}${Console.MAGENTA}WARN${Console.RESET}] [${Console.GREEN}%s${Console.RESET}] [${Console.GREEN}%s${Console.RESET}] [${Console.GREEN}%s${Console.RESET}] ${Console.MAGENTA}%s${Console.RESET}"
  private val infoFormat = s"[${Console.BOLD}${Console.GREEN}INFO${Console.RESET}] [${Console.GREEN}%s${Console.RESET}] [${Console.GREEN}%s${Console.RESET}] [${Console.GREEN}%s${Console.RESET}] ${Console.GREEN}%s${Console.RESET}"
  private val debugFormat = s"[${Console.BOLD}${Console.BLUE}DEBUG${Console.RESET}] [${Console.GREEN}%s${Console.RESET}] [${Console.GREEN}%s${Console.RESET}] [${Console.GREEN}%s${Console.RESET}] ${Console.BLUE}%s${Console.RESET}"

  override def error(event: Error): Unit = {
    val f = if (event.cause == Error.NoCause) errorFormatWithoutCause else errorFormat
    Console.println(f.format(
      timestamp(event),
      event.thread.getName,
      event.logSource,
      event.message,
      stackTraceFor(event.cause)))
  }

  override def warning(event: Warning): Unit = {
    Console.println(warningFormat.format(
      timestamp(event),
      event.thread.getName,
      event.logSource,
      event.message))
  }

  override def info(event: Info): Unit = {
    Console.println(infoFormat.format(
      timestamp(event),
      event.thread.getName,
      event.logSource,
      event.message))
  }

  override def debug(event: Debug): Unit = {
    Console.println(debugFormat.format(
      timestamp(event),
      event.thread.getName,
      event.logSource,
      event.message))
  }

}

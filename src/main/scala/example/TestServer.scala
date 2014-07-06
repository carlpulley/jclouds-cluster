package cakesolutions.example

import com.typesafe.config.{ ConfigFactory, Config }
import scala.concurrent.duration._
import akka.stream.scaladsl.Flow
import akka.io.IO
import akka.util.Timeout
import akka.actor.ActorSystem
import akka.pattern.ask
import akka.http.model._
import akka.http.Http
import HttpMethods._
import akka.stream.{ MaterializerSettings, FlowMaterializer }

object TestServer {
  val testConf: Config = ConfigFactory.parseString("""
    akka.loglevel = DEBUG
    akka.log-dead-letters = off
    akka.remote.netty.tcp.port = 2553
    """)
  implicit val system = ActorSystem("ServerTest", testConf)
  import system.dispatcher

  val requestHandler: HttpRequest => HttpResponse = {
    case HttpRequest(GET, Uri.Path("/"), _, _, _) => 
      println("Request: /")
      index
    case HttpRequest(GET, Uri.Path("/ping"), _, _, _) => 
      println("Request: /ping")
      HttpResponse(entity = "PONG!")
    case HttpRequest(GET, Uri.Path("/crash"), _, _, _) => 
      println("Request: /crash")
      sys.error("BOOM!")
    case HttpRequest(GET, Uri.Path(path), _, _, _) => 
      println(s"Request: $path")
      HttpResponse(404, entity = "Unknown resource!")
  }

  val materializer = FlowMaterializer(MaterializerSettings())

  implicit val askTimeout: Timeout = 500.millis
def start = {
  val bindingFuture = IO(Http) ? Http.Bind(interface = "localhost", port = 8080)
  bindingFuture foreach {
    case Http.ServerBinding(localAddress, connectionStream) =>
      Flow(connectionStream).foreach {
        case Http.IncomingConnection(remoteAddress, requestProducer, responseConsumer) =>
          println("Accepted new connection from " + remoteAddress)
          Flow(requestProducer).map(requestHandler).produceTo(materializer, responseConsumer)
      }.consume(materializer)
  }

  println(s"Server online at http://localhost:8080")
}
  ////////////// helpers //////////////

  lazy val index = HttpResponse(
    entity = HttpEntity(MediaTypes.`text/html`,
      """|<html>
         | <body>
         |    <h1>Say hello to <i>akka-http-core</i>!</h1>
         |    <p>Defined resources:</p>
         |    <ul>
         |      <li><a href="/ping">/ping</a></li>
         |      <li><a href="/crash">/crash</a></li>
         |    </ul>
         |  </body>
         |</html>""".stripMargin))

}

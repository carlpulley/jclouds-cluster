package cakesolutions.example

import com.typesafe.config.{ ConfigFactory, Config }
import scala.concurrent.Future
import scala.util.{ Failure, Success }
import scala.concurrent.duration._
import akka.util.Timeout
import akka.stream.{ MaterializerSettings, FlowMaterializer }
import akka.stream.scaladsl.Flow
import akka.io.IO
import akka.actor.ActorSystem
import akka.pattern.ask
import akka.http.model._
import akka.http.Http
import HttpMethods._
import akka.util.ByteString

object TestClient {
  val testConf: Config = ConfigFactory.parseString("""
    akka.loglevel = DEBUG
    akka.log-dead-letters = off
    """)

  implicit val system = ActorSystem("ServerTest", testConf)
  import system.dispatcher

  val materializer = FlowMaterializer(MaterializerSettings())
  implicit val askTimeout: Timeout = 500.millis

  def sendRequest(request: HttpRequest, connection: Http.OutgoingConnection): Future[HttpResponse] = {
    Flow(List(request -> 'NoContext)).produceTo(materializer, connection.processor)
    Flow(connection.processor).map(_._1).toFuture(materializer)
  }

  def start(path: String) = {
    val result = for {
      connection <- IO(Http).ask(Http.Connect("localhost", port = 8080)).mapTo[Http.OutgoingConnection]
      response <- sendRequest(HttpRequest(GET, uri = Uri(path)), connection)
    } yield response.entity.dataBytes(materializer)
  
    result onComplete {
      case Success(res)   => Flow(res).foreach( (body: ByteString) => println(s"Response:\n${body.utf8String}") ).consume(materializer)
      case Failure(error) => println(s"Error: $error")
    }
  }
}

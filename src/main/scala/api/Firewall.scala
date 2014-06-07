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

case class Source(
  name: String,
  owner: String,
  typ: String
)

case class Rule(
  allow_protocol: String,
  port_from: Option[Int],
  port_to: Option[Int],
  direction: String,
  sources: List[Source]
)

case class Firewall(
  owner_id: String,
  rules: List[Rule]
)

object Firewall {

  def xmlToSource(data: NodeSeq): Source = {
    val name = (data \ "@name").text
    val owner = (data \ "@owner").text
    val typ = (data \ "@type").text

    Source(name, owner, typ)
  }

  def xmlToRule(data: NodeSeq): Rule = {
    val allow_protocol = (data \ "allow_protocol").text
    val port_from = (data \ "port_from").text
    val port_to = (data \ "port_to").text
    val direction = (data \ "direction").text
    val sources = (data \ "sources" \\ "source").map(xmlToSource).toList

    Rule(allow_protocol, if (port_from.isEmpty || port_from.toInt < 0) None else Some(port_from.toInt), if (port_to.isEmpty || port_to.toInt < 0) None else Some(port_to.toInt), direction, sources)
  }

  def xmlToFirewall(data: NodeSeq): Firewall = {
    val owner_id = (data \ "owner_id").text
    val rules = (data \ "rules" \ "rule").map(xmlToRule).toList
  
    Firewall(owner_id, rules)
  }

  implicit val unmarshalFirewall = 
    Unmarshaller.delegate[NodeSeq, Firewall](`text/xml`, `application/xml`, `text/html`, `application/xhtml+xml`)(xmlToFirewall)

  implicit val unmarshalFirewalls = 
    Unmarshaller.delegate[NodeSeq, List[Firewall]](`text/xml`, `application/xml`, `text/html`, `application/xhtml+xml`) { data => 
      (data \ "firewall").map(xmlToFirewall).toList
    }

  def index(id: Option[String] = None)(implicit ec: ExecutionContext, pipeline: HttpRequest => Future[HttpResponse]) = 
    (pipeline ~> unmarshal[List[Firewall]])(aux2)(Get(Uri("/api/firewalls").copy(query = Query(Map(
      "id" -> id
    ).flatMap(kv => kv._2.map(v => (kv._1 -> v)))))))

  def show(id: String)(implicit ec: ExecutionContext, pipeline: HttpRequest => Future[HttpResponse]) = 
    (pipeline ~> unmarshal[Firewall])(aux2)(Get(s"/api/firewalls/$id"))

  def create(
    name: String, 
    description: Option[String] = None
  )(implicit ec: ExecutionContext, pipeline: HttpRequest => Future[HttpResponse]) = 
    (pipeline ~> unmarshal[Firewall])(aux2)(Post("/api/firewalls", FormData(Seq(
        "name" -> Some(name), 
        "description" -> description
    ).flatMap(kv => kv._2.map(v => (kv._1 -> v))))))

  def destroy(id: String)(implicit pipeline: HttpRequest => Future[HttpResponse]) = 
    pipeline(Delete(s"/api/firewalls/$id"))

  def new_rule(
    id: String, 
    protocol: String, 
    port_from: Int, 
    port_to: Int
  )(implicit pipeline: HttpRequest => Future[HttpResponse]) = 
    pipeline(Post(s"/api/firewalls/$id/rules", FormData(Map(
      "protocol" -> protocol,
      "port_from" -> port_from.toString,
      "port_to" -> port_to.toString
    ))))

}

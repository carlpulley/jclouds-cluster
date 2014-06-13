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
import akka.actor.Address
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import cakesolutions.api.deltacloud.Instance
import cakesolutions.api.deltacloud.Realm
import com.typesafe.config.ConfigFactory
import scala.concurrent.duration._
import scala.io.Source
import spray.can.Http
import spray.http._
import spray.client.pipelining._

class DeltacloudProvisioner(label: String, joinAddress: Address)(implicit system: ActorSystem) {
  import system.dispatcher

  val config = ConfigFactory.load()
  val host = config.getString("deltacloud.host")
  val port = config.getInt("deltacloud.port")

  implicit val timeout = Timeout(config.getInt("deltacloud.timeout").seconds)

  implicit val pipeline = (request: HttpRequest) => 
    (IO(Http) ? (request, Http.HostConnectorSetup(host, port = port))).mapTo[HttpResponse]

  var node: Option[Instance] = None

  def bootstrap(action: Instance => Unit): Unit = {
    if (node.isEmpty) {
      val driver = config.getString("deltacloud.driver")
      val chef_url = config.getString("deltacloud.chef.url")
      val chef_client = config.getString("deltacloud.chef.validation.client_name")
      val chef_validator = Scala.fromPath(config.getString("deltacloud.chef.validation.pem")).mkString

      for {
        realms <- Realm.index(state = Some("available"))
        vm <- Instance.create(
          image_id = config.getString(s"deltacloud.$driver.image"),
          keyname = Some(config.getString(s"deltacloud.$driver.keyname")),
          realm_id = Some(realms.head.id),
          hwp_id = Some(config.getString(s"deltacloud.$driver.hwp")),
          user_data = Some(s"""#cloud-config
            |
            |# Key from http://apt.opscode.com/packages@opscode.com.gpg.key
            |apt_sources:
            | - source: "deb http://apt.opscode.com/ $RELEASE-0.10 main"
            |   key: |
            |     -----BEGIN PGP PUBLIC KEY BLOCK-----
            |     Version: GnuPG v1.4.9 (GNU/Linux)
            |     
            |     mQGiBEppC7QRBADfsOkZU6KZK+YmKw4wev5mjKJEkVGlus+NxW8wItX5sGa6kdUu
            |     twAyj7Yr92rF+ICFEP3gGU6+lGo0Nve7KxkN/1W7/m3G4zuk+ccIKmjp8KS3qn99
            |     dxy64vcji9jIllVa+XXOGIp0G8GEaj7mbkixL/bMeGfdMlv8Gf2XPpp9vwCgn/GC
            |     JKacfnw7MpLKUHOYSlb//JsEAJqao3ViNfav83jJKEkD8cf59Y8xKia5OpZqTK5W
            |     ShVnNWS3U5IVQk10ZDH97Qn/YrK387H4CyhLE9mxPXs/ul18ioiaars/q2MEKU2I
            |     XKfV21eMLO9LYd6Ny/Kqj8o5WQK2J6+NAhSwvthZcIEphcFignIuobP+B5wNFQpe
            |     DbKfA/0WvN2OwFeWRcmmd3Hz7nHTpcnSF+4QX6yHRF/5BgxkG6IqBIACQbzPn6Hm
            |     sMtm/SVf11izmDqSsQptCrOZILfLX/mE+YOl+CwWSHhl+YsFts1WOuh1EhQD26aO
            |     Z84HuHV5HFRWjDLw9LriltBVQcXbpfSrRP5bdr7Wh8vhqJTPjrQnT3BzY29kZSBQ
            |     YWNrYWdlcyA8cGFja2FnZXNAb3BzY29kZS5jb20+iGAEExECACAFAkppC7QCGwMG
            |     CwkIBwMCBBUCCAMEFgIDAQIeAQIXgAAKCRApQKupg++Caj8sAKCOXmdG36gWji/K
            |     +o+XtBfvdMnFYQCfTCEWxRy2BnzLoBBFCjDSK6sJqCu5Ag0ESmkLtBAIAIO2SwlR
            |     lU5i6gTOp42RHWW7/pmW78CwUqJnYqnXROrt3h9F9xrsGkH0Fh1FRtsnncgzIhvh
            |     DLQnRHnkXm0ws0jV0PF74ttoUT6BLAUsFi2SPP1zYNJ9H9fhhK/pjijtAcQwdgxu
            |     wwNJ5xCEscBZCjhSRXm0d30bK1o49Cow8ZIbHtnXVP41c9QWOzX/LaGZsKQZnaMx
            |     EzDk8dyyctR2f03vRSVyTFGgdpUcpbr9eTFVgikCa6ODEBv+0BnCH6yGTXwBid9g
            |     w0o1e/2DviKUWCC+AlAUOubLmOIGFBuI4UR+rux9affbHcLIOTiKQXv79lW3P7W8
            |     AAfniSQKfPWXrrcAAwUH/2XBqD4Uxhbs25HDUUiM/m6Gnlj6EsStg8n0nMggLhuN
            |     QmPfoNByMPUqvA7sULyfr6xCYzbzRNxABHSpf85FzGQ29RF4xsA4vOOU8RDIYQ9X
            |     Q8NqqR6pydprRFqWe47hsAN7BoYuhWqTtOLSBmnAnzTR5pURoqcquWYiiEavZixJ
            |     3ZRAq/HMGioJEtMFrvsZjGXuzef7f0ytfR1zYeLVWnL9Bd32CueBlI7dhYwkFe+V
            |     Ep5jWOCj02C1wHcwt+uIRDJV6TdtbIiBYAdOMPk15+VBdweBXwMuYXr76+A7VeDL
            |     zIhi7tKFo6WiwjKZq0dzctsJJjtIfr4K4vbiD9Ojg1iISQQYEQIACQUCSmkLtAIb
            |     DAAKCRApQKupg++CauISAJ9CxYPOKhOxalBnVTLeNUkAHGg2gACeIsbobtaD4ZHG
            |     0GLl8EkfA8uhluM=
            |     =zKAm
            |     -----END PGP PUBLIC KEY BLOCK-----
            |
            |chef:
            |
            |# 11.10 will fail if install_type is "gems" (LP: #960576)
            |install_type: "packages"
            |
            |server_url: "$chef_url"
            |validation_name: "$chef_client"
            |
            | # value of validation_cert is not used if validation_key defined,
            | # but variable needs to be defined (LP: #960547)
            | validation_cert: "unused"
            | validation_key: |
            |     $chef_validator
            | 
            | # A run list for a first boot json
            | run_list:
            |  - "recipe[apt]"
            |  - "recipe[java]"
            |  - "recipe[cluster]"
            |
            | # Initial attributes used by the cookbooks
            | initial_attributes:
            |    java:
            |      jdk_version: 7
            |    cluster:
            |      role: "$label"
            |      seedNode: "${joinAddress.toString}"
            |
            |# Capture all subprocess output into a logfile
            |output: {all: '| tee -a /var/log/cloud-init-output.log'}
            |""".stripMargin)
        )
      } yield {
        node = Some(vm)
        action(vm)
      }
    }
  }

  def shutdown: Unit = {
    node.map(n => Instance.destroy(n.id))
  }
}

// Copyright (C) 2013  Carl Pulley
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

package provider.AwsEc2

import scala.collection.JavaConversions._
import org.jclouds.ContextBuilder
import org.jclouds.compute.ComputeServiceContext
import org.jclouds.domain.LoginCredentials
import org.jclouds.sshj.config.SshjSshClientModule
import org.streum.configrity.Configuration

trait Config { self: ClientContextConfig => 
  private[this] lazy val id = config.get[String]("aws-ec2.id")
  private[this] lazy val apikey = config.get[String]("aws-ec2.apikey")
  protected[this] lazy val ec2_private_key = scala.io.Source.fromFile(config.get[String]("ssl.certs") + "/aws-ec2.pem").mkString

  override lazy val client_context = ContextBuilder.newBuilder("aws-ec2")
      .credentials(id, apikey)
      .modules(Set(new SshjSshClientModule()))
      .buildView(classOf[ComputeServiceContext])
}

abstract class Ubuntu(version: String, group: String) extends image.Ubuntu(version, group) with Config {
  override lazy val admin = LoginCredentials.builder()
    .user("root")
    .privateKey(ec2_private_key)
    .authenticateSudo(false)
    .build()
}

// WARNING: Windows is currently to be considered experimental!
abstract class Windows(version: String, group: String) extends image.Windows(version, group) with Config {
  override lazy val admin = LoginCredentials.builder()
    .user("Administrator")
    .privateKey(ec2_private_key)
    .authenticateSudo(false)
    .build()
}

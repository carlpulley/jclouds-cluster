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

package vendor.AwsEc2

import scala.collection.JavaConversions._
import org.jclouds.ContextBuilder
import org.jclouds.compute.ComputeServiceContext
import org.jclouds.domain.LoginCredentials
import org.jclouds.ec2.compute.options.EC2TemplateOptions
import org.jclouds.sshj.config.SshjSshClientModule

// Reference: http://jclouds.apache.org/guides/aws-ec2/

// AWS EC2 vendor configuration data (shared between vendor image instances)
trait Config { self: ClientContextConfig => 
  private[this] lazy val id = config.getString("aws-ec2.id")
  private[this] lazy val apikey = config.getString("aws-ec2.apikey")
  protected[this] lazy val ec2_private_key = scala.io.Source.fromFile(config.getString("aws-ec2.ssh.private")).mkString

  override lazy val client_context = ContextBuilder.newBuilder("aws-ec2")
      .credentials(id, apikey)
      .modules(Set(new SshjSshClientModule()))
      .buildView(classOf[ComputeServiceContext])
}

// Vendor specialization of images

abstract class Ubuntu(version: String) extends image.Ubuntu(version) with Config {
  override lazy val admin = LoginCredentials.builder()
    .user("ubuntu")
    .privateKey(ec2_private_key)
    .authenticateSudo(false)
    .build()

  template_builder
    .options(template_builder.build.getOptions.asInstanceOf[EC2TemplateOptions].keyPair("aws-ec2"))
}

// WARNING: Windows is currently to be considered experimental!
abstract class Windows(version: String) extends image.Windows(version) with Config {
  override lazy val admin = LoginCredentials.builder()
    .user("Administrator")
    .privateKey(ec2_private_key)
    .authenticateSudo(false)
    .build()
}

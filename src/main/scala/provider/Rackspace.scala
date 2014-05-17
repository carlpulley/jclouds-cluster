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

package provider.Rackspace

import scala.collection.JavaConversions._
import org.jclouds.ContextBuilder
import org.jclouds.compute.ComputeServiceContext
import org.jclouds.openstack.nova.v2_0.compute.options.NovaTemplateOptions
import org.jclouds.openstack.nova.v2_0.compute.options.NovaTemplateOptions.Builder._
import org.jclouds.domain.LoginCredentials
import org.jclouds.sshj.config.SshjSshClientModule

abstract class Ubuntu(version: String, group: String) extends image.Ubuntu(version, group) {
  private[this] lazy val region = config.get[String]("rackspace.region")
  private[this] lazy val username = config.get[String]("rackspace.username")
  private[this] lazy val apikey = config.get[String]("rackspace.apikey")
  private[this] lazy val rackspace_private_key = scala.io.Source.fromFile(config.get[String]("ssl.certs") + "/rackspace").mkString
  private[this] val rackspace_public_key = scala.io.Source.fromFile(config.get[String]("ssl.certs") + "/rackspace.pub").mkString

  override lazy val admin = LoginCredentials.builder()
    .user("root")
    .privateKey(rackspace_private_key)
    .authenticateSudo(false)
    .build()

  override lazy val client_context = ContextBuilder.newBuilder(s"rackspace-cloudservers-$region")
    .credentials(username, apikey)
    .modules(Set(new SshjSshClientModule()))
    .buildView(classOf[ComputeServiceContext])

  template_builder
    .options(template_builder.build.getOptions.asInstanceOf[NovaTemplateOptions].authorizePublicKey(rackspace_public_key))
}

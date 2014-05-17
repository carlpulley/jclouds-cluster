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

package cloud.lib

import com.google.common.collect.ImmutableSet
import java.io.File
import java.io.FileOutputStream
import java.util.Properties
import net.liftweb.json.compact
import net.liftweb.json.JsonAST.JObject
import net.liftweb.json.JsonDSL._
import net.liftweb.json.render
import org.jclouds.chef.ChefApi
import org.jclouds.chef.ChefApiMetadata
import org.jclouds.chef.ChefContext
import org.jclouds.chef.config.ChefProperties
import org.jclouds.chef.util.RunListBuilder
import org.jclouds.compute.ComputeService
import org.jclouds.compute.ComputeServiceContext
import org.jclouds.compute.config.ComputeServiceProperties
import org.jclouds.compute.domain.NodeMetadata
import org.jclouds.compute.domain.NodeMetadataBuilder
import org.jclouds.compute.domain.TemplateBuilder
import org.jclouds.compute.options.RunScriptOptions.Builder.overrideLoginCredentials
import org.jclouds.ContextBuilder
import org.jclouds.domain.JsonBall
import org.jclouds.domain.LoginCredentials
import org.jclouds.scriptbuilder.domain.Statement
import org.jclouds.scriptbuilder.domain.StatementList
import org.streum.configrity.Configuration
import scala.collection.immutable.WrappedString
import scala.collection.JavaConversions._
import scala.collection.mutable
import scala.language.implicitConversions

trait ClientContextConfig {
  // N.B. cloud providers are *not* organised by module
  protected[this] val config = Config.load("cloud.conf")

  protected[this] val client_context: ComputeServiceContext // 'override lazy val' in children
}

abstract class Image(val module: String) extends ClientContextConfig {
  assert(new WrappedString(module).filter("abcdefghijklmnopqrstuvwxyz0123456789-".contains(_)).toString == module, "module names need to contain lower case alphanumerics or hypens")

  private[this] val chef_server     = config.get[String]("chef.url")
  private[this] val chef_user       = config.get[String]("chef.user.name")
  private[this] val chef_pem        = config.get[String]("chef.user.pem")
  private[this] val validation_name = config.get[String]("chef.validation.client_name")
  private[this] val validation_pem  = config.get[String]("chef.validation.pem")

  private[this] val chef_config = new Properties()
  chef_config
    .put(ChefProperties.CHEF_VALIDATOR_NAME, validation_name)
  chef_config
    .put(ChefProperties.CHEF_VALIDATOR_CREDENTIAL, scala.io.Source.fromFile(validation_pem).mkString)

  private[this] val chef_context: ChefContext = ContextBuilder.newBuilder("chef")
    .endpoint(chef_server)
    .credentials(chef_user, scala.io.Source.fromFile(chef_pem).mkString)
    .overrides(chef_config)
    .buildView(classOf[ChefContext])

  protected[this] val chef_runlist: RunListBuilder = new RunListBuilder()
  protected[this] val chef_attributes = mutable.Map[String, JObject]()

  protected[this] val client_properties = new Properties()
  client_properties
    .put(ComputeServiceProperties.TIMEOUT_SCRIPT_COMPLETE, (config.get[Int]("jclouds.script-complete") * 1000).asInstanceOf[java.lang.Integer]) // Convert seconds to milliseconds

  private[this] val client: ComputeService = client_context.getComputeService()

  protected[this] val template_builder: TemplateBuilder = client.templateBuilder()
    
  protected[this] val admin: LoginCredentials // 'override lazy val' in children

  protected[this] var node: Option[NodeMetadata] = None // initialised in bootstrap

  protected[this] val bootstrap_builder: ImmutableSet.Builder[Statement] = ImmutableSet.builder()

  protected[this] var ports = mutable.Set[Int]()

  def bootstrap(): NodeMetadata = {
    val template = template_builder.options(template_builder.build.getOptions.inboundPorts(ports.toArray : _*)).build()
    val chef_attrs: Map[String, JObject] = chef_attributes.toMap
    chef_context.getChefService().updateBootstrapConfigForGroup(chef_runlist.build(), new JsonBall(compact(render(chef_attrs))), module)
    val chef_bootstrap = chef_context.getChefService().createBootstrapScriptForGroup(module)
    val bootstrap_node = new StatementList(bootstrap_builder.add(chef_bootstrap).build())
    node = Some(client.createNodesInGroup(module, 1, template).head)
    client.runScriptOnNode(node.get.getId(), bootstrap_node, overrideLoginCredentials(admin))
    node.get
  }

  def shutdown() {
    node.map( metadata => {
      val chef_service = chef_context.getChefService()
      val ipaddr = metadata.getPrivateAddresses().head
      val nodename = s"$module-$ipaddr"
      if (chef_service.listClientsDetails().toSeq.map(_.getName()) contains (nodename)) {
        chef_service.deleteAllClientsInList(Seq(nodename))
      }
      if (chef_service.listNodes().toSeq.map(_.getName()) contains (nodename)) {
        chef_service.deleteAllNodesInList(Seq(nodename))
      }
      val chef_api = chef_context.getApi(classOf[ChefApi])
      val bootstrap_databag = ChefApiMetadata.defaultProperties().get(ChefProperties.CHEF_BOOTSTRAP_DATABAG).asInstanceOf[String]
      if (chef_api.listDatabags.contains(bootstrap_databag) && chef_api.listDatabagItems(bootstrap_databag).contains(module)) {
        chef_api.deleteDatabagItem(bootstrap_databag, module)
      }
      client.destroyNode(metadata.getId())
    })
  }
}

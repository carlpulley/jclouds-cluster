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

import sbt._
import Process._
import Keys._
import com.typesafe.sbt.SbtNativePackager._
import NativePackagerKeys._

trait Resolvers {
  val JCloudResolvers = Seq(
    "Java.net" at "http://download.java.net/maven/2/",
    "Maven Central" at "http://repo1.maven.org/",
    "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
    "Typesafe Snapshots" at "http://repo.typesafe.com/typesafe/snapshots/",
    "Sonatype Releases" at "http://oss.sonatype.org/content/repositories/releases",
    "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
  )
}

object V {
  val AKKA = "2.3.3"
  val CONFIG = "1.2.1"
  val JCLOUDS = "1.7.2"
  val LIFT = "2.5.1"
  val LOG4J = "1.2.17"
  val SCALA = "2.10.2"
  val SCALACHECK = "1.11.4"
  val SCALATEST = "2.1.7"
  val SLF4J = "1.7.5"
}

trait Dependencies {
  val Testing = Seq(
    "org.scalatest"  %% "scalatest" % V.SCALATEST % "test",
    "org.scalacheck" %% "scalacheck" % V.SCALACHECK % "test"
  )

  val Akka = Seq(
    "com.typesafe.akka" %% "akka-kernel" % V.AKKA,
    "com.typesafe.akka" %% "akka-actor" % V.AKKA,
    "com.typesafe.akka" %% "akka-remote" % V.AKKA,
    "com.typesafe.akka" %% "akka-cluster" % V.AKKA,
    "com.typesafe.akka" %% "akka-testkit" % V.AKKA % "test"
  )
  
  val JClouds = Seq(
    // Other cloud providers are possible here (e.g. Google Compute Engine, Microsoft Azure, Virtualbox, etc.)
    "org.apache.jclouds.provider" % "aws-ec2" % V.JCLOUDS,
    "org.apache.jclouds.provider" % "rackspace-cloudservers-uk" % V.JCLOUDS,
    // Other provisioners are possible (e.g. Puppet)
    "org.apache.jclouds.api" % "chef" % V.JCLOUDS,
    // Miscellaneous support code
    "org.apache.jclouds.api" % "openstack-nova" % V.JCLOUDS,
    "org.apache.jclouds.driver" % "jclouds-sshj" % V.JCLOUDS,
    // Needed due to "issues"
    "com.google.code.findbugs" % "jsr305" % "1.3.9",
    "org.eclipse.jetty.orbit" % "javax.servlet" % "3.0.0.v201112011016" artifacts (Artifact("javax.servlet", "jar", "jar")),
    // JClouds Logging
    "org.slf4j" % "slf4j-log4j12" % V.SLF4J,
    "log4j" % "log4j" % V.LOG4J
  )

  val Miscellaneous = Seq(
    // Configuration
    "com.typesafe" % "config" % V.CONFIG,
    // JSON (used to configure Chef)
    "net.liftweb" %% "lift-json" % V.LIFT
  )
}

object JCloudBuild extends Build with Resolvers with Dependencies {
  val jvmOptions = Seq("-Xms256M", "-Xmx1024M", "-XX:+UseParallelGC")

  lazy val JCloudSettings = Defaults.defaultSettings ++ packageArchetype.java_server ++ Seq(
    organization := "CakeSolutions",
    version := "1.0",
    scalaVersion := V.SCALA,
    shellPrompt := { st => Project.extract(st).currentProject.id + "> " },
    autoCompilerPlugins := true,
    resolvers := JCloudResolvers,
    libraryDependencies := Akka ++ JClouds ++ Miscellaneous,
    checksums := Seq("sha1", "md5"),
    scalacOptions ++= Seq("-deprecation", "-unchecked", "-feature", "-language:experimental.macros"),
    javacOptions ++= Seq("-Xlint:unchecked", "-Xlint:deprecation"),
    javaOptions ++= jvmOptions,
    parallelExecution in Test := false,
    mainClass in Compile := Some("akka.kernel.Main cakesolutions.example.WorkerNode"),
    packageDescription := "Ping-Pong Application (Clustered)",
    packageSummary in Linux := "Ping-Pong Application (Clustered)",
    maintainer in Linux := "Carl Pulley",
    daemonUser in Linux := "cluster",
    daemonGroup in Linux := "cluster",
    debianPackageDependencies in Debian ++= Seq("openjdk-7-jre"),
    rpmRequirements ++= Seq("java-1.7.0-openjdk"),
    rpmVendor := "CakeSolutions",
    rpmLicense := Some("GPLv3+"),
    rpmGroup := Some("group"),
    rpmBrpJavaRepackJars := true,
    // Ensure that we do not pack the jclouds.conf resource!
    mappings in (Compile, packageBin) ~= { _.filterNot { case (_, name) =>
      Seq("jclouds.conf").contains(name)
    }},
    initialCommands in console := "import cakesolutions.example._; import cakesolutions.example.ClusterMessages._"
  )
  
  lazy val root = Project(
    id = "cluster",
    base = file("."),
    settings = JCloudSettings
  )
}

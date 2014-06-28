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
  val DeltaCloudResolvers = Seq(
    "Java.net" at "http://download.java.net/maven/2/",
    "Maven Central" at "http://repo1.maven.org/",
    "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
    "Typesafe Snapshots" at "http://repo.typesafe.com/typesafe/snapshots/",
    "Sonatype Releases" at "http://oss.sonatype.org/content/repositories/releases",
    "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
    "Spray Repository" at "http://repo.spray.io"
  )
}

object V {
  val AKKA = "2.3.3"
  val CONFIG = "1.2.1"
  val LOG4J = "1.2.17"
  val SCALA = "2.11.1"
  val SCALACHECK = "1.11.4"
  val SCALATEST = "2.1.7"
  val SLF4J = "1.7.5"
  val SPRAY = "1.3.1"
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
    "com.typesafe.akka" %% "akka-contrib" % V.AKKA,
    "com.typesafe.akka" %% "akka-persistence-experimental" % V.AKKA,
    "com.typesafe.akka" %% "akka-http-core-experimental" % "0.4",
    "com.typesafe.akka" %% "akka-stream-experimental" % "0.4",
    "com.typesafe.akka" %% "akka-testkit" % V.AKKA % "test"
  )

  val Spray = Seq(
    "io.spray" %% "spray-client"  % V.SPRAY,
    "io.spray" %% "spray-http"    % V.SPRAY,
    "io.spray" %% "spray-httpx"   % V.SPRAY,
    "io.spray" %% "spray-json"    % "1.2.6",
    "io.spray" %% "spray-routing" % V.SPRAY
  )

  val Miscellaneous = Seq(
    // Configuration
    "com.typesafe" % "config"       % V.CONFIG
  )
}

object DeltaCloudBuild extends Build with Resolvers with Dependencies {
  lazy val DeltaCloudSettings = Defaults.defaultSettings ++ packageArchetype.java_server ++ Seq(
    organization := "CakeSolutions",
    version := "1.0",
    scalaVersion := V.SCALA,
    shellPrompt := { st => Project.extract(st).currentProject.id + "> " },
    autoCompilerPlugins := true,
    resolvers := DeltaCloudResolvers,
    libraryDependencies := Akka ++ Spray ++ Miscellaneous,
    checksums := Seq("sha1", "md5"),
    scalacOptions ++= Seq("-deprecation", "-unchecked", "-feature", "-language:experimental.macros"),
    javacOptions ++= Seq("-Xlint:unchecked", "-Xlint:deprecation"),
    javaOptions ++= Seq("-Xms256M", "-Xmx1024M", "-XX:+UseParallelGC"),
    parallelExecution in Test := false,
    mainClass in Compile := Some("akka.kernel.Main"),
    bashScriptExtraDefines += """addApp "cakesolutions.example.WorkerNode" """,
    packageDescription := "Ping-Pong Application (Clustered)",
    packageSummary in Linux := "Ping-Pong Application (Clustered)",
    maintainer in Linux := "Carl Pulley",
    daemonUser in Linux := "cluster",
    daemonGroup in Linux := "cluster",
    rpmVendor := "CakeSolutions",
    rpmLicense := Some("GPLv3+"),
    rpmGroup := Some("group"),
    rpmBrpJavaRepackJars := true,
    // Ensure that we do not pack the deltacloud.conf resource!
    mappings in (Compile, packageBin) ~= { _.filterNot { case (_, name) =>
      Seq("deltacloud.conf").contains(name)
    }},
    initialCommands in console := "import cakesolutions.example._; import cakesolutions.example.ClusterMessages._"
  )
  
  lazy val root = Project(
    id = "cluster",
    base = file("."),
    settings = DeltaCloudSettings
  )
}

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
  val AKKA = "2.3.4"
  val ASPECTJ = "1.8.2"
  val CONFIG = "1.2.1"
  val LOG4J = "1.2.17"
  val SCALA = "2.11.1"
  val SCALACHECK = "1.11.4"
  val SCALATEST = "2.1.7"
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
    "com.typesafe.akka" %% "akka-http-core-experimental" % "0.6",
    "com.typesafe.akka" %% "akka-stream-experimental" % "0.6",
    "com.typesafe.akka" %% "akka-testkit" % V.AKKA % "test"
  )

  val Miscellaneous = Seq(
    // Configuration
    "com.typesafe"            % "config"      % V.CONFIG,
    // Async niceness
    "org.scala-lang.modules" %% "scala-async" % "0.9.1",
    "org.scala-lang"          % "scala-library-all" % V.SCALA
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
    libraryDependencies := Akka ++ Miscellaneous,
    checksums := Seq("sha1", "md5"),
    scalacOptions ++= Seq("-deprecation", "-unchecked", "-feature", "-language:experimental.macros"),
    javacOptions ++= Seq("-Xlint:unchecked", "-Xlint:deprecation"),
    javaOptions ++= Seq("-Xms256M", "-Xmx1024M", "-XX:+UseParallelGC"),
    parallelExecution in Test := false,
    mainClass in Compile := Some("akka.kernel.Main"),
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

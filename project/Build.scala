import sbt._
import Keys._
import com.typesafe.startscript.StartScriptPlugin
import akka.sbt.AkkaKernelPlugin
import akka.sbt.AkkaKernelPlugin.{ Dist, outputDirectory, distJvmOptions }
import cc.spray.revolver.RevolverPlugin._
import classpath.ClasspathUtilities.isArchive
import java.io.FileOutputStream
import sbtassembly.Plugin._
import AssemblyKeys._

object BuildSettings {
  import Dependencies._

  val buildOrganization = "org.akkaresque"
  val buildVersion = "0.0.1"
  val buildScalaVersion = "2.9.1"

  val globalSettings = Seq(
    organization := buildOrganization,
    version := buildVersion,
    scalaVersion := buildScalaVersion,
    scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8"),
    javacOptions := Seq("-Xlint:unchecked", "-Xlint:deprecation","-encoding", "utf8"),
    fork in test := true,
    libraryDependencies ++= Seq(Test.scalatest),
    resolvers ++= Dependencies.resolutionRepos)
  val projectSettings = Defaults.defaultSettings ++ globalSettings
}

object Build extends sbt.Build {
  import Dependencies._
  import BuildSettings._

  override lazy val settings = super.settings ++ globalSettings

  lazy val merge = TaskKey[File]("merge-reference",
    "merge all reference.conf")

  lazy val mergeSettings: Seq[Project.Setting[_]] = Seq(
    merge <<= (fullClasspath in assembly) map { c =>
      // collect from all elements of the full classpath
      val (libs, dirs) =
        c map (_.data) partition (isArchive)
      // goal is to simply concatenate files here
      val dest = file("reference.conf")
      val out = new FileOutputStream(dest)
      val append = IO.transfer(_: File, out)
      try {
        // first collect from managed sources
        (dirs * "reference.conf").get foreach append
        // then from dependency jars by unzipping and
        // collecting reference.conf if present
        for (lib <- libs) {
          IO withTemporaryDirectory { dir =>
            IO.unzip(lib, dir, "reference.conf")
            (dir * "reference.conf").get foreach append
          }
        }
        // return merged file location as task result
        dest
      } finally {
        out.close()
      }
    },

     // get rid of the individual files from jars
    excludedFiles in assembly <<=
      (excludedFiles in assembly) {
        (old) =>
          (bases) =>
            old(bases) ++ (bases flatMap (base =>
              (base / "reference.conf").get))
      },

    // tell sbt-assembly to include our merged file
    assembledMappings in assembly <<=
      (assembledMappings in assembly, merge) map {
        (old, merged) =>
          (f) =>
            old(f) :+ (merged, "reference.conf")
      })

  lazy val scalaResqueWorker = Project("Akka-Resque",
    file("."),
    settings = projectSettings ++ assemblySettings ++ mergeSettings ++
    		   Revolver.settings ++ AkkaKernelPlugin.distSettings ++ Seq(
      distJvmOptions in Dist := "-Xms256M -Xmx1024M",
      outputDirectory in Dist := file("target")) ++
      StartScriptPlugin.startScriptForJarSettings ++
      Seq(libraryDependencies ++= Seq(
        Compile.akkaActor,
        Compile.akkaRemote,
        Compile.akkaKernel,
        Compile.akkaSlf4j,
        Compile.logback,
        Compile.sprayjson,
        Compile.redis,
        Compile.scalaTime,
        Test.specs2,
        Test.scalatest)))
}

object Dependencies {

  //val repoMgr = "127.0.0.1"

  val resolutionRepos = Seq(
    "Scala Tools" at "http://scala-tools.org/repo-releases/",
    "Typesafe repo" at "http://repo.typesafe.com/typesafe/releases",
    "spray repo" at "http://repo.spray.cc",
    "Jboss repo" at "https://repository.jboss.org/nexus/content/groups/public-jboss/")

  object V {
    val akka = "2.0.1"
    val specs2 = "1.7.1"
    val scalatest = "1.6.1"
    val slf4j = "1.6.4"
    val logback = "1.0.0"
    val sprayjson = "1.1.0"
    val redis = "2.5"
  }
  object Compile {
    val akkaAgent = "com.typesafe.akka" % "akka-agent" % V.akka % "compile"
    val akkaActor = "com.typesafe.akka" % "akka-actor" % V.akka % "compile"
    val akkaRemote = "com.typesafe.akka" % "akka-remote" % V.akka % "compile"
    val akkaKernel = "com.typesafe.akka" % "akka-kernel" % V.akka % "compile"
    val akkaTestkit = "com.typesafe.akka" % "akka-testkit" % V.akka % "compile"
    val akkaSlf4j = "com.typesafe.akka" % "akka-slf4j" % V.akka % "compile"
    val logback = "ch.qos.logback" % "logback-classic" % "1.0.0" % "runtime"
    val sprayjson = "cc.spray" %% "spray-json" % V.sprayjson % "compile"
    val redis = "net.debasishg" %% "redisclient" % V.redis % "compile"
    val log4j = "log4j" % "log4j" % "1.2.14" % "compile"
    val scalaTime = "org.scala-tools.time" %% "time" % "0.5" % "compile"
  }
  object Test {
    val specs2 = "org.specs2" %% "specs2" % V.specs2 % "test"
    val scalatest = "org.scalatest" %% "scalatest" % V.scalatest % "test"
  }
}

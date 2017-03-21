import sbt._, Keys._
import sbtunidoc.Plugin._
import Common._
import scalanative.sbtplugin.ScalaNativePlugin.autoImport._
import scalajscrossproject.ScalaJSCrossPlugin.autoImport.{toScalaJSGroupID => _, _}
import sbtcrossproject.CrossPlugin.autoImport._
import sbtcrossproject.{CrossProject, CrossClasspathDependency}
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport.{scalaJSVersion, isScalaJSProject}

object build {

  private[this] val genName = "scalaprops-gen"
  private[this] val coreName = "scalaprops-core"
  val allName = "scalaprops-all"
  private[this] val scalazlawsName = "scalaprops-scalazlaws"
  private[this] val scalapropsName = "scalaprops"

  val scalazVersion = SettingKey[String]("scalazVersion")

  val modules: List[String] = (
    genName ::
    coreName ::
    allName ::
    scalazlawsName ::
    scalapropsName ::
    Nil
  )

  // avoid move files
  object CustomCrossType extends sbtcrossproject.CrossType {
    override def projectDir(crossBase: File, projectType: String) =
      crossBase / projectType

    override def projectDir(crossBase: File, projectType: sbtcrossproject.Platform) = {
      val dir = projectType match {
        case JVMPlatform => "jvm"
        case JSPlatform => "js"
        case NativePlatform => "native"
       }
       crossBase / dir
    }

    def shared(projectBase: File, conf: String) =
      projectBase.getParentFile / "src" / conf / "scala"

    override def sharedSrcDir(projectBase: File, conf: String) =
      Some(shared(projectBase, conf))
  }

  private[this] def module(id: String) =
    CrossProject(id, file(id), CustomCrossType, JSPlatform, JVMPlatform, NativePlatform).settings(
      commonSettings,
      scalazVersion := "7.2.10",
      libraryDependencies += "org.scalaz" %%% "scalaz-core" % scalazVersion.value,
      initialCommands in console += {
        "import scalaprops._, scalaz._;" + Seq(
          "Gen", "Cogen", "Rand"
        ).map(a => s"val $a = scalaprops.$a").mkString(";") // for tab completion
      }
    ).jsSettings(
      scalaJSOptimizerOptions ~= { options =>
        // https://github.com/scala-js/scala-js/issues/2798
        try {
          scala.util.Properties.isJavaAtLeast("1.8")
          options
        } catch {
          case _: NumberFormatException =>
            options.withParallel(false)
        }
      },
      scalacOptions += {
        val a = (baseDirectory in LocalRootProject).value.toURI.toString
        val g = "https://raw.githubusercontent.com/scalaprops/scalaprops/" + Common.tagOrHash.value
        s"-P:scalajs:mapSourceURI:$a->$g/"
      }
    )

  lazy val gen = module("gen").settings(
    name := genName,
    description := "pure functional random value generator"
  ).jvmSettings(
    Generator.settings
  ).nativeSettings(
    unmanagedSourceDirectories in Compile += {
      baseDirectory.value.getParentFile / "js/src/main/scala/"
    }
  )

  lazy val core = module("core").settings(
    name := coreName
  ).dependsOn(gen)

  lazy val scalazlaws = module("scalazlaws").settings(
    name := scalazlawsName
  ).dependsOn(core)

  private[this] val testInterfaceSourceJar = SettingKey[xsbti.api.Lazy[Array[Byte]]]("testInterfaceSourceJar")

  lazy val scalaprops = module(scalapropsName).settings(
    name := scalapropsName
  ).dependsOn(
    core, new CrossClasspathDependency(scalazlaws, Some("test"))
  ).jvmSettings(
    libraryDependencies += "org.scala-sbt" % "test-interface" % "1.0",
    libraryDependencies += "org.scala-js" %% "scalajs-stubs" % scalaJSVersion % "provided"
  ).jsSettings(
    libraryDependencies += "org.scala-js" %% "scalajs-test-interface" % scalaJSVersion
  ).nativeSettings(
    libraryDependencies += "org.scala-js" %% "scalajs-stubs" % scalaJSVersion % "provided",
    testInterfaceSourceJar := xsbti.SafeLazy{
      IO.withTemporaryDirectory{ dir =>
        val f = dir / "test-interface-source-jar"
        val u = "http://repo1.maven.org/maven2/org/scala-js/scalajs-test-interface_2.11/0.6.14/scalajs-test-interface_2.11-0.6.14-sources.jar"
        println("download from " + u)
        IO.download(url(u), f)
        java.nio.file.Files.readAllBytes(f.toPath)
      }
    },
    sourceGenerators in Compile += Def.task{
      val dir = (sourceManaged in Compile).value
      val jar = new java.io.ByteArrayInputStream(testInterfaceSourceJar.value.get())
      val filter = new SimpleFilter(f =>
        f.endsWith("scala") && f.startsWith("sbt")
      )
      val files = IO.unzipStream(jar, dir, filter).toSeq
      files.foreach(println)
      val Seq(frameworkScala) = files.filter(_.getName == "Framework.scala")
      IO.writeLines(
        frameworkScala,
        IO.readLines(frameworkScala).filterNot{ line =>
          line.contains("JSExportDescendentClasses") || line.contains("scalajs")
        }
      )
      files
    }.taskValue
  )

}

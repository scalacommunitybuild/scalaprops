import UnidocKeys._
import build._

lazy val jvmProjects = Seq[ProjectReference](
  genJVM, coreJVM, scalapropsJVM, scalazlawsJVM
)

lazy val jsProjects = Seq[ProjectReference](
  genJS, coreJS, scalapropsJS, scalazlawsJS
)

lazy val nativeProjects = Seq[ProjectReference](
  genNative, coreNative, scalapropsNative, scalazlawsNative
)

lazy val genJS = gen.js
lazy val genJVM = gen.jvm
lazy val genNative = gen.native
lazy val genRoot = project.aggregate(genJS, genJVM, genNative)

lazy val coreJS = core.js
lazy val coreJVM = core.jvm
lazy val coreNative = core.native
lazy val coreRoot = project.aggregate(coreJS, coreJVM, genNative)

lazy val scalazlawsJS = scalazlaws.js
lazy val scalazlawsJVM = scalazlaws.jvm
lazy val scalazlawsNative = scalazlaws.native
lazy val scalazlawsRoot = project.aggregate(scalazlawsJS, scalazlawsJVM, scalazlawsNative)

lazy val scalapropsJS = scalaprops.js
lazy val scalapropsJVM = scalaprops.jvm
lazy val scalapropsNative = scalaprops.native
lazy val scalapropsRoot = project.aggregate(scalapropsJS, scalapropsJVM, scalapropsNative)

lazy val notPublish = Seq(
  publishArtifact := false,
  publish := {},
  publishLocal := {},
  PgpKeys.publishSigned := {},
  PgpKeys.publishLocalSigned := {}
)

lazy val nativeTest = Project(
  "nativeTest", file("nativeTest")
).enablePlugins(ScalaNativePlugin).settings(
  Common.commonSettings,
  notPublish,
  sourceGenerators in Compile += Def.task {
    (sources in Test in scalapropsNative).value.map{ test =>
      val dir = (sourceManaged in Compile).value
      val f = dir / "scalaprops" / test.getName
      IO.copyFile(test, f)
      f
    }
  }.taskValue,
  sourceGenerators in Compile += Def.task {
    val tests = (scalapropsTestNames in scalapropsNative).value.toList.sortBy(_._1).flatMap{
      case (obj, methods) =>
        methods.map{ m =>
          s"    TestMain.test($obj.param, $obj.$m)"
        }
    }.mkString("\n")
    val src = s"""package scalaprops

object NativeTestMain {
  def main(args: Array[String]): Unit = {
$tests
  }
}
"""
    println(src)

    val dir = (sourceManaged in Compile).value
    val f = dir / "NativeTestMain.scala"
    IO.write(f, src)
    Seq(f)
  }.taskValue
).dependsOn(
  scalapropsNative, scalazlawsNative
)

val root = Project("root", file(".")).settings(
  Common.commonSettings ++
  unidocSettings ++ (
    coreJVM ::
    scalapropsJVM ::
    scalazlawsJVM ::
    Nil
  ).map(p => libraryDependencies ++= (libraryDependencies in p).value)
).settings(
  name := allName,
  artifacts := Nil,
  unidocProjectFilter in (ScalaUnidoc, unidoc) := {
    (jsProjects ++ nativeProjects).foldLeft(inAnyProject)((acc, a) => acc -- inProjects(a))
  },
  packagedArtifacts := Map.empty,
  artifacts ++= Classpaths.artifactDefs(Seq(packageDoc in Compile)).value,
  packagedArtifacts ++= Classpaths.packaged(Seq(packageDoc in Compile)).value,
  Sxr.settings1,
  Defaults.packageTaskSettings(
    packageDoc in Compile, (UnidocKeys.unidoc in Compile).map{_.flatMap(Path.allSubpaths)}
  ),
  Sxr.settings2
).aggregate(
  jvmProjects ++ jsProjects ++ nativeProjects: _*
)

lazy val rootJS = project.aggregate(jsProjects: _*)
lazy val rootJVM = project.aggregate(jvmProjects: _*)
lazy val rootNative = project.aggregate(nativeProjects: _*)

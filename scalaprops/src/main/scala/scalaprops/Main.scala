package scalaprops

object Main {
  def main(args: Array[String]): Unit = {
    Gen[List[Int]].samples().foreach(println)
  }
}

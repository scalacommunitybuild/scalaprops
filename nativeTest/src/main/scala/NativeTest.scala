package scalaprops

import scalaz._

object TestMain {
  def test(objParam: Param, props: Property): Unit = {
    test(objParam, Properties.single((), props))
  }

  def test(objParam: Param, props: Properties[_]): Unit = {
    props.props.loc.cojoin.toTree.map{ t =>
      val name = (t.tree.rootLabel +: t.parents.map(_._2)).map(_._1).reverse.map(_.toString).mkString(".")
      name -> t.tree.rootLabel
    }.map{
      case (fullName, (id, Maybe.Just(check))) =>
        val param = check.paramEndo(objParam)
        print(fullName)
        println(check.prop.check(param, () => false, _ => print(".") ))
      case _ =>
    }.toStrictTree
  }
}

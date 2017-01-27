package scalaprops

import scalaz._
import scalaz.std.anyVal._

object CofreeTest extends Scalaprops {

  private[this] implicit def cogenCofree[F[_], A](implicit
    A: Cogen[A],
    F: Cogen1[F]
  ): Cogen[Cofree[F, A]] =
    new Cogen[Cofree[F, A]] {
      override def cogen[B](a: Cofree[F, A], g: CogenState[B]) =
        A.cogen(a.head, F.cogen1[Cofree[F, A]].cogen(a.tail, g))
    }

  private[this] implicit def cofreeEqual[F[_], A](implicit
    F: Eq1[F],
    A: Equal[A]
  ): Equal[Cofree[F, A]] =
    Equal.equal((a, b) =>
      A.equal(a.head, b.head) && F.eq1[Cofree[F, A]].equal(a.tail, b.tail)
    )

  private[this] implicit def cofreeZipEqual[F[_], A](implicit
    F: Eq1[F],
    A: Equal[A]
  ): Equal[CofreeZip[F, A]] =
    Tags.Zip.subst(cofreeEqual[F, A])

  private[this] type CofreeZip[F[_], A] = Cofree[F, A] @@ Tags.Zip

  private def cofreeZipTest[F[_]: Applicative](implicit
    F1: Gen[CofreeZip[F, Int]],
    F2: Gen[CofreeZip[F, Int => Int]],
    F3: Equal[CofreeZip[F, Int]]
  ) = {
    type G[A] = CofreeZip[F, A]
    implicit val a = Cofree.cofreeZipApplicative[F]
    scalazlaws.apply.all[G]
  }

  val zipMaybe = {
    import CofreeGenImplicit._
    cofreeZipTest[Maybe]
  }

  val zipIList = {
    import CofreeGenImplicit._
    cofreeZipTest[IList]
  }.andThenParam(Param.maxSize(2) andThen Param.minSuccessful(2))

  val zipValidation = {
    import CofreeGenImplicit._
    type F[A] = ValidationNel[Byte, A]
    cofreeZipTest[F]
  }

  val zipDisjunction = {
    import CofreeGenImplicit._
    type F[A] = Byte \/ A
    cofreeZipTest[F]
  }


  val testMaybe = {
    implicit def genCofreeMaybe[A: Gen] =
      Gen[OneAnd[List, A]].map{ list =>
        Cofree.unfold(list){
          case OneAnd(a, h :: t) =>
            (a, Maybe.just(OneAnd(h, t)))
          case OneAnd(a, Nil) =>
            (a, Maybe.empty[OneAnd[List, A]])
        }
      }

    type F[A] = Cofree[Maybe, A]

    Properties.list(
      scalazlaws.monad.all[F],
      scalazlaws.comonad.all[F],
      scalazlaws.traverse1.all[F],
      scalazlaws.equal.all[F[Int]]
    )
  }

  val stream = {
    type CofreeStream[A] = Cofree[Stream, A]

    import scalaz.std.stream._
    import scalaz.Isomorphism._

    val iso: Tree <~> CofreeStream =
      new IsoFunctorTemplate[Tree, CofreeStream] {
        def to[A](tree: Tree[A]) =
          Cofree(tree.rootLabel, tree.subForest.map(to))
        def from[A](c: CofreeStream[A]) =
          Tree.Node(c.head, c.tail.map(from(_)))
      }

    implicit def gen[A: Gen]: Gen[CofreeStream[A]] =
      Gen[Tree[A]].map(iso.to)

    Properties.list(
      scalazlaws.monad.all[CofreeStream],
      scalazlaws.comonad.all[CofreeStream],
      scalazlaws.traverse1.all[CofreeStream],
      scalazlaws.equal.all[CofreeStream[Int]]
    )
  }

  object CofreeGenImplicit {
    implicit def gen[F[_], A](implicit
      F: Gen1[F],
      A: Gen[A]
    ): Gen[Cofree[F, A]] = {
      lazy val g1 = {
        val g0 = F.gen1[Cofree[F, A]]
        Gen.gen[F[Cofree[F, A]]]{(size, rand) =>
          if(size > 0) {
            val (r, s) = rand.choose(0, 2) match {
              case (r0, 0) => (r0, size)
              case (r0, _) => (r0, size - 1)
            }
            g0.f(s, r)
          } else {
            g0.f(size, rand)
          }
        }
      }
      Apply[Gen].apply2(A, g1)((h, t) =>
        Cofree(h, t)
      )
    }

    implicit def genCofreeZip[F[_], A](implicit
      F: Gen1[F],
      A: Gen[A]
    ): Gen[CofreeZip[F, A]] =
      Tags.Zip.subst(gen[F, A])
  }

  val disjunction = {
    type E[A] = Byte \/ A
    type F[A] = Cofree[E, A]

    import CofreeGenImplicit._

    Properties.list(
      scalazlaws.bind.all[F],
      scalazlaws.comonad.all[F],
      scalazlaws.traverse1.all[F],
      scalazlaws.equal.all[F[Byte]]
    )
  }

  val validation = {
    type E[A] = ValidationNel[Byte, A]
    type F[A] = Cofree[E, A]

    import CofreeGenImplicit._

    Properties.list(
      scalazlaws.bind.all[F],
      scalazlaws.comonad.all[F],
      scalazlaws.traverse1.all[F],
      scalazlaws.equal.all[F[Byte]]
    )
  }
}

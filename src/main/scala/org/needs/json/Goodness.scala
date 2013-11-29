package org.needs.json

import scala.language.experimental.macros
import scala.language.higherKinds
import scala.reflect.macros.Context
import play.api.libs.json._
import play.api.libs.functional.Applicative

object Goodness {
  def liftAllImpl[M[_]](c: Context)(app: c.Expr[Applicative[M]])(implicit weakTypeOfM: c.WeakTypeTag[M[_]]) = {
    import c.universe._
    val Apply(_, List(victim)) = c.prefix.tree
    val TypeRef(_, _, List(TypeRef(_, tup, args))) = victim.tpe.widen
    if (!tup.name.toString.startsWith("Tuple")) {
      // TODO: more sane check for tuples?
      c.error(c.enclosingPosition, "Should be a tuple")
    }
    val x = newTermName(c.fresh("x"))
    val builder = args.zipWithIndex.map { case (tp, i) ⇒
      val accessor = newTermName(s"_${i + 1}")
      // TODO: why <:< does not work?
      if (tp.baseClasses.contains(weakTypeOf[M[_]].typeSymbol)) q"$x.$accessor" else q"$app.pure($x.$accessor)"
    } reduce { (x, y) ⇒
      // using and from play applicative builders
      q"$x and $y"
    }
    val res = q"$victim.map { $x ⇒ import play.api.libs.functional.syntax._; $builder.tupled }"
    c.macroApplication.setType(c.typeCheck(res).tpe)
    c.Expr[Reads[M[Any]]](res)
  }
}
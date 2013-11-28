package org.needs.json

import scala.language.experimental.macros
import org.needs.{Need, Endpoint}
import scala.concurrent.{Future, ExecutionContext}
import scala.reflect.macros.Context
import play.api.libs.json._

trait AsyncReads {
  import AsyncReadMacros._

  implicit class ResolvingReads[T](reads: Reads[T]) {
    def fulfillAll[X](points: List[Endpoint])(implicit ec: ExecutionContext): Reads[Future[X]] = macro fulfillAllImpl[X]
  }

  implicit class MappingReads[A](reads: Reads[Future[A]]) {
    def mapM[B](f: A ⇒ B)(implicit ec: ExecutionContext): Reads[Future[B]] = reads.map(v ⇒ v.map(f))
  }
}

object AsyncReads extends AsyncReads

object AsyncReadMacros {
  def fulfillAllImpl[X: c.WeakTypeTag](c: Context)(points: c.Expr[List[Endpoint]])(ec: c.Expr[ExecutionContext]) = {
    import c.universe._
    val Apply(_, List(victim)) = c.prefix.tree
    val TypeRef(_, _, List(TypeRef(_, tup, args))) = victim.tpe.widen
    if (!tup.name.toString.startsWith("Tuple")) {
      c.error(c.enclosingPosition, "Should be a tuple")
    }
    val x = newTermName(c.fresh("x"))
    val xs = args.zipWithIndex.map { case (tp, i) ⇒
      val accessor = newTermName(s"_${i + 1}")
      if (tp <:< weakTypeOf[Need[_]]) q"scala.async.Async.await($x.$accessor.fulfill($points)($ec))" else q"$x.$accessor"
    }
    val res = q"$victim.map($x ⇒ scala.async.Async.async { (..$xs) } ($ec))"
    c.macroApplication.setType(c.typeCheck(res).tpe)
    c.Expr[Reads[Future[X]]](res)
  }
}
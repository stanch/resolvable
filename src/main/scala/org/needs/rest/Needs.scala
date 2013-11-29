package org.needs.rest

import scala.language.experimental.macros
import scala.concurrent.ExecutionContext
import play.api.libs.json._
import org.needs.{Fulfillable, Endpoint, Need}
import scala.reflect.macros.Context

trait RestNeed[A] extends HasId {
//  import RestNeedMacros._
//
//  def probeRest[E <: HasId](implicit ec: ExecutionContext, reads: Reads[A]): Need.Probe[A] =
//    macro probeRestImpl[E, A]
//  def probeRestAsync[E <: HasId](implicit endpoints: List[Endpoint], ec: ExecutionContext, reads: Reads[Fulfillable[A]]): Need.Probe[A] =
//    macro probeRestAsyncImpl[E, A]
}

object RestNeedMacros {
//  def probeRestImpl[E <: HasId : c.WeakTypeTag, A: c.WeakTypeTag](c: Context)(ec: c.Expr[ExecutionContext], reads: c.Expr[Reads[A]]) = {
//    import c.universe._
//    c.Expr[Need.Probe[A]](q"{ case e: ${weakTypeOf[E]} if e.id == this.id ⇒ e.read($reads)($ec) }")
//  }
//  def probeRestAsyncImpl[E <: HasId : c.WeakTypeTag, A: c.WeakTypeTag](c: Context)(endpoints: c.Expr[List[Endpoint]], ec: c.Expr[ExecutionContext], reads: c.Expr[Reads[Fulfillable[A]]]) = {
//    import c.universe._
//    c.Expr[Need.Probe[A]](q"{ case e: ${weakTypeOf[E]} if e.id == this.id ⇒ e.readAsync($reads.fulfill($endpoints, $ec))($ec) }")
//  }
}

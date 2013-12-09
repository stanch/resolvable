package org.needs.rest

import scala.language.experimental.macros
import org.needs.{Fulfillable, Endpoint, Need}
import play.api.libs.json.Reads
import scala.reflect.macros.Context

trait Probing[A] { self: Need[A] ⇒
  import ProbingMacros._

  val id: String

  def singleResource[X <: SingleResourceEndpoint](implicit reads: Reads[Fulfillable[A]]): PartialFunction[Endpoint, Fulfillable[A]] =
    macro singleResourceImpl[A, X]

  def multipleResources[X <: MultipleResourceEndpoint](implicit reads: Reads[List[Fulfillable[A]]], id: HasId[A]): PartialFunction[Endpoint, Fulfillable[A]] =
    macro multipleResourcesImpl[A, X]
}

object ProbingMacros {
  def singleResourceImpl[A, X <: SingleResourceEndpoint](c: Context)(reads: c.Expr[Reads[Fulfillable[A]]])(implicit aType: c.WeakTypeTag[A], xType: c.WeakTypeTag[X]) = {
    import c.universe._
    c.Expr[PartialFunction[Endpoint, Fulfillable[A]]](
      q"{ case x: ${weakTypeOf[X]} if x.id == this.id ⇒ x.probeAs[${weakTypeOf[A]}] }"
    )
  }

  def multipleResourcesImpl[A, X <: MultipleResourceEndpoint](c: Context)(reads: c.Expr[Reads[List[Fulfillable[A]]]], id: c.Expr[HasId[A]])(implicit aType: c.WeakTypeTag[A], xType: c.WeakTypeTag[X]) = {
    import c.universe._
    c.Expr[PartialFunction[Endpoint, Fulfillable[A]]](q"""{
      case x: ${weakTypeOf[X]} if x.ids.contains(this.id) ⇒
        val list = x.probeAs[${weakTypeOf[List[A]]}]($reads.map(org.needs.Fulfillable.jumpList))
        org.needs.Fulfillable.map(list, { l: ${weakTypeOf[List[A]]} ⇒ l.find(v ⇒ $id.id(v) == this.id).get })
      }"""
    )
  }
}

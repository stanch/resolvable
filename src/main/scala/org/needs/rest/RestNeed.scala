package org.needs.rest

import scala.language.experimental.macros
import org.needs.{Fulfillable, Endpoint, Need}
import play.api.libs.json.Reads
import scala.reflect.macros.Context

trait RestNeed[A <: HasId] extends HasId { self: Need[A] ⇒
  import RestNeedMacros._

  def singleResource[X <: SingleResourceEndpoint](implicit reads: Reads[Fulfillable[A]]): PartialFunction[Endpoint, Fulfillable[A]] =
    macro singleResourceImpl[A, X]

  def multipleResources[X <: MultipleResourceEndpoint](implicit reads: Reads[List[Fulfillable[A]]]): PartialFunction[Endpoint, Fulfillable[A]] =
    macro multipleResourcesImpl[A, X]
}

object RestNeedMacros {
  def singleResourceImpl[A, X <: SingleResourceEndpoint](c: Context)(reads: c.Expr[Reads[Fulfillable[A]]])(implicit aType: c.WeakTypeTag[A], xType: c.WeakTypeTag[X]) = {
    import c.universe._
    c.Expr[PartialFunction[Endpoint, Fulfillable[A]]](
      q"{ case x: ${weakTypeOf[X]} if x.id == this.id ⇒ x.asFulfillable[${weakTypeOf[A]}] }"
    )
  }

  def multipleResourcesImpl[A, X <: MultipleResourceEndpoint](c: Context)(reads: c.Expr[Reads[List[Fulfillable[A]]]])(implicit aType: c.WeakTypeTag[A], xType: c.WeakTypeTag[X]) = {
    import c.universe._
    c.Expr[PartialFunction[Endpoint, Fulfillable[A]]](q"""{
      case x: ${weakTypeOf[X]} if x.ids.contains(this.id) ⇒
        val list = x.asFulfillable[${weakTypeOf[List[A]]}]($reads.map(org.needs.Fulfillable.sequence))
        org.needs.Fulfillable.fulfillableFunctor.fmap(list, { l: ${weakTypeOf[List[A]]} ⇒ l.find(_.id == this.id).get })
      }"""
    )
  }
}

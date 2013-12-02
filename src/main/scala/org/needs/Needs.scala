package org.needs

import scala.concurrent.{Future, ExecutionContext}
import scala.async.Async._
import scala.collection.immutable.TreeSet

case class Unfulfilled(needs: List[(Fulfillable[_], List[Endpoint])])
  extends Exception(needs map { case (need, tried) ⇒ s"Could not fulfill $need; tried ${tried.mkString(", ")}" } mkString ". ")

trait Need[A] extends Fulfillable[A] {
  var default: EndpointPool = EndpointPool.empty
  var probes: PartialFunction[Endpoint, Fulfillable[A]] = PartialFunction.empty

  def use(endpoints: Endpoint*) {
    default ++= endpoints
  }

  def from(how: PartialFunction[Endpoint, Fulfillable[A]]) {
    probes = probes orElse how
  }

  lazy val sources = default

  /** Fulfill the need using the specified endpoints */
  def fulfill(endpoints: EndpointPool)(implicit ec: ExecutionContext) = async {
    val it = endpoints.iterator
    var found: Option[A] = None
    var tried: List[Endpoint] = Nil
    while (it.hasNext && found.isEmpty) {
      val pt = it.next()
      if (probes.isDefinedAt(pt)) {
        found = await(probes(pt).fulfill(endpoints).map(x ⇒ Some(x)).recover {
          case u @ Unfulfilled(needs) if !needs.contains(this) ⇒ throw u
          case _ ⇒ tried ::= pt; None
        })
      }
    }
    found match {
      case Some(a) ⇒ a
      case None ⇒ throw Unfulfilled((this, tried) :: Nil)
    }
  }
}

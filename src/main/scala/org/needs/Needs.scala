package org.needs

import scala.concurrent.{Future, ExecutionContext}
import scala.async.Async._

case class Unfulfilled(needs: List[(Fulfillable[_], List[Endpoint])])
  extends Exception(needs map { case (need, tried) ⇒ s"Could not fulfill $need; tried $tried" } mkString ". ")

object Need {
  type Probe[A] = PartialFunction[Endpoint, Future[A]]
}

trait Need[A] extends Fulfillable[A] {
  /** Fulfill the need using the default endpoints */
  def go(implicit ec: ExecutionContext) = fulfill(Nil, ec)

  /** Fulfill the need using the specified endpoints */
  def fulfill(implicit endpoints: List[Endpoint], ec: ExecutionContext) = async {
    val points = endpoints ++ default
    println(s"Points: $points")
    val it = points.iterator
    var found: Option[A] = None
    var tried: List[Endpoint] = Nil
    while (it.hasNext && found.isEmpty) {
      val pt = it.next()
      if (probe(points, ec).isDefinedAt(pt)) {
        println(s"Trying $pt")
        found = await(probe(points, ec).apply(pt).map(x ⇒ Some(x)).recover {
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

  /** Try to fulfill the need using a given endpoint */
  def probe(implicit endpoints: List[Endpoint], ec: ExecutionContext): Need.Probe[A]

  /** Endpoints to be used if nothing is found */
  val default: List[Endpoint]
}
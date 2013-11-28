package org.needs

import scala.concurrent.{Future, ExecutionContext}
import scala.async.Async._

case class Unfulfilled[A](need: Need[A]) extends Throwable {
  override def toString = s"Could not fulfill $need"
}

trait Need[A] {
  /** Fulfill the need using the default endpoints */
  def fulfill(implicit ec: ExecutionContext): Future[A] = fulfill(Nil)

  /** Fulfill the need using the specified endpoints */
  def fulfill(endpoints: List[Endpoint])(implicit ec: ExecutionContext): Future[A] = async {
    val points = endpoints ++ default
    println(s"Points: $points")
    val it = points.iterator
    var found: Option[A] = None
    while (it.hasNext && found.isEmpty) {
      val pt = it.next()
      if (probe(points).isDefinedAt(pt)) {
        println(s"Trying $pt")
        found = await(probe(points).apply(pt).map(x ⇒ Some(x)).recover { case _ ⇒ None })
      }
    }
    found match {
      case Some(a) ⇒ a
      case None ⇒ throw Unfulfilled(this)
    }
  }

  /** Try to fulfill the need from a given endpoint
    * @param endpoints Current endpoints (to be passed to child needs)
    */
  def probe(endpoints: List[Endpoint])(implicit ec: ExecutionContext): PartialFunction[Endpoint, Future[A]]

  /** Endpoints to be used if nothing is found */
  val default: List[Endpoint]
}
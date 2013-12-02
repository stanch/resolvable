package org.needs

import scala.language.implicitConversions
import scala.concurrent.{Future, ExecutionContext}
import scala.async.Async._
import play.api.libs.functional.syntax._

/** An exception class to report unfulfilled needs */
case class Unfulfilled(needs: List[(Fulfillable[_], List[Endpoint])])
  extends Exception(needs map { case (need, tried) ⇒ s" - $need; tried ${tried.mkString(", ")}" } mkString("\nCould not fulfill\n", ".\n", "."))

trait Need[A] extends Fulfillable[A] {
  private var default: EndpointPool = EndpointPool.empty
  private var probes: PartialFunction[Endpoint, Fulfillable[A]] = PartialFunction.empty
  private var optimizations: (EndpointPool, ExecutionContext) ⇒ Future[EndpointPool] = (points, ec) ⇒ Future.successful(points)

  /** Add endpoints */
  def use(endpoints: Endpoint*) {
    default ++= endpoints
  }

  /** Define how to fulfill the need from a particular endpoint */
  def from(how: PartialFunction[Endpoint, Fulfillable[A]]) {
    probes = probes orElse how
  }

  /** Optimize fulfillment by adding aggregated endpoints.
    * Important! Do not remove already existing endpoints. Rely on priority instead. */
  def optimize(how: (EndpointPool, ExecutionContext) ⇒ Future[EndpointPool]) {
    val opt = optimizations
    optimizations = (points, ec) ⇒ async {
      val o = opt(points, ec)
      val h = how(points, ec)
      await(o) ++ await(h)
    }(ec)
  }

  /** Optimize fulfillment by adding aggregated endpoints.
    * Important! Do not remove already existing endpoints. Rely on priority instead. */
  def optimize(how: EndpointPool ⇒ EndpointPool) {
    val opt = optimizations
    optimizations = (points, ec) ⇒ async {
      val o = opt(points, ec)
      await(o) ++ how(points)
    }(ec)
  }

  protected lazy val sources = default

  protected def addOptimal(endpoints: EndpointPool)(implicit ec: ExecutionContext) =
    optimizations(endpoints, ec)

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

object Need {
  implicit def toFBO[A](x: Need[A]) = toFunctionalBuilderOps[Fulfillable, A](x)
}

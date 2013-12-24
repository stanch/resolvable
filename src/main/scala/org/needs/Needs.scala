package org.needs

import scala.language.implicitConversions
import scala.language.existentials
import scala.concurrent.{Future, ExecutionContext}
import scala.async.Async._
import play.api.libs.functional.syntax._

/** Holds an exception encountered during probing an endpoint */
case class Probed(endpoint: Endpoint, got: Throwable) {
  override def toString = s"$endpoint, got $got"
}

/** Holds an unfulfilled Need */
case class Unfulfilled(need: Need[_], probed: List[Probed]) {
  override def toString = s"$need, ${probed.mkString("probed:\n   - ", ";\n   - ", "")}"
}

/** An exception class to report unfulfilled needs */
case class Chagrin(needs: List[Unfulfilled])
  extends Exception(needs.mkString("\nCould not fulfill:\n - ", ";\n - ", "\n"))

/** A Need is a concrete implementation of Fulfillable, that is fulfilled by probing a set of Endpoints */
trait Need[A] extends Fulfillable[A] {
  private var default: EndpointPool = EndpointPool.empty
  private var probes: PartialFunction[Endpoint, Fulfillable[A]] = PartialFunction.empty
  private var optimizations: (EndpointPool, ExecutionContext) ⇒ Future[EndpointPool] = (points, ec) ⇒ Future.successful(points)

  /** Add endpoints */
  def use(endpoints: Endpoint*) {
    default ++= endpoints
  }

  /** Define how to fulfill the need by probing a particular endpoint */
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

  lazy val sources = default

  def addOptimal(endpoints: EndpointPool)(implicit ec: ExecutionContext) =
    optimizations(endpoints, ec)

  /** Fulfill the need using the specified endpoints */
  def fulfill(endpoints: EndpointPool)(implicit ec: ExecutionContext) = async {
    val it = endpoints.iterator
    var found: Option[A] = None
    var probed: List[Probed] = Nil
    while (it.hasNext && found.isEmpty) {
      val pt = it.next()
      if (probes.isDefinedAt(pt)) {
        found = await(probes(pt).fulfill(endpoints).map(x ⇒ Some(x)).recover {
          // if something else was not found, stop trying
          case u @ Chagrin(needs) if !needs.map(_.need).contains(this) ⇒ throw u
          case t ⇒ probed ::= Probed(pt, t); None
        })
      }
    }
    found match {
      case Some(a) ⇒ a
      case None ⇒ throw Chagrin(Unfulfilled(this, probed) :: Nil)
    }
  }
}

object Need {
  implicit def toFBO[A](x: Need[A]) = toFunctionalBuilderOps[Fulfillable, A](x)
}

/** A Need that uses itself as an Endpoint */
abstract class SelfFulfillingNeed[A] extends Need[A] with Endpoint {
  type Data = A
  use { this }
  from {
    case e if e == this ⇒ probe
  }
}

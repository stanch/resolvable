package resolvable

import scala.language.implicitConversions
import scala.concurrent.{Future, ExecutionContext}
import play.api.data.mapping.Rule

/** A [[Resolvable]] that selects matching endpoints from the pool and probes them one by one, respecting priority */
final case class Source[A](initial: Endpoint*)(
  matching: Source.Matching[A],
  priority: Source.Priority = PartialFunction.empty,
  optimal: Source.Optimizer = (_, _) ⇒ Future.successful(EndpointPool.empty)) extends Resolvable[A] {

  val manager: EndpointPoolManager = EndpointPoolManager(EndpointPool(initial)).addOptimizer(optimal)

  def seqOrder(p1: Seq[Int], p2: Seq[Int]) =
    (p1 zip p2).find { case (x, y) ⇒ x != y } match {
      case Some((x, y)) ⇒ x > y
      case None ⇒ p1.size > p2.size
    }

  private def select(pool: EndpointPool) =
    pool.select(matching.lift).map {
      case (pt, f) ⇒ (priority.lift(pt).getOrElse(Seq(0)), f)
    }.toVector.sortWith {
      case ((p1, _), (p2, _)) ⇒ Source.seqOrder(p1, p2)
    }.map {
      case (_, f) ⇒ f
    }

  def resolve(endpoints: EndpointPool)(implicit ec: ExecutionContext) =
    Future.successful(Resolution.Propagate(select(endpoints).reduce(_ orElse _)))
}

object Source {
  /** Tells whether the endpoint is suitable for the source and how to get a resolvable from it */
  type Matching[A] = PartialFunction[Endpoint, Resolvable[A]]

  /** Defines the order in which endpoints are probed and fetched.
    * Priority of each endpoint has type Seq[Int] and follows these rules:
    * - Seq(a, b, c, d, x, ...) < Seq(a, b, c, d, y, ...) if x < y
    * - Seq(a, b, c, d) < Seq(a, b, c, d, ...)
    * Default priority is Seq(0).
    */
  type Priority = PartialFunction[Endpoint, Seq[Int]]

  type Optimizer = (ExecutionContext, EndpointPool) ⇒ Future[EndpointPool]

  def seqOrder(p1: Seq[Int], p2: Seq[Int]) =
    (p1 zip p2).find { case (x, y) ⇒ x != y } match {
      case Some((x, y)) ⇒ x > y
      case None ⇒ p1.size > p2.size
    }

  /** Create a source builder */
  def apply[A] = new SourceBuilder[A]
}

class SourceBuilder[A] {
  /** A source fetched from `endpoint.data` */
  def from[E <: Endpoint](endpoint: E)(implicit rule: Rule[E#Data, Resolvable[A]]): Resolvable[A] =
    Source(endpoint) {
      case pt @ `endpoint` ⇒ Resolvable[A].fromEndpoint(pt)
    }

  /** A source fetched from `endpoint.data`, mapped onto `path` */
  def fromPath[E <: Endpoint, D](endpoint: E)(path: E#Data ⇒ D)(implicit rule: Rule[D, Resolvable[A]]): Resolvable[A] =
    Source(endpoint) {
      case pt @ `endpoint` ⇒ Resolvable[A].fromEndpointPath(pt)(path)
    }
}

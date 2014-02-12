package org.needs

/** A pool of endpoints */
final class EndpointPool(protected val endpoints: Set[Endpoint]) {
  /** Add a single endpoint */
  def +(endpoint: Endpoint) = new EndpointPool(this.endpoints + endpoint)

  /** Add several endpoints */
  def ++(endpoints: Seq[Endpoint]) = new EndpointPool(this.endpoints ++ endpoints)

  /** Append another pool */
  def ++(pool: EndpointPool) = new EndpointPool(this.endpoints ++ pool.endpoints)

  /** Fold */
  def fold[A](z: A)(f: (A, Endpoint) ⇒ A) = endpoints.foldLeft[A](z) { case (x, e) ⇒ f(x, e) }

  /** Select endpoints for which the predicate `f` isDefined, along with its value */
  def select[A](f: Endpoint ⇒ Option[A]) = endpoints flatMap { p ⇒
    Set(Some(p).zip(f(p)).toSeq: _*)
  }

  override def toString = s"EndpointPool(${endpoints.mkString(", ")})"
}

object EndpointPool {
  /** A pool with a single endpoint */
  def apply(endpoint: Endpoint) = new EndpointPool(Set(endpoint))

  /** A pool with several endpoints */
  def apply(endpoints: Seq[Endpoint]) = new EndpointPool(Set(endpoints: _*))

  /** An empty pool */
  val empty = new EndpointPool(Set.empty)

  /** Merge several pools together */
  def merge(pools: Seq[EndpointPool]) = pools.fold(empty)(_ ++ _)
}

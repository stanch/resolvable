package org.needs

/** A pool of endpoints */
final class EndpointPool(protected val endpoints: Set[EndpointPool.Entry]) {
  /** Add a single endpoint */
  def +(endpoint: Endpoint) = new EndpointPool(this.endpoints + EndpointPool.Entry(endpoint))

  /** Add several endpoints */
  def ++(endpoints: Seq[Endpoint]) = new EndpointPool(this.endpoints ++ endpoints.map(EndpointPool.Entry))

  /** Append another pool */
  def ++(pool: EndpointPool) = new EndpointPool(this.endpoints ++ pool.endpoints)

  /** Fold */
  def fold[A](z: A)(f: (A, Endpoint) ⇒ A) = endpoints.foldLeft[A](z) { case (x, e) ⇒ f(x, e.endpoint) }

  /** Select endpoints for which the predicate `f` isDefined, along with its value */
  def select[A](f: Endpoint ⇒ Option[A]) = endpoints flatMap {
    case EndpointPool.Entry(p) ⇒ Set(Some(p).zip(f(p)).toSeq: _*)
  }

  override def toString = {
    val points = endpoints.toList map { e ⇒ (if (e.endpoint.isFetched) "*" else "") + e.toString }
    s"Endpoints(${points.mkString(", ")})"
  }
}

object EndpointPool {
  /** A wrapper class to ensure that an already fetched endpoint can’t equal an otherwise identical non-fetched one */
  final case class Entry(endpoint: Endpoint) {
    def canEqual(other: Any) = other match {
      case Entry(point) if point.isFetched == endpoint.isFetched ⇒ true
      case _ ⇒ false
    }
  }

  /** A pool with a single endpoint */
  def apply(endpoint: Endpoint) = new EndpointPool(Set(Entry(endpoint)))

  /** A pool with several endpoints */
  def apply(endpoints: Seq[Endpoint]) = new EndpointPool(Set(endpoints.map(Entry): _*))

  /** An empty pool */
  val empty = new EndpointPool(Set.empty)

  /** Merge several pools together */
  def merge(pools: Seq[EndpointPool]) = pools.fold(empty)(_ ++ _)
}

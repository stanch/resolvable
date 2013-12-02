package org.needs

import scala.concurrent.{ExecutionContext, Future}
import com.typesafe.scalalogging.slf4j.Logging

/** An Endpoint represents something that can deliver data */
trait Endpoint extends Logging {
  /** Data type of the endpoint */
  type Data

  /** The implementation of data fetching */
  protected def fetch(implicit ec: ExecutionContext): Future[Data]

  /** Priorities dictate endpoint probing and fetching order */
  val priority = Seq(0, 0)

  // this is important to avoid deleting already fetched endpoints from the pool
  def canEqual(other: Any) = other match {
    case point: Endpoint if point.isFetched == isFetched ⇒ true
    case _ ⇒ false
  }

  /** Tells if the fetching process has started */
  final def isFetched = _fetched.synchronized(_fetched.isDefined)

  // all this could be a lazy val, if not for the ExecutionContext
  final private var _fetched: Option[Future[Data]] = None
  final protected def data(implicit ec: ExecutionContext) = _fetched.synchronized {
    if (_fetched.isEmpty) {
      logger.debug(s"--> Downloading $this")
      _fetched = Some(fetch)
    }
    _fetched.get
  }

  /** Returns a Fulfillable with the data */
  def asFulfillable = Fulfillable.fromFuture(implicit ec ⇒ data)
}

object Endpoint {
  /** Orders endpoints by 1) having been fetched 2) priority */
  def order(pt1: Endpoint, pt2: Endpoint) = if (pt1.isFetched != pt2.isFetched) {
    pt1.isFetched > pt2.isFetched
  } else (pt1.priority zip pt2.priority).find { case (p, q) ⇒ p != q } match {
    case Some((p, q)) ⇒ p > q
    case None ⇒ pt1.priority.size > pt2.priority.size
  }
}

/** An EndpointPool is a collection of Endpoints */
class EndpointPool(protected val endpoints: Set[Endpoint]) {
  def +(endpoint: Endpoint) = new EndpointPool(this.endpoints + endpoint)
  def ++(endpoints: Seq[Endpoint]) = new EndpointPool(this.endpoints ++ endpoints)
  def ++(pool: EndpointPool) = new EndpointPool(this.endpoints ++ pool.endpoints)
  def fold[A](z: A)(f: (A, Endpoint) ⇒ A) = endpoints.foldLeft[A](z)(f)
  def iterator = endpoints.toList.sortWith(Endpoint.order).iterator

  override def toString = {
    val points = endpoints.toList map { e ⇒ (if (e.isFetched) "*" else "") + e.toString }
    s"Endpoints(${points.mkString(", ")})"
  }
}

object EndpointPool {
  def empty = new EndpointPool(Set.empty)
  def merge(pools: List[EndpointPool]) = pools.fold(EndpointPool.empty)(_ ++ _)
}
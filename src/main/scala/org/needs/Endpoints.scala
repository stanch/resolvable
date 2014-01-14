package org.needs

import scala.concurrent.{ExecutionContext, Future}

/** An Endpoint represents something that can deliver data */
trait Endpoint {
  /** Data type of the endpoint */
  type Data

  /** The implementation of data fetching */
  protected def fetch(implicit ec: ExecutionContext): Future[Data]

  /** Endpoint logger */
  val logger: EndpointLogger

  // this is important to avoid deleting already fetched endpoints from the pool
  def canEqual(other: Any) = other match {
    case point: Endpoint if point.isFetched == isFetched ⇒ true
    case _ ⇒ false
  }

  /** Tells if the fetching process has started */
  final def isFetched = _fetchingLock.synchronized(_fetched.isDefined)

  // all this could be a lazy val, if not for the ExecutionContext
  final private val _fetchingLock = new Object
  final private var _fetched: Option[Future[Data]] = None
  final protected def data(implicit ec: ExecutionContext) = _fetchingLock.synchronized {
    if (_fetched.isEmpty) {
      logger.logFetching(this)
      _fetched = Some(fetch)
    }
    _fetched.get
  }

  /** Returns a Fulfillable with the data */
  def probe = Fulfillable.fromFuture(implicit ec ⇒ data)
}

/** Endpoint logger */
trait EndpointLogger {
  /** This method is called by an endpoint when it starts fetching */
  def logFetching(pt: Endpoint): Unit
}

object EndpointLogger{
  /** An endpoint logger that does nothing */
  object none extends EndpointLogger {
    def logFetching(pt: Endpoint) = ()
  }
}

/** Endpoint priority defines the order in which endpoints are probed and fetched.
  * Priority of each endpoint has type Seq[Int] and follows these rules:
  * - Seq(a, b, c, d, x, ...) < Seq(a, b, c, d, y, ...) if x < y
  * - Seq(a, b, c, d) < Seq(a, b, c, d, ...)
  * Default priority is Seq(0, 0).
  */
case class EndpointPriority(priority: PartialFunction[Endpoint, Seq[Int]]) {
  def p(pt: Endpoint) = priority.lift(pt).getOrElse(Seq(0, 0))

  def order(pt1: Endpoint, pt2: Endpoint) = if (pt1.isFetched != pt2.isFetched) {
    pt1.isFetched > pt2.isFetched
  } else {
    val (p1, p2) = (p(pt1), p(pt2))
    (p1 zip p2).find { case (x, y) ⇒ x != y } match {
      case Some((x, y)) ⇒ x > y
      case None ⇒ p1.size > p2.size
    }
  }
}

object EndpointPriority {
  /** Uses default priority for all endpoints */
  val none = EndpointPriority(PartialFunction.empty)
}

/** An EndpointPool is a collection of Endpoints */
class EndpointPool(protected val endpoints: Set[Endpoint]) {
  def +(endpoint: Endpoint) = new EndpointPool(this.endpoints + endpoint)
  def ++(endpoints: Seq[Endpoint]) = new EndpointPool(this.endpoints ++ endpoints)
  def ++(pool: EndpointPool) = new EndpointPool(this.endpoints ++ pool.endpoints)
  def fold[A](z: A)(f: (A, Endpoint) ⇒ A) = endpoints.foldLeft[A](z)(f)
  def iterator(priority: EndpointPriority) = endpoints.toList.sortWith(priority.order).iterator

  override def toString = {
    val points = endpoints.toList map { e ⇒ (if (e.isFetched) "*" else "") + e.toString }
    s"Endpoints(${points.mkString(", ")})"
  }
}

object EndpointPool {
  def empty = new EndpointPool(Set.empty)
  def merge(pools: List[EndpointPool]) = pools.fold(EndpointPool.empty)(_ ++ _)
}
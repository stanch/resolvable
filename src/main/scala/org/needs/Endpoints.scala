package org.needs

import scala.concurrent.{ExecutionContext, Future}

/** Represents something that can deliver data */
trait Endpoint {
  /** Data type of the endpoint */
  type Data

  /** The implementation of data fetching */
  protected def fetch(implicit ec: ExecutionContext): Future[Data]

  /** Endpoint logger. See [[EndpointLogger]] */
  protected val logger: EndpointLogger

  /** Tells if the fetching process has started */
  final def isFetched = _fetchingLock.synchronized(_fetched.isDefined)

  // all this could be a lazy val, if not for the ExecutionContext
  final private val _fetchingLock = new Object
  final private var _fetched: Option[Future[Data]] = None

  /** Fetched data */
  final def data(implicit ec: ExecutionContext) = _fetchingLock.synchronized {
    if (_fetched.isEmpty) {
      logger.logFetching(this)
      _fetched = Some(fetch)
    }
    _fetched.get
  }
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

  /** An endpoint logger that uses println */
  def println(prefix: String = "--> Fetching") = new EndpointLogger {
    def logFetching(pt: Endpoint) = Predef.println(s"$prefix $pt")
  }
}

/** Adds more optimal endpoints, e.g. bulk requests */
final case class EndpointPoolOptimizer(addOptimal: (EndpointPool, ExecutionContext) ⇒ Future[EndpointPool]) {
  def andThen(that: EndpointPoolOptimizer) = EndpointPoolOptimizer { (pool, ec) ⇒
    addOptimal(pool, ec).flatMap(that.addOptimal(_, ec))(ec)
  }
}

object EndpointPoolOptimizer {
  /** An optimizer that does nothing */
  def none = EndpointPoolOptimizer((x, _) ⇒ Future.successful(x))
  def future(in: ExecutionContext ⇒ Future[EndpointPoolOptimizer]) = EndpointPoolOptimizer { (pool, ec) ⇒
    in(ec).flatMap(_.addOptimal(pool, ec))(ec)
  }

  def chain(optimizers: Seq[EndpointPoolOptimizer]) = optimizers.fold(none)(_ andThen _)
}

final case class EndpointPoolInitiator(initPool: ExecutionContext ⇒ Future[EndpointPool]) {
  def +(that: EndpointPoolInitiator) = EndpointPoolInitiator { implicit ec ⇒
    initPool(ec).zip(that.initPool(ec)) map { case (x, y) ⇒ x ++ y }
  }
}

object EndpointPoolInitiator {
  def none = EndpointPoolInitiator(_ ⇒ Future.successful(EndpointPool.empty))
  def apply(pool: EndpointPool): EndpointPoolInitiator = EndpointPoolInitiator(_ ⇒ Future.successful(pool))
  def future(in: ExecutionContext ⇒ Future[EndpointPoolInitiator]) = EndpointPoolInitiator { ec ⇒
    in(ec).flatMap(_.initPool(ec))(ec)
  }

  def merge(initiators: Seq[EndpointPoolInitiator]) = initiators.fold(none)(_ + _)
}

/** A pool of endpoints */
final class EndpointPool(protected val endpoints: Set[EndpointPool.Entry]) {
  def +(endpoint: Endpoint) = new EndpointPool(this.endpoints + EndpointPool.Entry(endpoint))
  def ++(endpoints: Seq[Endpoint]) = new EndpointPool(this.endpoints ++ endpoints.map(EndpointPool.Entry))
  def ++(pool: EndpointPool) = new EndpointPool(this.endpoints ++ pool.endpoints)
  def fold[A](z: A)(f: (A, Endpoint) ⇒ A) = endpoints.foldLeft[A](z) { case (x, e) ⇒ f(x, e.endpoint) }
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

  def apply(endpoint: Endpoint) = new EndpointPool(Set(Entry(endpoint)))
  def apply(endpoints: Seq[Endpoint]) = new EndpointPool(Set(endpoints.map(Entry): _*))
  def empty = new EndpointPool(Set.empty)
  def merge(pools: Seq[EndpointPool]) = pools.fold(EndpointPool.empty)(_ ++ _)
}

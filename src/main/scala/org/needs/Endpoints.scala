package org.needs

import scala.concurrent.{ExecutionContext, Future}
import com.typesafe.scalalogging.slf4j.Logging
import scala.collection.immutable.TreeSet

trait Endpoint extends Ordered[Endpoint] with Logging {
  type Data
  protected def fetch(implicit ec: ExecutionContext): Future[Data]
  val priority = Seq(0, 0)

  object SeqOrdering extends Ordering[Seq[Int]] {
    def compare(x: Seq[Int], y: Seq[Int]) = (x zip y).find { case (p, q) ⇒ p != q } match {
      case Some((p, q)) ⇒ p - q
      case None ⇒ x.size - y.size
    }
  }

  def compare(that: Endpoint) = if (this == that) {
    // push the downloading endpoints forward
    that.isFetched compareTo this.isFetched
  } else if (this.priority != that.priority) {
    // highest priority goes first
    SeqOrdering.compare(that.priority, this.priority)
  } else {
    // random stable order
    // TODO: a better way?
    that.toString compareTo this.toString
  }

  final def isFetched = _fetched.synchronized(_fetched.isDefined)
  final private var _fetched: Option[Future[Data]] = None
  final protected def data(implicit ec: ExecutionContext) = _fetched.synchronized {
    if (_fetched.isEmpty) {
      logger.debug(s"--> Downloading $this")
      _fetched = Some(fetch)
    }
    _fetched.get
  }

  def asFulfillable = Fulfillable.fromFuture(implicit ec ⇒ data)
}

case class EndpointPool(endpoints: TreeSet[Endpoint]) {
  def +(endpoint: Endpoint) = EndpointPool(this.endpoints + endpoint)
  def ++(endpoints: Seq[Endpoint]) = EndpointPool(this.endpoints ++ endpoints)
  def ++(pool: EndpointPool) = EndpointPool(this.endpoints ++ pool.endpoints)
  def iterator = endpoints.iterator
  override def toString = {
    val points = endpoints.toList map { e ⇒ (if (e.isFetched) "*" else "") + e.toString }
    s"Endpoints(${points.mkString(", ")})"
  }
}

object EndpointPool {
  def empty = EndpointPool(TreeSet.empty)

  def merge(pools: List[EndpointPool]) =
    EndpointPool(TreeSet(pools.flatMap(_.endpoints): _*))
}
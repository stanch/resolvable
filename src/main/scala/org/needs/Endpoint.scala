package org.needs

import scala.concurrent.{ExecutionContext, Future}

trait Endpoint extends Ordered[Endpoint] {
  type Data
  protected def fetch(implicit ec: ExecutionContext): Future[Data]
  val priority = 0

  def compare(that: Endpoint) = if (this == that) {
    // push the downloading endpoints forward
    that.isFetched compareTo this.isFetched
  } else if (this.priority != that.priority) {
    // highest priority goes first
    that.priority compareTo this.priority
  } else {
    // random stable order
    // TODO: a better way?
    that.toString compareTo this.toString
  }

  final def isFetched = _fetched.synchronized(_fetched.isDefined)
  final private var _fetched: Option[Future[Data]] = None
  final protected def data(implicit ec: ExecutionContext) = _fetched.synchronized {
    if (_fetched.isEmpty) {
      println(s"downloading $this")
      _fetched = Some(fetch)
    }
    _fetched.get
  }

  def asFulfillable = Fulfillable.fromFuture(implicit ec ⇒ data)
}
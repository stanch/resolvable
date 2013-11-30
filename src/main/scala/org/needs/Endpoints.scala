package org.needs

import scala.concurrent.{ExecutionContext, Future}

trait Endpoint { self ⇒
  type Data
  protected def fetch(implicit ec: ExecutionContext): Future[Data]

  final def isFetched = _fetched.synchronized(_fetched.isDefined)
  final private var _fetched: Option[Future[Data]] = None
  final def data(implicit ec: ExecutionContext) = _fetched.synchronized {
    if (_fetched.isEmpty) {
      println(s"downloading $this")
      _fetched = Some(fetch)
    }
    _fetched.get
  }
}

trait EndpointOps {
  implicit class RichEndpoint(point: Endpoint) {
    def orElse(other: Endpoint) = point :: other :: Nil
  }
  implicit class RichEndpointList(list: List[Endpoint]) {
    def orElse(other: Endpoint) = list :+ other
  }
}
object EndpointOps extends EndpointOps
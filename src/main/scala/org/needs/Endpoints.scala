package org.needs

import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.json._

trait Endpoint { self ⇒
  type Data
  val data: Future[Data]
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
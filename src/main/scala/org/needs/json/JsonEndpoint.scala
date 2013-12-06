package org.needs.json

import org.needs.{Fulfillable, Endpoint}
import play.api.libs.json._

trait JsonEndpoint extends Endpoint {
  type Data = JsValue

  def asFulfillable[A](implicit reads: Reads[Fulfillable[A]]) =
    Fulfillable.jumpFuture(implicit ec ⇒ data.map(_.as[Fulfillable[A]]))
}

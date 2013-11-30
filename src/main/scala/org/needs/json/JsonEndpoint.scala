package org.needs.json

import play.api.libs.json._
import org.needs.{Fulfillable, Endpoint}
import scala.concurrent.{Future, ExecutionContext}
import scala.collection.immutable.TreeSet

trait JsonEndpoint extends Endpoint {
  type Data = JsValue

  def as[A](implicit reads: Reads[Fulfillable[A]]) =
    Fulfillable.fromFuture(implicit ec ⇒ data.map(_.as[Fulfillable[A]]))
}

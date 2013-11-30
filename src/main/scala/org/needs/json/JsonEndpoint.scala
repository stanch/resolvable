package org.needs.json

import play.api.libs.json._
import org.needs.{Fulfillable, Endpoint}
import scala.concurrent.ExecutionContext

trait JsonEndpoint extends Endpoint {
  type Data = JsValue
  def as[A](implicit endpoints: List[Endpoint], ec: ExecutionContext, reads: Reads[Fulfillable[A]]) =
    data.flatMap(v ⇒ v.as[Fulfillable[A]].fulfill)

  def read[A](reads: Reads[Fulfillable[A]])(implicit endpoints: List[Endpoint], ec: ExecutionContext) =
    data.flatMap(v ⇒ v.as[Fulfillable[A]](reads).fulfill)
}

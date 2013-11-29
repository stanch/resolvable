package org.needs.json

import play.api.libs.json._
import scala.concurrent.{Future, ExecutionContext}
import org.needs.Endpoint
import org.needs.json.Async.FulfillableReads

trait JsonEndpoint extends Endpoint {
  type Data = JsValue
  def as[A](implicit reads: Reads[A], ec: ExecutionContext) = read(reads)
  def asFulfillable[A](points: List[Endpoint])(implicit reads: FulfillableReads[A], ec: ExecutionContext) = readAsync(reads(points))
  def read[A](reads: Reads[A])(implicit ec: ExecutionContext) = data.map(d ⇒ d.as(reads))
  def readAsync[A](reads: Reads[Future[A]])(implicit ec: ExecutionContext) = data.flatMap(d ⇒ d.as(reads))
}
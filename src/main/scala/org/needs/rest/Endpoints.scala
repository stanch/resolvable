package org.needs.rest

import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.json._
import org.needs.{Fulfillable, Endpoint}

trait JsonEndpoint extends Endpoint {
  type Data = JsValue

  def asFulfillable[A](implicit reads: Reads[Fulfillable[A]]) =
    Fulfillable.jumpFuture(implicit ec ⇒ data.map(_.as[Fulfillable[A]]))
}

trait RestEndpoint extends JsonEndpoint {
  def client(url: String)(implicit ec: ExecutionContext): Future[JsValue]
}

trait HasId {
  val id: String
}

trait SingleResourceEndpoint extends RestEndpoint with HasId {
  val path: String
  def fetch(implicit ec: ExecutionContext) = client(s"$path/$id")
}

trait HasIds {
  val ids: Set[String]
}

trait MultipleResourceEndpoint extends RestEndpoint with HasIds {
  val path: String
  def fetch(implicit ec: ExecutionContext) = client(s"$path/${ids.mkString(",")}")
}

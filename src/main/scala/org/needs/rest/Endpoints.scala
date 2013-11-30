package org.needs.rest

import org.needs.json.JsonEndpoint
import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.json._

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

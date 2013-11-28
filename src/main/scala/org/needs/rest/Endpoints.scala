package org.needs.rest

import org.needs.json.JsonEndpoint
import scala.concurrent.Future
import play.api.libs.json._

trait RestEndpoint extends JsonEndpoint {
  def client(url: String): Future[JsValue]
}

trait HasId {
  val id: String
}

trait SingleResourceEndpoint extends RestEndpoint with HasId {
  val path: String
  lazy val data = client(s"$path/$id")
}

trait HasIds {
  val ids: List[String]
}

trait MultipleResourceEndpoint extends RestEndpoint with HasIds {
  val path: String
  lazy val data = client(s"$path/${ids.mkString(",")}")
}

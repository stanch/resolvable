package org.needs.rest

import scala.concurrent.ExecutionContext
import org.needs.json.JsonEndpoint
import org.needs.http.HttpEndpoint

sealed trait RestEndpoint extends HttpEndpoint with JsonEndpoint

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

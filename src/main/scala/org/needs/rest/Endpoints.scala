package org.needs.rest

import scala.concurrent.ExecutionContext
import org.needs.json.HttpJsonEndpoint
import scala.annotation.implicitNotFound

sealed trait RestEndpoint extends HttpJsonEndpoint

@implicitNotFound("Class ${A} needs to provide a HasId typeclass instance in order to be identified")
trait HasId[-A] {
  def id(entity: A): String
}

trait SingleResourceEndpoint extends RestEndpoint {
  val id: String
  val baseUrl: String
  def fetch(implicit ec: ExecutionContext) = client.getJson(s"$baseUrl/$id")
}

trait MultipleResourceEndpoint extends RestEndpoint {
  val ids: Set[String]
  val baseUrl: String
  def fetch(implicit ec: ExecutionContext) = client.getJson(s"$baseUrl/${ids.mkString(",")}")
}

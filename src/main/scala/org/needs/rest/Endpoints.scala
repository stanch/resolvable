package org.needs.rest

import scala.concurrent.ExecutionContext
import org.needs.json.JsonEndpoint
import org.needs.http.HttpEndpoint
import scala.annotation.implicitNotFound

sealed trait RestEndpoint extends HttpEndpoint with JsonEndpoint

@implicitNotFound("Class ${A} needs to provide a HasId typeclass instance in order to be identified")
trait HasId[-A] {
  def id(entity: A): String
}

trait SingleResourceEndpoint extends RestEndpoint {
  val id: String
  val baseUrl: String
  def fetch(implicit ec: ExecutionContext) = client(s"$baseUrl/$id")
}

trait MultipleResourceEndpoint extends RestEndpoint {
  val ids: Set[String]
  val baseUrl: String
  def fetch(implicit ec: ExecutionContext) = client(s"$baseUrl/${ids.mkString(",")}")
}

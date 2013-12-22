package org.needs.http

import dispatch._
import play.api.libs.json.Json
import scala.concurrent.ExecutionContext
import org.needs.json.JsonEndpoint
import org.needs.file.FileEndpoint

sealed trait DispatchClient

trait DispatchJsonClient extends DispatchClient { self: HttpEndpoint with JsonEndpoint ⇒
  def client(url: String, query: Map[String, String] = Map.empty)(implicit ec: ExecutionContext) =
    Http((dispatch.url(url) <<? query) OK dispatch.as.String).map(Json.parse)
}

trait DispatchFileClient extends DispatchClient { self: HttpEndpoint with FileEndpoint ⇒
  def client(url: String, query: Map[String, String] = Map.empty)(implicit ec: ExecutionContext) = {
    val f = create
    Http((dispatch.url(url) <<? query) > dispatch.as.File(f)).map(_ ⇒ f)
  }
}

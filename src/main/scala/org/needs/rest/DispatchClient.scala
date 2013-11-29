package org.needs.rest

import dispatch._
import play.api.libs.json.Json
import scala.concurrent.ExecutionContext

trait DispatchClient { self: RestEndpoint ⇒
  def client(url: String)(implicit ec: ExecutionContext) =
    Http(dispatch.url(url) OK dispatch.as.String).map(Json.parse)
}
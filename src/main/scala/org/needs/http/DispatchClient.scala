package org.needs.http

import dispatch._
import play.api.libs.json.Json
import scala.concurrent.ExecutionContext
import org.needs.json.JsonEndpoint
import org.needs.file.FileEndpoint
import java.io.File

class DispatchClient extends HttpClient {
  def get(url: String, query: Map[String, String] = Map.empty)(implicit ec: ExecutionContext) =
    Http((dispatch.url(url) <<? query) OK dispatch.as.String)

  def getFile(saveTo: File, url: String, query: Map[String, String] = Map.empty)(implicit ec: ExecutionContext) = {
    Http((dispatch.url(url) <<? query) > dispatch.as.File(saveTo)).map(_ ⇒ saveTo)
  }
}

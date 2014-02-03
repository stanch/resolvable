package org.needs.json

import org.needs._
import play.api.libs.json._
import org.needs.http.{HttpClient, HttpEndpoint}
import scala.concurrent.ExecutionContext

trait JsonEndpoint extends Endpoint {
  type Data = JsValue
}

trait HttpJsonEndpoint extends JsonEndpoint with HttpEndpoint {
  implicit class JsonHttpClient(client: HttpClient) {
    def getJson(url: String, query: Map[String, String] = Map.empty)(implicit ec: ExecutionContext) =
      client.get(url, query).map(Json.parse)
  }
}

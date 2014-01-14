package org.needs.json

import org.needs.{Fulfillable, Endpoint}
import play.api.libs.json._
import org.needs.http.{HttpClient, HttpEndpoint}
import scala.concurrent.ExecutionContext

trait JsonEndpoint extends Endpoint {
  type Data = JsValue

  def probeAs[A](implicit reads: Reads[Fulfillable[A]]) =
    Fulfillable.jumpFuture(implicit ec ⇒ data.map(_.as[Fulfillable[A]]))
}

trait HttpJsonEndpoint extends JsonEndpoint with HttpEndpoint {
  implicit class JsonHttpClient(client: HttpClient) {
    def getJson(url: String, query: Map[String, String] = Map.empty)(implicit ec: ExecutionContext) =
      client.get(url, query).map(Json.parse)
  }
}

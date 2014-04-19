package resolvable.json

import resolvable._
import play.api.libs.json._
import resolvable.http.{HttpClient, HttpEndpoint}
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

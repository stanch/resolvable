package resolvable.flickr

import scala.concurrent.ExecutionContext
import resolvable.json.HttpJsonEndpoint
import resolvable.http.HttpClient
import resolvable.EndpointLogger
import play.api.libs.json.JsString

private[flickr] trait Endpoints {
  def apiKey: String
  def httpClient: HttpClient
  def endpointLogger: EndpointLogger

  abstract class FlickrEndpoint(method: String)(args: (String, String)*) extends HttpJsonEndpoint {
    val logger = endpointLogger
    val client = httpClient

    protected def fetch(implicit ec: ExecutionContext) =
      client.getJson("https://api.flickr.com/services/rest", Map(args: _*) ++ Map(
        "method" → method,
        "api_key" → apiKey,
        "format" → "json",
        "nojsoncallback" → "1"
      )) map { js ⇒
        if ((js \ "stat") == JsString("fail")) throw FlickrApiException((js \ "message").as[String]) else js
      }
  }

  case class NearbyPhotos(lat: Double, lng: Double, radius: Int) extends FlickrEndpoint("flickr.photos.search")(
    "lat" → lat.toString,
    "lon" → lat.toString,
    "radius" → radius.toString
  )

  case class PersonInfo(id: String) extends FlickrEndpoint("flickr.people.getInfo")(
    "user_id" → id
  )
}

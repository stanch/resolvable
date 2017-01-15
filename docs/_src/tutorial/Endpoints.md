# Endpoints

To actually fetch some data, we will need to define our endpoints. That’s a piece of cake!
(quite literally, we’ll use the cake pattern for our convenience). An endpoint is basically
something with a `Data` type and a `fetch` method, which returns `Future[Data]`. Endpoints
correspond directly to the external APIs. If there’s a REST API `/webservice/api/books/#id`,
which returns JSON, we’ll have an endpoint with `Data = JsValue` and `fetch` using an HTTP
client to download the respective url.

```scala
import resolvable._
import resolvable.json._
import resolvable.http._

trait Endpoints {

  // this logger just println-s the endpoints being fetched
  // great for debugging!
  val endpointLogger = EndpointLogger.println(success = true, failure = false)
  
  // Dispatch and Android clients are provided
  val httpClient = new DispatchClient

  // the base of our endpoints
  abstract class RemoteResource(val baseUrl: String) extends HttpJsonEndpoint {
    val id: String
    val logger = endpointLogger
    val client = httpClient
    protected def fetch(implicit ec: ExecutionContext) =
      client.getJson(s"$baseUrl/$id")
  }
  
  // an endpoint for our books
  case class RemoteBook1(id: String)
    extends RemoteResource("/webservice1/api/books")
    
  // another one
  case class RemoteBook2(id: String)
    extends RemoteResource("/webservice2/api/books")
  
  // an endpoint for our authors
  case class RemoteAuthor(id: String)
    extends RemoteResource("/webservice/api/authors")
  
}
```

In the same fashion we can add endpoints for other services or for local databases.
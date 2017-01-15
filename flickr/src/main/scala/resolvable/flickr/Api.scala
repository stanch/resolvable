package resolvable.flickr

import resolvable.http.HttpClient
import resolvable.EndpointLogger

case class Api(apiKey: String, httpClient: HttpClient, endpointLogger: EndpointLogger)
  extends Endpoints
  with JsonFormats
  with Needs

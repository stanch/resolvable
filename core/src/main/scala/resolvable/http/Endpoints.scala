package resolvable.http

import resolvable.Endpoint
import scala.concurrent.{Future, ExecutionContext}
import java.io.File

trait HttpClient {
  def get(url: String, query: Map[String, String] = Map.empty)(implicit ec: ExecutionContext): Future[String]
  def getFile(saveTo: File, url: String, query: Map[String, String] = Map.empty)(implicit ec: ExecutionContext): Future[File]
}

trait HttpEndpoint extends Endpoint {
  val client: HttpClient
}

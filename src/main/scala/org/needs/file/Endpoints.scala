package org.needs.file

import org.needs.Endpoint
import java.io.File
import scala.concurrent.{Future, ExecutionContext}
import org.needs.http.{HttpClient, HttpEndpoint}

trait FileEndpoint extends Endpoint {
  type Data = File

  /** Resolved file will be put here */
  def create: File
}

trait LocalFileEndpoint extends FileEndpoint {
  case object NotFound extends Throwable
  protected def fetch(implicit ec: ExecutionContext) =
    Option(create).filter(_.exists()).map(Future.successful).getOrElse(Future.failed(NotFound))
}

trait HttpFileEndpoint extends FileEndpoint with HttpEndpoint {
  implicit class JsonHttpClient(client: HttpClient) {
    def getFile(url: String, query: Map[String, String] = Map.empty)(implicit ec: ExecutionContext) =
      client.getFile(create, url, query)
  }
}

package org.needs.file

import org.needs.Endpoint
import java.io.File
import scala.concurrent.{Future, ExecutionContext}
import org.needs.http.HttpEndpoint

trait FileEndpoint extends Endpoint {
  type Data = File
  def create: File
}

trait LocalFileEndpoint extends FileEndpoint {
  case object NotFound extends Throwable
  protected def fetch(implicit ec: ExecutionContext) =
    Option(create).filter(_.exists()).map(Future.successful).getOrElse(Future.failed(NotFound))
}

trait HttpFileEndpoint extends FileEndpoint with HttpEndpoint {
  val url: String
  val baseUrl: String
  protected def fetch(implicit ec: ExecutionContext) = client(new File(baseUrl, url).getAbsolutePath)
}

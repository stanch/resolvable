package org.needs.http

import org.needs.Endpoint
import scala.concurrent.{Future, ExecutionContext}

trait HttpEndpoint extends Endpoint {
  def client(url: String)(implicit ec: ExecutionContext): Future[Data]
}

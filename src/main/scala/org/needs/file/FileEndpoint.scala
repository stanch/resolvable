package org.needs.file

import org.needs.Endpoint
import java.io.File

trait FileEndpoint extends Endpoint {
  type Data = File
  def create: File
}

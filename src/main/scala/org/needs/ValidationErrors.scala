package org.needs

import play.api.data.mapping._

case class ValidationErrors(paths: Seq[(Path, Seq[ValidationError])]) extends Exception(paths.toString())

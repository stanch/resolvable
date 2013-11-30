package org.needs

import org.needs.Fulfillable.Optimizer
import scala.concurrent.{ExecutionContext, Future}

object Optimizers {
  val blank: Optimizer = endpoints ⇒ Future.successful(endpoints)

  object Implicits {
    implicit val blank = Optimizers.blank
  }
}

trait OptimizerOps {
  implicit class AddingOptimizer(o: Optimizer) {
    def +(other: Optimizer)(implicit ec: ExecutionContext): Optimizer =
      endpoints ⇒ o(endpoints).flatMap(other)
  }
}

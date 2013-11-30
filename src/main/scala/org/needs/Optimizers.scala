package org.needs

import org.needs.Fulfillable.Optimizer
import scala.concurrent.{ExecutionContext, Future}

object Optimizers {
  val basic: Optimizer = endpoints ⇒ Future.successful(endpoints.sortBy(!_.isFetched))

  object Implicits {
    implicit val basic = Optimizers.basic
  }
}

trait OptimizerOps {
  implicit class AddingOptimizer(o: Optimizer) {
    def +(other: Optimizer)(implicit ec: ExecutionContext): Optimizer =
      endpoints ⇒ o(endpoints).flatMap(other)
  }
}

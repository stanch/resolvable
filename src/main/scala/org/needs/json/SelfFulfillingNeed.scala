package org.needs.json

import org.needs.{Need, Fulfillable}
import play.api.libs.json.Reads

abstract class SelfFulfillingNeed[A](implicit reads: Reads[Fulfillable[A]]) extends Need[A] with JsonEndpoint {
  use { this }
  from {
    case e if e == this ⇒ asFulfillable[A]
  }
}

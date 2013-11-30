package org.needs.rest

import org.needs.{Fulfillable, Endpoint, Need}
import play.api.libs.json.Reads
import scala.reflect.ClassTag

trait RestNeed[A] extends HasId { self: Need[A] ⇒
  def singleResource[X <: SingleResourceEndpoint](implicit reads: Reads[Fulfillable[A]], T: ClassTag[X]): PartialFunction[Endpoint, Fulfillable[A]] = {
    case x if T.runtimeClass.isInstance(x) && x.asInstanceOf[X].id == self.id ⇒
      x.asInstanceOf[X].asFulfillable[A]
  }
}

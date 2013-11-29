package org.needs

import scala.language.higherKinds
import scala.language.experimental.macros
import play.api.libs.json.Reads
import play.api.libs.functional.{Functor, Applicative}

package object json {
  import Goodness._

  implicit def puréeingReads[M[_], A](implicit app: Applicative[M], reads: Reads[A]): Reads[M[A]] =
    reads.map(app.pure)

  implicit class LiftingReads[A](reads: Reads[A]) {
    def liftAll[M[_]](implicit app: Applicative[M]): Reads[M[Any]] = macro liftAllImpl[M]
  }

  implicit class MappingReads[M[_]: Functor, A](reads: Reads[M[A]]) {
    def fmap[B](f: A ⇒ B): Reads[M[B]] = reads.map(v ⇒ implicitly[Functor[M]].fmap(v, f))
  }
}

package org.needs

import scala.concurrent.{Future, ExecutionContext}
import play.api.libs.functional.{Functor, Applicative}
import scala.async.Async._

trait Fulfillable[A] { self ⇒
  def fulfill(implicit endpoints: List[Endpoint], ec: ExecutionContext): Future[A]
}

object Fulfillable {
  implicit object fulfillableFunctor extends Functor[Fulfillable] {
    def fmap[A, B](m: Fulfillable[A], f: A ⇒ B) = new Fulfillable[B] {
      def fulfill(implicit endpoints: List[Endpoint], ec: ExecutionContext) = m.fulfill.map(f)
    }
  }

  implicit object fulfillableApplicative extends Applicative[Fulfillable] {
    def pure[A](a: A) = new Fulfillable[A] {
      def fulfill(implicit endpoints: List[Endpoint], ec: ExecutionContext) = Future.successful(a)
    }

    def map[A, B](m: Fulfillable[A], f: A ⇒ B) = new Fulfillable[B] {
      def fulfill(implicit endpoints: List[Endpoint], ec: ExecutionContext) = m.fulfill.map(f)
    }

    def apply[A, B](mf: Fulfillable[A ⇒ B], ma: Fulfillable[A]) = new Fulfillable[B] {
      def eitherFuture[X](f: Future[X])(implicit ec: ExecutionContext) =
        f.map(Right[Throwable, X]).recover { case x ⇒ Left(x) }

      def fulfill(implicit endpoints: List[Endpoint], ec: ExecutionContext) = async {
        val f = await(eitherFuture(mf.fulfill))
        val a = await(eitherFuture(ma.fulfill))
        (f, a) match {
          case (Right(x), Right(y)) ⇒ x(y)
          case (Left(Unfulfilled(x)), Left(Unfulfilled(y))) ⇒ throw Unfulfilled(x ::: y)
          case (Left(Unfulfilled(_)), Left(t)) ⇒ throw t // don’t know how to merge Unfulfilled with other Throwables
          case (Left(t), _) ⇒ throw t
          case (_, Left(t)) ⇒ throw t
        }
      }
    }
  }
}
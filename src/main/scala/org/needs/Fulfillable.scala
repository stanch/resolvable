package org.needs

import scala.language.higherKinds
import scala.concurrent.{Future, ExecutionContext}
import play.api.libs.functional.{Functor, Applicative}
import scala.async.Async._

trait Fulfillable[A] { self ⇒
  val default: List[Endpoint]
  def go(implicit ec: ExecutionContext) = fulfill(default, ec)
  def fulfill(implicit endpoints: List[Endpoint], ec: ExecutionContext): Future[A]
}

object Fulfillable {
  // TODO: use CanBuildFrom to generalize to traversables?
  def sequence[A](in: List[Fulfillable[A]], optimizer: Option[List[Endpoint] ⇒ Future[List[Endpoint]]] = None) = new Fulfillable[List[A]] {
    val default = in.flatMap(_.default)
    def fulfill(implicit endpoints: List[Endpoint], ec: ExecutionContext) = async {
      val points = if (optimizer.isDefined) {
        await(optimizer.get(endpoints ::: default))
      } else {
        // move already fetched endpoints forward
        (endpoints ::: default).sortBy(!_.isFetched)
      }
      await(Future.sequence(in.map(_.fulfill(points, ec))))
    }
  }

  implicit object fulfillableFunctor extends Functor[Fulfillable] {
    def fmap[A, B](m: Fulfillable[A], f: A ⇒ B) = new Fulfillable[B] {
      val default = m.default
      def fulfill(implicit endpoints: List[Endpoint], ec: ExecutionContext) = m.fulfill.map(f)
    }
  }

  implicit object fulfillableApplicative extends Applicative[Fulfillable] {
    def pure[A](a: A) = new Fulfillable[A] {
      val default = Nil
      def fulfill(implicit endpoints: List[Endpoint], ec: ExecutionContext) = Future.successful(a)
    }

    def map[A, B](m: Fulfillable[A], f: A ⇒ B) = fulfillableFunctor.fmap(m, f)

    def apply[A, B](mf: Fulfillable[A ⇒ B], ma: Fulfillable[A]) = new Fulfillable[B] {
      // already fetched endpoints will come first
      // TODO: how to inject the optimizer from `sequence`?
      val default = mf.default ::: ma.default

      def eitherFuture[X](f: Future[X])(implicit ec: ExecutionContext) =
        f.map(Right[Throwable, X]).recover { case x ⇒ Left(x) }

      def fulfill(implicit endpoints: List[Endpoint], ec: ExecutionContext) = async {
        // we merge endpoints from mf and ma in hope that they cross-fertilize
        val points = (endpoints ::: default).sortBy(!_.isFetched)
        val f = eitherFuture(mf.fulfill(points, ec))
        val a = eitherFuture(ma.fulfill(points, ec))
        (await(f), await(a)) match {
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
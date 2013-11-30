package org.needs

import scala.language.higherKinds
import scala.concurrent.{Future, ExecutionContext}
import play.api.libs.functional.{Functor, Applicative}
import scala.async.Async._
import scala.annotation.implicitNotFound

trait Fulfillable[A] { self ⇒
  val default: List[Endpoint]
  def go(implicit ec: ExecutionContext) = fulfill(default, ec)
  def fulfill(implicit endpoints: List[Endpoint], ec: ExecutionContext): Future[A]
}

object Fulfillable {
  @implicitNotFound("No optimizer found in scope. You can import the basic one from Optimizers.Implicits.basic")
  type Optimizer = List[Endpoint] ⇒ Future[List[Endpoint]]

  // TODO: use CanBuildFrom to generalize to traversables?
  def sequence[A](in: List[Fulfillable[A]])(implicit optimizer: Optimizer) = new Fulfillable[List[A]] {
    val default = in.flatMap(_.default)
    def fulfill(implicit endpoints: List[Endpoint], ec: ExecutionContext) = async {
      val points = await(optimizer(endpoints ::: default))
      await(Future.sequence(in.map(_.fulfill(points, ec))))
    }
  }

  implicit object fulfillableFunctor extends Functor[Fulfillable] {
    def fmap[A, B](m: Fulfillable[A], f: A ⇒ B) = new Fulfillable[B] {
      val default = m.default
      def fulfill(implicit endpoints: List[Endpoint], ec: ExecutionContext) = m.fulfill.map(f)
      override def toString = s"$m mapped to $f"
    }
  }

  implicit def fulfillableApplicative(implicit optimizer: Optimizer) = new Applicative[Fulfillable] {
    def pure[A](a: A) = new Fulfillable[A] {
      val default = Nil
      def fulfill(implicit endpoints: List[Endpoint], ec: ExecutionContext) = Future.successful(a)
      override def toString = s"purified fulfillable from $a"
    }

    def map[A, B](m: Fulfillable[A], f: A ⇒ B) = fulfillableFunctor.fmap(m, f)

    def apply[A, B](mf: Fulfillable[A ⇒ B], ma: Fulfillable[A]) = new Fulfillable[B] {
      val default = mf.default ::: ma.default

      def eitherFuture[X](f: Future[X])(implicit ec: ExecutionContext) =
        f.map(Right[Throwable, X]).recover { case x ⇒ Left(x) }

      def fulfill(implicit endpoints: List[Endpoint], ec: ExecutionContext) = async {
        // we merge endpoints from mf and ma in hope that they cross-fertilize
        val points = endpoints ::: default//await(optimizer(endpoints ::: default))
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

      override def toString = s"$ma applied to $mf"
    }
  }
}
package org.needs

import scala.language.higherKinds
import scala.concurrent.{Future, ExecutionContext}
import play.api.libs.functional.{Functor, Applicative}
import scala.async.Async._
import scala.annotation.implicitNotFound
import scala.collection.immutable.TreeSet

trait Fulfillable[A] {
  def sources(endpoints: TreeSet[Endpoint])(implicit ec: ExecutionContext): Future[TreeSet[Endpoint]]
  def fulfill(endpoints: TreeSet[Endpoint])(implicit ec: ExecutionContext): Future[A]
  def go(implicit ec: ExecutionContext) = sources(TreeSet.empty).flatMap(fulfill)
}

object Fulfillable {
  // TODO: this is not actually displayed
  @implicitNotFound("No optimizer found in scope. You can import the basic one from Optimizers.Implicits.blank")
  type Optimizer = TreeSet[Endpoint] ⇒ Future[TreeSet[Endpoint]]

  def fromFuture[A](in: ExecutionContext ⇒ Future[A]) = new Fulfillable[A] {
    def sources(endpoints: TreeSet[Endpoint])(implicit ec: ExecutionContext) =
      Future.successful(endpoints)
    def fulfill(endpoints: TreeSet[Endpoint])(implicit ec: ExecutionContext) = in(ec)
  }

  def fromFutureFulfillable[A](in: ExecutionContext ⇒ Future[Fulfillable[A]]) = new Fulfillable[A] {
    def sources(endpoints: TreeSet[Endpoint])(implicit ec: ExecutionContext) =
      Future.successful(endpoints)
    def fulfill(endpoints: TreeSet[Endpoint])(implicit ec: ExecutionContext) =
      in(ec).flatMap(_.fulfill(endpoints))
  }

  // TODO: use CanBuildFrom to generalize to traversables?
  def sequence[A](in: List[Fulfillable[A]]) = new Fulfillable[List[A]] {
    def sources(endpoints: TreeSet[Endpoint])(implicit ec: ExecutionContext) = async {
      val it = in.iterator
      var merged = endpoints
      while (it.hasNext) {
        merged ++= await(it.next().sources(merged))
      }
      println(s"in sequence got $endpoints, returning $merged")
      merged
    }
    def fulfill(endpoints: TreeSet[Endpoint])(implicit ec: ExecutionContext) = {
      println(s"in sequence with $endpoints")
      Future.sequence(in.map(_.fulfill(endpoints)))
    }
    override def toString = s"sequenced fulfillable from $in"
  }

  implicit object fulfillableFunctor extends Functor[Fulfillable] {
    def fmap[A, B](m: Fulfillable[A], f: A ⇒ B) = new Fulfillable[B] {
      def sources(endpoints: TreeSet[Endpoint])(implicit ec: ExecutionContext) =
        m.sources(endpoints)
      def fulfill(endpoints: TreeSet[Endpoint])(implicit ec: ExecutionContext) =
        m.fulfill(endpoints).map(f)
      override def toString = s"$m mapped to $f"
    }
  }

  implicit object fulfillableApplicative extends Applicative[Fulfillable] {
    def pure[A](a: A) = new Fulfillable[A] {
      def sources(endpoints: TreeSet[Endpoint])(implicit ec: ExecutionContext) =
        Future.successful(endpoints)
      def fulfill(endpoints: TreeSet[Endpoint])(implicit ec: ExecutionContext) =
        Future.successful(a)
      override def toString = s"purified fulfillable from $a"
    }

    def map[A, B](m: Fulfillable[A], f: A ⇒ B) = fulfillableFunctor.fmap(m, f)

    def apply[A, B](mf: Fulfillable[A ⇒ B], ma: Fulfillable[A]) = new Fulfillable[B] {
      def sources(endpoints: TreeSet[Endpoint])(implicit ec: ExecutionContext) = async {
        val merged = await(ma.sources(await(mf.sources(endpoints))))
        println(s"in apply got $endpoints, returning $merged")
        merged
      }

      def eitherFuture[X](f: Future[X])(implicit ec: ExecutionContext) =
        f.map(Right[Throwable, X]).recover { case x ⇒ Left(x) }

      def fulfill(endpoints: TreeSet[Endpoint])(implicit ec: ExecutionContext) = async {
        println(s"in apply with $endpoints")
        val s = await(sources(endpoints))
        val f = eitherFuture(mf.fulfill(s))
        val a = eitherFuture(ma.fulfill(s))
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
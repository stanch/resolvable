package org.needs

import scala.language.higherKinds
import scala.concurrent.{Future, ExecutionContext}
import play.api.libs.functional.{Functor, Applicative}
import scala.async.Async._
import scala.util.{Failure, Success}
import com.typesafe.scalalogging.slf4j.Logging

trait Fulfillable[A] extends Logging {
  val sources: EndpointPool
  def fulfill(endpoints: EndpointPool)(implicit ec: ExecutionContext): Future[A]
  def go(implicit ec: ExecutionContext) = fulfill(sources)
}

object Fulfillable {
  def fromFuture[A](in: ExecutionContext ⇒ Future[A]) = new Fulfillable[A] {
    val sources = EndpointPool.empty
    def fulfill(endpoints: EndpointPool)(implicit ec: ExecutionContext) = in(ec)
  }

  def fromFutureFulfillable[A](in: ExecutionContext ⇒ Future[Fulfillable[A]]) = new Fulfillable[A] {
    val sources = EndpointPool.empty
    def fulfill(endpoints: EndpointPool)(implicit ec: ExecutionContext) =
      in(ec).flatMap(_.fulfill(endpoints))
  }

  // TODO: use CanBuildFrom to generalize to traversables?
  def sequence[A](optimize: Option[EndpointPool ⇒ EndpointPool] = None)(in: List[Fulfillable[A]]) = new Fulfillable[List[A]] {
    val sources = EndpointPool.merge(in.map(_.sources))
    def fulfill(endpoints: EndpointPool)(implicit ec: ExecutionContext) = {
      val optimized = optimize.map { o ⇒
        logger.debug("Optimizing endpoints...")
        o(endpoints)
      } getOrElse endpoints
      Future.sequence(in.map(_.fulfill(optimized)))
    }
    override def toString = s"sequenced fulfillable from $in"
  }

  implicit object fulfillableFunctor extends Functor[Fulfillable] {
    def fmap[A, B](m: Fulfillable[A], f: A ⇒ B) = new Fulfillable[B] {
      val sources = m.sources
      def fulfill(endpoints: EndpointPool)(implicit ec: ExecutionContext) =
        m.fulfill(endpoints).map(f)
      override def toString = s"$m mapped to $f"
    }
  }

  implicit object fulfillableApplicative extends Applicative[Fulfillable] {
    def pure[A](a: A) = new Fulfillable[A] {
      val sources = EndpointPool.empty
      def fulfill(endpoints: EndpointPool)(implicit ec: ExecutionContext) =
        Future.successful(a)
      override def toString = s"purified fulfillable from $a"
    }

    def map[A, B](m: Fulfillable[A], f: A ⇒ B) = fulfillableFunctor.fmap(m, f)

    def apply[A, B](mf: Fulfillable[A ⇒ B], ma: Fulfillable[A]) = new Fulfillable[B] {
      val sources = mf.sources ++ ma.sources

      def tryFuture[X](f: Future[X])(implicit ec: ExecutionContext) =
        f.map(x ⇒ Success(x)).recover { case x ⇒ Failure(x) }

      def fulfill(endpoints: EndpointPool)(implicit ec: ExecutionContext) = async {
        val s = endpoints ++ sources
        //logger.debug(s"using $s")
        val f = tryFuture(mf.fulfill(s))
        val a = tryFuture(ma.fulfill(s))
        (await(f), await(a)) match {
          case (Success(x), Success(y)) ⇒ x(y)
          case (Failure(Unfulfilled(x)), Failure(Unfulfilled(y))) ⇒ throw Unfulfilled(x ::: y)
          case (Failure(Unfulfilled(_)), Failure(t)) ⇒ throw t // don’t know how to merge Unfulfilled with other Throwables
          case (Failure(t), _) ⇒ throw t
          case (_, Failure(t)) ⇒ throw t
        }
      }

      override def toString = s"$ma applied to $mf"
    }
  }
}
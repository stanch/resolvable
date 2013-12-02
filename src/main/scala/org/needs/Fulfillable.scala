package org.needs

import scala.language.higherKinds
import scala.concurrent.{Future, ExecutionContext}
import play.api.libs.functional.{Functor, Applicative}
import scala.async.Async._
import scala.util.{Failure, Success}

/** Fulfillable is something that can be fetched using a pool of Endpoints */
trait Fulfillable[A] {
  /** Default sources of fulfillment */
  protected val sources: EndpointPool

  /** Optimize fulfillment by adding aggregated endpoints */
  protected def addOptimal(endpoints: EndpointPool)(implicit ec: ExecutionContext): Future[EndpointPool]

  /** Fulfill using the specified endpoints */
  def fulfill(endpoints: EndpointPool)(implicit ec: ExecutionContext): Future[A]

  /** Fulfill using the default sources */
  def go(implicit ec: ExecutionContext) = fulfill(sources)
}

object Fulfillable {
  /** Create a Fulfillable from existing ExecutionContext and Future */
  def fromFuture[A](in: ExecutionContext ⇒ Future[A]) = new Fulfillable[A] {
    protected val sources = EndpointPool.empty
    protected def addOptimal(endpoints: EndpointPool)(implicit ec: ExecutionContext) =
      Future.successful(endpoints)
    def fulfill(endpoints: EndpointPool)(implicit ec: ExecutionContext) = in(ec)
  }

  /** Jump over the Future monad */
  def jumpOverFuture[A](in: ExecutionContext ⇒ Future[Fulfillable[A]]) = new Fulfillable[A] {
    protected val sources = EndpointPool.empty
    protected def addOptimal(endpoints: EndpointPool)(implicit ec: ExecutionContext) =
      Future.successful(endpoints)
    def fulfill(endpoints: EndpointPool)(implicit ec: ExecutionContext) =
      in(ec).flatMap(_.fulfill(endpoints))
  }

  // TODO: use CanBuildFrom to generalize to traversables?
  /** Jump over a List */
  def jumpOverList[A](in: List[Fulfillable[A]]) = new Fulfillable[List[A]] {
    protected val sources = EndpointPool.merge(in.map(_.sources))
    protected def addOptimal(endpoints: EndpointPool)(implicit ec: ExecutionContext) = async {
      var optimized = endpoints
      val it = in.iterator
      while (it.hasNext) {
        optimized = await(it.next().addOptimal(optimized))
      }
      optimized
    }
    def fulfill(endpoints: EndpointPool)(implicit ec: ExecutionContext) = async {
      val optimized = await(addOptimal(endpoints))
      await(Future.sequence(in.map(_.fulfill(optimized))))
    }
  }

  /** A Functor instance for Fulfillable */
  implicit object fulfillableFunctor extends Functor[Fulfillable] {
    def fmap[A, B](m: Fulfillable[A], f: A ⇒ B) = new Fulfillable[B] {
      protected val sources = m.sources
      protected def addOptimal(endpoints: EndpointPool)(implicit ec: ExecutionContext) =
        m.addOptimal(endpoints)
      def fulfill(endpoints: EndpointPool)(implicit ec: ExecutionContext) =
        m.fulfill(endpoints).map(f)
    }
  }

  /** An Applicative instance for Fulfillable */
  implicit object fulfillableApplicative extends Applicative[Fulfillable] {
    def pure[A](a: A) = new Fulfillable[A] {
      protected val sources = EndpointPool.empty
      protected def addOptimal(endpoints: EndpointPool)(implicit ec: ExecutionContext) =
        Future.successful(endpoints)
      def fulfill(endpoints: EndpointPool)(implicit ec: ExecutionContext) =
        Future.successful(a)
    }

    def map[A, B](m: Fulfillable[A], f: A ⇒ B) = fulfillableFunctor.fmap(m, f)

    def apply[A, B](mf: Fulfillable[A ⇒ B], ma: Fulfillable[A]) = new Fulfillable[B] {
      protected val sources = mf.sources ++ ma.sources

      protected def addOptimal(endpoints: EndpointPool)(implicit ec: ExecutionContext) = async {
        await(ma.addOptimal(await(mf.addOptimal(endpoints))))
      }

      def tryFuture[X](f: Future[X])(implicit ec: ExecutionContext) =
        f.map(x ⇒ Success(x)).recover { case x ⇒ Failure(x) }

      def fulfill(endpoints: EndpointPool)(implicit ec: ExecutionContext) = async {
        val s = await(addOptimal(endpoints ++ sources))
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
    }
  }
}
package org.needs

import scala.language.experimental.macros
import scala.language.higherKinds
import scala.concurrent.{Future, ExecutionContext}
import play.api.libs.functional.{Functor, Applicative}
import scala.async.Async._
import scala.util.{Failure, Success}
import scala.reflect.macros.Context
import play.api.libs.json.Reads

/** Fulfillable is something that can be fetched using a pool of Endpoints */
trait Fulfillable[A] {
  /** Default sources of fulfillment */
  val sources: EndpointPool

  /** Optimize fulfillment by adding aggregated endpoints */
  def addOptimal(endpoints: EndpointPool)(implicit ec: ExecutionContext): Future[EndpointPool]

  /** Fulfill using the specified endpoints */
  def fulfill(endpoints: EndpointPool)(implicit ec: ExecutionContext): Future[A]

  /** Fulfill using the default sources */
  def go(implicit ec: ExecutionContext) = fulfill(sources)

  /** Map over a function f */
  def map[B](f: A ⇒ B) = Fulfillable.map(this, f)

  /** Flatmap over a function f */
  def flatMap[B](f: A ⇒ Fulfillable[B]) = Fulfillable.flatMap(this, f)
}

object Fulfillable {
  import FulfillableMacros._

  /** Create a Reads[Fulfillable[A]] from Reads combinators */
  def reads[A](builder: Any): Reads[Fulfillable[A]] = macro readsImpl[A]

  /** Create a Reads[Fulfillable[A]] from Reads[A] */
  implicit def reads[A](implicit reads: Reads[A]): Reads[Fulfillable[A]] =
    reads.map(applicative.pure)

  /** Shorthand map function */
  def map[A, B](m: Fulfillable[A], f: A ⇒ B) = new Fulfillable[B] {
    val sources = m.sources
    def addOptimal(endpoints: EndpointPool)(implicit ec: ExecutionContext) =
      m.addOptimal(endpoints)
    def fulfill(endpoints: EndpointPool)(implicit ec: ExecutionContext) =
      m.fulfill(endpoints).map(f)
  }

  /** Shorthand flatMap function */
  def flatMap[A, B](m: Fulfillable[A], f: A ⇒ Fulfillable[B]) = new Fulfillable[B] {
    val sources = m.sources
    def addOptimal(endpoints: EndpointPool)(implicit ec: ExecutionContext) =
      m.addOptimal(endpoints)
    def fulfill(endpoints: EndpointPool)(implicit ec: ExecutionContext) = async {
      val mb = f(await(m.fulfill(endpoints)))
      val points = await(mb.addOptimal(endpoints ++ mb.sources))
      await(mb.fulfill(points))
    }
  }

  /** Create a Fulfillable from existing ExecutionContext and Future */
  def fromFuture[A](in: ExecutionContext ⇒ Future[A]) = new Fulfillable[A] {
    val sources = EndpointPool.empty
    def addOptimal(endpoints: EndpointPool)(implicit ec: ExecutionContext) =
      Future.successful(endpoints)
    def fulfill(endpoints: EndpointPool)(implicit ec: ExecutionContext) = in(ec)
  }

  /** Jump over the Future monad */
  def jumpFuture[A](in: ExecutionContext ⇒ Future[Fulfillable[A]]) = new Fulfillable[A] {
    val sources = EndpointPool.empty
    def addOptimal(endpoints: EndpointPool)(implicit ec: ExecutionContext) =
      Future.successful(endpoints)
    def fulfill(endpoints: EndpointPool)(implicit ec: ExecutionContext) =
      in(ec).flatMap(_.fulfill(endpoints))
  }

  // TODO: use CanBuildFrom to generalize to traversables?
  /** Jump over a List */
  def jumpList[A](in: List[Fulfillable[A]]) = new Fulfillable[List[A]] {
    val sources = EndpointPool.merge(in.map(_.sources))
    def addOptimal(endpoints: EndpointPool)(implicit ec: ExecutionContext) = async {
      var optimized = endpoints
      val it = in.iterator
      while (it.hasNext) {
        optimized = await(it.next().addOptimal(optimized))
      }
      optimized
    }
    def fulfill(endpoints: EndpointPool)(implicit ec: ExecutionContext) = async {
      val optimized = await(addOptimal(endpoints ++ sources))
      await(Future.sequence(in.map(_.fulfill(optimized))))
    }
  }

  /** Jump over an Option */
  def jumpOption[A](in: Option[Fulfillable[A]]) = new Fulfillable[Option[A]] {
    val sources = in.map(_.sources).getOrElse(EndpointPool.empty)
    def addOptimal(endpoints: EndpointPool)(implicit ec: ExecutionContext) =
      in.map(_.addOptimal(endpoints)).getOrElse(Future.successful(endpoints))
    def fulfill(endpoints: EndpointPool)(implicit ec: ExecutionContext) =
      in.map(_.fulfill(endpoints).map(Some.apply)).getOrElse(Future.successful(None))
  }

  /** A Functor instance for Fulfillable */
  implicit object functor extends Functor[Fulfillable] {
    def fmap[A, B](m: Fulfillable[A], f: A ⇒ B) = Fulfillable.map(m, f)
  }

  /** An Applicative instance for Fulfillable */
  implicit object applicative extends Applicative[Fulfillable] {
    def pure[A](a: A) = new Fulfillable[A] {
      val sources = EndpointPool.empty
      def addOptimal(endpoints: EndpointPool)(implicit ec: ExecutionContext) =
        Future.successful(endpoints)
      def fulfill(endpoints: EndpointPool)(implicit ec: ExecutionContext) =
        Future.successful(a)
    }

    def map[A, B](m: Fulfillable[A], f: A ⇒ B) = Fulfillable.map(m, f)

    def apply[A, B](mf: Fulfillable[A ⇒ B], ma: Fulfillable[A]) = new Fulfillable[B] {
      val sources = mf.sources ++ ma.sources

      def addOptimal(endpoints: EndpointPool)(implicit ec: ExecutionContext) = async {
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
          case (Failure(Chagrin(x)), Failure(Chagrin(y))) ⇒ throw Chagrin(x ::: y)
          case (Failure(Chagrin(_)), Failure(t)) ⇒ throw t // don’t know how to merge Unfulfilled with other Throwables
          case (Failure(t), _) ⇒ throw t
          case (_, Failure(t)) ⇒ throw t
        }
      }
    }
  }
}

object FulfillableMacros {
  def readsImpl[A: c.WeakTypeTag](c: Context)(builder: c.Expr[Any]) = {
    import c.universe._
    val tupled = scala.util.Try {
      c.typeCheck(q"{ import play.api.libs.functional.lifting._; $builder.tupled.map(_.liftAll[org.needs.Fulfillable]) }")
    } getOrElse {
      c.abort(builder.tree.pos, "Reads builder expected")
    }
    val TypeRef(_, _, List(TypeRef(_, _, List(TypeRef(_, _, types))))) = tupled.tpe
    val args = types.map(_ ⇒ c.fresh("arg"))
    val idents = args.map(a ⇒ Ident(a))
    val sign = args zip types map { case (a, t) ⇒ q"val ${newTermName(a)}: $t"}
    val res = scala.util.Try {
      val constructor = q"{ (..$sign) ⇒ ${weakTypeOf[A].typeSymbol.companionSymbol}.apply(..$idents) }.tupled"
      c.typeCheck(q"$tupled.map(org.needs.Fulfillable.map(_, $constructor))")
    } getOrElse {
      c.abort(c.enclosingPosition, s"Could not generate Reads[Fulfillable[${weakTypeOf[A]}]]")
    }
    c.Expr[Reads[Fulfillable[A]]](res)
  }
}
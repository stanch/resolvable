package org.needs

import scala.language.experimental.macros
import scala.language.higherKinds
import scala.concurrent.{Future, ExecutionContext}
import scala.async.Async._
import scala.util.{Failure, Success}
import scala.reflect.macros.Context
import play.api.libs.functional.{Functor, Applicative}
import play.api.data.mapping.Rule

/** Resolvable is something that can be fetched using a pool of Endpoints */
trait Resolvable[A] {
  /** Enables resolution by adding default endpoints */
  val initiator: EndpointPoolInitiator

  /** Optimizes resolution by adding aggregated endpoints */
  val optimizer: EndpointPoolOptimizer

  /** Resolve using the specified endpoints */
  def resolve(endpoints: EndpointPool)(implicit ec: ExecutionContext): Future[A]

  /** Resolve using the initial endpoints */
  final def go(implicit ec: ExecutionContext) = initiator.initPool(ec).flatMap(resolve)

  /** Map over a function f */
  final def map[B](f: A ⇒ B): Resolvable[B] = MappedResolvable(this, f)

  /** FlatMap over a function f */
  final def flatMap[B](f: A ⇒ Resolvable[B]): Resolvable[B] = FlatMappedResolvable(this, f)

  /** Provide an alternative */
  final def orElse(alternative: Resolvable[A]): Resolvable[A] = AlternativeResolvable(this, alternative)
}

final case class MappedResolvable[A, B](ma: Resolvable[A], f: A ⇒ B) extends Resolvable[B] {
  val initiator = ma.initiator
  val optimizer = ma.optimizer
  def resolve(endpoints: EndpointPool)(implicit ec: ExecutionContext) = ma.resolve(endpoints).map(f)
}

final case class FlatMappedResolvable[A, B](ma: Resolvable[A], f: A ⇒ Resolvable[B]) extends Resolvable[B] {
  val initiator = ma.initiator
  val optimizer = ma.optimizer
  def resolve(endpoints: EndpointPool)(implicit ec: ExecutionContext) = async {
    val mb = f(await(ma.resolve(endpoints)))
    val points = await(mb.optimizer.addOptimal(endpoints ++ await(mb.initiator.initPool(ec)), ec))
    await(mb.resolve(points))
  }
}

final case class AlternativeResolvable[A](m1: Resolvable[A], m2: Resolvable[A]) extends Resolvable[A] {
  val initiator = m1.initiator + m2.initiator
  val optimizer = m1.optimizer andThen m2.optimizer
  // TODO: concatenate failures
  def resolve(endpoints: EndpointPool)(implicit ec: ExecutionContext) = m1.resolve(endpoints).recoverWith {
    case _ ⇒ m2.resolve(endpoints)
  }
}

final case class PureResolvable[A](a: A) extends Resolvable[A] {
  val initiator = EndpointPoolInitiator.none
  val optimizer = EndpointPoolOptimizer.none
  def resolve(endpoints: EndpointPool)(implicit ec: ExecutionContext) = Future.successful(a)
}

final case class AppliedResolvable[A, B](mf: Resolvable[A ⇒ B], ma: Resolvable[A]) extends Resolvable[B] {
  val initiator = mf.initiator + ma.initiator
  val optimizer = mf.optimizer andThen ma.optimizer

  def tryFuture[X](f: Future[X])(implicit ec: ExecutionContext) =
    f.map(x ⇒ Success(x)).recover { case x ⇒ Failure(x) }

  def resolve(endpoints: EndpointPool)(implicit ec: ExecutionContext) = async {
    val s = await(optimizer.addOptimal(endpoints ++ await(initiator.initPool(ec)), ec))
    val f = tryFuture(mf.resolve(s))
    val a = tryFuture(ma.resolve(s))
    // TODO: concatenate errors
    (await(f), await(a)) match {
      case (Success(x), Success(y)) ⇒ x(y)
      case (Failure(t), _) ⇒ throw t
      case (_, Failure(t)) ⇒ throw t
    }
  }
}

final case class FutureResolvable[A](in: ExecutionContext ⇒ Future[Resolvable[A]]) extends Resolvable[A] {
  val initiator = EndpointPoolInitiator.future(implicit ec ⇒ in(ec).map(_.initiator))
  val optimizer = EndpointPoolOptimizer.future(implicit ec ⇒ in(ec).map(_.optimizer))
  def resolve(endpoints: EndpointPool)(implicit ec: ExecutionContext) = in(ec).flatMap(_.resolve(endpoints))
}

// TODO: use CanBuildFrom to generalize to traversables?
final case class ListResolvable[A](in: List[Resolvable[A]]) extends Resolvable[List[A]] {
  val initiator = EndpointPoolInitiator.merge(in.map(_.initiator))
  val optimizer = EndpointPoolOptimizer.chain(in.map(_.optimizer))
  def resolve(endpoints: EndpointPool)(implicit ec: ExecutionContext) = async {
    val optimized = await(optimizer.addOptimal(endpoints ++ await(initiator.initPool(ec)), ec))
    await(Future.sequence(in.map(_.resolve(optimized))))
  }
}

final case class OptionResolvable[A](in: Option[Resolvable[A]]) extends Resolvable[Option[A]] {
  val initiator = in.map(_.initiator).getOrElse(EndpointPoolInitiator.none)
  val optimizer = in.map(_.optimizer).getOrElse(EndpointPoolOptimizer.none)
  def resolve(endpoints: EndpointPool)(implicit ec: ExecutionContext) =
    in.map(_.resolve(endpoints).map(Some.apply)).getOrElse(Future.successful(None))
}

object EndpointDataResolvable {
  def apply[A, E <: Endpoint](in: E)(implicit rule: Rule[E#Data, Resolvable[A]]): Resolvable[A] =
    FutureResolvable { implicit ec ⇒ in.data.map(rule.validate).map(_.get) }

  def apply[A, E <: Endpoint, D](in: E)(path: E#Data ⇒ D)(implicit rule: Rule[D, Resolvable[A]]): Resolvable[A] =
    FutureResolvable { implicit ec ⇒ in.data.map(path).map(rule.validate).map(_.get) }
}

object Resolvable {
  import ResolvableMacros._

  /** Create a Rule[I, Resolvable[O]] from builder */
  def rule[I, O](builder: Any): Rule[I, Resolvable[O]] = macro ruleImpl[I, O]

  /** Create a Rule[I, Resolvable[O]] from Rule[I, O] */
  implicit def rule[I, O](implicit rule: Rule[I, O]): Rule[I, Resolvable[O]] = rule.fmap(PureResolvable.apply)

  /** Jump over the Future monad */
  def jumpFuture[A](in: ExecutionContext ⇒ Future[Resolvable[A]]) = FutureResolvable(in)

  /** Jump over a List */
  def jumpList[A](in: List[Resolvable[A]]) = ListResolvable(in)

  /** Jump over an Option */
  def jumpOption[A](in: Option[Resolvable[A]]) = OptionResolvable(in)

  /** A Functor instance for Resolvable */
  implicit object functor extends Functor[Resolvable] {
    def fmap[A, B](m: Resolvable[A], f: A ⇒ B): Resolvable[B] = MappedResolvable(m, f)
  }

  /** An Applicative instance for Resolvable */
  implicit object applicative extends Applicative[Resolvable] {
    def pure[A](a: A): Resolvable[A] = PureResolvable(a)
    def map[A, B](m: Resolvable[A], f: A ⇒ B): Resolvable[B] = MappedResolvable(m, f)
    def apply[A, B](mf: Resolvable[A ⇒ B], ma: Resolvable[A]): Resolvable[B] = AppliedResolvable(mf, ma)
  }
}

object ResolvableMacros {
  def ruleImpl[I: c.WeakTypeTag, O: c.WeakTypeTag](c: Context)(builder: c.Expr[Any]) = {
    import c.universe._
    val tupled = scala.util.Try {
      c.typeCheck(q"{ import play.api.libs.functional.lifting._; $builder.tupled.fmap(_.liftAll[org.needs.Resolvable]) }")
    } getOrElse {
      c.abort(builder.tree.pos, "Builder expected")
    }
    val TypeRef(_, _, List(TypeRef(_, _, _), TypeRef(_, _, List(TypeRef(_, _, types))))) = tupled.tpe
    val args = types.map(_ ⇒ c.fresh("arg"))
    val idents = args.map(a ⇒ Ident(a))
    val sign = args zip types map { case (a, t) ⇒ q"val ${newTermName(a)}: $t"}
    val res = scala.util.Try {
      val constructor = q"{ (..$sign) ⇒ ${weakTypeOf[O].typeSymbol.companionSymbol}.apply(..$idents) }.tupled"
      c.typeCheck(q"$tupled.fmap(_.map($constructor))")
    } getOrElse {
      c.abort(c.enclosingPosition, s"Could not generate Rule[${weakTypeOf[I]}, Resolvable[${weakTypeOf[O]}]]")
    }
    c.Expr[Rule[I, Resolvable[O]]](res)
  }
}
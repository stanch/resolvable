package org.needs

import scala.language.experimental.macros
import scala.language.higherKinds
import scala.concurrent.{Future, ExecutionContext}
import scala.async.Async._
import scala.util.{Failure, Success}
import scala.reflect.macros.Context
import play.api.libs.functional.{Functor, Applicative}
import play.api.data.mapping.{Reader, Rule}

/** A Resolution is either a pure value, or a `Resolvable` value,
  * which is propagated from the next layer of dependencies.
  * */
sealed trait Resolution[+A] {
  def map[B](f: A ⇒ B): Resolution[B]
  def mapR[B](f: A ⇒ Resolvable[B]): Resolution[B]
  def reResolve(endpoints: EndpointPool)(implicit ec: ExecutionContext): Future[Resolution[A]]
  def isResolved: Boolean
}
object Resolution {
  case class Result[A](r: A) extends Resolution[A] {
    def map[B](f: A ⇒ B) = Result(f(r))
    def mapR[B](f: A ⇒ Resolvable[B]) = Propagate(f(r))
    def reResolve(endpoints: EndpointPool)(implicit ec: ExecutionContext) = Future.successful(Result(r))
    def isResolved = true
  }
  case class Propagate[A](r: Resolvable[A]) extends Resolution[A] {
    def map[B](f: A ⇒ B) = Propagate(r.map(f))
    def mapR[B](f: A ⇒ Resolvable[B]) = Propagate(r.flatMap(f))
    def reResolve(endpoints: EndpointPool)(implicit ec: ExecutionContext) = r.resolve(endpoints)
    def isResolved = false
  }
  /** Recursively resolve a list of resolutions */
  def exhaustList[A](endpoints: EndpointPool, xs: List[Resolution[A]])(implicit ec: ExecutionContext): Future[List[A]] = async {
    if (xs.forall(_.isResolved)) {
      // everything is resolved, return
      xs flatMap { case Resolution.Result(r) ⇒ r :: Nil; case _ ⇒ Nil }
    } else {
      // compose managers of all propagated resolvables
      val man = EndpointPoolManager.compose(xs.flatMap {
        case Resolution.Result(_) ⇒ Nil
        case Resolution.Propagate(r) ⇒ r.manager :: Nil
      }.toSeq)

      // process the pool again
      val points = await(man.process(endpoints))

      // resolve the ones that were not resolved
      val next = await(Future.sequence(xs.map(_.reResolve(points))))

      // loop
      await(exhaustList(points, next))
    }
  }
}

/** Resolvable is something that can be fetched using a pool of Endpoints */
trait Resolvable[+A] {
  /** Manages the endpoint pool */
  def manager: EndpointPoolManager

  /** Resolve using the specified endpoints */
  def resolve(endpoints: EndpointPool)(implicit ec: ExecutionContext): Future[Resolution[A]]

  /** Resolve using the initial endpoints */
  final def go(implicit ec: ExecutionContext): Future[A] = async {
    val points = await(manager.initial)
    val res = await(resolve(points))
    // recursively pull and resolve all bottom layers
    await(Resolution.exhaustList(points, List(res))).head
  }

  /** Map over a function f */
  final def map[B](f: A ⇒ B): Resolvable[B] = MappedResolvable(this, f)

  /** FlatMap over a function f */
  final def flatMap[B](f: A ⇒ Resolvable[B]): Resolvable[B] = FlatMappedResolvable(this, f)

  /** Provide an alternative */
  final def orElse[B >: A](alternative: Resolvable[B]): Resolvable[B] = AlternativeResolvable(this, alternative)
}

final case class MappedResolvable[A, B](ma: Resolvable[A], f: A ⇒ B) extends Resolvable[B] {
  val manager = ma.manager
  def resolve(endpoints: EndpointPool)(implicit ec: ExecutionContext) = ma.resolve(endpoints).map(_.map(f))
}

final case class FlatMappedResolvable[A, B](ma: Resolvable[A], f: A ⇒ Resolvable[B]) extends Resolvable[B] {
  val manager = ma.manager
  def resolve(endpoints: EndpointPool)(implicit ec: ExecutionContext) = ma.resolve(endpoints).map(_.mapR(f))
}

final case class AlternativeResolvable[A, B >: A](m1: Resolvable[A], m2: Resolvable[B]) extends Resolvable[B] {
  val manager = m1.manager + m2.manager
  // TODO: concatenate failures
  def resolve(endpoints: EndpointPool)(implicit ec: ExecutionContext) = m1.resolve(endpoints).recoverWith {
    case _ ⇒ m2.resolve(endpoints)
  }
}

final case class PureResolvable[A](a: A) extends Resolvable[A] {
  val manager = EndpointPoolManager.none
  def resolve(endpoints: EndpointPool)(implicit ec: ExecutionContext) = Future.successful(Resolution.Result(a))
}

final case class AppliedResolvable[A, B](mf: Resolvable[A ⇒ B], ma: Resolvable[A]) extends Resolvable[B] {
  val manager = mf.manager + ma.manager

  def tryFuture[X](f: Future[X])(implicit ec: ExecutionContext) =
    f.map(x ⇒ Success(x)).recover { case x ⇒ Failure(x) }

  def resolve(endpoints: EndpointPool)(implicit ec: ExecutionContext) = async {
    val points = await(manager.process(endpoints))
    val f = tryFuture(mf.resolve(points))
    val a = tryFuture(ma.resolve(points))
    // TODO: concatenate errors
    (await(f), await(a)) match {
      case (Success(x), Success(y)) ⇒ (x, y) match {
        case (Resolution.Propagate(mf1), Resolution.Propagate(ma1)) ⇒ Resolution.Propagate(AppliedResolvable(mf1, ma1))
        case (Resolution.Propagate(mf1), Resolution.Result(a1)) ⇒ Resolution.Propagate(mf1.map(_(a1)))
        case (Resolution.Result(f1), Resolution.Propagate(ma1)) ⇒ Resolution.Propagate(ma1.map(f1))
        case (Resolution.Result(f1), Resolution.Result(a1)) ⇒ Resolution.Result(f1(a1))
      }
      case (Failure(t), _) ⇒ throw t
      case (_, Failure(t)) ⇒ throw t
    }
  }
}

final case class FutureResolvable[A](in: ExecutionContext ⇒ Future[Resolvable[A]]) extends Resolvable[A] {
  val manager = EndpointPoolManager.future(implicit ec ⇒ in(ec).map(_.manager))
  def resolve(endpoints: EndpointPool)(implicit ec: ExecutionContext) = in(ec).flatMap(_.resolve(endpoints))
}

// TODO: use CanBuildFrom to generalize to traversables?
final case class ListResolvable[A](in: List[Resolvable[A]]) extends Resolvable[List[A]] {
  val manager = EndpointPoolManager.compose(in.map(_.manager))
  def resolve(endpoints: EndpointPool)(implicit ec: ExecutionContext) = async {
    val points = await(manager.process(endpoints))
    val res = await(Future.sequence(in.map { x: Resolvable[A] ⇒ x.resolve(points) }))
    // recursively pull and resolve all bottom layers
    Resolution.Result(await(Resolution.exhaustList(points, res.toList)))
  }
}

final case class OptionResolvable[A](in: Option[Resolvable[A]]) extends Resolvable[Option[A]] {
  val manager = in.map(_.manager).getOrElse(EndpointPoolManager.none)
  def resolve(endpoints: EndpointPool)(implicit ec: ExecutionContext) =
    in.map(_.resolve(endpoints).map(_.map(Some.apply))).getOrElse(Future.successful(Resolution.Result(None)))
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
  def rule[I, O](builder: Reader[I] ⇒ Any): Rule[I, Resolvable[O]] = macro ruleImpl[I, O]

  /** Create a Rule[I, Resolvable[O]] from Rule[I, O] */
  implicit def pureRule[I, O](implicit rule: Rule[I, O]): Rule[I, Resolvable[O]] = rule.fmap(PureResolvable.apply)

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
  def ruleImpl[I: c.WeakTypeTag, O: c.WeakTypeTag](c: Context)(builder: c.Expr[Reader[I] ⇒ Any]) = {
    import c.universe._
    val reader = newTermName(c.fresh("reader"))
    val tupled = scala.util.Try {
      c.typeCheck(q"{ $reader: play.api.data.mapping.Reader[${weakTypeOf[I]}] ⇒ import play.api.libs.functional.lifting._; $builder($reader).tupled.fmap(_.liftAll[org.needs.Resolvable]) }")
    } getOrElse {
      c.abort(builder.tree.pos, "Builder expected")
    }
    val TypeRef(_, _, List(_, TypeRef(_, _, List(TypeRef(_, _, _), TypeRef(_, _, List(TypeRef(_, _, types))))))) = tupled.tpe
    val args = types.map(_ ⇒ c.fresh("arg"))
    val idents = args.map(a ⇒ Ident(a))
    val sign = args zip types map { case (a, t) ⇒ q"val ${newTermName(a)}: $t"}
    val res = scala.util.Try {
      val constructor = q"{ (..$sign) ⇒ ${weakTypeOf[O].typeSymbol.companionSymbol}.apply(..$idents) }.tupled"
      c.typeCheck(q"{ $reader: play.api.data.mapping.Reader[${weakTypeOf[I]}] ⇒ $tupled($reader).fmap(_.map($constructor)) }")
    } getOrElse {
      c.abort(c.enclosingPosition, s"Could not generate Rule[${weakTypeOf[I]}, Resolvable[${weakTypeOf[O]}]]")
    }
    c.Expr[Rule[I, Resolvable[O]]](q"play.api.data.mapping.From[${weakTypeOf[I]}]($res)")
  }
}
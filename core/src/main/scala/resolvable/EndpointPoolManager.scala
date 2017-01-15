package resolvable

import scala.concurrent.{Future, ExecutionContext}
import scala.async.Async._

trait EndpointPoolManager {
  /** Initial endpoints to add to the pool */
  def initial(implicit ec: ExecutionContext): Future[EndpointPool]

  /** Optimized (e.g. aggregated) endpoints to add to the pool */
  def optimal(implicit ec: ExecutionContext): EndpointPool ⇒ Future[EndpointPool]

  /** Process the pool by adding the initial endpoints and then optimizing it */
  final def process(pool: EndpointPool)(implicit ec: ExecutionContext) = async {
    val i = await(initial)
    pool ++ i ++ await(optimal.apply(i))
  }

  /** Compose with another manager */
  final def +(that: EndpointPoolManager): EndpointPoolManager =
    ComposedEndpointPoolManager(this, that)

  /** Add pool initializer */
  final def addInitializer(initializer: Future[EndpointPool]): EndpointPoolManager =
    InitializedEndpointPoolManager(this, initializer)

  /** Add pool optimizer */
  final def addOptimizer(optimizer: (ExecutionContext, EndpointPool) ⇒ Future[EndpointPool]): EndpointPoolManager =
    OptimizedEndpointPoolManager(this, optimizer)

  implicit class FuturePoolAddition(x: Future[EndpointPool]) {
    def ++(y: Future[EndpointPool])(implicit ec: ExecutionContext) = x zip y map { case (a, b) ⇒ a ++ b }
  }
}

private[resolvable] final case class ComposedEndpointPoolManager(m1: EndpointPoolManager, m2: EndpointPoolManager) extends EndpointPoolManager {
  def initial(implicit ec: ExecutionContext) = m1.initial ++ m2.initial
  def optimal(implicit ec: ExecutionContext) = pool ⇒ m1.optimal.apply(pool) ++ m2.optimal.apply(pool)
}

private[resolvable] final case class InitializedEndpointPoolManager(m: EndpointPoolManager, i: Future[EndpointPool]) extends EndpointPoolManager {
  def initial(implicit ec: ExecutionContext) = m.initial ++ i
  def optimal(implicit ec: ExecutionContext) = m.optimal
}

private[resolvable] final case class OptimizedEndpointPoolManager(m: EndpointPoolManager, o: (ExecutionContext, EndpointPool) ⇒ Future[EndpointPool]) extends EndpointPoolManager {
  def initial(implicit ec: ExecutionContext) = m.initial
  def optimal(implicit ec: ExecutionContext) = pool ⇒ m.optimal.apply(pool) ++ o.apply(ec, pool)
}

private[resolvable] final case class FutureEndpointPoolManager(f: ExecutionContext ⇒ Future[EndpointPoolManager]) extends EndpointPoolManager {
  def initial(implicit ec: ExecutionContext) = f(ec).flatMap(_.initial)
  def optimal(implicit ec: ExecutionContext) = pool ⇒ f(ec).flatMap(_.optimal.apply(pool))
}

object EndpointPoolManager {
  /** A manager that does nothing */
  object none extends EndpointPoolManager {
    def initial(implicit ec: ExecutionContext) = Future.successful(EndpointPool.empty)
    def optimal(implicit ec: ExecutionContext) = pool ⇒ Future.successful(pool)
  }

  /** A manager with some initial endpoints */
  def apply(initial: EndpointPool): EndpointPoolManager =
    none.addInitializer(Future.successful(initial))

  /** Wait for the manager from the future! */
  def future(futureManager: ExecutionContext ⇒ Future[EndpointPoolManager]): EndpointPoolManager =
    FutureEndpointPoolManager(futureManager)

  /** Compose several managers together */
  def compose(managers: Seq[EndpointPoolManager]) = managers.fold(none)(_ + _)
}

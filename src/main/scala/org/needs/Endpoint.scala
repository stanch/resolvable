package org.needs

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

/** Represents something that can deliver data */
trait Endpoint {
  /** Data type of the endpoint */
  type Data

  /** The implementation of data fetching */
  protected def fetch(implicit ec: ExecutionContext): Future[Data]

  /** Endpoint logger. See [[EndpointLogger]] */
  protected val logger: EndpointLogger

  /** Tells if the fetching process has started */
  final def isFetched = _fetchingLock.synchronized(_fetched.isDefined)

  // all this could be a lazy val, if not for the ExecutionContext
  final private val _fetchingLock = new Object
  final private var _fetched: Option[Future[Data]] = None

  /** Fetched data */
  final def data(implicit ec: ExecutionContext) = _fetchingLock.synchronized {
    if (_fetched.isEmpty) {
      logger.logStart(this)
      val f = fetch
      f onComplete {
        case Success(d) ⇒ logger.logSuccess(this)(d)
        case Failure(t) ⇒ logger.logFailure(this)(t)
      }
      _fetched = Some(f)
    }
    _fetched.get
  }
}

/** Endpoint logger */
trait EndpointLogger {
  /** Called by an endpoint when it starts fetching */
  def logStart(pt: Endpoint): Unit
  
  /** Called by an endpoint when it finishes fetching */
  def logSuccess(pt: Endpoint)(data: pt.Data): Unit

  /** Called by an endpoint when it fails to fetch */
  def logFailure(pt: Endpoint)(error: Throwable): Unit
}

object EndpointLogger{
  /** An endpoint logger that does nothing */
  object none extends EndpointLogger {
    def logStart(pt: Endpoint) = ()
    def logSuccess(pt: Endpoint)(data: pt.Data) = ()
    def logFailure(pt: Endpoint)(error: Throwable) = ()
  }

  /** An endpoint logger that uses println */
  def println(success: Boolean = true, failure: Boolean = true) = new EndpointLogger {
    def logStart(pt: Endpoint) = Predef.println(s"--> Fetching $pt")
    def logSuccess(pt: Endpoint)(data: pt.Data) = if (success) {
      Predef.println(s"--+ Fetched $pt, got $data")
    }
    def logFailure(pt: Endpoint)(error: Throwable) = if (failure) {
      Predef.println(s"--x Failed to fetch $pt")
      error.printStackTrace()
    }
  }
}

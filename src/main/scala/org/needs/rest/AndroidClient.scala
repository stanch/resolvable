package org.needs.rest

import play.api.libs.json._
import scala.concurrent.{ExecutionContext, Promise}
import scala.util.{Try, Failure}
import com.loopj.android.http.{AsyncHttpResponseHandler, AsyncHttpClient}

case class JsonHandler(promise: Promise[JsValue]) extends AsyncHttpResponseHandler {
  override def onSuccess(response: String) {
    promise.complete(Try(Json.parse(response)))
  }
  override def onFailure(t: Throwable, response: String) {
    promise.complete(Failure(t))
  }
}

trait AndroidClient { self: RestEndpoint ⇒
  val asyncHttpClient: AsyncHttpClient
  def client(url: String)(implicit ec: ExecutionContext) = {
    val promise = Promise[JsValue]()
    asyncHttpClient.get(url, JsonHandler(promise))
    promise.future
  }
}

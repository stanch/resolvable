package org.needs.http

import com.loopj.android.http.{FileAsyncHttpResponseHandler, AsyncHttpClient, AsyncHttpResponseHandler}
import play.api.libs.json.{Json, JsValue}
import scala.concurrent.{ExecutionContext, Promise}
import scala.util.{Success, Failure, Try}
import org.needs.json.JsonEndpoint
import org.needs.file.FileEndpoint
import java.io.File

sealed trait AndroidClient {
  val asyncHttpClient: AsyncHttpClient
}

trait AndroidJsonClient extends AndroidClient { self: HttpEndpoint with JsonEndpoint ⇒
  def client(url: String)(implicit ec: ExecutionContext) = {
    val promise = Promise[JsValue]()
    asyncHttpClient.get(url, JsonHandler(promise))
    promise.future
  }
}

case class JsonHandler(promise: Promise[JsValue]) extends AsyncHttpResponseHandler {
  override def onSuccess(response: String) {
    promise.complete(Try(Json.parse(response)))
  }
  override def onFailure(t: Throwable, response: String) {
    promise.complete(Failure(t))
  }
}

trait AndroidFileClient extends AndroidClient { self: HttpEndpoint with FileEndpoint ⇒
  def client(url: String)(implicit ec: ExecutionContext) = {
    val promise = Promise[File]()
    asyncHttpClient.get(url, FileHandler(create, promise))
    promise.future
  }
}

case class FileHandler(file: File, promise: Promise[File]) extends FileAsyncHttpResponseHandler(file) {
  override def onSuccess(response: File) {
    promise.complete(Success(response))
  }
  override def onFailure(t: Throwable, response: String) {
    promise.complete(Failure(t))
  }
}
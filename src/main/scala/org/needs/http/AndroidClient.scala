package org.needs.http

import com.loopj.android.http.{RequestParams, FileAsyncHttpResponseHandler, AsyncHttpClient, AsyncHttpResponseHandler}
import play.api.libs.json.{Json, JsValue}
import scala.concurrent.{Future, ExecutionContext, Promise}
import scala.util.{Success, Failure, Try}
import org.needs.json.JsonEndpoint
import org.needs.file.FileEndpoint
import java.io.File
import scala.collection.JavaConverters._

case class AndroidClient(client: AsyncHttpClient) extends HttpClient {
  def get(url: String, query: Map[String, String] = Map.empty)(implicit ec: ExecutionContext) = {
    val promise = Promise[String]()
    client.get(url, new RequestParams(query.asJava), StringHandler(promise))
    promise.future
  }

  def getFile(saveTo: File, url: String, query: Map[String, String])(implicit ec: ExecutionContext) = {
    val promise = Promise[File]()
    client.get(url, new RequestParams(query.asJava), FileHandler(saveTo, promise))
    promise.future
  }
}

case class StringHandler(promise: Promise[String]) extends AsyncHttpResponseHandler {
  override def onSuccess(response: String) {
    promise.success(response)
  }
  override def onFailure(t: Throwable, response: String) {
    promise.failure(t)
  }
}

case class FileHandler(file: File, promise: Promise[File]) extends FileAsyncHttpResponseHandler(file) {
  override def onSuccess(response: File) {
    promise.success(response)
  }
  override def onFailure(t: Throwable, response: String) {
    promise.failure(t)
  }
}

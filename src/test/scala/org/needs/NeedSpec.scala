package org.needs

import scala.language.experimental.macros
import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.json._
import play.api.libs.functional.syntax._
import org.scalatest.FlatSpec
import org.needs.json.AsyncReads._

/* Data model */

case class Author(id: String, name: String)
object Author {
  implicit val reads = Json.reads[Author]
}

case class Story(id: String, name: String, author: Author)
object Story {
  def reads(points: List[Endpoint])(implicit ec: ExecutionContext) = (
    (__ \ '_id).read[String] and
    (__ \ 'meta \ 'title).read[String] and
    (__ \ 'authorId).read[String].map(NeedAuthor)
  ).tupled.fulfillAll(points).mapM(Story.apply _ tupled)
}

/* Endpoints */

abstract class DispatchSingleResource(val path: String) extends rest.SingleResourceEndpoint with rest.DispatchClient {
  implicit val ec = ExecutionContext.Implicits.global
}

trait SingleAuthorEndpoint extends json.JsonEndpoint with rest.HasId
case class LocalAuthor(id: String) extends SingleAuthorEndpoint {
  lazy val data = Future.failed[JsValue](new Exception)
}
case class RemoteAuthor(id: String) extends DispatchSingleResource("http://routestory.herokuapp.com/api/authors") with SingleAuthorEndpoint

trait SingleStoryEndpoint extends json.JsonEndpoint with rest.HasId
case class LocalStory(id: String) extends SingleStoryEndpoint {
  lazy val data = Future.failed[JsValue](new Exception)
}
case class RemoteStory(id: String) extends DispatchSingleResource("http://routestory.herokuapp.com/api/stories") with SingleStoryEndpoint

/* Needs */

case class NeedAuthor(id: String) extends Need[Author] {
  val default = LocalAuthor(id) orElse RemoteAuthor(id)
  def probe(points: List[Endpoint])(implicit ec: ExecutionContext) = {
    case s: SingleAuthorEndpoint if s.id == id ⇒ s.as[Author]
  }
}

case class NeedStory(id: String) extends Need[Story] {
  val default = LocalStory(id) orElse RemoteStory(id)
  def probe(points: List[Endpoint])(implicit ec: ExecutionContext) = {
    case s: SingleStoryEndpoint if s.id == id ⇒ s.readAsync(Story.reads(points))
  }
}

class NeedsSpec extends FlatSpec {
  it should "do smth" in {
    import scala.concurrent.ExecutionContext.Implicits.global
    NeedStory("story-DLMwDHAyDknJxvidn4G6pA").fulfill onComplete { a ⇒
      println(a)
    }
  }
}
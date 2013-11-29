package org.needs

import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.json._
import play.api.libs.functional.syntax._
import org.scalatest.FlatSpec
import org.needs.json.Async._

/* Data model */

case class Author(id: String, name: String)
object Author {
  implicit val reads = Json.reads[Author]
}

case class Story(id: String, name: String, author: Author)
object Story {
  implicit def reads(implicit ec: ExecutionContext): FulfillableReads[Story] = points ⇒ (
    (__ \ '_id).read[String] and
    (__ \ 'meta \ 'title).read[String] and
    (__ \ 'authorId).read[String].map(NeedAuthor)
  ).tupled.fulfillAll(points).mapAsync(Story.apply _ tupled)
}

/* Endpoints */

abstract class DispatchSingleResource(val path: String) extends rest.SingleResourceEndpoint with rest.DispatchClient

trait SingleAuthorEndpoint extends json.JsonEndpoint with rest.HasId
case class LocalAuthor(id: String) extends SingleAuthorEndpoint {
  def fetch(implicit ec: ExecutionContext) = Future.failed[JsValue](new Exception)
}
case class RemoteAuthor(id: String) extends DispatchSingleResource("http://routestory.herokuapp.com/api/authors") with SingleAuthorEndpoint

trait SingleStoryEndpoint extends json.JsonEndpoint with rest.HasId
case class LocalStory(id: String) extends SingleStoryEndpoint {
  def fetch(implicit ec: ExecutionContext) = Future.failed[JsValue](new Exception)
}
case class RemoteStory(id: String) extends DispatchSingleResource("http://routestory.herokuapp.com/api/stories") with SingleStoryEndpoint

/* Needs */

case class NeedAuthor(id: String) extends Need[Author] with rest.RestNeed[Author] {
  val default = LocalAuthor(id) orElse RemoteAuthor(id)
  def probe(points: List[Endpoint])(implicit ec: ExecutionContext) =
    probeRest[RemoteAuthor]
}

case class NeedStory(id: String) extends Need[Story] with rest.RestNeed[Story] {
  val default = LocalStory(id) orElse RemoteStory(id)
  def probe(points: List[Endpoint])(implicit ec: ExecutionContext) =
    probeRestAsync[RemoteStory](points)
}

class NeedsSpec extends FlatSpec {
  it should "do smth" in {
    import scala.concurrent.ExecutionContext.Implicits.global
    NeedStory("story-DLMwDHAyDknJxvidn4G6pA").fulfill onComplete { a ⇒
      println(a)
    }
  }
}
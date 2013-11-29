package org.needs

import scala.language.postfixOps
import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.json._
import play.api.libs.functional.syntax._
import org.scalatest.FlatSpec
import org.needs.json._

/* Data model */

case class Author(id: String, name: String)
object Author {
  implicit val reads = Json.reads[Author]
}

case class Story(id: String, name: String, author: Author)
object Story {
  implicit val reads = (
    (__ \ '_id).read[String] and
    (__ \ 'meta \ 'title).read[String] and
    (__ \ 'authorId).read[String].map(NeedAuthor)
  ).tupled.liftAll[Fulfillable].fmap(Story.apply _ tupled)
}

/* Endpoints */

abstract class DispatchSingleResource(val path: String)
  extends rest.SingleResourceEndpoint
  with rest.DispatchClient

abstract class LocalSingleResource
  extends json.JsonEndpoint
  with rest.HasId {

  def fetch(implicit ec: ExecutionContext) = Future.failed[JsValue](new Exception)
}

case class LocalAuthor(id: String) extends LocalSingleResource
case class RemoteAuthor(id: String) extends DispatchSingleResource("http://routestory.herokuapp.com/api/authors")

case class LocalStory(id: String) extends LocalSingleResource
case class RemoteStory(id: String) extends DispatchSingleResource("http://routestory.herokuapp.com/api/stories")

/* Needs */

case class NeedAuthor(id: String) extends Need[Author] with rest.RestNeed[Author] {
  val default = LocalAuthor(id) orElse RemoteAuthor(id)

  def probe(implicit endpoints: List[Endpoint], ec: ExecutionContext) = {
    case e @ RemoteAuthor(i) if i == id ⇒ e.as[Author]
  }
}

case class NeedStory(id: String) extends Need[Story] with rest.RestNeed[Story] {
  val default = LocalStory(id) orElse RemoteStory(id)

  def probe(implicit endpoints: List[Endpoint], ec: ExecutionContext) = {
    case e @ RemoteStory(i) if i == id ⇒ e.as[Story]
  }
}

class NeedsSpec extends FlatSpec {
  it should "do smth" in {
    import scala.concurrent.ExecutionContext.Implicits.global
    NeedStory("story-DLMwDHAyDknJxvidn4G6pA").go onComplete println
  }
}
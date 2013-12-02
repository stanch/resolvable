package org.needs

import scala.language.postfixOps
import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.libs.json.applicative._
import org.scalatest.FlatSpec
import org.needs.rest.JsonEndpoint

/* Data model */

object Optimizer {
  def testMulti: Endpoint ⇒ Boolean = {
    case RemoteAuthors(_) ⇒ true
    case _ ⇒ false
  }
  val o = { endpoints: EndpointPool ⇒
    val add = endpoints.endpoints.foldLeft(List.empty[String]) {
      case (ids, RemoteAuthor(id)) ⇒ id :: ids
      case (ids, RemoteAuthors(i)) ⇒ i.toList ::: ids
      case (ids, _) ⇒ ids
    }
    val res = add match {
      case x :: _ ⇒ endpoints + RemoteAuthors(add.toSet)
      case _ ⇒ endpoints
    }
    res
  }
}

case class Author(id: String, name: String) extends rest.HasId
object Author {
  implicit val reads = (
    (__ \ '_id).read[String] and
    (__ \ 'name).read[String]
  ).tupled.liftAll[Fulfillable].fmap(Author.apply _ tupled)
}

case class Story(id: String, name: String, author: Author) extends rest.HasId
object Story {
  implicit val reads = (
    (__ \ '_id).read[String] and
    (__ \ 'meta \ 'title).read[String] and
    (__ \ 'authorId).read[String].map(NeedAuthor)
  ).tupled.liftAll[Fulfillable].fmap(Story.apply _ tupled)
}

case class StoryPreview(id: String, name: String, author: Author)
object StoryPreview {
  implicit val reads = (
    (__ \ 'id).read[String] and
    (__ \ 'value \ 'title).read[String] and
    (__ \ 'value \ 'authorId).read[String].map(NeedAuthor)
  ).tupled.liftAll[Fulfillable].fmap(StoryPreview.apply _ tupled)
}

case class Latest(totalRows: Int, stories: List[StoryPreview])
object Latest {
  implicit val reads = (
    (__ \ 'total_rows).read[Int] and
    (__ \ 'rows).read[List[Fulfillable[StoryPreview]]].map(Fulfillable.sequence(Some(Optimizer.o)))
  ).tupled.liftAll[Fulfillable].fmap(Latest.apply _ tupled)
}

/* Endpoints */

abstract class DispatchSingleResource(val path: String)
  extends rest.SingleResourceEndpoint
  with rest.DispatchClient

abstract class DispatchMultipleResource(val path: String)
  extends rest.MultipleResourceEndpoint
  with rest.DispatchClient

abstract class LocalSingleResource
  extends JsonEndpoint
  with rest.HasId {

  def fetch(implicit ec: ExecutionContext) = Future.failed[JsValue](new Exception)
}

trait Local { self: Endpoint ⇒ override val priority = Seq(1) }

case class LocalAuthor(id: String) extends LocalSingleResource with Local
case class RemoteAuthor(id: String) extends DispatchSingleResource("http://routestory.herokuapp.com/api/authors")
case class RemoteAuthors(ids: Set[String]) extends DispatchMultipleResource("http://routestory.herokuapp.com/api/authors") {
  override val priority = Seq(0, ids.size)
}

case class LocalStory(id: String) extends LocalSingleResource with Local
case class RemoteStory(id: String) extends DispatchSingleResource("http://routestory.herokuapp.com/api/stories")

/* Needs */

case class NeedAuthor(id: String) extends Need[Author] with rest.RestNeed[Author] {
  use { LocalAuthor(id) }
  use { RemoteAuthor(id) }
  from {
    singleResource[RemoteAuthor]
  }
  from {
    multipleResources[RemoteAuthors]
  }
}

case class NeedStory(id: String) extends Need[Story] with rest.RestNeed[Story] {
  use { LocalStory(id) }
  use { RemoteStory(id) }
  from {
    singleResource[RemoteStory]
  }
}

case object NeedLatest extends Need[Latest] with rest.RestEndpoint with rest.DispatchClient { Self ⇒
  def fetch(implicit ec: ExecutionContext) =
    client("http://routestory.herokuapp.com/api/stories/latest")

  use { Self }
  from {
    case Self ⇒ asFulfillable[Latest]
  }
}

class NeedsSpec extends FlatSpec {
  it should "do smth" in {
    import scala.concurrent.ExecutionContext.Implicits.global
    //NeedStory("story-DLMwDHAyDknJxvidn4G6pA").go onComplete println
    NeedLatest.go onComplete println
  }
}
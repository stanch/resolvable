package org.needs

import scala.language.postfixOps
import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.json._
import play.api.libs.functional.syntax._
import org.scalatest.FlatSpec
import org.needs.json._
import scala.collection.immutable.TreeSet

/* Data model */

//object Optimizer {
//  implicit val o = { endpoints: TreeSet[Endpoint] ⇒
//    val (add, p) = endpoints.foldLeft((List.empty[String], List.empty[Endpoint])) {
//      case ((ids, pts), RemoteAuthor(id)) ⇒ (id :: ids, pts)
//      case ((ids, pts), RemoteAuthors(i)) ⇒ (i.toList ::: ids, pts)
//      case ((ids, pts), pt) ⇒ (ids, pts ::: pt :: Nil)
//    }
//    Future.successful(add match {
//      case x :: Nil ⇒ RemoteAuthor(x) :: p
//      case x :: _ ⇒ RemoteAuthors(add.toSet) :: p
//      case _ ⇒ p
//    })
//  }
//}

import org.needs.Optimizers.Implicits.blank

case class Author(id: String, name: String)
object Author {
  implicit val reads = (
    (__ \ '_id).read[String] and
    (__ \ 'name).read[String]
  )(Author.apply _)
}

case class Story(id: String, name: String, author: Author)
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
    (__ \ 'rows).read[List[Fulfillable[StoryPreview]]].map(Fulfillable.sequence)
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
  extends json.JsonEndpoint
  with rest.HasId {

  def fetch(implicit ec: ExecutionContext) = Future.failed[JsValue](new Exception)
}

trait Local { self: Endpoint ⇒ override val priority = 1 }

case class LocalAuthor(id: String) extends LocalSingleResource with Local
case class RemoteAuthor(id: String) extends DispatchSingleResource("http://routestory.herokuapp.com/api/authors")
case class RemoteAuthors(ids: Set[String]) extends DispatchMultipleResource("http://routestory.herokuapp.com/api/authors")

case class LocalStory(id: String) extends LocalSingleResource with Local
case class RemoteStory(id: String) extends DispatchSingleResource("http://routestory.herokuapp.com/api/stories")

/* Needs */

case class NeedAuthor(id: String) extends Need[Author] with rest.RestNeed[Author] {
  use { LocalAuthor(id) }
  use { RemoteAuthor(id) }
  from {
    singleResource[RemoteAuthor]
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
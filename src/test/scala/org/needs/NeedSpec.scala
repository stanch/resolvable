package org.needs

import scala.language.postfixOps
import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.json._
import play.api.libs.functional.syntax._
import org.scalatest.FlatSpec
import org.needs.json._

/* Data model */

object Optimizer {
  import ExecutionContext.Implicits.global
  implicit val o = /*Optimizers.basic +*/ { pts: List[Endpoint] ⇒
    println(s"Trying to optimize $pts...")
    val add = RemoteAuthors(pts.foldLeft(List.empty[String]) {
      case (ids, RemoteAuthor(id)) ⇒ id :: ids
      case (ids, _) ⇒ ids
    }.toSet)
    Future.successful(add :: pts)
  }
}

import Optimizer._

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

case class LocalAuthor(id: String) extends LocalSingleResource
case class RemoteAuthor(id: String) extends DispatchSingleResource("http://routestory.herokuapp.com/api/authors")
case class RemoteAuthors(ids: Set[String]) extends DispatchMultipleResource("http://routestory.herokuapp.com/api/authors")

case class LocalStory(id: String) extends LocalSingleResource
case class RemoteStory(id: String) extends DispatchSingleResource("http://routestory.herokuapp.com/api/stories")

/* Needs */

case class NeedAuthor(id: String) extends Need[Author] {
  val default = LocalAuthor(id) orElse RemoteAuthor(id)

  def probe(implicit endpoints: List[Endpoint], ec: ExecutionContext) = {
    case e @ RemoteAuthor(i) if i == id ⇒ e.as[Author]
    case e @ RemoteAuthors(ids) if ids contains id ⇒
      val f = e.as[List[Author]].map(_.find(_.id == id).get)
      f onFailure { case t ⇒ t.printStackTrace() }
      f
  }
}

case class NeedStory(id: String) extends Need[Story] {
  val default = LocalStory(id) orElse RemoteStory(id)

  def probe(implicit endpoints: List[Endpoint], ec: ExecutionContext) = {
    case e @ RemoteStory(i) if i == id ⇒ e.as[Story]
  }
}

case object NeedLatest extends Need[Latest] with rest.RestEndpoint with rest.DispatchClient {
  protected def fetch(implicit ec: ExecutionContext) =
    client("http://routestory.herokuapp.com/api/stories/latest")

  val default = this :: Nil

  def probe(implicit endpoints: List[Endpoint], ec: ExecutionContext) = {
    case x if x == this ⇒ as[Latest]
  }
}

class NeedsSpec extends FlatSpec {
  it should "do smth" in {
    import scala.concurrent.ExecutionContext.Implicits.global
    //NeedStory("story-DLMwDHAyDknJxvidn4G6pA").go onComplete println
    NeedLatest.go onComplete println
  }
}
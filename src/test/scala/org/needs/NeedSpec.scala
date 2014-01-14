package org.needs

import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.json._
import play.api.libs.functional.syntax._
import org.scalatest.FlatSpec
import org.needs.json.{HttpJsonEndpoint, JsonEndpoint}
import org.needs.http.{HttpClient, HttpEndpoint, DispatchClient}
import java.io.File
import org.needs.file.HttpFileEndpoint

/* Data model */

object Optimizer {
  val o = { endpoints: EndpointPool ⇒
    val add = endpoints.fold(Set.empty[String]) {
      case (ids, RemoteAuthor(id)) ⇒ ids + id
      case (ids, RemoteAuthors(i)) ⇒ ids ++ i
      case (ids, _) ⇒ ids
    }
    if (add.size > 1) endpoints + RemoteAuthors(add) else endpoints
  }
}

case class Author(id: String, name: String)
object Author {
  implicit object hasId extends rest.HasId[Author] { def id(entity: Author) = entity.id }
  implicit val reads = Fulfillable.reads[Author] {
    (__ \ '_id).read[String] and
    (__ \ 'name).read[String]
  }
}

case class Story(id: String, name: String, author: Author)
object Story {
  implicit object hasId extends rest.HasId[Story] { def id(entity: Story) = entity.id }
  implicit val reads = Fulfillable.reads[Story] {
    (__ \ '_id).read[String] and
    (__ \ 'meta \ 'title).read[String] and
    (__ \ 'authorId).read[String].map(NeedAuthor)
  }
}

case class StoryPreview(id: String, name: String, author: Author)
object StoryPreview {
  implicit val reads = Fulfillable.reads[StoryPreview] {
    (__ \ 'id).read[String] and
    (__ \ 'value \ 'title).read[String] and
    (__ \ 'value \ 'authorId).read[String].map(NeedAuthor)
  }
}

case class Latest(totalRows: Int, stories: List[StoryPreview])
object Latest {
  implicit val reads = Fulfillable.reads[Latest] {
    (__ \ 'total_rows).read[Int] and
    (__ \ 'rows).read[List[Fulfillable[StoryPreview]]].map(Fulfillable.jumpList)
  }
}

/* Endpoints */

trait Logging { self: Endpoint ⇒
  override protected def logFetching() {
    println(s"--> Downloading $this")
  }
}

trait HttpBase extends Logging { self: HttpEndpoint ⇒
  def client: HttpClient = new DispatchClient
}

abstract class DispatchSingleResource(val baseUrl: String)
  extends rest.SingleResourceEndpoint with HttpBase

abstract class DispatchMultipleResource(val baseUrl: String)
  extends rest.MultipleResourceEndpoint with HttpBase

abstract class LocalSingleResource extends JsonEndpoint {
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

case class RemoteMedia(url: String)
  extends HttpFileEndpoint with HttpBase {
  def create = new File(s"${url.replace("/", "-")}")
  val baseUrl = "http://routestory.herokuapp.com/api/stories"
}

case class LocalMedia(url: String)
  extends file.FileEndpoint with Local with Logging {
  case object CacheMiss extends Exception
  def create = new File(s"${url.replace("/", "-")}")
  protected def fetch(implicit ec: ExecutionContext) =
    Option(create).filter(_.exists()).map(Future.successful).getOrElse(Future.failed(CacheMiss))
}

/* Needs */

case class NeedAuthor(id: String) extends Need[Author] with rest.Probing[Author] {
  use { LocalAuthor(id) }
  use { RemoteAuthor(id) }

  from {
    singleResource[RemoteAuthor]
  }
  from {
    multipleResources[RemoteAuthors]
  }

  optimize {
    Optimizer.o
  }
}

case class NeedStory(id: String) extends Need[Story] with rest.Probing[Story] {
  use { LocalStory(id) }
  use { RemoteStory(id) }
  from {
    singleResource[RemoteStory]
  }
}

case class NeedLatest(count: Int) extends json.SelfFulfillingNeed[Latest] with HttpJsonEndpoint with HttpBase {
  def fetch(implicit ec: ExecutionContext) =
    client.getJson("http://routestory.herokuapp.com/api/stories/latest", Map("limit" → count.toString))
}

case class NeedMedia(url: String) extends Need[File] {
  use { RemoteMedia(url) }
  use { LocalMedia(url) }
  from {
    case e @ LocalMedia(`url`) ⇒ e.probe
    case e @ RemoteMedia(`url`) ⇒ e.probe
  }
}

class NeedsSpec extends FlatSpec {
  it should "do smth" in {
    import scala.concurrent.ExecutionContext.Implicits.global
    //NeedMedia("story-zwAsEW54BBCt6kTCvmoaNA/audio/2.aac").go onComplete println
    //NeedStory("story-DLMwDHAyDknJxvidn4G6pA").go onComplete println
    NeedStory("story-DLMwDHAyDknJxvidn4G6pA").flatMap(_ ⇒ NeedLatest(5)).go onComplete println
    //NeedLatest(5).go onComplete println
    //(NeedAuthor("author-7sKtNqyebaTECWmr5LAJC") and NeedAuthor("author-QgMggevaDcYNRE8Aov3rY")).tupled.go onComplete println
  }
}
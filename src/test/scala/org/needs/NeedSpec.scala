package org.needs

import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.data.mapping.json.Rules._
import org.scalatest.FlatSpec
import org.needs.json.HttpJsonEndpoint
import org.needs.http.{HttpClient, HttpEndpoint, DispatchClient}
import java.io.File
import org.needs.file.HttpFileEndpoint

/* Data model */

//object Optimizer {
//  val o = { endpoints: EndpointPool ⇒
//    val add = endpoints.fold(Set.empty[String]) {
//      case (ids, RemoteAuthor(id)) ⇒ ids + id
//      case (ids, RemoteAuthors(i)) ⇒ ids ++ i
//      case (ids, _) ⇒ ids
//    }
//    if (add.size > 1) endpoints + RemoteAuthors(add) else endpoints
//  }
//}

case class Author(id: String, name: String, picture: File)
object Author {
  implicit val rule = Resolvable.rule[JsValue, Author] { __ ⇒
    (__ \ "id").read[String] and
    (__ \ "name").read[String] and
    (__ \ "picture").read[String].fmap(Needs.picture)
  }
}

case class Story(id: String, name: String, author: Author)
object Story {
  implicit val rule = Resolvable.rule[JsValue, Story] { __ ⇒
    (__ \ "_id").read[String] and
    (__ \ "meta" \ "title").read[String] and
    (__ \ "authorId").read[String].fmap(Needs.author)
  }
}

case class StoryPreview(id: String, name: String, author: Author)
object StoryPreview {
  implicit val rule = Resolvable.rule[JsValue, StoryPreview] { __ ⇒
    (__ \ "id").read[String] and
    (__ \ "value" \ "title").read[String] and
    (__ \ "value" \ "authorId").read[String].fmap(Needs.author)
  }
}

case class Latest(totalRows: Int, stories: List[StoryPreview])
object Latest {
  implicit val rule = Resolvable.rule[JsValue, Latest] { __ ⇒
    (__ \ "total_rows").read[Int] and
    (__ \ "rows").read[List[Resolvable[StoryPreview]]].fmap(Resolvable.jumpList)
  }
}

/* Endpoints */

trait Base extends HttpEndpoint {
  val logger = EndpointLogger.println(success = true, failure = false)
  val client = new DispatchClient
}

case class RemoteFile(url: String) extends Base with HttpFileEndpoint {
  def create = File.createTempFile("fetched", ".bin")
  protected def fetch(implicit ec: ExecutionContext) = client.getFile(url)
}

abstract class SingleResource(val baseUrl: String) extends Base with HttpJsonEndpoint {
  val id: String
  protected def fetch(implicit ec: ExecutionContext) = client.getJson(s"$baseUrl/$id")
}

case class RemoteAuthor(id: String)
  extends SingleResource("http://routestory.herokuapp.com/api/authors")

case class RemoteStory(id: String)
  extends SingleResource("http://routestory.herokuapp.com/api/stories")

case class RemoteLatest(count: Int) extends Base with HttpJsonEndpoint {
  def fetch(implicit ec: ExecutionContext) =
    client.getJson("http://routestory.herokuapp.com/api/stories/latest", Map("limit" → count.toString))
}

/* Needs */

object Needs {
  def author(id: String) = Source[Author].from(RemoteAuthor(id))
  def picture(url: String) = Source[File].from(RemoteFile(url))
  def story(id: String) = Source[Story].from(RemoteStory(id))
  def latest(count: Int) = Source[Latest].from(RemoteLatest(count))
}

class NeedsSpec extends FlatSpec {
  it should "do smth" in {
    import scala.concurrent.ExecutionContext.Implicits.global
    //Needs.story("story-DLMwDHAyDknJxvidn4G6pA").go onComplete println
    //NeedMedia("story-zwAsEW54BBCt6kTCvmoaNA/audio/2.aac").go onComplete println
    //NeedStory("story-DLMwDHAyDknJxvidn4G6pA").go onComplete println
    //NeedStory("story-DLMwDHAyDknJxvidn4G6pA").flatMap(_ ⇒ NeedLatest(5)).go onComplete println
    Needs.latest(10).go onComplete println
    //(Needs.author("author-bWTPRa8rCLgVAVSMNsb7QV") and Needs.author("author-bWTPRa8rCLgVAVSMNsb7QV")).tupled.go onComplete println
//    val rule = implicitly[Rule[JsValue, Resolvable[Author]]]
//    val x = RemoteAuthor("author-bWTPRa8rCLgVAVSMNsb7QV").data.map(rule.validate).map(_.get)
//    x foreach println
    //Needs.story("story-DLMwDHAyDknJxvidn4G6pA").go onComplete println
  }
}

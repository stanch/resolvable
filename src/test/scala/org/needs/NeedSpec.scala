package org.needs

import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.data.mapping.From
import play.api.data.mapping.json.Rules._
import org.scalatest.FlatSpec
import org.needs.json.HttpJsonEndpoint
import org.needs.http.{HttpClient, HttpEndpoint, DispatchClient}

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

case class Author(id: String, name: String)
object Author {
  implicit val rule = From[JsValue] { __ ⇒
    Resolvable.rule[JsValue, Author] {
      (__ \ "id").read[String] and
      (__ \ "name").read[String]
    }
  }
}

case class Story(id: String, name: String, author: Author)
object Story {
  implicit val rule = From[JsValue] { __ ⇒
    Resolvable.rule[JsValue, Story] {
      (__ \ "_id").read[String] and
      (__ \ "meta" \ "title").read[String] and
      (__ \ "authorId").read[String].fmap(Needs.author)
    }
  }
}

case class StoryPreview(id: String, name: String, author: Author)
object StoryPreview {
  implicit val rule = From[JsValue] { __ ⇒
    Resolvable.rule[JsValue, StoryPreview] {
      (__ \ "id").read[String] and
      (__ \ "value" \ "title").read[String] and
      (__ \ "value" \ "authorId").read[String].fmap(Needs.author)
    }
  }
}

case class Latest(totalRows: Int, stories: List[StoryPreview])
object Latest {
  implicit val rule = From[JsValue] { __ ⇒
    Resolvable.rule[JsValue, Latest] {
      (__ \ "total_rows").read[Int] and
      (__ \ "rows").read[List[Resolvable[StoryPreview]]].fmap(Resolvable.jumpList)
    }
  }
}

/* Endpoints */

trait Base extends HttpJsonEndpoint {
  val logger = EndpointLogger.println()
  val client = new DispatchClient
}

abstract class SingleResource(val baseUrl: String) extends Base {
  val id: String
  protected def fetch(implicit ec: ExecutionContext) = client.getJson(s"$baseUrl/$id")
}

case class RemoteAuthor(id: String)
  extends SingleResource("http://routestory.herokuapp.com/api/authors")

case class RemoteStory(id: String)
  extends SingleResource("http://routestory.herokuapp.com/api/stories")

case class RemoteLatest(count: Int) extends Base {
  def fetch(implicit ec: ExecutionContext) =
    client.getJson("http://routestory.herokuapp.com/api/stories/latest", Map("limit" → count.toString))
}

/* Needs */

object Needs {
  def author(id: String) = Source[Author].from(RemoteAuthor(id))
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
    //(Needs.author("author-7sKtNqyebaTECWmr5LAJC") and Needs.author("author-QgMggevaDcYNRE8Aov3rY")).tupled.go onComplete println
    //Needs.story("story-DLMwDHAyDknJxvidn4G6pA").go onComplete println
  }
}
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
import java.util.concurrent.Executors

/* Data model */

case class Author(id: String, name: String, picture: Future[File])
object Author {
  implicit val rule = Resolvable.rule[JsValue, Author] { __ ⇒
    (__ \ "id").read[String] and
    (__ \ "name").read[String] and
    (__ \ "picture").read[String].fmap(Needs.picture).fmap(Resolvable.defer)
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
    (__ \ "rows").read[List[Resolvable[StoryPreview]]].fmap(Resolvable.fromList)
  }
}

/* Endpoints */

trait Base extends HttpEndpoint {
  val logger = EndpointLogger.println(success = true, failure = false)
  val client = new DispatchClient
}

object FileDownloadContext {
  lazy val ec = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(2))
}

case class RemoteFile(url: String) extends Base with HttpFileEndpoint {
  def create = File.createTempFile("fetched", ".bin")
  protected def fetch(implicit ec: ExecutionContext) = {
    client.getFile(url)(FileDownloadContext.ec).map { x ⇒ Thread.sleep(3000); x }(FileDownloadContext.ec)
  }
}

abstract class SingleResource(val baseUrl: String) extends Base with HttpJsonEndpoint {
  val id: String
  protected def fetch(implicit ec: ExecutionContext) = client.getJson(s"$baseUrl/$id")
}

abstract class MultipleResources(val baseUrl: String) extends Base with HttpJsonEndpoint {
  val ids: Set[String]
  protected def fetch(implicit ec: ExecutionContext) = client.getJson(s"$baseUrl/${ids.mkString(",")}")
}

case class RemoteAuthor(id: String)
  extends SingleResource("http://routestory.herokuapp.com/api/authors")

case class RemoteAuthors(ids: Set[String])
  extends MultipleResources("http://routestory.herokuapp.com/api/authors")

case class RemoteStory(id: String)
  extends SingleResource("http://routestory.herokuapp.com/api/stories")

case class RemoteLatest(count: Int) extends Base with HttpJsonEndpoint {
  def fetch(implicit ec: ExecutionContext) =
    client.getJson("http://routestory.herokuapp.com/api/stories/latest", Map("limit" → count.toString))
}

/* Needs */

object Needs {
  def author(id: String) =
    Source[Author](RemoteAuthor(id))({
      case e @ RemoteAuthor(`id`) ⇒
        Resolvable[Author].fromEndpoint(e)
      case e @ RemoteAuthors(ids) if ids.contains(id) ⇒
        Resolvable[Author].fromEndpointPath(e)(_.as[List[JsValue]].find(_ \ "_id" == JsString(id)).get)
    }, {
      case RemoteAuthors(uuids) ⇒ Seq(1, uuids.size)
    }, { (ec, pool) ⇒
      Future.successful(pool.fold(Set.empty[String]) {
        case (ids, RemoteAuthor(i)) ⇒ ids + i
        case (ids, _) ⇒ ids
      } match {
        case x if x.size > 1 ⇒ EndpointPool(RemoteAuthors(x))
        case _ ⇒ EndpointPool.empty
      })
    })

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
    Needs.latest(20).go onComplete println
    //(Needs.author("abc") orElse Needs.author("author-bWTPRa8rCLgVAVSMNsb7QV")).go onComplete println
    //(Needs.author("author-bWTPRa8rCLgVAVSMNsb7QV") and Needs.author("author-bWTPRa8rCLgVAVSMNsb7QV")).tupled.go onComplete println
//    val rule = implicitly[Rule[JsValue, Resolvable[Author]]]
//    val x = RemoteAuthor("author-bWTPRa8rCLgVAVSMNsb7QV").data.map(rule.validate).map(_.get)
//    x foreach println
    //Needs.story("story-DLMwDHAyDknJxvidn4G6pA").go onComplete println
  }
}

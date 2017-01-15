package resolvable

import scala.concurrent.{ExecutionContext, Future}
import java.io.File
import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.data.mapping.json.Rules._
import org.scalatest.{Matchers, FlatSpec}
import scala.util.Success

class RuleSpec extends FlatSpec with Matchers {

  case class Author(id: String, name: String)
  object Author {
    implicit val rule = Resolvable.rule[JsValue, Author] { __ ⇒
      (__ \ "id").read[String] and
      (__ \ "name").read[String]
    }
  }

  case class Book(id: String, title: String, author: Author)
  object Book {
    implicit val rule = Resolvable.rule[JsValue, Book] { __ ⇒
      (__ \ "id").read[String] and
      (__ \ "title").read[String] and
      (__ \ "authorId").read[String].fmap(needAuthor)
    }
  }

  case object AuthorEndpoint extends json.JsonEndpoint {
    protected val logger = EndpointLogger.none
    protected def fetch(implicit ec: ExecutionContext) = Future.successful {
      Json.obj(
        "id" → "1",
        "name" → "Bill Bruford"
      )
    }
  }

  case object BookEndpoint extends json.JsonEndpoint {
    protected val logger = EndpointLogger.none
    protected def fetch(implicit ec: ExecutionContext) = Future.successful {
      Json.obj(
        "id" → "1",
        "title" → "When In Doubt, Roll",
        "authorId" → "1"
      )
    }
  }

  def needAuthor(id: String) =
    Source[Author].from(AuthorEndpoint)

  def needBook(id: String) =
    Source[Book].from(BookEndpoint)

  "Rules" should "be generated correctly" in {
    import scala.concurrent.ExecutionContext.Implicits.global
    needBook("1").go onComplete {
      case x ⇒ assert(x == Success(Book("1", "When In Doubt, Roll", Author("1", "Bill Bruford"))))
    }
  }

}

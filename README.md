### Motivating example

Suppose we have this data model:

```scala
case class Book(id: String, title: String, author: Author)
case class Author(id: String, name: String, avatar: File)
```

Now we want to fetch a book from a RESTful endpoint, but alas! the API does not quite match our model:

`/webservice/api/books/12`:
```javascript
{
  "id": "12",
  "title": "Hamlet",
  "authorId": "34"
}
```
`/webservice/api/authors/34`:
```javascript
{
  "id": "34",
  "name": "William Sheakspeare",
  "avatar": "http://upload.wikimedia.org/wikipedia/commons/thumb/a/a2/Shakespeare.jpg/250px-Shakespeare.jpg"
}
```

Here’s a “simple” way to do it:

```scala
import play.api.libs.json._
import scala.async.Async._
def getBook(id: String): Future[Book] = async {
  val book = await(httpClient.get(s"/webservice/api/books/$id"))
  val author = await(getAuthor((book \ "authorId").as[String]))
  Book((book \ "id").as[String], (book \ "title").as[String], author)
}
def getAuthor(id: String): Future[Author] = async {
  ...
}
```

Why is this not a good idea? It mixes 3 things into one:
* *Where to get stuff*. In a real app, each object may be stored in several places, e.g. local file cache, local partial database replica, remote RESTful API, remote RDF API. If the `/webservice/api/books/:id` API may or may not already include the author (see “Compound documents” section at http://jsonapi.org/format/), we have to jump through the hoops to optimize for that.
* *How to get stuff from an endpoint*. Different endpoints clearly require different handling: one might return a `File`, another one — a `JsValue`. There are even some that return `xml.Node`, duh.
* *How to read stuff from JSON, XML, ...*. The fact that a book needs an author forces us to insert fetching code directly inside JSON deserialization, which is by no means flexible.

### Needs

*Needs* separates the concepts of `Endpoints`, dependencies (“`Need`s”) and serialization. Let’s have another take on our example:

```scala
import play.api.libs.json._
import org.needs._

/* Deserialization (see http://www.playframework.com/documentation/2.2.1/ScalaJsonCombinators) */

object Author {
  // `points` will be a current set of utilized endpoints
  def reads(points: List[Endpoint])(implicit ec: ExecutionContext) = (
    (__ \ 'id).read[String] and
    (__ \ 'name).read[String] and
    (__ \ 'avatar).read[String].map(NeedAvatar)
  ).tupled.fulfillAll(points).mapM(Author.apply _ tupled)
  
  // the last line is a bit magical right now...
  // basically `fulfillAll` converts Reads[(String, String, Need[File])] to Reads[Future[(String, String, File)]]
  // and `mapM` converts Reads[Future[(String, String, File)]] to Reads[Future[Author]]
}

object Book {
  def reads(points: List[Endpoint])(implicit ec: ExecutionContext) = (
    (__ \ 'id).read[String] and
    (__ \ 'title).read[String] and
    (__ \ 'authorId).read[String].map(NeedAuthor)
  ).tupled.fulfillAll(points).mapM(Book.apply _ tupled)
}

/* Endpoints */

// baking in some conventions and an http client
abstract class SingleResource(val path: String)
  extends rest.SingleResourceEndpoint
  with rest.DispatchClient

case class BookEndpoint(id: String) extends SingleResource("/webservice/api/books")

case class AuthorEndpoint(id: String) extends SingleResource("/webservice/api/authors")

trait AvatarEndpoint {
  type Data = File
  val url: String
}

case class CachedAvatar(url: String) extends AvatarEndpoint {
  def fetch(implicit ec: ExecutionContext): Future[File] = ??? // read file from disk
}

case class RemoteAvatar(url: String) extends AvatarEndpoint {
  def fetch(implicit ec: ExecutionContext): Future[File] = ??? // download file from the net and cache it
}

/* Needs */

case class NeedBook(id: String) extends Need[Book] {
  // default endpoints
  val default = BookEndpoint(id)
  
  // try to load from a single endpoint
  // `points` represent already utilized endpoints
  def probe(points: List[Endpoint])(implicit ec: ExecutionContext): Future[Book] = {
    case e @ BookEndpoint(i) if i == id ⇒ e.readAsync(Book.reads(points))
    // put other endpoints here
  }
}

case class NeedAuthor(id: String) extends Need[Author] {
  val default = AuthorEndpoint(id)
  def probe(points: List[Endpoint])(implicit ec: ExecutionContext) = {
    case e @ AuthorEndpoint(i) if i == id ⇒ e.readAsync(Author.reads(points))
  }
}

case class NeedAvatar(url: String) extends Need[File] {
  val default = CachedAvatar(url) orElse RemoteAvatar(url)
  def probe(points: List[Endpoint])(implicit ec: ExecutionContext) = {
    case e: AvatarEndpoint if e.url == url ⇒ e.data
  }
}

import ExecutionContext.Implicits.global
val book: Future[Book] = NeedBook("12").fulfill
```

This looks like a lot of code, but now if you want to add another endpoint, you just need a couple of lines.

### Status

Experimental / not published. Things might change.

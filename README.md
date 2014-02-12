*Heads-up: the readme has been rewritten to describe the version 2.0. It’s not published yet.*

### The RESTful hell

So this happens. We have a book:

```scala
case class Book(id: String, title: String, author: Author)
```

A book has an author:

```scala
case class Author(id: String, name: String, avatar: File)
```

An author has an avatar, which we want as a `File`.

We ask a webservice for a book with `id` `"123"`, and it gives us this:

```javascript
{
  "id": "123",
  "title": "Hamlet",
  "authorId": "345"
}
```

**So we fetch this JSON, extract the author `id`, fetch the author JSON from another endpoint, get the avatar `url`, fetch the avatar from yet another endoint...** Sounds simple, right?

Food for thought:

* We happen to have a small local database with some of the books aready fetched. There is also a local avatar cache.
* If we fetch 10 books written by the same author, we expect the said author to be fetched only once.
* If we fetch 10 books written by different authors, we expect the authors to be fetched with an aggregated request.
* [json-api](http://jsonapi.org/) compound documents look nice!
* **We want to optimize for all the above**. Still simple?
* Don’t forget we are not going to modify the original data model in any way. Say no to annotations!

### Resolvable

That all really called for an abstraction in shiny armor. Well, here it is!
`Resolvable[A]` is something that can be resolved given a pool of endpoints:
```scala
def needBook(id: String): Resolvable[Book] = ???
val book: Future[Book] = needBook.go // `go` uses the default endpoints
```
When you combine `Resolvable`s using combinators, they form a dependency tree, which will fetch the endpoints
in the most optimal way, going layer by layer (i.e. breadth-first):
```scala
// if the books have the same author, it will be fetched only once
Resolvable.fromList(List(needBook("1"), needBook("2"))).go // Future[List[Book]]

import play.api.libs.functional.syntax._
// if, for example, the book’s author has id "1", it will be fetched only once
(needBook("1") and needAuthor("1")).tupled.go // Future[(Book, Author)]

// map, flatMap, orElse also work!
(needBook("1") orElse needBook("2")).go // Future[Book]
needBook("1").map(_.author.name).go // Future[String]
```

### Endpoints

To actually fetch some data, we will need to define our endpoints. That’s a piece of cake!
(quite literally, we’ll use the cake pattern for our convenience). An endpoint is basically
something with a `Data` type and `fetch` method, which returns `Future[Data]`. Endpoints
correspond directly to the external APIs. If there’s a REST API `/webservice/api/books/#id`,
which returns JSON, we’ll have an endpoint with `Data = JsValue` and `fetch` using an http
client to download the respective url.

```scala
import org.needs._
import org.needs.http._
import org.needs.json._
import org.needs.file._

trait Endpoints {

  // this logger just println-s the endpoints being fetched
  // great for debugging!
  val endpointLogger = EndpointLogger.println(success = true, failure = false)
  
  // Dispatch and Android clients are provided
  val httpClient = new DispatchClient

  // the base of our http endpoints
  trait RemoteBase extends HttpEndpoint {
    val logger = endpointLogger
    val client = httpClient
  }
  
  // reducing boilerplate for RESTful endpoints
  abstract class RemoteResource(val baseUrl: String) extends RemoteBase with HttpJsonEndpoint {
    def id: String
    protected def fetch(implicit ec: ExecutionContext) = client.getJson(s"$baseUrl/$id")
  }
  
  // an endpoint for our books
  case class RemoteBook(id: String) extends RemoteResource("/webservice/api/books")
  
  // an endpoint for our authors
  case class RemoteAuthor(id: String) extends RemoteResource("/webservice/api/authors")
  
  // the base for out file endpoints quick and dirty!
  abstract class FileBase(url: String) extends FileEndpoint {
    val logger = endpointLogger
    // where the file will be create upon download
    def create = new File(s"$cacheDir/${sanitize(url)}") // define cacheDir and sanitize somewhere
  }
  
  // this one just tries to load the file from disk where it was cached
  case class LocalCachedFile(url: String) extends FileBase(url) with LocalFileEndpoint
  
  // this one downloads the file to exactly where it’s supposed to be cached
  case class RemoteFile(url: String) extends FileBase(url) with HttpFileEndpoint {
    protected def fetch(implicit ec: ExecutionContext) = client.getFile(url)
  }
}
```

### Needs

Now let’s remember what we needed. A book, an author and an avatar. Here it goes:

```scala
import org.needs.Source
import play.api.data.mapping.json.Rules._

trait Needs { self: Endpoints with JsonFormats ⇒
  def book(id: String) = Source[Book].from(RemoteBook(id))
  def author(id: String) = Source[Author].from(RemoteAuthor(id))
  def avatar(url: String) = Source[File].from(LocalCachedfile(url)) orElse Source[File].from(RemoteFile(url))
}
```

That was easy... but how does it know how to load `Book`s from `RemoteBook`, which fetches `JsValue`? Good question! We need some deserialization logic. We’ll use the curring edge [*Play 2.3 API*](http://jto.github.io/articles/play_new_validation_api/)!

### Deserialization

```scala
import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.data.mapping.json.Rules._

// baking Needs in
trait JsonFormats { self: Needs ⇒

  // Rule[JsValue, Resolvable[Book]]
  implicit val bookRule = Resolvable.rule[JsValue, Book] { __ ⇒
    (__ \ "id").read[String] and
    (__ \ "title").read[String] and
    (__ \ "authorId").read[String].fmap(author) // here’s where the magic happens!
  }
  
  // Rule[JsValue, Resolvable[Author]]
  implicit val authorRule = Resolvable.rule[JsValue, Author] { __ ⇒
    (__ \ "id").read[String] and
    (__ \ "name").read[String] and
    (__ \ "avatar").read[String].fmap(avatar)
  }
}
```

### Baking the cake

```scala
object BookApi extends Endpoints with Needs with JsonFormats

import scala.concurrent.ExecutionContext.Implicits.global
val book = BookApi.book("1").go // hurray!
```

The example is not complete w.r.t. all the optimizations claimed above. But come on, it even deals with
a fictional web service. Let me know which one I should use (no API key is a must). Anyway, there is
another *working* example [in the tests](https://github.com/stanch/needs/blob/master/src/test/scala/org/needs/NeedSpec.scala).

### Other examples

Some Flickr API: [endpoints](https://github.com/scala-needs/needs-flickr/blob/master/src/main/scala/org/needs/flickr/Endpoints.scala), [needs](https://github.com/scala-needs/needs-flickr/blob/master/src/main/scala/org/needs/flickr/Needs.scala), [formats](https://github.com/scala-needs/needs-flickr/blob/master/src/main/scala/org/needs/flickr/JsonFormats.scala).

### Status

Version 1.0 was rewritten and abandoned at the RC5 stage. Version 2.0 is not published yet. Please stay tuned!

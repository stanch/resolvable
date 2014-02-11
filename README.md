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
* **We want to optimize for all the above**. Still simple? [Skip the rest and show me the code!](#the-code)

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
// returns Future[List[Book]]
// if the books have the same author, it will be fetched only once
Resolvable.jumpList(List(needBook("1"), needBook("2"))).go

import play.api.libs.functional.syntax._
// returns Future[(Book, Author)]
// if, for example, the book’s author has id "1", it will be fetched only once
(needBook("1") and needAuthor("1")).tupled.go

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
trait Endpoints {

  // this logger just println’s the endpoints being fetched
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



### Deserialization

The only bit left is deserialization. Let’s use the awesome
[play-json combinators](http://www.playframework.com/documentation/2.2.1/ScalaJsonCombinators)
and some help from `Fulfillable`.

```scala
import play.api.libs.json._
import play.api.libs.functional.syntax._

object Author {
  // this will have type Reads[Fulfillable[Author]]
  implicit val reads = Fulfillable.reads[Author] {
    (__ \ 'id).read[String] and
    (__ \ 'name).read[String] and
    (__ \ 'avatar).read[String].map(NeedAvatar)
  }
}
```

Once everything is set, we just need to call `NeedBook("123").go` to get a `Future[Book]`. Need several books?
`Fulfillable.jumpList(List(NeedBook("123"), NeedBook("234"))).go`. You can use the play functional syntax as well:
`(NeedBook("123") and NeedBook("234") and NeedAuthor("89")).tupled.go`.

### The code

This example is not complete w.r.t. all the optimizations claimed above. But come on, it even deals with
a fictional web service. Let me know which one I should use (no API key is a must). Anyway, there is
another *working* example [in the tests](https://github.com/stanch/needs/blob/master/src/test/scala/org/needs/NeedSpec.scala).

```scala
import play.api.libs.json._
import play.api.libs.functional.syntax._
import org.needs._

/* Deserialization */

object Author {
  implicit val reads = Fulfillable.reads[Author] {
    (__ \ 'id).read[String] and
    (__ \ 'name).read[String] and
    (__ \ 'avatar).read[String].map(NeedAvatar)
  }
}

object Book {
  implicit val reads = Fulfillable.reads[Book] {
    (__ \ 'id).read[String] and
    (__ \ 'title).read[String] and
    (__ \ 'authorId).read[String].map(NeedAuthor)
  }
}

/* Endpoints */

// baking in some conventions and an http client
abstract class SingleResource(val baseUrl: String)
  extends rest.SingleResourceEndpoint
  with http.DispatchJsonClient

case class RemoteBook(id: String) extends SingleResource("/webservice/api/books")

case class RemoteAuthor(id: String) extends SingleResource("/webservice/api/authors")

trait AvatarEndpoint extends file.FileEndpoint {
  def create = ??? // create a temp file to hold the avatar
  val url: String
}

case class CachedAvatar(url: String)
  extends AvatarEndpoint
  with file.LocalFileEndpoint {
  
  override val priority = Seq(1) // probe before RemoteAvatar
}

case class RemoteAvatar(url: String)
  extends AvatarEndpoint
  with file.HttpFileEndpoint
  with http.DispatchFileClient {
  
  val baseUrl = "/webservice/files" // file will be loaded from /sebservice/files/:url
}

/* Needs */

case class NeedBook(id: String) extends Need[Book] with rest.Probing[Book] {
  // list endpoints
  use {
    RemoteBook(id)
  }
  
  // describe how to load from them
  from {
    singleResource[RemoteBook]
  }
}

case class NeedAuthor(id: String) extends Need[Author] with rest.Probing[Author] {
  use {
    RemoteAuthor(id)
  }
  from {
    singleResource[RemoteAuthor]
  }
}

case class NeedAvatar(url: String) extends Need[File] {
  use(CachedAvatar(url), RemoteAvatar(url))
  from {
    // here we don’t use the REST sugar as above
    // in general, file APIs are more diverse
    // let me know, if you can come up with a good abstraction
    case e: AvatarEndpoint if e.url == url ⇒ e.probe
  }
}

import ExecutionContext.Implicits.global
val book: Future[Book] = NeedBook("12").go
```

This looks like a lot of code, but now if you want to add another endpoint, you just need a couple of lines.

### Status

RC2!

```scala
resolvers += "Stanch@bintray" at "http://dl.bintray.com/stanch/maven"

libraryDependencies += "org.needs" %% "needs" % "1.0.0-RC2"
```

### Cake baking cookbook

This section will be expanded with more detailed info and scaladoc links.

* `json`: `JsonEndpoint`
* `http`: `HttpEndpoint` and two optional clients: [Dispatch](https://github.com/stanch/needs/blob/master/src/main/scala/org/needs/http/DispatchClients.scala) and [AndroidAsync](https://github.com/stanch/needs/blob/master/src/main/scala/org/needs/http/AndroidClients.scala)
* `file`: `FileEndpoint`, `LocalFileEndpoint`, `HttpFileEndpoint` (see [code](https://github.com/stanch/needs/blob/master/src/main/scala/org/needs/file/Endpoints.scala))
* `rest`: A mix of JSON and HTTP with reasonable conventions

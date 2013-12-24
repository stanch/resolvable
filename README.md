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

We will now split our problem into dealing with [endpoints](#endpoints), [dependencies](#needs)
and [(JSON) deserialization](#deserialization).

### Endpoints

Endpoints are probably the simplest part of the equation. Let’s start by baking in some common conventions:

```scala
abstract class SingleResource(val baseUrl: String)
  extends rest.SingleResourceEndpoint
  with http.DispatchJsonClient
  
abstract class MultipleResources(val baseUrl: String)
  extends rest.MultipleResourceEndpoint
  with http.DispatchJsonClient

case class RemoteBook(id: String)
  extends SingleResource("/webservice/api/books")

case class RemoteAuthor(id: String)
  extends SingleResource("/webservice/api/authors")
  
case class RemoteAuthors(ids: Set[String])
  extends MultipleResources("/webservice/api/authors")
```

The local endpoints currently require some boilerplate. Here’s an example:

```scala
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
  
  val baseUrl = "/webservice/files" // file will be loaded from /webservice/files/:url
}
```

### A small detour: Fulfillable

Let’s call `Fulfillable[A]` **something, that knows how and where to get an `A` (asynchronously)**.
This implies that in our example `Fulfillable[Book]` will need a little help from some `Fulfillable[Author]`
(which in its turn needs a `Fulfillable[File]`): you can’t get a `Book` without an `Author`!

### Needs

A concrete example of a `Fulfillable` is a `Need`. The `Need[A]` describes how to get an `A` by sequentially probing
a number of `Endpoint`s:

```scala
case class NeedBook(id: String) extends Need[Book] with rest.Probing[Book] {
  // list endpoints
  use(RemoteBook(id), LocalBook(id))
  
  // describe how to load from them
  from {
    // using REST sugar
    singleResource[RemoteBook]
  }
  from {
    // using pattern matching
    // (we say that a book is downloadable from LocalBook with the same id)
    case b @ LocalBook(`id`) ⇒ b.probe
  }
}
```

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

RC1!

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

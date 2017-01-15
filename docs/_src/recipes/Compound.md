# Compound responses

Something like [json-api](http://jsonapi.org/format/) may return JSON with many objects in it:

```javascript
{
  "links": {
    "books.author": {
      "href": "http://example.com/authors/{books.author}",
      "type": "authors"
    }
  },
  "books": [{
    "id": "1",
    "title": "When In Doubt, Roll",
    "links": {
      "author": "1"
    }
  }],
  "linked": {
    "authors": [{
      "id": "1",
      "name": "Bill Bruford"
    }]
  }
}
```

Normally we would fetch an `Author` from `http://example.com/people/{id}`.
However, if we already got this JSON for a book, we can reuse it.

Let’s write a `Source` for an `Author`, that will be able to use both `AuthorEndpoint` and `CompoundBookEndpoint`.

The first step is to understand that `Source` can take 4 parameters:
1. Endpoints used by default
2. How to get data from each supported endpoint
3. Priority for supported endpoints
4. How to add more optimal endpoints

We only need the first three to make it work.

The default endpoint is `AuthorEndpoint`:

```scala
def needAuthor(id: String) =
  Source[Author](AuthorEndpoint(id))(
    ...,
    ...
  )
```

Next, we show how to load an `Author` from both endpoints:

```scala
{
  case e @ AuthorEndpoint(`id`) ⇒
    // just load normally
    Resolvable[A].fromEndpoint(e)
  case e @ CompoundBookEndpoint(_) ⇒
    // inside the data returned by the endpoint,
    // try to find the author we are looking for
    // if not found, the endpoint will be simply skipped
    Resolvable[A].fromEndpointPath(e) { js ⇒
      (js \ "linked" \ "authors").find(_ \ "id" == JsString(id)).get
    }
}
```

Finally, we need to assign higher priority to the compound endpoint, to avoid unnecessary fetching of `AuthorEndpoint`:

```scala
{
  case CompoundBookEndpoint(_) ⇒ Seq(1)
  case AuthorEndpoint(_) ⇒ Seq(0) // default
}
```

Assembling everything together, we get:

```scala
def needAuthor(id: String) =
  Source[Author](AuthorEndpoint(id))({
    case e @ AuthorEndpoint(`id`) ⇒
      Resolvable[A].fromEndpoint(e)
    case e @ CompoundBookEndpoint(_) ⇒
      Resolvable[A].fromEndpointPath(e) { js ⇒
        (js \ "linked" \ "authors").find(_ \ "id" == JsString(id)).get
      }
  }, {
    case CompoundBookEndpoint(_) ⇒ Seq(1)
    case AuthorEndpoint(_) ⇒ Seq(0) // default
  })
```

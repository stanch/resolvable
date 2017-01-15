# Aggregated requests

In [CouchDB](http://couchdb.apache.org/) we can fetch a single document with an HTTP request like this:

```
GET mydb/doc-1
```

The response is the document in JSON.

We can also fetch multiple documents at once:

```
POST mydb/_all_docs?include_docs=true {"keys": ["doc-1", "doc-2"]}
```

The response contains a list of the requested documents.

We can optimize our workflow by aggregating all parallel requests into one!

For the purpose of this example, let’s imagine that we have these two endpoints:

```scala
case class SingleDocument(id: String) {
  type Data = JsValue
  ...
}
case class MultipleDocuments(ids: Set[String]) {
  type Data = List[JsValue]
  ...
}
```

Now we want to define a `Source` for a document, that would “glue” to the other documents being fetched.

The first step is to understand that `Source` can take 4 parameters:
1. Endpoints used by default
2. How to get data from each supported endpoint
3. Priority for supported endpoints
4. How to add more optimal endpoints

The first one is easy:

```scala
def document[A](id: String)(implicit rule: Rule[JsValue, Resolvable[A]]) =
  // use SingleDocument by default
  Source[A](SingleDocument(id)) {
    ...,
    ...,
    ...
  }
```

For the second one, we’ll teach our `Source` to handle both `SingleDocument` and `MultipleDocuments`:

```scala
{
  case e @ SingleDocument(`id`) ⇒
    // just load normally
    Resolvable[A].fromEndpoint(e)
  case e @ MultipleDocuments(ids) if ids contains id ⇒
    // inside the data returned by the endpoint,
    // find the document we are looking for
    Resolvable[A].fromEndpointPath(e)(_.find(_ \ "_id" == JsString(id)).get)
}
```

For aggregated requests to work, we need to give `MultipleDocuments` higher priority.
The priority also increases with the number of documents in the batch.

```scala
{
  case MultipleDocuments(ids) ⇒ Seq(1, ids.size)
  case SingleDocument(_) ⇒ Seq(0) // default
}
```

Finally, we need to supply the function that will add `MultipleDocuments` endpoints.
This function is of the form `(ExecutionContext, EndpointPool) ⇒ Future[EndpointPool]` and looks like this:

```scala
{ (ec, pool) ⇒
  Future.successful(pool.fold(Set.empty[String]) {
    case (ids, SingleDocument(id)) ⇒ ids + id
    case (ids, _) ⇒ ids
  } match {
    case x if x.size > 1 ⇒ EndpointPool(MultipleDocuments(x))
    case _ ⇒ EndpointPool.empty
  })
}
```

Assembling everything together, we get:

```scala
def document[A](id: String)(implicit rule: Rule[JsValue, Resolvable[A]]) =
  Source[A](SingleDocument(id))({
    case e @ SingleDocument(`id`) ⇒
      Resolvable[A].fromEndpoint(e)
    case e @ MultipleDocuments(ids) if ids contains id ⇒
      Resolvable[A].fromEndpointPath(e)(_.find(_ \ "_id" == JsString(id)).get)
  }, {
    case MultipleDocuments(ids) ⇒ Seq(1, ids.size)
    case SingleDocument(_) ⇒ Seq(0)
  }, { (ec, pool) ⇒
    Future.successful(pool.fold(Set.empty[String]) {
      case (ids, SingleDocument(id)) ⇒ ids + id
      case (ids, _) ⇒ ids
    } match {
      case x if x.size > 1 ⇒ EndpointPool(MultipleDocuments(x))
      case _ ⇒ EndpointPool.empty
    })
  })
```

Here is a typical use-case:

```scala
def needBook(id: String) = document[Book](id)
// this will issue only one request!
Resolvable.fromList(List("book-1", "book-2").map(needBook)).go
```
# Fallbacks

Suppose a `Book` can be fetched either from `LocalLibraryBook`, `RemoteLibraryBook1`, or `RemoteLibraryBook2` endpoints.
Normally we would want to try the local endpoint first.

This is expressed like so:

```scala
def needBook(id: String): Resolvable[Book] =
  Source[Book].from(LocalLibraryBook(id)) orElse
  Source[Book].from(RemoteLibraryBook1(id)) orElse
  Source[Book].from(RemoteLibraryBook2(id))
```

If any of the sources fails with an exception, the next one will be tried.

Note that the endpoints do not have to be of the same data type. For example, `RemoteLibraryBook1` may return JSON,
while `RemoteLibraryBook2` may return XML. In this case it is enough to define two deserialization `Rule`s:

* `Rule[JsValue, Resolvable[Book]]`
* `Rule[xml.Node, Resolvable[Book]]`

It is also possible to grab different pieces of what the endpoints return. For example (assuming JSON):

```scala
def needBook(id: String): Resolvable[Book] =
  Source[Book].fromPath(RemoteLibraryBook1(id))(_ \ "theBook" \ "entry") orElse
  Source[Book].fromPath(RemoteLibraryBook2(id))(_ \ "data")
```

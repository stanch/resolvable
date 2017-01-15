# Lists and Options

Sometimes we need more than one object. Here is how we can define a method to fetch several books efficiently:

```scala
def needBook(id: String) =
  Source[Book].from(BookEndpoint(id))

def needBooks(ids: List[String]) =
  Resolvable.fromList(ids.map(needBook))
```

`Resolvable.fromList` also helps in deserialization: if a book has several authors, we can write:

```scala
case class Book(id: String, title: String, authors: List[Author])

implicit val bookRule = Resolvable.rule[JsValue, Book] { __ ⇒
  (__ \ "id").read[String] and
  (__ \ "title").read[Stirng] and
  (__ \ "authorIds").read[List[String]]
    .fmap(ids ⇒ ids.map(needAuthor))
    .fmap(Resolvable.fromList)
}
```

The same approach can be used with `Option`s:

```scala
case class Author(id: String, name: String, pet: Option[Pet])

implicit val authorRule = Resolvable.rule[JsValue, Author] { __ ⇒
  (__ \ "id").read[String] and
  (__ \ "name").read[Stirng] and
  (__ \ "petId").read[Option[String]]
    .fmap(maybeId ⇒ maybeId.map(needPet))
    .fmap(Resolvable.fromOption)
}
```
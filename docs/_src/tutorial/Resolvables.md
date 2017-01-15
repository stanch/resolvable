# Resolvables

The introduction really called for an abstraction in shiny armor. Well, here it is!
`Resolvable[A]` is something that can be resolved given a pool of endpoints:

```scala
def needBook(id: String): Resolvable[Book] = ???
val book: Future[Book] = needBook.go // `go` uses the default endpoints
```

When you combine `Resolvable`s using combinators, they form a dependency tree, which will fetch the endpoints
in the most optimal way, going layer by layer (i.e. breadth-first):

```scala
// if the books have the same author, it will be fetched only once
Resolvable.fromList(List(
  needBook("book-1"),
  needBook("book-2")
)).go // Future[List[Book]]

// if needBook("book-1") fails, needBook("book-2") will be fetched
(needBook("book-1") orElse needBook("book-2")).go // Future[Book]

// map and flatMap also work!
needBook("book-1").map(_.author.name).go // Future[String]
```

Next, we are going to learn how to create `Resolvable`s by declaring endpoints, sources and deserialization.
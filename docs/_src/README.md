# Introduction

This Scala library allows to fetch nested data like

```scala
Book(
  "book-1",
  "The Design of Everyday Things",
  Author(
    "author-1",
    "Donald Norman"
  )
)
```

from a number of endpoints, local or remote.

You might wonder why you would need such a library... Well, here is a simple case where this really gets out of hand fast:

* 2 remote databases with books: XML and JSON
* 1 remote database with authors
* 1 local database with authors (try first!)
* authors have avatars stored in Amazon S3
* avatar cache (try first!)
* download things in batches, but
* never download the same thing twice

And, finally, all we want is a function `needBook(id: String): Future[Book]` that takes care of all this.

If this sounds like your problem, proceed to the [tutorial](Tutorial.html).

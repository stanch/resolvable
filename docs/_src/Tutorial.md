# Guide

So this happens. We have a book:

```scala
case class Book(id: String, title: String, author: Author)
```

A book has an author:

```scala
case class Author(id: String, name: String)
```

We ask a webservice for a book, and it gives us this:

```javascript
{
  "id": "book-1",
  "title": "The Design of Everyday Things",
  "authorId": "author-1"
}
```

We then have to ask for an author:

```javascript
{
  "id": "author-1",
  "name": "Donald Norman"
}
```

Finally, we can assemble our data:

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

Sounds simple, right?

But what if...

* *There is more than one webservice*
  
  We might need to try a couple different libraries before we find the book.
  Assuming the same for the author, that gives at least 4 different options.
  
* *Each webservice has its own data format*

  JSON, XML — you name it. There could be different different JSON structures as well!
  
* *There is a local cache*

  Actually “The Design of Everyday Things” is on the desktop. No need to reach to the web!

* *Some data is duplicated*

  If we want both “The Design of Everyday Things” and “Emotional Design: Why We Love (or Hate) Everyday Things”,
  why download the Donald Norman entry twice?
  
* *The data model is really deep*

  Top-10 lists have books, books have authors, authors have avatars.
  All that is stored in different places, yet we want a `case class` tree back.
  
* *Webservice supports aggregated requests*

  We can issue 1 request for 10 books at once!
  
* *Webservice supports compound documents, [json-api](http://jsonapi.org/)-style*

  The book and the author may arrive in the same JSON (but not in the nested structure we want).
  
This library allows to optimize for all of the above at once. As a bonus, your abstract data model remains completely untouched :)
Say “no” to the ugly Java annotations!

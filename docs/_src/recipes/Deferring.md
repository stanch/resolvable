# Deferring

Consider an example of a `User` `case class`:

```scala
case class User(name: String, avatar: File)
```

Deserialization logic would look like this (`needAvatar` downloads the avatar from somewhere):

```scala
implicit val userRule = Resolvable.rule[JsValue, User] { __ ⇒
  (__ \ "name").read[String] and
  (__ \ "avatarUrl").read[String].fmap(needAvatar)
}
```

The problem here is that our entire data tree will wait until the pictures are downloaded. This may take time, and we could
spend it more efficiently, for example, by showing at least user names in advance. Therefore a more suitable data structure would be

```scala
case class User(name: String, avatar: Future[File])
```

With this we can manipulate the data and handle the avatar asynchronously. The missing piece is the deserialization, and this is
where we are going to use a special trick:

```scala
implicit val userRule = Resolvable.rule[JsValue, User] { __ ⇒
  (__ \ "name").read[String] and
  // notice the Resolvable.defer combinator
  (__ \ "avatarUrl").read[String].fmap(needAvatar).fmap(Resolvable.defer)
}
```

And here is the typical use-case:

```scala
async {
  val user = await(needUser("123").go)
  // show the name
  val avatar = await(user.avatar)
  // show the avatar later
}
```
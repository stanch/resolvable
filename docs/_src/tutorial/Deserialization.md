# Deserialization

The deserialization bit looks almost identical to the normal [Play 2.3 validation API](http://jto.github.io/articles/play_new_validation_api/).
The differences are:
* We are using `Resolvable.rule` instead of `From` to generate rules;
* We get a `Rule[JsValue, Resolvable[A]]` instead of `Rule[JsValue, A]`.

```scala
import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.data.mapping.json.Rules._

trait JsonFormats { self: Sources ⇒

  // Rule[JsValue, Resolvable[Book]]
  implicit val bookRule = Resolvable.rule[JsValue, Book] { __ ⇒
    (__ \ "id").read[String] and
    (__ \ "title").read[String] and
    // here's where the magic happens!
    // we read a string and map it to a Resolvable[Author]
    (__ \ "authorId").read[String].fmap(needAuthor)
  }
  
  // Rule[JsValue, Resolvable[Author]]
  implicit val authorRule = Resolvable.rule[JsValue, Author] { __ ⇒
    (__ \ "id").read[String] and
    (__ \ "name").read[String]
  }
  
}
```

Our cake is almost ready!
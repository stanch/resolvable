# Sources

Sources are a special kind of `Resolvable`s: they know from which `Endpoint`s they should be resolved.
In this example our `Sources` trait is a slice of cake that includes `Endpoints` seen previously and `JsonRules`,
which we’ll define in the next section.

```scala
import resolvable.Source
import play.api.data.mapping.json.Rules._

trait Sources { self: Endpoints with JsonRules ⇒

  def book(id: String): Resolvable[Book] =
    // try RemoteBook1 first
    Source[Book].from(RemoteBook1(id)) orElse
    // fallback to RemoteBook2
    Source[Book].from(RemoteBook2(id))
  
  def author(id: String): Resolvable[Author] =
    Source[Author].from(RemoteAuthor(id))
    
}
```

That was easy... but how does it know how to load `Book`s from, say, `RemoteBook1`, which fetches `JsValue`?
Good question. We need some deserialization logic.
We’ll use the cutting edge [Play 2.3 validation API](http://jto.github.io/articles/play_new_validation_api/)!
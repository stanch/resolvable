# Wrapping up

Finally, we bake our cake and enjoy:

```scala
object BookApi extends Endpoints with Sources with JsonRules

import scala.concurrent.ExecutionContext.Implicits.global
val book: Future[Book] = BookApi.needBook("book-1").go // hurray!
```

The example is not complete w.r.t. all the optimizations claimed in the introduction. But come on, it even deals with
a fictional web service. For a better idea on how things work, head to the [guide](/Guide.html). If you are specifically interested
in how to do this or that, check out the [recipes](/Recipes.html) section.
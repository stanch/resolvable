package resolvable.flickr

import resolvable.Resolvable
import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.data.mapping.json.Rules._

private[flickr] trait JsonFormats {
  def person(id: String): Resolvable[Person]

  implicit val photoRule = Resolvable.rule[JsValue, Photo] { __ ⇒
    (__ \ "id").read[String] and
    (
      (__ \ "farm").read[Int] and
      (__ \ "server").read[String] and
      (__ \ "id").read[String] and
      (__ \ "secret").read[String]
    ).tupled.fmap { case (farm, server, id, secret) ⇒ s"http://farm$farm.staticflickr.com/$server/${id}_$secret.jpg" } and
    (__ \ "title").read[String] and
    (__ \ "owner").read[String].fmap(person)
  }

  implicit val ownerReads = Resolvable.rule[JsValue, Person] { __ ⇒
    (__ \ "person" \ "nsid").read[String] and
    (__ \ "person" \ "username" \ "_content").read[String]
  }
}

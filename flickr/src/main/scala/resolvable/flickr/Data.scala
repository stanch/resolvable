package resolvable.flickr

case class FlickrApiException(message: String) extends Exception(message)

case class Photo(id: String, url: String, title: String, owner: Person)

case class Person(id: String, name: String)



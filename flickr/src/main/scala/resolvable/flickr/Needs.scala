package resolvable.flickr

import resolvable.{Resolvable, Source}
import play.api.data.mapping.json.Rules._

private[flickr] trait Needs { self: Endpoints with JsonFormats ⇒
  def nearbyPhotos(lat: Double, lng: Double, radius: Int) = Source[List[Resolvable[Photo]]]
    .fromPath(NearbyPhotos(lat, lng, radius))(_ \ "photos" \ "photo")
    .flatMap(Resolvable.fromList)

  def person(id: String): Resolvable[Person] = Source[Person].from(PersonInfo(id))
}

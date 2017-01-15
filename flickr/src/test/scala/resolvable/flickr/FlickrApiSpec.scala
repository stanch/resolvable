package resolvable.flickr

import resolvable.http.DispatchClient
import scala.concurrent.ExecutionContext.Implicits.global
import org.scalatest.FlatSpec
import resolvable.{Endpoint, EndpointLogger}
import org.apache.commons.io.FileUtils
import java.io.File

class FlickrApiSpec extends FlatSpec {
  val key = FileUtils.readFileToString(new File("local-key.txt"))
  val api = Api(key, new DispatchClient, EndpointLogger.println())

  it should "do something" in {
    api.nearbyPhotos(37, 9, 5).go onComplete println
  }
}

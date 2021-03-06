package im.ghasedak.server.frontend

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.stream.Materializer
import com.typesafe.config.Config

import scala.collection.JavaConverters._
import scala.concurrent.Future

object EndpointType {

  def fromConfig(str: String): EndpointTypes.EndpointType = {
    str match {
      case "grpc" ⇒ EndpointTypes.Grpc
    }
  }

}

object EndpointTypes {

  sealed trait EndpointType

  case object Grpc extends EndpointType

}

object Endpoint {

  def fromConfig(config: Config): Endpoint = {
    Endpoint(EndpointType.fromConfig(config.getString("type")), config.getString("interface"), config.getInt("port"))
  }

}

case class Endpoint(typ: EndpointTypes.EndpointType, interface: String, port: Int)

object Frontend {

  def start(services: HttpRequest ⇒ Future[HttpResponse])(
    implicit
    system: ActorSystem,
    mat:    Materializer,
    config: Config): Unit = {
    config.getConfigList("endpoints").asScala.map(Endpoint.fromConfig) map (startEndpoint(_, services))
  }

  private def startEndpoint(endpoint: Endpoint, services: HttpRequest ⇒ Future[HttpResponse])(
    implicit
    system: ActorSystem,
    mat:    Materializer): Future[Unit] = {
    endpoint.typ match {
      case EndpointTypes.Grpc ⇒ GrpcFrontend.start(endpoint.interface, endpoint.port, services)
    }
  }

}
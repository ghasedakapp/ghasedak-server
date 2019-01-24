package im.ghasedak.server

import java.net.ServerSocket

import akka.actor.ActorSystem
import akka.grpc.GrpcClientSettings
import akka.stream.ActorMaterializer
import com.typesafe.config.{ Config, ConfigFactory }
import im.ghasedak.rpc.auth.{ AuthServiceClient, AuthServiceClientPowerApi }
import im.ghasedak.rpc.contact.{ ContactServiceClient, ContactServiceClientPowerApi }
import im.ghasedak.rpc.messaging.{ MessagingServiceClient, MessagingServiceClientPowerApi }
import im.ghasedak.rpc.test.{ TestServiceClient, TestServiceClientPowerApi }
import im.ghasedak.rpc.user.{ UserServiceClient, UserServiceClientPowerApi }
import im.ghasedak.server.config.{ AppType, GhasedakConfigFactory }
import im.ghasedak.server.db.DbExtension
import im.ghasedak.server.model.org.ApiKey
import im.ghasedak.server.utils.UserTestUtils
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{ BeforeAndAfterAll, FlatSpec, Inside, Matchers }

import scala.collection.JavaConverters._
import scala.concurrent.Await
import scala.concurrent.duration.Duration

// todo: config this for parallel execution
abstract class GrpcBaseSuit extends FlatSpec
  with Matchers
  with ScalaFutures
  with Inside
  with UserTestUtils
  with BeforeAndAfterAll {

  private def randomPort: Int = {
    val socket = new ServerSocket(0)
    try {
      socket.setReuseAddress(true)
      socket.getLocalPort
    } finally {
      socket.close()
    }
  }

  private def createConfig: Config = {
    ConfigFactory.empty().withFallback(ConfigFactory.parseString(
      s"""
         |endpoints: [
         |  {
         |    type: grpc
         |    interface: 0.0.0.0
         |    port: $randomGrpcPort
         |  }
         |]
         |akka.grpc.client {
         |  ghasedak {
         |    host = 127.0.0.1
         |    port = $randomGrpcPort
         |    use-tls = false
         |  }
         |}
         |akka.remote.netty.tcp.port: $randomAkkaPort
      """.stripMargin))
      .withFallback(GhasedakConfigFactory.load(AppType.Test))
  }

  protected val randomAkkaPort: Int = randomPort

  protected val randomGrpcPort: Int = randomPort

  protected val config: Config = createConfig

  protected val officialApiKeys: Seq[ApiKey] =
    config.getConfigList("module.auth.official-api-keys")
      .asScala.map { conf ⇒
        ApiKey(
          conf.getInt("org-id"),
          conf.getString("api-key"))
      }

  protected implicit val system: ActorSystem = GhasedakServerBuilder.start(config)
  protected implicit val mat = ActorMaterializer()
  protected implicit val ec = system.dispatcher

  protected val db = DbExtension(system).db

  protected val testStub: TestServiceClientPowerApi = TestServiceClient(GrpcClientSettings.fromConfig("ghasedak"))

  protected val authStub: AuthServiceClientPowerApi = AuthServiceClient(GrpcClientSettings.fromConfig("ghasedak"))

  protected val messagingStub: MessagingServiceClientPowerApi = MessagingServiceClient(GrpcClientSettings.fromConfig("ghasedak"))

  protected val contactStub: ContactServiceClientPowerApi = ContactServiceClient(GrpcClientSettings.fromConfig("ghasedak"))

  protected val userStub: UserServiceClientPowerApi = UserServiceClient(GrpcClientSettings.fromConfig("ghasedak"))

  override def afterAll(): Unit = {
    super.afterAll()
    Await.result(system.terminate(), Duration.Inf)
  }

}

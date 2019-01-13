package im.ghasedak.server

import java.net.ServerSocket

import akka.actor.ActorSystem
import com.typesafe.config.{ Config, ConfigFactory }
import im.ghasedak.rpc.auth.AuthServiceGrpc
import im.ghasedak.rpc.contact.ContactServiceGrpc
import im.ghasedak.rpc.messaging.MessagingServiceGrpc
import im.ghasedak.rpc.test.TestServiceGrpc
import im.ghasedak.rpc.user.UserServiceGrpc
import io.grpc.{ ManagedChannel, ManagedChannelBuilder }
import im.ghasedak.server.db.DbExtension
import im.ghasedak.server.config.{ AppType, GhasedakConfigFactory }
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

  protected val system: ActorSystem = GhasedakServerBuilder.start(config)

  protected val db = DbExtension(system).db

  protected val channel: ManagedChannel =
    ManagedChannelBuilder.forAddress("127.0.0.1", randomGrpcPort).usePlaintext.build

  protected val testStub: TestServiceGrpc.TestServiceBlockingStub =
    TestServiceGrpc.blockingStub(channel)

  protected val authStub: AuthServiceGrpc.AuthServiceBlockingStub =
    AuthServiceGrpc.blockingStub(channel)

  protected val messagingStub: MessagingServiceGrpc.MessagingServiceBlockingStub =
    MessagingServiceGrpc.blockingStub(channel)

  protected val contactStub: ContactServiceGrpc.ContactServiceBlockingStub =
    ContactServiceGrpc.blockingStub(channel)

  protected val userStub: UserServiceGrpc.UserServiceBlockingStub =
    UserServiceGrpc.blockingStub(channel)

  override def afterAll(): Unit = {
    super.afterAll()
    Await.result(system.terminate(), Duration.Inf)
  }

}
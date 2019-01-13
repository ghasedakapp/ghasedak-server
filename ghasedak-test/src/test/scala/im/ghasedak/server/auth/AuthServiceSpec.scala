package im.ghasedak.server.auth

import java.time.temporal.ChronoUnit
import java.time.{ LocalDateTime, ZoneOffset }
import java.util.UUID

import im.ghasedak.rpc.auth.{ RequestSignOut, RequestSignUp, RequestStartPhoneAuth, RequestValidateCode }
import im.ghasedak.rpc.test.RequestTestAuth
import io.grpc.Status.Code
import io.grpc.StatusRuntimeException
import im.ghasedak.server.repo.auth.{ AuthTransactionRepo, GateAuthCodeRepo }
import im.ghasedak.server.GrpcBaseSuit
import im.ghasedak.server.rpc.Constant

import scala.util.{ Failure, Random, Try }

class AuthServiceSpec extends GrpcBaseSuit {

  behavior of "AuthServiceImpl"

  it should "start phone auth and get transaction hash" in startPhoneAuth

  it should "get same transaction hash in twice start phone auth" in sameTransactionHash

  it should "get different transaction hash in twice start phone auth after expiration" in expireTransactionHash

  it should "get invalid phone number in start phone auth" in invalidPhoneNumber

  it should "return error in validate code with invalid transaction" in invalidTransaction

  it should "validate auth code" in testValidateCode

  it should "return invalid auth code in validate phone auth code" in invalidAuthCode

  it should "return auth code expired in validate phone auth code" in authCodeExpired

  it should "successfully sign up" in signUp

  it should "sign up and after that sign in" in signUpAndSignIn

  it should "authorized client after sign up" in authorizedAfterSignUp

  it should "return right error in authorize method" in rightAuthorizeError

  it should "return different transaction hash after validate" in differentTransactionHashAfterValidate

  it should "sign out and cant send request again" in signOut

  it should "successfully login with test phone number" in testPhoneNumber

  def startPhoneAuth(): Unit = {
    val request = RequestStartPhoneAuth(generatePhoneNumber(), officialApiKeys.head.apiKey)
    val response = authStub.startPhoneAuth(request)
    response.transactionHash should not be empty
    db.run(GateAuthCodeRepo.find(response.transactionHash)).futureValue should not be None
  }

  def sameTransactionHash(): Unit = {
    val request = RequestStartPhoneAuth(generatePhoneNumber(), officialApiKeys.head.apiKey)
    val response1 = authStub.startPhoneAuth(request)
    val response2 = authStub.startPhoneAuth(request)
    response1.transactionHash shouldEqual response2.transactionHash
  }

  def expireTransactionHash(): Unit = {
    val request = RequestStartPhoneAuth(generatePhoneNumber(), officialApiKeys.head.apiKey)
    val response1 = authStub.startPhoneAuth(request)
    val now = LocalDateTime.now(ZoneOffset.UTC)
    val oldDate = now.minusMinutes(20)
    oldDate.until(now, ChronoUnit.MINUTES) shouldEqual 20
    db.run(AuthTransactionRepo.updateCreateAt(response1.transactionHash, oldDate)).futureValue
    val response2 = authStub.startPhoneAuth(request)
    response1.transactionHash should not equal response2.transactionHash
  }

  def invalidPhoneNumber(): Unit = {
    val request = RequestStartPhoneAuth(2, officialApiKeys.head.apiKey)
    Try(authStub.startPhoneAuth(request)) match {
      case Failure(ex: StatusRuntimeException) ⇒
        ex.getStatus.getCode shouldEqual Code.INTERNAL
        ex.getTrailers.get(Constant.TAG_METADATA_KEY) shouldEqual "INVALID_PHONE_NUMBER"
    }
  }

  def invalidTransaction(): Unit = {
    val request = RequestValidateCode(UUID.randomUUID().toString, "12345")
    Try(authStub.validateCode(request)) match {
      case Failure(ex: StatusRuntimeException) ⇒
        ex.getStatus.getCode shouldEqual Code.INTERNAL
        ex.getTrailers.get(Constant.TAG_METADATA_KEY) shouldEqual "AUTH_CODE_EXPIRED"
    }
  }

  def testValidateCode(): Unit = {
    val request1 = RequestStartPhoneAuth(generatePhoneNumber(), officialApiKeys.head.apiKey)
    val response1 = authStub.startPhoneAuth(request1)
    val codeGate = db.run(GateAuthCodeRepo.find(response1.transactionHash)).futureValue
    val request2 = RequestValidateCode(response1.transactionHash, codeGate.get.codeHash)
    val response2 = authStub.validateCode(request2)
    response2.isRegistered shouldEqual false
    response2.apiAuth shouldEqual None
  }

  def invalidAuthCode(): Unit = {
    val request1 = RequestStartPhoneAuth(generatePhoneNumber(), officialApiKeys.head.apiKey)
    val response1 = authStub.startPhoneAuth(request1)
    val request2 = RequestValidateCode(response1.transactionHash, "12345")
    Try(authStub.validateCode(request2)) match {
      case Failure(ex: StatusRuntimeException) ⇒
        ex.getStatus.getCode shouldEqual Code.INTERNAL
        ex.getTrailers.get(Constant.TAG_METADATA_KEY) shouldEqual "INVALID_AUTH_CODE"
    }
  }

  def authCodeExpired(): Unit = {
    val request1 = RequestStartPhoneAuth(generatePhoneNumber(), officialApiKeys.head.apiKey)
    val response1 = authStub.startPhoneAuth(request1)
    val now = LocalDateTime.now(ZoneOffset.UTC)
    val oldDate = now.minusMinutes(20)
    oldDate.until(now, ChronoUnit.MINUTES) shouldEqual 20
    db.run(AuthTransactionRepo.updateCreateAt(response1.transactionHash, oldDate)).futureValue
    val codeGate = db.run(GateAuthCodeRepo.find(response1.transactionHash)).futureValue
    val request2 = RequestValidateCode(response1.transactionHash, codeGate.get.codeHash)
    Try(authStub.validateCode(request2)) match {
      case Failure(ex: StatusRuntimeException) ⇒
        ex.getStatus.getCode shouldEqual Code.INTERNAL
        ex.getTrailers.get(Constant.TAG_METADATA_KEY) shouldEqual "AUTH_CODE_EXPIRED"
    }
  }

  def signUp(): Unit = {
    val request1 = RequestStartPhoneAuth(generatePhoneNumber(), officialApiKeys.head.apiKey)
    val response1 = authStub.startPhoneAuth(request1)
    val codeGate = db.run(GateAuthCodeRepo.find(response1.transactionHash)).futureValue
    val request2 = RequestValidateCode(response1.transactionHash, codeGate.get.codeHash)
    val response2 = authStub.validateCode(request2)
    response2.isRegistered shouldEqual false
    response2.apiAuth shouldEqual None
    val request3 = RequestSignUp(
      response1.transactionHash,
      Random.alphanumeric.take(20).mkString)
    val response3 = authStub.signUp(request3)
    response3.isRegistered shouldEqual true
    response3.apiAuth should not be None
  }

  def signUpAndSignIn(): Unit = {
    val request1 = RequestStartPhoneAuth(generatePhoneNumber(), officialApiKeys.head.apiKey)
    val response1 = authStub.startPhoneAuth(request1)
    val codeGate1 = db.run(GateAuthCodeRepo.find(response1.transactionHash)).futureValue
    val request2 = RequestValidateCode(response1.transactionHash, codeGate1.get.codeHash)
    val response2 = authStub.validateCode(request2)
    response2.isRegistered shouldEqual false
    response2.apiAuth shouldEqual None
    val request3 = RequestSignUp(
      response1.transactionHash,
      Random.alphanumeric.take(20).mkString)
    val response3 = authStub.signUp(request3)
    response3.isRegistered shouldEqual true
    response3.apiAuth should not be None
    val request4 = request1
    val response4 = authStub.startPhoneAuth(request4)
    val codeGate2 = db.run(GateAuthCodeRepo.find(response4.transactionHash)).futureValue
    val request5 = RequestValidateCode(response4.transactionHash, codeGate2.get.codeHash)
    val response5 = authStub.validateCode(request5)
    response5.isRegistered shouldEqual true
    response5.apiAuth should not be None
    response5.apiAuth.get.user.get shouldEqual response3.apiAuth.get.user.get
    response5.apiAuth.get.token should not equal response3.apiAuth.get.token
  }

  def authorizedAfterSignUp(): Unit = {
    val user = createUserWithPhone()
    val stub = testStub.withInterceptors(clientTokenInterceptor(user.token))
    val response = stub.testAuth(RequestTestAuth())
    response.auth shouldEqual true
  }

  def rightAuthorizeError(): Unit = {
    val user = createUserWithPhone()
    val stub = testStub.withInterceptors(clientTokenInterceptor(user.token))
    Try(stub.testAuth(RequestTestAuth(true))) match {
      case Failure(ex: StatusRuntimeException) ⇒
        ex.getStatus.getCode shouldEqual Code.INTERNAL
        ex.getTrailers.get(Constant.TAG_METADATA_KEY) shouldEqual "AUTH_TEST_ERROR"
    }
  }

  def differentTransactionHashAfterValidate(): Unit = {
    val request1 = RequestStartPhoneAuth(generatePhoneNumber(), officialApiKeys.head.apiKey)
    val response1 = authStub.startPhoneAuth(request1)
    val codeGate = db.run(GateAuthCodeRepo.find(response1.transactionHash)).futureValue
    val request2 = RequestValidateCode(response1.transactionHash, codeGate.get.codeHash)
    val response2 = authStub.validateCode(request2)
    val response3 = authStub.startPhoneAuth(request1)
    response3.transactionHash should not equal response1.transactionHash
  }

  def signOut(): Unit = {
    val user = createUserWithPhone()
    val stub1 = authStub.withInterceptors(clientTokenInterceptor(user.token))
    stub1.signOut(RequestSignOut())
    val stub2 = testStub.withInterceptors(clientTokenInterceptor(user.token))
    Try(stub2.testAuth(RequestTestAuth())) match {
      case Failure(ex: StatusRuntimeException) ⇒
        ex.getStatus.getCode shouldEqual Code.UNAUTHENTICATED
        ex.getTrailers.get(Constant.TAG_METADATA_KEY) shouldEqual "INVALID_TOKEN"
    }
  }

  def testPhoneNumber(): Unit = {
    val (phoneNumber, code) = generateTestPhoneNumber()
    val request1 = RequestStartPhoneAuth(phoneNumber, officialApiKeys.head.apiKey)
    val response1 = authStub.startPhoneAuth(request1)
    val request2 = RequestValidateCode(response1.transactionHash, code)
    val response2 = authStub.validateCode(request2)
    response2.isRegistered shouldEqual false
    response2.apiAuth shouldEqual None
    val request3 = RequestSignUp(
      response1.transactionHash,
      Random.alphanumeric.take(20).mkString)
    val response3 = authStub.signUp(request3)
    response3.isRegistered shouldEqual true
    response3.apiAuth should not be None
  }

}
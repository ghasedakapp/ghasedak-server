package ir.sndu.server.utils.number

import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber
import com.typesafe.config.ConfigFactory

import scala.annotation.tailrec
import scala.util.Try

object PhoneNumberUtils {

  private val config = ConfigFactory.load()

  private val testPhoneNumberPrefix = config.getString("module.auth.test-phone-number.prefix")

  def parse(number: String, defaultCountry: String = ""): Seq[PhoneNumber] = {
    val phoneUtil = PhoneNumberUtil.getInstance()

    val tries = Try(phoneUtil.parse(s"+$number", defaultCountry)) +:
      (if (number.startsWith("+"))
        Seq.empty
      else
        Seq(Try(phoneUtil.parse(number, defaultCountry))))

    tries.view.map(_.toOption).distinct.flatten.toSeq
  }

  def normalizeStr(number: String, defaultCountry: String = ""): Seq[Long] = {
    parse(number, defaultCountry) map { p ⇒
      val phoneNumber = p.getCountryCode * Math.pow(10L, (sizeOf(p.getNationalNumber) + 1).toDouble).longValue + p.getNationalNumber
      phoneNumber
    }
  }

  def normalizeWithCountry(number: Long, defaultCountry: String = ""): Seq[(Long, String)] = {
    parse(number.toString, defaultCountry) map { p ⇒
      val phoneNumber = p.getCountryCode * Math.pow(10L, (sizeOf(p.getNationalNumber) + 1).toDouble).longValue + p.getNationalNumber
      (phoneNumber, PhoneNumberUtil.getInstance().getRegionCodeForCountryCode(p.getCountryCode))
    }
  }

  def isTestPhone(number: Long): Boolean = number.toString.startsWith(testPhoneNumberPrefix)

  def isValid(number: String, defaultCountry: String = ""): Boolean = normalizeStr(number, defaultCountry).nonEmpty

  def normalizeLong(number: Long, defaultCountry: String = ""): Seq[Long] = normalizeStr(s"$number", defaultCountry)

  def tryNormalize(number: Long, defaultCountry: String = ""): Long = normalizeLong(number, defaultCountry).headOption.getOrElse(number)

  private def sizeOf(number: Long): Long = {
    @tailrec
    def f(n: Long, res: Long): Long = {
      if (n >= 10) f(n / 10, res + 1)
      else res
    }
    f(number, 0)
  }
}

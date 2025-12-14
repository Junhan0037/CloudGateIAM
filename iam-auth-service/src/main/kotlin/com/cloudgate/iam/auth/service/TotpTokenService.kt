package com.cloudgate.iam.auth.service

import com.cloudgate.iam.auth.config.MfaProperties
import org.springframework.stereotype.Component
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.time.Clock
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.pow

/**
 * TOTP 시크릿 생성·검증과 otpauth 프로비저닝 URI 생성을 담당
 */
@Component
class TotpTokenService(
    private val clock: Clock,
    private val mfaProperties: MfaProperties
) {

    private val secureRandom: SecureRandom = SecureRandom()
    private val otpRegex = Regex("^\\d{${mfaProperties.digits}}$")

    /**
     * 안전한 시드로 Base32 시크릿을 생성
     */
    fun generateSecret(): String {
        val buffer = ByteArray(SECRET_BYTE_SIZE)
        secureRandom.nextBytes(buffer)
        return Base32Codec.encode(buffer)
    }

    /**
     * 사용자가 인증 앱에서 스캔할 수 있는 otpauth URI를 생성
     */
    fun buildProvisioningUri(secret: String, accountLabel: String): String {
        val encodedIssuer = URLEncoder.encode(mfaProperties.issuer, StandardCharsets.UTF_8)
        val encodedAccount = URLEncoder.encode(accountLabel, StandardCharsets.UTF_8)
        return "otpauth://totp/$encodedIssuer:$encodedAccount?secret=$secret&issuer=$encodedIssuer&digits=${mfaProperties.digits}&period=${mfaProperties.periodSeconds}"
    }

    /**
     * 입력된 OTP 코드가 시계 드리프트 허용 범위 내에서 유효한지 검증
     */
    fun verifyCode(secret: String, code: String): Boolean {
        val normalized = code.trim()
        if (!otpRegex.matches(normalized)) {
            return false
        }

        val secretKey = try {
            Base32Codec.decode(secret)
        } catch (ex: IllegalArgumentException) {
            return false
        }

        val currentStep = Instant.now(clock).epochSecond / mfaProperties.periodSeconds
        val maxDrift = mfaProperties.allowedDriftWindows.coerceAtLeast(0)

        for (offset in -maxDrift..maxDrift) {
            val step = currentStep + offset
            if (step < 0) {
                continue
            }
            val candidate = generateCode(secretKey, step)
            if (candidate == normalized) {
                return true
            }
        }

        return false
    }

    /**
     * 테스트와 내부 검증을 위해 특정 시점의 OTP를 계산
     */
    internal fun generateCode(secret: String, instant: Instant = Instant.now(clock)): String {
        val secretKey = Base32Codec.decode(secret)
        val timeStep = instant.epochSecond / mfaProperties.periodSeconds
        return generateCode(secretKey, timeStep)
    }

    private fun generateCode(secretKey: ByteArray, timeStep: Long): String {
        val data = ByteBuffer.allocate(8).putLong(timeStep).array()
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(secretKey, "HmacSHA1"))
        val hash = mac.doFinal(data)

        val offset = hash.last().toInt() and 0x0f
        val binary = ((hash[offset].toInt() and 0x7f) shl 24) or
            ((hash[offset + 1].toInt() and 0xff) shl 16) or
            ((hash[offset + 2].toInt() and 0xff) shl 8) or
            (hash[offset + 3].toInt() and 0xff)

        val divisor = 10.0.pow(mfaProperties.digits.toDouble()).toInt()
        val otp = binary % divisor
        return otp.toString().padStart(mfaProperties.digits, '0')
    }

    companion object {
        private const val SECRET_BYTE_SIZE: Int = 20
    }
}

/**
 * 외부 라이브러리에 의존하지 않고 Base32 인코딩/디코딩을 수행
 */
private object Base32Codec {
    private const val PADDING_CHAR = '='
    private val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray()
    private val lookup: Map<Char, Int> = alphabet
        .withIndex()
        .associate { it.value to it.index }

    fun encode(data: ByteArray): String {
        if (data.isEmpty()) {
            return ""
        }

        val builder = StringBuilder((data.size * 8 + 4) / 5)
        var buffer = 0
        var bitsLeft = 0

        for (byte in data) {
            buffer = (buffer shl 8) or (byte.toInt() and 0xff)
            bitsLeft += 8
            while (bitsLeft >= 5) {
                val index = (buffer shr (bitsLeft - 5)) and 0x1f
                builder.append(alphabet[index])
                bitsLeft -= 5
            }
        }

        if (bitsLeft > 0) {
            val index = (buffer shl (5 - bitsLeft)) and 0x1f
            builder.append(alphabet[index])
        }

        return builder.toString()
    }

    fun decode(value: String): ByteArray {
        val sanitized = value
            .trim()
            .replace(PADDING_CHAR.toString(), "")
            .uppercase()

        if (sanitized.isEmpty()) {
            return ByteArray(0)
        }

        val output = ByteArray((sanitized.length * 5) / 8)
        var buffer = 0
        var bitsLeft = 0
        var index = 0

        for (char in sanitized) {
            val lookupValue = lookup[char] ?: throw IllegalArgumentException("지원하지 않는 Base32 문자입니다.")
            buffer = (buffer shl 5) or lookupValue
            bitsLeft += 5

            if (bitsLeft >= 8) {
                output[index++] = ((buffer shr (bitsLeft - 8)) and 0xff).toByte()
                bitsLeft -= 8
            }
        }

        return if (index == output.size) {
            output
        } else {
            output.copyOf(index)
        }
    }
}

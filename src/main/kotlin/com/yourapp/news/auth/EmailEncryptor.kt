package com.yourapp.news.auth

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 이메일 암호화/복호화 유틸리티
 * AES-GCM 암호화 사용 (인증 태그 포함)
 */
@Component
class EmailEncryptor(
    @Value("\${email.encryption.key}") private val encryptionKey: String
) {
    companion object {
        private const val ALGORITHM = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128
    }

    private val secretKey: SecretKeySpec by lazy {
        // 키가 32바이트가 아니면 SHA-256으로 해싱하여 32바이트로 만듦
        val keyBytes = if (encryptionKey.length >= 32) {
            encryptionKey.substring(0, 32).toByteArray(Charsets.UTF_8)
        } else {
            java.security.MessageDigest.getInstance("SHA-256")
                .digest(encryptionKey.toByteArray(Charsets.UTF_8))
        }
        SecretKeySpec(keyBytes, "AES")
    }

    /**
     * 이메일 암호화
     * @return Base64 인코딩된 암호화 문자열 (IV + 암호문)
     */
    fun encrypt(email: String): String {
        val cipher = Cipher.getInstance(ALGORITHM)

        // 랜덤 IV 생성
        val iv = ByteArray(GCM_IV_LENGTH)
        SecureRandom().nextBytes(iv)

        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)

        val encryptedBytes = cipher.doFinal(email.toByteArray(Charsets.UTF_8))

        // IV + 암호문을 합쳐서 Base64 인코딩
        val combined = ByteArray(iv.size + encryptedBytes.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(encryptedBytes, 0, combined, iv.size, encryptedBytes.size)

        return Base64.getEncoder().encodeToString(combined)
    }

    /**
     * 이메일 복호화
     * @param encryptedEmail Base64 인코딩된 암호화 문자열
     * @return 복호화된 이메일
     */
    fun decrypt(encryptedEmail: String): String {
        val combined = Base64.getDecoder().decode(encryptedEmail)

        // IV 추출
        val iv = ByteArray(GCM_IV_LENGTH)
        System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH)

        // 암호문 추출
        val encryptedBytes = ByteArray(combined.size - GCM_IV_LENGTH)
        System.arraycopy(combined, GCM_IV_LENGTH, encryptedBytes, 0, encryptedBytes.size)

        val cipher = Cipher.getInstance(ALGORITHM)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

        return String(cipher.doFinal(encryptedBytes), Charsets.UTF_8)
    }

    /**
     * 이메일 해시 생성 (검색용)
     * 같은 이메일은 항상 같은 해시값을 반환
     */
    fun hash(email: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest((email.lowercase() + encryptionKey).toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(hashBytes)
    }
}

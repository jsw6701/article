package com.yourapp.news.auth

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Service
import java.security.SecureRandom

@Service
class EmailService(
    private val mailSender: JavaMailSender,
    @Value("\${email.verification.expire-minutes:10}") private val expireMinutes: Int,
    @Value("\${email.verification.code-length:6}") private val codeLength: Int
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val random = SecureRandom()

    /**
     * 인증 코드 생성
     */
    fun generateVerificationCode(): String {
        val chars = "0123456789"
        return (1..codeLength)
            .map { chars[random.nextInt(chars.length)] }
            .joinToString("")
    }

    /**
     * 인증 이메일 발송
     */
    fun sendVerificationEmail(email: String, code: String): Boolean {
        return try {
            val message = mailSender.createMimeMessage()
            val helper = MimeMessageHelper(message, true, "UTF-8")

            helper.setTo(email)
            helper.setSubject("[뉴스 브리핑] 이메일 인증 코드")
            helper.setText(buildEmailContent(code), true)

            mailSender.send(message)
            log.info("Verification email sent to: ${maskEmail(email)}")
            true
        } catch (e: Exception) {
            log.error("Failed to send verification email to: ${maskEmail(email)}", e)
            false
        }
    }

    /**
     * 이메일 본문 HTML 생성
     */
    private fun buildEmailContent(code: String): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: 'Noto Sans KR', sans-serif; background-color: #f5f5f5; margin: 0; padding: 20px; }
                    .container { max-width: 500px; margin: 0 auto; background: white; border-radius: 12px; padding: 40px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }
                    h1 { color: #333; margin-bottom: 20px; }
                    .code { font-size: 32px; font-weight: bold; color: #3b82f6; letter-spacing: 8px; padding: 20px; background: #f0f7ff; border-radius: 8px; text-align: center; margin: 30px 0; }
                    .info { color: #666; font-size: 14px; line-height: 1.6; }
                    .footer { margin-top: 30px; padding-top: 20px; border-top: 1px solid #eee; color: #999; font-size: 12px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <h1>이메일 인증</h1>
                    <p class="info">안녕하세요! 뉴스 브리핑 회원가입을 위한 인증 코드입니다.</p>
                    <div class="code">$code</div>
                    <p class="info">
                        위 코드를 회원가입 페이지에 입력해 주세요.<br>
                        이 코드는 <strong>${expireMinutes}분</strong> 동안 유효합니다.
                    </p>
                    <div class="footer">
                        본인이 요청하지 않은 경우, 이 이메일을 무시해 주세요.<br>
                        문의사항이 있으시면 고객센터로 연락해 주세요.
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    /**
     * 이메일 마스킹 (로깅용)
     */
    private fun maskEmail(email: String): String {
        val parts = email.split("@")
        if (parts.size != 2) return "***"
        val local = parts[0]
        val domain = parts[1]
        val maskedLocal = if (local.length > 2) {
            local.take(2) + "*".repeat(local.length - 2)
        } else {
            "*".repeat(local.length)
        }
        return "$maskedLocal@$domain"
    }

    /**
     * 비밀번호 재설정 이메일 발송
     */
    fun sendPasswordResetEmail(email: String, code: String): Boolean {
        return try {
            val message = mailSender.createMimeMessage()
            val helper = MimeMessageHelper(message, true, "UTF-8")

            helper.setTo(email)
            helper.setSubject("[SHIFT] 비밀번호 재설정 코드")
            helper.setText(buildPasswordResetEmailContent(code), true)

            mailSender.send(message)
            log.info("Password reset email sent to: ${maskEmail(email)}")
            true
        } catch (e: Exception) {
            log.error("Failed to send password reset email to: ${maskEmail(email)}", e)
            false
        }
    }

    /**
     * 비밀번호 재설정 이메일 본문 HTML 생성
     */
    private fun buildPasswordResetEmailContent(code: String): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: 'Noto Sans KR', sans-serif; background-color: #f5f5f5; margin: 0; padding: 20px; }
                    .container { max-width: 500px; margin: 0 auto; background: white; border-radius: 12px; padding: 40px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }
                    h1 { color: #333; margin-bottom: 20px; }
                    .code { font-size: 32px; font-weight: bold; color: #3b82f6; letter-spacing: 8px; padding: 20px; background: #f0f7ff; border-radius: 8px; text-align: center; margin: 30px 0; }
                    .info { color: #666; font-size: 14px; line-height: 1.6; }
                    .warning { color: #dc2626; font-size: 13px; margin-top: 20px; padding: 15px; background: #fef2f2; border-radius: 8px; }
                    .footer { margin-top: 30px; padding-top: 20px; border-top: 1px solid #eee; color: #999; font-size: 12px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <h1>비밀번호 재설정</h1>
                    <p class="info">비밀번호 재설정을 요청하셨습니다.<br>아래 인증 코드를 입력하여 새 비밀번호를 설정해 주세요.</p>
                    <div class="code">$code</div>
                    <p class="info">
                        이 코드는 <strong>${expireMinutes}분</strong> 동안 유효합니다.
                    </p>
                    <div class="warning">
                        ⚠️ 본인이 요청하지 않았다면 이 이메일을 무시하고, 계정 보안을 위해 비밀번호를 변경해 주세요.
                    </div>
                    <div class="footer">
                        SHIFT - 뉴스 브리핑 서비스<br>
                        문의: shift.context@gmail.com
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    /**
     * 인증 코드 만료 시간 (분) 반환
     */
    fun getExpireMinutes(): Int = expireMinutes
}

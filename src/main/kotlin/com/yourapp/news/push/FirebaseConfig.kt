package com.yourapp.news.push

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.io.FileInputStream

@ConfigurationProperties(prefix = "firebase")
data class FirebaseProperties(
    val credentialsFile: String = "",
    val enabled: Boolean = false
)

@Configuration
class FirebaseConfig(
    private val firebaseProperties: FirebaseProperties
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostConstruct
    fun initialize() {
        if (!firebaseProperties.enabled) {
            log.info("Firebase is disabled")
            return
        }

        if (firebaseProperties.credentialsFile.isBlank()) {
            log.warn("Firebase credentials file not configured")
            return
        }

        try {
            if (FirebaseApp.getApps().isEmpty()) {
                val serviceAccount = FileInputStream(firebaseProperties.credentialsFile)
                val options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build()

                FirebaseApp.initializeApp(options)
                log.info("Firebase initialized successfully")
            }
        } catch (e: Exception) {
            log.error("Failed to initialize Firebase: {}", e.message, e)
        }
    }

    @Bean
    fun firebaseMessaging(): FirebaseMessaging? {
        return if (firebaseProperties.enabled && FirebaseApp.getApps().isNotEmpty()) {
            FirebaseMessaging.getInstance()
        } else {
            null
        }
    }
}

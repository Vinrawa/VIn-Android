package com.vin.browser.engine

import android.net.http.SslCertificate
import com.vin.browser.data.SiteTrustInfo
import java.text.SimpleDateFormat
import java.util.Locale

object TrustEvaluator {

    private val trustedDomains = setOf(
        "google.com", "youtube.com", "github.com", "wikipedia.org",
        "reddit.com", "chess.com", "lichess.org", "stackoverflow.com",
        "microsoft.com", "apple.com", "amazon.com", "mozilla.org"
    )

    private val knownPhishingPatterns = setOf(
        "free-prize", "account-verify-login", "login-security-update",
        "paypal-secure-verify", "bank-login-update"
    )

    private val dateFormat = SimpleDateFormat("dd MMM yyyy 'at' HH:mm", Locale.getDefault())

    fun evaluate(url: String, certificate: SslCertificate? = null): SiteTrustInfo {
        val isHttps = url.startsWith("https://", ignoreCase = true)
        val domain = try {
            java.net.URI(url).host?.removePrefix("www.")?.lowercase() ?: "unknown"
        } catch (_: Exception) { "unknown" }

        // Strict domain verification (prevent evil-google.com spoofing)
        val isTrusted = trustedDomains.any { domain == it || domain.endsWith(".$it") }
        val isPhishing = knownPhishingPatterns.any { domain.contains(it) }

        val safetyRating = when {
            isPhishing -> "Dangerous - Suspected Phishing"
            !isHttps -> "Not Secure - Unencrypted HTTP"
            isTrusted -> "Verified Safe & Secure"
            else -> "Standard Security"
        }

        val phishingRisk = when {
            isPhishing -> "High"
            isTrusted -> "Very Low"
            isHttps -> "Low"
            else -> "Medium"
        }

        // Extract REAL SSL Certificate details if available
        var sslIssuer = if (isHttps) "Verified Certificate Authority" else "None"
        var sslIssuedTo = domain
        var validFrom = if (isHttps) "Active" else "N/A"
        var expireOn = if (isHttps) "Active" else "N/A"

        certificate?.let { cert ->
            val issuedBy = cert.issuedBy
            val issuedTo = cert.issuedTo

            val cName = issuedBy?.cName?.takeIf { it.isNotBlank() }
            val oName = issuedBy?.oName?.takeIf { it.isNotBlank() }
            sslIssuer = cName ?: oName ?: "Standard CA"

            val toCName = issuedTo?.cName?.takeIf { it.isNotBlank() }
            sslIssuedTo = toCName ?: domain

            cert.validNotBeforeDate?.let {
                validFrom = dateFormat.format(it)
            }
            cert.validNotAfterDate?.let {
                expireOn = dateFormat.format(it)
            }
        }

        return SiteTrustInfo(
            domain = domain,
            isSecure = isHttps,
            sslIssuer = sslIssuer,
            sslIssuedTo = sslIssuedTo,
            sslValidFrom = validFrom,
            sslExpireOn = expireOn,
            encryption = if (isHttps) "HTTPS (Encrypted)" else "Unencrypted (HTTP)",
            trackersBlocked = com.vin.browser.adblock.AdBlockEngine.instance.getSiteBlockedCount(domain),
            safetyRating = safetyRating,
            phishingRisk = phishingRisk
        )
    }
}

package br.com.estacaoqr.offline.security

import android.content.Context
import android.os.Build
import android.util.Base64
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

data class VerifiedReviewerQr(val reviewerId: String, val reviewerName: String)

class ReviewerQrSigner(private val context: Context) {
    fun create(reviewerId: String, reviewerName: String): String {
        val encodedName = URLEncoder.encode(reviewerName, StandardCharsets.UTF_8.name())
        val canonical = "$PREFIX|$reviewerId|$encodedName"
        val signature = sign(canonical)
        return "$canonical|${Base64.encodeToString(signature, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)}"
    }

    fun verify(payload: String): VerifiedReviewerQr? {
        val parts = payload.split('|')
        if (parts.size != 6 || parts.take(3).joinToString("|") != PREFIX) return null
        val canonical = parts.take(5).joinToString("|")
        val supplied = runCatching {
            Base64.decode(parts[5], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        }.getOrNull() ?: return null
        val validPortable = MessageDigest.isEqual(sign(canonical), supplied)
        val validLegacy = if (validPortable) false else getExistingLegacyKey()?.let { key ->
            MessageDigest.isEqual(signWith(canonical, key), supplied)
        } ?: false
        if (!validPortable && !validLegacy) return null
        val name = runCatching { URLDecoder.decode(parts[4], StandardCharsets.UTF_8.name()) }.getOrNull() ?: return null
        return VerifiedReviewerQr(parts[3], name)
    }

    private fun sign(value: String): ByteArray {
        return signWith(value, getOrCreatePortableKey())
    }

    private fun signWith(value: String, key: SecretKey): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(key)
        return mac.doFinal(value.toByteArray(StandardCharsets.UTF_8))
    }

    fun exportPortableKey(): String = Base64.encodeToString(
        getOrCreatePortableKey().encoded,
        Base64.NO_WRAP
    )

    fun importPortableKey(encodedKey: String) {
        val bytes = runCatching { Base64.decode(encodedKey, Base64.NO_WRAP) }
            .getOrElse { throw IllegalArgumentException("Chave de assinatura inválida.") }
        require(bytes.size == 32) { "Chave de assinatura inválida." }
        context.getSharedPreferences(PORTABLE_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(PORTABLE_KEY, Base64.encodeToString(bytes, Base64.NO_WRAP))
            .commit()
    }

    private fun getOrCreatePortableKey(): SecretKey {
        val prefs = context.getSharedPreferences(PORTABLE_PREFS, Context.MODE_PRIVATE)
        val stored = prefs.getString(PORTABLE_KEY, null)
        val bytes = if (stored != null) {
            Base64.decode(stored, Base64.NO_WRAP)
        } else {
            ByteArray(32).also { key ->
                java.security.SecureRandom().nextBytes(key)
                prefs.edit().putString(PORTABLE_KEY, Base64.encodeToString(key, Base64.NO_WRAP)).commit()
            }
        }
        return SecretKeySpec(bytes, "HmacSHA256")
    }

    private fun getExistingLegacyKey(): SecretKey? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            runCatching {
                val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                keyStore.getKey(KEY_ALIAS, null) as? SecretKey
            }.getOrNull()
        } else {
            val stored = context.getSharedPreferences("qr_signing_legacy", Context.MODE_PRIVATE)
                .getString("hmac_key", null) ?: return null
            SecretKeySpec(Base64.decode(stored, Base64.NO_WRAP), "HmacSHA256")
        }
    }

    companion object {
        private const val PREFIX = "EQR|REV|1"
        private const val KEY_ALIAS = "estacao_qr_reviewer_hmac_v1"
        private const val PORTABLE_PREFS = "qr_signing_portable"
        private const val PORTABLE_KEY = "hmac_key"
    }
}

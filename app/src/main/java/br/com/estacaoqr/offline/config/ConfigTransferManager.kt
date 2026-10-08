package br.com.estacaoqr.offline.config

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.room.withTransaction
import br.com.estacaoqr.offline.BuildConfig
import br.com.estacaoqr.offline.data.AppDatabase
import br.com.estacaoqr.offline.data.PieceEntity
import br.com.estacaoqr.offline.data.ReviewerEntity
import br.com.estacaoqr.offline.security.ReviewerQrSigner
import br.com.estacaoqr.offline.settings.AppSettings
import br.com.estacaoqr.offline.settings.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class ConfigTransferSummary(
    val pieces: Int,
    val reviewers: Int,
    val replaced: Boolean
)

class ConfigTransferManager(private val context: Context) {
    private val database = AppDatabase.get(context)
    private val settings = AppSettings(context)
    private val signer = ReviewerQrSigner(context)

    suspend fun exportTo(uri: Uri, password: String): ConfigTransferSummary = withContext(Dispatchers.IO) {
        requirePassword(password)
        val pieces = database.dao().getAllPieces()
        val reviewers = database.dao().getAllReviewers()
        val payload = JSONObject().apply {
            put("schema", SCHEMA_VERSION)
            put("appVersion", BuildConfig.VERSION_NAME)
            put("exportedAt", System.currentTimeMillis())
            put("qrSigningKey", signer.exportPortableKey())
            put("pieces", JSONArray().apply {
                pieces.forEach { piece ->
                    put(JSONObject().apply {
                        put("code", piece.code)
                        put("description", piece.description)
                        put("active", piece.active)
                        put("updatedAt", piece.updatedAt)
                    })
                }
            })
            put("reviewers", JSONArray().apply {
                reviewers.forEach { reviewer ->
                    put(JSONObject().apply {
                        put("id", reviewer.id)
                        put("name", reviewer.name)
                        put("active", reviewer.active)
                        put("updatedAt", reviewer.updatedAt)
                    })
                }
            })
            put("settings", JSONObject().apply {
                put("kioskEnabled", settings.kioskEnabled)
                put("soundEnabled", settings.soundEnabled)
            })
        }

        val envelope = encrypt(payload.toString(), password)
        val stream = requireNotNull(context.contentResolver.openOutputStream(uri, "w")) {
            "Não foi possível abrir o arquivo escolhido."
        }
        stream.bufferedWriter(StandardCharsets.UTF_8).use { it.write(envelope.toString()) }
        ConfigTransferSummary(pieces.size, reviewers.size, replaced = false)
    }

    suspend fun importFrom(
        uri: Uri,
        password: String,
        replaceExisting: Boolean
    ): ConfigTransferSummary = withContext(Dispatchers.IO) {
        requirePassword(password)
        val stream = requireNotNull(context.contentResolver.openInputStream(uri)) {
            "Não foi possível abrir o arquivo selecionado."
        }
        val encryptedText = stream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        require(encryptedText.length <= MAX_CONFIG_CHARS) { "Arquivo de configuração muito grande." }
        val payload = JSONObject(decrypt(JSONObject(encryptedText), password))
        require(payload.optInt("schema") == SCHEMA_VERSION) { "Versão de configuração não suportada." }

        // Arquivo "somente peças": sem chave de assinatura. Nesse caso a chave atual dos
        // cartões de revisora e as revisoras cadastradas são preservadas.
        val signingKey = payload.optString("qrSigningKey", "").trim()
        val piecesOnly = signingKey.isEmpty()
        if (!piecesOnly) {
            val signingBytes = Base64.decode(signingKey, Base64.NO_WRAP)
            require(signingBytes.size == 32) { "Chave de assinatura inválida." }
        }

        val pieces = parsePieces(payload.getJSONArray("pieces"))
        val reviewers = if (piecesOnly) emptyList() else parseReviewers(payload.optJSONArray("reviewers") ?: JSONArray())
        val importedSettings = payload.optJSONObject("settings")

        database.withTransaction {
            if (replaceExisting) {
                database.dao().clearPieces()
                if (!piecesOnly) database.dao().clearReviewers()
            }
            pieces.forEach { database.dao().savePiece(it) }
            reviewers.forEach { database.dao().saveReviewer(it) }
        }
        if (!piecesOnly) {
            signer.importPortableKey(signingKey)
            SessionStore(context).clear()
        }
        importedSettings?.let {
            settings.kioskEnabled = it.optBoolean("kioskEnabled", settings.kioskEnabled)
            settings.soundEnabled = it.optBoolean("soundEnabled", settings.soundEnabled)
        }
        ConfigTransferSummary(pieces.size, reviewers.size, replaceExisting)
    }

    private fun parsePieces(array: JSONArray): List<PieceEntity> {
        val items = mutableListOf<PieceEntity>()
        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            val code = item.getString("code").trim().uppercase(Locale.ROOT)
            val description = item.getString("description").trim()
            require(code.isNotEmpty() && code.length <= 256) { "Código de peça inválido." }
            require(description.isNotEmpty() && description.length <= 1000) { "Descrição de peça inválida." }
            items += PieceEntity(
                code = code,
                description = description,
                active = item.optBoolean("active", true),
                updatedAt = item.optLong("updatedAt", System.currentTimeMillis())
            )
        }
        return items.distinctBy { it.code }
    }

    private fun parseReviewers(array: JSONArray): List<ReviewerEntity> {
        val items = mutableListOf<ReviewerEntity>()
        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            val id = item.getString("id").trim()
            val name = item.getString("name").trim()
            require(id.isNotEmpty() && id.length <= 100) { "Identificador de revisora inválido." }
            require(name.isNotEmpty() && name.length <= 200) { "Nome de revisora inválido." }
            items += ReviewerEntity(
                id = id,
                name = name,
                active = item.optBoolean("active", true),
                updatedAt = item.optLong("updatedAt", System.currentTimeMillis())
            )
        }
        return items.distinctBy { it.id }
    }

    private fun encrypt(plainText: String, password: String): JSONObject {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt, ITERATIONS), GCMParameterSpec(128, iv))
        cipher.updateAAD(AAD.toByteArray(StandardCharsets.UTF_8))
        val encrypted = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
        return JSONObject().apply {
            put("format", FILE_FORMAT)
            put("version", FILE_VERSION)
            put("iterations", ITERATIONS)
            put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
            put("data", Base64.encodeToString(encrypted, Base64.NO_WRAP))
        }
    }

    private fun decrypt(envelope: JSONObject, password: String): String {
        require(envelope.optString("format") == FILE_FORMAT) { "Arquivo de configuração inválido." }
        require(envelope.optInt("version") == FILE_VERSION) { "Versão de arquivo não suportada." }
        val iterations = envelope.optInt("iterations", ITERATIONS)
        require(iterations in 50_000..1_000_000) { "Parâmetros de segurança inválidos." }
        val salt = Base64.decode(envelope.getString("salt"), Base64.NO_WRAP)
        val iv = Base64.decode(envelope.getString("iv"), Base64.NO_WRAP)
        val data = Base64.decode(envelope.getString("data"), Base64.NO_WRAP)
        require(salt.size == 16 && iv.size == 12) { "Arquivo de configuração inválido." }
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt, iterations), GCMParameterSpec(128, iv))
            cipher.updateAAD(AAD.toByteArray(StandardCharsets.UTF_8))
            String(cipher.doFinal(data), StandardCharsets.UTF_8)
        } catch (_: Exception) {
            throw IllegalArgumentException("Senha incorreta ou arquivo danificado.")
        }
    }

    private fun deriveKey(password: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
        return try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded
            SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun requirePassword(password: String) {
        require(password.length >= 6) { "A senha do arquivo precisa ter pelo menos 6 caracteres." }
    }

    companion object {
        private const val FILE_FORMAT = "ESTACAO_QR_CONFIG"
        private const val FILE_VERSION = 1
        private const val SCHEMA_VERSION = 1
        private const val ITERATIONS = 150_000
        private const val MAX_CONFIG_CHARS = 8_000_000
        private const val AAD = "ESTACAO_QR_CONFIG_V1"
    }
}

package br.com.estacaoqr.offline.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import br.com.estacaoqr.offline.R
import br.com.estacaoqr.offline.data.AppDatabase
import br.com.estacaoqr.offline.data.PieceEntity
import br.com.estacaoqr.offline.data.ReviewerEntity
import br.com.estacaoqr.offline.data.ScanLogEntity
import br.com.estacaoqr.offline.databinding.ActivityMainBinding
import br.com.estacaoqr.offline.kiosk.KioskController
import br.com.estacaoqr.offline.security.AdminSecurity
import br.com.estacaoqr.offline.security.ReviewerQrSigner
import br.com.estacaoqr.offline.settings.AppSettings
import br.com.estacaoqr.offline.settings.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val database by lazy { AppDatabase.get(this) }
    private val session by lazy { SessionStore(this) }
    private val settings by lazy { AppSettings(this) }
    private val signer by lazy { ReviewerQrSigner(this) }
    private val dimmer by lazy { ScreenDimmer(this) }
    private var processing = false
    private var lastValue = ""
    private var lastReadAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        KioskController.hideSystemUi(this)

        binding.cameraView.onQrRead = ::onQrRead
        binding.cameraView.onCameraError = { showNeutral("ERRO DE CÂMERA", it) }
        binding.cameraView.onCameraSwitched = { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }
        binding.adminButton.setOnClickListener {
            startActivity(Intent(this, AdminAuthActivity::class.java))
        }
        binding.endSessionButton.setOnClickListener { requestPinToEndSession() }

        updateSessionHeader()
        ensureCameraPermission()
    }

    override fun onResume() {
        super.onResume()
        KioskController.hideSystemUi(this)
        dimmer.setEnabled(settings.screenAlwaysOn)
        if (settings.kioskEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            KioskController.start(this)
        }
        updateSessionHeader()
    }

    override fun onPause() {
        dimmer.pause()
        super.onPause()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (dimmer.handleTouch(ev)) return true
        return super.dispatchTouchEvent(ev)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) KioskController.hideSystemUi(this)
    }

    override fun onBackPressed() {
        if (!settings.kioskEnabled) super.onBackPressed()
    }

    private fun ensureCameraPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        ) {
            binding.cameraView.visibility = View.VISIBLE
        } else {
            binding.cameraView.visibility = View.INVISIBLE
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_REQUEST)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_REQUEST && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            binding.cameraView.visibility = View.VISIBLE
        } else if (requestCode == CAMERA_REQUEST) {
            showNeutral("CÂMERA NECESSÁRIA", "Autorize a câmera nas configurações do aparelho.")
        }
    }

    private fun onQrRead(raw: String) {
        dimmer.onActivity()
        val now = System.currentTimeMillis()
        if (processing || (raw == lastValue && now - lastReadAt < DEBOUNCE_MS)) return
        processing = true
        lastValue = raw
        lastReadAt = now
        lifecycleScope.launch {
            runCatching { processQr(raw) }
                .onFailure { showNeutral("ERRO NA LEITURA", it.message ?: "Falha inesperada") }
            processing = false
        }
    }

    private suspend fun processQr(raw: String) {
        val activeReviewer = session.reviewer
        if (activeReviewer == null) {
            processReviewerQr(raw)
            return
        }

        if (signer.verify(raw) != null) {
            showScannerInstructions(
                "LEIA O QR DA PEÇA",
                "A estação já está liberada por ${activeReviewer.name}.\n\n" +
                    "Aponte a câmera para o QR com o código da peça."
            )
            return
        }

        val readCode = normalizePieceCode(raw)
        val readPiece = withContext(Dispatchers.IO) { database.dao().findActivePiece(readCode) }
        val expected = session.expectedPiece

        if (expected == null) {
            if (readPiece == null) {
                saveLog("LEITURA_PECA", "NAO_CADASTRADA", activeReviewer, null, readCode, null)
                showWrong("CÓDIGO NÃO CADASTRADO", "Lido: $readCode\nCadastre a peça na área administrativa.")
                return
            }
            session.setExpected(readPiece)
            saveLog("DEFINICAO_PECA", "LIBERADA", activeReviewer, readPiece, readPiece.code, readPiece.description)
            updateSessionHeader()
            showOk(readPiece, "Primeira peça da sessão. Este passa a ser o código esperado.")
            return
        }

        if (readCode.equals(expected.code, ignoreCase = true)) {
            saveLog("LEITURA_PECA", "LIBERADA", activeReviewer, expected, readCode, readPiece?.description)
            showOk(expected, "Código correto para esta sessão.")
        } else {
            val readText = readPiece?.let { "${it.code} — ${it.description}" } ?: "$readCode — NÃO CADASTRADA"
            saveLog("LEITURA_PECA", "DIFERENTE", activeReviewer, expected, readCode, readPiece?.description)
            showWrong(
                "PEÇA DIFERENTE ✖",
                "LIDA: $readText\n\nDEVERIA SER: ${expected.code} — ${expected.description}"
            )
        }
    }

    private suspend fun processReviewerQr(raw: String) {
        val verified = signer.verify(raw)
        if (verified == null) {
            saveLog("LIBERACAO", "QR_INVALIDO", null, null, null, null)
            showWrong("REVISORA NÃO AUTORIZADA ✖", "QR inválido ou sem assinatura deste aparelho.")
            return
        }
        val reviewer = withContext(Dispatchers.IO) { database.dao().findActiveReviewer(verified.reviewerId) }
        if (reviewer == null) {
            saveLog("LIBERACAO", "REVISORA_INATIVA", null, null, null, null)
            showWrong("REVISORA INATIVA ✖", "A revisora não está ativa no cadastro local.")
            return
        }
        session.unlock(reviewer)
        saveLog("LIBERACAO", "AUTORIZADA", reviewer, null, null, null)
        updateSessionHeader()
        showNeutral("ESTAÇÃO LIBERADA", "Revisora: ${reviewer.name}\nAgora leia a primeira peça.")
    }

    private suspend fun saveLog(
        event: String,
        result: String,
        reviewer: ReviewerEntity?,
        expected: PieceEntity?,
        readCode: String?,
        readDescription: String?
    ) = withContext(Dispatchers.IO) {
        database.dao().insertLog(
            ScanLogEntity(
                timestamp = System.currentTimeMillis(),
                event = event,
                result = result,
                reviewerId = reviewer?.id,
                reviewerName = reviewer?.name,
                expectedCode = expected?.code,
                expectedDescription = expected?.description,
                readCode = readCode,
                readDescription = readDescription
            )
        )
    }

    private fun updateSessionHeader() {
        val reviewer = session.reviewer
        val expected = session.expectedPiece
        when {
            reviewer == null -> {
                binding.stationStatus.text = "ESTAÇÃO BLOQUEADA"
                binding.instruction.text = "Leia o QR assinado de uma revisora"
                binding.endSessionButton.isEnabled = false
                showScannerInstructions(
                    "INSTRUÇÕES PARA LEITURA",
                    "1. Aponte a câmera para o QR assinado da revisora.\n\n" +
                        "2. Aguarde a liberação da estação."
                )
            }
            expected == null -> {
                binding.stationStatus.text = "LIBERADA POR ${reviewer.name.uppercase(Locale.getDefault())}"
                binding.instruction.text = "Leia a primeira peça para definir a sessão"
                binding.endSessionButton.isEnabled = true
                showScannerInstructions(
                    "LEIA A PRIMEIRA PEÇA",
                    "Estação liberada por ${reviewer.name}.\n\n" +
                        "Leia o QR da PRIMEIRA PEÇA para definir o código esperado da sessão."
                )
            }
            else -> {
                binding.stationStatus.text = "ESPERADO: ${expected.code}"
                binding.instruction.text = "${expected.description} • Revisora: ${reviewer.name}"
                binding.endSessionButton.isEnabled = true
                showScannerInstructions(
                    "CONTINUE A LEITURA",
                    "PEÇA ESPERADA:\n${expected.code} — ${expected.description}\n\n" +
                        "Leia somente peças com este mesmo código."
                )
            }
        }
    }

    private fun showOk(piece: PieceEntity, note: String) {
        showResult(
            ScanResultActivity.KIND_OK,
            "PEÇA LIBERADA ✔",
            "${piece.code}\n${piece.description}\n\n$note",
            RESULT_OK_DURATION_MS
        )
    }

    private fun showWrong(title: String, details: String) {
        showResult(ScanResultActivity.KIND_ERROR, title, details, RESULT_ERROR_DURATION_MS)
    }

    private fun showNeutral(title: String, details: String) {
        showResult(ScanResultActivity.KIND_INFO, title, details, RESULT_INFO_DURATION_MS)
    }

    private fun showScannerInstructions(title: String, details: String) {
        binding.resultPanel.setBackgroundResource(R.drawable.panel_blue)
        binding.resultTitle.text = title
        binding.resultDetails.text = details
    }

    private fun showResult(kind: String, title: String, details: String, durationMs: Long) {
        startActivity(ScanResultActivity.createIntent(this, kind, title, details, durationMs))
        overridePendingTransition(0, 0)
    }

    private fun requestPinToEndSession() {
        val input = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "PIN administrativo"
            setPadding(40, 20, 40, 20)
        }
        AlertDialog.Builder(this)
            .setTitle("Encerrar sessão")
            .setMessage("Informe o PIN para exigir uma nova liberação de revisora.")
            .setView(input)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Encerrar") { _, _ ->
                if (AdminSecurity(this).verifyPin(input.text.toString())) {
                    val reviewer = session.reviewer
                    session.clear()
                    lifecycleScope.launch {
                        saveLog("FIM_SESSAO", "ENCERRADA", reviewer, null, null, null)
                    }
                    updateSessionHeader()
                    showNeutral("SESSÃO ENCERRADA", "Leia o QR assinado de uma revisora.")
                } else {
                    Toast.makeText(this, "PIN incorreto", Toast.LENGTH_LONG).show()
                }
            }.show()
    }

    private fun normalizePieceCode(raw: String): String =
        raw.removePrefix("EQR|PART|").trim().uppercase(Locale.ROOT)

    companion object {
        private const val CAMERA_REQUEST = 900
        private const val DEBOUNCE_MS = 1800L
        private const val RESULT_OK_DURATION_MS = 2200L
        private const val RESULT_INFO_DURATION_MS = 2400L
        private const val RESULT_ERROR_DURATION_MS = 3800L
    }
}

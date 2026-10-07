package br.com.estacaoqr.offline.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import br.com.estacaoqr.offline.data.ReviewerEntity
import br.com.estacaoqr.offline.databinding.ActivityReviewerQrBinding
import br.com.estacaoqr.offline.pdf.ReviewerCardPdf
import br.com.estacaoqr.offline.security.ReviewerQrSigner
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReviewerQrActivity : AppCompatActivity() {
    private lateinit var binding: ActivityReviewerQrBinding
    private var reviewer: ReviewerEntity? = null
    private val saveCardPdf = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val selectedReviewer = reviewer
        if (uri == null || selectedReviewer == null) return@registerForActivityResult
        lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching {
                ReviewerCardPdf.writeSingle(this@ReviewerQrActivity, uri, selectedReviewer)
            }
            withContext(Dispatchers.Main) {
                result.onSuccess { toast("Cartão PDF salvo.") }
                    .onFailure { toast("Não foi possível salvar o cartão: ${it.message}") }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReviewerQrBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val id = intent.getStringExtra(EXTRA_ID).orEmpty()
        val name = intent.getStringExtra(EXTRA_NAME).orEmpty()
        if (id.isEmpty() || name.isEmpty()) {
            finish()
            return
        }
        reviewer = ReviewerEntity(id = id, name = name)
        binding.reviewerName.text = name
        binding.reviewerId.text = "ID: $id"
        val payload = ReviewerQrSigner(this).create(id, name)
        binding.qrImage.setImageBitmap(createQrBitmap(payload, 800))
        binding.downloadCardButton.setOnClickListener {
            val safeName = name.replace(Regex("[^A-Za-z0-9_-]+"), "-").trim('-')
            saveCardPdf.launch("cartao-revisora-${safeName.ifEmpty { id.takeLast(8) }}.pdf")
        }
        binding.backButton.setOnClickListener { finish() }
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    private fun createQrBitmap(value: String, size: Int): Bitmap {
        val matrix = QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, size, size)
        val pixels = IntArray(size * size)
        for (y in 0 until size) for (x in 0 until size) {
            pixels[y * size + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
        }
        return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, size, 0, 0, size, size)
        }
    }

    companion object {
        const val EXTRA_ID = "reviewer_id"
        const val EXTRA_NAME = "reviewer_name"
    }
}

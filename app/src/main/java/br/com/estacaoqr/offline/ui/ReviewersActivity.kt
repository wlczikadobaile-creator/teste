package br.com.estacaoqr.offline.ui

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import br.com.estacaoqr.offline.data.AppDatabase
import br.com.estacaoqr.offline.data.ReviewerEntity
import br.com.estacaoqr.offline.databinding.ActivityReviewersBinding
import br.com.estacaoqr.offline.pdf.ReviewerCardPdf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReviewersActivity : AppCompatActivity() {
    private lateinit var binding: ActivityReviewersBinding
    private val database by lazy { AppDatabase.get(this) }
    private val adapter = ReviewerAdapter(::showQr, ::showReviewerDialog, ::confirmDelete)
    private var pendingPdfReviewers = emptyList<ReviewerEntity>()
    private val saveAllPdf = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri == null || pendingPdfReviewers.isEmpty()) return@registerForActivityResult
        val reviewers = pendingPdfReviewers
        lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching { ReviewerCardPdf.writeAllA4(this@ReviewersActivity, uri, reviewers) }
            withContext(Dispatchers.Main) {
                result.onSuccess { pages ->
                    toast("PDF salvo: ${reviewers.size} cartão(ões) em $pages página(s) A4.")
                }.onFailure { toast("Não foi possível salvar o PDF: ${it.message}") }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReviewersBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.reviewersList.layoutManager = LinearLayoutManager(this)
        binding.reviewersList.adapter = adapter
        binding.addReviewerButton.setOnClickListener { showReviewerDialog(null) }
        binding.generateAllPdfButton.setOnClickListener { generateAllPdf() }
        binding.backButton.setOnClickListener { finish() }
        refresh()
    }

    private fun generateAllPdf() = lifecycleScope.launch {
        val activeReviewers = withContext(Dispatchers.IO) {
            database.dao().getAllReviewers().filter { it.active }
        }
        if (activeReviewers.isEmpty()) {
            toast("Não há revisoras ativas para gerar cartões.")
            return@launch
        }
        pendingPdfReviewers = activeReviewers
        val date = SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(Date())
        saveAllPdf.launch("cartoes-revisoras-$date.pdf")
    }

    private fun refresh() = lifecycleScope.launch {
        val items = withContext(Dispatchers.IO) { database.dao().getAllReviewers() }
        adapter.submit(items)
        binding.emptyReviewers.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showReviewerDialog(existing: ReviewerEntity?) {
        val name = EditText(this).apply {
            hint = "Nome da revisora"
            setText(existing?.name.orEmpty())
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        }
        val active = CheckBox(this).apply {
            text = "Revisora ativa"
            isChecked = existing?.active ?: true
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), 0)
            addView(name, LinearLayout.LayoutParams(-1, dp(62)))
            addView(active, LinearLayout.LayoutParams(-1, dp(54)))
        }
        AlertDialog.Builder(this)
            .setTitle(if (existing == null) "Nova revisora" else "Editar revisora")
            .setView(content)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Salvar") { _, _ ->
                val text = name.text.toString().trim()
                if (text.isEmpty()) toast("Informe o nome da revisora.") else {
                    val reviewer = ReviewerEntity(existing?.id ?: UUID.randomUUID().toString(), text, active.isChecked)
                    lifecycleScope.launch(Dispatchers.IO) {
                        database.dao().saveReviewer(reviewer)
                        withContext(Dispatchers.Main) { refresh() }
                    }
                }
            }.show()
    }

    private fun showQr(reviewer: ReviewerEntity) {
        startActivity(Intent(this, ReviewerQrActivity::class.java).apply {
            putExtra(ReviewerQrActivity.EXTRA_ID, reviewer.id)
            putExtra(ReviewerQrActivity.EXTRA_NAME, reviewer.name)
        })
    }

    private fun confirmDelete(reviewer: ReviewerEntity) {
        AlertDialog.Builder(this)
            .setTitle("Excluir ${reviewer.name}?")
            .setMessage("O QR desta revisora deixará de liberar a estação. Os logs antigos serão preservados.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Excluir") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    database.dao().deleteReviewer(reviewer)
                    withContext(Dispatchers.Main) { refresh() }
                }
            }.show()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun toast(value: String) = Toast.makeText(this, value, Toast.LENGTH_LONG).show()
}

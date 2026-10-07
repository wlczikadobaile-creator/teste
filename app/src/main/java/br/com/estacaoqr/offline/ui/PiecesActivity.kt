package br.com.estacaoqr.offline.ui

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
import br.com.estacaoqr.offline.data.PieceEntity
import br.com.estacaoqr.offline.databinding.ActivityPiecesBinding
import br.com.estacaoqr.offline.pdf.PiecePlaquePdf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.text.SimpleDateFormat
import java.util.Date

class PiecesActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPiecesBinding
    private val database by lazy { AppDatabase.get(this) }
    private val adapter = PieceAdapter(::downloadPlaque, ::showPieceDialog, ::confirmDelete)
    private var currentItems = emptyList<PieceEntity>()
    private var pendingSinglePiece: PieceEntity? = null
    private var pendingAllPieces = emptyList<PieceEntity>()
    private val saveSinglePdf = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val piece = pendingSinglePiece
        if (uri == null || piece == null) return@registerForActivityResult
        lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching { PiecePlaquePdf.writeSingle(this@PiecesActivity, uri, piece) }
            withContext(Dispatchers.Main) {
                result.onSuccess { toast("Plaquinha PDF salva.") }
                    .onFailure { toast("Não foi possível salvar a plaquinha: ${it.message}") }
            }
        }
    }
    private val saveAllPdf = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri == null || pendingAllPieces.isEmpty()) return@registerForActivityResult
        val pieces = pendingAllPieces
        lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching { PiecePlaquePdf.writeAllA4(this@PiecesActivity, uri, pieces) }
            withContext(Dispatchers.Main) {
                result.onSuccess { pages ->
                    toast("PDF salvo: ${pieces.size} plaquinha(s) em $pages página(s) A4.")
                }.onFailure { toast("Não foi possível salvar o PDF: ${it.message}") }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPiecesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.piecesList.layoutManager = LinearLayoutManager(this)
        binding.piecesList.adapter = adapter
        binding.addPieceButton.setOnClickListener { showPieceDialog(null) }
        binding.generateAllPlaquesButton.setOnClickListener { generateAllPlaques() }
        binding.backButton.setOnClickListener { finish() }
        refresh()
    }

    private fun downloadPlaque(piece: PieceEntity) {
        pendingSinglePiece = piece
        val safeCode = piece.code.replace(Regex("[^A-Za-z0-9_-]+"), "-").trim('-')
        saveSinglePdf.launch("plaquinha-${safeCode.ifEmpty { "peca" }}.pdf")
    }

    private fun generateAllPlaques() {
        val activePieces = currentItems.filter { it.active }
        if (activePieces.isEmpty()) {
            toast("Não há peças ativas para gerar plaquinhas.")
            return
        }
        pendingAllPieces = activePieces
        val date = SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(Date())
        saveAllPdf.launch("plaquinhas-pecas-$date.pdf")
    }

    private fun refresh() = lifecycleScope.launch {
        currentItems = withContext(Dispatchers.IO) { database.dao().getAllPieces() }
        adapter.submit(currentItems)
        binding.emptyPieces.visibility = if (currentItems.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showPieceDialog(existing: PieceEntity?) {
        val code = EditText(this).apply {
            hint = "Código da peça"
            setText(existing?.code.orEmpty())
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
        }
        val description = EditText(this).apply {
            hint = "Descrição"
            setText(existing?.description.orEmpty())
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        val active = CheckBox(this).apply {
            text = "Peça ativa"
            isChecked = existing?.active ?: true
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), 0)
            addView(code, LinearLayout.LayoutParams(-1, dp(60)))
            addView(description, LinearLayout.LayoutParams(-1, dp(60)))
            addView(active, LinearLayout.LayoutParams(-1, dp(54)))
        }
        AlertDialog.Builder(this)
            .setTitle(if (existing == null) "Nova peça" else "Editar peça")
            .setView(content)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Salvar") { _, _ ->
                val normalized = code.text.toString().trim().uppercase(Locale.ROOT)
                val text = description.text.toString().trim()
                when {
                    normalized.isEmpty() || text.isEmpty() -> toast("Preencha código e descrição.")
                    currentItems.any { it.code.equals(normalized, true) && it.code != existing?.code } -> toast("Já existe uma peça com esse código.")
                    else -> lifecycleScope.launch(Dispatchers.IO) {
                        if (existing != null && existing.code != normalized) database.dao().deletePiece(existing)
                        database.dao().savePiece(PieceEntity(normalized, text, active.isChecked))
                        withContext(Dispatchers.Main) { refresh() }
                    }
                }
            }.show()
    }

    private fun confirmDelete(piece: PieceEntity) {
        AlertDialog.Builder(this)
            .setTitle("Excluir ${piece.code}?")
            .setMessage("Os logs antigos serão preservados.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Excluir") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    database.dao().deletePiece(piece)
                    withContext(Dispatchers.Main) { refresh() }
                }
            }.show()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun toast(value: String) = Toast.makeText(this, value, Toast.LENGTH_LONG).show()
}

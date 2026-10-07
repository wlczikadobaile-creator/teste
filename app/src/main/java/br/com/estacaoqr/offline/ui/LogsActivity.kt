package br.com.estacaoqr.offline.ui

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import br.com.estacaoqr.offline.data.AppDatabase
import br.com.estacaoqr.offline.databinding.ActivityLogsBinding
import br.com.estacaoqr.offline.export.LogExportManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LogsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLogsBinding
    private val database by lazy { AppDatabase.get(this) }
    private val logExporter by lazy { LogExportManager(this) }
    private val adapter = LogAdapter()
    private val createCsvFile = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let(::exportLogs)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLogsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.logsList.layoutManager = LinearLayoutManager(this)
        binding.logsList.adapter = adapter
        binding.exportLogsButton.setOnClickListener {
            createCsvFile.launch(logExporter.suggestedFileName())
        }
        binding.clearLogsButton.setOnClickListener { confirmClear() }
        binding.backButton.setOnClickListener { finish() }
        refresh()
    }

    private fun refresh() = lifecycleScope.launch {
        val items = withContext(Dispatchers.IO) { database.dao().getRecentLogs() }
        adapter.submit(items)
        binding.emptyLogs.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        binding.exportLogsButton.isEnabled = items.isNotEmpty()
    }

    private fun exportLogs(uri: Uri) = lifecycleScope.launch {
        binding.exportLogsButton.isEnabled = false
        runCatching { logExporter.exportTo(uri) }
            .onSuccess { count ->
                Toast.makeText(
                    this@LogsActivity,
                    "$count log(s) exportado(s) com sucesso.",
                    Toast.LENGTH_LONG
                ).show()
            }
            .onFailure {
                Toast.makeText(
                    this@LogsActivity,
                    "Falha ao exportar logs: ${it.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        binding.exportLogsButton.isEnabled = true
    }

    private fun confirmClear() {
        AlertDialog.Builder(this)
            .setTitle("Apagar todos os logs?")
            .setMessage("Esta ação não pode ser desfeita.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Apagar") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    database.dao().clearLogs()
                    withContext(Dispatchers.Main) { refresh() }
                }
            }.show()
    }
}

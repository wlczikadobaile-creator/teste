package br.com.estacaoqr.offline.export

import android.content.Context
import android.net.Uri
import br.com.estacaoqr.offline.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LogExportManager(context: Context) {
    private val appContext = context.applicationContext
    private val database by lazy { AppDatabase.get(appContext) }

    fun suggestedFileName(): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())
        return "logs-estacao-$stamp.csv"
    }

    suspend fun exportTo(uri: Uri): Int = withContext(Dispatchers.IO) {
        val logs = database.dao().getAllLogs()
        val output = appContext.contentResolver.openOutputStream(uri)
            ?: error("Não foi possível abrir o arquivo selecionado.")
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("pt", "BR"))

        OutputStreamWriter(output, Charsets.UTF_8).use { writer ->
            writer.write("\uFEFF")
            writer.write(
                listOf(
                    "Data e hora",
                    "Evento",
                    "Resultado",
                    "ID revisora",
                    "Revisora",
                    "Código esperado",
                    "Descrição esperada",
                    "Código lido",
                    "Descrição lida",
                    "Detalhes"
                ).joinToString(";") { csv(it) }
            )
            writer.write("\r\n")

            logs.forEach { log ->
                writer.write(
                    listOf(
                        dateFormat.format(Date(log.timestamp)),
                        log.event,
                        log.result,
                        log.reviewerId,
                        log.reviewerName,
                        log.expectedCode,
                        log.expectedDescription,
                        log.readCode,
                        log.readDescription,
                        log.details
                    ).joinToString(";") { csv(it) }
                )
                writer.write("\r\n")
            }
        }
        logs.size
    }

    private fun csv(value: Any?): String {
        val normalized = value?.toString().orEmpty()
            .replace("\r", " ")
            .replace("\n", " ")
            .replace("\"", "\"\"")
        return "\"$normalized\""
    }
}

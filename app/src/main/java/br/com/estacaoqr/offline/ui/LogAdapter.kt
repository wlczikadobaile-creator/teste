package br.com.estacaoqr.offline.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import br.com.estacaoqr.offline.data.ScanLogEntity
import br.com.estacaoqr.offline.databinding.ItemLogBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LogAdapter : RecyclerView.Adapter<LogAdapter.Holder>() {
    private var items = emptyList<ScanLogEntity>()
    private val formatter = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())

    fun submit(newItems: List<ScanLogEntity>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder = Holder(
        ItemLogBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )
    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])
    override fun getItemCount(): Int = items.size

    inner class Holder(private val binding: ItemLogBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(log: ScanLogEntity) {
            binding.logTitle.text = "${label(log.event)} — ${label(log.result)}"
            binding.logTitle.setTextColor(
                if (log.result in setOf("DIFERENTE", "QR_INVALIDO", "REVISORA_INATIVA", "NAO_CADASTRADA"))
                    Color.rgb(176, 0, 32) else Color.rgb(8, 127, 35)
            )
            binding.logTime.text = formatter.format(Date(log.timestamp))
            val details = buildList {
                log.reviewerName?.let { add("Revisora: $it") }
                log.readCode?.let { add("Lida: $it${log.readDescription?.let { d -> " — $d" }.orEmpty()}") }
                log.expectedCode?.let { add("Esperada: $it${log.expectedDescription?.let { d -> " — $d" }.orEmpty()}") }
                log.details?.let(::add)
            }
            binding.logDetails.text = if (details.isEmpty()) "Sem detalhes adicionais" else details.joinToString("\n")
        }

        private fun label(value: String) = value.replace('_', ' ')
    }
}

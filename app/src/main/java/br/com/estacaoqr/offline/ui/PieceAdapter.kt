package br.com.estacaoqr.offline.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import br.com.estacaoqr.offline.data.PieceEntity
import br.com.estacaoqr.offline.databinding.ItemPieceBinding

class PieceAdapter(
    private val onPdf: (PieceEntity) -> Unit,
    private val onEdit: (PieceEntity) -> Unit,
    private val onDelete: (PieceEntity) -> Unit
) : RecyclerView.Adapter<PieceAdapter.Holder>() {
    private var items = emptyList<PieceEntity>()

    fun submit(newItems: List<PieceEntity>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder = Holder(
        ItemPieceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])
    override fun getItemCount(): Int = items.size

    inner class Holder(private val binding: ItemPieceBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(piece: PieceEntity) {
            binding.pieceCode.text = piece.code
            binding.pieceDescription.text = piece.description
            binding.pieceStatus.text = if (piece.active) "ATIVA" else "INATIVA"
            binding.piecePdfButton.isEnabled = piece.active
            binding.piecePdfButton.setOnClickListener { onPdf(piece) }
            binding.editPieceButton.setOnClickListener { onEdit(piece) }
            binding.deletePieceButton.setOnClickListener { onDelete(piece) }
        }
    }
}

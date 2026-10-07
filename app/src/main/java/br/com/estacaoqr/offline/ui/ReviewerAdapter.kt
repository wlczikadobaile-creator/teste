package br.com.estacaoqr.offline.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import br.com.estacaoqr.offline.data.ReviewerEntity
import br.com.estacaoqr.offline.databinding.ItemReviewerBinding

class ReviewerAdapter(
    private val onQr: (ReviewerEntity) -> Unit,
    private val onEdit: (ReviewerEntity) -> Unit,
    private val onDelete: (ReviewerEntity) -> Unit
) : RecyclerView.Adapter<ReviewerAdapter.Holder>() {
    private var items = emptyList<ReviewerEntity>()

    fun submit(newItems: List<ReviewerEntity>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder = Holder(
        ItemReviewerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )
    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])
    override fun getItemCount(): Int = items.size

    inner class Holder(private val binding: ItemReviewerBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(reviewer: ReviewerEntity) {
            binding.reviewerName.text = reviewer.name
            binding.reviewerId.text = "ID: ${reviewer.id}"
            binding.reviewerStatus.text = if (reviewer.active) "ATIVA" else "INATIVA"
            binding.showQrButton.isEnabled = reviewer.active
            binding.showQrButton.setOnClickListener { onQr(reviewer) }
            binding.editReviewerButton.setOnClickListener { onEdit(reviewer) }
            binding.deleteReviewerButton.setOnClickListener { onDelete(reviewer) }
        }
    }
}

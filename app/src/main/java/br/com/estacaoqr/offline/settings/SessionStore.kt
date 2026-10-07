package br.com.estacaoqr.offline.settings

import android.content.Context
import br.com.estacaoqr.offline.data.PieceEntity
import br.com.estacaoqr.offline.data.ReviewerEntity

class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("active_session", Context.MODE_PRIVATE)

    val reviewer: ReviewerEntity?
        get() {
            val id = prefs.getString("reviewer_id", null) ?: return null
            return ReviewerEntity(id, prefs.getString("reviewer_name", "") ?: "")
        }

    val expectedPiece: PieceEntity?
        get() {
            val code = prefs.getString("expected_code", null) ?: return null
            return PieceEntity(code, prefs.getString("expected_description", "") ?: "")
        }

    fun unlock(reviewer: ReviewerEntity) {
        prefs.edit()
            .putString("reviewer_id", reviewer.id)
            .putString("reviewer_name", reviewer.name)
            .remove("expected_code")
            .remove("expected_description")
            .apply()
    }

    fun setExpected(piece: PieceEntity) {
        prefs.edit()
            .putString("expected_code", piece.code)
            .putString("expected_description", piece.description)
            .apply()
    }

    fun clear() = prefs.edit().clear().apply()
}

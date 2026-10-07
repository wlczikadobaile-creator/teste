package br.com.estacaoqr.offline.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AppDao {
    @Query("SELECT * FROM pieces ORDER BY code COLLATE NOCASE")
    suspend fun getAllPieces(): List<PieceEntity>

    @Query("SELECT * FROM pieces WHERE code = :code COLLATE NOCASE AND active = 1 LIMIT 1")
    suspend fun findActivePiece(code: String): PieceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePiece(piece: PieceEntity)

    @Delete
    suspend fun deletePiece(piece: PieceEntity)

    @Query("DELETE FROM pieces")
    suspend fun clearPieces()

    @Query("SELECT * FROM reviewers ORDER BY name COLLATE NOCASE")
    suspend fun getAllReviewers(): List<ReviewerEntity>

    @Query("SELECT * FROM reviewers WHERE id = :id AND active = 1 LIMIT 1")
    suspend fun findActiveReviewer(id: String): ReviewerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveReviewer(reviewer: ReviewerEntity)

    @Delete
    suspend fun deleteReviewer(reviewer: ReviewerEntity)

    @Query("DELETE FROM reviewers")
    suspend fun clearReviewers()

    @Insert
    suspend fun insertLog(log: ScanLogEntity)

    @Query("SELECT * FROM scan_logs ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentLogs(limit: Int = 1000): List<ScanLogEntity>

    @Query("SELECT * FROM scan_logs ORDER BY timestamp DESC")
    suspend fun getAllLogs(): List<ScanLogEntity>

    @Query("DELETE FROM scan_logs")
    suspend fun clearLogs()
}

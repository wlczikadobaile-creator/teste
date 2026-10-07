package br.com.estacaoqr.offline.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pieces")
data class PieceEntity(
    @PrimaryKey val code: String,
    val description: String,
    val active: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "reviewers")
data class ReviewerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val active: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "scan_logs")
data class ScanLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val event: String,
    val result: String,
    val reviewerId: String? = null,
    val reviewerName: String? = null,
    val expectedCode: String? = null,
    val expectedDescription: String? = null,
    val readCode: String? = null,
    val readDescription: String? = null,
    val details: String? = null
)

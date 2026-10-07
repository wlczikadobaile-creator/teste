package br.com.estacaoqr.offline.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import br.com.estacaoqr.offline.databinding.ActivityAdminBinding

class AdminActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityAdminBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.piecesButton.setOnClickListener { startActivity(Intent(this, PiecesActivity::class.java)) }
        binding.reviewersButton.setOnClickListener { startActivity(Intent(this, ReviewersActivity::class.java)) }
        binding.logsButton.setOnClickListener { startActivity(Intent(this, LogsActivity::class.java)) }
        binding.settingsButton.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        binding.backToScannerButton.setOnClickListener { finish() }
    }
}

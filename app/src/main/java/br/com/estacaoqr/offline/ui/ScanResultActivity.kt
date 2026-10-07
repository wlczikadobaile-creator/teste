package br.com.estacaoqr.offline.ui

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import br.com.estacaoqr.offline.R
import br.com.estacaoqr.offline.databinding.ActivityScanResultBinding
import br.com.estacaoqr.offline.kiosk.KioskController
import br.com.estacaoqr.offline.settings.AppSettings

class ScanResultActivity : AppCompatActivity() {
    private lateinit var binding: ActivityScanResultBinding
    private var closing = false
    private var toneGenerator: ToneGenerator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScanResultBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        KioskController.hideSystemUi(this)

        val kind = intent.getStringExtra(EXTRA_KIND) ?: KIND_INFO
        binding.resultRoot.setBackgroundColor(
            ContextCompat.getColor(
                this,
                when (kind) {
                    KIND_OK -> R.color.ok_green
                    KIND_ERROR -> R.color.error_red
                    else -> R.color.industrial_blue
                }
            )
        )
        binding.resultTitle.text = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        binding.resultDetails.text = intent.getStringExtra(EXTRA_DETAILS).orEmpty()

        binding.resultRoot.apply {
            alpha = 0f
            scaleX = 0.97f
            scaleY = 0.97f
            animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(280L).start()
            setOnClickListener { closeWithAnimation() }
        }

        if (kind == KIND_ERROR && AppSettings(this).soundEnabled) playAlert()

        val duration = intent.getLongExtra(EXTRA_DURATION, DEFAULT_DURATION_MS)
        binding.resultRoot.postDelayed({ closeWithAnimation() }, duration)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) KioskController.hideSystemUi(this)
    }

    override fun onBackPressed() {
        closeWithAnimation()
    }

    override fun onDestroy() {
        toneGenerator?.release()
        toneGenerator = null
        super.onDestroy()
    }

    private fun playAlert() {
        toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100).also {
            it.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 900)
        }
        binding.resultRoot.postDelayed({
            toneGenerator?.release()
            toneGenerator = null
        }, 1100L)
    }

    private fun closeWithAnimation() {
        if (closing) return
        closing = true
        binding.resultRoot.animate()
            .alpha(0f)
            .scaleX(1.02f)
            .scaleY(1.02f)
            .setDuration(220L)
            .withEndAction {
                finish()
                overridePendingTransition(0, 0)
            }
            .start()
    }

    companion object {
        const val KIND_OK = "OK"
        const val KIND_ERROR = "ERROR"
        const val KIND_INFO = "INFO"

        private const val EXTRA_KIND = "result_kind"
        private const val EXTRA_TITLE = "result_title"
        private const val EXTRA_DETAILS = "result_details"
        private const val EXTRA_DURATION = "result_duration"
        private const val DEFAULT_DURATION_MS = 2400L

        fun createIntent(
            context: Context,
            kind: String,
            title: String,
            details: String,
            durationMs: Long
        ): Intent = Intent(context, ScanResultActivity::class.java).apply {
            putExtra(EXTRA_KIND, kind)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_DETAILS, details)
            putExtra(EXTRA_DURATION, durationMs)
        }
    }
}

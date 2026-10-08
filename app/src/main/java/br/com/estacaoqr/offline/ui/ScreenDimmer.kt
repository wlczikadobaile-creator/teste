package br.com.estacaoqr.offline.ui

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.WindowManager

/**
 * Mantém a tela sempre ligada e, após um período sem uso, reduz o brilho do app.
 * O primeiro toque (ou uma leitura de QR) devolve o brilho anterior.
 */
class ScreenDimmer(
    private val activity: Activity,
    private val idleMs: Long = IDLE_MS,
    private val dimLevel: Float = DIM_LEVEL
) {
    private val handler = Handler(Looper.getMainLooper())
    private var enabled = false
    private var dimmed = false
    private var swallowingGesture = false
    private val dimRunnable = Runnable { dim() }

    fun setEnabled(value: Boolean) {
        enabled = value
        if (value) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            restartTimer()
        } else {
            handler.removeCallbacks(dimRunnable)
            restore()
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    /** Qualquer atividade (leitura de QR, toque) reinicia a contagem e restaura o brilho. */
    fun onActivity() {
        if (!enabled) return
        restore()
        restartTimer()
    }

    /**
     * Retorna true quando o toque deve ser consumido: o toque que "acorda" a tela
     * apenas restaura o brilho, sem acionar botões por engano.
     */
    fun handleTouch(event: MotionEvent): Boolean {
        if (!enabled) return false
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            swallowingGesture = dimmed
            onActivity()
        }
        val consume = swallowingGesture
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            swallowingGesture = false
        }
        return consume
    }

    fun pause() {
        handler.removeCallbacks(dimRunnable)
        restore()
    }

    private fun restartTimer() {
        handler.removeCallbacks(dimRunnable)
        handler.postDelayed(dimRunnable, idleMs)
    }

    private fun dim() {
        if (!enabled || dimmed) return
        dimmed = true
        setBrightness(dimLevel)
    }

    private fun restore() {
        if (!dimmed) return
        dimmed = false
        setBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
    }

    private fun setBrightness(value: Float) {
        val params = activity.window.attributes
        params.screenBrightness = value
        activity.window.attributes = params
    }

    companion object {
        const val IDLE_MS = 5 * 60 * 1000L
        const val DIM_LEVEL = 0.05f
    }
}

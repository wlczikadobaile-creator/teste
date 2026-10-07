package br.com.estacaoqr.offline.scanner

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.Camera
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import br.com.estacaoqr.offline.settings.AppSettings
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Visualização da câmera em modo "cortado" (center-crop): a imagem preenche toda a área
 * disponível mantendo a proporção real do sensor, sem distorção. As sobras ficam fora da
 * área visível, mas a decodificação do QR continua usando o quadro inteiro da câmera.
 *
 * Quatro toques rápidos sobre a câmera alternam entre a câmera traseira e a frontal.
 */
@Suppress("DEPRECATION")
class QrCameraView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs), SurfaceHolder.Callback, Camera.PreviewCallback {

    var onQrRead: ((String) -> Unit)? = null
    var onCameraError: ((String) -> Unit)? = null
    /** Chamado após a troca de câmera, com uma mensagem curta para exibir ao usuário. */
    var onCameraSwitched: ((String) -> Unit)? = null

    private val settings = AppSettings(context)
    private val surfaceView = SurfaceView(context)
    private var surfaceReady = false

    private var camera: Camera? = null
    private var cameraId = -1
    @Volatile private var frameWidth = 0
    @Volatile private var frameHeight = 0
    /** Proporção da imagem já girada para a orientação da tela (largura / altura). */
    private var displayAspect = 0f

    private var tapCount = 0
    private var lastTapAt = 0L

    private val decoding = AtomicBoolean(false)
    private val executor = Executors.newSingleThreadExecutor()
    private val reader = MultiFormatReader().apply {
        setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
    }

    init {
        clipChildren = true
        clipToPadding = true
        setBackgroundColor(0xFF000000.toInt())
        surfaceView.holder.addCallback(this)
        surfaceView.holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS)
        addView(surfaceView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    // ---------- Layout: center-crop sem distorção ----------

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(width, height)

        var childW = width
        var childH = height
        if (displayAspect > 0f && width > 0 && height > 0) {
            // Escala a imagem até cobrir toda a área (o excesso é cortado).
            val scale = max(width / displayAspect, height.toFloat())
            childH = scale.roundToInt()
            childW = (scale * displayAspect).roundToInt()
            if (childW < width) childW = width
            if (childH < height) childH = height
        }
        surfaceView.measure(
            MeasureSpec.makeMeasureSpec(childW, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(childH, MeasureSpec.EXACTLY)
        )
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val w = right - left
        val h = bottom - top
        val cw = surfaceView.measuredWidth
        val ch = surfaceView.measuredHeight
        val l = (w - cw) / 2
        val t = (h - ch) / 2
        surfaceView.layout(l, t, l + cw, t + ch)
    }

    override fun setVisibility(visibility: Int) {
        super.setVisibility(visibility)
        surfaceView.visibility = if (visibility == View.VISIBLE) View.VISIBLE else View.INVISIBLE
    }

    // ---------- Troca de câmera com 4 toques rápidos ----------

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_UP -> {
                val now = System.currentTimeMillis()
                if (now - lastTapAt > TAP_INTERVAL_MS) tapCount = 0
                tapCount++
                lastTapAt = now
                if (tapCount >= TAPS_TO_SWITCH) {
                    tapCount = 0
                    switchCamera()
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    fun switchCamera() {
        if (Camera.getNumberOfCameras() < 2) {
            onCameraSwitched?.invoke("Este aparelho possui apenas uma câmera")
            return
        }
        settings.useFrontCamera = !settings.useFrontCamera
        stopCamera()
        if (surfaceReady) openCamera(surfaceView.holder)
        val cameraFront = cameraId >= 0 && isFront(cameraId)
        onCameraSwitched?.invoke(if (cameraFront) "Câmera frontal" else "Câmera traseira")
    }

    // ---------- Ciclo de vida da superfície ----------

    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceReady = true
        openCamera(holder)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        camera?.let { cam ->
            runCatching {
                cam.stopPreview()
                cam.setDisplayOrientation(displayOrientation(cameraId))
                cam.setPreviewDisplay(holder)
                cam.setPreviewCallbackWithBuffer(this)
                allocateBuffer(cam)
                cam.startPreview()
            }.onFailure { onCameraError?.invoke("Não foi possível iniciar a câmera: ${it.message}") }
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceReady = false
        stopCamera()
    }

    private fun openCamera(surfaceHolder: SurfaceHolder) {
        if (camera != null) return
        runCatching {
            val id = findCamera(settings.useFrontCamera)
            val cam = if (id >= 0) Camera.open(id) else Camera.open()
            camera = cam
            cameraId = id
            configureCamera(cam)
            cam.setPreviewDisplay(surfaceHolder)
            cam.setPreviewCallbackWithBuffer(this)
            allocateBuffer(cam)
            cam.startPreview()
        }.onFailure {
            stopCamera()
            onCameraError?.invoke("Câmera indisponível: ${it.message}")
        }
    }

    private fun configureCamera(cam: Camera) {
        val params = cam.parameters
        val orientation = displayOrientation(cameraId)
        val rotated = orientation == 90 || orientation == 270

        // Escolhe um tamanho de prévia bom para leitura, próximo da proporção da área visível.
        val viewW = width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val viewH = height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
        val targetW = if (rotated) viewH else viewW
        val targetH = if (rotated) viewW else viewH
        val targetAspect = targetW.toFloat() / targetH
        val sizes = params.supportedPreviewSizes.orEmpty()
        val candidates = sizes.filter { it.width * it.height in MIN_PIXELS..MAX_PIXELS }.ifEmpty { sizes }
        candidates.minByOrNull { size ->
            val aspectPenalty = abs(size.width.toFloat() / size.height - targetAspect) * 4000f
            val sizePenalty = abs(size.width - targetW) + abs(size.height - targetH)
            aspectPenalty + sizePenalty
        }?.let { params.setPreviewSize(it.width, it.height) }

        params.previewFormat = ImageFormat.NV21
        val focusModes = params.supportedFocusModes.orEmpty()
        when {
            Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE in focusModes ->
                params.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
            Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO in focusModes ->
                params.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO
            Camera.Parameters.FOCUS_MODE_AUTO in focusModes ->
                params.focusMode = Camera.Parameters.FOCUS_MODE_AUTO
        }
        cam.parameters = params
        cam.setDisplayOrientation(orientation)

        val size = cam.parameters.previewSize
        frameWidth = size.width
        frameHeight = size.height
        val newAspect = if (rotated) size.height.toFloat() / size.width else size.width.toFloat() / size.height
        if (newAspect != displayAspect) {
            displayAspect = newAspect
            post { requestLayout() }
        }
    }

    private fun allocateBuffer(cam: Camera) {
        val size = cam.parameters.previewSize
        val bits = ImageFormat.getBitsPerPixel(cam.parameters.previewFormat)
        cam.addCallbackBuffer(ByteArray(size.width * size.height * bits / 8))
    }

    // ---------- Decodificação (sempre no quadro inteiro, independente do corte) ----------

    override fun onPreviewFrame(data: ByteArray?, sourceCamera: Camera?) {
        if (data == null || sourceCamera == null || !decoding.compareAndSet(false, true)) {
            if (data != null) runCatching { sourceCamera?.addCallbackBuffer(data) }
            return
        }
        val w = frameWidth
        val h = frameHeight
        if (w <= 0 || h <= 0 || data.size < w * h) {
            decoding.set(false)
            runCatching { sourceCamera.addCallbackBuffer(data) }
            return
        }
        executor.execute {
            val value = decode(data, w, h)
            post { value?.let { onQrRead?.invoke(it) } }
            decoding.set(false)
            if (camera === sourceCamera) runCatching { sourceCamera.addCallbackBuffer(data) }
        }
    }

    private fun decode(data: ByteArray, width: Int, height: Int): String? {
        decodeLuma(data, width, height)?.let { return it }
        val rotated = ByteArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                rotated[x * height + (height - y - 1)] = data[y * width + x]
            }
        }
        return decodeLuma(rotated, height, width)
    }

    private fun decodeLuma(data: ByteArray, width: Int, height: Int): String? = runCatching {
        val source = PlanarYUVLuminanceSource(data, width, height, 0, 0, width, height, false)
        reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
    }.getOrNull().also { reader.reset() }

    fun stopCamera() {
        camera?.let { cam ->
            runCatching { cam.setPreviewCallbackWithBuffer(null) }
            runCatching { cam.stopPreview() }
            runCatching { cam.release() }
        }
        camera = null
    }

    // ---------- Utilidades ----------

    private fun findCamera(front: Boolean): Int {
        val wanted = if (front) Camera.CameraInfo.CAMERA_FACING_FRONT else Camera.CameraInfo.CAMERA_FACING_BACK
        val info = Camera.CameraInfo()
        for (id in 0 until Camera.getNumberOfCameras()) {
            Camera.getCameraInfo(id, info)
            if (info.facing == wanted) return id
        }
        return if (Camera.getNumberOfCameras() > 0) 0 else -1
    }

    private fun isFront(id: Int): Boolean {
        val info = Camera.CameraInfo().also { Camera.getCameraInfo(id, it) }
        return info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT
    }

    private fun displayOrientation(id: Int): Int {
        if (id < 0) return 90
        val info = Camera.CameraInfo().also { Camera.getCameraInfo(id, it) }
        val rotation = (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
            .defaultDisplay.rotation
        val degrees = when (rotation) {
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
        return if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            (360 - (info.orientation + degrees) % 360) % 360
        } else {
            (info.orientation - degrees + 360) % 360
        }
    }

    private companion object {
        const val TAPS_TO_SWITCH = 4
        const val TAP_INTERVAL_MS = 450L
        const val MIN_PIXELS = 320 * 240
        const val MAX_PIXELS = 1920 * 1080
    }
}

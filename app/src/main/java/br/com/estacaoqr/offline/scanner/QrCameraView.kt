package br.com.estacaoqr.offline.scanner

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.Camera
import android.util.AttributeSet
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

@Suppress("DEPRECATION")
class QrCameraView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SurfaceView(context, attrs), SurfaceHolder.Callback, Camera.PreviewCallback {

    var onQrRead: ((String) -> Unit)? = null
    var onCameraError: ((String) -> Unit)? = null

    private var camera: Camera? = null
    private val decoding = AtomicBoolean(false)
    private val executor = Executors.newSingleThreadExecutor()
    private val reader = MultiFormatReader().apply {
        setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
    }

    init {
        holder.addCallback(this)
        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS)
    }

    override fun surfaceCreated(holder: SurfaceHolder) = openCamera(holder)

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        camera?.let { cam ->
            runCatching {
                cam.stopPreview()
                configureCamera(cam)
                cam.setPreviewDisplay(holder)
                cam.setPreviewCallbackWithBuffer(this)
                allocateBuffer(cam)
                cam.startPreview()
            }.onFailure { onCameraError?.invoke("Não foi possível iniciar a câmera: ${it.message}") }
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) = stopCamera()

    private fun openCamera(surfaceHolder: SurfaceHolder) {
        if (camera != null) return
        runCatching {
            val cameraId = findBackCamera()
            val cam = if (cameraId >= 0) Camera.open(cameraId) else Camera.open()
            camera = cam
            configureCamera(cam, cameraId)
            cam.setPreviewDisplay(surfaceHolder)
            cam.setPreviewCallbackWithBuffer(this)
            allocateBuffer(cam)
            cam.startPreview()
        }.onFailure {
            stopCamera()
            onCameraError?.invoke("Câmera indisponível: ${it.message}")
        }
    }

    private fun configureCamera(cam: Camera, cameraId: Int = findBackCamera()) {
        val params = cam.parameters
        val targetWidth = width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val targetHeight = height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
        params.supportedPreviewSizes?.minByOrNull { size ->
            abs(size.width - targetHeight) + abs(size.height - targetWidth)
        }?.let { params.setPreviewSize(it.width, it.height) }
        params.previewFormat = ImageFormat.NV21
        if (Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE in params.supportedFocusModes.orEmpty()) {
            params.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
        } else if (Camera.Parameters.FOCUS_MODE_AUTO in params.supportedFocusModes.orEmpty()) {
            params.focusMode = Camera.Parameters.FOCUS_MODE_AUTO
        }
        cam.parameters = params
        cam.setDisplayOrientation(displayOrientation(cameraId))
    }

    private fun allocateBuffer(cam: Camera) {
        val size = cam.parameters.previewSize
        val bits = ImageFormat.getBitsPerPixel(cam.parameters.previewFormat)
        cam.addCallbackBuffer(ByteArray(size.width * size.height * bits / 8))
    }

    override fun onPreviewFrame(data: ByteArray?, sourceCamera: Camera?) {
        if (data == null || sourceCamera == null || !decoding.compareAndSet(false, true)) {
            if (data != null) sourceCamera?.addCallbackBuffer(data)
            return
        }
        val size = sourceCamera.parameters.previewSize
        executor.execute {
            val value = decode(data, size.width, size.height)
            post { value?.let { onQrRead?.invoke(it) } }
            decoding.set(false)
            runCatching { sourceCamera.addCallbackBuffer(data) }
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

    private fun findBackCamera(): Int {
        val info = Camera.CameraInfo()
        for (id in 0 until Camera.getNumberOfCameras()) {
            Camera.getCameraInfo(id, info)
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) return id
        }
        return -1
    }

    private fun displayOrientation(cameraId: Int): Int {
        if (cameraId < 0) return 90
        val info = Camera.CameraInfo().also { Camera.getCameraInfo(cameraId, it) }
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
}

package br.com.estacaoqr.offline.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import br.com.estacaoqr.offline.data.PieceEntity
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlin.math.ceil

object PiecePlaquePdf {
    // Plaquinha horizontal baseada no modelo fornecido: 100 x 50 mm.
    private const val PLAQUE_WIDTH = 283
    private const val PLAQUE_HEIGHT = 142
    private const val A4_WIDTH = 595
    private const val A4_HEIGHT = 842
    private const val COLUMNS = 2
    private const val ROWS = 5
    private const val PLAQUES_PER_PAGE = COLUMNS * ROWS
    private const val GAP_X = 0f
    private const val GAP_Y = 8f

    fun writeSingle(context: Context, uri: Uri, piece: PieceEntity) {
        val document = PdfDocument()
        try {
            val page = document.startPage(
                PdfDocument.PageInfo.Builder(PLAQUE_WIDTH, PLAQUE_HEIGHT, 1).create()
            )
            drawPlaque(page.canvas, 0f, 0f, piece)
            document.finishPage(page)
            writeDocument(context, uri, document)
        } finally {
            document.close()
        }
    }

    fun writeAllA4(context: Context, uri: Uri, pieces: List<PieceEntity>): Int {
        require(pieces.isNotEmpty())
        val document = PdfDocument()
        val pageCount = ceil(pieces.size / PLAQUES_PER_PAGE.toDouble()).toInt()
        try {
            val usedWidth = COLUMNS * PLAQUE_WIDTH + (COLUMNS - 1) * GAP_X
            val usedHeight = ROWS * PLAQUE_HEIGHT + (ROWS - 1) * GAP_Y
            val marginX = (A4_WIDTH - usedWidth) / 2f
            val marginY = (A4_HEIGHT - usedHeight) / 2f

            pieces.chunked(PLAQUES_PER_PAGE).forEachIndexed { pageIndex, pagePieces ->
                val page = document.startPage(
                    PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, pageIndex + 1).create()
                )
                page.canvas.drawColor(Color.WHITE)
                pagePieces.forEachIndexed { index, piece ->
                    val column = index % COLUMNS
                    val row = index / COLUMNS
                    val x = marginX + column * (PLAQUE_WIDTH + GAP_X)
                    val y = marginY + row * (PLAQUE_HEIGHT + GAP_Y)
                    drawPlaque(page.canvas, x, y, piece)
                    drawCutGuides(page.canvas, x, y)
                }
                document.finishPage(page)
            }
            writeDocument(context, uri, document)
            return pageCount
        } finally {
            document.close()
        }
    }

    private fun drawPlaque(canvas: Canvas, x: Float, y: Float, piece: PieceEntity) {
        val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val blackLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        canvas.drawRect(x, y, x + PLAQUE_WIDTH, y + PLAQUE_HEIGHT, white)
        canvas.drawRect(x + 0.5f, y + 0.5f, x + PLAQUE_WIDTH - 0.5f, y + PLAQUE_HEIGHT - 0.5f, blackLine)

        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 11f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("QR CODE - PADRÃO", x + PLAQUE_WIDTH / 2f, y + 17f, title)
        canvas.drawLine(x + 92f, y + 20f, x + 191f, y + 20f, blackLine)

        val qr = createQrBitmap(piece.code, 600)
        canvas.drawBitmap(qr, null, RectF(x + 8f, y + 29f, x + 108f, y + 129f), null)
        qr.recycle()

        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 7.2f
        }
        val value = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textSize = 8.2f
        }

        canvas.drawText("DESCRIÇÃO:", x + 116f, y + 48f, label)
        drawWrappedText(canvas, piece.description, x + 158f, y + 48f, 116f, value, 3)
        canvas.drawText("CÓDIGO:", x + 116f, y + 112f, label)
        drawFittedText(canvas, piece.code, x + 151f, y + 112f, 123f, value)
    }

    private fun drawWrappedText(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        maxWidth: Float,
        paint: Paint,
        maxLines: Int
    ) {
        var remaining = text.trim()
        var line = 0
        while (remaining.isNotEmpty() && line < maxLines) {
            val count = paint.breakText(remaining, true, maxWidth, null).coerceAtLeast(1)
            var breakAt = if (count < remaining.length) remaining.lastIndexOf(' ', count) else count
            if (breakAt <= 0) breakAt = count
            var segment = remaining.substring(0, breakAt).trim()
            remaining = remaining.substring(breakAt).trim()
            if (line == maxLines - 1 && remaining.isNotEmpty()) {
                val ellipsisWidth = paint.measureText("…")
                val fitted = paint.breakText(segment + " " + remaining, true, maxWidth - ellipsisWidth, null)
                segment = (segment + " " + remaining).take(fitted).trimEnd() + "…"
                remaining = ""
            }
            canvas.drawText(segment, x, y + line * 12f, paint)
            line++
        }
    }

    private fun drawFittedText(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        maxWidth: Float,
        basePaint: Paint
    ) {
        val fitted = Paint(basePaint)
        while (fitted.textSize > 5.5f && fitted.measureText(text) > maxWidth) {
            fitted.textSize -= 0.4f
        }
        canvas.drawText(text, x, y, fitted)
    }

    private fun drawCutGuides(canvas: Canvas, x: Float, y: Float) {
        val guide = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(165, 165, 165)
            style = Paint.Style.STROKE
            strokeWidth = 0.35f
        }
        canvas.drawRect(x, y, x + PLAQUE_WIDTH, y + PLAQUE_HEIGHT, guide)
    }

    private fun createQrBitmap(value: String, size: Int): Bitmap {
        val matrix = QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, size, size)
        val pixels = IntArray(size * size)
        for (row in 0 until size) for (column in 0 until size) {
            pixels[row * size + column] = if (matrix[column, row]) Color.BLACK else Color.WHITE
        }
        return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, size, 0, 0, size, size)
        }
    }

    private fun writeDocument(context: Context, uri: Uri, document: PdfDocument) {
        val stream = requireNotNull(context.contentResolver.openOutputStream(uri, "w")) {
            "Não foi possível abrir o arquivo escolhido."
        }
        stream.use(document::writeTo)
    }
}

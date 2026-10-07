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
import br.com.estacaoqr.offline.data.ReviewerEntity
import br.com.estacaoqr.offline.security.ReviewerQrSigner
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlin.math.ceil

object ReviewerCardPdf {
    // Cartão padrão ID-1: 85,6 x 54 mm, convertido para pontos PDF (72 pontos/polegada).
    private const val CARD_WIDTH = 243
    private const val CARD_HEIGHT = 153
    private const val A4_WIDTH = 595
    private const val A4_HEIGHT = 842
    private const val COLUMNS = 2
    private const val ROWS = 5
    private const val CARDS_PER_PAGE = COLUMNS * ROWS
    private const val GAP = 10f

    fun writeSingle(context: Context, uri: Uri, reviewer: ReviewerEntity) {
        val document = PdfDocument()
        try {
            val page = document.startPage(PdfDocument.PageInfo.Builder(CARD_WIDTH, CARD_HEIGHT, 1).create())
            drawCard(page.canvas, 0f, 0f, reviewer, context)
            document.finishPage(page)
            writeDocument(context, uri, document)
        } finally {
            document.close()
        }
    }

    fun writeAllA4(context: Context, uri: Uri, reviewers: List<ReviewerEntity>): Int {
        require(reviewers.isNotEmpty())
        val document = PdfDocument()
        val pageCount = ceil(reviewers.size / CARDS_PER_PAGE.toDouble()).toInt()
        try {
            val totalCardsWidth = COLUMNS * CARD_WIDTH + (COLUMNS - 1) * GAP
            val totalCardsHeight = ROWS * CARD_HEIGHT + (ROWS - 1) * GAP
            val marginX = (A4_WIDTH - totalCardsWidth) / 2f
            val marginY = (A4_HEIGHT - totalCardsHeight) / 2f

            reviewers.chunked(CARDS_PER_PAGE).forEachIndexed { pageIndex, pageReviewers ->
                val page = document.startPage(
                    PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, pageIndex + 1).create()
                )
                page.canvas.drawColor(Color.WHITE)
                pageReviewers.forEachIndexed { index, reviewer ->
                    val column = index % COLUMNS
                    val row = index / COLUMNS
                    val x = marginX + column * (CARD_WIDTH + GAP)
                    val y = marginY + row * (CARD_HEIGHT + GAP)
                    drawCard(page.canvas, x, y, reviewer, context)
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

    private fun drawCard(canvas: Canvas, x: Float, y: Float, reviewer: ReviewerEntity, context: Context) {
        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(11, 61, 92)
            style = Paint.Style.STROKE
            strokeWidth = 1.4f
        }
        canvas.drawRect(x, y, x + CARD_WIDTH, y + CARD_HEIGHT, background)
        canvas.drawRect(x + 0.7f, y + 0.7f, x + CARD_WIDTH - 0.7f, y + CARD_HEIGHT - 0.7f, border)

        val band = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(11, 61, 92) }
        canvas.drawRect(x + 1.4f, y + 1.4f, x + 103f, y + CARD_HEIGHT - 1.4f, band)

        val whiteBold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 14f
        }
        val whiteSmall = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textSize = 7.2f
        }
        val blueSmall = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(11, 61, 92)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 7f
            textAlign = Paint.Align.CENTER
        }

        canvas.drawText("ESTAÇÃO QR", x + 9f, y + 17f, whiteSmall)
        drawName(canvas, reviewer.name.uppercase(), x + 9f, y + 43f, 85f, whiteBold)
        canvas.drawText("REVISORA AUTORIZADA", x + 9f, y + 89f, whiteSmall)
        canvas.drawText("ID ${reviewer.id.takeLast(8).uppercase()}", x + 9f, y + 106f, whiteSmall)
        canvas.drawText("APRESENTE ESTE CARTÃO", x + 9f, y + 137f, whiteSmall)

        val payload = ReviewerQrSigner(context).create(reviewer.id, reviewer.name)
        val qr = createQrBitmap(payload, 600)
        val qrArea = RectF(x + 111f, y + 8f, x + 235f, y + 132f)
        canvas.drawBitmap(qr, null, qrArea, null)
        qr.recycle()
        canvas.drawText("QR ASSINADO PARA LIBERAÇÃO", x + 173f, y + 144f, blueSmall)
    }

    private fun drawName(canvas: Canvas, name: String, x: Float, y: Float, maxWidth: Float, paint: Paint) {
        if (paint.measureText(name) <= maxWidth) {
            canvas.drawText(name, x, y, paint)
            return
        }
        val firstCount = paint.breakText(name, true, maxWidth, null).coerceAtLeast(1)
        val breakAt = name.lastIndexOf(' ', firstCount).takeIf { it > 0 } ?: firstCount
        val first = name.substring(0, breakAt).trim()
        var second = name.substring(breakAt).trim()
        if (paint.measureText(second) > maxWidth) {
            val count = paint.breakText(second, true, maxWidth - paint.measureText("…"), null).coerceAtLeast(1)
            second = second.take(count).trimEnd() + "…"
        }
        canvas.drawText(first, x, y, paint)
        canvas.drawText(second, x, y + 17f, paint)
    }

    private fun drawCutGuides(canvas: Canvas, x: Float, y: Float) {
        val guide = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(175, 175, 175)
            style = Paint.Style.STROKE
            strokeWidth = 0.35f
        }
        canvas.drawRect(x, y, x + CARD_WIDTH, y + CARD_HEIGHT, guide)
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

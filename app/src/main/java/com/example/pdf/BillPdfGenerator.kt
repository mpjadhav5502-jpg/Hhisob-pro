package com.example.pdf

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.print.pdf.PrintedPdfDocument
import androidx.core.content.FileProvider
import com.example.data.local.BillEntity
import com.example.data.local.ShopSettingsEntity
import com.example.domain.BillingCalculator
import java.io.File
import java.io.FileOutputStream

object BillPdfGenerator {
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842

    fun createBillPdfFile(
        context: Context,
        bill: BillEntity,
        settings: ShopSettingsEntity
    ): Result<File> {
        return try {
            val billsDir = File(context.cacheDir, "bills").apply { mkdirs() }
            val safeNo = bill.billNumber.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
            val file = File(billsDir, "MasoorBill_$safeNo.pdf")

            val document = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
            val page = document.startPage(pageInfo)
            drawBillPage(page.canvas, PAGE_WIDTH, PAGE_HEIGHT, bill, settings)
            document.finishPage(page)

            FileOutputStream(file).use { out ->
                document.writeTo(out)
            }
            document.close()
            Result.success(file)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun shareBillPdf(
        context: Context,
        bill: BillEntity,
        settings: ShopSettingsEntity
    ): Result<Unit> {
        val fileResult = createBillPdfFile(context, bill, settings)
        val file = fileResult.getOrElse { return Result.failure(it) }
        return try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(
                    Intent.EXTRA_SUBJECT,
                    "${bill.shopNameSnapshot} - बिल ${bill.billNumber}"
                )
                putExtra(
                    Intent.EXTRA_TEXT,
                    "${bill.shopNameSnapshot} | बिल क्र.: ${bill.billNumber} | ग्राहक: ${bill.customerName} | एकूण: ${BillingCalculator.formatInr(bill.grandTotal)} | बाकी: ${BillingCalculator.formatInr(bill.pendingAmount)}"
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, "PDF बिल शेअर करा").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun printBill(
        context: Context,
        bill: BillEntity,
        settings: ShopSettingsEntity
    ): Result<Unit> {
        return try {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
                ?: return Result.failure(IllegalStateException("प्रिंट सेवा उपलब्ध नाही"))
            val jobName = "MasoorBill_${bill.billNumber}"
            printManager.print(jobName, BillPrintAdapter(context, bill, settings), null)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    internal fun drawBillPage(
        canvas: Canvas,
        width: Int,
        height: Int,
        bill: BillEntity,
        settings: ShopSettingsEntity
    ) {
        val left = 48f
        val right = width - 48f
        val center = width / 2f

        val bgPaint = Paint().apply { color = Color.WHITE }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        val headerBgPaint = Paint().apply { color = Color.rgb(154, 52, 18) }
        canvas.drawRect(0f, 0f, width.toFloat(), 118f, headerBgPaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 237, 213)
            textSize = 12f
            textAlign = Paint.Align.CENTER
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(90, 70, 60)
            textSize = 12.5f
        }
        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(31, 22, 18)
            textSize = 13.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val rightValuePaint = Paint(valuePaint).apply {
            textAlign = Paint.Align.RIGHT
        }
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(214, 195, 179)
            strokeWidth = 1.2f
            pathEffect = DashPathEffect(floatArrayOf(6f, 4f), 0f)
        }
        val solidDividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(154, 52, 18)
            strokeWidth = 1.6f
        }

        // Shop Header
        canvas.drawText(bill.shopNameSnapshot, center, 48f, titlePaint)
        if (settings.shopSubtitle.isNotBlank()) {
            canvas.drawText(settings.shopSubtitle, center, 70f, subtitlePaint)
        }
        val contactLine = listOfNotNull(
            settings.shopPhone.takeIf { it.isNotBlank() }?.let { "मो.: $it" },
            settings.shopAddress.takeIf { it.isNotBlank() }
        ).joinToString(" • ")
        if (contactLine.isNotBlank()) {
            canvas.drawText(contactLine, center, 92f, subtitlePaint)
        }

        var y = 152f

        // Bill Metadata
        canvas.drawText("बिल क्र.: ${bill.billNumber}", left, y, valuePaint)
        canvas.drawText(
            "दिनांक: ${BillingCalculator.formatDateTime(bill.billTimestampMillis)}",
            right,
            y,
            rightValuePaint
        )
        y += 26f
        canvas.drawText("ग्राहक: ${bill.customerName}", left, y, valuePaint)
        if (bill.customerMobile.isNotBlank()) {
            canvas.drawText("मोबाईल: ${bill.customerMobile}", right, y, rightValuePaint)
        }
        y += 22f
        canvas.drawLine(left, y, right, y, solidDividerPaint)
        y += 24f

        // Table Header
        val colQtyX = right - 180f
        val colRateX = right - 95f
        val headerRowPaint = Paint(valuePaint).apply {
            color = Color.rgb(124, 45, 18)
            textSize = 13f
        }
        val headerRightPaint = Paint(headerRowPaint).apply { textAlign = Paint.Align.RIGHT }

        canvas.drawText("पदार्थ (Item)", left, y, headerRowPaint)
        canvas.drawText("नग", colQtyX, y, headerRightPaint)
        canvas.drawText("दर", colRateX, y, headerRightPaint)
        canvas.drawText("रक्कम", right, y, headerRightPaint)
        y += 14f
        canvas.drawLine(left, y, right, y, dividerPaint)
        y += 26f

        fun drawItemRow(name: String, qty: Int, price: Int) {
            if (qty <= 0) return
            val rowTotal = qty * price
            canvas.drawText(name, left, y, valuePaint)
            canvas.drawText(qty.toString(), colQtyX, y, rightValuePaint)
            canvas.drawText(BillingCalculator.formatInr(price), colRateX, y, rightValuePaint)
            canvas.drawText(BillingCalculator.formatInr(rowTotal), right, y, rightValuePaint)
            y += 28f
        }

        drawItemRow("फुल आखा मसूर (Full Akha Masoor)", bill.fullMasoorQty, bill.fullMasoorPrice)
        drawItemRow("हाफ पक्का मसूर (Half Pakka Masoor)", bill.halfMasoorQty, bill.halfMasoorPrice)
        drawItemRow("रोटी (Roti)", bill.rotiQty, bill.rotiPrice)

        y += 6f
        canvas.drawLine(left, y, right, y, solidDividerPaint)
        y += 32f

        // Grand Total
        val grandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(154, 52, 18)
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val grandRightPaint = Paint(grandPaint).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText("एकूण बिल (Grand Total)", left, y, grandPaint)
        canvas.drawText(BillingCalculator.formatInr(bill.grandTotal), right, y, grandRightPaint)
        y += 22f
        canvas.drawLine(left, y, right, y, dividerPaint)
        y += 28f

        // Payment Details
        canvas.drawText("रोख जमा (Cash Paid)", left, y, labelPaint)
        canvas.drawText(BillingCalculator.formatInr(bill.cashPaid), right, y, rightValuePaint)
        y += 24f

        val onlineLabel = if (bill.onlinePaid > 0 && bill.onlineConfirmed) {
            "ऑनलाईन जमा (Online Confirmed)"
        } else {
            "ऑनलाईन जमा (Online)"
        }
        canvas.drawText(onlineLabel, left, y, labelPaint)
        canvas.drawText(BillingCalculator.formatInr(bill.onlinePaid), right, y, rightValuePaint)
        y += 24f

        canvas.drawText("एकूण जमा रक्कम (Total Received)", left, y, valuePaint)
        canvas.drawText(BillingCalculator.formatInr(bill.totalReceived), right, y, rightValuePaint)
        y += 28f

        val pendingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (bill.pendingAmount > 0) Color.rgb(185, 28, 28) else Color.rgb(21, 128, 61)
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val pendingRightPaint = Paint(pendingPaint).apply { textAlign = Paint.Align.RIGHT }
        val balanceLabel = if (bill.pendingAmount > 0) {
            "बाकी रक्कम (Pending Balance)"
        } else {
            "स्थिती: पूर्ण जमा (PAID)"
        }
        canvas.drawText(balanceLabel, left, y, pendingPaint)
        canvas.drawText(BillingCalculator.formatInr(bill.pendingAmount), right, y, pendingRightPaint)

        // Footer
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(107, 87, 76)
            textSize = 12f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawLine(left, height - 92f, right, height - 92f, dividerPaint)
        canvas.drawText("धन्यवाद! पुन्हा भेट द्या.", center, height - 62f, footerPaint)
        canvas.drawText("Masoor Billing • शुद्ध मराठी हॉटेल बिलिंग", center, height - 42f, footerPaint)
    }

    private class BillPrintAdapter(
        private val context: Context,
        private val bill: BillEntity,
        private val settings: ShopSettingsEntity
    ) : PrintDocumentAdapter() {
        private var pdfDocument: PrintedPdfDocument? = null

        override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes,
            cancellationSignal: CancellationSignal?,
            callback: LayoutResultCallback,
            extras: Bundle?
        ) {
            pdfDocument = PrintedPdfDocument(context, newAttributes)
            if (cancellationSignal?.isCanceled == true) {
                callback.onLayoutCancelled()
                return
            }
            val info = PrintDocumentInfo.Builder("MasoorBill_${bill.billNumber}.pdf")
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(1)
                .build()
            callback.onLayoutFinished(info, true)
        }

        override fun onWrite(
            pages: Array<out PageRange>,
            destination: ParcelFileDescriptor,
            cancellationSignal: CancellationSignal?,
            callback: WriteResultCallback
        ) {
            val doc = pdfDocument ?: return
            val page = doc.startPage(0)
            if (cancellationSignal?.isCanceled == true) {
                callback.onWriteCancelled()
                doc.close()
                pdfDocument = null
                return
            }
            drawBillPage(
                page.canvas,
                page.info.pageWidth,
                page.info.pageHeight,
                bill,
                settings
            )
            doc.finishPage(page)
            try {
                FileOutputStream(destination.fileDescriptor).use { out ->
                    doc.writeTo(out)
                }
                callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (e: Exception) {
                callback.onWriteFailed(e.toString())
            } finally {
                doc.close()
                pdfDocument = null
            }
        }
    }
}

package com.vin.browser.engine

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView

/**
 * "Save as PDF": drives WebView's PrintDocumentAdapter via Android PrintManager
 * allowing the user to select "Save as PDF" to save paginated web pages.
 */
object PdfExporter {

    fun export(context: Context, webView: WebView, pageTitle: String, onDone: (String?) -> Unit) {
        val jobName = sanitize(pageTitle.ifBlank { "page" }).take(50)
        try {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
            if (printManager != null) {
                val adapter = webView.createPrintDocumentAdapter(jobName)
                val attrs = PrintAttributes.Builder()
                    .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                    .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                    .build()
                printManager.print(jobName, adapter, attrs)
                onDone("Opening Print / Save as PDF...")
            } else {
                onDone(null)
            }
        } catch (_: Exception) {
            onDone(null)
        }
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("[^A-Za-z0-9 ()_-]"), "").trim().ifBlank { "page" }
}

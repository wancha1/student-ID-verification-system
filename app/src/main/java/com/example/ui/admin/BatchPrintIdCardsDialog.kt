package com.example.ui.admin

import android.widget.Toast
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.util.Base64
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayOutputStream
import com.example.util.QrCodeGenerator
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.Card
import com.example.model.Student
import com.example.ui.components.PrintableStudentIdCard
import com.example.ui.theme.SchoolPrimary
import com.example.util.ExportUtils


/**
 * Opens Android's real print workflow with one CR80-sized front and back page
 * per student. The QR image encodes the active card payload, not display text.
 */
private fun printStudentCards(
    context: Context,
    students: List<Student>,
    cards: List<Card>
) {
    if (students.isEmpty()) {
        Toast.makeText(context, "There are no student cards to print.", Toast.LENGTH_SHORT).show()
        return
    }

    fun html(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")

    fun qrDataUri(payload: String): String? {
        val bitmap = QrCodeGenerator.generateQrBitmap(
            content = payload,
            size = 360,
            foregroundColor = AndroidColor.BLACK,
            backgroundColor = AndroidColor.WHITE,
            addLtcLogo = false
        ) ?: return null
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        bitmap.recycle()
        return "data:image/png;base64," + Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
    }

    val pages = buildString {
        students.forEach { student ->
            val card = cards.firstOrNull { it.studentId == student.id && it.status.name == "ACTIVE" }
                ?: cards.firstOrNull { it.studentId == student.id }
            val payload = card?.qrPayload?.takeIf { it.isNotBlank() } ?: student.uniqueQrCode
            val qr = qrDataUri(payload)
            val cardId = card?.cardIdentifier ?: "CRD-${student.studentNumber.removePrefix("LTC-").removePrefix("OAK-")}-01"
            append("""
                <section class="face front">
                  <header><span class="crest">LTC</span><div><strong>LIRA TOWN COLLEGE</strong><small>LIRA, UGANDA · STUDENT ID</small></div><span class="year">${html(student.academicYear)}</span></header>
                  <main>
                    <div class="identity"><strong>${html(student.fullName.uppercase())}</strong><span>CLASS: ${html(student.gradeClass)}</span><span>STUDENT NO: ${html(student.studentNumber)}</span><span>CARD: ${html(cardId)}</span></div>
                    ${if (qr != null) "<img class=\"qr\" alt=\"Student verification QR\" src=\"$qr\"/>" else "<div class=\"qr-error\">QR UNAVAILABLE</div>"}
                  </main>
                  <footer><span>${if (student.isDayScholar) "DAY SCHOLAR" else "BOARDER"}</span><span>VALID LTC ID</span></footer>
                </section>
                <section class="face back">
                  <header><span class="crest">LTC</span><div><strong>LIRA TOWN COLLEGE</strong><small>STUDENT ID · REVERSE</small></div></header>
                  <main class="back-copy">
                    <strong>THIS CARD IS SCHOOL PROPERTY</strong>
                    <span>Issued to: ${html(student.fullName)}</span>
                    <span>Student number: ${html(student.studentNumber)}</span>
                    <span>Academic year: ${html(student.academicYear)}</span>
                    <p>Present this card when requested by authorized LTC staff. If found, please return it to Lira Town College, Lira, Uganda.</p>
                  </main>
                  <footer><span>LTC AT THE CENTRE</span><span>UNAUTHORIZED ALTERATION INVALIDATES CARD</span></footer>
                </section>
            """.trimIndent())
        }
    }

    val document = """
        <!doctype html><html><head><meta charset="utf-8">
        <style>
          @page { size: 85.6mm 53.98mm; margin: 0; }
          * { box-sizing: border-box; }
          html, body { margin: 0; padding: 0; width: 85.6mm; }
          body { font-family: Arial, Helvetica, sans-serif; color: #142236; }
          .face { width: 85.6mm; height: 53.98mm; overflow: hidden; position: relative; page-break-after: always; break-after: page; border: .25mm solid #1e3a8a; border-radius: 2mm; background: #fff; }
          header { height: 11mm; display: flex; align-items: center; gap: 2mm; padding: 1.5mm 2.5mm; color: white; background: #102941; border-bottom: .7mm solid #d7ad53; }
          header div { display: flex; flex-direction: column; min-width: 0; flex: 1; }
          header strong { font-size: 8pt; letter-spacing: .25pt; white-space: nowrap; }
          header small { font-size: 5pt; color: #f4d78f; margin-top: .5mm; }
          .crest { width: 7mm; height: 7mm; border-radius: 50%; background: #e8c77a; color: #102941; font-size: 7pt; font-weight: bold; display: flex; align-items: center; justify-content: center; }
          .year { font-size: 6pt; color: #f4d78f; }
          main { height: 35mm; padding: 2mm 2.5mm; display: flex; align-items: center; gap: 2mm; }
          .identity { display: flex; flex-direction: column; gap: 1.4mm; min-width: 0; flex: 1; }
          .identity strong { font-size: 7.5pt; line-height: 1.1; overflow-wrap: anywhere; }
          .identity span { font-size: 5.5pt; overflow-wrap: anywhere; }
          .qr { width: 23mm; height: 23mm; object-fit: contain; flex-shrink: 0; }
          .qr-error { width: 23mm; font-size: 5pt; }
          footer { height: 6mm; position: absolute; bottom: 0; left: 0; right: 0; background: #102941; color: #f4d78f; display: flex; align-items: center; justify-content: space-between; padding: 1mm 2.5mm; font-size: 4.5pt; }
          .back { background: #f8fafc; }
          .back main { display: block; padding: 3mm; }
          .back-copy { font-size: 6pt; line-height: 1.35; }
          .back-copy strong, .back-copy span { display: block; margin-bottom: 1.5mm; }
          .back-copy p { font-size: 5.5pt; margin-top: 2mm; }
        </style></head><body>$pages</body></html>
    """.trimIndent()

    try {
        val webView = WebView(context)
        webView.setBackgroundColor(AndroidColor.WHITE)
        webView.settings.javaScriptEnabled = false
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                val attributes = PrintAttributes.Builder()
                    .setMediaSize(PrintAttributes.MediaSize("LTC_CR80", "LTC CR80 85.60 x 53.98 mm", 3370, 2125))
                    .setResolution(PrintAttributes.Resolution("LTC_300_DPI", "300 dpi", 300, 300))
                    .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                    .build()
                printManager.print(
                    "LTC Student ID Cards",
                    view.createPrintDocumentAdapter("LTC Student ID Cards"),
                    attributes
                )
                Toast.makeText(context, "Print preview opened. Check actual size and duplex settings before printing.", Toast.LENGTH_LONG).show()
            }
        }
        webView.loadDataWithBaseURL(null, document, "text/html", "UTF-8", null)
    } catch (error: Exception) {
        Toast.makeText(context, "Could not open print preview: ${error.localizedMessage ?: "unknown error"}", Toast.LENGTH_LONG).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchPrintIdCardsDialog(
    students: List<Student>,
    cards: List<Card> = emptyList(),
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isSimulatingPrint by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Batch ID Card Print Studio", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text(
                                "${students.size} student badges formatted for print",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                        }
                    },
                    actions = {
                        Button(
                            onClick = {
                                printStudentCards(context, students, cards)
                            },
                            enabled = students.isNotEmpty(),
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .testTag("button_print_all_badges")
                        ) {
                            Icon(imageVector = Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Print All (${students.size})")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            },
            modifier = modifier.fillMaxSize()
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // Info Banner
                Surface(
                    color = SchoolPrimary.copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = SchoolPrimary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Standard CR-80 High-Security PVC Dimensions. High-density 2D Barcode with tamper-evident Lira Town College (LTC) branding.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SchoolPrimary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    items(
                        items = students,
                        key = { it.id }
                    ) { student ->
                        val matchingCard = cards.firstOrNull { it.studentId == student.id }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "${student.fullName} • ${student.studentNumber}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                            PrintableStudentIdCard(
                                student = student,
                                card = matchingCard,
                                modifier = Modifier.fillMaxWidth(0.95f)
                            )
                        }
                    }
                }
            }
        }
    }
}

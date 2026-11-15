package com.adi.bitlifesaveeditor

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.security.MessageDigest

private val Bg = Color(0xFF090B12)
private val Panel = Color(0xFF151927)
private val Accent = Color(0xFF9B7BFF)
private val Muted = Color(0xFF9AA3B7)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Bg, surface = Panel)) { EditorHome() } }
    }
}

@Composable
private fun EditorHome() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var fileName by remember { mutableStateOf("Belum ada save dipilih") }
    var report by remember { mutableStateOf("Import savedLife.data untuk mulai pemeriksaan.") }
    var size by remember { mutableStateOf(0L) }
    var sha by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            selectedUri = uri
            busy = true
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val bytes = input.readBytes()
                    size = bytes.size.toLong()
                    fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "BitLife save"
                    sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
                    val ascii = bytes.toString(Charsets.ISO_8859_1)
                    val markers = listOf("Life3", "SimFinances", "BankBalance", "Att_money", "Hero", "World", "Father", "Mother", "Sibling", "Child", "Lover")
                    val found = markers.filter { ascii.contains(it) }
                    val header = bytes.take(48).joinToString(" ") { "%02X".format(it) }
                    report = buildString {
                        appendLine("Pemeriksaan read-only selesai.")
                        appendLine("Ukuran: ${bytes.size} byte (${"%.2f".format(bytes.size / 1024.0 / 1024.0)} MiB)")
                        appendLine("SHA-256: $sha")
                        appendLine("Marker yang ditemukan: ${if (found.isEmpty()) "tidak ada" else found.joinToString(", ")}")
                        appendLine("Header 48 byte: $header")
                        appendLine()
                        append("Catatan: marker hanya menunjukkan teks yang tertanam di binary; ini belum memetakan nilai saldo atau offset aman.")
                    }
                } ?: run { report = "Gagal membaca file." }
            } catch (e: Exception) {
                report = "Gagal membaca: ${e.message ?: e.javaClass.simpleName}"
            } finally { busy = false }
        }
    }

    val exportCopy = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null && selectedUri != null) {
            try {
                val input = context.contentResolver.openInputStream(selectedUri!!)
                val output = context.contentResolver.openOutputStream(uri)
                if (input != null && output != null) input.use { src -> output.use { dst -> src.copyTo(dst) } }
                report += "\n\nSalinan berhasil diekspor. File sumber tidak diubah."
            } catch (e: Exception) { report += "\n\nEkspor gagal: ${e.message}" }
        }
    }

    Column(Modifier.fillMaxSize().background(Bg).verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("ADI LABS", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
        Spacer(Modifier.height(8.dp))
        Text("BitLife Save Editor", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Text("ANDROID EDITION · OFFLINE", color = Muted, fontSize = 11.sp, letterSpacing = 1.5.sp)
        Spacer(Modifier.height(22.dp))
        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(18.dp)) {
                Text("SAVE FILE", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(fileName, color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(if (size > 0) "$size byte" else "Pilih file savedLife.data", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(14.dp))
                Button(onClick = { openFile.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text("Pilih Save BitLife")
                }
                OutlinedButton(onClick = { if (selectedUri != null) exportCopy.launch("savedLife_backup.data") }, enabled = selectedUri != null, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Save, null); Spacer(Modifier.width(8.dp)); Text("Ekspor Salinan / Backup")
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("EDITOR MODULES", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        Spacer(Modifier.height(8.dp))
        listOf("Money Editor" to "Belum aktif — perlu parser BinaryFormatter terverifikasi", "Age & Character Stats" to "Menunggu pemetaan struktur save", "Family & Relationships" to "Menunggu pemetaan struktur save", "Career, Education & Assets" to "Menunggu pemetaan struktur save").forEach { (title, subtitle) ->
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Text(title, color = Color.White, fontWeight = FontWeight.Bold)
                    Text(subtitle, color = Muted, fontSize = 11.sp)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("ANALYSIS REPORT", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(if (busy) "Membaca file…" else report, color = Color(0xFFD9DEEA), fontSize = 12.sp, lineHeight = 18.sp)
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("Alpha 0.1 · Read-only build · Tidak mengubah save asli", color = Muted, fontSize = 10.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

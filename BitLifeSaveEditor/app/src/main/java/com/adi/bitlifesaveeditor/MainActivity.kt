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

private data class MarkerHit(val name: String, val offset: Int, val context: String, val hex: String)

private fun findBytes(data: ByteArray, pattern: ByteArray): List<Int> {
    if (pattern.isEmpty() || pattern.size > data.size) return emptyList()
    val hits = mutableListOf<Int>()
    var i = 0
    while (i <= data.size - pattern.size) {
        var match = true
        for (j in pattern.indices) {
            if (data[i + j] != pattern[j]) { match = false; break }
        }
        if (match) {
            hits += i
            i += pattern.size
        } else i++
    }
    return hits
}

private data class NumericHit(val format: String, val offset: Int, val hex: String, val context: String)

private fun findNumericCandidates(data: ByteArray, target: Long): List<NumericHit> {
    val patterns = listOf(
        "Int32 LE" to byteArrayOf(0x00, 0xCA.toByte(), 0x9A.toByte(), 0x3B),
        "Int64 LE" to byteArrayOf(0x00, 0xCA.toByte(), 0x9A.toByte(), 0x3B, 0, 0, 0, 0),
        "Float32 1e9" to java.nio.ByteBuffer.allocate(4).order(java.nio.ByteOrder.LITTLE_ENDIAN).putFloat(target.toFloat()).array(),
        "Float64 1e9" to java.nio.ByteBuffer.allocate(8).order(java.nio.ByteOrder.LITTLE_ENDIAN).putDouble(target.toDouble()).array(),
        "ASCII decimal" to target.toString().toByteArray(Charsets.US_ASCII)
    )
    val found = mutableListOf<NumericHit>()
    for ((label, pattern) in patterns) {
        for (offset in findBytes(data, pattern).take(50)) {
            val start = (offset - 12).coerceAtLeast(0)
            val end = (offset + pattern.size + 20).coerceAtMost(data.size)
            val around = data.copyOfRange(start, end)
            val printable = around.map { b ->
                val c = b.toInt() and 0xff
                if (c in 32..126) c.toChar() else '.'
            }.joinToString("")
            val hex = data.copyOfRange(offset, (offset + pattern.size).coerceAtMost(data.size))
                .joinToString(" ") { "%02X".format(it.toInt() and 0xff) }
            found += NumericHit(label, offset, hex, printable)
        }
    }
    return found.distinctBy { "${it.format}:${it.offset}" }.sortedBy { it.offset }
}

private fun analyzeBinary(data: ByteArray): Pair<List<MarkerHit>, String> {
    val names = listOf(
        "BankBalance", "SimFinances", "Att_money", "Life3", "Hero", "World",
        "Father", "Mother", "Sibling", "Child", "Lover",
        "_HouseArray", "_CarArray", "_AircraftArray", "_WatercraftArray"
    )
    val hits = mutableListOf<MarkerHit>()
    for (name in names) {
        val pattern = name.toByteArray(Charsets.US_ASCII)
        for (offset in findBytes(data, pattern).take(20)) {
            val start = (offset - 24).coerceAtLeast(0)
            val end = (offset + pattern.size + 40).coerceAtMost(data.size)
            val slice = data.copyOfRange(start, end)
            val printable = slice.map { b ->
                val c = b.toInt() and 0xff
                if (c in 32..126) c.toChar() else '.'
            }.joinToString("")
            val hex = slice.take(48).joinToString(" ") { "%02X".format(it.toInt() and 0xff) }
            hits += MarkerHit(name, offset, printable, hex)
        }
    }
    val numericHits = findNumericCandidates(data, 1_000_000_000L)
    val summary = buildString {
        appendLine("Binary scan selesai.")
        appendLine("Total ukuran: ${data.size} byte")
        appendLine("Rentang byte: 0x00000000–0x${(data.size - 1).coerceAtLeast(0).toString(16).uppercase()}")
        appendLine("Signature awal: " + data.take(16).joinToString(" ") { "%02X".format(it.toInt() and 0xff) })
        appendLine("Hit marker: ${hits.size}")
        appendLine()
        appendLine("PENTING: offset di bawah adalah lokasi teks field, bukan otomatis lokasi nilai saldo.")
        appendLine("Belum ada nilai yang diubah.")
        appendLine()
        appendLine("PEMERIKSAAN ANGKA PEMBANDING: 1.000.000.000")
        appendLine("Kandidat representasi angka: ${numericHits.size}")
        appendLine("Format diuji: Int32 LE, Int64 LE, Float32, Float64, dan ASCII.")
        appendLine("CATATAN: kecocokan angka belum membuktikan bahwa itu BankBalance.")
        numericHits.take(40).forEach { hit ->
            appendLine()
            appendLine("${hit.format} · Offset 0x${hit.offset.toString(16).uppercase()} (${hit.offset})")
            appendLine("HEX: ${hit.hex}")
            appendLine("Konteks: ${hit.context}")
        }
        if (numericHits.isEmpty()) appendLine("Tidak ditemukan representasi langsung angka pembanding.")
    }
    return hits.sortedBy { it.offset } to summary
}

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
    var hits by remember { mutableStateOf<List<MarkerHit>>(emptyList()) }
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
                    val result = analyzeBinary(bytes)
                    val foundHits = result.first
                    hits = foundHits
                    report = buildString {
                        append(result.second)
                        appendLine("SHA-256: $sha")
                        appendLine()
                        appendLine("Jumlah marker unik: ${foundHits.map { it.name }.distinct().size}")
                        append("Sumber dibaca secara read-only.")
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
        Text("ANALYSIS REPORT", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        Spacer(Modifier.height(8.dp))
        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(16.dp)) {
            Text(if (busy) "Menganalisis binary…" else report, modifier = Modifier.padding(16.dp), color = Color(0xFFD9DEEA), fontSize = 12.sp, lineHeight = 18.sp)
        }
        Spacer(Modifier.height(16.dp))
        Text("FIELD MARKERS · ${hits.size}", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        hits.forEach { hit ->
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(hit.name, color = Color.White, fontWeight = FontWeight.Bold)
                    Text("Offset: 0x${hit.offset.toString(16).uppercase()} (${hit.offset})", color = Accent, fontSize = 11.sp)
                    Text(hit.context, color = Muted, fontSize = 10.sp, lineHeight = 14.sp)
                    Text("HEX: ${hit.hex}", color = Color(0xFFD9DEEA), fontSize = 9.sp, lineHeight = 13.sp)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("Alpha 0.2 · Binary marker analysis · Read-only · Save asli aman", color = Muted, fontSize = 10.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

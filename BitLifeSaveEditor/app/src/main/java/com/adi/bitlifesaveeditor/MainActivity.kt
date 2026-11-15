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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

private val Bg = Color(0xFF090B12)
private val Panel = Color(0xFF151927)
private val Accent = Color(0xFF9B7BFF)
private val Muted = Color(0xFF9AA3B7)
private val Good = Color(0xFF69D6A4)

private data class BalanceSlot(val offset: Int, val value: Double, val markerOffset: Int)

// Accept plain digits and common grouped formats such as 9,876,543 or 9.876.543.
private fun parseBalanceAmount(raw: String): Double? {
    val value = raw.trim().replace(" ", "")
    if (value.isEmpty()) return null
    val normalized = when {
        Regex("^[0-9]{1,3}(,[0-9]{3})+(\\\\.[0-9]+)?$").matches(value) -> value.replace(",", "")
        Regex("^[0-9]{1,3}(\\\\.[0-9]{3})+(,[0-9]+)?$").matches(value) -> value.replace(".", "").replace(",", ".")
        value.count { it == ',' } > 1 -> value.replace(",", "")
        value.contains(',') && !value.contains('.') -> value.replace(',', '.')
        else -> value
    }
    return normalized.toDoubleOrNull()
}

private fun findBytes(data: ByteArray, pattern: ByteArray): List<Int> {
    if (pattern.isEmpty() || pattern.size > data.size) return emptyList()
    val hits = mutableListOf<Int>()
    var i = 0
    while (i <= data.size - pattern.size) {
        var match = true
        for (j in pattern.indices) {
            if (data[i + j] != pattern[j]) { match = false; break }
        }
        if (match) { hits += i; i += pattern.size } else i++
    }
    return hits
}

// Experimental locator: candidate observed in two compared saves; not a complete save parser.
private fun detectBalanceSlot(data: ByteArray): BalanceSlot? {
    val marker = "SimBankLoan".toByteArray(Charsets.US_ASCII)
    val matches = findBytes(data, marker).mapNotNull { markerOffset ->
        val offset = markerOffset + 19
        if (offset < 0 || offset + 8 > data.size) return@mapNotNull null
        val value = ByteBuffer.wrap(data, offset, 8).order(ByteOrder.LITTLE_ENDIAN).double
        if (value.isFinite() && value >= 0.0 && value <= 1.0e18) BalanceSlot(offset, value, markerOffset) else null
    }
    return matches.distinctBy { it.offset }.singleOrNull()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Bg, surface = Panel)) {
                EditorHome()
            }
        }
    }
}

@Composable
private fun FeatureCard(
    title: String,
    description: String,
    status: String,
    active: Boolean = false,
    onClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(16.dp),
        onClick = onClick,
        enabled = active
    ) {
        Row(
            Modifier.fillMaxWidth().padding(15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.height(3.dp))
                Text(description, color = Muted, fontSize = 11.sp)
            }
            Text(
                status,
                color = if (active) Good else Muted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun EditorHome() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var fileName by remember { mutableStateOf("Belum ada save dipilih") }
    var size by remember { mutableStateOf(0L) }
    var sourceBytes by remember { mutableStateOf<ByteArray?>(null) }
    var editedBytes by remember { mutableStateOf<ByteArray?>(null) }
    var balanceSlot by remember { mutableStateOf<BalanceSlot?>(null) }
    var balanceInput by remember { mutableStateOf("") }
    var editStatus by remember { mutableStateOf("Pilih save untuk mulai mengedit.") }
    var expandedFeature by remember { mutableStateOf("Uang") }
    var busy by remember { mutableStateOf(false) }

    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            selectedUri = uri
            busy = true
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val bytes = input.readBytes()
                    sourceBytes = bytes.copyOf()
                    editedBytes = null
                    size = bytes.size.toLong()
                    fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "BitLife save"
                    balanceSlot = detectBalanceSlot(bytes)
                    balanceInput = balanceSlot?.value?.toString() ?: ""
                    editStatus = if (balanceSlot != null) {
                        "Save siap. Periksa saldo yang terdeteksi sebelum menerapkan."
                    } else {
                        "Slot saldo tidak ditemukan secara unik. Fitur uang dikunci untuk mencegah salah edit."
                    }
                } ?: run { editStatus = "File gagal dibaca." }
            } catch (e: Exception) {
                editStatus = "Gagal membuka save: ${e.message ?: e.javaClass.simpleName}"
            } finally { busy = false }
        }
    }

    val exportCopy = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val source = sourceBytes
        if (uri != null && source != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { it.write(source) }
                editStatus = "Backup berhasil diekspor. Save asli tetap aman."
            } catch (e: Exception) { editStatus = "Backup gagal: ${e.message}" }
        }
    }

    val exportEdited = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val bytes = editedBytes
        if (uri != null && bytes != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                editStatus = "Save hasil edit berhasil diekspor."
            } catch (e: Exception) { editStatus = "Ekspor gagal: ${e.message}" }
        }
    }

    Column(
        Modifier.fillMaxSize().background(Bg).verticalScroll(rememberScrollState()).padding(18.dp)
    ) {
        Text("ADI LABS", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
        Spacer(Modifier.height(7.dp))
        Text("BitLife Save Editor", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
        Text("EDIT YOUR LIFE · ANDROID OFFLINE", color = Muted, fontSize = 10.sp, letterSpacing = 1.2.sp)
        Spacer(Modifier.height(20.dp))

        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("SAVE GAME", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
                Spacer(Modifier.height(7.dp))
                Text(fileName, color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(if (size > 0) "$size byte" else "Import savedLife.data untuk membuka fitur editor", color = Muted, fontSize = 11.sp)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { openFile.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.FolderOpen, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Pilih Save BitLife")
                }
                OutlinedButton(
                    onClick = { exportCopy.launch("savedLife_backup.data") },
                    enabled = sourceBytes != null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Save, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Backup Save")
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("FITUR EDITOR", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
        Spacer(Modifier.height(9.dp))

        FeatureCard("💰  Uang", "Atur saldo karakter", "TERSEDIA", active = true) {
            expandedFeature = if (expandedFeature == "Uang") "" else "Uang"
        }
        if (expandedFeature == "Uang") {
            Spacer(Modifier.height(8.dp))
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    val slot = balanceSlot
                    if (slot == null) {
                        Text("Import save dengan struktur yang dikenali untuk mengaktifkan editor uang.", color = Muted, fontSize = 12.sp)
                    } else {
                        Text("Saldo terdeteksi", color = Muted, fontSize = 10.sp)
                        Text(String.format(Locale.US, "%,.2f", slot.value), color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                        Text("Kandidat field · offset 0x${slot.offset.toString(16).uppercase(Locale.US)}", color = Muted, fontSize = 10.sp)
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = balanceInput,
                            onValueChange = { balanceInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Saldo baru") },
                            placeholder = { Text("2000000000") }
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                val amount = parseBalanceAmount(balanceInput)
                                val original = sourceBytes
                                if (amount == null || !amount.isFinite() || amount < 0.0 || amount > 1.0e18 || original == null) {
                                    editStatus = "Nominal tidak valid."
                                } else {
                                    val patched = original.copyOf()
                                    ByteBuffer.wrap(patched, slot.offset, 8).order(ByteOrder.LITTLE_ENDIAN).putDouble(amount)
                                    editedBytes = patched
                                    editStatus = "Saldo diubah pada salinan. Ekspor save hasil edit untuk menyimpan."
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Terapkan perubahan") }
                        OutlinedButton(
                            onClick = { exportEdited.launch("savedLife_edited.data") },
                            enabled = editedBytes != null,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Ekspor Save Hasil Edit") }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Status: $editStatus", color = Muted, fontSize = 10.sp)
                    Text("Editor uang masih eksperimental. Selalu uji file hasil edit; file asli tidak ditimpa.", color = Color(0xFFFFC56E), fontSize = 10.sp)
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        FeatureCard("🎂  Usia", "Ubah umur dan tahun kehidupan", "SEGERA HADIR")
        Spacer(Modifier.height(8.dp))
        FeatureCard("📊  Statistik", "Health, happiness, looks, smarts", "SEGERA HADIR")
        Spacer(Modifier.height(8.dp))
        FeatureCard("👨‍👩‍👧  Keluarga", "Orang tua, pasangan, anak, relasi", "SEGERA HADIR")
        Spacer(Modifier.height(8.dp))
        FeatureCard("💼  Karier", "Pekerjaan, pendidikan, dan gaji", "SEGERA HADIR")
        Spacer(Modifier.height(8.dp))
        FeatureCard("🏠  Aset", "Rumah, mobil, pesawat, kapal", "SEGERA HADIR")

        Spacer(Modifier.height(18.dp))
        if (busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
        }
        Text(editStatus, color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
        Spacer(Modifier.height(12.dp))
        Text("ADI LABS · ALPHA 0.6 · FEATURE DASHBOARD", color = Muted, fontSize = 9.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

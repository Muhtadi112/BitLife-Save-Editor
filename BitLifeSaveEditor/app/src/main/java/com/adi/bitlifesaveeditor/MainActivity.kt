package com.adi.bitlifesaveeditor

import android.content.Context
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
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Locale

private val Bg = Color(0xFF090B12)
private val Panel = Color(0xFF151927)
private val Accent = Color(0xFF9B7BFF)
private val Muted = Color(0xFF9AA3B7)
private val Good = Color(0xFF69D6A4)

private const val MAX_SAVE_BYTES = 64 * 1024 * 1024

private fun readBounded(input: InputStream): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    var total = 0
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        if (count == 0) continue
        total += count
        require(total <= MAX_SAVE_BYTES) { "Ukuran save melewati batas 64 MiB." }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}

private fun writeAndReadBack(context: Context, uri: Uri, expected: ByteArray): ByteArray? {
    val output = context.contentResolver.openOutputStream(uri) ?: return null
    output.use { it.write(expected) }
    return context.contentResolver.openInputStream(uri)?.use { readBounded(it) }
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
    var fileName by remember { mutableStateOf("Belum ada save dipilih") }
    var size by remember { mutableStateOf(0L) }
    var sourceBytes by remember { mutableStateOf<ByteArray?>(null) }
    var sourceUriString by remember { mutableStateOf<String?>(null) }
    var editedBytes by remember { mutableStateOf<ByteArray?>(null) }
    var balanceSlot by remember { mutableStateOf<BalanceSlot?>(null) }
    var selectedBalancePreset by remember { mutableStateOf("2 Miliar") }
    var customBalanceText by remember { mutableStateOf("2000000000") }
    var editedBalanceAmount by remember { mutableStateOf<Double?>(null) }
    var sourceHash by remember { mutableStateOf("") }
    var editedHash by remember { mutableStateOf("") }
    var presetMenuExpanded by remember { mutableStateOf(false) }
    val balancePresets = listOf(
        "2 Miliar" to 2.0e9,
        "5 Miliar" to 5.0e9,
        "10 Miliar" to 1.0e10,
        "50 Miliar" to 5.0e10,
        "100 Miliar" to 1.0e11,
        "500 Miliar" to 5.0e11,
        "1 Triliun" to 1.0e12,
        "10 Triliun" to 1.0e13,
        "100 Triliun" to 1.0e14,
        "1 Kuadriliun" to 1.0e15,
        "10 Kuadriliun" to 1.0e16,
        "100 Kuadriliun" to 1.0e17,
        "1 Kuantiliun" to 1.0e18
    )
    var editStatus by remember { mutableStateOf("Pilih save untuk mulai mengedit.") }
    var expandedFeature by remember { mutableStateOf("Uang") }
    var busy by remember { mutableStateOf(false) }

    fun invalidateEditedCopy(message: String) {
        editedBytes = null
        editedBalanceAmount = null
        editedHash = ""
        editStatus = message
    }

    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            // Clear the previous selection first so a failed import cannot leave
            // an older save silently available for export.
            sourceBytes = null
            sourceUriString = null
            editedBytes = null
            balanceSlot = null
            editedBalanceAmount = null
            sourceHash = ""
            editedHash = ""
            size = 0L
            fileName = "Membaca save..."
            busy = true
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { readBounded(it) }
                    ?: throw IllegalStateException("File tidak dapat dibaca.")
                sourceUriString = uri.toString()
                sourceBytes = bytes.copyOf()
                sourceHash = BalancePatchEngine.sha256(bytes)
                size = bytes.size.toLong()
                fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "BitLife save"
                balanceSlot = BalancePatchEngine.inspect(bytes)
                selectedBalancePreset = "2 Miliar"
                customBalanceText = "2000000000"
                editStatus = if (balanceSlot != null) {
                    "Save siap. Periksa saldo yang terdeteksi sebelum menerapkan."
                } else {
                    "Header NRBF atau kandidat saldo tidak lolos validasi. Fitur uang dikunci untuk mencegah salah edit."
                }
            } catch (e: Exception) {
                sourceBytes = null
                sourceUriString = null
                balanceSlot = null
                sourceHash = ""
                size = 0L
                fileName = "Save gagal dibaca"
                editStatus = "Gagal membuka save: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                busy = false
            }
        }
    }

    val exportCopy = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val source = sourceBytes
        if (uri != null && source != null) {
            if (!ExportTargetGuard.canExport(sourceUriString, uri.toString())) {
                editStatus = "Backup dibatalkan: URI sumber tidak tersedia atau tujuan sama dengan save asli. Pilih file baru."
            } else try {
                val readBack = writeAndReadBack(context, uri, source)
                if (readBack == null || !ExportVerifier.verifyBackup(source, readBack)) {
                    editStatus = "Backup tidak dapat diverifikasi saat dibaca ulang. Jangan gunakan file ini."
                } else {
                    val verifiedHash = BalancePatchEngine.sha256(readBack)
                    editStatus = "Backup berhasil diekspor dan diverifikasi byte demi byte. SHA-256: ${verifiedHash.take(16)}…"
                }
            } catch (e: Exception) {
                editStatus = "Backup gagal diverifikasi: ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    val exportEdited = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val bytes = editedBytes
        val original = sourceBytes
        val slot = balanceSlot
        val expectedAmount = editedBalanceAmount
        if (uri != null && bytes != null && original != null && slot != null && expectedAmount != null) {
            if (!ExportTargetGuard.canExport(sourceUriString, uri.toString())) {
                editStatus = "Ekspor dibatalkan: URI sumber tidak tersedia atau tujuan sama dengan save asli. Pilih file baru."
            } else try {
                if (!BalancePatchEngine.verifyPatched(original, bytes, slot, expectedAmount)) {
                    editStatus = "Verifikasi sebelum ekspor gagal. File tidak ditulis."
                } else {
                    val readBack = writeAndReadBack(context, uri, bytes)
                    if (readBack == null ||
                        !ExportVerifier.verifyEdited(original, bytes, readBack, slot, expectedAmount) ||
                        BalancePatchEngine.sha256(readBack) != editedHash
                    ) {
                        editStatus = "Ekspor tidak dapat diverifikasi saat dibaca ulang. Jangan gunakan atau pasang file ini."
                    } else {
                        editStatus = "Salinan ekspor lolos verifikasi byte sebelum dan sesudah penulisan. Penerimaan BitLife belum diuji. SHA-256: ${editedHash.take(16)}…"
                    }
                }
            } catch (e: Exception) {
                editStatus = "Ekspor gagal diverifikasi: ${e.message ?: e.javaClass.simpleName}"
            }
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
                if (sourceHash.isNotEmpty()) Text("SHA-256 sumber: ${sourceHash.take(16)}…", color = Muted, fontSize = 9.sp)
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

        FeatureCard("💰  Uang", "Edit kandidat saldo bank (BankBalance)", "EKSPERIMENTAL", active = true) {
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
                        Text("Kandidat nilai saldo", color = Muted, fontSize = 10.sp)
                        Text(String.format(Locale.US, "%,.2f", slot.value), color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                        Text("BankBalance (kandidat) · Finances · offset 0x${slot.offset.toString(16).uppercase(Locale.US)}", color = Muted, fontSize = 10.sp)
                        Spacer(Modifier.height(10.dp))
                        Text("Pilih nominal saldo", color = Muted, fontSize = 10.sp)
                        Spacer(Modifier.height(5.dp))
                        Box(Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { presetMenuExpanded = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(selectedBalancePreset, modifier = Modifier.weight(1f))
                                Text("▾")
                            }
                            DropdownMenu(
                                expanded = presetMenuExpanded,
                                onDismissRequest = { presetMenuExpanded = false },
                                modifier = Modifier.heightIn(max = 360.dp)
                            ) {
                                balancePresets.forEach { (label, amount) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            selectedBalancePreset = label
                                            customBalanceText = String.format(Locale.US, "%.0f", amount)
                                            invalidateEditedCopy("Preset berubah. Terapkan ulang sebelum ekspor.")
                                            presetMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                        OutlinedTextField(
                            value = customBalanceText,
                            onValueChange = {
                                customBalanceText = it
                                invalidateEditedCopy("Nominal berubah. Terapkan ulang sebelum ekspor.")
                            },
                            label = { Text("Nominal saldo") },
                            placeholder = { Text("Contoh: 2000000000") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        val parsedAmount = BalanceAmountParser.parse(customBalanceText)
                        val amountValid = parsedAmount != null && parsedAmount.isFinite() && parsedAmount >= 0.0 && parsedAmount <= 1.0e18
                        Text(
                            if (amountValid) "Target: " + String.format(Locale.US, "%,.2f", parsedAmount!!)
                            else "Nominal harus 0–1 kuintiliun dan tidak boleh kehilangan presisi Double.",
                            color = if (amountValid) Good else Color(0xFFFFC56E),
                            fontSize = 11.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                val amount = BalanceAmountParser.parse(customBalanceText)
                                val original = sourceBytes
                                val patched = if (amount != null && original != null) {
                                    BalancePatchEngine.patch(original, slot, amount)
                                } else null
                                if (patched == null || amount == null) {
                                    editedBytes = null
                                    editedBalanceAmount = null
                                    editedHash = ""
                                    editStatus = "Patch dibatalkan: nominal atau struktur save gagal diverifikasi."
                                } else {
                                    editedBytes = patched
                                    editedBalanceAmount = amount
                                    editedHash = BalancePatchEngine.sha256(patched)
                                    editStatus = "Salinan kandidat berhasil dipatch dan lolos pemeriksaan byte. Penerimaan BitLife belum diuji; save asli tetap utuh."
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Terapkan perubahan ke salinan") }
                        OutlinedButton(
                            onClick = { exportEdited.launch("savedLife_edited.data") },
                            enabled = editedBytes != null,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Ekspor Save Hasil Edit") }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Status: $editStatus", color = Muted, fontSize = 10.sp)
                    if (editedHash.isNotEmpty()) Text("SHA-256 hasil: ${editedHash.take(16)}…", color = Muted, fontSize = 9.sp)
                    Text("Patch fixed-layout: memeriksa header NRBF dan metadata Life.Finances/SimFinances/BankBalance. Pemetaan field dicocokkan dengan decoder NRBF aman pada dua revisi save; ini belum parser/serializer penuh dan belum menjamin BitLife menerima hasilnya. Selalu uji salinan, jangan timpa save asli.", color = Color(0xFFFFC56E), fontSize = 10.sp)
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
        Text("ADI LABS · ALPHA 0.9.2 · BYTE-CHECKED / GAME TEST PENDING", color = Muted, fontSize = 9.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

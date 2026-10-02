package com.luxwallet.app.feature.review

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.luxwallet.app.core.common.LocalAmountsHidden
import com.luxwallet.app.core.common.privateRupiah
import com.luxwallet.app.core.ui.luxViewModel

@Composable fun NeedsReviewScreen(onTransactionClick: (Long) -> Unit = {}, onManual: () -> Unit = {}) {
    val vm = luxViewModel { NeedsReviewViewModel.create(it) }
    val state by vm.uiState.collectAsState()
    val busy by vm.busy.collectAsState()
    val error by vm.error.collectAsState()
    val message by vm.message.collectAsState()
    var selection by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var deleting by rememberSaveable { mutableStateOf<List<String>?>(null) }
    var retrying by rememberSaveable { mutableStateOf<Long?>(null) }
    val selected = selection.filter { it in state.keys }
    val hidden = LocalAmountsHidden.current
    val dateFormat = remember { java.time.format.DateTimeFormatter.ofPattern("d MMM, HH:mm", java.util.Locale("id", "ID")).withZone(java.time.ZoneId.systemDefault()) }
    val linked = remember(state.observations) { state.observations.filter { it.linkedTransactionId != null }.groupBy { it.linkedTransactionId } }
    fun toggle(key: String) { selection = if (key in selected) selected - key else selected + key }
    Column(Modifier.fillMaxSize()) {
        Surface(shadowElevation = 1.dp) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (selected.isEmpty()) "${state.keys.size} catatan menunggu keputusan" else "${selected.size} catatan dipilih", style = MaterialTheme.typography.bodyMedium)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(enabled = !busy && state.keys.isNotEmpty(), onClick = { selection = if (selected.size == state.keys.size) emptyList() else state.keys }) {
                        Text(if (selected.isNotEmpty() && selected.size == state.keys.size) "Batal pilih" else "Pilih semua")
                    }
                    Button(enabled = !busy && selected.isNotEmpty(), onClick = { deleting = selected.toList() }) { Text(if (busy) "Menghapus…" else "Hapus terpilih (${selected.size})") }
                }
                if (hidden) Text("Angka dalam pesan disamarkan mengikuti mode privasi.", style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(state.transactions, key = { "tx:${it.id}" }) { tx ->
                val key = "tx:${tx.id}"
                val evidence = reviewMessage(linked[tx.id].orEmpty(), tx.note?.takeIf { it.isNotBlank() }
                    ?: listOfNotNull(tx.merchantName, tx.counterpartyName).joinToString(" · ").ifBlank { "Pesan asli tidak tersedia. Buka detail untuk meninjau catatan." })
                ReviewCard(key, privateRupiah(tx.amount),
                    "${state.accountNames[tx.sourceAccountId] ?: "Rekening belum diatur"} · ${dateFormat.format(java.time.Instant.ofEpochMilli(tx.transactionTime))}",
                    state.categoryNames[tx.categoryId] ?: "Kategori belum jelas", privateReviewMessage(evidence, hidden),
                    selected = key in selected, enabled = !busy, onSelect = { toggle(key) },
                    onReview = { onTransactionClick(tx.id) }, onDelete = { deleting = listOf(key) }) {
                    if (tx.reviewReason == com.luxwallet.app.core.model.ReviewReason.POSSIBLE_DUPLICATE) Text("Calon duplikat · belum dihitung", style = MaterialTheme.typography.labelMedium)
                }
            }
            items(state.failed, key = { "obs:${it.id}" }) { item ->
                val key = "obs:${item.id}"
                ReviewCard(key, when (item.sourceApp) {
                    com.luxwallet.app.core.model.SourceApp.MYBCA -> "BCA mobile / myBCA"
                    com.luxwallet.app.core.model.SourceApp.SEABANK -> "SeaBank"
                    com.luxwallet.app.core.model.SourceApp.SHOPEEPAY -> "ShopeePay"
                    com.luxwallet.app.core.model.SourceApp.GOPAY -> "GoPay"
                }, dateFormat.format(java.time.Instant.ofEpochMilli(item.postedAt)), "Belum dikenali · belum masuk saldo",
                    privateReviewMessage(reviewMessage(listOf(item), "Pesan asli tidak tersedia."), hidden),
                    selected = key in selected, enabled = !busy, onSelect = { toggle(key) }, onReview = onManual,
                    onDelete = { deleting = listOf(key) }, reviewLabel = "Catat manual") {
                    TextButton({ retrying = item.id }, enabled = !busy) { Text("Coba baca ulang") }
                }
            }
            if (state.isLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            else if (state.keys.isEmpty()) item { Text("Semua sudah ditinjau.") }
        }
    }
    retrying?.let { id -> AlertDialog(onDismissRequest = { if (!busy) retrying = null },
        title = { Text("Baca ulang pesan ini?") },
        text = { Column {
            Text("Gunakan jika transaksi ini belum kamu catat manual. Jika terbaca, Lumi akan memprosesnya ke riwayat dan saldo. Jangan lanjut jika nominalnya sudah masuk dalam saldo yang kamu koreksi.")
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { TextButton({ vm.retry(id) { retrying = null } }, enabled = !busy) { Text("Belum dicatat, baca ulang") } },
        dismissButton = { TextButton({ retrying = null }, enabled = !busy) { Text("Batal") } }) }
    deleting?.let { snapshot -> AlertDialog(onDismissRequest = { if (!busy) deleting = null },
        title = { Text(if (snapshot.size == 1) "Hapus dari daftar tinjauan?" else "Hapus ${snapshot.size} catatan terpilih?") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Catatan diabaikan. Nominal yang sudah dihitung akan dibalik dari saldo. Histori tetap tersimpan.")
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { TextButton(enabled = !busy, onClick = { vm.dismiss(snapshot) { selection = selection - snapshot.toSet(); deleting = null } }) { Text("Hapus catatan") } },
        dismissButton = { TextButton({ deleting = null }, enabled = !busy) { Text("Batal") } }) }
}

@Composable private fun ReviewCard(key: String, title: String, subtitle: String, status: String, message: String,
    selected: Boolean, enabled: Boolean, onSelect: () -> Unit, onReview: () -> Unit, onDelete: () -> Unit,
    reviewLabel: String = "Tinjau", extra: @Composable () -> Unit = {}) {
    var expanded by rememberSaveable(key) { mutableStateOf(false) }
    var overflow by remember(message) { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Checkbox(selected, { onSelect() }, enabled = enabled, modifier = Modifier.semantics { contentDescription = "Pilih catatan $key" })
            }
            Text(status, style = MaterialTheme.typography.labelMedium)
            HorizontalDivider()
            Text(message, style = MaterialTheme.typography.bodyMedium, maxLines = if (expanded) Int.MAX_VALUE else 4,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, onTextLayout = { if (!expanded) overflow = it.hasVisualOverflow })
            if (overflow || expanded) TextButton({ expanded = !expanded }) { Text(if (expanded) "Ringkas pesan" else "Selengkapnya") }
            extra()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onReview, enabled = enabled) { Text(reviewLabel) }
                TextButton(onDelete, enabled = enabled) { Text("Hapus") }
            }
        }
    }
}

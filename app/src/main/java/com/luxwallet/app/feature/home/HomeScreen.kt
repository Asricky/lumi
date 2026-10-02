package com.luxwallet.app.feature.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.luxwallet.app.LuxWalletApp
import com.luxwallet.app.R
import com.luxwallet.app.core.common.AmountFormat
import com.luxwallet.app.core.ui.component.*
import com.luxwallet.app.core.ui.luxViewModel
import com.luxwallet.app.core.ui.theme.*
import com.luxwallet.app.navigation.LuxDestinations
import kotlinx.coroutines.launch

@Composable fun HomeScreen(onNavigate: (String) -> Unit = {}) {
    val viewModel = luxViewModel { HomeViewModel.create(it) }
    val state by viewModel.uiState.collectAsState()
    val reviewCount by viewModel.reviewCount.collectAsState()
    val planner = luxViewModel { com.luxwallet.app.feature.planner.PlannerViewModel(it) }
    val planState by planner.state.collectAsState()
    val status = planState.status
    val app = LocalContext.current.applicationContext as LuxWalletApp
    val hidden by app.preferences.amountsHidden.collectAsState(initial = true)
    val profileName by app.preferences.profileName.collectAsState(initial = "")
    val mood by app.lumiState.mood.collectAsState()
    val scope = rememberCoroutineScope()
    var showInfo by remember { mutableStateOf(false) }
    fun money(amount: Long) = if (hidden) "********" else AmountFormat.rupiah(amount)
    LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(com.luxwallet.app.core.common.greeting(profileName), style = MaterialTheme.typography.titleLarge)
                    Text("Yuk, lihat kabar uangmu hari ini.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                NotificationIndicator { onNavigate(LuxDestinations.NOTIFICATION_SETTINGS) }
                IconButton({ onNavigate(LuxDestinations.SETTINGS) }) { Icon(Icons.Outlined.Settings, "Pengaturan") }
            }
        }
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(Color(0xFF087A78), Color(0xFF16324F)))).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.AccountBalanceWallet, null, tint = LuxGold)
                    Text("  Kekayaan bersih", color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    IconButton({ scope.launch { app.preferences.setAmountsHidden(!hidden) } }) {
                        Icon(if (hidden) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (hidden) "Tampilkan nominal" else "Sembunyikan nominal", tint = LuxGold)
                    }
                }
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (state.isLoading) "Memuat…" else money(state.netWorth), style = MaterialTheme.typography.headlineLarge)
                    Text("Total aset − kewajiban", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider(Modifier.padding(top = 8.dp))
                    TextButton({ onNavigate(LuxDestinations.ASSETS) }, contentPadding = PaddingValues(0.dp)) { Text("Lihat & atur aset"); Icon(Icons.Outlined.ChevronRight, null, Modifier.size(18.dp)) }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Shortcut("Riwayat", Icons.Outlined.ReceiptLong, Modifier.weight(1f)) { onNavigate(LuxDestinations.TRANSACTIONS) }
                Shortcut("Rencana", Icons.Outlined.Savings, Modifier.weight(1f)) { onNavigate(LuxDestinations.PLANNER) }
                Shortcut("Kalkulator", Icons.Outlined.Calculate, Modifier.weight(1f)) { onNavigate(LuxDestinations.CALCULATOR) }
                Shortcut("Tinjau", Icons.Outlined.FactCheck, Modifier.weight(1f)) { onNavigate(LuxDestinations.NEEDS_REVIEW) }
            }
        }
        if (reviewCount > 0) item {
            TextButton({ onNavigate(LuxDestinations.NEEDS_REVIEW) }) {
                Icon(Icons.Outlined.FactCheck, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                Text("$reviewCount catatan perlu ditinjau")
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Sisa budget hari ini", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        IconButton({ showInfo = true }) { Icon(Icons.Outlined.Info, "Cara menghitung ruang belanja") }
                    }
                    if (status == null || planState.adaptive == null) {
                        Lumi(mood, Modifier.size(64.dp))
                        Text(if (status == null) "Konfirmasi saldo yang tersedia" else "Waktunya memperbarui rencana", style = MaterialTheme.typography.titleLarge)
                        Text("Atur kebutuhan sampai pemasukan tanggal 25 atau 1. Uang yang belum diterima belum bisa dibelanjakan.")
                        planState.interim?.let { InterimBudgetCard(it, hidden) }
                    } else {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(money(planState.adaptive?.safeToSpend ?: 0), Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium)
                            Lumi(mood, Modifier.size(72.dp))
                        }
                        Text("Budget ${money(status.dailyBudget)} − terpakai ${money(status.spentToday)}", style = MaterialTheme.typography.bodyMedium)
                        if ((planState.adaptive?.safeToSpend ?: 0) < (status.dailyBudget - status.spentToday).coerceAtLeast(0)) Text("Sisa disesuaikan dana tersedia agar cukup sampai gajian.", style = MaterialTheme.typography.bodySmall)
                        Text("Sampai ${status.plan.end.format(com.luxwallet.app.feature.planner.planDateFormat)} · transportasi terpisah", style = MaterialTheme.typography.bodySmall)
                        Text("Cadangan transportasi tersisa ${money((status.plan.transportReserve - status.transportSpent).coerceAtLeast(0))}", style = MaterialTheme.typography.bodySmall)
                        if (!hidden) LinearProgressIndicator(progress = { (status.usedPercent / 100).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(),
                            color = if (status.remainingToday < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                        Text(if (hidden) "Penggunaan disembunyikan" else if (status.dailyBudget <= 0 && status.spentToday > 0) "Belanja tanpa budget tersedia · ${money(status.spentToday)}" else "${status.usedPercent.toInt()}% terpakai · ${money(status.spentToday)} dari ${money(status.dailyBudget)}", style = MaterialTheme.typography.bodySmall)
                        if (status.freeRemaining < 0) Text("Alokasi melebihi dana tersedia. Tinjau rencana sebelum belanja.", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton({ onNavigate(LuxDestinations.PLANNER) }) { Text("Atur rencana sampai gajian") }
                }
            }
        }
        item {
            val advice = com.luxwallet.app.engine.MoneyCoach.advise(status, planState.today)
            OutlinedCard(onClick = { onNavigate(LuxDestinations.COACH) }, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("Saran Lumi", style = MaterialTheme.typography.labelMedium); Text(when(mood) {
                        LumiMood.CURIOUS -> "Ada transaksi yang perlu kamu cek"
                        LumiMood.SHOCKED -> "Transaksi terakhir lebih besar dari biasanya"
                        LumiMood.EXCITED -> "Pemasukan baru sudah tercatat"
                        LumiMood.NERVOUS -> if ((status?.usedPercent ?: 0.0) >= 90) "Budget hampir habis, dahulukan kebutuhan utama" else "Budget mulai menipis, cek sebelum belanja lagi"
                        else -> advice.title
                    }, style = MaterialTheme.typography.titleMedium)
                        if (status != null && !status.expired) Text(if (hidden) "Rencana harianmu tersimpan" else "${status.usedPercent.toInt()}% budget terpakai · sisa ${money(planState.adaptive?.safeToSpend ?: 0)}", style = MaterialTheme.typography.bodySmall) }
                    Icon(Icons.Outlined.ChevronRight, "Buka saran Lumi")
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Riwayat transaksi", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                TextButton({ onNavigate(LuxDestinations.TRANSACTIONS) }) { Text("Semua") }
            }
        }
        if (state.recentTransactions.isEmpty()) item {
            OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp)) {
                Text("Belum ada transaksi")
                TextButton({ onNavigate(LuxDestinations.QUICK_ADD) }) { Text("Catat transaksi pertama") }
            } }
        }
        items(state.recentTransactions, key = { it.id }) { tx ->
            TransactionCard(tx, state.accountNames[tx.sourceAccountId], state.accountNames[tx.destinationAccountId],
                state.categoryNames[tx.categoryId], { onNavigate(LuxDestinations.transactionDetail(tx.id)) }, hideAmounts = hidden)
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Sebaran aset", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                IconButton({ scope.launch { app.preferences.setAmountsHidden(!hidden) } }) { Icon(if (hidden) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, "Tampilkan atau sembunyikan aset") }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!hidden && state.assetDistribution.isNotEmpty()) DonutChart(state.assetDistribution, money(state.assetDistribution.sumOf { it.value }.toLong()), "Aset positif")
                    if (hidden) Text("Nominal & proporsi aset disembunyikan", Modifier.padding(20.dp), style = MaterialTheme.typography.bodyMedium)
                    state.assetDistribution.forEach { slice ->
                        Row(Modifier.fillMaxWidth()) { Text(slice.label, Modifier.weight(1f)); Text(money(slice.value.toLong())) }
                    }
                    if (state.assetDistribution.isEmpty()) TextButton({ onNavigate(LuxDestinations.ASSETS) }) { Text("Tambahkan asetmu") }
                }
            }
        }
    }
    if (showInfo) AlertDialog(onDismissRequest = { showInfo = false }, title = { Text("Dari mana angkanya?") },
        text = { Text("Saldo sekarang dikurangi cadangan tagihan, penyangga, bisnis, investasi, dan transportasi yang belum terpakai. Belanja sebelum konfirmasi ditambahkan kembali hanya untuk menentukan budget harian, lalu seluruh belanja hari ini mengurangi budget. Saldo tidak dipotong dua kali. Sisa yang ditampilkan juga dibatasi rekomendasi serta dana bebas yang masih tersedia.\n\nTop-up sendiri tidak mengurangi budget. Tagihan terencana dan Transportasi rutin memakai cadangannya dahulu. Perkiraan pemasukan tidak dihitung sebagai saldo. Lihat rincian pada rencana sampai gajian.") },
        confirmButton = { TextButton({ showInfo = false; onNavigate(LuxDestinations.PLANNER) }) { Text("Lihat rencana") } },
        dismissButton = { TextButton({ showInfo = false }) { Text("Tutup") } })
}
@Composable private fun Shortcut(label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        FilledTonalIconButton(onClick, Modifier.size(48.dp)) { Icon(icon, label) }
        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp))
    }
}

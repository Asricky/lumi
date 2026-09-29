package com.luxwallet.app.feature.calculator

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.luxwallet.app.LuxWalletApp
import com.luxwallet.app.core.common.AmountFormat
import com.luxwallet.app.core.ui.component.MoneyField
import com.luxwallet.app.engine.FinanceCalculator
import com.luxwallet.app.parser.core.AmountParser

@Composable fun CalculatorScreen() {
    var tab by rememberSaveable { mutableStateOf(0) }
    var expression by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var result by rememberSaveable { mutableStateOf<String?>(null) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var initial by rememberSaveable { mutableStateOf("0") }
    var monthly by rememberSaveable { mutableStateOf("1500000") }
    var rate by rememberSaveable { mutableStateOf("6") }
    var years by rememberSaveable { mutableStateOf("10") }
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val app = LocalContext.current.applicationContext as LuxWalletApp
    val accounts by app.accountRepository.observeActiveAccounts().collectAsState(initial = emptyList())
    val assets by app.assetRepository.observeAll().collectAsState(initial = emptyList())
    val debts by app.liabilityRepository.observeAll().collectAsState(initial = emptyList())
    val total = accounts.filter { it.includeInNetWorth }.sumOf { it.currentEstimatedBalance } +
        assets.filter { it.includeInNetWorth }.sumOf { it.currentValue } - debts.sumOf { it.currentOutstanding }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TabRow(tab) { listOf("Hitung aset", "Pertumbuhan").forEachIndexed { i, title -> Tab(tab == i, { tab = i }, text = { Text(title) }) } }
        if (tab == 0) {
            fun insert(key: String) {
                error = null
                val completed = result
                val base = if (completed != null) {
                    if (key in listOf("+", "−", "×", "÷", "%")) TextFieldValue(completed, TextRange(completed.length)) else TextFieldValue("")
                } else expression
                val start = minOf(base.selection.start, base.selection.end)
                val end = maxOf(base.selection.start, base.selection.end)
                val next = base.text.replaceRange(start, end, key)
                if (next.length <= 250) { expression = TextFieldValue(next, TextRange(start + key.length)); result = null }
            }
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(expression, { incoming ->
                        val value = CalculatorFormatting.input(incoming)
                        if (value != null) { expression = value; result = null; error = null }
                        else error = "Gunakan titik untuk ribuan dan koma untuk desimal."
                    }, Modifier.fillMaxWidth(), label = { Text("Perhitungan") }, placeholder = { Text("0") },
                        visualTransformation = CalculatorFormatting,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        textStyle = MaterialTheme.typography.headlineSmall.copy(textAlign = TextAlign.End),
                        shape = RoundedCornerShape(16.dp), maxLines = 3,
                        isError = error != null)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Hasil", style = MaterialTheme.typography.labelMedium)
                        Text(result?.let { CalculatorFormatting.display(it) } ?: "Tekan =", Modifier.weight(1f), textAlign = TextAlign.End,
                            style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            }
            OutlinedButton({
                focus.clearFocus()
                val value = total.toString()
                expression = TextFieldValue(value, TextRange(value.length)); result = null; error = null
            }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) { Text("Pakai kekayaan bersih sebagai nilai awal") }
            listOf(listOf("C", "(", ")", "⌫"), listOf("7", "8", "9", "÷"), listOf("4", "5", "6", "×"),
                listOf("1", "2", "3", "−"), listOf("0", ",", "%", "+")).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { row.forEach { key ->
                    val operator = key in listOf("÷", "×", "−", "+")
                    val utility = key in listOf("C", "(", ")", "⌫")
                    FilledTonalButton(onClick = {
                        focus.clearFocus()
                        when (key) {
                            "C" -> { expression = TextFieldValue(""); result = null; error = null }
                            "⌫" -> {
                                val start = minOf(expression.selection.start, expression.selection.end)
                                val end = maxOf(expression.selection.start, expression.selection.end)
                                val from = if (start == end) (start - 1).coerceAtLeast(0) else start
                                expression = TextFieldValue(expression.text.removeRange(from, end), TextRange(from)); result = null; error = null
                            }
                            else -> insert(key)
                        }
                    }, modifier = Modifier.weight(1f).heightIn(min = 54.dp).semantics {
                        contentDescription = when(key) { "⌫" -> "Hapus satu angka"; "C" -> "Bersihkan perhitungan"; else -> key }
                    }, shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (operator) MaterialTheme.colorScheme.primaryContainer else if (utility) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
                            contentColor = if (operator) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface),
                        contentPadding = PaddingValues(4.dp)) { Text(key, style = MaterialTheme.typography.titleLarge) }
                } }
            }
            Button({ focus.clearFocus(); try { result = FinanceCalculator.evaluate(expression.text).toPlainString().replace('.', ','); error = null }
                catch (e: Exception) { error = e.message ?: "Periksa perhitungan" } }, Modifier.fillMaxWidth().heightIn(min = 54.dp), shape = RoundedCornerShape(18.dp)) {
                Text("=", style = MaterialTheme.typography.headlineSmall)
            }
            Text("Contoh: 1.500.000,50 · Persen berarti ÷100. Perhitungan tidak mengubah saldo.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            MoneyField("Modal awal", initial, { initial = it })
            MoneyField("Setoran tiap akhir bulan", monthly, { monthly = it })
            OutlinedTextField(rate, { rate = it }, Modifier.fillMaxWidth(), label = { Text("Asumsi hasil efektif per tahun (%)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
            OutlinedTextField(years, { years = it }, Modifier.fillMaxWidth(), label = { Text("Durasi (tahun)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
            val projection = runCatching {
                FinanceCalculator.compound(AmountParser.normalizeOrNull(initial)?.toDouble() ?: error("Modal tidak valid"),
                    AmountParser.normalizeOrNull(monthly)?.toDouble() ?: error("Setoran tidak valid"),
                    rate.replace(',', '.').toDouble(), years.toInt())
            }.getOrNull()
            val projectionHidden = com.luxwallet.app.core.common.LocalAmountsHidden.current
            fun projectionMoney(value: Double): String = if (projectionHidden) "********" else "Rp" + CalculatorFormatting.display(java.math.BigDecimal.valueOf(value).setScale(0, java.math.RoundingMode.HALF_UP).toPlainString())
            if (projection != null) Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Simulasi nilai akhir", style = MaterialTheme.typography.titleMedium)
                    Text(projectionMoney(projection.futureValue), style = MaterialTheme.typography.headlineMedium)
                    Text("Total setoran: ${projectionMoney(projection.principal)}")
                    Text("Pertumbuhan: ${projectionMoney(projection.futureValue - projection.principal)}")
                }
            } else Text("Isi nilai yang valid: durasi 1–60 tahun dan hasil −99% hingga 100%.")
            Text("Simulasi, bukan janji hasil. Angka 6% hanya contoh; pajak, biaya, dan inflasi belum dimasukkan. Hasil investasi dapat turun. Perhitungan mengasumsikan hasil diinvestasikan kembali.", style = MaterialTheme.typography.bodySmall)
        }

    }
}

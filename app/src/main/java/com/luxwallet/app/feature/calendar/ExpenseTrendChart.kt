package com.luxwallet.app.feature.calendar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.luxwallet.app.core.common.AmountFormat
import com.luxwallet.app.engine.DailyExpense
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.*

internal fun expenseScale(max: Long): Double {
    if (max <= 0) return 1000.0
    val magnitude = 10.0.pow(floor(log10(max.toDouble())))
    val normalized = max / magnitude
    return (listOf(1.0, 2.0, 5.0, 10.0).first { it >= normalized }) * magnitude
}
internal fun expenseIndex(fraction: Float, count: Int): Int =
    if (count <= 1) 0 else floor(fraction.coerceIn(0f, 1f) * count).toInt().coerceIn(0, count - 1)

@Composable fun ExpenseTrendChart(points: List<DailyExpense>, selected: LocalDate, hidden: Boolean, onSelect: (LocalDate) -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
    val grid = MaterialTheme.colorScheme.outlineVariant
    val max = points.maxOfOrNull { it.amount } ?: 0L
    val scale = expenseScale(max)
    val total = points.sumOf { it.amount }
    val selectedIndex = points.indexOfFirst { it.date == selected }
    val index = if (selectedIndex >= 0) selectedIndex else points.lastIndex.coerceAtLeast(0)
    val current = points.getOrNull(index)
    val format = remember { DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("id", "ID")) }
    fun axis(value: Double): String {
        val (divisor, suffix) = when { value >= 1e12 -> 1e12 to " T"; value >= 1e9 -> 1e9 to " M"; value >= 1e6 -> 1e6 to " jt"; value >= 1e3 -> 1e3 to " rb"; else -> 1.0 to "" }
        return "Rp" + java.text.DecimalFormat("0.#", java.text.DecimalFormatSymbols(Locale("id", "ID"))).format(value/divisor) + suffix
    }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Tren Pengeluaran", style = MaterialTheme.typography.titleMedium)
            when {
                hidden -> Text("Tampilkan nominal untuk melihat grafik dan rinciannya.", style = MaterialTheme.typography.bodySmall)
                points.isEmpty() -> Text("Belum ada hari aktual pada periode ini.", style = MaterialTheme.typography.bodySmall)
                else -> {
                    Text(AmountFormat.rupiah(total), style = MaterialTheme.typography.headlineSmall)
                    Text("Total 1–${points.last().date.format(format)} · rata-rata ${AmountFormat.rupiah(total / points.size)} per hari", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (max == 0L) Text("Belum ada pengeluaran tercatat pada periode ini.", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.width(58.dp).height(172.dp), verticalArrangement = Arrangement.SpaceBetween) {
                            listOf(scale, scale/2, 0.0).forEach { Text(axis(it), style = MaterialTheme.typography.labelSmall, maxLines = 1) }
                        }
                        Column(Modifier.weight(1f)) {
                            Canvas(Modifier.fillMaxWidth().height(172.dp).semantics {
                                contentDescription = "Grafik batang pengeluaran harian. Pilih tanggal melalui pemilih di bawah."
                            }.pointerInput(points) { detectTapGestures { position ->
                                onSelect(points[expenseIndex(position.x / size.width, points.size)].date)
                            } }) {
                                val top = 6.dp.toPx()
                                val bottom = size.height - 6.dp.toPx()
                                val plotHeight = bottom-top
                                (0..2).forEach { i ->
                                    val y = top + plotHeight*i/2
                                    drawLine(grid, Offset(0f,y),Offset(size.width,y),1.dp.toPx(),pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(),4.dp.toPx())))
                                }
                                val slot = size.width / points.size
                                val width = (slot*0.64f).coerceAtMost(24.dp.toPx())
                                points.forEachIndexed { i, item ->
                                    val center = slot*(i+0.5f)
                                    val height = (plotHeight*(item.amount.coerceAtLeast(0)/scale)).toFloat()
                                    if (i == index) drawLine(primary.copy(alpha=0.12f),Offset(center,top),Offset(center,bottom),slot*0.88f)
                                    if (height > 0f) drawRoundRect(if(i==index) primary else muted,Offset(center-width/2,bottom-height),Size(width,height),CornerRadius(minOf(3.dp.toPx(),height/2)))
                                    else drawCircle(if(i==index) primary else grid,1.5.dp.toPx(),Offset(center,bottom))
                                }
                            }
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                                Text("1",style=MaterialTheme.typography.labelSmall)
                                if(points.size>2) Text(((points.size+1)/2).toString(),style=MaterialTheme.typography.labelSmall)
                                if(points.size>1) Text(points.last().date.dayOfMonth.toString(),style=MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    Surface(color=MaterialTheme.colorScheme.primaryContainer,shape=RoundedCornerShape(16.dp)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            Text(current!!.date.format(format),style=MaterialTheme.typography.labelLarge)
                            Text(AmountFormat.rupiah(current.amount),style=MaterialTheme.typography.titleLarge)
                            Text(if(current.amount>0 && current.amount==max) "Pengeluaran harian tertinggi dalam periode ini" else "Pengeluaran tercatat pada tanggal ini",style=MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (points.size > 1) Slider(value=index.toFloat(), onValueChange={onSelect(points[it.roundToInt()].date)},
                        valueRange=0f..points.lastIndex.toFloat(),steps=(points.size-2).coerceAtLeast(0),
                        modifier=Modifier.semantics { contentDescription="Pilih tanggal tren pengeluaran";stateDescription=current!!.date.format(format)+", "+AmountFormat.rupiah(current.amount) })
                    Text("Ketuk batang atau geser tanggal. Hari mendatang belum dihitung; transfer sendiri tidak termasuk pengeluaran.", style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

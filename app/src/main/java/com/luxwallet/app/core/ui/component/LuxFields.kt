@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.luxwallet.app.core.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable fun ChoiceField(label: String, value: String, options: List<String>, onSelect: (Int) -> Unit, enabled: Boolean = true) {
    var open by remember { mutableStateOf(false) }
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedCard(onClick = { focus.clearFocus(); open = true }, enabled = enabled, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Row(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(value, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Icon(Icons.Outlined.KeyboardArrowDown, "Pilih $label", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
    if (open) ModalBottomSheet(onDismissRequest = { open = false }) {
        Text(label, Modifier.padding(horizontal = 24.dp, vertical = 12.dp), style = MaterialTheme.typography.titleLarge)
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 440.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (options.isEmpty()) item { Text("Belum ada pilihan. Tambahkan melalui Pengaturan.", Modifier.padding(24.dp)) }
            itemsIndexed(options) { index, option ->
                Surface(onClick = { onSelect(index); open = false }, color = if (option == value) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(option, Modifier.weight(1f))
                        if (option == value) Icon(Icons.Outlined.Check, "Terpilih", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable fun MoneyField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(value.takeUnless { it == "0" }.orEmpty(), { raw -> rupiahDigits(raw)?.let { onChange(it.trimStart('0').ifEmpty { if (it.isEmpty()) "" else "0" }) } }, modifier.fillMaxWidth(), label = { Text(label) }, placeholder = { Text("0") },
        prefix = { Text("Rp ") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        visualTransformation = RupiahTransformation, singleLine = true, shape = RoundedCornerShape(16.dp))
}

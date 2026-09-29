package com.luxwallet.app.feature.calculator

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.*

/** Editable values use comma decimals and no grouping; dots are visual separators only. */
object CalculatorFormatting : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val groupedBefore = mutableSetOf<Int>()
        Regex("[0-9]+(?:,[0-9]*)?").findAll(raw).forEach { match ->
            // A run immediately after a comma is a fractional part, never an integer.
            if (match.range.first > 0 && raw[match.range.first - 1] == ',') return@forEach
            val integer = match.value.substringBefore(',')
            for (i in 1 until integer.length) if ((integer.length - i) % 3 == 0) groupedBefore.add(match.range.first + i)
        }
        val original = IntArray(raw.length + 1)
        val inverse = mutableListOf(0)
        val display = buildString {
            raw.forEachIndexed { index, c ->
                original[index] = length
                if (index in groupedBefore) { append('.'); inverse.add(index) }
                append(c); inverse.add(index + 1)
            }
            original[raw.length] = length
        }
        return TransformedText(AnnotatedString(display), object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = original[offset.coerceIn(original.indices)]
            override fun transformedToOriginal(offset: Int) = inverse[offset.coerceIn(inverse.indices)]
        })
    }
    fun display(raw: String) = filter(AnnotatedString(raw)).text.text
    fun input(value: TextFieldValue): TextFieldValue? {
        val source = value.text
        if (source.length > 350 || source.any { it !in "0123456789.,+-−×÷*/()% " }) return null
        // Accept pasted Indonesian amounts without silently changing malformed decimals.
        Regex("[0-9.,]+").findAll(source).forEach { token ->
            if ('.' in token.value && !Regex("[0-9]{1,3}(\\.[0-9]{3})+(,[0-9]*)?").matches(token.value)) return null
        }
        fun clean(s: String) = s.replace(".", "").replace(" ", "")
        val raw = clean(source)
        if (raw.length > 250) return null
        return value.copy(text = raw, selection = androidx.compose.ui.text.TextRange(
            clean(source.take(value.selection.start)).length, clean(source.take(value.selection.end)).length), composition = null)
    }
}

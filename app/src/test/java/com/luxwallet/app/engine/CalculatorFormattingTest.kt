package com.luxwallet.app.engine

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.luxwallet.app.feature.calculator.CalculatorFormatting
import com.luxwallet.app.feature.calendar.expenseScale
import com.luxwallet.app.feature.calendar.expenseIndex
import org.junit.Assert.*
import org.junit.Test

class CalculatorFormattingTest {
    @Test fun groupsEachOperandAndResultWithoutGroupingFractions() {
        assertEquals("(1.500.000,50+2.500)×10%",CalculatorFormatting.display("(1500000,50+2500)×10%"))
        assertEquals("−2.000,123456",CalculatorFormatting.display("−2000,123456"))
        assertEquals(",123456",CalculatorFormatting.display(",123456"))
        val raw=CalculatorFormatting.input(TextFieldValue("1.500.000,50×2"))!!.text
        assertEquals("3000001",FinanceCalculator.evaluate(raw).toPlainString())
    }
    @Test fun cursorAndSelectionRoundTripAcrossAllOperatorBoundaries() {
        listOf("", "1000", "(3200000,25+9000)÷−10", ",123456", "1000000%×20").forEach { value ->
            val formatted=CalculatorFormatting.filter(AnnotatedString(value))
            (0..value.length).forEach { i -> assertEquals(i,formatted.offsetMapping.transformedToOriginal(formatted.offsetMapping.originalToTransformed(i))) }
            (0..formatted.text.length).forEach { i -> assertTrue(formatted.offsetMapping.transformedToOriginal(i) in 0..value.length) }
        }
        val pasted=CalculatorFormatting.input(TextFieldValue("1.500.000+2.000",TextRange(9,14)))!!
        assertEquals("1500000+2000",pasted.text)
        assertEquals(TextRange(7,11),pasted.selection)
        assertNull(CalculatorFormatting.input(TextFieldValue("1.5")))
        assertNull(CalculatorFormatting.input(TextFieldValue("1.00.000")))
        assertNull(CalculatorFormatting.input(TextFieldValue("NaN")))
    }
    @Test fun chartScaleNeverClipsAndBarHitRegionsAreEven() {
        assertEquals(1000.0,expenseScale(0),0.0)
        assertEquals(50000.0,expenseScale(32100),0.0)
        listOf(1L,3L,999L,1000L,1001L,Long.MAX_VALUE).forEach { assertTrue(expenseScale(it)>=it.toDouble()) }
        assertEquals(0,expenseIndex(-1f,31));assertEquals(30,expenseIndex(1f,31))
        assertEquals(15,expenseIndex(0.5f,31));assertEquals(0,expenseIndex(0.9f,1))
        (0..30).forEach { assertEquals(it,expenseIndex((it+0.5f)/31,31)) }
    }
}

package com.luxwallet.app.engine

import com.luxwallet.app.core.database.entity.*
import com.luxwallet.app.core.model.*
import com.luxwallet.app.data.prioritizeCategories
import com.luxwallet.app.core.ui.component.LumiMood
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class AdaptiveBudgetTest {
    private val day = LocalDate.of(2026, 9, 23)
    private val zone = ZoneId.systemDefault()
    private val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
    private fun plan() = PaydayPlan(start, day.toEpochDay(), day.plusDays(2).toEpochDay(), 1_000_000,
        bills = 100000, buffer = 100000, business = 100000, investment = 100000, transportDaily = 0, expectedOn25 = 9000000)
    private fun tx(at: LocalDate = day, amount: Long = 20000, incoming: Boolean = false) = TransactionEntity(
        type = if (incoming) TransactionType.INCOME else TransactionType.EXPENSE,
        direction = if (incoming) TransactionDirection.IN else TransactionDirection.OUT, amount = amount, sourceAccountId = 1,
        transactionTime = at.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(), createdAt = start, updatedAt = start,
        confidenceScore = 1.0, reviewStatus = ReviewStatus.CONFIRMED)
    @Test fun recommendationProtectsSavingsAndDoesNotAddIncomeTwice() {
        val status = PaydayMath.status(plan(), listOf(tx(), tx(amount = 100000, incoming = true)), emptySet(), emptySet(), day)
        val result = AdaptiveBudgetEngine.calculate(status, 1080000, day)!!
        assertEquals(1080000L, result.cash)
        assertEquals(350000L, result.recommended)
        assertEquals(300000L, result.current)
        assertEquals(280000L, result.safeToSpend)
        assertEquals(20000L, result.totalSpent)
        assertEquals(300000L, status.plan.dailyBudget)
    }
    @Test fun lowerReconciledBalanceCapsRecommendationAndShortageIsNotSpendable() {
        val status = PaydayMath.status(plan(), emptyList(), emptySet(), emptySet(), day)
        assertEquals(50000L, AdaptiveBudgetEngine.calculate(status, 500000, day)!!.safeToSpend)
        val poor = AdaptiveBudgetEngine.calculate(status, 100000, day)!!
        assertEquals(0L, poor.safeToSpend)
        assertTrue(poor.remaining < 0)
        assertEquals(0L, poor.bufferRemaining)
        assertNull(AdaptiveBudgetEngine.calculate(status.copy(expired = true), null, day))
        assertNull(AdaptiveBudgetEngine.calculate(null, null, day))
    }
    @Test fun budgetWarningHasPriorityOverYesterdaySuccess() {
        val previousDay = day.minusDays(1)
        val p = plan().copy(startDay = previousDay.toEpochDay(), capturedAt = start - 86400000)
        val spent = tx(amount = (p.dailyBudget * 0.9).toLong())
        assertEquals(LumiMood.NERVOUS, LumiCondition.evaluate(listOf(p), listOf(spent), emptySet(), emptySet(), emptyList(), spent.transactionTime + 1000, day))
    }
    @Test fun trendIncludesOnlyActiveMonthActualDaysAndEligibleCashflow() {
        val transactions = listOf(tx(), tx(amount = 999, incoming = true), tx(at = day.minusMonths(1), amount = 100),
            tx(amount = 777).copy(isInternalTransfer = true), tx(amount = 888).copy(reviewStatus = ReviewStatus.IGNORED))
        val points = expenseTrend(transactions, YearMonth.from(day), day, zone)
        assertEquals(23, points.size)
        assertEquals(20000L, points.sumOf { it.amount })
        assertEquals(day, points.last().date)
        assertEquals(100L, expenseTrend(transactions, YearMonth.from(day.minusMonths(1)), day, zone).sumOf { it.amount })
        assertTrue(expenseTrend(transactions, YearMonth.from(day.plusMonths(1)), day, zone).isEmpty())
    }
    @Test fun foodAndDrinkComesFirstEvenWithEarlierAlphabeticalCategories() {
        val categories = listOf(CategoryEntity(id = 1, name = "Aset"), CategoryEntity(id = 2, name = "Food & Drink"), CategoryEntity(id = 3, name = "Belanja"))
        assertEquals("Food & Drink", prioritizeCategories(categories).first().name)
    }
    @Test fun actualIncomeReviewAndSavingsDriveLumi() {
        val income = tx(incoming = true)
        val now = income.transactionTime + 1000
        assertEquals(LumiMood.EXCITED, LumiCondition.evaluate(emptyList(), listOf(income), emptySet(), emptySet(), emptyList(), now, day))
        assertEquals(LumiMood.CURIOUS, LumiCondition.evaluate(emptyList(), listOf(income.copy(reviewStatus = ReviewStatus.NEEDS_REVIEW)), emptySet(), emptySet(), emptyList(), now, day))
        val goal = GoalEntity(name = "Modal", targetAmount = 100000, currentAmount = 100000, createdAt = start)
        assertEquals(LumiMood.PROUD, LumiCondition.evaluate(emptyList(), emptyList(), emptySet(), emptySet(), listOf(goal), now, day))
        assertEquals(LumiMood.CALM, LumiCondition.evaluate(emptyList(), listOf(income), emptySet(), emptySet(), emptyList(), now + 7200000, day))
    }
    @Test fun safeActivePlanIsNotConfusedByReviewBacklog() {
        val reviewed = tx(amount = 1000).copy(reviewStatus = ReviewStatus.NEEDS_REVIEW)
        assertEquals(LumiMood.HAPPY, LumiCondition.evaluate(listOf(plan()), listOf(reviewed), emptySet(), emptySet(), emptyList(), reviewed.transactionTime + 1000, day))
        assertEquals(LumiMood.CALM, LumiCondition.evaluate(emptyList(), listOf(reviewed), emptySet(), emptySet(), emptyList(), reviewed.transactionTime + 86400001, day.plusDays(1)))
    }

}

package com.luxwallet.app.engine

import com.luxwallet.app.core.common.CashflowMath
import com.luxwallet.app.core.database.entity.TransactionEntity
import com.luxwallet.app.core.model.TransactionDirection
import com.luxwallet.app.core.model.TransactionType
import kotlinx.serialization.Serializable
import java.time.*
import java.time.temporal.ChronoUnit

@Serializable
data class PaydayPlan(
    val capturedAt: Long,
    val startDay: Long,
    val payday: Long,
    val availableCash: Long,
    val bills: Long = 0,
    val buffer: Long = 0,
    val business: Long = 0,
    val investment: Long = 0,
    val transportDaily: Long = 6000,
    val weekdaysOnly: Boolean = true,
    val expectedOn25: Long = 0,
    val expectedOn1: Long = 0,
    // Already reflected in availableCash, but still counts against the first day's allowance.
    val spentBeforeCapture: Long = 0,
    val transportPaidBeforeCapture: Long = 0
) {
    val start: LocalDate get() = LocalDate.ofEpochDay(startDay)
    val end: LocalDate get() = LocalDate.ofEpochDay(payday)
    val days: Int get() = ChronoUnit.DAYS.between(start, end).toInt()
    val transportDays: Int get() = (0 until days).count {
        !weekdaysOnly || start.plusDays(it.toLong()).dayOfWeek !in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    }
    val transportGross: Long get() = transportDays * transportDaily
    val transportReserve: Long get() = (transportGross - transportPaidBeforeCapture).coerceAtLeast(0)
    val protectedCash get() = bills + buffer + business + investment + transportReserve
    val freeCash get() = availableCash - protectedCash
    val dailyBudget get() = ((freeCash + spentBeforeCapture) / days.coerceAtLeast(1)).coerceAtLeast(0)
    fun validate() {
        require(days in 1..62) { "Tanggal pemasukan harus 1–62 hari setelah hari ini." }
        require(listOf(availableCash, bills, buffer, business, investment, transportDaily, expectedOn25, expectedOn1, spentBeforeCapture, transportPaidBeforeCapture).all { it in 0..999_999_999_999_999L }) { "Nominal tidak valid." }
        require(transportDaily <= 10_000_000L) { "Biaya perjalanan terlalu besar." }
    }
    companion object {
        const val BILLS_CATEGORY = "Tagihan terencana"
        fun nextPayday(after: LocalDate): LocalDate = generateSequence(after.plusDays(1)) { it.plusDays(1) }
            .first { it.dayOfMonth == 25 || it.dayOfMonth == 1 }
        fun forDay(plans: List<PaydayPlan>, day: LocalDate): PaydayPlan? = plans
            .filter { day >= it.start && day < it.end }.maxByOrNull { it.capturedAt }
    }
}

data class PaydayStatus(
    val plan: PaydayPlan, val cashRemaining: Long, val freeRemaining: Long,
    val spentToday: Long, val dailyBudget: Long, val remainingToday: Long,
    val transportSpent: Long, val billsSpent: Long, val actualIncome: Long,
    val expired: Boolean
) {
    val usedPercent get() = if (dailyBudget > 0) spentToday * 100.0 / dailyBudget else if (spentToday > 0) 100.0 else 0.0
}

object PaydayMath {
    fun capture(plan: PaydayPlan, transactions: List<TransactionEntity>, transportIds: Set<Long>, billIds: Set<Long>, zone: ZoneId = ZoneId.systemDefault()): PaydayPlan {
        val dayStart = plan.start.atStartOfDay(zone).toInstant().toEpochMilli()
        val earlier = CashflowMath.cashflowEligible(transactions).filter {
            it.transactionTime in dayStart until plan.capturedAt && it.direction == TransactionDirection.OUT &&
                it.type !in setOf(TransactionType.EWALLET_TOPUP, TransactionType.BALANCE_ADJUSTMENT)
        }
        val dailyTravel = if (!plan.weekdaysOnly || plan.start.dayOfWeek !in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)) plan.transportDaily else 0L
        val travelPaid = earlier.filter { it.categoryId in transportIds }.sumOf { it.amount }
        val freeSpent = earlier.filter { it.categoryId !in transportIds && it.categoryId !in billIds }.sumOf { it.amount }
        return plan.copy(spentBeforeCapture = freeSpent + (travelPaid - dailyTravel).coerceAtLeast(0),
            transportPaidBeforeCapture = minOf(travelPaid, dailyTravel))
    }

    fun eligible(plan: PaydayPlan, transactions: List<TransactionEntity>, until: LocalDate, zone: ZoneId): List<TransactionEntity> {
        val end = minOf(until.plusDays(1), plan.end).atStartOfDay(zone).toInstant().toEpochMilli()
        return CashflowMath.cashflowEligible(transactions).filter {
            it.transactionTime >= plan.capturedAt && it.transactionTime < end && it.type != TransactionType.EWALLET_TOPUP && it.type != TransactionType.BALANCE_ADJUSTMENT
        }
    }
    fun status(plan: PaydayPlan, transactions: List<TransactionEntity>, transportIds: Set<Long>, billIds: Set<Long>, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): PaydayStatus {
        val eligible = eligible(plan, transactions, today, zone)
        val out = eligible.filter { it.direction == TransactionDirection.OUT }
        val income = eligible.filter { it.direction == TransactionDirection.IN }.sumOf { it.amount }
        val transport = out.filter { it.categoryId in transportIds }.sumOf { it.amount }
        val bills = out.filter { it.categoryId in billIds }.sumOf { it.amount }
        val free = out.filter { it.categoryId !in transportIds && it.categoryId !in billIds }
        val dayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
        fun excessForDay(ids: Set<Long>, reserve: Long): Long {
            val total = out.filter { it.categoryId in ids }.sumOf { it.amount }
            val before = out.filter { it.categoryId in ids && it.transactionTime < dayStart }.sumOf { it.amount }
            return (total - reserve).coerceAtLeast(0) - (before - reserve).coerceAtLeast(0)
        }
        val spentToday = free.filter { it.transactionTime >= dayStart }.sumOf { it.amount } +
            excessForDay(transportIds, plan.transportReserve) + excessForDay(billIds, plan.bills) +
            if (today == plan.start) plan.spentBeforeCapture else 0L
        val remaining = plan.availableCash + income - out.sumOf { it.amount }
        val protected = plan.buffer + plan.business + plan.investment +
            (plan.transportReserve - transport).coerceAtLeast(0) + (plan.bills - bills).coerceAtLeast(0)
        val freeRemaining = remaining - protected
        // The saved daily target stays fixed so historical days cannot become surplus by changing today's plan.
        val todayRemaining = minOf(plan.dailyBudget - spentToday, freeRemaining)
        return PaydayStatus(plan, remaining, freeRemaining, spentToday, plan.dailyBudget, todayRemaining,
            transport, bills, income, today >= plan.end)
    }
}

package com.luxwallet.app.engine

import com.luxwallet.app.core.common.CashflowMath
import com.luxwallet.app.core.database.entity.*
import com.luxwallet.app.core.model.*
import com.luxwallet.app.core.ui.component.*
import java.time.LocalDate

object LumiCondition {
    fun evaluate(plans: List<PaydayPlan>, transactions: List<TransactionEntity>, transport: Set<Long>, bills: Set<Long>,
                 goals: List<GoalEntity>, now: Long, today: LocalDate): LumiMood {
        val plan = plans.maxByOrNull { it.capturedAt }
        val status = plan?.let { PaydayMath.status(it, transactions, transport, bills, today) }
        val eligible = CashflowMath.cashflowEligible(transactions).filter { it.transactionTime <= now }
        val latest = eligible.maxByOrNull { it.transactionTime }
        val recent = latest != null && now - latest.transactionTime in 0..3_600_000L
        val expenses = eligible.filter { it.direction == TransactionDirection.OUT && it.id != latest?.id && now - it.transactionTime in 0..604_800_000L }
        val spike = recent && latest?.direction == TransactionDirection.OUT && expenses.size >= 3 && latest.amount > expenses.map { it.amount }.average() * 3
        val yesterday = today.minusDays(1)
        val previous = PaydayPlan.forDay(plans, yesterday)?.let { PaydayMath.status(it, transactions, transport, bills, yesterday) }
        val achieved = goals.any { it.targetAmount > 0 && it.currentAmount >= it.targetAmount } ||
            (previous != null && previous.dailyBudget > 0 && previous.spentToday <= previous.dailyBudget && previous.freeRemaining >= 0)
        return companionMood(status, transactions.any { it.reviewStatus == ReviewStatus.NEEDS_REVIEW && now - it.transactionTime in 0..86_400_000L }, spike,
            recent && latest?.direction == TransactionDirection.IN, achieved)
    }
}

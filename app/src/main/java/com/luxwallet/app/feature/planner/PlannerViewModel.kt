package com.luxwallet.app.feature.planner

import androidx.room.withTransaction
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luxwallet.app.LuxWalletApp
import com.luxwallet.app.core.common.TransportPlan
import com.luxwallet.app.core.database.entity.*
import com.luxwallet.app.engine.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

data class PlannerState(
    val plans: List<PaydayPlan> = emptyList(), val transactions: List<TransactionEntity> = emptyList(),
    val transportIds: Set<Long> = emptySet(), val billIds: Set<Long> = emptySet(),
    val interim: InterimBudget? = null, val accountCash: Long = 0, val today: LocalDate = LocalDate.now(), val loaded: Boolean = false, val hasAccounts: Boolean = false
) {
    val adaptive get() = AdaptiveBudgetEngine.calculate(status, accountCash.takeIf { hasAccounts }, today)
    val latest get() = plans.maxByOrNull { it.capturedAt }
    val status get() = (PaydayPlan.forDay(plans, today) ?: latest)?.let { PaydayMath.status(it, transactions, transportIds, billIds, today) }
}
class PlannerViewModel(private val app: LuxWalletApp) : ViewModel() {
    private val date = flow { while (true) { emit(LocalDate.now()); kotlinx.coroutines.delay(60_000) } }.distinctUntilChanged()
    private val context = combine(app.preferences.transportPlan, app.financialProfileRepository.observe(), date) { transport, profile, today -> Triple(transport, profile, today) }
    val state = combine(app.paydayPlanRepository.plans, app.transactionRepository.observeAll(),
        app.categoryRepository.observeAll(), app.accountRepository.observeActiveAccounts(), context) { plans, txs, categories, accounts, context ->
        val (transport, profile, today) = context
        val cash = accounts.filter { it.isOwnedByUser }.sumOf { it.currentEstimatedBalance }
        val owned = accounts.any { it.isOwnedByUser }
        PlannerState(plans, txs, categories.filter { it.name == TransportPlan.CATEGORY }.map { it.id }.toSet(),
            categories.filter { it.name == PaydayPlan.BILLS_CATEGORY }.map { it.id }.toSet(),
            InterimBudgetEngine.calculate(plans, cash.takeIf { owned }, txs,
                categories.filter { it.name == TransportPlan.CATEGORY }.map { it.id }.toSet(),
                categories.filter { it.name == PaydayPlan.BILLS_CATEGORY }.map { it.id }.toSet(), profile, transport, today),
            cash, today, true, owned)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlannerState())
    val saving = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    fun save(plan: PaydayPlan, onSaved: () -> Unit) {
        if (saving.value) return
        saving.value = true
        viewModelScope.launch {
            try {
                app.database.withTransaction {
                    val categories = app.categoryRepository.observeAll().first()
                    val captured = PaydayMath.capture(plan, app.transactionRepository.observeAll().first(),
                        categories.filter { it.name == TransportPlan.CATEGORY }.map { it.id }.toSet(),
                        categories.filter { it.name == PaydayPlan.BILLS_CATEGORY }.map { it.id }.toSet())
                    captured.validate()
                    app.paydayPlanRepository.save(captured)
                }
                message.value = "Rencana diperbarui. Belanja sebelum konfirmasi tetap mengurangi budget hari ini; saldo tidak dipotong ulang."
                onSaved()
            }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { message.value = e.message ?: "Rencana belum tersimpan. Coba lagi." }
            finally { saving.value = false }
        }
    }
}

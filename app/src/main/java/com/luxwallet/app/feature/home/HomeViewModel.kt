package com.luxwallet.app.feature.home

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luxwallet.app.LuxWalletApp
import com.luxwallet.app.core.common.TransportPlan
import com.luxwallet.app.core.database.entity.*
import com.luxwallet.app.core.model.ReviewStatus
import com.luxwallet.app.core.ui.component.DonutSlice
import com.luxwallet.app.engine.DailyAllowance
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

data class HomeUiState(
    val netWorth: Long = 0,
    val assetDistribution: List<DonutSlice> = emptyList(),
    val recentTransactions: List<TransactionEntity> = emptyList(),
    val accountNames: Map<Long, String> = emptyMap(),
    val categoryNames: Map<Long, String> = emptyMap(),
    val allowance: DailyAllowance? = null,
    val isLoading: Boolean = true
)

class HomeViewModel(private val app: LuxWalletApp) : ViewModel() {
    val reviewCount = combine(app.transactionRepository.observeByReviewStatus(ReviewStatus.NEEDS_REVIEW),
        app.notificationRepository.observeAll()) { txs, observations ->
        txs.size + observations.count { it.parseStatus == com.luxwallet.app.core.model.ParseStatus.FAILED }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    private val state = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = state
    private data class Inputs(val accounts: List<AccountEntity>, val assets: List<AssetEntity>,
        val liabilities: List<LiabilityEntity>, val profile: FinancialProfileEntity)
    init {
        viewModelScope.launch {
            val inputs = combine(app.accountRepository.observeActiveAccounts(), app.assetRepository.observeAll(),
                app.liabilityRepository.observeAll(), app.financialProfileRepository.observe()) { accounts, assets, liabilities, profile ->
                Inputs(accounts, assets, liabilities, profile)
            }
            val date = flow { while (true) { emit(LocalDate.now()); kotlinx.coroutines.delay(60_000) } }.distinctUntilChanged()
            combine(inputs, app.transactionRepository.observeAll(), app.categoryRepository.observeAll(), app.preferences.transportPlan, date) {
                data, transactions, categories, transport, today ->
                val balances = data.accounts.filter { it.includeInNetWorth }
                val assets = data.assets.filter { it.includeInNetWorth }
                val netWorth = balances.sumOf { it.currentEstimatedBalance } + assets.sumOf { it.currentValue } - data.liabilities.sumOf { it.currentOutstanding }
                val colors = listOf(Color(0xFFD8B66A), Color(0xFF6E97A3), Color(0xFF9D8AAA), Color(0xFF75A38C), Color(0xFFC58E73))
                val distribution = (balances.map { it.name to it.currentEstimatedBalance } + assets.map { it.name to it.currentValue })
                    .filter { it.second > 0 }.mapIndexed { i, pair -> DonutSlice(pair.first, pair.second.toDouble(), colors[i % colors.size]) }
                HomeUiState(netWorth, distribution,
                    transactions.filter { it.reviewStatus != ReviewStatus.IGNORED }.sortedByDescending { it.transactionTime }.take(5),
                    data.accounts.associate { it.id to it.name }, categories.associate { it.id to it.name },
                    DailyAllowance.calculate(data.profile, transactions, categories.filter { it.name == TransportPlan.CATEGORY }.map { it.id }.toSet(), transport, today),
                    false)
            }.collect { state.value = it }
        }
    }
    companion object { fun create(app: LuxWalletApp) = HomeViewModel(app) }
}

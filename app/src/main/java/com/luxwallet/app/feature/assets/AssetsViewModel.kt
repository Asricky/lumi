package com.luxwallet.app.feature.assets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luxwallet.app.LuxWalletApp
import com.luxwallet.app.core.database.entity.AccountEntity
import com.luxwallet.app.core.database.entity.AssetEntity
import com.luxwallet.app.core.database.entity.LiabilityEntity
import com.luxwallet.app.data.AccountRepository
import com.luxwallet.app.data.AssetRepository
import com.luxwallet.app.data.LiabilityRepository
import com.luxwallet.app.engine.NetWorthEngine
import com.luxwallet.app.engine.NetWorthInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class AssetsUiState(
    val netWorth: Long = 0,
    val totalAssets: Long = 0,
    val totalLiabilities: Long = 0,
    val liquidAccounts: List<AccountEntity> = emptyList(),
    val investments: List<AssetEntity> = emptyList(),
    val otherAssets: List<AssetEntity> = emptyList(),
    val liabilities: List<LiabilityEntity> = emptyList(),
    val isLoading: Boolean = true
)

private val INVESTMENT_CLASSES = setOf(
    com.luxwallet.app.core.model.AssetClass.DEPOSITO,
    com.luxwallet.app.core.model.AssetClass.SAHAM,
    com.luxwallet.app.core.model.AssetClass.REKSA_DANA,
    com.luxwallet.app.core.model.AssetClass.OBLIGASI_SBN,
    com.luxwallet.app.core.model.AssetClass.CRYPTO
)

class AssetsViewModel(
    private val accountRepository: AccountRepository,
    private val assetRepository: AssetRepository,
    private val liabilityRepository: LiabilityRepository,
    private val transactionRepository: com.luxwallet.app.data.TransactionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AssetsUiState())
    val uiState: StateFlow<AssetsUiState> = _uiState
    val saving = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)

    fun saveValue(accountId: Long?, asset: AssetEntity?, liability: LiabilityEntity?, name: String,
                  assetClass: com.luxwallet.app.core.model.AssetClass, value: Long, originalValue: Long, notes: String, onSaved: () -> Unit) {
        if (saving.value) return
        if (value < 0 || name.isBlank() || notes.length > 2000) { error.value = "Isi nama dan nominal yang valid."; return }
        saving.value = true
        error.value = null
        viewModelScope.launch {
            try {
                when {
                    accountId != null -> transactionRepository.updateAccountValue(accountId, name, value.takeIf { it != originalValue }, notes)
                    liability != null -> liabilityRepository.upsert(liability.copy(name = name, notes = notes.trim().ifBlank { null }, currentOutstanding = value, updatedAt = System.currentTimeMillis()))
                    else -> assetRepository.upsert(asset?.copy(name = name, notes = notes.trim().ifBlank { null }, assetClass = assetClass, currentValue = value, updatedAt = System.currentTimeMillis())
                        ?: AssetEntity(name = name, notes = notes.trim().ifBlank { null }, assetClass = assetClass, currentValue = value, updatedAt = System.currentTimeMillis()))
                }
                onSaved()
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { error.value = "Belum tersimpan. Periksa nominal dan coba lagi." }
            finally { saving.value = false }
        }
    }

    fun delete(accountId: Long?, asset: AssetEntity?, liability: LiabilityEntity?, onSaved: () -> Unit) {
        if (saving.value) return
        saving.value = true
        error.value = null
        viewModelScope.launch {
            try {
                if (accountId != null) accountRepository.setActive(accountId, false)
                else if (asset != null) assetRepository.delete(asset)
                else if (liability != null) liabilityRepository.delete(liability)
                onSaved()
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { error.value = "Aset belum dihapus. Coba lagi." }
            finally { saving.value = false }
        }
    }

    init {
        viewModelScope.launch {
            combine(
                accountRepository.observeActiveAccounts(),
                assetRepository.observeAll(),
                liabilityRepository.observeAll()
            ) { accounts, assets, liabilities ->
                val netWorthResult = NetWorthEngine.calculate(
                    NetWorthInput(
                        accountBalances = accounts.filter { it.includeInNetWorth }.map { it.currentEstimatedBalance },
                        otherAssetValues = assets.filter { it.includeInNetWorth }.map { it.currentValue },
                        liabilityOutstanding = liabilities.map { it.currentOutstanding }
                    )
                )
                AssetsUiState(
                    netWorth = netWorthResult.netWorth,
                    totalAssets = netWorthResult.totalAssets,
                    totalLiabilities = netWorthResult.totalLiabilities,
                    liquidAccounts = accounts,
                    investments = assets.filter { it.assetClass in INVESTMENT_CLASSES },
                    otherAssets = assets.filter { it.assetClass !in INVESTMENT_CLASSES },
                    liabilities = liabilities,
                    isLoading = false
                )
            }.collect { _uiState.value = it }
        }
    }

    companion object {
        fun create(app: LuxWalletApp) = AssetsViewModel(app.accountRepository, app.assetRepository, app.liabilityRepository, app.transactionRepository)
    }
}

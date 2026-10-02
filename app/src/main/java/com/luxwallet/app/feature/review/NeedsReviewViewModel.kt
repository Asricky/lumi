package com.luxwallet.app.feature.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luxwallet.app.LuxWalletApp
import com.luxwallet.app.core.database.entity.*
import com.luxwallet.app.core.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class NeedsReviewUiState(
    val transactions: List<TransactionEntity> = emptyList(),
    val accountNames: Map<Long, String> = emptyMap(),
    val categoryNames: Map<Long, String> = emptyMap(),
    val observations: List<NotificationObservationEntity> = emptyList(),
    val isLoading: Boolean = true
) {
    val failed get() = observations.filter { it.parseStatus == ParseStatus.FAILED }
    val keys get() = transactions.map { "tx:${it.id}" } + failed.map { "obs:${it.id}" }
}

fun reviewMessage(observations: List<NotificationObservationEntity>, fallback: String): String =
    observations.map { item ->
        val body = item.bigText?.takeIf { it.isNotBlank() } ?: item.text.takeIf { it.isNotBlank() }
            ?: item.textLines.orEmpty()
        listOf(item.title, body, item.subText.orEmpty()).filter { it.isNotBlank() }.distinct().joinToString("\n")
    }.filter { it.isNotBlank() }.distinct().joinToString("\n\n").ifBlank { fallback }

fun privateReviewMessage(message: String, hidden: Boolean) = if (hidden) message.replace(Regex("[0-9]"), "•") else message

class NeedsReviewViewModel(private val app: LuxWalletApp) : ViewModel() {
    val uiState = combine(app.transactionRepository.observeByReviewStatus(ReviewStatus.NEEDS_REVIEW),
        app.accountRepository.observeAllAccounts(), app.categoryRepository.observeAll(), app.notificationRepository.observeAll()) { txs, accounts, categories, observations ->
        NeedsReviewUiState(txs, accounts.associate { it.id to it.name }, categories.associate { it.id to it.name }, observations, false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NeedsReviewUiState())
    val busy = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val message = MutableStateFlow<String?>(null)
    fun retry(id: Long, onDone: () -> Unit) {
        if (busy.value) return
        busy.value = true; error.value = null; message.value = null
        viewModelScope.launch {
            try {
                message.value = app.transactionRepository.retryFailedNotification(id, app.parserRegistry)
                onDone()
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { error.value = "Pesan belum berhasil dibaca ulang. Coba lagi." }
            finally { busy.value = false }
        }
    }
    fun dismiss(keys: List<String>, onDone: () -> Unit) {
        if (busy.value) return
        busy.value = true; error.value = null; message.value = null
        viewModelScope.launch {
            try {
                val txs = keys.filter { it.startsWith("tx:") }.mapNotNull { it.substringAfter(':').toLongOrNull() }.toSet()
                val obs = keys.filter { it.startsWith("obs:") }.mapNotNull { it.substringAfter(':').toLongOrNull() }.toSet()
                val count = app.transactionRepository.dismissReview(txs, obs)
                message.value = if (count == 0) "Catatan sudah ditinjau sebelumnya." else "$count catatan dihapus dari tinjauan."
                onDone()
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { error.value = "Belum berhasil dihapus. Tidak ada perubahan yang disimpan. Coba lagi." }
            finally { busy.value = false }
        }
    }
    companion object { fun create(app: LuxWalletApp) = NeedsReviewViewModel(app) }
}

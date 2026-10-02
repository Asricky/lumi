package com.luxwallet.app.data

import com.luxwallet.app.core.database.dao.AccountDao
import com.luxwallet.app.core.database.dao.LedgerEntryDao
import com.luxwallet.app.core.database.dao.MerchantRuleDao
import com.luxwallet.app.core.database.dao.NotificationObservationDao
import com.luxwallet.app.core.database.dao.TransactionDao
import com.luxwallet.app.core.database.entity.LedgerEntryEntity
import com.luxwallet.app.core.database.entity.NotificationObservationEntity
import com.luxwallet.app.core.database.entity.TransactionEntity
import com.luxwallet.app.core.model.AccountProvider
import com.luxwallet.app.core.model.ParseStatus
import com.luxwallet.app.core.model.ReviewReason
import com.luxwallet.app.core.model.ReviewStatus
import com.luxwallet.app.core.model.SourceApp
import com.luxwallet.app.core.model.TransactionDirection
import com.luxwallet.app.core.model.TransactionType
import com.luxwallet.app.engine.CategorizationEngine
import com.luxwallet.app.engine.CategorizationOutcome
import com.luxwallet.app.engine.DeduplicationEngine
import com.luxwallet.app.engine.LedgerCandidate
import com.luxwallet.app.engine.MatchingEngine
import com.luxwallet.app.engine.MerchantRule
import com.luxwallet.app.parser.core.TransactionCandidate
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit
import androidx.room.withTransaction
import com.luxwallet.app.core.database.LuxDatabase

/**
 * Turns a parsed [TransactionCandidate] into ledger truth: dedupes against recent activity,
 * attempts to fold it into an existing opposite-direction leg as one internal transfer
 * (PRD §7/§11/§21), assigns a category (PRD §23), and only then writes a [TransactionEntity] +
 * [LedgerEntryEntity] rows and updates account balances.
 */
class TransactionRepository(
    private val transactionDao: TransactionDao,
    private val ledgerEntryDao: LedgerEntryDao,
    private val observationDao: NotificationObservationDao,
    private val accountDao: AccountDao,
    private val merchantRuleDao: MerchantRuleDao,
    private val categoryRepository: CategoryRepository,
    private val database: LuxDatabase
) {
    private val dedupeWindow = TimeUnit.HOURS.toMillis(24)

    fun observeAll(): Flow<List<TransactionEntity>> = transactionDao.observeAll()
    fun observeByReviewStatus(status: ReviewStatus): Flow<List<TransactionEntity>> = transactionDao.observeByReviewStatus(status)
    fun observeInRange(from: Long, to: Long): Flow<List<TransactionEntity>> = transactionDao.observeInRange(from, to)
    suspend fun getInRange(from: Long, to: Long): List<TransactionEntity> = transactionDao.getInRange(from, to)

    suspend fun saveDetails(id: Long, merchant: String, note: String, categoryId: Long?, excluded: Boolean, learn: Boolean, selectedAccountId: Long? = null) = database.withTransaction {
        val tx = transactionDao.getById(id) ?: error("Transaksi tidak ditemukan")
        check(tx.reviewStatus != ReviewStatus.IGNORED) { "Transaksi sudah diabaikan" }
        if (categoryId != null) require(categoryRepository.getById(categoryId) != null)
        val accountId = tx.sourceAccountId ?: selectedAccountId ?: error("Pilih rekening untuk transaksi ini")
        val selectedAccount = accountDao.getById(accountId) ?: error("Rekening tidak ditemukan")
        if (tx.sourceAccountId == null) {
            require(selectedAccount.isActive) { "Pilih rekening yang aktif" }
            require(tx.transactionTime >= selectedAccount.openingBalanceDate) { "Transaksi ini sudah termasuk saldo awal. Abaikan catatan agar saldo tidak dihitung dua kali." }
            if (ledgerEntryDao.getAllOnce().none { it.transactionId == id }) {
                val delta = if (tx.direction == TransactionDirection.IN) tx.amount else -tx.amount
                ledgerEntryDao.insert(LedgerEntryEntity(transactionId = id, accountId = accountId, deltaAmount = delta, createdAt = System.currentTimeMillis()))
                accountDao.applyBalanceDelta(accountId, delta)
            }
        }
        transactionDao.update(tx.copy(sourceAccountId = accountId, merchantName = merchant.trim().ifBlank { null }, note = note.trim().ifBlank { null },
            categoryId = categoryId, subcategoryId = null,
            isExcludedFromCashflow = if (tx.reviewReason == ReviewReason.POSSIBLE_DUPLICATE) true else excluded,
            updatedAt = System.currentTimeMillis()))
        confirm(id)
        if (learn && merchant.isNotBlank() && categoryId != null && merchantRuleDao.getAll().none {
                it.merchantContains.equals(merchant.trim(), true) && it.categoryId == categoryId }) {
            merchantRuleDao.insert(com.luxwallet.app.core.database.entity.MerchantRuleEntity(
                merchantContains = merchant.trim(), categoryId = categoryId, createdAt = System.currentTimeMillis()))
        }
    }

    /** Retry only by explicit user action; never replay a transaction already reflected in a reconciled balance. */
    suspend fun retryFailedNotification(id: Long, registry: com.luxwallet.app.parser.core.ParserRegistry): String = database.withTransaction {
        val item = observationDao.getById(id) ?: return@withTransaction "Pesan sudah tidak tersedia."
        if (item.parseStatus != ParseStatus.FAILED || item.linkedTransactionId != null)
            return@withTransaction "Pesan sudah diproses sebelumnya."
        val input = com.luxwallet.app.parser.core.NotificationInput(item.sourceApp, item.title, item.text,
            item.bigText, item.subText, item.textLines?.split("\n").orEmpty(), item.postedAt)
        when (val result = registry.parse(input)) {
            is com.luxwallet.app.parser.core.ParseResult.Parsed -> {
                val account = accountDao.findAllUserOwnedByProvider(providerFor(item.sourceApp)).singleOrNull()
                if (account?.lastReconciledAt?.let { item.postedAt <= it } == true)
                    return@withTransaction "Pesan ini mendahului koreksi saldo. Saldo tidak diubah agar uang tidak dihitung dua kali; periksa riwayat sebelum mencatat manual."
                val pending = item.copy(parseStatus = ParseStatus.PENDING, parseFailureReason = null,
                    parserVersion = com.luxwallet.app.parser.core.ParserRegistry.PARSER_VERSION)
                observationDao.update(pending)
                ingest(pending, result.candidate)
                "Pesan telah dibaca ulang. Periksa hasilnya di riwayat atau tinjauan."
            }
            is com.luxwallet.app.parser.core.ParseResult.Failed -> {
                observationDao.update(item.copy(parseFailureReason = result.reason,
                    parserVersion = com.luxwallet.app.parser.core.ParserRegistry.PARSER_VERSION))
                "Pesan masih belum dikenali. Kamu bisa mencatatnya secara manual."
            }
            com.luxwallet.app.parser.core.ParseResult.NotFinancial -> "Pesan bukan bukti transaksi berhasil; saldo tidak diubah."
        }
    }

    suspend fun updateAccountValue(id: Long, name: String, value: Long?, notes: String? = null) = database.withTransaction {
        require(name.isNotBlank())
        require(notes == null || notes.length <= 2000)
        if (value != null) setAccountBalance(id, value)
        accountDao.updateMetadata(id, name.trim(), notes?.trim()?.ifBlank { null })
    }

    suspend fun confirm(id: Long, merchant: String? = null, note: String? = null) = database.withTransaction {
        val tx = transactionDao.getById(id) ?: return@withTransaction
        if (tx.reviewStatus == ReviewStatus.IGNORED) return@withTransaction
        require(tx.sourceAccountId != null) { "Pilih rekening terlebih dahulu" }
        val held = tx.reviewReason == ReviewReason.POSSIBLE_DUPLICATE
        if (held && ledgerEntryDao.getAllOnce().none { it.transactionId == id }) {
            val accountId = tx.sourceAccountId ?: error("Pilih rekening terlebih dahulu")
            val delta = if (tx.direction == TransactionDirection.IN) tx.amount else -tx.amount
            ledgerEntryDao.insert(LedgerEntryEntity(transactionId = id, accountId = accountId, deltaAmount = delta, createdAt = System.currentTimeMillis()))
            accountDao.applyBalanceDelta(accountId, delta)
        }
        transactionDao.update(tx.copy(merchantName = merchant?.ifBlank { null } ?: tx.merchantName,
            note = note?.ifBlank { null } ?: tx.note, reviewStatus = ReviewStatus.CONFIRMED, reviewReason = null,
            isExcludedFromCashflow = if (held) false else tx.isExcludedFromCashflow, updatedAt = System.currentTimeMillis()))
    }

    /** One-time upgrade repair. Preserve ambiguous records for an explicit user decision. */
    suspend fun reviewExistingDuplicates() = database.withTransaction {
        val observations = observationDao.linkedInRange(0, Long.MAX_VALUE)
        val previous = mutableListOf<NotificationObservationEntity>()
        for (observation in observations) {
            if (observation.contentHash == null) observationDao.update(observation.copy(contentHash = com.luxwallet.app.engine.NotificationIdentity.hash(observation)))
            previous.removeAll { it.postedAt < observation.postedAt - com.luxwallet.app.engine.NotificationIdentity.REVIEW_WINDOW_MS }
            val tx = observation.linkedTransactionId?.let { transactionDao.getById(it) } ?: continue
            if (tx.isManual || tx.isInternalTransfer || tx.reviewStatus == ReviewStatus.IGNORED ||
                tx.reviewReason == ReviewReason.POSSIBLE_DUPLICATE) continue
            val duplicate = previous.asReversed().firstOrNull { old ->
                if (old.linkedTransactionId == tx.id) false else {
                    val oldTx = old.linkedTransactionId?.let { transactionDao.getById(it) }
                    oldTx != null && oldTx.sourceAccountId == tx.sourceAccountId && oldTx.amount == tx.amount &&
                        oldTx.direction == tx.direction && oldTx.reviewStatus != ReviewStatus.IGNORED &&
                        oldTx.reviewReason != ReviewReason.POSSIBLE_DUPLICATE &&
                        com.luxwallet.app.engine.NotificationIdentity.compare(old, observation) != null
                }
            }
            if (duplicate != null) {
                holdDuplicate(tx)
            } else previous.add(observation)
        }
    }

    private suspend fun holdDuplicate(tx: TransactionEntity) {
        ledgerEntryDao.getAllOnce().filter { it.transactionId == tx.id }.forEach { accountDao.applyBalanceDelta(it.accountId, -it.deltaAmount) }
        ledgerEntryDao.deleteForTransaction(tx.id)
        transactionDao.update(tx.copy(reviewStatus = ReviewStatus.NEEDS_REVIEW, reviewReason = ReviewReason.POSSIBLE_DUPLICATE,
            isExcludedFromCashflow = true, updatedAt = System.currentTimeMillis()))
    }

    suspend fun setAccountBalance(accountId: Long, target: Long) = database.withTransaction {
        require(target in 0..1_000_000_000_000_000)
        val account = accountDao.getById(accountId) ?: error("Rekening tidak ditemukan")
        val delta = Math.subtractExact(target, account.currentEstimatedBalance)
        if (delta != 0L) insertManual(TransactionType.BALANCE_ADJUSTMENT,
            if (delta > 0) TransactionDirection.IN else TransactionDirection.OUT, kotlin.math.abs(delta), accountId,
            note = "Penyesuaian saldo manual")
        accountDao.reconcileBalance(accountId, target, System.currentTimeMillis())
    }

    /** Snapshot selection, fresh status checks and one transaction prevent partial bulk deletion. */
    suspend fun dismissReview(transactionIds: Set<Long>, observationIds: Set<Long>): Int = database.withTransaction {
        var count = 0
        for (id in transactionIds) {
            if (transactionDao.getById(id)?.reviewStatus == ReviewStatus.NEEDS_REVIEW) {
                markIgnored(id)
                count++
            }
        }
        for (id in observationIds) {
            val item = observationDao.getById(id) ?: continue
            if (item.parseStatus == ParseStatus.FAILED) {
                observationDao.update(item.copy(parseStatus = ParseStatus.IGNORED))
                count++
            }
        }
        count
    }

    suspend fun markIgnored(id: Long) = database.withTransaction {
        val tx = transactionDao.getById(id) ?: return@withTransaction
        if (tx.reviewStatus == ReviewStatus.IGNORED) return@withTransaction
        ledgerEntryDao.getForTransaction(id).forEach {
            accountDao.applyBalanceDelta(it.accountId, -it.deltaAmount)
        }
        ledgerEntryDao.deleteForTransaction(id)
        transactionDao.update(tx.copy(reviewStatus = ReviewStatus.IGNORED, updatedAt = System.currentTimeMillis()))
    }

    /**
     * Manual entry (PRD §25): expense/income/transfer/asset purchase/liability payment/balance
     * adjustment, using the same Transaction + LedgerEntry model as automatic ones — always
     * CONFIRMED and full-confidence since it is direct user intent, not inferred from evidence.
     */
    suspend fun insertManual(
        type: TransactionType,
        direction: TransactionDirection,
        amount: Long,
        sourceAccountId: Long,
        destinationAccountId: Long? = null,
        merchantName: String? = null,
        categoryId: Long? = null,
        note: String? = null,
        transactionTime: Long = System.currentTimeMillis()
    ): Long = database.withTransaction {
        require(amount > 0) { "Nominal harus lebih dari nol" }
        require(sourceAccountId != destinationAccountId) { "Akun tujuan harus berbeda" }
        require(accountDao.getById(sourceAccountId) != null) { "Akun sumber tidak ditemukan" }
        require(destinationAccountId == null || accountDao.getById(destinationAccountId) != null) { "Akun tujuan tidak ditemukan" }
        val now = System.currentTimeMillis()
        val isTransfer = destinationAccountId != null

        val tx = TransactionEntity(
            type = type,
            direction = if (isTransfer) TransactionDirection.OUT else direction,
            amount = amount,
            sourceAccountId = sourceAccountId,
            destinationAccountId = destinationAccountId,
            merchantName = merchantName,
            categoryId = categoryId,
            transactionTime = transactionTime,
            createdAt = now,
            updatedAt = now,
            confidenceScore = 1.0,
            reviewStatus = ReviewStatus.CONFIRMED,
            note = note,
            isInternalTransfer = isTransfer,
            isManual = true,
            isExcludedFromCashflow = type == TransactionType.BALANCE_ADJUSTMENT
        )
        val txId = transactionDao.insert(tx)

        if (destinationAccountId != null) {
            ledgerEntryDao.insertAll(
                listOf(
                    LedgerEntryEntity(transactionId = txId, accountId = sourceAccountId, deltaAmount = -amount, createdAt = now),
                    LedgerEntryEntity(transactionId = txId, accountId = destinationAccountId, deltaAmount = amount, createdAt = now)
                )
            )
            accountDao.applyBalanceDelta(sourceAccountId, -amount)
            accountDao.applyBalanceDelta(destinationAccountId, amount)
        } else {
            val delta = if (direction == TransactionDirection.IN) amount else -amount
            ledgerEntryDao.insert(LedgerEntryEntity(transactionId = txId, accountId = sourceAccountId, deltaAmount = delta, createdAt = now))
            accountDao.applyBalanceDelta(sourceAccountId, delta)
        }

        if (type != TransactionType.BALANCE_ADJUSTMENT) queueConfirmation(txId, now)
        txId
    }

    suspend fun ingest(observation: NotificationObservationEntity, candidate: TransactionCandidate) = database.withTransaction {
        val stored = observationDao.getById(observation.id) ?: return@withTransaction
        if (stored.parseStatus != ParseStatus.PENDING) return@withTransaction
        val previousId = transactionDao.lastId()
        ingestOnce(stored, candidate)
        val id = observationDao.getById(stored.id)?.linkedTransactionId
        val tx = id?.let { transactionDao.getById(it) }
        if (tx != null && tx.id > previousId) {
            val waiting = !tx.isInternalTransfer && tx.type in setOf(TransactionType.EXTERNAL_TRANSFER, TransactionType.EWALLET_TOPUP)
            queueConfirmation(tx.id, if (waiting) tx.createdAt + MatchingEngine.DEFAULT_TIME_WINDOW_MS else System.currentTimeMillis())
        }
    }

    private suspend fun attachObservation(observation: NotificationObservationEntity, tx: TransactionEntity) {
        val ids = tx.sourceObservationIds.orEmpty().split(',').mapNotNull { it.toLongOrNull() }.toSet() + observation.id
        transactionDao.update(tx.copy(sourceObservationIds = ids.sorted().joinToString(",")))
        observationDao.update(observation.copy(linkedTransactionId = tx.id, parseStatus = ParseStatus.PARSED))
    }

    private suspend fun ingestOnce(observation: NotificationObservationEntity, candidate: TransactionCandidate) {
        val now = System.currentTimeMillis()
        val sourceAccount = accountDao.findAllUserOwnedByProvider(providerFor(candidate.sourceApp)).singleOrNull()
        if (sourceAccount != null && candidate.transactionTime < sourceAccount.openingBalanceDate) {
            observationDao.update(observation.copy(parseStatus = ParseStatus.IGNORED,
                parseFailureReason = "Sudah termasuk dalam saldo awal akun"))
            return
        }

        if (sourceAccount == null) {
            for (old in observationDao.linkedInRange(candidate.transactionTime - dedupeWindow, candidate.transactionTime + dedupeWindow)) {
                val tx = old.linkedTransactionId?.let { transactionDao.getById(it) } ?: continue
                if (tx.sourceAccountId == null && tx.amount == candidate.amount && tx.direction == candidate.direction &&
                    com.luxwallet.app.engine.NotificationIdentity.compare(old, observation) == com.luxwallet.app.engine.NotificationIdentityMatch.SAME_EVENT) {
                    attachObservation(observation, tx)
                    return
                }
            }
            val txId = transactionDao.insert(
                buildTransaction(
                    candidate = candidate, sourceAccountId = null, categoryId = null, subcategoryId = null,
                    reviewStatus = ReviewStatus.NEEDS_REVIEW, reviewReason = ReviewReason.ACCOUNT_NOT_CONFIGURED,
                    now = now, observationId = observation.id
                ).copy(isExcludedFromCashflow = true)
            )
            observationDao.update(observation.copy(linkedTransactionId = txId, parseStatus = ParseStatus.PARSED))
            return
        }

        val observations = observationDao.linkedInRange(
            candidate.transactionTime - dedupeWindow, candidate.transactionTime + dedupeWindow).sortedBy {
                if (com.luxwallet.app.engine.NotificationIdentity.compare(it, observation) ==
                    com.luxwallet.app.engine.NotificationIdentityMatch.SAME_EVENT) 0 else 1
            }
        val crossBcaAllowed = candidate.sourceApp == SourceApp.MYBCA && accountDao.findAllUserOwnedByProvider(AccountProvider.BCA).size == 1
        for (old in observations) {
            val tx = old.linkedTransactionId?.let { transactionDao.getById(it) } ?: continue
            // A merged transfer has one OUT direction, but its incoming observation still owns
            // its original event identity. Re-delivery must attach to that same logical transfer.
            if (tx.isInternalTransfer && tx.amount == candidate.amount &&
                sourceAccount.id in listOf(tx.sourceAccountId, tx.destinationAccountId) &&
                com.luxwallet.app.engine.NotificationIdentity.compare(old, observation) ==
                    com.luxwallet.app.engine.NotificationIdentityMatch.SAME_EVENT) {
                attachObservation(observation, tx)
                return
            }
            if (tx.sourceAccountId != sourceAccount.id || tx.amount != candidate.amount || tx.direction != candidate.direction ||
                tx.isManual || tx.isInternalTransfer) continue
            if (tx.referenceNumber != null && candidate.referenceNumber != null && tx.referenceNumber != candidate.referenceNumber) continue
            val identity = com.luxwallet.app.engine.NotificationIdentity.compare(old, observation)
                ?: if (crossBcaAllowed) com.luxwallet.app.engine.BcaDuplicateEvidence.compare(old, observation, tx, candidate, sourceAccount.id) else null
            if (identity == null) continue
            if (identity == com.luxwallet.app.engine.NotificationIdentityMatch.POSSIBLE_DUPLICATE &&
                (tx.reviewReason == ReviewReason.POSSIBLE_DUPLICATE || tx.reviewStatus == ReviewStatus.IGNORED)) continue
            if (identity == com.luxwallet.app.engine.NotificationIdentityMatch.SAME_EVENT) {
                attachObservation(observation, tx)
            } else {
                val pendingId = transactionDao.insert(buildTransaction(candidate, sourceAccount.id, tx.categoryId, tx.subcategoryId,
                    ReviewStatus.NEEDS_REVIEW, ReviewReason.POSSIBLE_DUPLICATE, now, observation.id).copy(isExcludedFromCashflow = true))
                observationDao.update(observation.copy(linkedTransactionId = pendingId, parseStatus = ParseStatus.PARSED))
            }
            return
        }

        val newAsLedgerCandidate = LedgerCandidate(
            id = -1,
            sourceApp = candidate.sourceApp,
            accountId = sourceAccount.id,
            direction = candidate.direction,
            amount = candidate.amount,
            merchantOrCounterparty = candidate.merchantName ?: candidate.counterpartyName,
            referenceNumber = candidate.referenceNumber,
            transactionTime = candidate.transactionTime,
            notificationPostedAt = observation.postedAt,
            rawPayloadHash = observation.rawPayloadHash,
            destinationProviderLabelHint = candidate.destinationProviderHint?.providerLabel,
            destinationOwnerNameHint = candidate.destinationProviderHint?.ownerNameRaw
        )

        val recentOnThisAccount = transactionDao
            .getInRange(candidate.transactionTime - dedupeWindow, candidate.transactionTime + dedupeWindow)
            .filter { it.sourceAccountId == sourceAccount.id || it.destinationAccountId == sourceAccount.id }
            .map { it.toLedgerCandidate().copy(sourceApp = candidate.sourceApp) }

        DeduplicationEngine.findDuplicate(newAsLedgerCandidate, recentOnThisAccount)?.let { duplicate ->
            transactionDao.getById(duplicate.id)?.let { attachObservation(observation, it) }
            return
        }

        val userRules = merchantRuleDao.getAll().map { MerchantRule(it.merchantContains, it.categoryId, it.subcategoryId) }
        val categorization = CategorizationEngine.categorize(candidate.explicitCategory, candidate.merchantName, candidate.counterpartyName, userRules)

        var categoryId: Long? = null
        var subcategoryId: Long? = null
        var categorizationNeedsReview = false
        when (categorization) {
            is CategorizationOutcome.Explicit -> categoryId = categoryRepository.resolveOrCreateTopLevel(categorization.categoryName)
            is CategorizationOutcome.RuleMatched -> {
                categoryId = categorization.categoryId
                subcategoryId = categorization.subcategoryId
            }
            is CategorizationOutcome.KeywordMatched -> categoryId = categoryRepository.resolveOrCreateTopLevel(categorization.categoryName)
            CategorizationOutcome.NeedsReview -> categorizationNeedsReview = true
        }

        val isTransferType = candidate.type == TransactionType.EXTERNAL_TRANSFER || candidate.type == TransactionType.EWALLET_TOPUP

        if (isTransferType) {
            val pool = transactionDao
                .findUnmatchedInWindow(candidate.transactionTime - MatchingEngine.DEFAULT_TIME_WINDOW_MS, candidate.transactionTime + MatchingEngine.DEFAULT_TIME_WINDOW_MS)
                .map { it.toLedgerCandidate() }
                .filter { other -> other.accountId?.let { id ->
                    accountDao.getById(id)?.openingBalanceDate?.let { candidate.transactionTime >= it }
                } == true }

            val accountCache = accountCacheSnapshot()

            val match = MatchingEngine.findBestMatch(
                candidate = newAsLedgerCandidate,
                pool = pool,
                isAccountOwnedByUser = { id -> id != null && accountCache[id]?.isOwnedByUser == true },
                accountProviderLabel = { id -> id?.let { accountCache[it]?.provider?.name } },
                accountOwnerName = { id -> id?.let { accountCache[it]?.ownerName } }
            )

            if (match != null) {
                mergeIntoInternalTransfer(
                    existingTransactionId = match.match.id,
                    newAccountId = sourceAccount.id,
                    newDirection = candidate.direction,
                    amount = candidate.amount,
                    confidence = match.confidence,
                    autoMatch = match.autoMatch,
                    now = now,
                    newObservationId = observation.id
                )
                observationDao.update(observation.copy(linkedTransactionId = match.match.id, parseStatus = ParseStatus.PARSED))
                return
            }
        }

        val reviewStatus: ReviewStatus
        val reviewReason: ReviewReason?
        when {
            categorizationNeedsReview -> {
                reviewStatus = ReviewStatus.NEEDS_REVIEW
                reviewReason = ReviewReason.UNKNOWN_MERCHANT
            }
            candidate.confidenceScore < CONFIRM_CONFIDENCE_THRESHOLD -> {
                reviewStatus = ReviewStatus.NEEDS_REVIEW
                reviewReason = ReviewReason.LOW_CONFIDENCE
            }
            else -> {
                reviewStatus = ReviewStatus.CONFIRMED
                reviewReason = null
            }
        }

        val txId = transactionDao.insert(
            buildTransaction(
                candidate = candidate, sourceAccountId = sourceAccount.id, categoryId = categoryId, subcategoryId = subcategoryId,
                reviewStatus = reviewStatus, reviewReason = reviewReason, now = now, observationId = observation.id
            )
        )

        val delta = if (candidate.direction == TransactionDirection.IN) candidate.amount else -candidate.amount
        ledgerEntryDao.insert(LedgerEntryEntity(transactionId = txId, accountId = sourceAccount.id, deltaAmount = delta, createdAt = now))
        accountDao.applyBalanceDelta(sourceAccount.id, delta)

        // Keep a reported balance as evidence; reconciliation is an explicit user adjustment.

        observationDao.update(observation.copy(linkedTransactionId = txId, parseStatus = ParseStatus.PARSED))
    }

    private suspend fun mergeIntoInternalTransfer(
        existingTransactionId: Long,
        newAccountId: Long,
        newDirection: TransactionDirection,
        amount: Long,
        confidence: Double,
        autoMatch: Boolean,
        now: Long,
        newObservationId: Long
    ) {
        val existing = transactionDao.getById(existingTransactionId) ?: return
        val existingAccountId = existing.sourceAccountId ?: return

        val (finalSourceId, finalDestId) = if (newDirection == TransactionDirection.OUT) {
            newAccountId to existingAccountId
        } else {
            existingAccountId to newAccountId
        }

        val transferCategoryId = categoryRepository.resolveOrCreateTopLevel("Transfer")

        val merged = existing.copy(
            type = TransactionType.INTERNAL_TRANSFER,
            direction = TransactionDirection.OUT,
            sourceAccountId = finalSourceId,
            destinationAccountId = finalDestId,
            isInternalTransfer = true,
            categoryId = transferCategoryId,
            subcategoryId = null,
            confidenceScore = confidence,
            reviewStatus = if (autoMatch) ReviewStatus.CONFIRMED else ReviewStatus.NEEDS_REVIEW,
            reviewReason = if (autoMatch) null else ReviewReason.POSSIBLE_INTERNAL_TRANSFER,
            updatedAt = now,
            sourceObservationIds = listOfNotNull(existing.sourceObservationIds, newObservationId.toString())
                .joinToString(",")
        )
        transactionDao.update(merged)
        database.transactionConfirmationDao().ready(existingTransactionId, now)

        ledgerEntryDao.deleteForTransaction(existingTransactionId)
        ledgerEntryDao.insertAll(
            listOf(
                LedgerEntryEntity(transactionId = existingTransactionId, accountId = finalSourceId, deltaAmount = -amount, createdAt = now),
                LedgerEntryEntity(transactionId = existingTransactionId, accountId = finalDestId, deltaAmount = amount, createdAt = now)
            )
        )

        // The pre-existing leg's account balance was already adjusted when it was first inserted;
        // only the newly-joined account needs its balance applied now.
        val newAccountDelta = if (newDirection == TransactionDirection.IN) amount else -amount
        accountDao.applyBalanceDelta(newAccountId, newAccountDelta)
    }

    private fun buildTransaction(
        candidate: TransactionCandidate,
        sourceAccountId: Long?,
        categoryId: Long?,
        subcategoryId: Long?,
        reviewStatus: ReviewStatus,
        reviewReason: ReviewReason?,
        now: Long,
        observationId: Long
    ) = TransactionEntity(
        type = candidate.type,
        direction = candidate.direction,
        amount = candidate.amount,
        currency = candidate.currency,
        sourceAccountId = sourceAccountId,
        merchantName = candidate.merchantName,
        counterpartyName = candidate.counterpartyName,
        categoryId = categoryId,
        subcategoryId = subcategoryId,
        transactionTime = candidate.transactionTime,
        createdAt = now,
        updatedAt = now,
        confidenceScore = candidate.confidenceScore,
        reviewStatus = reviewStatus,
        reviewReason = reviewReason,
        referenceNumber = candidate.referenceNumber,
        note = candidate.notes,
        isInternalTransfer = false,
        isManual = false,
        reportedBalance = candidate.reportedBalance,
        sourceObservationIds = observationId.toString(),
        destinationProviderLabelHint = candidate.destinationProviderHint?.providerLabel,
        destinationOwnerNameHint = candidate.destinationProviderHint?.ownerNameRaw
    )

    private suspend fun queueConfirmation(id: Long, dueAt: Long) {
        database.transactionConfirmationDao().insert(com.luxwallet.app.core.database.entity.TransactionConfirmationEntity(id, dueAt))
    }

    private suspend fun accountCacheSnapshot(): Map<Long, com.luxwallet.app.core.database.entity.AccountEntity> {
        // A plain suspend snapshot read; accounts are few, so this small full scan is inexpensive per ingest call.
        return accountDao.getAllAccountsOnce().associateBy { it.id }
    }

    private fun TransactionEntity.toLedgerCandidate(): LedgerCandidate = LedgerCandidate(
        id = id,
        sourceApp = SourceApp.MYBCA, // not used by matching/dedup comparisons beyond same-source checks we don't rely on here
        accountId = if (direction == TransactionDirection.OUT) sourceAccountId else (destinationAccountId ?: sourceAccountId),
        direction = direction,
        amount = amount,
        merchantOrCounterparty = merchantName ?: counterpartyName,
        referenceNumber = referenceNumber,
        transactionTime = transactionTime,
        notificationPostedAt = transactionTime,
        rawPayloadHash = null,
        destinationProviderLabelHint = destinationProviderLabelHint,
        destinationOwnerNameHint = destinationOwnerNameHint,
        isInternalTransfer = isInternalTransfer
    )

    companion object {
        const val CONFIRM_CONFIDENCE_THRESHOLD = 0.75

        fun providerFor(sourceApp: SourceApp): AccountProvider = when (sourceApp) {
            SourceApp.MYBCA -> AccountProvider.BCA
            SourceApp.SEABANK -> AccountProvider.SEABANK
            SourceApp.SHOPEEPAY -> AccountProvider.SHOPEEPAY
            SourceApp.GOPAY -> AccountProvider.GOPAY
        }
    }
}

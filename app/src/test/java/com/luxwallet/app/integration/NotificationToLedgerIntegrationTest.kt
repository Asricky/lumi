package com.luxwallet.app.integration

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.luxwallet.app.core.database.LuxDatabase
import com.luxwallet.app.core.database.entity.NotificationObservationEntity
import com.luxwallet.app.core.model.AccountKind
import com.luxwallet.app.core.model.AccountProvider
import com.luxwallet.app.core.model.ParseStatus
import com.luxwallet.app.core.model.ReviewStatus
import com.luxwallet.app.core.model.SourceApp
import com.luxwallet.app.core.model.TransactionType
import com.luxwallet.app.data.AccountRepository
import com.luxwallet.app.data.CategoryRepository
import com.luxwallet.app.data.NotificationRepository
import com.luxwallet.app.data.TransactionRepository
import com.luxwallet.app.parser.core.NotificationInput
import com.luxwallet.app.parser.core.ParseResult
import com.luxwallet.app.parser.core.ParserRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * End-to-end regression test of the full pipeline (PRD §49 "Integration tests"):
 * Notification → Observation → Parser → Transaction → Ledger → account balances.
 * Uses an in-memory Room database via Robolectric so it runs on the JVM, no emulator required.
 */
@RunWith(RobolectricTestRunner::class)
class NotificationToLedgerIntegrationTest {

    private lateinit var db: LuxDatabase
    private lateinit var accountRepository: AccountRepository
    private lateinit var categoryRepository: CategoryRepository
    private lateinit var notificationRepository: NotificationRepository
    private lateinit var transactionRepository: TransactionRepository
    private val registry = ParserRegistry()

    private var bcaAccountId = 0L
    private var seabankAccountId = 0L
    private var shopeepayAccountId = 0L

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, LuxDatabase::class.java).allowMainThreadQueries().build()

        accountRepository = AccountRepository(db.accountDao())
        categoryRepository = CategoryRepository(db.categoryDao())
        notificationRepository = NotificationRepository(db.notificationObservationDao())
        transactionRepository = TransactionRepository(
            db.transactionDao(), db.ledgerEntryDao(), db.notificationObservationDao(),
            db.accountDao(), db.merchantRuleDao(), categoryRepository, db
        )
        categoryRepository.seedDefaultsIfEmpty()

        bcaAccountId = accountRepository.createAccount("BCA", AccountKind.BANK, AccountProvider.BCA, 15_000_000, 0L)
        seabankAccountId = accountRepository.createAccount("SeaBank", AccountKind.BANK, AccountProvider.SEABANK, 7_500_000, 0L)
        shopeepayAccountId = accountRepository.createAccount("ShopeePay", AccountKind.EWALLET, AccountProvider.SHOPEEPAY, 25_000, 0L)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun ingestNotification(sourceApp: SourceApp, title: String, text: String, postedAt: Long,
        notificationKey: String? = null, eventTime: Long? = null, packageName: String = sourceApp.name) {
        val input = NotificationInput(sourceApp = sourceApp, title = title, text = text, postedAt = postedAt)
        val hash = NotificationRepository.hashPayload(sourceApp.name, packageName, title, text, postedAt)
        val observation = NotificationObservationEntity(
            sourceApp = sourceApp, packageName = packageName, notificationKey = notificationKey,
            title = title, text = text, bigText = null, subText = null, textLines = null,
            postedAt = postedAt, receivedAt = postedAt, rawPayloadHash = hash,
            parserVersion = ParserRegistry.PARSER_VERSION, parseStatus = ParseStatus.PENDING, eventTime = eventTime
        )
        val id = notificationRepository.insertIfNew(observation) ?: return
        val stored = db.notificationObservationDao().getById(id)!!

        when (val result = registry.parse(input)) {
            is ParseResult.Parsed -> transactionRepository.ingest(stored, result.candidate)
            ParseResult.NotFinancial -> notificationRepository.update(stored.copy(parseStatus = ParseStatus.IGNORED))
            is ParseResult.Failed -> notificationRepository.update(stored.copy(parseStatus = ParseStatus.FAILED, parseFailureReason = result.reason))
        }
    }

    @Test
    fun sample2and3_endToEnd_becomesOneInternalTransfer() = runBlocking {
        // Sample 2: BCA receives Rp329.306 from SeaBank
        ingestNotification(
            SourceApp.MYBCA, "Catatan Finansial",
            "Pemasukan sebesar IDR 329,306.00 dari **KAS **CKY K di kategori Transfer Rekening.",
            postedAt = 1_000_000_000_000
        )
        // Sample 3: SeaBank realtime transfer out Rp329.306, 20s later
        ingestNotification(
            SourceApp.SEABANK, "Realtime Transfer",
            "Kamu baru melakukan transfer real-time senilai Rp329.306 ...",
            postedAt = 1_000_000_020_000
        )

        val all = db.transactionDao().getAllOnce()
        assertEquals("expected exactly one merged transaction, not two separate legs", 1, all.size)
        val tx = all.single()
        assertTrue(tx.isInternalTransfer)
        assertEquals(TransactionType.INTERNAL_TRANSFER, tx.type)
        assertEquals(329_306L, tx.amount)
        assertEquals(seabankAccountId, tx.sourceAccountId)
        assertEquals(bcaAccountId, tx.destinationAccountId)

        // Ledger + balances reflect both legs.
        val bca = accountRepository.getById(bcaAccountId)!!
        val seabank = accountRepository.getById(seabankAccountId)!!
        assertEquals(15_000_000 + 329_306, bca.currentEstimatedBalance)
        assertEquals(7_500_000 - 329_306, seabank.currentEstimatedBalance)

        // Internal transfers must not affect cashflow totals (PRD §11).
        val cashflowTx = db.transactionDao().getAllOnce().filter { !it.isInternalTransfer }
        assertTrue(cashflowTx.isEmpty())
    }

    @Test
    fun sample4and5_endToEnd_becomesOneInternalTransfer() = runBlocking {
        // Sample 4: ShopeePay receives Rp10.000 top-up
        ingestNotification(
            SourceApp.SHOPEEPAY, "Isi Saldo Berhasil",
            "Pengisian saldo sebesar Rp10.000 telah ditambahkan ke ShopeePay-mu. Saldo saat ini sebesar Rp25.191.",
            postedAt = 2_000_000_000_000, notificationKey = "shopee-topup", eventTime = 2_000_000_000_000
        )
        assertTrue(db.transactionConfirmationDao().due(System.currentTimeMillis()).isEmpty())
        assertEquals(1, db.transactionConfirmationDao().due(Long.MAX_VALUE).size)
        // Sample 5: SeaBank sends Rp10.000 to ShopeePay, 15s later
        ingestNotification(
            SourceApp.SEABANK, "Pembayaran Berhasil",
            "Kamu telah melakukan transfer virtual account sebesar Rp10.000 kepada ShopeePay ...",
            postedAt = 2_000_000_015_000
        )

        val all = db.transactionDao().getAllOnce()
        assertEquals(1, all.size)
        val tx = all.single()
        assertTrue(tx.isInternalTransfer)
        assertEquals(ReviewStatus.CONFIRMED, tx.reviewStatus) // explicit destination hint => auto-match
        assertEquals(seabankAccountId, tx.sourceAccountId)
        assertEquals(shopeepayAccountId, tx.destinationAccountId)

        // A reported snapshot must not silently overwrite the opening balance plus ledger delta.
        val shopeepay = accountRepository.getById(shopeepayAccountId)!!
        assertEquals(35_000L, shopeepay.currentEstimatedBalance)
        // Incoming leg re-posted with a new post time after merging must retain event identity.
        ingestNotification(SourceApp.SHOPEEPAY, "Isi Saldo Berhasil",
            "Pengisian saldo sebesar Rp10.000 telah ditambahkan ke ShopeePay-mu. Saldo saat ini sebesar Rp25.191.",
            2_000_000_020_000, notificationKey = "shopee-topup", eventTime = 2_000_000_000_000)
        assertEquals(1, db.transactionDao().getAllOnce().size)
        assertEquals(35_000L, accountRepository.getById(shopeepayAccountId)!!.currentEstimatedBalance)
        assertEquals(listOf(tx.id), db.transactionConfirmationDao().due(System.currentTimeMillis()).map { it.transactionId })
        db.transactionConfirmationDao().handled(tx.id)
        ingestNotification(SourceApp.SEABANK, "Pembayaran Berhasil", "Kamu telah melakukan transfer virtual account sebesar Rp10.000 kepada ShopeePay ...", 2_000_000_015_000)
        assertTrue(db.transactionConfirmationDao().due(Long.MAX_VALUE).isEmpty())
    }

    @Test
    fun sample8_bcaExpense_neverInventsMerchant() = runBlocking {
        ingestNotification(
            SourceApp.MYBCA, "Catatan Finansial",
            "Pengeluaran sebesar IDR 1.00 di kategori Belanja Bulanan.",
            postedAt = 3_000_000_000_000
        )

        val tx = db.transactionDao().getAllOnce().single()
        assertEquals(1L, tx.amount)
        assertEquals(null, tx.merchantName)
        assertEquals(bcaAccountId, tx.sourceAccountId)
    }

    @Test
    fun duplicateNotification_doesNotCreateSecondTransaction() = runBlocking {
        val title = "Pembayaran QRIS berhasil"
        val text = "Pembayaran QRIS untuk PopCorn Technology sebesar 50.500 telah berhasil."
        ingestNotification(SourceApp.SEABANK, title, text, postedAt = 4_000_000_000_000)
        // The exact same notification delivered again (e.g. OS redelivery) has an identical raw hash.
        ingestNotification(SourceApp.SEABANK, title, text, postedAt = 4_000_000_000_000)

        assertEquals(1, db.transactionDao().getAllOnce().size)
    }

    @Test fun oldNotificationDoesNotCountOpeningBalanceTwice() = runBlocking {
        val account = accountRepository.getById(seabankAccountId)!!
        db.accountDao().update(account.copy(openingBalanceDate = 5000L))
        ingestNotification(SourceApp.SEABANK, "Pembayaran QRIS berhasil",
            "Pembayaran QRIS untuk PopCorn Technology sebesar 50.500 telah berhasil.", 4000L)
        assertTrue(db.transactionDao().getAllOnce().isEmpty())
        assertEquals(7500000L, accountRepository.getById(seabankAccountId)!!.currentEstimatedBalance)
    }

    @Test fun twoPurchasesAtSameMerchantRemainTwoTransactions() = runBlocking {
        val body = "Pembayaran QRIS untuk PopCorn Technology sebesar 50.500 telah berhasil."
        ingestNotification(SourceApp.SEABANK, "Pembayaran QRIS berhasil", body, 10000L)
        ingestNotification(SourceApp.SEABANK, "Pembayaran QRIS berhasil", body, 20000L)
        assertEquals(2, db.transactionDao().getAllOnce().size)
        assertEquals(7500000L - 101000L, accountRepository.getById(seabankAccountId)!!.currentEstimatedBalance)
    }

    @Test fun retryingSameObservationDoesNotApplyLedgerTwice() = runBlocking {
        val body = "Pembayaran QRIS untuk PopCorn Technology sebesar 50.500 telah berhasil."
        ingestNotification(SourceApp.SEABANK, "Pembayaran QRIS berhasil", body, 10000L)
        val observation = db.notificationObservationDao().getById(1L)!!
        val result = registry.parse(NotificationInput(SourceApp.SEABANK, observation.title, body, postedAt = 10000L)) as ParseResult.Parsed
        transactionRepository.ingest(observation.copy(parseStatus = ParseStatus.PENDING), result.candidate)
        assertEquals(1, db.transactionDao().getAllOnce().size)
        assertEquals(7500000L - 50500L, accountRepository.getById(seabankAccountId)!!.currentEstimatedBalance)
    }

    @Test fun ignoredTransactionReversesItsBalanceExactlyOnce() = runBlocking {
        ingestNotification(SourceApp.SEABANK, "Pembayaran QRIS berhasil",
            "Pembayaran QRIS untuk PopCorn Technology sebesar 50.500 telah berhasil.", 10000L)
        val id = db.transactionDao().getAllOnce().single().id
        transactionRepository.markIgnored(id)
        transactionRepository.markIgnored(id)
        assertEquals(7500000L, accountRepository.getById(seabankAccountId)!!.currentEstimatedBalance)
        assertTrue(db.ledgerEntryDao().getAllOnce().isEmpty())
    }

    @Test fun paymentIsNeverMergedWithIncomingTopup() = runBlocking {
        ingestNotification(SourceApp.SEABANK, "Pembayaran QRIS berhasil",
            "Pembayaran QRIS untuk PopCorn Technology sebesar 10.000 telah berhasil.", 10000L)
        ingestNotification(SourceApp.SHOPEEPAY, "Isi Saldo Berhasil",
            "Pengisian saldo sebesar Rp10.000 telah ditambahkan ke ShopeePay-mu.", 11000L)
        assertEquals(2, db.transactionDao().getAllOnce().size)
        assertTrue(db.transactionDao().getAllOnce().none { it.isInternalTransfer })
    }

    @Test fun failedLedgerWriteRollsBackTransactionAndKeepsObservationPending() = runBlocking {
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_ledger BEFORE INSERT ON ledger_entries BEGIN SELECT RAISE(ABORT, 'test failure'); END")
        var failed = false
        try {
            ingestNotification(SourceApp.SEABANK, "Pembayaran QRIS berhasil",
                "Pembayaran QRIS untuk PopCorn Technology sebesar 50.500 telah berhasil.", 10000L)
        } catch (_: Exception) { failed = true }
        assertTrue(failed)
        assertTrue(db.transactionDao().getAllOnce().isEmpty())
        assertEquals(1, notificationRepository.getPending().size)
        assertEquals(7500000L, accountRepository.getById(seabankAccountId)!!.currentEstimatedBalance)
    }

    @Test fun myBcaThreeRupiahRedeliveryUsesOriginalEventIdentity() = runBlocking {
        val body = "Pengeluaran sebesar IDR 3.00 di kategori Belanja Bulanan."
        ingestNotification(SourceApp.MYBCA, "Catatan Finansial", body, 10000L, "bca:key", 9000L)
        ingestNotification(SourceApp.MYBCA, "Catatan Finansial", body, 12000L, "bca:key", 9000L)
        assertEquals(1, db.transactionDao().getAllOnce().size)
        assertEquals(3L, com.luxwallet.app.core.common.CashflowMath.totalExpense(db.transactionDao().getAllOnce()))
        assertEquals(15000000L - 3L, accountRepository.getById(bcaAccountId)!!.currentEstimatedBalance)
    }

    @Test fun myBcaAmbiguousDuplicateIsHeldUntilConfirmedAsSeparatePayment() = runBlocking {
        val body = "Pengeluaran sebesar IDR 3.00 di kategori Belanja Bulanan."
        ingestNotification(SourceApp.MYBCA, "Catatan Finansial", body, 10000L, "first", 9000L)
        ingestNotification(SourceApp.MYBCA, "Catatan Finansial", body, 12000L, "second", 11000L)
        val held = db.transactionDao().getAllOnce().single { it.reviewReason == com.luxwallet.app.core.model.ReviewReason.POSSIBLE_DUPLICATE }
        assertEquals(3L, com.luxwallet.app.core.common.CashflowMath.totalExpense(db.transactionDao().getAllOnce()))
        assertEquals(15000000L - 3L, accountRepository.getById(bcaAccountId)!!.currentEstimatedBalance)
        transactionRepository.confirm(held.id)
        transactionRepository.confirm(held.id)
        assertEquals(6L, com.luxwallet.app.core.common.CashflowMath.totalExpense(db.transactionDao().getAllOnce()))
        assertEquals(15000000L - 6L, accountRepository.getById(bcaAccountId)!!.currentEstimatedBalance)
    }

    @Test fun myBcaSameAmountDifferentCategoryRemainsIndependent() = runBlocking {
        ingestNotification(SourceApp.MYBCA, "Catatan Finansial", "Pengeluaran sebesar IDR 3.00 di kategori Belanja Bulanan.", 10000L)
        ingestNotification(SourceApp.MYBCA, "Catatan Finansial", "Pengeluaran sebesar IDR 3.00 di kategori Makanan.", 12000L)
        assertEquals(6L, com.luxwallet.app.core.common.CashflowMath.totalExpense(db.transactionDao().getAllOnce()))
    }

    @Test fun myBcaReplayAfterRawRetentionDoesNotDoubleCount() = runBlocking {
        val body = "Pengeluaran sebesar IDR 3.00 di kategori Belanja Bulanan."
        ingestNotification(SourceApp.MYBCA, "Catatan Finansial", body, 10000L, "bca:key", 9000L)
        notificationRepository.purgeAccordingToPolicy(com.luxwallet.app.core.model.RawRetentionPolicy.NEVER)
        assertEquals("", db.notificationObservationDao().getById(1L)!!.text)
        ingestNotification(SourceApp.MYBCA, "Catatan Finansial", body, 12000L, "bca:key", 9000L)
        assertEquals(1, db.transactionDao().getAllOnce().size)
    }

    @Test fun changingAccountValueCreatesAdjustmentWithoutIncomeOrExpense() = runBlocking {
        transactionRepository.setAccountBalance(bcaAccountId, 10000000L)
        assertEquals(10000000L, accountRepository.getById(bcaAccountId)!!.currentEstimatedBalance)
        val all = db.transactionDao().getAllOnce()
        assertEquals(TransactionType.BALANCE_ADJUSTMENT, all.single().type)
        assertEquals(0L, com.luxwallet.app.core.common.CashflowMath.totalExpense(all))
        assertEquals(0L, com.luxwallet.app.core.common.CashflowMath.totalIncome(all))
    }

    @Test fun repeatedHeldNotificationDoesNotCreateMoreReviewRows() = runBlocking {
        val body = "Pengeluaran sebesar IDR 3.00 di kategori Belanja Bulanan."
        ingestNotification(SourceApp.MYBCA, "Catatan Finansial", body, 10000L, "first", 9000L)
        ingestNotification(SourceApp.MYBCA, "Catatan Finansial", body, 12000L, "second", 11000L)
        ingestNotification(SourceApp.MYBCA, "Catatan Finansial", body, 13000L, "second", 11000L)
        assertEquals(2, db.transactionDao().getAllOnce().size)
        assertEquals(15000000L - 3L, accountRepository.getById(bcaAccountId)!!.currentEstimatedBalance)
    }

    @Test fun upgradingReviewsExistingDoubleCountAndRestoresBalanceOnce() = runBlocking {
        val body = "Pengeluaran sebesar IDR 3.00 di kategori Belanja Bulanan."
        ingestNotification(SourceApp.MYBCA, "Catatan Finansial", body, 10000L)
        ingestNotification(SourceApp.MYBCA, "Catatan Finansial", body, 50000L)
        assertEquals(15000000L - 6L, accountRepository.getById(bcaAccountId)!!.currentEstimatedBalance)
        // Recreate the v1 state: both notifications were counted, although posted seconds apart.
        val second = db.notificationObservationDao().getById(2)!!
        db.notificationObservationDao().update(second.copy(postedAt = 12000L, contentHash = null))
        transactionRepository.reviewExistingDuplicates()
        transactionRepository.reviewExistingDuplicates()
        assertEquals(15000000L - 3L, accountRepository.getById(bcaAccountId)!!.currentEstimatedBalance)
        assertEquals(1, db.transactionDao().getAllOnce().count { it.reviewReason == com.luxwallet.app.core.model.ReviewReason.POSSIBLE_DUPLICATE })
    }
    @Test fun crossBcaReferenceMergesOneEventAndBothObservations() = runBlocking {
        ingestNotification(SourceApp.MYBCA, "Catatan Finansial", "Pengeluaran sebesar IDR 3.00 di kategori Belanja Bulanan. No Ref: ABC12345", 10000, packageName = "com.bca.mybca")
        ingestNotification(SourceApp.MYBCA, "BCA mobile", "Pembayaran QRIS berhasil Rp3.00 Merchant: TOKO INTAN; No Ref: ABC12345", 12000, packageName = "com.bca")
        assertEquals(1, db.transactionDao().getAllOnce().size)
        assertEquals(1, db.ledgerEntryDao().getAllOnce().size)
        assertEquals(2, db.notificationObservationDao().linkedInRange(0, Long.MAX_VALUE).size)
        assertEquals(1, db.notificationObservationDao().linkedInRange(0, Long.MAX_VALUE).map { it.linkedTransactionId }.distinct().size)
        assertEquals(14999997L, accountRepository.getById(bcaAccountId)!!.currentEstimatedBalance)
    }
    @Test fun crossBcaWithoutStrongIdentityIsHeldForReview() = runBlocking {
        ingestNotification(SourceApp.MYBCA, "Catatan Finansial", "Pengeluaran sebesar IDR 3.00 di kategori Belanja Bulanan.", 10000, packageName = "com.bca.mybca")
        ingestNotification(SourceApp.MYBCA, "BCA mobile", "Pembayaran QRIS berhasil Rp3.00 Merchant: TOKO INTAN", 12000, packageName = "com.bca")
        assertEquals(2, db.transactionDao().getAllOnce().size)
        assertEquals(1, db.transactionDao().getAllOnce().count { it.reviewReason == com.luxwallet.app.core.model.ReviewReason.POSSIBLE_DUPLICATE })
        assertEquals(14999997L, accountRepository.getById(bcaAccountId)!!.currentEstimatedBalance)
    }
    @Test fun crossBcaDifferentPartiesOrTimesRemainIndependent() = runBlocking {
        ingestNotification(SourceApp.MYBCA, "BCA mobile", "Pembayaran QRIS berhasil Rp3.00 Merchant: TOKO INTAN", 10000, packageName = "com.bca")
        ingestNotification(SourceApp.MYBCA, "myBCA", "Pembayaran QRIS berhasil Rp3.00 Merchant: TOKO BERLIAN", 12000, packageName = "com.bca.mybca")
        ingestNotification(SourceApp.MYBCA, "myBCA", "Pembayaran QRIS berhasil Rp3.00 Merchant: TOKO INTAN", 200000, packageName = "com.bca.mybca")
        assertEquals(3, db.transactionDao().getAllOnce().size)
        assertEquals(14999991L, accountRepository.getById(bcaAccountId)!!.currentEstimatedBalance)
    }
    @Test fun detailsSaveIsAtomicAndDoesNotWriteAnotherLedger() = runBlocking {
        ingestNotification(SourceApp.MYBCA, "Catatan Finansial", "Pengeluaran sebesar IDR 3.00 di kategori Belanja Bulanan.", 10000)
        val id = db.transactionDao().getAllOnce().single().id
        val category = categoryRepository.resolveOrCreateTopLevel("Food & Drink")
        repeat(2) { transactionRepository.saveDetails(id, "Toko", "Catatan", category, false, true) }
        assertEquals(1, db.transactionDao().getAllOnce().size)
        assertEquals(1, db.ledgerEntryDao().getAllOnce().size)
        assertEquals(category, db.transactionDao().getById(id)!!.categoryId)
        assertEquals(14999997L, accountRepository.getById(bcaAccountId)!!.currentEstimatedBalance)
    }
    @Test fun assetNotesDeleteAndLegacyRestorePreserveLedger() = runBlocking {
        val assets = com.luxwallet.app.data.AssetRepository(db.assetDao())
        val asset = com.luxwallet.app.core.database.entity.AssetEntity(name = "Emas", assetClass = com.luxwallet.app.core.model.AssetClass.EMAS, currentValue = 100000, updatedAt = 1)
        val id = assets.upsert(asset)
        assets.upsert(asset.copy(id = id, currentValue = 200000, notes = "Simpan sampai 2028"))
        assertEquals("Simpan sampai 2028", db.assetDao().getAllOnce().single().notes)
        assertEquals(200000L, assets.observeTotalValue().first())
        db.assetDao().setArchived(id, true)
        assertEquals(0L, assets.observeTotalValue().first())
        assertTrue(db.assetDao().getAllOnce().single().isArchived)
        assets.restore(id)
        assertEquals(200000L, assets.observeTotalValue().first())
        assets.delete(db.assetDao().getAllOnce().single())
        assertTrue(db.assetDao().getAllOnce().isEmpty())
        ingestNotification(SourceApp.MYBCA, "Catatan Finansial", "Pengeluaran sebesar IDR 3.00 di kategori Belanja Bulanan.", 10000)
        transactionRepository.updateAccountValue(bcaAccountId, "BCA pribadi", 14999997)
        accountRepository.setActive(bcaAccountId, false)
        assertEquals(1, db.transactionDao().getAllOnce().size)
        assertEquals(1, db.ledgerEntryDao().getAllOnce().size)
        assertEquals("BCA pribadi", accountRepository.getById(bcaAccountId)!!.name)
        assertTrue(accountRepository.observeActiveAccounts().first().none { it.id == bcaAccountId })
        accountRepository.setActive(bcaAccountId, true)
        assertEquals(14999997L, accountRepository.getById(bcaAccountId)!!.currentEstimatedBalance)
    }

    @Test fun multipleBcaAccountsStayUncountedUntilUserAssignsAccount() = runBlocking {
        accountRepository.createAccount("BCA kedua", AccountKind.BANK, AccountProvider.BCA, 0, 0)
        val body = "Pengeluaran sebesar IDR 3.00 di kategori Belanja Bulanan."
        ingestNotification(SourceApp.MYBCA, "Catatan Finansial", body, 10000, "same", 9000)
        ingestNotification(SourceApp.MYBCA, "Catatan Finansial", body, 12000, "same", 9000)
        val tx = db.transactionDao().getAllOnce().single()
        assertEquals(null, tx.sourceAccountId)
        assertEquals(0L, com.luxwallet.app.core.common.CashflowMath.totalExpense(listOf(tx)))
        assertTrue(db.ledgerEntryDao().getAllOnce().isEmpty())
        transactionRepository.saveDetails(tx.id, "", "", null, false, false, bcaAccountId)
        transactionRepository.saveDetails(tx.id, "", "", null, false, false, bcaAccountId)
        assertEquals(1, db.ledgerEntryDao().getAllOnce().size)
        assertEquals(14999997L, accountRepository.getById(bcaAccountId)!!.currentEstimatedBalance)
        assertEquals(3L, com.luxwallet.app.core.common.CashflowMath.totalExpense(db.transactionDao().getAllOnce()))
    }

    private suspend fun reviewAndFailed(): Pair<Long, Long> {
        ingestNotification(SourceApp.MYBCA, "Catatan Finansial", "Pengeluaran sebesar IDR 3.00 di kategori Belanja Bulanan.", 10000)
        val tx = db.transactionDao().getAllOnce().single()
        db.transactionDao().update(tx.copy(reviewStatus = ReviewStatus.NEEDS_REVIEW))
        ingestNotification(SourceApp.MYBCA, "Pesan bank", "Pembayaran IDR 5.00 dengan format baru", 20000)
        return tx.id to db.notificationObservationDao().getByStatus(ParseStatus.FAILED).single().id
    }
    @Test fun bulkReviewDeleteHandlesMixedRowsOnceAndSkipsConfirmedTransactions() = runBlocking {
        val (tx, obs) = reviewAndFailed()
        val confirmed = transactionRepository.insertManual(TransactionType.EXPENSE, com.luxwallet.app.core.model.TransactionDirection.OUT, 7, bcaAccountId)
        assertEquals(2, transactionRepository.dismissReview(setOf(tx, confirmed), setOf(obs)))
        assertEquals(0, transactionRepository.dismissReview(setOf(tx, confirmed), setOf(obs)))
        assertEquals(14999993L, accountRepository.getById(bcaAccountId)!!.currentEstimatedBalance)
        assertEquals(ReviewStatus.IGNORED, db.transactionDao().getById(tx)!!.reviewStatus)
        assertEquals(ReviewStatus.CONFIRMED, db.transactionDao().getById(confirmed)!!.reviewStatus)
        assertEquals(ParseStatus.IGNORED, db.notificationObservationDao().getById(obs)!!.parseStatus)
        assertEquals(2, db.transactionDao().getAllOnce().size)
        assertEquals(1, db.ledgerEntryDao().getAllOnce().size)
    }
    @Test fun bulkReviewFailureRollsBackEveryBalanceAndStatusChange() = runBlocking {
        val (tx, obs) = reviewAndFailed()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_bulk BEFORE UPDATE OF parseStatus ON notification_observations WHEN NEW.parseStatus = 'IGNORED' BEGIN SELECT RAISE(ABORT, 'test failure'); END")
        var failed = false
        try { transactionRepository.dismissReview(setOf(tx), setOf(obs)) } catch (_: Exception) { failed = true }
        assertTrue(failed)
        assertEquals(ReviewStatus.NEEDS_REVIEW, db.transactionDao().getById(tx)!!.reviewStatus)
        assertEquals(ParseStatus.FAILED, db.notificationObservationDao().getById(obs)!!.parseStatus)
        assertEquals(14999997L, accountRepository.getById(bcaAccountId)!!.currentEstimatedBalance)
        assertEquals(1, db.ledgerEntryDao().getAllOnce().size)
    }

    @Test fun actualSeaBankIncomingCreditsAmountOnceNotAccountNumber() = runBlocking {
        val body = "kamu menerima transfer saldo senilai Rp647.500 ke rekening 3422. dst."
        repeat(2) { ingestNotification(SourceApp.SEABANK, "TRANSFER MASUK", body, 10000, "incoming-1") }
        val tx = db.transactionDao().getAllOnce().single()
        assertEquals(647500L, tx.amount)
        assertEquals(com.luxwallet.app.core.model.TransactionDirection.IN, tx.direction)
        assertEquals(8147500L, accountRepository.getById(seabankAccountId)!!.currentEstimatedBalance)
        assertEquals(647500L, db.ledgerEntryDao().getAllOnce().single().deltaAmount)
    }
    @Test fun noteOnlyEditDoesNotResetBalanceAfterNewNotification() = runBlocking {
        ingestNotification(SourceApp.MYBCA, "Catatan Finansial", "Pengeluaran sebesar IDR 3.00 di kategori Belanja Bulanan.", 10000)
        transactionRepository.updateAccountValue(bcaAccountId, "Belanja", null, "Uang operasional")
        assertEquals(14999997L, accountRepository.getById(bcaAccountId)!!.currentEstimatedBalance)
        assertEquals("Uang operasional", accountRepository.getById(bcaAccountId)!!.notes)
        assertEquals(1, db.transactionDao().getAllOnce().size)
        assertEquals(1, db.ledgerEntryDao().getAllOnce().size)
    }
    private suspend fun failedSeaBank(): Long = notificationRepository.insertIfNew(NotificationObservationEntity(
        sourceApp = SourceApp.SEABANK, packageName = "id.co.bankbkemobile.digitalbank", notificationKey = "old-incoming",
        title = "TRANSFER MASUK", text = "kamu menerima transfer saldo senilai Rp647.500 ke rekening 3422.",
        bigText = null, subText = null, textLines = null,
        postedAt = 10000, receivedAt = 10000, rawPayloadHash = "old-incoming", parserVersion = 3, parseStatus = ParseStatus.FAILED))!!
    @Test fun explicitRetryOfFailedIncomingIsAtomicAndIdempotent() = runBlocking {
        val id = failedSeaBank()
        repeat(2) { transactionRepository.retryFailedNotification(id, registry) }
        assertEquals(1, db.transactionDao().getAllOnce().size)
        assertEquals(8147500L, accountRepository.getById(seabankAccountId)!!.currentEstimatedBalance)
        assertEquals(ParseStatus.PARSED, db.notificationObservationDao().getById(id)!!.parseStatus)
        assertEquals(4, db.notificationObservationDao().getById(id)!!.parserVersion)
    }
    @Test fun retryNeverAddsHistoricalIncomeAfterBalanceWasCorrected() = runBlocking {
        val id = failedSeaBank()
        transactionRepository.updateAccountValue(seabankAccountId, "SeaBank", 8147500)
        val count = db.transactionDao().getAllOnce().size
        transactionRepository.retryFailedNotification(id, registry)
        assertEquals(count, db.transactionDao().getAllOnce().size)
        assertEquals(8147500L, accountRepository.getById(seabankAccountId)!!.currentEstimatedBalance)
        assertEquals(ParseStatus.FAILED, db.notificationObservationDao().getById(id)!!.parseStatus)
    }

}

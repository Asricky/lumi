package com.luxwallet.app.integration

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import com.luxwallet.app.LuxWalletApp
import com.luxwallet.app.core.model.*
import com.luxwallet.app.core.ui.theme.LuxWalletTheme
import com.luxwallet.app.navigation.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = LuxWalletApp::class)
class AppJourneyTest {
    @get:Rule val compose = createComposeRule()
    private val app get() = ApplicationProvider.getApplicationContext<LuxWalletApp>()
    private lateinit var nav: NavHostController
    private lateinit var back: OnBackPressedDispatcher
    @Before fun prepare() = runBlocking {
        app.preferences.planV2Ready.first { it }
        app.preferences.setProfileName("")
        app.accountRepository.createAccount("Rekening uji", AccountKind.BANK, AccountProvider.BCA, 100_000, 0)
        app.categoryRepository.resolveOrCreateTopLevel("Makan uji")
        Unit
    }
    @After fun cleanUp() = runBlocking {
        app.applicationScope.coroutineContext[Job]?.cancelAndJoin()
        app.database.close()
    }
    private fun launch() {
        compose.setContent {
            nav = rememberNavController()
            back = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            LuxWalletTheme { LuxAppScaffold(nav) }
        }
    }
    @Test
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    @Config(qualifiers="w393dp-h851dp-mdpi")
    fun reportsMenuMonthPrivacyAndBackRemainUsable() {
        lateinit var androidView: android.view.View
        compose.setContent {
            androidView = androidx.compose.ui.platform.LocalView.current
            nav = rememberNavController()
            LuxWalletTheme { LuxAppScaffold(nav) }
        }
        compose.onNodeWithText("Lainnya").performClick()
        compose.onNodeWithText("Laporan bulanan").performClick()
        compose.onNodeWithText("Buat preview PDF").assertIsDisplayed()
        compose.onNode(isToggleable()).assertIsOff()
        compose.onNode(isToggleable()).performClick().assertIsOn()
        compose.onNodeWithContentDescription("Pilih Bulan laporan").performClick()
        val previous=java.time.YearMonth.now().minusMonths(1).format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy",java.util.Locale("id","ID")))
        compose.onNodeWithText(previous).performClick()
        compose.onNodeWithText(previous).assertIsDisplayed()
        java.io.File("build/reports/ui").mkdirs()
        val bitmap=android.graphics.Bitmap.createBitmap(androidView.width,androidView.height,android.graphics.Bitmap.Config.ARGB_8888)
        compose.runOnIdle { androidView.draw(android.graphics.Canvas(bitmap)) }
        java.io.File("build/reports/ui/reports-v7.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
        compose.onNodeWithContentDescription("Kembali").performClick()
        compose.runOnIdle { Assert.assertEquals(LuxDestinations.MORE, nav.currentDestination?.route) }
        compose.onNodeWithText("Beranda").performClick()
        compose.onNodeWithText("Hi there 👋").assertIsDisplayed()
    }
    @Test
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    @Config(qualifiers="w320dp-h740dp-mdpi")
    fun calculatorFormatsPasteResultAndKeepsNavigationUsable() {
        lateinit var view: android.view.View
        compose.setContent {
            view=androidx.compose.ui.platform.LocalView.current
            nav=rememberNavController()
            LuxWalletTheme { LuxAppScaffold(nav) }
        }
        compose.runOnIdle { nav.openScreen(LuxDestinations.CALCULATOR) }
        compose.onNode(hasSetTextAction()).performTextInput("1.500.000,50×2")
        compose.onNodeWithText("=").performScrollTo().performClick()
        compose.onNodeWithText("3.000.001").performScrollTo().assertIsDisplayed()
        val bitmap=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
        compose.runOnIdle { view.draw(android.graphics.Canvas(bitmap)) }
        java.io.File("build/reports/ui").mkdirs()
        java.io.File("build/reports/ui/calculator-v9.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
        compose.onNodeWithContentDescription("+").performScrollTo().performClick()
        compose.onNodeWithContentDescription("9").performScrollTo().performClick()
        compose.onNodeWithText("=").performScrollTo().performClick()
        compose.onNodeWithText("3.000.010").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Bersihkan perhitungan").performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performTextInput("5000÷0")
        compose.onNodeWithText("=").performScrollTo().performClick()
        compose.onNodeWithText("Tidak bisa dibagi nol").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Kembali").performClick()
        compose.runOnIdle { Assert.assertEquals(LuxDestinations.HOME,nav.currentDestination?.route) }
    }
    @Test
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    @Config(qualifiers="w393dp-h851dp-mdpi")
    fun expenseBarsSelectDatesAndHideAllFinancialData() {
        lateinit var view: android.view.View
        var hidden by mutableStateOf(false)
        var selected by mutableStateOf(java.time.LocalDate.of(2026,9,1))
        val points=(1..29).map { com.luxwallet.app.engine.DailyExpense(java.time.LocalDate.of(2026,9,it),if(it==20) 95000 else if(it%3==0) 0 else 12000L+it*1000) }
        compose.setContent {
            view=androidx.compose.ui.platform.LocalView.current
            LuxWalletTheme { com.luxwallet.app.feature.calendar.ExpenseTrendChart(points,selected,hidden) {selected=it} }
        }
        compose.onNodeWithContentDescription("Pilih tanggal tren pengeluaran").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(19f) }
        compose.onNodeWithText("20 September 2026").assertIsDisplayed()
        compose.onNodeWithText("Rp95.000").assertIsDisplayed()
        val bitmap=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
        compose.runOnIdle { view.draw(android.graphics.Canvas(bitmap)) }
        java.io.File("build/reports/ui").mkdirs()
        java.io.File("build/reports/ui/expense-bars-v8.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
        compose.runOnIdle { hidden=true }
        compose.onNodeWithText("Rp95.000").assertDoesNotExist()
        compose.onNodeWithContentDescription("Pilih tanggal tren pengeluaran").assertDoesNotExist()
        compose.onNodeWithText("Tampilkan nominal untuk melihat grafik dan rinciannya.").assertIsDisplayed()
    }
    @Test fun actualManualEntryCanReturnEditSaveAndNavigateAgain() {
        launch()
        compose.onNodeWithContentDescription("Catat transaksi").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("Pilih rekening").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Nominal").performTextInput("3000")
        compose.onNodeWithText("Pilih rekening").performScrollTo().performClick()
        compose.onNodeWithText("Rekening uji").performClick()
        compose.onNodeWithText("Pilih kategori").performScrollTo().performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Makan uji"))
        compose.onNodeWithText("Makan uji").performClick()
        compose.onNodeWithText("Lanjut ke ringkasan").performScrollTo().performClick()
        compose.onNodeWithText("Periksa catatan").assertIsDisplayed()
        compose.onNodeWithContentDescription("Kembali").performClick()
        compose.onNodeWithText("3.000").assertIsDisplayed()
        compose.onNodeWithText("Lanjut ke ringkasan").performScrollTo().performClick()
        compose.onNodeWithText("Simpan transaksi").performScrollTo().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("Hi there 👋").fetchSemanticsNodes().isNotEmpty() }
        val transactions = runBlocking { app.transactionRepository.observeAll().first() }
        Assert.assertEquals(1, transactions.count { it.isManual && it.amount == 3000L })
        compose.onNodeWithText("Kalender").performClick()
        compose.onNodeWithText("Kalender keuangan").assertIsDisplayed()
        compose.onNodeWithText("Aset").performClick()
        compose.onNodeWithText("Aset saya").assertIsDisplayed()
        compose.runOnIdle { back.onBackPressed() }
        compose.onNodeWithText("Hi there 👋").assertIsDisplayed()
    }
    @Test fun abandoningDraftRequiresAnExplicitChoiceThenReturns() {
        launch()
        compose.onNodeWithContentDescription("Catat transaksi").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("Nominal").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Nominal").performTextInput("3200000")
        compose.onNodeWithContentDescription("Kembali").performClick()
        compose.onNodeWithText("Lanjut mengisi").performClick()
        compose.onNodeWithText("3.200.000").assertIsDisplayed()
        compose.onNodeWithContentDescription("Kembali").performClick()
        compose.onNodeWithText("Buang catatan").performClick()
        compose.onNodeWithText("Hi there 👋").assertIsDisplayed()
        Assert.assertTrue(runBlocking { app.transactionRepository.observeAll().first() }.none { it.isManual })
    }
    @Test
    @Config(sdk = [34], application = LuxWalletApp::class, qualifiers = "w320dp-h640dp-mdpi")
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    fun smallScreenProfileAssetsAndNotificationSettingsRemainUsable() {
        runBlocking { app.preferences.setProfileName("Lucas"); app.preferences.setAmountsHidden(false) }
        lateinit var androidView: android.view.View
        compose.setContent {
            androidView = androidx.compose.ui.platform.LocalView.current
            nav = rememberNavController()
            LuxWalletTheme { LuxAppScaffold(nav) }
        }
        compose.waitUntil(5000) { compose.onAllNodesWithText("Hi, Lucas 👋").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Aset").performClick()
        compose.onNodeWithText("Alokasi aset").assertIsDisplayed()
        fun capture(name: String) {
            compose.waitForIdle()
            val bitmap = android.graphics.Bitmap.createBitmap(androidView.width, androidView.height, android.graphics.Bitmap.Config.ARGB_8888)
            compose.runOnIdle { androidView.draw(android.graphics.Canvas(bitmap)) }
            java.io.File("build/reports/ui").mkdirs()
            java.io.File("build/reports/ui/$name").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
        capture("assets-320.png")
        compose.runOnIdle { nav.openScreen(LuxDestinations.ALERT_SETTINGS) }
        compose.onNodeWithText("Kabar transaksi, sesuai pilihanmu").assertIsDisplayed()
        compose.onNodeWithText("Peringatan budget").performScrollTo().assertIsDisplayed()
        capture("notifications-320.png")
        compose.onNodeWithContentDescription("Kembali").performClick()
        compose.onNodeWithText("Aset saya").assertIsDisplayed()
    }
    @Test
    @Config(sdk = [34], application = LuxWalletApp::class, qualifiers = "w393dp-h851dp-mdpi")
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    fun renderHomeAndCalendarInBothThemes() {
        runBlocking {
            app.preferences.setAmountsHidden(false)
            val today = java.time.LocalDate.now()
            app.paydayPlanRepository.save(com.luxwallet.app.engine.PaydayPlan(System.currentTimeMillis(), today.toEpochDay(),
                com.luxwallet.app.engine.PaydayPlan.nextPayday(today).toEpochDay(), 100_000))
        }
        var theme by mutableStateOf(com.luxwallet.app.core.ui.theme.LuxThemePreference.LIGHT)
        lateinit var androidView: android.view.View
        compose.setContent {
            androidView = androidx.compose.ui.platform.LocalView.current
            nav = rememberNavController()
            LuxWalletTheme(theme) { LuxAppScaffold(nav) }
        }
        compose.waitUntil(5000) { compose.onAllNodesWithText("Hi there 👋").fetchSemanticsNodes().isNotEmpty() && compose.onAllNodesWithText("Memuat…").fetchSemanticsNodes().isEmpty() }
        val dir = java.io.File("build/reports/ui").apply { mkdirs() }
        fun capture(name: String) {
            compose.waitForIdle()
            val bitmap = android.graphics.Bitmap.createBitmap(androidView.width, androidView.height, android.graphics.Bitmap.Config.ARGB_8888)
            compose.runOnIdle { androidView.draw(android.graphics.Canvas(bitmap)) }
            java.io.File(dir, name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
        capture("home-light.png")
        compose.onNodeWithText("Kalender").performClick()
        compose.onNodeWithText("Kalender keuangan").assertIsDisplayed()
        capture("calendar-light.png")
        compose.runOnIdle { theme = com.luxwallet.app.core.ui.theme.LuxThemePreference.DARK }
        capture("calendar-dark.png")
        compose.onNodeWithText("Beranda").performClick()
        capture("home-dark.png")
    }
    private fun reviewTransaction(): Long = runBlocking {
        val account = app.accountRepository.observeActiveAccounts().first().first()
        val id = app.transactionRepository.insertManual(TransactionType.EXPENSE, TransactionDirection.OUT, 3000, account.id)
        val tx = app.database.transactionDao().getById(id)!!
        app.database.transactionDao().update(tx.copy(reviewStatus = ReviewStatus.NEEDS_REVIEW, reviewReason = ReviewReason.UNKNOWN_MERCHANT))
        id
    }
    @Test
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    @Config(sdk = [34], application = LuxWalletApp::class, qualifiers = "w393dp-h851dp-mdpi")
    fun reviewSaveConfirmsCategoryReturnsHomeAndDoesNotDoubleCount() {
        val id = reviewTransaction()
        launch()
        compose.runOnIdle { nav.openScreen(LuxDestinations.NEEDS_REVIEW) }
        compose.waitUntil(5000) { compose.onAllNodesWithText("Tinjau").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Tinjau").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("Pilih kategori").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Pilih kategori").performClick()
        compose.onNodeWithText("Food & Drink").assertIsDisplayed().performClick()
        compose.onNodeWithText("Simpan & konfirmasi").performScrollTo().performClick()
        try { compose.waitUntil(5000) { compose.onAllNodesWithText("Hi there 👋").fetchSemanticsNodes().isNotEmpty() } } catch (e: Throwable) { throw AssertionError(compose.onRoot().printToString(), e) }
        compose.runOnIdle { Assert.assertEquals(LuxDestinations.HOME, nav.currentDestination?.route) }
        runBlocking {
            Assert.assertEquals(1, app.database.transactionDao().getAllOnce().size)
            Assert.assertEquals(ReviewStatus.CONFIRMED, app.database.transactionDao().getById(id)!!.reviewStatus)
            Assert.assertEquals(97000L, app.accountRepository.observeActiveAccounts().first().first().currentEstimatedBalance)
        }
    }
    @Test fun ignoreInDetailsReturnsHomeAndReversesOnce() {
        val id = reviewTransaction()
        launch()
        compose.runOnIdle { nav.openScreen(LuxDestinations.transactionDetail(id)) }
        compose.waitUntil(5000) { compose.onAllNodesWithText("Abaikan duplikat").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Abaikan duplikat").performScrollTo().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("Hi there 👋").fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle { Assert.assertEquals(LuxDestinations.HOME, nav.currentDestination?.route) }
        runBlocking {
            Assert.assertEquals(1, app.database.transactionDao().getAllOnce().size)
            Assert.assertEquals(ReviewStatus.IGNORED, app.database.transactionDao().getById(id)!!.reviewStatus)
            Assert.assertEquals(100000L, app.accountRepository.observeActiveAccounts().first().first().currentEstimatedBalance)
        }
    }
    @Test
    @Config(sdk = [34], application = LuxWalletApp::class, qualifiers = "w320dp-h740dp-mdpi")
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    fun reviewDeleteRequiresConfirmationAndRemovesOnlyReviewItem() {
        val id = reviewTransaction()
        launch()
        compose.runOnIdle { nav.openScreen(LuxDestinations.NEEDS_REVIEW) }
        compose.waitUntil(5000) { compose.onAllNodesWithText("Hapus").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Hapus").performScrollTo().performClick()
        compose.onNodeWithText("Batal").performClick()
        compose.onNodeWithText("Tinjau").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Hapus").performScrollTo().performClick()
        compose.onNodeWithText("Hapus catatan").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("Semua sudah ditinjau.").fetchSemanticsNodes().isNotEmpty() }
        Assert.assertNotNull(runBlocking { app.database.transactionDao().getById(id) })
    }

    @Test
    @Config(sdk = [34], application = LuxWalletApp::class, qualifiers = "w320dp-h740dp-mdpi")
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    fun financialSummariesAndTrendRemainReadableOnSmallScreens() {
        runBlocking {
            app.preferences.setAmountsHidden(false)
            val today = java.time.LocalDate.now()
            val start = today.minusDays(2).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            val account = app.accountRepository.observeActiveAccounts().first().first()
            app.transactionRepository.setAccountBalance(account.id, 3200000)
            app.paydayPlanRepository.save(com.luxwallet.app.engine.PaydayPlan(start, today.minusDays(2).toEpochDay(),
                today.plusDays(3).toEpochDay(), 3200000, bills = 100000, buffer = 100000, business = 1000000, investment = 1500000))
            app.transactionRepository.insertManual(TransactionType.EXPENSE, TransactionDirection.OUT, 80000, account.id,
                transactionTime = System.currentTimeMillis() - 1000)
        }
        lateinit var view: android.view.View
        compose.setContent {
            view = androidx.compose.ui.platform.LocalView.current
            nav = rememberNavController()
            LuxWalletTheme { LuxAppScaffold(nav) }
        }
        fun capture(name: String) {
            compose.waitForIdle()
            val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            compose.runOnIdle { view.draw(android.graphics.Canvas(bitmap)) }
            java.io.File("build/reports/ui").mkdirs()
            java.io.File("build/reports/ui/$name").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
        compose.runOnIdle { nav.openScreen(LuxDestinations.PLANNER) }
        compose.waitUntil(5000) { compose.onAllNodesWithText("Sisa budget hari ini").fetchSemanticsNodes().isNotEmpty() }
        capture("planner-320.png")
        compose.runOnIdle { nav.openScreen(LuxDestinations.COACH) }
        compose.waitUntil(5000) { compose.onAllNodesWithText("Pengingat & ikon Lumi").fetchSemanticsNodes().isNotEmpty() }
        capture("coach-320.png")
        compose.runOnIdle { nav.openScreen(LuxDestinations.CALENDAR) }
        compose.onNodeWithText("Tren Pengeluaran").performScrollTo()
        compose.onNodeWithContentDescription("Pilih tanggal tren pengeluaran").performScrollTo()
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(0f) }
        compose.onNodeWithText(java.time.LocalDate.now().withDayOfMonth(1).format(java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy",java.util.Locale("id","ID")))).assertExists()
        capture("trend-320.png")
    }

    @Test
    @Config(sdk = [34], application = LuxWalletApp::class, qualifiers = "w393dp-h851dp-mdpi")
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    fun reviewShowsMessageAndDeletesOnlySnapshotSelectionTogether() {
        val first = reviewTransaction()
        val second = reviewTransaction()
        val now = System.currentTimeMillis()
        runBlocking {
            val original = com.luxwallet.app.core.database.entity.NotificationObservationEntity(
                sourceApp = SourceApp.MYBCA, packageName = "com.bca.mybca", notificationKey = "test",
                title = "Pembayaran berhasil", text = "Pembayaran QRIS Rp3.000 di TOKO INTAN.", bigText = null,
                subText = null, textLines = null, postedAt = now, receivedAt = now, rawPayloadHash = "review-preview",
                parserVersion = 3, parseStatus = ParseStatus.PARSED, linkedTransactionId = first)
            app.notificationRepository.insertIfNew(original)
            app.notificationRepository.insertIfNew(original.copy(notificationKey = "unknown", rawPayloadHash = "review-unknown",
                title = "Pesan bank", text = "Transfer masuk Rp50.000 perlu diperiksa.", parseStatus = ParseStatus.FAILED, linkedTransactionId = null))
        }
        var hidden by mutableStateOf(false)
        lateinit var view: android.view.View
        compose.setContent {
            view = androidx.compose.ui.platform.LocalView.current
            nav = rememberNavController()
            CompositionLocalProvider(com.luxwallet.app.core.common.LocalAmountsHidden provides hidden) {
                LuxWalletTheme { LuxAppScaffold(nav) }
            }
        }
        compose.runOnIdle { nav.openScreen(LuxDestinations.NEEDS_REVIEW) }
        compose.waitUntil(5000) { compose.onAllNodesWithText("3 catatan menunggu keputusan").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Pembayaran berhasil\nPembayaran QRIS Rp3.000 di TOKO INTAN.").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { hidden = true }
        compose.onNodeWithText("Pembayaran berhasil\nPembayaran QRIS Rp•.••• di TOKO INTAN.").assertIsDisplayed()
        compose.runOnIdle { hidden = false }
        compose.onNodeWithText("Pilih semua").performClick()
        compose.onNodeWithText("3 catatan dipilih").assertIsDisplayed()
        compose.onNodeWithText("Batal pilih").performClick()
        compose.onNodeWithContentDescription("Pilih catatan tx:$first").performScrollTo().performClick()
        compose.onNodeWithText("1 catatan dipilih").assertIsDisplayed()
        compose.onNodeWithText("Pilih semua").performClick()
        compose.waitForIdle()
        val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
        compose.runOnIdle { view.draw(android.graphics.Canvas(bitmap)) }
        java.io.File("build/reports/ui").mkdirs()
        java.io.File("build/reports/ui/review-v6.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        compose.onNodeWithText("Hapus terpilih (3)").performClick()
        compose.onNodeWithText("Batal").performClick()
        compose.onNodeWithText("Hapus terpilih (3)").performClick()
        val later = reviewTransaction() // arrived after the selection and confirmation opened
        compose.onNodeWithText("Hapus catatan").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("1 catatan menunggu keputusan").fetchSemanticsNodes().isNotEmpty() }
        runBlocking {
            Assert.assertEquals(ReviewStatus.IGNORED, app.database.transactionDao().getById(first)!!.reviewStatus)
            Assert.assertEquals(ReviewStatus.IGNORED, app.database.transactionDao().getById(second)!!.reviewStatus)
            Assert.assertEquals(ReviewStatus.NEEDS_REVIEW, app.database.transactionDao().getById(later)!!.reviewStatus)
            Assert.assertEquals(97000L, app.accountRepository.observeActiveAccounts().first().first().currentEstimatedBalance)
        }
    }

    @Test fun zeroMoneyFieldStartsFreshAndFormatsThousands() {
        var amount by mutableStateOf("0")
        compose.setContent { LuxWalletTheme {
            com.luxwallet.app.core.ui.component.MoneyField("Dana penyangga", amount, { amount = it })
        } }
        compose.onNodeWithText("Dana penyangga").performTextInput("6000")
        compose.runOnIdle { Assert.assertEquals("6000", amount) }
        compose.onNodeWithText("6.000").assertIsDisplayed()
        compose.onNodeWithText("Dana penyangga").performTextReplacement("0001500000")
        compose.runOnIdle { Assert.assertEquals("1500000", amount) }
        compose.onNodeWithText("1.500.000").assertIsDisplayed()
    }

    @Test fun assetNotesSaveAndPermanentDeleteWorkFromSheet() {
        runBlocking {
            app.preferences.setAmountsHidden(false)
            app.assetRepository.upsert(com.luxwallet.app.core.database.entity.AssetEntity(
                name = "Emas uji", assetClass = AssetClass.EMAS, currentValue = 1500000, updatedAt = 1))
        }
        launch()
        compose.runOnIdle { nav.openScreen(LuxDestinations.ASSETS) }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Utang"))
        compose.onNode(hasText("Lainnya") and hasAnyAncestor(hasScrollToIndexAction())).performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Emas uji"))
        compose.onNodeWithText("Emas uji").performScrollTo().performClick()
        compose.onNodeWithText("Catatan aset (opsional)").performScrollTo().performTextInput("Untuk dana cadangan")
        compose.onNodeWithText("Simpan perubahan").performScrollTo().performClick()
        compose.waitUntil(5000) { runBlocking { app.database.assetDao().getAllOnce().single().notes == "Untuk dana cadangan" } }
        compose.waitUntil(5000) { compose.onAllNodesWithText("Simpan perubahan").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("Untuk dana cadangan").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Emas uji").performScrollTo().performClick()
        compose.onNodeWithText("Hapus", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithText("Hapus catatan ini?").assertIsDisplayed()
        compose.onAllNodesWithText("Hapus").onLast().performClick()
        compose.waitUntil(5000) { runBlocking { app.database.assetDao().getAllOnce().isEmpty() } }
        compose.onNodeWithText("Beranda").performClick()
    }

    @Test fun confirmingCurrentCashKeepsEarlierSpendingAndEmptyAllocationsAreZero() {
        runBlocking {
            val account = app.accountRepository.observeActiveAccounts().first().first()
            app.transactionRepository.insertManual(TransactionType.EXPENSE, TransactionDirection.OUT, 20000,
                account.id, transactionTime = System.currentTimeMillis() - 1000)
            app.preferences.setAmountsHidden(false)
        }
        launch()
        compose.runOnIdle { nav.openScreen(LuxDestinations.PLANNER) }
        compose.waitForIdle()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Buat rencana"))
        compose.onNodeWithText("Buat rencana").performScrollTo().performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Saldo likuid yang tersedia sekarang"))
        compose.onNodeWithText("Saldo likuid yang tersedia sekarang").performTextInput("80000")
        listOf("Lindungi untuk modal bisnis", "Lindungi untuk investasi", "Transportasi per hari").forEach {
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(it))
            compose.onNodeWithText(it).performTextReplacement("0")
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Konfirmasi saldo & simpan rencana"))
        compose.onNodeWithText("Konfirmasi saldo & simpan rencana").performClick()
        compose.waitUntil(10000) { runBlocking { app.paydayPlanRepository.plans.first().isNotEmpty() } }
        runBlocking {
            val plan = app.paydayPlanRepository.plans.first().single()
            Assert.assertEquals(80000L, plan.availableCash)
            Assert.assertEquals(20000L, plan.spentBeforeCapture)
            Assert.assertEquals(0L, plan.bills)
            Assert.assertEquals(0L, plan.buffer)
            Assert.assertEquals(0L, plan.transportDaily)
            Assert.assertEquals(80000L, app.accountRepository.observeActiveAccounts().first().first().currentEstimatedBalance)
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Sisa budget hari ini"))
        compose.onNodeWithText("Sisa budget hari ini").assertIsDisplayed()
        compose.onNodeWithContentDescription("Kembali").performClick()
        compose.onNodeWithText("Beranda").assertExists()
    }

}

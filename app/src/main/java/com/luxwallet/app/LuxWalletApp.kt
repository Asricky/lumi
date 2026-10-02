package com.luxwallet.app

import android.app.Application
import androidx.room.Room
import androidx.work.Configuration
import com.luxwallet.app.core.common.AppPreferences
import com.luxwallet.app.core.database.LuxDatabase
import com.luxwallet.app.data.AccountRepository
import com.luxwallet.app.data.AssetRepository
import com.luxwallet.app.data.BackupRepository
import com.luxwallet.app.data.BudgetRepository
import com.luxwallet.app.data.CategoryRepository
import com.luxwallet.app.data.CsvExportRepository
import com.luxwallet.app.data.FinancialProfileRepository
import com.luxwallet.app.data.GoalRepository
import com.luxwallet.app.data.LiabilityRepository
import com.luxwallet.app.data.MerchantRuleRepository
import com.luxwallet.app.data.NotificationRepository
import com.luxwallet.app.data.TransactionRepository
import com.luxwallet.app.parser.core.ParserRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Simple hand-rolled DI container (PRD §54.2: "prefer simple deterministic implementations") —
 * no DI framework needed for a single-module, single-user-density app.
 */
class LuxWalletApp : Application(), Configuration.Provider {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: LuxDatabase by lazy {
        Room.databaseBuilder(this, LuxDatabase::class.java, LuxDatabase.DATABASE_NAME)
            .addMigrations(LuxDatabase.MIGRATION_1_2, LuxDatabase.MIGRATION_2_3, LuxDatabase.MIGRATION_3_4, LuxDatabase.MIGRATION_4_5, LuxDatabase.MIGRATION_5_6)
            .build()
    }

    val lumiState by lazy { com.luxwallet.app.data.LumiStateRepository(this) }
    val paydayPlanRepository by lazy { com.luxwallet.app.data.PaydayPlanRepository(database.paydayPlanDao()) }
    val preferences: AppPreferences by lazy { AppPreferences(this) }
    val parserRegistry: ParserRegistry by lazy { ParserRegistry() }

    val accountRepository: AccountRepository by lazy { AccountRepository(database.accountDao()) }
    val categoryRepository: CategoryRepository by lazy { CategoryRepository(database.categoryDao()) }
    val assetRepository: AssetRepository by lazy { AssetRepository(database.assetDao()) }
    val liabilityRepository: LiabilityRepository by lazy { LiabilityRepository(database.liabilityDao()) }
    val budgetRepository: BudgetRepository by lazy { BudgetRepository(database.budgetDao()) }
    val goalRepository: GoalRepository by lazy { GoalRepository(database.goalDao()) }
    val financialProfileRepository: FinancialProfileRepository by lazy { FinancialProfileRepository(database.financialProfileDao()) }
    val merchantRuleRepository: MerchantRuleRepository by lazy { MerchantRuleRepository(database.merchantRuleDao()) }
    val notificationRepository: NotificationRepository by lazy { NotificationRepository(database.notificationObservationDao()) }

    val transactionRepository: TransactionRepository by lazy {
        TransactionRepository(
            transactionDao = database.transactionDao(),
            ledgerEntryDao = database.ledgerEntryDao(),
            observationDao = database.notificationObservationDao(),
            accountDao = database.accountDao(),
            merchantRuleDao = database.merchantRuleDao(),
            categoryRepository = categoryRepository,
            database = database
        )
    }

    val backupRepository: BackupRepository by lazy { BackupRepository(this, database) }
    val csvExportRepository: CsvExportRepository by lazy { CsvExportRepository(database) }

    override fun onCreate() {
        super.onCreate()
        com.luxwallet.app.notification.ListenerRecovery.enqueue(this)
        com.luxwallet.app.notification.TransactionNotifications.createChannel(this)
        applicationScope.launch {
            kotlinx.coroutines.flow.combine(preferences.lumiIcon, lumiState.mood) { mode, mood ->
                if (mode == "AUTO") mood else runCatching { com.luxwallet.app.core.ui.component.LumiMood.valueOf(mode) }.getOrDefault(mood)
            }.distinctUntilChanged().collect { mood ->
                runCatching { com.luxwallet.app.core.ui.component.LumiLauncher.apply(this@LuxWalletApp, mood) }
            }
        }
        applicationScope.launch {
            database.transactionConfirmationDao().nextDue().distinctUntilChanged().collect { due ->
                if (due != null) com.luxwallet.app.notification.TransactionNotifications.schedule(this@LuxWalletApp, due)
            }
        }
        applicationScope.launch {
            preferences.coachEnabled.collect { com.luxwallet.app.notification.CoachNotifications.configure(this@LuxWalletApp, it) }
        }
        applicationScope.launch {
            categoryRepository.seedDefaultsIfEmpty()
            categoryRepository.resolveOrCreateTopLevel(com.luxwallet.app.engine.PaydayPlan.BILLS_CATEGORY)
            categoryRepository.resolveOrCreateTopLevel(com.luxwallet.app.core.common.TransportPlan.CATEGORY)
            if (!preferences.planV2Ready.first()) {
                val profile = financialProfileRepository.get()
                financialProfileRepository.save(profile.copy(savingsTargetMonthly = 1_000_000, investmentTargetMonthly = 1_500_000))
                transactionRepository.reviewExistingDuplicates()
                preferences.setPlanV2Ready()
            }
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}

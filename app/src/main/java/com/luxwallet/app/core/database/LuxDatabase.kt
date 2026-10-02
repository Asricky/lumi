package com.luxwallet.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.luxwallet.app.core.database.dao.AccountDao
import com.luxwallet.app.core.database.dao.AssetDao
import com.luxwallet.app.core.database.dao.BudgetDao
import com.luxwallet.app.core.database.dao.CategoryDao
import com.luxwallet.app.core.database.dao.FinancialProfileDao
import com.luxwallet.app.core.database.dao.GoalDao
import com.luxwallet.app.core.database.dao.LedgerEntryDao
import com.luxwallet.app.core.database.dao.LiabilityDao
import com.luxwallet.app.core.database.dao.MerchantRuleDao
import com.luxwallet.app.core.database.dao.NotificationObservationDao
import com.luxwallet.app.core.database.dao.TransactionDao
import com.luxwallet.app.core.database.entity.AccountEntity
import com.luxwallet.app.core.database.entity.AssetEntity
import com.luxwallet.app.core.database.entity.BudgetEntity
import com.luxwallet.app.core.database.entity.CategoryEntity
import com.luxwallet.app.core.database.entity.FinancialProfileEntity
import com.luxwallet.app.core.database.entity.GoalEntity
import com.luxwallet.app.core.database.entity.LedgerEntryEntity
import com.luxwallet.app.core.database.entity.LiabilityEntity
import com.luxwallet.app.core.database.entity.MerchantRuleEntity
import com.luxwallet.app.core.database.entity.NotificationObservationEntity
import com.luxwallet.app.core.database.entity.TransactionEntity

/**
 * Schema version 1 (MVP). Any future schema change MUST add a Room [androidx.room.migration.Migration]
 * here rather than relying on destructive migration (PRD §48) — [DEV_ALLOW_DESTRUCTIVE_MIGRATION]
 * exists only as an explicit, developer-opt-in escape hatch for pre-release local iteration.
 */
@Database(
    entities = [
        com.luxwallet.app.core.database.entity.TransactionConfirmationEntity::class,
        AccountEntity::class,
        AssetEntity::class,
        LiabilityEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        LedgerEntryEntity::class,
        NotificationObservationEntity::class,
        MerchantRuleEntity::class,
        BudgetEntity::class,
        GoalEntity::class,
        FinancialProfileEntity::class,
        com.luxwallet.app.core.database.entity.PaydayPlanEntity::class
    ],
    version = 6,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class LuxDatabase : RoomDatabase() {
    abstract fun transactionConfirmationDao(): com.luxwallet.app.core.database.dao.TransactionConfirmationDao
    abstract fun paydayPlanDao(): com.luxwallet.app.core.database.dao.PaydayPlanDao
    abstract fun accountDao(): AccountDao
    abstract fun assetDao(): AssetDao
    abstract fun liabilityDao(): LiabilityDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun ledgerEntryDao(): LedgerEntryDao
    abstract fun notificationObservationDao(): NotificationObservationDao
    abstract fun merchantRuleDao(): MerchantRuleDao
    abstract fun budgetDao(): BudgetDao
    abstract fun goalDao(): GoalDao
    abstract fun financialProfileDao(): FinancialProfileDao

    companion object {
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notification_observations ADD COLUMN eventTime INTEGER")
                db.execSQL("ALTER TABLE notification_observations ADD COLUMN contentHash TEXT")
            }
        }
        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS payday_plans (startDay INTEGER NOT NULL, capturedAt INTEGER NOT NULL, payload TEXT NOT NULL, PRIMARY KEY(startDay))")
            }
        }
        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS transaction_confirmations (transactionId INTEGER NOT NULL, dueAt INTEGER NOT NULL, handled INTEGER NOT NULL, PRIMARY KEY(transactionId))")
            }
        }
        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE assets ADD COLUMN isArchived INTEGER NOT NULL DEFAULT 0")
            }
        }
        val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE accounts ADD COLUMN notes TEXT")
            }
        }
        const val DATABASE_NAME = "lux_wallet.db"

        /** Never true in a release build; guarded in [com.luxwallet.app.LuxWalletApp]. */
        const val DEV_ALLOW_DESTRUCTIVE_MIGRATION = false
    }
}

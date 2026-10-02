package com.luxwallet.app.integration

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.luxwallet.app.core.database.LuxDatabase
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DatabaseMigrationTest {
    @Test fun versionOneDataSurvivesMigration() = migrate(1)
    @Test fun versionTwoDataSurvivesMigration() = migrate(2)
    @Test fun versionThreeDataSurvivesMigration() = migrate(3)
    @Test fun versionFourDataSurvivesMigration() = migrate(4)
    @Test fun versionFiveDataSurvivesMigration() = migrate(5)
    private fun migrate(version: Int) = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-test.db"
        context.deleteDatabase(name)
        val stream = javaClass.classLoader!!.getResourceAsStream("com.luxwallet.app.core.database.LuxDatabase/$version.json")!!
        val schema = stream.bufferedReader().use { Json.parseToJsonElement(it.readText()).jsonObject["database"]!!.jsonObject }
        val config = SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(object : SupportSQLiteOpenHelper.Callback(version) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                schema["entities"]!!.jsonArray.forEach { element ->
                    val entity = element.jsonObject
                    val table = entity["tableName"]!!.jsonPrimitive.content
                    db.execSQL(entity["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                    entity["indices"]!!.jsonArray.forEach { index ->
                        db.execSQL(index.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                    }
                }
                db.execSQL("INSERT INTO accounts (id,name,kind,provider,currentEstimatedBalance,openingBalance,openingBalanceDate,isOwnedByUser,includeInNetWorth,isActive) VALUES (1,'BCA','BANK','BCA',3199997,3200000,0,1,1,1)")
                db.execSQL("INSERT INTO transactions (id,type,direction,amount,currency,sourceAccountId,transactionTime,createdAt,updatedAt,confidenceScore,reviewStatus,isInternalTransfer,isManual,isExcludedFromCashflow) VALUES (1,'EXPENSE','OUT',3,'IDR',1,100,100,100,1.0,'CONFIRMED',0,0,0)")
                db.execSQL("INSERT INTO ledger_entries (id,transactionId,accountId,deltaAmount,createdAt) VALUES (1,1,1,-3,100)")
                db.execSQL("INSERT INTO assets (id,name,assetClass,currentValue,updatedAt,includeInNetWorth) VALUES (1,'Emas lama','EMAS',1500000,100,1)")
                schema["setupQueries"]!!.jsonArray.forEach { db.execSQL(it.jsonPrimitive.content) }
                db.execSQL("INSERT INTO notification_observations (id,sourceApp,packageName,notificationKey,title,text,postedAt,receivedAt,rawPayloadHash,parserVersion,parseStatus) VALUES (1,'MYBCA','com.bca.mybca','key','Catatan Finansial','Pengeluaran IDR 3.00',100,100,'hash',1,'PENDING')")
            }
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }).build()
        FrameworkSQLiteOpenHelperFactory().create(config).let { helper -> helper.writableDatabase; helper.close() }
        val db = Room.databaseBuilder(context, LuxDatabase::class.java, name).addMigrations(LuxDatabase.MIGRATION_1_2, LuxDatabase.MIGRATION_2_3, LuxDatabase.MIGRATION_3_4, LuxDatabase.MIGRATION_4_5, LuxDatabase.MIGRATION_5_6).allowMainThreadQueries().build()
        try {
            val observation = db.notificationObservationDao().getById(1)!!
            assertEquals("Pengeluaran IDR 3.00", observation.text)
            assertNull(observation.eventTime)
            assertNull(observation.contentHash)
            assertEquals(3199997L, db.accountDao().getById(1)!!.currentEstimatedBalance)
            assertNull(db.accountDao().getById(1)!!.notes)
            db.accountDao().updateMetadata(1, "BCA pribadi", "Dana harian")
            assertEquals("Dana harian", db.accountDao().getById(1)!!.notes)
            assertEquals(3199997L, db.accountDao().getById(1)!!.currentEstimatedBalance)
            assertEquals(3L, db.transactionDao().getById(1)!!.amount)
            assertEquals(-3L, db.ledgerEntryDao().getAllOnce().single().deltaAmount)
            assertEquals(1500000L, db.assetDao().getAllOnce().single().currentValue)
            assertFalse(db.assetDao().getAllOnce().single().isArchived)
            assertTrue(db.paydayPlanDao().getAllOnce().isEmpty())
            assertTrue(db.transactionConfirmationDao().due(Long.MAX_VALUE).isEmpty())
            assertEquals(6, db.openHelper.readableDatabase.version)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}

package com.luxwallet.app.parser

import com.luxwallet.app.core.model.SourceApp
import com.luxwallet.app.data.NotificationRepository
import com.luxwallet.app.notification.SupportedPackages
import com.luxwallet.app.parser.core.*
import org.junit.Assert.*
import org.junit.Test

class NotificationSafetyTest {
    private val registry = ParserRegistry()
    @Test fun officialSeaBankPackageIsSupportedAndEmailIsIgnored() {
        assertEquals(SourceApp.SEABANK, SupportedPackages.sourceForPackage("id.co.bankbkemobile.digitalbank"))
        assertNull(SupportedPackages.sourceForPackage("com.google.android.gm"))
        assertEquals(SourceApp.MYBCA, SupportedPackages.resolve("example.bank", mapOf("example.bank" to SourceApp.MYBCA)))
    }
    @Test fun declinedAndPromotionalPaymentsDoNotCreateTransactions() {
        listOf("Pembayaran QRIS gagal sebesar Rp50.000", "Promo pembayaran QRIS hingga Rp50.000",
            "Transfer pending sebesar Rp50.000", "Kode verifikasi pembayaran Rp50.000").forEach {
            assertEquals(ParseResult.NotFinancial, registry.parse(NotificationInput(SourceApp.SEABANK, "", it, postedAt = 1L)))
        }
    }
    @Test fun unfamiliarFinancialFormatIsPreservedForReview() {
        assertTrue(registry.parse(NotificationInput(SourceApp.SEABANK, "Aktivitas rekening",
            "Pemasukan Rp50.000 membutuhkan pemeriksaan", postedAt = 1L)) is ParseResult.Failed)
    }
    @Test fun expandedTextAndNotificationIdentityAffectHash() {
        fun hash(key: String, bigText: String) = NotificationRepository.hashPayload("SEABANK", "bank", "Transaksi", "", 1L, key, bigText)
        assertNotEquals(hash("a", "Rp10.000"), hash("a", "Rp20.000"))
        assertNotEquals(hash("a", "Rp10.000"), hash("b", "Rp10.000"))
        assertEquals(hash("a", "Rp10.000"), hash("a", "Rp10.000"))
    }
    @Test fun fullTextTakesPrecedenceOverTruncatedPreview() {
        val result = registry.parse(NotificationInput(SourceApp.SEABANK, "Pembayaran QRIS berhasil",
            "Pembayaran QRIS untuk PopCorn Technology sebesar 50...",
            bigText = "Pembayaran QRIS untuk PopCorn Technology sebesar 50.500 telah berhasil.", postedAt = 1L)) as ParseResult.Parsed
        assertEquals(50500L, result.candidate.amount)
    }
    @Test fun amountNeverOverflowsOrLosesIntegerPrecision() {
        assertEquals(9007199254740993L, AmountParser.normalizeOrNull("9007199254740993"))
        assertNull(AmountParser.normalizeOrNull("999999999999999999999999"))
        assertNull(AmountParser.normalizeOrNull("1,50"))
    }
    @Test fun seaBankIncomingUsesTransferAmountAndFullDigitGroups() {
        listOf("Rp647.500", "Rp647500", "IDR 647,500.00").forEach { amount ->
            val result = registry.parse(NotificationInput(SourceApp.SEABANK, "TRANSFER MASUK",
                "kamu menerima transfer saldo senilai $amount ke rekening 3422.", postedAt = 123)) as ParseResult.Parsed
            assertEquals(647500L, result.candidate.amount)
            assertEquals(com.luxwallet.app.core.model.TransactionDirection.IN, result.candidate.direction)
            assertEquals(123L, result.candidate.transactionTime)
        }
    }
    @Test fun incomingRealTimeIsNotMisclassifiedAsOutgoing() {
        val result = registry.parse(NotificationInput(SourceApp.SEABANK, "TRANSFER MASUK",
            "Kamu menerima transfer real-time senilai Rp647.500 ke rekening 3422.", postedAt = 123)) as ParseResult.Parsed
        assertEquals(com.luxwallet.app.core.model.TransactionDirection.IN, result.candidate.direction)
        assertEquals(647500L, result.candidate.amount)
    }
    @Test fun incomingWithoutAmountNeverUsesAccountDigits() {
        assertTrue(registry.parse(NotificationInput(SourceApp.SEABANK, "TRANSFER MASUK",
            "Kamu menerima transfer saldo ke rekening 3422.", postedAt = 123)) is ParseResult.Failed)
    }
    @Test fun pendingAndContradictoryIncomingNeverCreditsAccount() {
        listOf("Kamu belum menerima transfer senilai Rp647.500", "Kamu akan menerima transfer senilai Rp647.500",
            "Transfer masuk gagal senilai Rp647.500", "Transfer masuk pending senilai Rp647.500",
            "Kamu melakukan transfer keluar senilai Rp647.500").forEach {
            assertFalse(registry.parse(NotificationInput(SourceApp.SEABANK, "TRANSFER MASUK", it, postedAt = 123)) is ParseResult.Parsed)
        }
    }

}

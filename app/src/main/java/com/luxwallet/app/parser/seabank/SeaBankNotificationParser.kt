package com.luxwallet.app.parser.seabank

import com.luxwallet.app.core.model.SourceApp
import com.luxwallet.app.core.model.TransactionDirection
import com.luxwallet.app.core.model.TransactionType
import com.luxwallet.app.parser.core.AccountHint
import com.luxwallet.app.parser.core.AmountParser
import com.luxwallet.app.parser.core.NotificationInput
import com.luxwallet.app.parser.core.NotificationParser
import com.luxwallet.app.parser.core.ParseResult
import com.luxwallet.app.parser.core.TransactionCandidate

/**
 * Parses SeaBank notifications. SeaBank has several distinct notification "shapes" (samples 1,
 * 3, 5, 6, 7 of the golden dataset); this parser recognizes each by its characteristic phrasing
 * rather than relying on the title alone, since several shapes share the same title.
 */
class SeaBankNotificationParser : NotificationParser {

    override val sourceApp: SourceApp = SourceApp.SEABANK

    override fun canParse(input: NotificationInput): Boolean {
        val text = input.combinedText
        return INCOMING_REGEX.containsMatchIn(text) || SHOPS_PAYMENT_REGEX.containsMatchIn(text) ||
            text.contains("transfer virtual account", ignoreCase = true) ||
            text.contains("transfer real-time", ignoreCase = true) ||
            text.contains("Pembayaran QRIS", ignoreCase = true) ||
            text.contains("Top Up e-Wallet", ignoreCase = true)
    }

    override fun parse(input: NotificationInput): ParseResult {
        val text = input.combinedText

        return when {
            INCOMING_REGEX.containsMatchIn(text) -> parseIncoming(text, input)
            text.contains("Top Up e-Wallet", ignoreCase = true) -> parseEwalletTopup(text, input)
            text.contains("transfer virtual account", ignoreCase = true) -> parseVirtualAccountTransfer(text, input)
            text.contains("Pembayaran QRIS", ignoreCase = true) -> parseQris(text, input)
            text.contains("transfer real-time", ignoreCase = true) -> parseRealtimeTransfer(text, input)
            SHOPS_PAYMENT_REGEX.containsMatchIn(text) -> parseInstantPayment(text, input)
            else -> ParseResult.NotFinancial
        }
    }

    private fun parseIncoming(text: String, input: NotificationInput): ParseResult {
        if (Regex("""\b(belum|akan|gagal|pending|menunggu|dibatalkan|permintaan|promo|failed|declined)\b|tidak berhasil""", RegexOption.IGNORE_CASE).containsMatchIn(text))
            return ParseResult.NotFinancial
        if (Regex("""(?:melakukan|mengirim) transfer|transfer keluar""", RegexOption.IGNORE_CASE).containsMatchIn(text))
            return ParseResult.Failed("Arah transfer SeaBank belum jelas")
        // Anchor to the transfer amount, never to the destination account number.
        val match = Regex("""(?:senilai|sebesar|sejumlah)\s*(?:Rp\.?\s*|IDR\s*)?([0-9]+(?:[.,][0-9]+)*)|(?:transfer masuk|transfer diterima)\s*(?:Rp\.?\s*|IDR\s*)([0-9]+(?:[.,][0-9]+)*)""", RegexOption.IGNORE_CASE).find(text)
        val amount = match?.groupValues?.drop(1)?.firstOrNull { it.isNotEmpty() }?.let(AmountParser::normalizeOrNull)
            ?.takeIf { it > 0 } ?: return ParseResult.Failed("Nominal transfer masuk SeaBank belum terbaca")
        return ParseResult.Parsed(TransactionCandidate(sourceApp = sourceApp, direction = TransactionDirection.IN,
            amount = amount, type = TransactionType.EXTERNAL_TRANSFER, transactionTime = input.postedAt,
            confidenceScore = 0.95))
    }

    /** Sample 1: "SeaBank Bayar Instan: Pembayaran Shopee kamu sebesar Rp16.900 ... berhasil." */
    private fun parseInstantPayment(text: String, input: NotificationInput): ParseResult {
        val amount = AmountParser.findAmountAfterAnyKeyword(text, listOf("sebesar"))
            ?: return ParseResult.Failed("Could not extract amount from SeaBank instant payment")
        val merchant = SHOPS_PAYMENT_REGEX.find(text)?.groupValues?.get(1)?.trim()

        return ParseResult.Parsed(
            TransactionCandidate(
                sourceApp = sourceApp,
                direction = TransactionDirection.OUT,
                amount = amount,
                type = TransactionType.MARKETPLACE_PAYMENT,
                merchantName = merchant,
                transactionTime = input.postedAt,
                confidenceScore = if (merchant != null) 0.9 else 0.7
            )
        )
    }

    /** Sample 5: "Kamu telah melakukan transfer virtual account sebesar Rp10.000 kepada ShopeePay ..." */
    private fun parseVirtualAccountTransfer(text: String, input: NotificationInput): ParseResult {
        val amount = AmountParser.findAmountAfterAnyKeyword(text, listOf("sebesar"))
            ?: return ParseResult.Failed("Could not extract amount from SeaBank virtual account transfer")
        val destination = DESTINATION_KEPADA_REGEX.find(text)?.groupValues?.get(1)?.trim()

        return ParseResult.Parsed(
            TransactionCandidate(
                sourceApp = sourceApp,
                direction = TransactionDirection.OUT,
                amount = amount,
                type = TransactionType.EWALLET_TOPUP,
                destinationProviderHint = destination?.let { AccountHint(providerLabel = it, ownerNameRaw = null) },
                transactionTime = input.postedAt,
                confidenceScore = if (destination != null) 0.9 else 0.7
            )
        )
    }

    /** Sample 3: "Kamu baru melakukan transfer real-time senilai Rp329.306 ..." */
    private fun parseRealtimeTransfer(text: String, input: NotificationInput): ParseResult {
        val amount = AmountParser.findAmountAfterAnyKeyword(text, listOf("senilai"))
            ?: return ParseResult.Failed("Could not extract amount from SeaBank realtime transfer")

        return ParseResult.Parsed(
            TransactionCandidate(
                sourceApp = sourceApp,
                direction = TransactionDirection.OUT,
                amount = amount,
                type = TransactionType.EXTERNAL_TRANSFER, // provisional; matching engine may upgrade to INTERNAL_TRANSFER
                transactionTime = input.postedAt,
                confidenceScore = 0.85
            )
        )
    }

    /** Sample 6: "Pembayaran QRIS untuk PopCorn Technology sebesar 50.500 telah berhasil." */
    private fun parseQris(text: String, input: NotificationInput): ParseResult {
        val amount = AmountParser.findAmountAfterAnyKeyword(text, listOf("sebesar"))
            ?: return ParseResult.Failed("Could not extract amount from SeaBank QRIS payment")
        val merchant = QRIS_MERCHANT_REGEX.find(text)?.groupValues?.get(1)?.trim()

        return ParseResult.Parsed(
            TransactionCandidate(
                sourceApp = sourceApp,
                direction = TransactionDirection.OUT,
                amount = amount,
                type = TransactionType.QRIS_PAYMENT,
                merchantName = merchant,
                transactionTime = input.postedAt,
                confidenceScore = if (merchant != null) 0.9 else 0.7
            )
        )
    }

    /** Sample 7: "Kamu berhasil melakukan Top Up e-Wallet senilai Rp25.000 ke GOPAY LUKAS RICKY K." */
    private fun parseEwalletTopup(text: String, input: NotificationInput): ParseResult {
        val amount = AmountParser.findAmountAfterAnyKeyword(text, listOf("senilai"))
            ?: return ParseResult.Failed("Could not extract amount from SeaBank e-wallet topup")
        val match = TOPUP_DESTINATION_REGEX.find(text)
        val providerLabel = match?.groupValues?.get(1)
        val ownerName = match?.groupValues?.get(2)?.trim()?.trimEnd('.')

        return ParseResult.Parsed(
            TransactionCandidate(
                sourceApp = sourceApp,
                direction = TransactionDirection.OUT,
                amount = amount,
                type = TransactionType.EWALLET_TOPUP,
                destinationProviderHint = providerLabel?.let { AccountHint(providerLabel = it, ownerNameRaw = ownerName) },
                transactionTime = input.postedAt,
                confidenceScore = if (providerLabel != null) 0.9 else 0.7
            )
        )
    }

    companion object {
        private val INCOMING_REGEX = Regex("""\btransfer masuk\b|\b(?:kamu|anda)\s+(?:(?:telah|berhasil)\s+)?menerima\s+(?:transfer|dana)|\b(?:transfer|dana)\s+(?:(?:telah|berhasil)\s+)?diterima\b""", RegexOption.IGNORE_CASE)
        private val SHOPS_PAYMENT_REGEX =
            Regex("""Bayar Instan:\s*Pembayaran (.+?) kamu sebesar""", RegexOption.IGNORE_CASE)
        private val DESTINATION_KEPADA_REGEX = Regex("""kepada ([A-Za-z]+)""", RegexOption.IGNORE_CASE)
        private val QRIS_MERCHANT_REGEX = Regex("""Pembayaran QRIS untuk (.+?) sebesar""", RegexOption.IGNORE_CASE)
        private val TOPUP_DESTINATION_REGEX = Regex("""ke\s+([A-Za-z]+)\s+(.+?)\s*$""", RegexOption.IGNORE_CASE)
    }
}

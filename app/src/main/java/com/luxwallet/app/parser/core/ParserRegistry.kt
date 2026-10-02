package com.luxwallet.app.parser.core

import com.luxwallet.app.core.model.SourceApp
import com.luxwallet.app.parser.gopay.GoPayNotificationParser
import com.luxwallet.app.parser.mybca.MyBcaNotificationParser
import com.luxwallet.app.parser.seabank.SeaBankNotificationParser
import com.luxwallet.app.parser.shopeepay.ShopeePayNotificationParser

/** Central dispatch from [SourceApp] to its dedicated parser. One parser per source, never shared logic across sources. */
class ParserRegistry(
    private val parsers: Map<SourceApp, NotificationParser> = mapOf(
        SourceApp.MYBCA to MyBcaNotificationParser(),
        SourceApp.SEABANK to SeaBankNotificationParser(),
        SourceApp.SHOPEEPAY to ShopeePayNotificationParser(),
        SourceApp.GOPAY to GoPayNotificationParser()
    )
) {
    fun parse(input: NotificationInput): ParseResult {
        if (Regex("(?i)\\b(gagal|dibatalkan|dibatalkannya|menunggu|pending|otp|kode verifikasi|promo|diskon|hingga)\\b").containsMatchIn(input.combinedText)) {
            return ParseResult.NotFinancial
        }
        val parser = parsers[input.sourceApp]
            ?: return ParseResult.Failed("No parser registered for ${input.sourceApp}")
        if (!parser.canParse(input) && input.sourceApp == SourceApp.MYBCA) {
            val fallback = com.luxwallet.app.parser.mybca.BcaMobileNotificationParser()
            if (fallback.canParse(input)) return fallback.parse(input)
        }
        if (!parser.canParse(input)) {
            return if (Regex("(?i)(rp\\s*\\d|idr\\s*\\d)").containsMatchIn(input.combinedText) &&
                Regex("(?i)(pembayaran|transfer|pemasukan|pengeluaran|diterima|saldo)").containsMatchIn(input.combinedText))
                ParseResult.Failed("Format transaksi belum dikenali. Periksa notifikasi ini.") else ParseResult.NotFinancial
        }
        return parser.parse(input)
    }

    companion object {
        const val PARSER_VERSION = 4
    }
}

package com.dresskit.wardrobe.data

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * Normalizes garment/lookbook queries and builds accurate Turkish store search links.
 */
object StoreSearchLinks {

    private val TR_LOCALE = Locale("tr", "TR")

    private val NOISE_WORDS = setOf(
        "kombin", "kombini", "kombinler", "stili", "stil", "tarz", "tarzı",
        "veya", "ve", "ile", "için", "bir", "olan", "gibi",
        "look", "outfit", "style", "or", "with", "and", "the", "a",
        "minimal", "sokak", "street", "modern", "günlük", "casual",
        "temel", "parça", "parçalar", "tonu", "uyumu", "dengesi",
        "kontrast", "vurgusu", "katman", "katmanlı", "yıkama", "kalıp",
        "rahat", "düz", "heavy", "heavyweight", "fitted", "relaxed", "fit"
    )

    private val PUNCT_SPLIT_REGEX = Regex("""[/&+·–—(),|]+""")
    private val MULTI_SPACE_REGEX = Regex("""\s+""")

    fun cleanSearchQuery(
        genderPrefix: String,
        rawQuery: String,
        maxKeywords: Int = 3
    ): String {
        val preCleaned = rawQuery
            .replace("t-shirt", "tshirt", ignoreCase = true)
            .replace("low-top", "sneaker", ignoreCase = true)
            .replace("smart-street", "smart", ignoreCase = true)
            .replace('-', ' ')
            .replace(PUNCT_SPLIT_REGEX, " ")
            .replace(MULTI_SPACE_REGEX, " ")
            .trim()

        val rawTokens = preCleaned.split(' ')
            .map { it.trim().trim('.', ':', ';', '!', '?') }
            .filter { it.length >= 2 }

        val genderLower = genderPrefix.trim().lowercase(TR_LOCALE)
        val allFiltered = mutableListOf<String>()
        val seenLower = HashSet<String>()

        for (tok in rawTokens) {
            val lower = tok.lowercase(TR_LOCALE)
            if (lower in NOISE_WORDS) continue
            if (genderLower.isNotEmpty() && lower == genderLower) continue
            if (seenLower.add(lower)) {
                allFiltered.add(tok)
            }
        }

        // Preserve garment noun (usually at the end of Turkish/English phrases) alongside key descriptors
        val cappedFiltered = when {
            allFiltered.size <= maxKeywords -> allFiltered
            maxKeywords <= 1 -> listOf(allFiltered.last())
            else -> allFiltered.take(maxKeywords - 1) + allFiltered.last()
        }

        val chosenKeywords = if (cappedFiltered.isNotEmpty()) {
            cappedFiltered
        } else {
            rawTokens.filter { it.lowercase(TR_LOCALE) != genderLower }.distinct().take(maxKeywords)
        }

        val combined = buildList {
            if (genderPrefix.isNotBlank()) add(genderPrefix.trim())
            addAll(chosenKeywords)
        }
        return combined.joinToString(" ").ifBlank { rawQuery.trim() }
    }

    private fun encodeQueryParam(text: String): String =
        URLEncoder.encode(text, StandardCharsets.UTF_8.name())

    private fun encodePathSegment(text: String): String =
        URLEncoder.encode(text, StandardCharsets.UTF_8.name()).replace("+", "%20")

    fun googleShoppingUrl(genderPrefix: String, rawQuery: String): String {
        val clean = cleanSearchQuery(genderPrefix, rawQuery, maxKeywords = 3)
        return "https://www.google.com/search?tbm=shop&hl=tr&gl=tr&q=${encodeQueryParam(clean)}"
    }

    fun trendyolUrl(genderPrefix: String, rawQuery: String): String {
        val clean = cleanSearchQuery(genderPrefix, rawQuery, maxKeywords = 3)
        return "https://www.trendyol.com/sr?q=${encodeQueryParam(clean)}"
    }

    fun buildStoreSearchLinks(
        genderPrefix: String,
        rawQuery: String,
        googleLabel: String = "Google Alışveriş"
    ): List<Pair<String, String>> {
        val clean = cleanSearchQuery(genderPrefix, rawQuery, maxKeywords = 3)
        val qPlus = encodeQueryParam(clean)
        val qPath = encodePathSegment(clean)

        return listOf(
            googleLabel to "https://www.google.com/search?tbm=shop&hl=tr&gl=tr&q=$qPlus",
            "Trendyol" to "https://www.trendyol.com/sr?q=$qPlus",
            "Zara" to "https://www.zara.com/tr/tr/search?searchTerm=$qPlus",
            "Bershka" to "https://www.bershka.com/tr/q/$qPath",
            "Pull&Bear" to "https://www.google.com/search?tbm=shop&hl=tr&gl=tr&q=Pull%26Bear+$qPlus",
            "H&M" to "https://www2.hm.com/tr_tr/search-results.html?q=$qPlus",
            "Koton" to "https://www.koton.com/list/?search_text=$qPlus",
            "DeFacto" to "https://www.defacto.com.tr/arama?q=$qPlus",
            "LCW" to "https://www.lcw.com/arama?q=$qPlus"
        )
    }
}

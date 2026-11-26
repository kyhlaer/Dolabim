package com.dresskit.wardrobe.data

import android.content.Context
import java.text.Normalizer
import java.util.Locale

internal object TurkishPlaceNames {
    private var names: Map<String, String>? = null
    private val marks = Regex("\\p{M}+")

    private fun key(name: String) = Normalizer.normalize(name, Normalizer.Form.NFD)
        .replace(marks, "").lowercase(Locale.ROOT).replace('ı', 'i')

    @Synchronized
    fun display(context: Context, name: String, country: String?, lat: Double?, lon: Double?): String {
        val inTurkey = country?.takeIf { it.isNotBlank() }?.equals("TR", ignoreCase = true)
            ?: (lat != null && lon != null && lat in 35.8..42.2 && lon in 25.6..44.9)
        if (!inTurkey || name.isBlank()) return name
        val lookup = names ?: context.assets.open("tr-places.txt").bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() }.associateBy(::key)
        }.also { names = it }
        return lookup[key(name.trim())] ?: name
    }
}

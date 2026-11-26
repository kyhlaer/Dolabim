package com.dresskit.wardrobe.data

import org.json.JSONArray

internal fun requireSocialRead(code: Int) {
    check(code in 200..299) {
        if (code == 429) "Okuma kotasına ulaşıldı (429). Daha sonra tekrar dene."
        else "Sosyal veriler yüklenemedi ($code)"
    }
}

internal fun socialQueryRows(code: Int, body: String, strict: Boolean): JSONArray {
    val parsed = runCatching {
        requireSocialRead(code)
        JSONArray(body).also { rows ->
            for (i in 0 until rows.length()) {
                rows.getJSONObject(i).optJSONObject("error")?.let { requireSocialRead(it.optInt("code")) }
            }
        }
    }
    return if (strict) parsed.getOrThrow() else parsed.getOrElse { JSONArray() }
}

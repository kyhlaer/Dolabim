package com.dresskit.wardrobe.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import com.dresskit.wardrobe.model.AppLanguage
import com.dresskit.wardrobe.model.ClothingCategory
import com.dresskit.wardrobe.model.ClothingItem
import com.dresskit.wardrobe.model.ClothingSubCategory
import com.dresskit.wardrobe.model.ColorHueGroup
import com.dresskit.wardrobe.model.GarmentColor
import com.dresskit.wardrobe.model.GenderPreference
import com.dresskit.wardrobe.model.Season
import com.dresskit.wardrobe.model.StyleTag
import com.dresskit.wardrobe.model.localizedText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.HttpsURLConnection
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

enum class WeatherCondition(
    val label: String,
    val enLabel: String,
    val zhLabel: String,
    val tempRange: String,
    val enTempRange: String,
    val sampleTemp: Int,
    val emoji: String,
    val hint: String,
    val enHint: String,
    val zhHint: String,
    val preferredSeason: Season
) {
    HOT(
        "Güneşli & Sıcak", "Sunny & Hot", "晴热高温",
        "25°C+", "25°C+",
        27, "☀️",
        "İnce ve nefes alan parçalar", "Light & breathable pieces", "轻薄透气的单品",
        Season.SUMMER
    ),
    WARM(
        "Ilık & Açık", "Warm & Mild", "温暖晴朗",
        "18–24°C", "18–24°C",
        21, "🌤️",
        "Tişört, polo veya hafif gömlek", "T-shirt, polo or light shirt", "T恤、Polo衫或轻薄衬衫",
        Season.SPRING
    ),
    COOL(
        "Serin", "Cool", "微凉",
        "11–17°C", "11–17°C",
        14, "🌥️",
        "Hoodie, sweatshirt, hırka veya ceket", "Hoodie, sweatshirt, cardigan or jacket", "卫衣、开衫或轻薄夹克",
        Season.AUTUMN
    ),
    RAINY(
        "Yağmurlu & Rüzgarlı", "Rainy & Windy", "阴雨多风",
        "9–15°C", "9–15°C",
        12, "🌧️",
        "Koruyucu ceket/trençkot ve kapalı ayakkabı", "Protective jacket/trench & closed shoes", "防风夹克/风衣与防水鞋履",
        Season.AUTUMN
    ),
    COLD(
        "Soğuk", "Cold", "寒冷",
        "10°C altı", "Below 10°C",
        5, "❄️",
        "Kazak, mont/kaban ve sıcak katmanlar", "Sweater, coat & warm layers", "毛衣、大衣/羽绒服与保暖内搭",
        Season.WINTER
    );

    fun displayLabel(lang: AppLanguage): String = when (lang) {
        AppLanguage.TR -> label
        AppLanguage.ZH -> zhLabel
        else -> enLabel
    }

    fun displayTempRange(lang: AppLanguage): String = when (lang) {
        AppLanguage.TR -> tempRange
        AppLanguage.ZH -> if (this == COLD) "10°C 以下" else tempRange
        else -> enTempRange
    }

    fun displayHint(lang: AppLanguage): String = when (lang) {
        AppLanguage.TR -> hint
        AppLanguage.ZH -> zhHint
        else -> enHint
    }
}

enum class TurkishCity(val label: String, val lat: Double, val lon: Double) {
    ISTANBUL("İstanbul", 41.0082, 28.9784),
    ANKARA("Ankara", 39.9334, 32.8597),
    IZMIR("İzmir", 38.4192, 27.1287),
    BURSA("Bursa", 40.1885, 29.0610),
    ANTALYA("Antalya", 36.8969, 30.7133),
    ADANA("Adana", 37.0000, 35.3213),
    KONYA("Konya", 37.8746, 32.4932),
    ESKISEHIR("Eskişehir", 39.7767, 30.5206),
    GAZIANTEP("Gaziantep", 37.0662, 37.3833),
    TRABZON("Trabzon", 41.0027, 39.7168)
}

data class HourlyForecast(
    val hourLabel: String,
    val isoTime: String,
    val hourOfDay: Int,
    val tempC: Int,
    val feelsLikeC: Int,
    val precipitationProb: Int,
    val windKmh: Int,
    val weatherCode: Int,
    val condition: WeatherCondition,
    val isCurrentHour: Boolean = false
)

data class DailyForecast(
    val dayIndex: Int,
    val label: String,
    val dateText: String,
    val maxTempC: Int,
    val minTempC: Int,
    val condition: WeatherCondition,
    val isLive: Boolean,
    val hourly: List<HourlyForecast> = emptyList()
)

data class DetectedLocationForecast(
    val label: String,
    val lat: Double,
    val lon: Double,
    val days: List<DailyForecast>
)

data class OutfitSuggestion(
    val title: String,
    val items: List<ClothingItem>,
    val harmonyScore: Int,
    val harmonyNote: String,
    val weatherReason: String
)

data class DestinationPlace(
    val name: String,
    val country: String,
    val lat: Double,
    val lon: Double
) {
    val displayLabel: String get() = if (country.isNotBlank()) "$name, $country" else name
}

data class TravelDayPlan(
    val dayNumber: Int,
    val forecast: DailyForecast,
    val outfit: OutfitSuggestion
)

data class TravelPackingPlan(
    val destinationLabel: String,
    val dayPlans: List<TravelDayPlan>,
    val packingChecklist: List<ClothingItem>
)

object OutfitRecommendationEngine {

    private const val FORECAST_CACHE_TTL_MS = 20 * 60 * 1000L
    private const val MAX_JSON_BYTES = 262_144

    private data class CachedForecast(
        val timestampMs: Long,
        val days: List<DailyForecast>
    )

    private val forecastCache = ConcurrentHashMap<String, CachedForecast>()

    private fun readBoundedUtf8(conn: HttpsURLConnection): String {
        val buf = CharArray(MAX_JSON_BYTES)
        var total = 0
        conn.inputStream.bufferedReader().use { reader ->
            while (total < MAX_JSON_BYTES) {
                val n = reader.read(buf, total, MAX_JSON_BYTES - total)
                if (n <= 0) break
                total += n
            }
        }
        return String(buf, 0, total)
    }

    fun buildFallbackHourly(
        dayIndex: Int,
        maxTempC: Int,
        minTempC: Int,
        condition: WeatherCondition,
        currentHour: Int = LocalTime.now().hour
    ): List<HourlyForecast> {
        val span = (maxTempC - minTempC).coerceAtLeast(2)
        return (0..23).map { hour ->
            val factor = kotlin.math.sin(((hour - 5) / 19.0) * Math.PI).coerceIn(0.0, 1.0)
            val temp = (minTempC + span * factor).roundToInt()
            val wind = when (condition) {
                WeatherCondition.RAINY -> 18 + (hour % 6)
                WeatherCondition.COLD -> 14 + (hour % 5)
                else -> 8 + (hour % 6)
            }
            val rainProb = when (condition) {
                WeatherCondition.RAINY -> 60 + ((hour * 7) % 30)
                WeatherCondition.COOL -> if (hour in 15..19) 20 else 10
                else -> 5
            }.coerceIn(0, 100)
            val hCond = if (condition == WeatherCondition.RAINY && hour in 9..20) {
                WeatherCondition.RAINY
            } else {
                mapWeatherCondition(if (condition == WeatherCondition.RAINY) 61 else 1, temp)
            }
            HourlyForecast(
                hourLabel = String.format(Locale.US, "%02d:00", hour),
                isoTime = String.format(Locale.US, "T%02d:00", hour),
                hourOfDay = hour,
                tempC = temp,
                feelsLikeC = if (wind >= 16) temp - 2 else temp,
                precipitationProb = rainProb,
                windKmh = wind,
                weatherCode = if (hCond == WeatherCondition.RAINY) 61 else 1,
                condition = hCond,
                isCurrentHour = (dayIndex == 0 && hour == currentHour)
            )
        }
    }

    fun parseHourlyForDate(
        hourlyJson: JSONObject?,
        dateIso: String,
        isToday: Boolean,
        currentHour: Int = LocalTime.now().hour
    ): List<HourlyForecast> {
        hourlyJson ?: return emptyList()
        val timesArr = hourlyJson.optJSONArray("time") ?: return emptyList()
        val tempsArr = hourlyJson.optJSONArray("temperature_2m") ?: return emptyList()
        val apparentArr = hourlyJson.optJSONArray("apparent_temperature")
        val rainProbsArr = hourlyJson.optJSONArray("precipitation_probability")
        val codesArr = hourlyJson.optJSONArray("weathercode")
        val windsArr = hourlyJson.optJSONArray("windspeed_10m")

        val n = minOf(timesArr.length(), tempsArr.length())
        val times = (0 until n).map { timesArr.optString(it, "") }
        val temps = (0 until n).map { tempsArr.optDouble(it, 16.0) }
        val apparent = (0 until n).map { apparentArr?.optDouble(it, temps[it]) ?: temps[it] }
        val rainProbs = (0 until n).map { rainProbsArr?.optInt(it, 0) ?: 0 }
        val codes = (0 until n).map { codesArr?.optInt(it, 0) ?: 0 }
        val winds = (0 until n).map { windsArr?.optDouble(it, 8.0) ?: 8.0 }
        return parseHourlyArrays(
            times = times,
            temps = temps,
            apparent = apparent,
            rainProbs = rainProbs,
            codes = codes,
            winds = winds,
            dateIso = dateIso,
            isToday = isToday,
            currentHour = currentHour
        )
    }

    fun parseHourlyArrays(
        times: List<String>,
        temps: List<Double>,
        apparent: List<Double>,
        rainProbs: List<Int>,
        codes: List<Int>,
        winds: List<Double>,
        dateIso: String,
        isToday: Boolean,
        currentHour: Int = LocalTime.now().hour
    ): List<HourlyForecast> {
        val out = ArrayList<HourlyForecast>(24)
        val n = minOf(times.size, temps.size)
        for (i in 0 until n) {
            val iso = times[i]
            if (!iso.startsWith("${dateIso}T")) continue
            val hour = iso.substringAfter('T').substringBefore(':').toIntOrNull() ?: continue
            val tempC = temps[i].roundToInt()
            val feelsC = apparent.getOrElse(i) { tempC.toDouble() }.roundToInt()
            val rainProb = rainProbs.getOrElse(i) { 0 }.coerceIn(0, 100)
            val code = codes.getOrElse(i) { 0 }
            val wind = winds.getOrElse(i) { 8.0 }.roundToInt().coerceAtLeast(0)
            val cond = mapWeatherCondition(code, tempC)
            out.add(
                HourlyForecast(
                    hourLabel = String.format(Locale.US, "%02d:00", hour),
                    isoTime = iso,
                    hourOfDay = hour,
                    tempC = tempC,
                    feelsLikeC = feelsC,
                    precipitationProb = rainProb,
                    windKmh = wind,
                    weatherCode = code,
                    condition = cond,
                    isCurrentHour = isToday && hour == currentHour
                )
            )
        }
        return out
    }

    fun generateFallbackWeeklyForecast(lang: AppLanguage = AppLanguage.TR): List<DailyForecast> {
        val today = LocalDate.now()
        val defaults = listOf(
            WeatherCondition.COOL to (16 to 10),
            WeatherCondition.WARM to (20 to 12),
            WeatherCondition.RAINY to (13 to 9),
            WeatherCondition.COOL to (15 to 9),
            WeatherCondition.WARM to (19 to 11),
            WeatherCondition.COLD to (9 to 4),
            WeatherCondition.HOT to (26 to 16)
        )
        return (0..6).map { offset ->
            val date = today.plusDays(offset.toLong())
            val (cond, temps) = defaults[offset]
            DailyForecast(
                dayIndex = offset,
                label = dayLabel(offset, date, lang),
                dateText = date.format(DateTimeFormatter.ofPattern("dd.MM")),
                maxTempC = temps.first,
                minTempC = temps.second,
                condition = cond,
                isLive = false,
                hourly = buildFallbackHourly(offset, temps.first, temps.second, cond)
            )
        }
    }

    suspend fun fetchWeeklyForecast(
        city: TurkishCity,
        lang: AppLanguage = AppLanguage.TR
    ): List<DailyForecast>? {
        return fetchWeeklyForecastForCoords(city.lat, city.lon, lang)
    }

    suspend fun fetchWeeklyForecastForCoords(
        lat: Double,
        lon: Double,
        lang: AppLanguage = AppLanguage.TR
    ): List<DailyForecast>? =
        withContext(Dispatchers.IO) {
            if (lat !in -90.0..90.0 || lon !in -180.0..180.0 || lat.isNaN() || lon.isNaN()) {
                return@withContext null
            }
            val cacheKey = "${String.format(Locale.US, "%.2f,%.2f", lat, lon)}_${lang.code}"
            val cached = forecastCache[cacheKey]
            val now = System.currentTimeMillis()
            if (cached != null && now - cached.timestampMs < FORECAST_CACHE_TTL_MS) {
                return@withContext cached.days
            }

            val endpoint =
                "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current_weather=true&daily=weathercode,temperature_2m_max,temperature_2m_min&hourly=temperature_2m,apparent_temperature,precipitation_probability,weathercode,windspeed_10m&timezone=auto&forecast_days=7"
            val conn = SecurityGuards.openSafeHttpsGet(
                initialUrl = endpoint,
                allowedHosts = setOf("api.open-meteo.com"),
                maxRedirects = 1,
                connectTimeoutMs = 5500,
                readTimeoutMs = 5500
            ) ?: return@withContext null

            try {
                val body = readBoundedUtf8(conn)

                val root = JSONObject(body)
                val current = root.optJSONObject("current_weather")
                val daily = root.optJSONObject("daily") ?: return@withContext null
                val hourlyJson = root.optJSONObject("hourly")
                val currentHour = LocalTime.now().hour

                val dates = daily.optJSONArray("time") ?: return@withContext null
                val codes = daily.optJSONArray("weathercode") ?: return@withContext null
                val maxTemps = daily.optJSONArray("temperature_2m_max") ?: return@withContext null
                val minTemps = daily.optJSONArray("temperature_2m_min") ?: return@withContext null

                val list = mutableListOf<DailyForecast>()
                val count = minOf(dates.length(), codes.length(), maxTemps.length(), minTemps.length(), 7)
                for (i in 0 until count) {
                    val dateStr = dates.optString(i)
                    val parsedDate = runCatching { LocalDate.parse(dateStr) }.getOrElse {
                        LocalDate.now().plusDays(i.toLong())
                    }
                    val maxT = if (i == 0 && current != null) {
                        current.optDouble("temperature", maxTemps.optDouble(i, 18.0)).roundToInt()
                    } else {
                        maxTemps.optDouble(i, 18.0).roundToInt()
                    }
                    val minT = minTemps.optDouble(i, 10.0).roundToInt()
                    val code = if (i == 0 && current != null) {
                        current.optInt("weathercode", codes.optInt(i, 0))
                    } else {
                        codes.optInt(i, 0)
                    }

                    val avgFeel = if (i == 0 && current != null) maxT else ((maxT * 2 + minT) / 3.0).roundToInt()
                    val condition = mapWeatherCondition(code, avgFeel)
                    val parsedHourly = parseHourlyForDate(
                        hourlyJson = hourlyJson,
                        dateIso = dateStr.ifBlank { parsedDate.toString() },
                        isToday = i == 0,
                        currentHour = currentHour
                    ).ifEmpty {
                        buildFallbackHourly(i, maxT, minT, condition, currentHour)
                    }

                    list.add(
                        DailyForecast(
                            dayIndex = i,
                            label = dayLabel(i, parsedDate, lang),
                            dateText = parsedDate.format(DateTimeFormatter.ofPattern("dd.MM")),
                            maxTempC = maxT,
                            minTempC = minT,
                            condition = condition,
                            isLive = true,
                            hourly = parsedHourly
                        )
                    )
                }
                if (list.isNotEmpty()) {
                    forecastCache[cacheKey] = CachedForecast(now, list)
                    list
                } else {
                    null
                }
            } catch (_: Exception) {
                null
            } finally {
                conn.disconnect()
            }
        }

    @SuppressLint("MissingPermission")
    suspend fun detectUserLocationAndForecast(
        context: Context,
        hasLocationPermission: Boolean,
        lang: AppLanguage = AppLanguage.TR
    ): DetectedLocationForecast? = withContext(Dispatchers.IO) {
        var lat: Double? = null
        var lon: Double? = null
        var resolvedName: String? = null

        if (hasLocationPermission) {
            try {
                val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                val candidates = listOfNotNull(
                    runCatching { lm?.getLastKnownLocation(LocationManager.GPS_PROVIDER) }.getOrNull(),
                    runCatching { lm?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) }.getOrNull(),
                    runCatching { lm?.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER) }.getOrNull()
                )
                val best: Location? = candidates.maxByOrNull { it.time }
                if (best != null) {
                    lat = best.latitude
                    lon = best.longitude
                    @Suppress("DEPRECATION")
                    val addresses = runCatching {
                        val loc = when (lang) {
                            AppLanguage.TR -> Locale("tr", "TR")
                            AppLanguage.ZH -> Locale.SIMPLIFIED_CHINESE
                            else -> Locale("en", "US")
                        }
                        Geocoder(context, loc).getFromLocation(lat, lon, 1)
                    }.getOrNull()
                    val addr = addresses?.firstOrNull()
                    resolvedName = addr?.subAdminArea ?: addr?.adminArea ?: addr?.locality
                }
            } catch (_: Exception) {
                // Fall through to IP geolocation
            }
        }

        // Fallback to IP-based location when GPS has no fix (e.g., indoors / emulator)
        if (lat == null || lon == null) {
            val conn = SecurityGuards.openSafeHttpsGet(
                initialUrl = "https://ipwho.is/",
                allowedHosts = setOf("ipwho.is"),
                maxRedirects = 1,
                connectTimeoutMs = 4000,
                readTimeoutMs = 4000
            )
            if (conn != null) {
                try {
                    val json = JSONObject(readBoundedUtf8(conn))
                    if (json.optBoolean("success", true)) {
                        val ipLat = json.optDouble("latitude", Double.NaN)
                        val ipLon = json.optDouble("longitude", Double.NaN)
                        if (!ipLat.isNaN() && !ipLon.isNaN() && ipLat in -90.0..90.0 && ipLon in -180.0..180.0) {
                            lat = ipLat
                            lon = ipLon
                            resolvedName = json.optString("city").take(64).takeIf { it.isNotBlank() }
                        }
                    }
                } catch (_: Exception) {
                    // Offline
                } finally {
                    conn.disconnect()
                }
            }
        }

        if (lat == null || lon == null) return@withContext null
        val forecast = fetchWeeklyForecastForCoords(lat, lon, lang) ?: return@withContext null
        val fallbackLabel = localizedText(lang, "Konumum", "My Location", "Mein Standort", "Ma position", "Mi ubicación", "我的位置")
        DetectedLocationForecast(
            label = resolvedName ?: fallbackLabel,
            lat = lat,
            lon = lon,
            days = forecast
        )
    }

    private fun mapWeatherCondition(code: Int, tempC: Int): WeatherCondition {
        return when {
            code in 51..67 || code in 80..99 -> WeatherCondition.RAINY
            tempC <= 10 -> WeatherCondition.COLD
            tempC in 11..17 -> WeatherCondition.COOL
            tempC in 18..24 -> WeatherCondition.WARM
            else -> WeatherCondition.HOT
        }
    }

    fun dayLabel(offset: Int, date: LocalDate, lang: AppLanguage = AppLanguage.TR): String {
        if (offset == 0) return localizedText(lang, "Bugün", "Today", "Heute", "Aujourd'hui", "Hoy", "今天")
        if (offset == 1) return localizedText(lang, "Yarın", "Tomorrow", "Morgen", "Demain", "Mañana", "明天")
        return when (date.dayOfWeek) {
            DayOfWeek.MONDAY -> localizedText(lang, "Pazartesi", "Monday", "Montag", "Lundi", "Lunes", "星期一")
            DayOfWeek.TUESDAY -> localizedText(lang, "Salı", "Tuesday", "Dienstag", "Mardi", "Martes", "星期二")
            DayOfWeek.WEDNESDAY -> localizedText(lang, "Çarşamba", "Wednesday", "Mittwoch", "Mercredi", "Miércoles", "星期三")
            DayOfWeek.THURSDAY -> localizedText(lang, "Perşembe", "Thursday", "Donnerstag", "Jeudi", "Jueves", "星期四")
            DayOfWeek.FRIDAY -> localizedText(lang, "Cuma", "Friday", "Freitag", "Vendredi", "Viernes", "星期五")
            DayOfWeek.SATURDAY -> localizedText(lang, "Cumartesi", "Saturday", "Samstag", "Samedi", "Sábado", "星期六")
            DayOfWeek.SUNDAY -> localizedText(lang, "Pazar", "Sunday", "Sonntag", "Dimanche", "Domingo", "星期日")
        }
    }

    fun sortTopToBottom(items: List<ClothingItem>): List<ClothingItem> {
        return items.sortedBy {
            when (it.category) {
                ClothingCategory.ACCESSORY -> 0
                ClothingCategory.OUTERWEAR -> 1
                ClothingCategory.TOP -> 2
                ClothingCategory.SUIT -> 3
                ClothingCategory.BOTTOM -> 4
                ClothingCategory.SHOES -> 5
            }
        }
    }

    /**
     * Computes realistic color-theory harmony (25..98%) from garment colors and extracted pixels.
     */
    fun calculateColorHarmony(
        items: List<ClothingItem>,
        lang: AppLanguage = AppLanguage.TR
    ): Pair<Int, String> {
        if (items.isEmpty()) {
            return 80 to localizedText(lang, "Parça Seçilmedi", "No Pieces Selected", "Keine Teile", "Aucune pièce", "Sin prendas", "未选择单品")
        }
        val primaryColors = items.mapNotNull { it.colors.firstOrNull() }
        if (primaryColors.size <= 1) {
            return 94 to localizedText(lang, "Monokrom Ton Uyumu", "Monochrome Tone Harmony", "Monochrome Harmonie", "Harmonie monochrome", "Armonía monocromática", "单色和谐搭配")
        }

        val distinctColors = primaryColors.distinct()
        if (distinctColors.size == 1) {
            return 95 to localizedText(lang, "Tek Renk Ton Uyumu", "Single-Tone Harmony", "Einton-Harmonie", "Harmonie ton sur ton", "Armonía de un tono", "同色系和谐搭配")
        }

        // Pairwise color matrix averaging across every item pair in the outfit
        var totalPairScore = 0
        var pairCount = 0
        var minPairScore = 100
        for (i in primaryColors.indices) {
            for (j in i + 1 until primaryColors.size) {
                val pScore = pairHarmonyScore(primaryColors[i], primaryColors[j])
                totalPairScore += pScore
                pairCount++
                if (pScore < minPairScore) minPairScore = pScore
            }
        }

        var score = if (pairCount > 0) {
            // Weight worst clashing pair heavily so clashing colors visibly drop the score
            ((totalPairScore.toFloat() / pairCount) * 0.65f + minPairScore * 0.35f).roundToInt()
        } else {
            85
        }

        // Penalize >2 distinct chromatic (non-neutral) colors competing together
        val saturatedCount = distinctColors.count { !it.isNeutral }
        if (saturatedCount >= 3) {
            score -= (saturatedCount - 2) * 14
        }

        // Check extracted pixel brightness contrast if available
        val hexMeans = items.mapNotNull { item ->
            item.extractedHexes.firstOrNull()?.let { rgbBrightness(it.toInt()) }
        }
        if (hexMeans.size >= 2) {
            val spread = (hexMeans.max() - hexMeans.min())
            if (spread in 0.25f..0.75f) {
                score += 2
            } else if (spread < 0.08f && distinctColors.any { !it.isNeutral }) {
                score -= 3
            }
        }

        // Style coherence adjustment
        val styles = items.map { it.styleTag }.distinct()
        if (styles.size == 1) {
            score += 2
        } else if (styles.contains(StyleTag.SPORT) && styles.contains(StyleTag.FORMAL)) {
            score -= 8
        }

        val finalScore = score.coerceIn(26, 98)
        val note = when {
            finalScore >= 90 -> localizedText(lang, "Yüksek Renk Uyumu", "High Color Harmony", "Hohe Farbharmonie", "Forte harmonie", "Alta armonía", "高度色彩和谐")
            finalScore >= 78 -> localizedText(lang, "Dengeli Kombin Uyumu", "Balanced Outfit Harmony", "Ausgewogene Harmonie", "Harmonie équilibrée", "Armonía equilibrada", "均衡穿搭协调")
            finalScore >= 64 -> localizedText(lang, "Orta Düzey Kontrast", "Moderate Contrast", "Mittlerer Kontrast", "Contraste modéré", "Contraste moderado", "适度色彩对比")
            finalScore >= 48 -> localizedText(lang, "Zorlayıcı Renk Eşleşmesi", "Challenging Color Pairing", "Mutige Kombination", "Association audacieuse", "Combinación atrevida", "个性撞色搭配")
            else -> localizedText(lang, "Çarpışan Renk Tonları", "Clashing Color Tones", "Starker Farbkontrast", "Couleurs contrastées", "Tonos en contraste", "色调冲突较强")
        }
        return finalScore to note
    }

    private fun pairHarmonyScore(c1: GarmentColor, c2: GarmentColor): Int {
        if (c1 == c2) return 93

        val pair = setOf(c1, c2)

        // Specific classic / awkward pairs
        if (pair == setOf(GarmentColor.NAVY, GarmentColor.BLACK)) return 58
        if (pair == setOf(GarmentColor.BROWN, GarmentColor.BLACK)) return 54
        if (pair == setOf(GarmentColor.BROWN, GarmentColor.GRAY)) return 62
        if (pair == setOf(GarmentColor.RED, GarmentColor.GREEN)) return 32
        if (pair == setOf(GarmentColor.PINK, GarmentColor.ORANGE)) return 35
        if (pair == setOf(GarmentColor.PINK, GarmentColor.RED)) return 38
        if (pair == setOf(GarmentColor.YELLOW, GarmentColor.PINK)) return 41
        if (pair == setOf(GarmentColor.PURPLE, GarmentColor.ORANGE)) return 36
        if (pair == setOf(GarmentColor.PURPLE, GarmentColor.YELLOW)) return 42
        if (pair == setOf(GarmentColor.BURGUNDY, GarmentColor.GREEN)) return 44
        if (pair == setOf(GarmentColor.ORANGE, GarmentColor.COBALT)) return 52
        if (pair == setOf(GarmentColor.TERRACOTTA, GarmentColor.PINK)) return 45

        // High classic pairings
        if (pair == setOf(GarmentColor.NAVY, GarmentColor.BEIGE) ||
            pair == setOf(GarmentColor.NAVY, GarmentColor.ECRU) ||
            pair == setOf(GarmentColor.NAVY, GarmentColor.WHITE) ||
            pair == setOf(GarmentColor.NAVY, GarmentColor.CAMEL)
        ) return 95

        if (pair == setOf(GarmentColor.BLACK, GarmentColor.WHITE) ||
            pair == setOf(GarmentColor.BLACK, GarmentColor.ECRU) ||
            pair == setOf(GarmentColor.BLACK, GarmentColor.GRAY) ||
            pair == setOf(GarmentColor.BLACK, GarmentColor.BEIGE) ||
            pair == setOf(GarmentColor.BLACK, GarmentColor.OLIVE)
        ) return 94

        if (pair == setOf(GarmentColor.DENIM, GarmentColor.WHITE) ||
            pair == setOf(GarmentColor.DENIM, GarmentColor.ECRU) ||
            pair == setOf(GarmentColor.DENIM, GarmentColor.CAMEL) ||
            pair == setOf(GarmentColor.DENIM, GarmentColor.BLACK)
        ) return 92

        if (pair == setOf(GarmentColor.BLUSH, GarmentColor.GRAY) ||
            pair == setOf(GarmentColor.BLUSH, GarmentColor.LIGHT_GRAY) ||
            pair == setOf(GarmentColor.BLUSH, GarmentColor.WHITE) ||
            pair == setOf(GarmentColor.BLUSH, GarmentColor.NAVY) ||
            pair == setOf(GarmentColor.PINK, GarmentColor.LIGHT_GRAY) ||
            pair == setOf(GarmentColor.PINK, GarmentColor.DENIM)
        ) return 89

        // General rule based on neutral + hue groups
        if (c1.isNeutral && c2.isNeutral) return 86
        if (c1.isNeutral || c2.isNeutral) {
            val neutral = if (c1.isNeutral) c1 else c2
            val chrom = if (c1.isNeutral) c2 else c1
            return when {
                neutral == GarmentColor.WHITE || neutral == GarmentColor.ECRU ||
                    neutral == GarmentColor.LIGHT_GRAY || neutral == GarmentColor.GRAY -> 88
                neutral == GarmentColor.BLACK || neutral == GarmentColor.CHARCOAL ->
                    if (chrom.hueGroup == ColorHueGroup.BLUE && chrom == GarmentColor.COBALT) 76 else 85
                neutral == GarmentColor.BEIGE || neutral == GarmentColor.STONE ->
                    if (chrom.hueGroup == ColorHueGroup.WARM_ACCENT && chrom == GarmentColor.PINK) 68 else 86
                else -> 78
            }
        }

        // Two chromatic (non-neutral) colors together
        return when {
            c1.hueGroup == c2.hueGroup -> 74
            (c1.hueGroup == ColorHueGroup.BLUE && c2.hueGroup == ColorHueGroup.EARTH) ||
                (c2.hueGroup == ColorHueGroup.BLUE && c1.hueGroup == ColorHueGroup.EARTH) -> 84
            (c1.hueGroup == ColorHueGroup.GREEN && c2.hueGroup == ColorHueGroup.EARTH) ||
                (c2.hueGroup == ColorHueGroup.GREEN && c1.hueGroup == ColorHueGroup.EARTH) -> 83
            (c1.hueGroup == ColorHueGroup.BLUE && c2 == GarmentColor.MUSTARD) ||
                (c2.hueGroup == ColorHueGroup.BLUE && c1 == GarmentColor.MUSTARD) -> 82
            else -> 52
        }
    }

    private fun rgbBrightness(rgb: Int): Float {
        val r = ((rgb shr 16) and 0xFF) / 255f
        val g = ((rgb shr 8) and 0xFF) / 255f
        val b = (rgb and 0xFF) / 255f
        return (0.299f * r + 0.587f * g + 0.114f * b)
    }

    /**
     * Infers likely wardrobe style gender preference from subcategories, names, notes, and tones.
     */
    fun inferWardrobeGender(items: List<ClothingItem>): GenderPreference {
        if (items.isEmpty()) return GenderPreference.UNISEX
        var femScore = 0
        var mascScore = 0
        val femKeywords = listOf("etek", "elbise", "bluz", "crop", "topuklu", "babet", "tayt", "askılı", "straplez", "kadın", "mini")
        val mascKeywords = listOf("kravat", "erkek", "damatlık", "oxford", "klasik kesim", "kargo", "bomber")

        for (item in items) {
            val text = "${item.name} ${item.notes}".lowercase(Locale.ROOT)
            if (item.subCategory in setOf(
                    ClothingSubCategory.SKIRT,
                    ClothingSubCategory.DRESS,
                    ClothingSubCategory.HEELS,
                    ClothingSubCategory.CROP_TOP
                ) || femKeywords.any { text.contains(it) }
            ) {
                femScore += 2
            }
            if (item.subCategory in setOf(
                    ClothingSubCategory.SUIT_SET,
                    ClothingSubCategory.POLO,
                    ClothingSubCategory.CHINO
                ) || mascKeywords.any { text.contains(it) }
            ) {
                mascScore += 2
            }
            if (item.colors.contains(GarmentColor.BLUSH) || item.colors.contains(GarmentColor.PINK)) {
                femScore += 1
            }
        }

        return when {
            femScore >= mascScore + 2 -> GenderPreference.FEMALE
            mascScore >= femScore + 2 -> GenderPreference.MALE
            else -> GenderPreference.UNISEX
        }
    }

    fun suggestOutfits(
        items: List<ClothingItem>,
        weather: WeatherCondition,
        targetDayLabel: String,
        recentSignatures: MutableList<String>,
        lastUsedPrimaryIds: MutableSet<String>,
        count: Int = 3,
        lang: AppLanguage = AppLanguage.TR,
        genderPref: GenderPreference = GenderPreference.UNISEX,
        flatteringColors: Set<GarmentColor> = emptySet()
    ): List<OutfitSuggestion> {
        if (items.size < 2) return emptyList()

        val femaleOnlySubs = setOf(
            ClothingSubCategory.SKIRT,
            ClothingSubCategory.DRESS,
            ClothingSubCategory.HEELS,
            ClothingSubCategory.CROP_TOP
        )
        val filteredPool = if (genderPref == GenderPreference.MALE) {
            items.filter { it.subCategory !in femaleOnlySubs }.ifEmpty { items }
        } else {
            items
        }

        val tops = filteredPool.filter { it.category == ClothingCategory.TOP }
        val bottoms = filteredPool.filter { it.category == ClothingCategory.BOTTOM }
        val outerwear = filteredPool.filter { it.category == ClothingCategory.OUTERWEAR }
        val shoes = filteredPool.filter { it.category == ClothingCategory.SHOES }
        val accessories = filteredPool.filter { it.category == ClothingCategory.ACCESSORY }
        val suits = filteredPool.filter { it.category == ClothingCategory.SUIT }

        val candidates = mutableListOf<ScoredCandidate>()

        if (tops.isNotEmpty() && bottoms.isNotEmpty()) {
            val topPool = tops.sortedByDescending { weatherScore(it, weather) }.take(10)
            val bottomPool = bottoms.sortedByDescending { weatherScore(it, weather) }.take(8)
            val outerPool = outerwear.sortedByDescending { weatherScore(it, weather) }.take(6)
            val shoePool = shoes.sortedByDescending { weatherScore(it, weather) }.take(5)

            for (top in topPool) {
                for (bottom in bottomPool) {
                    val baseList = mutableListOf(top, bottom)

                    if (outerPool.isNotEmpty() &&
                        (weather == WeatherCondition.COOL ||
                            weather == WeatherCondition.RAINY ||
                            weather == WeatherCondition.COLD ||
                            (genderPref == GenderPreference.FEMALE && outerPool.any { it.subCategory == ClothingSubCategory.BLAZER }))
                    ) {
                        val bestOuter = outerPool
                            .shuffled(Random(System.nanoTime()))
                            .maxByOrNull { candidateOuter ->
                                calculateColorHarmony(baseList + candidateOuter, lang).first +
                                    weatherScore(candidateOuter, weather)
                            }
                        if (bestOuter != null) baseList.add(bestOuter)
                    }

                    if (shoePool.isNotEmpty()) {
                        val bestShoe = shoePool
                            .shuffled(Random(System.nanoTime()))
                            .maxByOrNull { shoe ->
                                calculateColorHarmony(baseList + shoe, lang).first +
                                    weatherScore(shoe, weather)
                            }
                        if (bestShoe != null) baseList.add(bestShoe)
                    }

                    if (accessories.isNotEmpty() && Random.nextFloat() < 0.45f) {
                        val acc = accessories.filter { weatherScore(it, weather) >= 0 }.randomOrNull()
                        if (acc != null) baseList.add(acc)
                    }

                    candidates.add(
                        scoreCandidate(
                            items = baseList,
                            weather = weather,
                            recentSignatures = recentSignatures,
                            lastUsedPrimaryIds = lastUsedPrimaryIds,
                            lang = lang,
                            genderPref = genderPref,
                            flatteringColors = flatteringColors
                        )
                    )
                }
            }
        }

        for (suit in suits) {
            val baseList = mutableListOf(suit)
            if (genderPref == GenderPreference.FEMALE && outerwear.isNotEmpty() && weather != WeatherCondition.HOT) {
                val coat = outerwear.sortedByDescending { weatherScore(it, weather) }.firstOrNull()
                if (coat != null) baseList.add(coat)
            }
            val compatibleShoe = shoes.sortedByDescending { weatherScore(it, weather) }.firstOrNull()
            if (compatibleShoe != null) baseList.add(compatibleShoe)
            if (genderPref == GenderPreference.FEMALE) {
                accessories.firstOrNull { it.subCategory == ClothingSubCategory.BAG }?.let { baseList.add(it) }
            }
            if (baseList.size >= 2) {
                candidates.add(
                    scoreCandidate(
                        items = baseList,
                        weather = weather,
                        recentSignatures = recentSignatures,
                        lastUsedPrimaryIds = lastUsedPrimaryIds,
                        lang = lang,
                        genderPref = genderPref,
                        flatteringColors = flatteringColors
                    )
                )
            }
        }

        if (candidates.isEmpty()) {
            repeat(6) {
                val sample = filteredPool.shuffled().take(minOf(3, filteredPool.size))
                candidates.add(
                    scoreCandidate(
                        items = sample,
                        weather = weather,
                        recentSignatures = recentSignatures,
                        lastUsedPrimaryIds = lastUsedPrimaryIds,
                        lang = lang,
                        genderPref = genderPref,
                        flatteringColors = flatteringColors
                    )
                )
            }
        }

        val sorted = candidates.sortedByDescending { it.totalScore }
        val chosen = mutableListOf<ScoredCandidate>()
        val chosenLeadIds = mutableSetOf<String>()

        for (cand in sorted) {
            val leadId = cand.items.firstOrNull()?.id
            val alreadyInChosen = chosen.any { it.signature == cand.signature }
            if (alreadyInChosen) continue
            if (chosen.size < count && (leadId !in chosenLeadIds || sorted.size <= count)) {
                chosen.add(cand)
                if (leadId != null) chosenLeadIds.add(leadId)
            }
            if (chosen.size >= count) break
        }
        if (chosen.size < count) {
            for (cand in sorted) {
                if (chosen.none { it.signature == cand.signature }) {
                    chosen.add(cand)
                    if (chosen.size >= count) break
                }
            }
        }

        lastUsedPrimaryIds.clear()
        chosen.forEach { cand ->
            recentSignatures.add(cand.signature)
            cand.items.take(2).forEach { lastUsedPrimaryIds.add(it.id) }
        }
        while (recentSignatures.size > 18) {
            recentSignatures.removeAt(0)
        }

        return chosen.mapIndexed { idx, cand ->
            val title = when (lang) {
                AppLanguage.TR -> "${weather.emoji} $targetDayLabel İçin Kombin #${idx + 1}"
                AppLanguage.EN -> "${weather.emoji} Outfit for $targetDayLabel #${idx + 1}"
                AppLanguage.DE -> "${weather.emoji} Outfit für $targetDayLabel #${idx + 1}"
                AppLanguage.FR -> "${weather.emoji} Tenue pour $targetDayLabel #${idx + 1}"
                AppLanguage.ES -> "${weather.emoji} Outfit para $targetDayLabel #${idx + 1}"
                AppLanguage.ZH -> "${weather.emoji} ${targetDayLabel}穿搭 #${idx + 1}"
            }
            OutfitSuggestion(
                title = title,
                items = sortTopToBottom(cand.items),
                harmonyScore = cand.harmonyScore,
                harmonyNote = cand.harmonyNote,
                weatherReason = weather.displayHint(lang)
            )
        }
    }

    suspend fun searchDestinations(
        query: String,
        lang: AppLanguage = AppLanguage.TR
    ): List<DestinationPlace> = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.length < 2) return@withContext emptyList()

        val presets = buildList {
            TurkishCity.entries.forEach { add(DestinationPlace(it.label, "Türkiye", it.lat, it.lon)) }
            add(DestinationPlace("Muğla / Bodrum", "Türkiye", 37.0344, 27.4305))
            add(DestinationPlace("Çeşme", "Türkiye", 38.3228, 26.3025))
            add(DestinationPlace("Kapadokya / Nevşehir", "Türkiye", 38.6244, 34.7239))
            add(DestinationPlace("Paris", "France", 48.8566, 2.3522))
            add(DestinationPlace("London", "UK", 51.5072, -0.1276))
            add(DestinationPlace("Berlin", "Germany", 52.5200, 13.4050))
            add(DestinationPlace("Barcelona", "Spain", 41.3874, 2.1686))
            add(DestinationPlace("Madrid", "Spain", 40.4168, -3.7038))
            add(DestinationPlace("Rome", "Italy", 41.9028, 12.4964))
            add(DestinationPlace("Amsterdam", "Netherlands", 52.3676, 4.9041))
            add(DestinationPlace("Bakü", "Azerbaijan", 40.4093, 49.8671))
            add(DestinationPlace("Dubai", "UAE", 25.2048, 55.2708))
            add(DestinationPlace("New York", "USA", 40.7128, -74.0060))
        }

        val results = mutableListOf<DestinationPlace>()
        val encoded = URLEncoder.encode(clean, "UTF-8")
        val endpoint = "https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=5&language=${lang.code}&format=json"
        val conn = SecurityGuards.openSafeHttpsGet(
            initialUrl = endpoint,
            allowedHosts = setOf("geocoding-api.open-meteo.com"),
            maxRedirects = 1,
            connectTimeoutMs = 4500,
            readTimeoutMs = 4500
        )
        if (conn != null) {
            try {
                val body = readBoundedUtf8(conn)
                val arr = JSONObject(body).optJSONArray("results")
                if (arr != null) {
                    for (i in 0 until minOf(arr.length(), 5)) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val name = obj.optString("name").trim()
                        val country = obj.optString("country").trim()
                        val lat = obj.optDouble("latitude", Double.NaN)
                        val lon = obj.optDouble("longitude", Double.NaN)
                        if (name.isNotBlank() && !lat.isNaN() && !lon.isNaN()) {
                            results.add(DestinationPlace(name, country, lat, lon))
                        }
                    }
                }
            } catch (_: Exception) {
                // Fall through to local city presets
            } finally {
                conn.disconnect()
            }
        }

        if (results.isEmpty()) {
            val lower = clean.lowercase(Locale.ROOT)
            presets.filter { it.name.lowercase(Locale.ROOT).contains(lower) || it.country.lowercase(Locale.ROOT).contains(lower) }
        } else {
            results
        }
    }

    fun generateTravelPlan(
        items: List<ClothingItem>,
        destinationLabel: String,
        forecasts: List<DailyForecast>,
        tripDays: Int,
        lang: AppLanguage = AppLanguage.TR,
        genderPref: GenderPreference = GenderPreference.UNISEX,
        flatteringColors: Set<GarmentColor> = emptySet()
    ): TravelPackingPlan {
        if (items.size < 2 || forecasts.isEmpty()) {
            return TravelPackingPlan(destinationLabel, emptyList(), emptyList())
        }
        val daysCount = tripDays.coerceIn(2, 7)
        val activeDays = (0 until daysCount).map { idx ->
            forecasts.getOrElse(idx) { forecasts.last() }
        }

        // Keep bottoms, outerwear, and shoes realistic like a true capsule suitcase
        val dominantCondition = activeDays
            .groupingBy { it.condition }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key ?: activeDays.first().condition

        val bottomBudget = if (daysCount <= 3) 2 else 3
        val outerBudget = if (activeDays.all { it.condition == WeatherCondition.HOT }) 0 else 2
        val shoeBudget = if (daysCount >= 5) 2 else 1

        val capsuleBottoms = items.filter { it.category == ClothingCategory.BOTTOM }
            .sortedByDescending { weatherScore(it, dominantCondition) }
            .take(bottomBudget)
        val capsuleOuterwear = items.filter { it.category == ClothingCategory.OUTERWEAR }
            .sortedByDescending { weatherScore(it, dominantCondition) }
            .take(outerBudget)
        val capsuleShoes = items.filter { it.category == ClothingCategory.SHOES }
            .sortedByDescending { weatherScore(it, dominantCondition) }
            .take(shoeBudget)

        val travelPool = (
            items.filter { it.category == ClothingCategory.TOP || it.category == ClothingCategory.SUIT || it.category == ClothingCategory.ACCESSORY } +
                capsuleBottoms + capsuleOuterwear + capsuleShoes
            ).distinctBy { it.id }
            .let { if (it.size >= 2) it else items }

        val usedSignatures = mutableListOf<String>()
        val lastPrimary = mutableSetOf<String>()
        val dayPlans = mutableListOf<TravelDayPlan>()

        activeDays.forEachIndexed { idx, dayForecast ->
            val dayName = localizedText(
                lang,
                "${idx + 1}. Gün",
                "Day ${idx + 1}",
                "Tag ${idx + 1}",
                "Jour ${idx + 1}",
                "Día ${idx + 1}",
                "第${idx + 1}天"
            )
            val pick = suggestOutfits(
                items = travelPool,
                weather = dayForecast.condition,
                targetDayLabel = "$dayName · ${dayForecast.maxTempC}°C",
                recentSignatures = usedSignatures,
                lastUsedPrimaryIds = lastPrimary,
                count = 1,
                lang = lang,
                genderPref = genderPref,
                flatteringColors = flatteringColors
            ).firstOrNull()

            if (pick != null) {
                dayPlans.add(
                    TravelDayPlan(
                        dayNumber = idx + 1,
                        forecast = dayForecast,
                        outfit = pick
                    )
                )
            }
        }

        val checklist = sortTopToBottom(
            dayPlans.flatMap { it.outfit.items }.distinctBy { it.id }
        )

        return TravelPackingPlan(
            destinationLabel = destinationLabel,
            dayPlans = dayPlans,
            packingChecklist = checklist
        )
    }

    private fun scoreCandidate(
        items: List<ClothingItem>,
        weather: WeatherCondition,
        recentSignatures: List<String>,
        lastUsedPrimaryIds: Set<String>,
        lang: AppLanguage = AppLanguage.TR,
        genderPref: GenderPreference = GenderPreference.UNISEX,
        flatteringColors: Set<GarmentColor> = emptySet()
    ): ScoredCandidate {
        val signature = items.map { it.id }.sorted().joinToString("-")
        val (harmony, harmonyNote) = calculateColorHarmony(items, lang)
        val avgWeatherFit = items.sumOf { weatherScore(it, weather) } / items.size.coerceAtLeast(1)

        var bonus = 0
        if (genderPref == GenderPreference.MALE) {
            val hasMenTop = items.any {
                it.subCategory in setOf(
                    ClothingSubCategory.HOODIE, ClothingSubCategory.SWEATSHIRT,
                    ClothingSubCategory.TSHIRT, ClothingSubCategory.SHIRT, ClothingSubCategory.POLO
                )
            }
            val hasMenBottom = items.any {
                it.subCategory in setOf(
                    ClothingSubCategory.JEANS, ClothingSubCategory.CARGO,
                    ClothingSubCategory.CHINO, ClothingSubCategory.PANTS
                )
            }
            if (hasMenTop && hasMenBottom) bonus += 4
        } else if (genderPref == GenderPreference.FEMALE) {
            val hasFemCoord = items.any {
                it.subCategory in setOf(
                    ClothingSubCategory.CROP_TOP, ClothingSubCategory.BLAZER,
                    ClothingSubCategory.TRENCH, ClothingSubCategory.CARDIGAN,
                    ClothingSubCategory.SKIRT, ClothingSubCategory.DRESS, ClothingSubCategory.JEANS
                )
            }
            if (hasFemCoord) bonus += 4
        }

        if (flatteringColors.isNotEmpty()) {
            bonus += items.count { it.colors.firstOrNull() in flatteringColors } * 3
        }

        var penalty = 0
        if (recentSignatures.contains(signature)) {
            penalty += 38
        }
        val overlapWithLast = items.count { it.id in lastUsedPrimaryIds }
        penalty += overlapWithLast * 11

        val jitter = Random.nextInt(0, 6)
        val total = harmony + avgWeatherFit + bonus + jitter - penalty

        return ScoredCandidate(
            items = items,
            signature = signature,
            harmonyScore = harmony,
            harmonyNote = harmonyNote,
            totalScore = total
        )
    }

    private fun weatherScore(item: ClothingItem, weather: WeatherCondition): Int {
        val sub = item.subCategory
        val warmth = sub.defaultWarmth
        var score = when (weather) {
            WeatherCondition.HOT -> when (sub) {
                ClothingSubCategory.TSHIRT, ClothingSubCategory.POLO, ClothingSubCategory.TANK,
                ClothingSubCategory.CROP_TOP, ClothingSubCategory.SKIRT, ClothingSubCategory.DRESS,
                ClothingSubCategory.SHORTS, ClothingSubCategory.SLIPPER, ClothingSubCategory.GLASSES -> 16
                ClothingSubCategory.SHIRT, ClothingSubCategory.SNEAKER, ClothingSubCategory.HEELS -> 8
                ClothingSubCategory.COAT, ClothingSubCategory.SWEATER, ClothingSubCategory.BOOT,
                ClothingSubCategory.SCARF, ClothingSubCategory.HOODIE -> -25
                else -> if (warmth <= 2) 6 else -10
            }
            WeatherCondition.WARM -> when (sub) {
                ClothingSubCategory.TSHIRT, ClothingSubCategory.POLO, ClothingSubCategory.SHIRT,
                ClothingSubCategory.CROP_TOP, ClothingSubCategory.SKIRT, ClothingSubCategory.DRESS,
                ClothingSubCategory.CHINO, ClothingSubCategory.JEANS, ClothingSubCategory.SNEAKER,
                ClothingSubCategory.LOAFER, ClothingSubCategory.HEELS -> 14
                ClothingSubCategory.SWEATSHIRT, ClothingSubCategory.CARDIGAN -> 6
                ClothingSubCategory.COAT, ClothingSubCategory.BOOT, ClothingSubCategory.SCARF -> -20
                else -> 4
            }
            WeatherCondition.COOL -> when (sub) {
                ClothingSubCategory.HOODIE, ClothingSubCategory.SWEATSHIRT, ClothingSubCategory.CARDIGAN,
                ClothingSubCategory.JACKET, ClothingSubCategory.VEST, ClothingSubCategory.PANTS,
                ClothingSubCategory.JEANS, ClothingSubCategory.CARGO, ClothingSubCategory.SWEATPANTS,
                ClothingSubCategory.SNEAKER -> 15
                ClothingSubCategory.SHIRT, ClothingSubCategory.BLAZER, ClothingSubCategory.TRENCH -> 10
                ClothingSubCategory.SHORTS, ClothingSubCategory.SLIPPER, ClothingSubCategory.TANK -> -22
                else -> 4
            }
            WeatherCondition.RAINY -> when (sub) {
                ClothingSubCategory.JACKET, ClothingSubCategory.TRENCH, ClothingSubCategory.COAT,
                ClothingSubCategory.BOOT, ClothingSubCategory.HOODIE, ClothingSubCategory.SWEATSHIRT,
                ClothingSubCategory.JEANS, ClothingSubCategory.PANTS -> 16
                ClothingSubCategory.SHORTS, ClothingSubCategory.SLIPPER, ClothingSubCategory.TANK -> -28
                else -> 5
            }
            WeatherCondition.COLD -> when (sub) {
                ClothingSubCategory.SWEATER, ClothingSubCategory.HOODIE, ClothingSubCategory.CARDIGAN,
                ClothingSubCategory.COAT, ClothingSubCategory.JACKET, ClothingSubCategory.VEST,
                ClothingSubCategory.BOOT, ClothingSubCategory.SCARF, ClothingSubCategory.PANTS,
                ClothingSubCategory.JEANS -> 18
                ClothingSubCategory.SHORTS, ClothingSubCategory.SLIPPER, ClothingSubCategory.TANK -> -32
                ClothingSubCategory.TSHIRT -> -4
                else -> 4
            }
        }

        if (item.seasons.contains(weather.preferredSeason)) {
            score += 6
        }
        return score
    }

    private data class ScoredCandidate(
        val items: List<ClothingItem>,
        val signature: String,
        val harmonyScore: Int,
        val harmonyNote: String,
        val totalScore: Int
    )
}

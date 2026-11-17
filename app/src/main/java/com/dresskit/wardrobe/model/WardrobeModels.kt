package com.dresskit.wardrobe.model

import androidx.compose.ui.graphics.Color

enum class AppLanguage(val code: String, val label: String) {
    TR("tr", "Türkçe"),
    EN("en", "English")
}

enum class AppThemeMode(
    val labelTr: String,
    val labelEn: String,
    val subtitleTr: String,
    val subtitleEn: String
) {
    MODERN(
        labelTr = "Modern",
        labelEn = "Modern",
        subtitleTr = "Ferah krem zemin, beyaz kartlar ve sade katalog görünümü",
        subtitleEn = "Airy cream canvas, crisp white cards and minimal catalog aesthetic"
    ),
    STREET(
        labelTr = "Sokak Tarzı",
        labelEn = "Street Style",
        subtitleTr = "Koyu arduvaz zemin, grafit kartlar ve sıcak kehribar sokak vurguları",
        subtitleEn = "Deep slate dark canvas, graphite cards and warm amber street accents"
    );

    fun label(lang: AppLanguage): String = if (lang == AppLanguage.EN) labelEn else labelTr
    fun subtitle(lang: AppLanguage): String = if (lang == AppLanguage.EN) subtitleEn else subtitleTr
}

enum class ClothingCategory(val label: String, val labelEn: String, val shortLabel: String) {
    TOP("Üst Giyim", "Tops", "Üst Giyim"),
    BOTTOM("Alt Giyim", "Bottoms", "Alt Giyim"),
    OUTERWEAR("Dış Giyim", "Outerwear", "Dış Giyim"),
    SHOES("Ayakkabı", "Shoes", "Ayakkabı"),
    ACCESSORY("Aksesuar", "Accessories", "Aksesuar"),
    SUIT("Takım & Özel", "Sets & Suits", "Takım");

    fun display(lang: AppLanguage): String = if (lang == AppLanguage.EN) labelEn else label
}

enum class ClothingSubCategory(
    val label: String,
    val labelEn: String,
    val parentCategory: ClothingCategory,
    val defaultWarmth: Int
) {
    TSHIRT("Tişört", "T-Shirt", ClothingCategory.TOP, 1),
    POLO("Polo Yaka", "Polo Shirt", ClothingCategory.TOP, 1),
    SHIRT("Gömlek", "Shirt", ClothingCategory.TOP, 2),
    SWEATSHIRT("Sweatshirt", "Sweatshirt", ClothingCategory.TOP, 3),
    HOODIE("Hoodie", "Hoodie", ClothingCategory.TOP, 4),
    CARDIGAN("Hırka", "Cardigan", ClothingCategory.TOP, 4),
    SWEATER("Kazak & Triko", "Sweater & Knit", ClothingCategory.TOP, 5),
    TANK("Atlet", "Tank Top", ClothingCategory.TOP, 1),

    PANTS("Pantolon", "Trousers", ClothingCategory.BOTTOM, 3),
    JEANS("Kot / Jean", "Jeans", ClothingCategory.BOTTOM, 3),
    CHINO("Chino / Kumaş", "Chinos", ClothingCategory.BOTTOM, 3),
    CARGO("Kargo Pantolon", "Cargo Pants", ClothingCategory.BOTTOM, 3),
    SWEATPANTS("Eşofman Altı", "Sweatpants", ClothingCategory.BOTTOM, 3),
    SHORTS("Şort", "Shorts", ClothingCategory.BOTTOM, 1),

    JACKET("Ceket", "Jacket", ClothingCategory.OUTERWEAR, 4),
    COAT("Mont & Kaban", "Coat & Puffer", ClothingCategory.OUTERWEAR, 5),
    BLAZER("Blazer Ceket", "Blazer", ClothingCategory.OUTERWEAR, 3),
    TRENCH("Trençkot", "Trench Coat", ClothingCategory.OUTERWEAR, 4),
    VEST("Şişme Yelek", "Puffer Vest", ClothingCategory.OUTERWEAR, 4),

    SNEAKER("Sneaker", "Sneakers", ClothingCategory.SHOES, 2),
    BOOT("Bot", "Boots", ClothingCategory.SHOES, 5),
    LOAFER("Klasik & Loafer", "Loafers", ClothingCategory.SHOES, 2),
    SLIPPER("Terlik & Sandalet", "Slides & Sandals", ClothingCategory.SHOES, 1),

    WATCH("Saat", "Watch", ClothingCategory.ACCESSORY, 3),
    CAP("Şapka & Bere", "Cap & Beanie", ClothingCategory.ACCESSORY, 3),
    BAG("Çanta", "Bag", ClothingCategory.ACCESSORY, 3),
    GLASSES("Gözlük", "Sunglasses", ClothingCategory.ACCESSORY, 1),
    BELT("Kemer", "Belt", ClothingCategory.ACCESSORY, 3),
    SCARF("Atkı", "Scarf", ClothingCategory.ACCESSORY, 5),

    SUIT_SET("Takım Elbise", "Suit", ClothingCategory.SUIT, 3),
    TRACKSUIT("Eşofman Takımı", "Tracksuit", ClothingCategory.SUIT, 3);

    fun display(lang: AppLanguage): String = if (lang == AppLanguage.EN) labelEn else label

    companion object {
        fun forCategory(category: ClothingCategory): List<ClothingSubCategory> =
            entries.filter { it.parentCategory == category }
    }
}

enum class StyleTag(val label: String, val labelEn: String) {
    CASUAL("Günlük", "Casual"),
    SPORT("Spor & Rahat", "Sporty"),
    SMART("Smart Casual", "Smart Casual"),
    FORMAL("Klasik / Ofis", "Formal");

    fun display(lang: AppLanguage): String = if (lang == AppLanguage.EN) labelEn else label
}

enum class Season(val label: String, val labelEn: String) {
    ALL("4 Mevsim", "All Seasons"),
    SPRING("İlkbahar", "Spring"),
    SUMMER("Yaz", "Summer"),
    AUTUMN("Sonbahar", "Autumn"),
    WINTER("Kış", "Winter");

    fun display(lang: AppLanguage): String = if (lang == AppLanguage.EN) labelEn else label
}

enum class ColorHueGroup {
    NEUTRAL_LIGHT,
    NEUTRAL_DARK,
    EARTH,
    BLUE,
    GREEN,
    WARM_ACCENT,
    COOL_ACCENT
}

enum class GarmentColor(
    val label: String,
    val labelEn: String,
    val hex: Long,
    val isNeutral: Boolean,
    val hueGroup: ColorHueGroup
) {
    WHITE("Beyaz", "White", 0xFFFAFAFA, true, ColorHueGroup.NEUTRAL_LIGHT),
    ECRU("Ekru / Krem", "Ecru / Cream", 0xFFF3EFE4, true, ColorHueGroup.NEUTRAL_LIGHT),
    BEIGE("Bej", "Beige", 0xFFDCCBB2, true, ColorHueGroup.EARTH),
    STONE("Taş / Vizon", "Stone", 0xFFC2B5A3, true, ColorHueGroup.EARTH),
    CAMEL("Camel", "Camel", 0xFFB88B58, true, ColorHueGroup.EARTH),
    BROWN("Kahverengi", "Brown", 0xFF5E3B24, true, ColorHueGroup.EARTH),
    LIGHT_GRAY("Açık Gri", "Light Gray", 0xFFD0D1CE, true, ColorHueGroup.NEUTRAL_LIGHT),
    GRAY("Gri", "Gray", 0xFF8E908C, true, ColorHueGroup.NEUTRAL_LIGHT),
    CHARCOAL("Antrasit", "Charcoal", 0xFF434542, true, ColorHueGroup.NEUTRAL_DARK),
    BLACK("Siyah", "Black", 0xFF1A1A19, true, ColorHueGroup.NEUTRAL_DARK),
    NAVY("Lacivert", "Navy", 0xFF1B2A44, true, ColorHueGroup.BLUE),

    DENIM("Denim Mavi", "Denim Blue", 0xFF4A6E8E, false, ColorHueGroup.BLUE),
    SKY("Açık Mavi", "Sky Blue", 0xFF8FB8D8, false, ColorHueGroup.BLUE),
    COBALT("Saks Mavi", "Cobalt Blue", 0xFF2152A3, false, ColorHueGroup.BLUE),
    TURQUOISE("Turkuaz", "Turquoise", 0xFF3A9B9C, false, ColorHueGroup.COOL_ACCENT),
    MINT("Mint Yeşili", "Mint", 0xFF98C9B5, false, ColorHueGroup.GREEN),
    SAGE("Adaçayı", "Sage", 0xFF839680, false, ColorHueGroup.GREEN),
    GREEN("Yeşil", "Green", 0xFF2E6F44, false, ColorHueGroup.GREEN),
    OLIVE("Haki / Zeytin", "Olive / Khaki", 0xFF556042, false, ColorHueGroup.GREEN),
    YELLOW("Sarı", "Yellow", 0xFFEAC435, false, ColorHueGroup.WARM_ACCENT),
    MUSTARD("Hardal", "Mustard", 0xFFC8922B, false, ColorHueGroup.WARM_ACCENT),
    ORANGE("Turuncu", "Orange", 0xFFD96B38, false, ColorHueGroup.WARM_ACCENT),
    TERRACOTTA("Kiremit", "Terracotta", 0xFFB6533C, false, ColorHueGroup.WARM_ACCENT),
    RED("Kırmızı", "Red", 0xFFC23232, false, ColorHueGroup.WARM_ACCENT),
    BURGUNDY("Bordo", "Burgundy", 0xFF6B212A, false, ColorHueGroup.WARM_ACCENT),
    BLUSH("Pudra", "Blush", 0xFFE8CCD7, false, ColorHueGroup.NEUTRAL_LIGHT),
    PINK("Pembe", "Pink", 0xFFE07A9A, false, ColorHueGroup.WARM_ACCENT),
    PURPLE("Mor / Lila", "Purple", 0xFF6E5282, false, ColorHueGroup.COOL_ACCENT);

    val composeColor: Color get() = Color(hex)
    fun display(lang: AppLanguage): String = if (lang == AppLanguage.EN) labelEn else label
}

data class ClothingItem(
    val id: String,
    val name: String,
    val category: ClothingCategory,
    val subCategory: ClothingSubCategory = ClothingSubCategory.TSHIRT,
    val styleTag: StyleTag = StyleTag.CASUAL,
    val seasons: List<Season>,
    val colors: List<GarmentColor>,
    val extractedHexes: List<Long> = emptyList(),
    val photoFileNames: List<String>,
    val originalPhotoFileNames: List<String> = emptyList(),
    val useOriginalPhotos: Boolean = false,
    val notes: String = "",
    val isFavorite: Boolean = false,
    val isWishlist: Boolean = false,
    val priceText: String = "",
    val wearCount: Int = 0,
    val lastWornAt: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
) {
    val activePhotos: List<String>
        get() = if (useOriginalPhotos && originalPhotoFileNames.size == photoFileNames.size && originalPhotoFileNames.isNotEmpty()) {
            originalPhotoFileNames
        } else {
            photoFileNames
        }

    val coverPhoto: String? get() = activePhotos.firstOrNull()
}

data class Outfit(
    val id: String,
    val title: String,
    val itemIds: List<String>,
    val season: Season = Season.ALL,
    val harmonyScore: Int = 88,
    val harmonyNote: String = "",
    val weatherTag: String = "",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

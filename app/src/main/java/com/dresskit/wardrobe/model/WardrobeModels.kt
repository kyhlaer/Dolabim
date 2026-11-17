package com.dresskit.wardrobe.model

import androidx.compose.ui.graphics.Color

enum class AppLanguage(val code: String, val label: String) {
    TR("tr", "Türkçe"),
    EN("en", "English"),
    DE("de", "Deutsch"),
    FR("fr", "Français"),
    ES("es", "Español")
}

fun localizedText(
    lang: AppLanguage,
    tr: String,
    en: String,
    de: String = en,
    fr: String = en,
    es: String = en
): String = when (lang) {
    AppLanguage.TR -> tr
    AppLanguage.EN -> en
    AppLanguage.DE -> de
    AppLanguage.FR -> fr
    AppLanguage.ES -> es
}

enum class GenderPreference {
    MALE,
    FEMALE,
    UNISEX;

    fun label(lang: AppLanguage): String = when (this) {
        MALE -> localizedText(lang, "Erkek", "Men", "Herren", "Homme", "Hombre")
        FEMALE -> localizedText(lang, "Kadın", "Women", "Damen", "Femme", "Mujer")
        UNISEX -> "Unisex"
    }
}

enum class AppThemeMode(
    val labelTr: String,
    val labelEn: String,
    val labelDe: String,
    val labelFr: String,
    val labelEs: String,
    val subtitleTr: String,
    val subtitleEn: String,
    val subtitleDe: String,
    val subtitleFr: String,
    val subtitleEs: String
) {
    MODERN(
        labelTr = "Modern (Açık)",
        labelEn = "Modern (Light)",
        labelDe = "Modern (Hell)",
        labelFr = "Moderne (Clair)",
        labelEs = "Moderno (Claro)",
        subtitleTr = "Ferah krem zemin, beyaz kartlar ve sade katalog görünümü",
        subtitleEn = "Airy cream canvas, crisp white cards and minimal catalog aesthetic",
        subtitleDe = "Heller Cremeton, weiße Karten und minimalistische Katalogoptik",
        subtitleFr = "Fond crème lumineux, cartes blanches épurées et style catalogue",
        subtitleEs = "Fondo crema luminoso, tarjetas blancas limpias y estética minimalista"
    ),
    MODERN_DARK(
        labelTr = "Modern (Koyu)",
        labelEn = "Modern (Dark)",
        labelDe = "Modern (Dunkel)",
        labelFr = "Moderne (Sombre)",
        labelEs = "Moderno (Oscuro)",
        subtitleTr = "Koyu oniks zemin, sıcak taş kartlar ve terracotta vurgular",
        subtitleEn = "Deep onyx canvas, warm stone cards and terracotta accents",
        subtitleDe = "Tiefer Onyx-Hintergrund, warme Steinkarten und Terrakotta-Akzente",
        subtitleFr = "Fond onyx profond, cartes pierre chaude et accents terracotta",
        subtitleEs = "Fondo ónix profundo, tarjetas piedra cálida y acentos terracota"
    ),
    STREET(
        labelTr = "Sokak Tarzı",
        labelEn = "Street Style",
        labelDe = "Streetstyle",
        labelFr = "Street Style",
        labelEs = "Estilo Urbano",
        subtitleTr = "Koyu arduvaz zemin, grafit kartlar ve sıcak kehribar sokak vurguları",
        subtitleEn = "Deep slate dark canvas, graphite cards and warm amber street accents",
        subtitleDe = "Schieferdunkler Hintergrund, Graphitkarten und warme Bernstein-Akzente",
        subtitleFr = "Fond ardoise sombre, cartes graphite et accents ambre urbains",
        subtitleEs = "Fondo pizarra oscuro, tarjetas grafito y acentos ámbar urbanos"
    );

    fun label(lang: AppLanguage): String = localizedText(lang, labelTr, labelEn, labelDe, labelFr, labelEs)
    fun subtitle(lang: AppLanguage): String = localizedText(lang, subtitleTr, subtitleEn, subtitleDe, subtitleFr, subtitleEs)
}

enum class ClothingCategory(
    val label: String,
    val labelEn: String,
    val shortLabel: String,
    val labelDe: String = labelEn,
    val labelFr: String = labelEn,
    val labelEs: String = labelEn
) {
    TOP("Üst Giyim", "Tops", "Üst Giyim", "Oberteile", "Hauts", "Partes de arriba"),
    BOTTOM("Alt Giyim", "Bottoms", "Alt Giyim", "Hosen & Röcke", "Bas", "Partes de abajo"),
    OUTERWEAR("Dış Giyim", "Outerwear", "Dış Giyim", "Jacken & Mäntel", "Manteaux & Vestes", "Abrigos y Chaquetas"),
    SHOES("Ayakkabı", "Shoes", "Ayakkabı", "Schuhe", "Chaussures", "Calzado"),
    ACCESSORY("Aksesuar", "Accessories", "Aksesuar", "Accessoires", "Accessoires", "Accesorios"),
    SUIT("Takım & Özel", "Sets & Suits", "Takım", "Anzüge & Kleider", "Ensembles & Robes", "Conjuntos y Vestidos");

    fun display(lang: AppLanguage): String = localizedText(lang, label, labelEn, labelDe, labelFr, labelEs)
}

enum class ClothingSubCategory(
    val label: String,
    val labelEn: String,
    val parentCategory: ClothingCategory,
    val defaultWarmth: Int,
    val labelDe: String = labelEn,
    val labelFr: String = labelEn,
    val labelEs: String = labelEn
) {
    TSHIRT("Tişört", "T-Shirt", ClothingCategory.TOP, 1, "T-Shirt", "T-shirt", "Camiseta"),
    POLO("Polo Yaka", "Polo Shirt", ClothingCategory.TOP, 1, "Poloshirt", "Polo", "Polo"),
    SHIRT("Gömlek", "Shirt", ClothingCategory.TOP, 2, "Hemd / Bluse", "Chemise", "Camisa"),
    CROP_TOP("Crop & Bluz", "Crop & Blouse", ClothingCategory.TOP, 1, "Crop & Bluse", "Crop Top & Blouse", "Crop & Blusa"),
    SWEATSHIRT("Sweatshirt", "Sweatshirt", ClothingCategory.TOP, 3, "Sweatshirt", "Sweatshirt", "Sudadera"),
    HOODIE("Hoodie", "Hoodie", ClothingCategory.TOP, 4, "Hoodie", "Sweat à capuche", "Sudadera con capucha"),
    CARDIGAN("Hırka", "Cardigan", ClothingCategory.TOP, 4, "Strickjacke", "Gilet", "Cárdigan"),
    SWEATER("Kazak & Triko", "Sweater & Knit", ClothingCategory.TOP, 5, "Pullover", "Pull", "Suéter"),
    TANK("Atlet", "Tank Top", ClothingCategory.TOP, 1, "Top", "Débardeur", "Camiseta sin mangas"),

    PANTS("Pantolon", "Trousers", ClothingCategory.BOTTOM, 3, "Stoffhose", "Pantalon", "Pantalón"),
    JEANS("Kot / Jean", "Jeans", ClothingCategory.BOTTOM, 3, "Jeans", "Jean", "Vaqueros"),
    CHINO("Chino / Kumaş", "Chinos", ClothingCategory.BOTTOM, 3, "Chinohose", "Chino", "Chino"),
    CARGO("Kargo Pantolon", "Cargo Pants", ClothingCategory.BOTTOM, 3, "Cargohose", "Pantalon cargo", "Pantalón cargo"),
    SWEATPANTS("Eşofman Altı", "Sweatpants", ClothingCategory.BOTTOM, 3, "Jogginghose", "Jogging", "Pantalón deportivo"),
    SHORTS("Şort", "Shorts", ClothingCategory.BOTTOM, 1, "Shorts", "Short", "Pantalón corto"),
    SKIRT("Etek", "Skirt", ClothingCategory.BOTTOM, 2, "Rock", "Jupe", "Falda"),

    JACKET("Ceket", "Jacket", ClothingCategory.OUTERWEAR, 4, "Jacke", "Veste", "Chaqueta"),
    COAT("Mont & Kaban", "Coat & Puffer", ClothingCategory.OUTERWEAR, 5, "Mantel & Daunenjacke", "Manteau & Doudoune", "Abrigo"),
    BLAZER("Blazer Ceket", "Blazer", ClothingCategory.OUTERWEAR, 3, "Blazer", "Blazer", "Blazer"),
    TRENCH("Trençkot", "Trench Coat", ClothingCategory.OUTERWEAR, 4, "Trenchcoat", "Trench", "Gabardina"),
    VEST("Şişme Yelek", "Puffer Vest", ClothingCategory.OUTERWEAR, 4, "Steppweste", "Doudoune sans manches", "Chaleco"),

    SNEAKER("Sneaker", "Sneakers", ClothingCategory.SHOES, 2, "Sneaker", "Baskets", "Zapatillas"),
    BOOT("Bot", "Boots", ClothingCategory.SHOES, 5, "Stiefel", "Bottines", "Botas"),
    LOAFER("Klasik & Loafer", "Loafers", ClothingCategory.SHOES, 2, "Loafer", "Mocassins", "Mocasines"),
    HEELS("Topuklu Ayakkabı", "Heels", ClothingCategory.SHOES, 2, "Absatzschuhe", "Talons", "Tacones"),
    SLIPPER("Terlik & Sandalet", "Slides & Sandals", ClothingCategory.SHOES, 1, "Sandalen", "Sandales", "Sandalias"),

    WATCH("Saat", "Watch", ClothingCategory.ACCESSORY, 3, "Uhr", "Montre", "Reloj"),
    CAP("Şapka & Bere", "Cap & Beanie", ClothingCategory.ACCESSORY, 3, "Mütze & Cap", "Casquette & Bonnet", "Gorra y Gorro"),
    BAG("Çanta", "Bag", ClothingCategory.ACCESSORY, 3, "Tasche", "Sac", "Bolso"),
    GLASSES("Gözlük", "Sunglasses", ClothingCategory.ACCESSORY, 1, "Sonnenbrille", "Lunettes", "Gafas"),
    BELT("Kemer", "Belt", ClothingCategory.ACCESSORY, 3, "Gürtel", "Ceinture", "Cinturón"),
    SCARF("Atkı", "Scarf", ClothingCategory.ACCESSORY, 5, "Schal", "Écharpe", "Bufanda"),

    SUIT_SET("Takım Elbise", "Suit", ClothingCategory.SUIT, 3, "Anzug", "Costume", "Traje"),
    DRESS("Elbise", "Dress", ClothingCategory.SUIT, 2, "Kleid", "Robe", "Vestido"),
    TRACKSUIT("Eşofman Takımı", "Tracksuit", ClothingCategory.SUIT, 3, "Trainingsanzug", "Survêtement", "Chándal");

    fun display(lang: AppLanguage): String = localizedText(lang, label, labelEn, labelDe, labelFr, labelEs)

    companion object {
        fun forCategory(category: ClothingCategory): List<ClothingSubCategory> =
            entries.filter { it.parentCategory == category }
    }
}

enum class StyleTag(
    val label: String,
    val labelEn: String,
    val labelDe: String = labelEn,
    val labelFr: String = labelEn,
    val labelEs: String = labelEn
) {
    CASUAL("Günlük", "Casual", "Lässig", "Décontracté", "Casual"),
    SPORT("Spor & Rahat", "Sporty", "Sportlich", "Sport", "Deportivo"),
    SMART("Smart Casual", "Smart Casual", "Smart Casual", "Chic décontracté", "Smart Casual"),
    FORMAL("Klasik / Ofis", "Formal", "Formell", "Formel / Bureau", "Formal");

    fun display(lang: AppLanguage): String = localizedText(lang, label, labelEn, labelDe, labelFr, labelEs)
}

enum class Season(
    val label: String,
    val labelEn: String,
    val labelDe: String = labelEn,
    val labelFr: String = labelEn,
    val labelEs: String = labelEn
) {
    ALL("4 Mevsim", "All Seasons", "Alle Jahreszeiten", "Toutes saisons", "Todo el año"),
    SPRING("İlkbahar", "Spring", "Frühling", "Printemps", "Primavera"),
    SUMMER("Yaz", "Summer", "Sommer", "Été", "Verano"),
    AUTUMN("Sonbahar", "Autumn", "Herbst", "Automne", "Otoño"),
    WINTER("Kış", "Winter", "Winter", "Hiver", "Invierno");

    fun display(lang: AppLanguage): String = localizedText(lang, label, labelEn, labelDe, labelFr, labelEs)
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
    val hueGroup: ColorHueGroup,
    val labelDe: String = labelEn,
    val labelFr: String = labelEn,
    val labelEs: String = labelEn
) {
    WHITE("Beyaz", "White", 0xFFFAFAFA, true, ColorHueGroup.NEUTRAL_LIGHT, "Weiß", "Blanc", "Blanco"),
    ECRU("Ekru / Krem", "Ecru / Cream", 0xFFF3EFE4, true, ColorHueGroup.NEUTRAL_LIGHT, "Ecru / Creme", "Écru / Crème", "Crudo / Crema"),
    BEIGE("Bej", "Beige", 0xFFDCCBB2, true, ColorHueGroup.EARTH, "Beige", "Beige", "Beige"),
    STONE("Taş / Vizon", "Stone", 0xFFC2B5A3, true, ColorHueGroup.EARTH, "Steingrau", "Taupe / Pierre", "Piedra / Topo"),
    CAMEL("Camel", "Camel", 0xFFB88B58, true, ColorHueGroup.EARTH, "Camel", "Camel", "Camel"),
    BROWN("Kahverengi", "Brown", 0xFF5E3B24, true, ColorHueGroup.EARTH, "Braun", "Marron", "Marrón"),
    LIGHT_GRAY("Açık Gri", "Light Gray", 0xFFD0D1CE, true, ColorHueGroup.NEUTRAL_LIGHT, "Hellgrau", "Gris clair", "Gris claro"),
    GRAY("Gri", "Gray", 0xFF8E908C, true, ColorHueGroup.NEUTRAL_LIGHT, "Grau", "Gris", "Gris"),
    CHARCOAL("Antrasit", "Charcoal", 0xFF434542, true, ColorHueGroup.NEUTRAL_DARK, "Anthrazit", "Anthracite", "Antracita"),
    BLACK("Siyah", "Black", 0xFF1A1A19, true, ColorHueGroup.NEUTRAL_DARK, "Schwarz", "Noir", "Negro"),
    NAVY("Lacivert", "Navy", 0xFF1B2A44, true, ColorHueGroup.BLUE, "Marineblau", "Bleu marine", "Azul marino"),

    DENIM("Denim Mavi", "Denim Blue", 0xFF4A6E8E, false, ColorHueGroup.BLUE, "Denimblau", "Bleu denim", "Azul vaquero"),
    SKY("Açık Mavi", "Sky Blue", 0xFF8FB8D8, false, ColorHueGroup.BLUE, "Hellblau", "Bleu ciel", "Azul cielo"),
    COBALT("Saks Mavi", "Cobalt Blue", 0xFF2152A3, false, ColorHueGroup.BLUE, "Kobaltblau", "Bleu cobalt", "Azul cobalto"),
    TURQUOISE("Turkuaz", "Turquoise", 0xFF3A9B9C, false, ColorHueGroup.COOL_ACCENT, "Türkis", "Turquoise", "Turquesa"),
    MINT("Mint Yeşili", "Mint", 0xFF98C9B5, false, ColorHueGroup.GREEN, "Mintgrün", "Menthe", "Menta"),
    SAGE("Adaçayı", "Sage", 0xFF839680, false, ColorHueGroup.GREEN, "Salbeigrün", "Sauge", "Salvia"),
    GREEN("Yeşil", "Green", 0xFF2E6F44, false, ColorHueGroup.GREEN, "Grün", "Vert", "Verde"),
    OLIVE("Haki / Zeytin", "Olive / Khaki", 0xFF556042, false, ColorHueGroup.GREEN, "Oliv / Khaki", "Kaki / Olive", "Oliva / Caqui"),
    YELLOW("Sarı", "Yellow", 0xFFEAC435, false, ColorHueGroup.WARM_ACCENT, "Gelb", "Jaune", "Amarillo"),
    MUSTARD("Hardal", "Mustard", 0xFFC8922B, false, ColorHueGroup.WARM_ACCENT, "Senfgelb", "Moutarde", "Mostaza"),
    ORANGE("Turuncu", "Orange", 0xFFD96B38, false, ColorHueGroup.WARM_ACCENT, "Orange", "Orange", "Naranja"),
    TERRACOTTA("Kiremit", "Terracotta", 0xFFB6533C, false, ColorHueGroup.WARM_ACCENT, "Terrakotta", "Terracotta", "Terracota"),
    RED("Kırmızı", "Red", 0xFFC23232, false, ColorHueGroup.WARM_ACCENT, "Rot", "Rouge", "Rojo"),
    BURGUNDY("Bordo", "Burgundy", 0xFF6B212A, false, ColorHueGroup.WARM_ACCENT, "Bordeaux", "Bordeaux", "Burdeos"),
    BLUSH("Pudra", "Blush", 0xFFE8CCD7, false, ColorHueGroup.NEUTRAL_LIGHT, "Puderrosa", "Rose poudré", "Rosa empolvado"),
    PINK("Pembe", "Pink", 0xFFE07A9A, false, ColorHueGroup.WARM_ACCENT, "Rosa", "Rose", "Rosa"),
    PURPLE("Mor / Lila", "Purple", 0xFF6E5282, false, ColorHueGroup.COOL_ACCENT, "Lila", "Violet", "Morado");

    val composeColor: Color get() = Color(hex)
    fun display(lang: AppLanguage): String = localizedText(lang, label, labelEn, labelDe, labelFr, labelEs)
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
    val productUrl: String = "",
    val lastCheckedPrice: Double = 0.0,
    val previousPrice: Double = 0.0,
    val priceDropped: Boolean = false,
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

package com.dresskit.wardrobe.model

import androidx.compose.ui.graphics.Color

enum class AppLanguage(val code: String, val label: String) {
    TR("tr", "Türkçe"),
    EN("en", "English"),
    DE("de", "Deutsch"),
    FR("fr", "Français"),
    ES("es", "Español"),
    ZH("zh", "中文")
}

fun localizedText(
    lang: AppLanguage,
    tr: String,
    en: String,
    de: String = en,
    fr: String = en,
    es: String = en,
    zh: String = en
): String = when (lang) {
    AppLanguage.TR -> tr
    AppLanguage.EN -> en
    AppLanguage.DE -> de
    AppLanguage.FR -> fr
    AppLanguage.ES -> es
    AppLanguage.ZH -> zh
}

enum class GenderPreference {
    MALE,
    FEMALE,
    UNISEX;

    fun label(lang: AppLanguage): String = when (this) {
        MALE -> localizedText(lang, "Erkek", "Men", "Herren", "Homme", "Hombre", "男装")
        FEMALE -> localizedText(lang, "Kadın", "Women", "Damen", "Femme", "Mujer", "女装")
        UNISEX -> localizedText(lang, "Unisex", "Unisex", "Unisex", "Unisex", "Unisex", "中性/百搭")
    }
}

enum class AppThemeMode(
    val labelTr: String,
    val labelEn: String,
    val labelDe: String,
    val labelFr: String,
    val labelEs: String,
    val labelZh: String,
    val subtitleTr: String,
    val subtitleEn: String,
    val subtitleDe: String,
    val subtitleFr: String,
    val subtitleEs: String,
    val subtitleZh: String
) {
    MODERN(
        labelTr = "Modern (Açık)",
        labelEn = "Modern (Light)",
        labelDe = "Modern (Hell)",
        labelFr = "Moderne (Clair)",
        labelEs = "Moderno (Claro)",
        labelZh = "现代 (浅色)",
        subtitleTr = "Ferah krem zemin, beyaz kartlar ve sade katalog görünümü",
        subtitleEn = "Airy cream canvas, crisp white cards and minimal catalog aesthetic",
        subtitleDe = "Heller Cremeton, weiße Karten und minimalistische Katalogoptik",
        subtitleFr = "Fond crème lumineux, cartes blanches épurées et style catalogue",
        subtitleEs = "Fondo crema luminoso, tarjetas blancas limpias y estética minimalista",
        subtitleZh = "柔和米白画布、纯白卡片与极简目录美学"
    ),
    MODERN_DARK(
        labelTr = "Modern (Koyu)",
        labelEn = "Modern (Dark)",
        labelDe = "Modern (Dunkel)",
        labelFr = "Moderne (Sombre)",
        labelEs = "Moderno (Oscuro)",
        labelZh = "现代 (深色)",
        subtitleTr = "Koyu oniks zemin, sıcak taş kartlar ve terracotta vurgular",
        subtitleEn = "Deep onyx canvas, warm stone cards and terracotta accents",
        subtitleDe = "Tiefer Onyx-Hintergrund, warme Steinkarten und Terrakotta-Akzente",
        subtitleFr = "Fond onyx profond, cartes pierre chaude et accents terracotta",
        subtitleEs = "Fondo ónix profundo, tarjetas piedra cálida y acentos terracota",
        subtitleZh = "深邃玛瑙黑背景、暖石卡片与赤陶点缀"
    ),
    STREET(
        labelTr = "Sokak Tarzı",
        labelEn = "Street Style",
        labelDe = "Streetstyle",
        labelFr = "Street Style",
        labelEs = "Estilo Urbano",
        labelZh = "街头潮流",
        subtitleTr = "Koyu arduvaz zemin, grafit kartlar ve sıcak kehribar sokak vurguları",
        subtitleEn = "Deep slate dark canvas, graphite cards and warm amber street accents",
        subtitleDe = "Schieferdunkler Hintergrund, Graphitkarten und warme Bernstein-Akzente",
        subtitleFr = "Fond ardoise sombre, cartes graphite et accents ambre urbains",
        subtitleEs = "Fondo pizarra oscuro, tarjetas grafito y acentos ámbar urbanos",
        subtitleZh = "深岩灰背景、石墨卡片与暖琥珀街头感点缀"
    );

    fun label(lang: AppLanguage): String = localizedText(lang, labelTr, labelEn, labelDe, labelFr, labelEs, labelZh)
    fun subtitle(lang: AppLanguage): String = localizedText(lang, subtitleTr, subtitleEn, subtitleDe, subtitleFr, subtitleEs, subtitleZh)
}

enum class ClothingCategory(
    val label: String,
    val labelEn: String,
    val shortLabel: String,
    val labelDe: String = labelEn,
    val labelFr: String = labelEn,
    val labelEs: String = labelEn,
    val labelZh: String = labelEn
) {
    TOP("Üst Giyim", "Tops", "Üst Giyim", "Oberteile", "Hauts", "Partes de arriba", "上装"),
    BOTTOM("Alt Giyim", "Bottoms", "Alt Giyim", "Hosen & Röcke", "Bas", "Partes de abajo", "下装"),
    OUTERWEAR("Dış Giyim", "Outerwear", "Dış Giyim", "Jacken & Mäntel", "Manteaux & Vestes", "Abrigos y Chaquetas", "外套"),
    SHOES("Ayakkabı", "Shoes", "Ayakkabı", "Schuhe", "Chaussures", "Calzado", "鞋履"),
    ACCESSORY("Aksesuar", "Accessories", "Aksesuar", "Accessoires", "Accessoires", "Accesorios", "配饰"),
    SUIT("Takım & Özel", "Sets & Suits", "Takım", "Anzüge & Kleider", "Ensembles & Robes", "Conjuntos y Vestidos", "套装与连衣裙");

    fun display(lang: AppLanguage): String = localizedText(lang, label, labelEn, labelDe, labelFr, labelEs, labelZh)
}

enum class ClothingSubCategory(
    val label: String,
    val labelEn: String,
    val parentCategory: ClothingCategory,
    val defaultWarmth: Int,
    val labelDe: String = labelEn,
    val labelFr: String = labelEn,
    val labelEs: String = labelEn,
    val labelZh: String = labelEn
) {
    TSHIRT("Tişört", "T-Shirt", ClothingCategory.TOP, 1, "T-Shirt", "T-shirt", "Camiseta", "T恤"),
    POLO("Polo Yaka", "Polo Shirt", ClothingCategory.TOP, 1, "Poloshirt", "Polo", "Polo", "Polo衫"),
    SHIRT("Gömlek", "Shirt", ClothingCategory.TOP, 2, "Hemd / Bluse", "Chemise", "Camisa", "衬衫"),
    CROP_TOP("Crop & Bluz", "Crop & Blouse", ClothingCategory.TOP, 1, "Crop & Bluse", "Crop Top & Blouse", "Crop & Blusa", "短款/女衫"),
    SWEATSHIRT("Sweatshirt", "Sweatshirt", ClothingCategory.TOP, 3, "Sweatshirt", "Sweatshirt", "Sudadera", "套头卫衣"),
    HOODIE("Hoodie", "Hoodie", ClothingCategory.TOP, 4, "Hoodie", "Sweat à capuche", "Sudadera con capucha", "连帽卫衣"),
    CARDIGAN("Hırka", "Cardigan", ClothingCategory.TOP, 4, "Strickjacke", "Gilet", "Cárdigan", "针织开衫"),
    SWEATER("Kazak & Triko", "Sweater & Knit", ClothingCategory.TOP, 5, "Pullover", "Pull", "Suéter", "毛衣/针织"),
    TANK("Atlet", "Tank Top", ClothingCategory.TOP, 1, "Top", "Débardeur", "Camiseta sin mangas", "背心"),

    PANTS("Pantolon", "Trousers", ClothingCategory.BOTTOM, 3, "Stoffhose", "Pantalon", "Pantalón", "长裤"),
    JEANS("Kot / Jean", "Jeans", ClothingCategory.BOTTOM, 3, "Jeans", "Jean", "Vaqueros", "牛仔裤"),
    CHINO("Chino / Kumaş", "Chinos", ClothingCategory.BOTTOM, 3, "Chinohose", "Chino", "Chino", "休闲西裤"),
    CARGO("Kargo Pantolon", "Cargo Pants", ClothingCategory.BOTTOM, 3, "Cargohose", "Pantalon cargo", "Pantalón cargo", "工装裤"),
    SWEATPANTS("Eşofman Altı", "Sweatpants", ClothingCategory.BOTTOM, 3, "Jogginghose", "Jogging", "Pantalón deportivo", "运动裤"),
    SHORTS("Şort", "Shorts", ClothingCategory.BOTTOM, 1, "Shorts", "Short", "Pantalón corto", "短裤"),
    SKIRT("Etek", "Skirt", ClothingCategory.BOTTOM, 2, "Rock", "Jupe", "Falda", "半身裙"),

    JACKET("Ceket", "Jacket", ClothingCategory.OUTERWEAR, 4, "Jacke", "Veste", "Chaqueta", "夹克"),
    COAT("Mont & Kaban", "Coat & Puffer", ClothingCategory.OUTERWEAR, 5, "Mantel & Daunenjacke", "Manteau & Doudoune", "Abrigo", "大衣/羽绒服"),
    BLAZER("Blazer Ceket", "Blazer", ClothingCategory.OUTERWEAR, 3, "Blazer", "Blazer", "Blazer", "西装外套"),
    TRENCH("Trençkot", "Trench Coat", ClothingCategory.OUTERWEAR, 4, "Trenchcoat", "Trench", "Gabardina", "风衣"),
    VEST("Şişme Yelek", "Puffer Vest", ClothingCategory.OUTERWEAR, 4, "Steppweste", "Doudoune sans manches", "Chaleco", "马甲"),

    SNEAKER("Sneaker", "Sneakers", ClothingCategory.SHOES, 2, "Sneaker", "Baskets", "Zapatillas", "运动鞋"),
    BOOT("Bot", "Boots", ClothingCategory.SHOES, 5, "Stiefel", "Bottines", "Botas", "靴子"),
    LOAFER("Klasik & Loafer", "Loafers", ClothingCategory.SHOES, 2, "Loafer", "Mocassins", "Mocasines", "乐福/皮鞋"),
    HEELS("Topuklu Ayakkabı", "Heels", ClothingCategory.SHOES, 2, "Absatzschuhe", "Talons", "Tacones", "高跟鞋"),
    SLIPPER("Terlik & Sandalet", "Slides & Sandals", ClothingCategory.SHOES, 1, "Sandalen", "Sandales", "Sandalias", "凉拖/凉鞋"),

    WATCH("Saat", "Watch", ClothingCategory.ACCESSORY, 3, "Uhr", "Montre", "Reloj", "手表"),
    CAP("Şapka & Bere", "Cap & Beanie", ClothingCategory.ACCESSORY, 3, "Mütze & Cap", "Casquette & Bonnet", "Gorra y Gorro", "帽子"),
    BAG("Çanta", "Bag", ClothingCategory.ACCESSORY, 3, "Tasche", "Sac", "Bolso", "包包"),
    GLASSES("Gözlük", "Sunglasses", ClothingCategory.ACCESSORY, 1, "Sonnenbrille", "Lunettes", "Gafas", "墨镜"),
    BELT("Kemer", "Belt", ClothingCategory.ACCESSORY, 3, "Gürtel", "Ceinture", "Cinturón", "腰带"),
    SCARF("Atkı", "Scarf", ClothingCategory.ACCESSORY, 5, "Schal", "Écharpe", "Bufanda", "围巾"),

    SUIT_SET("Takım Elbise", "Suit", ClothingCategory.SUIT, 3, "Anzug", "Costume", "Traje", "西服套装"),
    DRESS("Elbise", "Dress", ClothingCategory.SUIT, 2, "Kleid", "Robe", "Vestido", "连衣裙"),
    TRACKSUIT("Eşofman Takımı", "Tracksuit", ClothingCategory.SUIT, 3, "Trainingsanzug", "Survêtement", "Chándal", "运动套装");

    fun display(lang: AppLanguage): String = localizedText(lang, label, labelEn, labelDe, labelFr, labelEs, labelZh)

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
    val labelEs: String = labelEn,
    val labelZh: String = labelEn
) {
    CASUAL("Günlük", "Casual", "Lässig", "Décontracté", "Casual", "日常休闲"),
    SPORT("Spor & Rahat", "Sporty", "Sportlich", "Sport", "Deportivo", "运动潮流"),
    SMART("Smart Casual", "Smart Casual", "Smart Casual", "Chic décontracté", "Smart Casual", "轻熟商务"),
    FORMAL("Klasik / Ofis", "Formal", "Formell", "Formel / Bureau", "Formal", "正式通勤");

    fun display(lang: AppLanguage): String = localizedText(lang, label, labelEn, labelDe, labelFr, labelEs, labelZh)
}

enum class Season(
    val label: String,
    val labelEn: String,
    val labelDe: String = labelEn,
    val labelFr: String = labelEn,
    val labelEs: String = labelEn,
    val labelZh: String = labelEn
) {
    ALL("4 Mevsim", "All Seasons", "Alle Jahreszeiten", "Toutes saisons", "Todo el año", "四季通用"),
    SPRING("İlkbahar", "Spring", "Frühling", "Printemps", "Primavera", "春季"),
    SUMMER("Yaz", "Summer", "Sommer", "Été", "Verano", "夏季"),
    AUTUMN("Sonbahar", "Autumn", "Herbst", "Automne", "Otoño", "秋季"),
    WINTER("Kış", "Winter", "Winter", "Hiver", "Invierno", "冬季");

    fun display(lang: AppLanguage): String = localizedText(lang, label, labelEn, labelDe, labelFr, labelEs, labelZh)
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
    val labelEs: String = labelEn,
    val labelZh: String = labelEn
) {
    WHITE("Beyaz", "White", 0xFFFAFAFA, true, ColorHueGroup.NEUTRAL_LIGHT, "Weiß", "Blanc", "Blanco", "白色"),
    ECRU("Ekru / Krem", "Ecru / Cream", 0xFFF3EFE4, true, ColorHueGroup.NEUTRAL_LIGHT, "Ecru / Creme", "Écru / Crème", "Crudo / Crema", "米白/奶白"),
    BEIGE("Bej", "Beige", 0xFFDCCBB2, true, ColorHueGroup.EARTH, "Beige", "Beige", "Beige", "米色"),
    STONE("Taş / Vizon", "Stone", 0xFFC2B5A3, true, ColorHueGroup.EARTH, "Steingrau", "Taupe / Pierre", "Piedra / Topo", "石灰色"),
    CAMEL("Camel", "Camel", 0xFFB88B58, true, ColorHueGroup.EARTH, "Camel", "Camel", "Camel", "驼色"),
    BROWN("Kahverengi", "Brown", 0xFF5E3B24, true, ColorHueGroup.EARTH, "Braun", "Marron", "Marrón", "棕色"),
    LIGHT_GRAY("Açık Gri", "Light Gray", 0xFFD0D1CE, true, ColorHueGroup.NEUTRAL_LIGHT, "Hellgrau", "Gris clair", "Gris claro", "浅灰"),
    GRAY("Gri", "Gray", 0xFF8E908C, true, ColorHueGroup.NEUTRAL_LIGHT, "Grau", "Gris", "Gris", "灰色"),
    CHARCOAL("Antrasit", "Charcoal", 0xFF434542, true, ColorHueGroup.NEUTRAL_DARK, "Anthrazit", "Anthracite", "Antracita", "炭灰"),
    BLACK("Siyah", "Black", 0xFF1A1A19, true, ColorHueGroup.NEUTRAL_DARK, "Schwarz", "Noir", "Negro", "黑色"),
    NAVY("Lacivert", "Navy", 0xFF1B2A44, true, ColorHueGroup.BLUE, "Marineblau", "Bleu marine", "Azul marino", "藏青"),

    DENIM("Denim Mavi", "Denim Blue", 0xFF4A6E8E, false, ColorHueGroup.BLUE, "Denimblau", "Bleu denim", "Azul vaquero", "牛仔蓝"),
    SKY("Açık Mavi", "Sky Blue", 0xFF8FB8D8, false, ColorHueGroup.BLUE, "Hellblau", "Bleu ciel", "Azul cielo", "天蓝"),
    COBALT("Saks Mavi", "Cobalt Blue", 0xFF2152A3, false, ColorHueGroup.BLUE, "Kobaltblau", "Bleu cobalt", "Azul cobalto", "钴蓝"),
    TURQUOISE("Turkuaz", "Turquoise", 0xFF3A9B9C, false, ColorHueGroup.COOL_ACCENT, "Türkis", "Turquoise", "Turquesa", "青绿/松石"),
    MINT("Mint Yeşili", "Mint", 0xFF98C9B5, false, ColorHueGroup.GREEN, "Mintgrün", "Menthe", "Menta", "薄荷绿"),
    SAGE("Adaçayı", "Sage", 0xFF839680, false, ColorHueGroup.GREEN, "Salbeigrün", "Sauge", "Salvia", "鼠尾草绿"),
    GREEN("Yeşil", "Green", 0xFF2E6F44, false, ColorHueGroup.GREEN, "Grün", "Vert", "Verde", "绿色"),
    OLIVE("Haki / Zeytin", "Olive / Khaki", 0xFF556042, false, ColorHueGroup.GREEN, "Oliv / Khaki", "Kaki / Olive", "Oliva / Caqui", "军绿/橄榄绿"),
    YELLOW("Sarı", "Yellow", 0xFFEAC435, false, ColorHueGroup.WARM_ACCENT, "Gelb", "Jaune", "Amarillo", "黄色"),
    MUSTARD("Hardal", "Mustard", 0xFFC8922B, false, ColorHueGroup.WARM_ACCENT, "Senfgelb", "Moutarde", "Mostaza", "芥末黄"),
    ORANGE("Turuncu", "Orange", 0xFFD96B38, false, ColorHueGroup.WARM_ACCENT, "Orange", "Orange", "Naranja", "橙色"),
    TERRACOTTA("Kiremit", "Terracotta", 0xFFB6533C, false, ColorHueGroup.WARM_ACCENT, "Terrakotta", "Terracotta", "Terracota", "赤陶橘"),
    RED("Kırmızı", "Red", 0xFFC23232, false, ColorHueGroup.WARM_ACCENT, "Rot", "Rouge", "Rojo", "红色"),
    BURGUNDY("Bordo", "Burgundy", 0xFF6B212A, false, ColorHueGroup.WARM_ACCENT, "Bordeaux", "Bordeaux", "Burdeos", "酒红"),
    BLUSH("Pudra", "Blush", 0xFFE8CCD7, false, ColorHueGroup.NEUTRAL_LIGHT, "Puderrosa", "Rose poudré", "Rosa empolvado", "裸粉"),
    PINK("Pembe", "Pink", 0xFFE07A9A, false, ColorHueGroup.WARM_ACCENT, "Rosa", "Rose", "Rosa", "粉色"),
    PURPLE("Mor / Lila", "Purple", 0xFF6E5282, false, ColorHueGroup.COOL_ACCENT, "Lila", "Violet", "Morado", "紫色"),

    CHAMPAGNE("Şampanya", "Champagne", 0xFFF2E6D0, true, ColorHueGroup.NEUTRAL_LIGHT, "Champagner", "Champagne", "Champán", "香槟色"),
    SAND("Kum Beji", "Sand", 0xFFCDB99C, true, ColorHueGroup.EARTH, "Sand", "Sable", "Arena", "沙色"),
    CHOCOLATE("Çikolata Kahve", "Chocolate", 0xFF3E261A, false, ColorHueGroup.EARTH, "Schokolade", "Chocolat", "Chocolate", "巧克力棕"),
    SLATE_BLUE("Arduvaz Mavi", "Slate Blue", 0xFF53687E, false, ColorHueGroup.BLUE, "Schieferblau", "Bleu ardoise", "Azul pizarra", "板岩蓝"),
    ICE_BLUE("Buz Mavisi", "Ice Blue", 0xFFB8D4E8, false, ColorHueGroup.BLUE, "Eisblau", "Bleu glacier", "Azul hielo", "冰蓝"),
    PETROL("Petrol Yeşili", "Petrol", 0xFF19535F, false, ColorHueGroup.COOL_ACCENT, "Petrol", "Bleu pétrole", "Petróleo", "孔雀蓝"),
    TEAL("Camgöbeği / Teal", "Teal", 0xFF2A7B7C, false, ColorHueGroup.COOL_ACCENT, "Blaugrün", "Sarcelle", "Verde azulado", "水鸭绿"),
    EMERALD("Zümrüt Yeşili", "Emerald", 0xFF1B6B4F, false, ColorHueGroup.GREEN, "Smaragdgrün", "Émeraude", "Esmeralda", "祖母绿"),
    FOREST("Orman Yeşili", "Forest Green", 0xFF214231, false, ColorHueGroup.GREEN, "Waldgrün", "Vert forêt", "Verde bosque", "森林绿"),
    OCHRE("Safran / Ochre", "Ochre", 0xFFC78A28, false, ColorHueGroup.WARM_ACCENT, "Ocker", "Ocre", "Ocre", "赭石黄"),
    RUST("Pas Turuncu", "Rust", 0xFFA4452B, false, ColorHueGroup.WARM_ACCENT, "Rostrot", "Rouille", "Óxido", "铁锈红"),
    CORAL("Mercan", "Coral", 0xFFE36B5B, false, ColorHueGroup.WARM_ACCENT, "Koralle", "Corail", "Coral", "珊瑚红"),
    WINE("Şarap Bordo", "Wine Red", 0xFF5A1A28, false, ColorHueGroup.WARM_ACCENT, "Weinrot", "Lie-de-vin", "Vino", "深红酒"),
    ROSEWOOD("Gül Kurusu", "Rosewood", 0xFFB36A72, false, ColorHueGroup.WARM_ACCENT, "Rosenholz", "Bois de rose", "Palo de rosa", "玫瑰木"),
    LAVENDER("Lavanta", "Lavender", 0xFFB7A6D6, false, ColorHueGroup.COOL_ACCENT, "Lavendel", "Lavande", "Lavanda", "薰衣草紫"),
    PLUM("Mürdüm", "Plum", 0xFF4E2A4F, false, ColorHueGroup.COOL_ACCENT, "Pflaume", "Prune", "Ciruela", "梅子紫");

    val composeColor: Color get() = Color(hex)
    fun display(lang: AppLanguage): String = localizedText(lang, label, labelEn, labelDe, labelFr, labelEs, labelZh)
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
    val catalogPhotoFileNames: List<String> = emptyList(),
    val useCatalogPhotos: Boolean = false,
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
        get() = when {
            useOriginalPhotos && originalPhotoFileNames.size == photoFileNames.size && originalPhotoFileNames.isNotEmpty() ->
                originalPhotoFileNames
            useCatalogPhotos && catalogPhotoFileNames.isNotEmpty() ->
                photoFileNames.mapIndexed { idx, cutout ->
                    catalogPhotoFileNames.getOrNull(idx)?.takeIf { it.isNotBlank() } ?: cutout
                }
            else -> photoFileNames
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

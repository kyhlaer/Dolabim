package com.dresskit.wardrobe.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FaceRetouchingNatural
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Female
import androidx.compose.material.icons.outlined.Male
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.animation.animateContentSize
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.dresskit.wardrobe.data.AppFeatureKey
import com.dresskit.wardrobe.data.ColorSeason
import com.dresskit.wardrobe.data.FacialContrast
import com.dresskit.wardrobe.data.ImageStorage
import com.dresskit.wardrobe.data.PersonalColorAnalyzer
import com.dresskit.wardrobe.data.StoreSearchLinks
import com.dresskit.wardrobe.data.PersonalColorProfile
import com.dresskit.wardrobe.data.RemoteConfigState
import com.dresskit.wardrobe.model.AppLanguage
import com.dresskit.wardrobe.model.ClothingItem
import com.dresskit.wardrobe.model.GarmentColor
import com.dresskit.wardrobe.model.GenderPreference
import com.dresskit.wardrobe.model.localizedText
import com.dresskit.wardrobe.ui.components.ColorDot
import com.dresskit.wardrobe.ui.components.FilterChipPill
import com.dresskit.wardrobe.ui.components.HarmonyScoreBadge
import com.dresskit.wardrobe.ui.components.RemoteAdSlotBanner
import com.dresskit.wardrobe.ui.components.bouncyOverscroll
import com.dresskit.wardrobe.ui.theme.DressKitStyle
import com.dresskit.wardrobe.ui.theme.LocalAppLanguage
import com.dresskit.wardrobe.ui.theme.appStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

enum class LookbookGender(
    val trLabel: String,
    val enLabel: String,
    val deLabel: String,
    val frLabel: String,
    val esLabel: String,
    val zhLabel: String
) {
    MEN("Erkek", "Men", "Herren", "Homme", "Hombre", "男装"),
    WOMEN("Kadın", "Women", "Damen", "Femme", "Mujer", "女装");

    fun label(lang: AppLanguage): String = localizedText(lang, trLabel, enLabel, deLabel, frLabel, esLabel, zhLabel)
}

enum class InspirationCategory(val trLabel: String, val enLabel: String, val zhLabel: String = enLabel) {
    ALL("Tümü", "All", "全部"),
    OVERSIZE("Oversize & Hoodie", "Oversize & Hoodie", "宽松卫衣"),
    LAYERING("Katman & Ceket", "Layering & Jackets", "叠穿外套"),
    DENIM_CARGO("Denim & Kargo", "Denim & Cargo", "牛仔工装"),
    TRACK_SPORT("Tracksuit & Sport", "Tracksuit & Sport", "运动套装"),
    SMART_STREET("Smart Street", "Smart Street", "轻熟街头");

    fun label(lang: AppLanguage): String = when (lang) {
        AppLanguage.TR -> trLabel
        AppLanguage.ZH -> zhLabel
        else -> enLabel
    }
}

data class StreetLookItem(
    val id: String,
    val imageUrl: String,
    val category: InspirationCategory,
    val harmonyPercent: Int,
    val trTitle: String,
    val enTitle: String,
    val trPalette: String,
    val enPalette: String,
    val trPieces: List<String>,
    val enPieces: List<String>,
    val colorDots: List<Color>
)

private val VERIFIED_STREET_LOOKS = listOf(
    StreetLookItem(
        id = "ys_4",
        imageUrl = "https://images.unsplash.com/photo-1783597873127-761b2e74fc5b?w=900&auto=format&fit=crop&q=80",
        category = InspirationCategory.LAYERING,
        harmonyPercent = 92,
        trTitle = "Desenli Sokak Ceketi & Siyah Tote",
        enTitle = "Patterned Street Jacket & Black Tote",
        trPalette = "Siyah + Ekru Grafik Doku",
        enPalette = "Black + Ecru Graphic Texture",
        trPieces = listOf("Ekru-Siyah Grafik Ceket", "Düz Siyah Pantolon", "Kanvas Tote Çanta"),
        enPieces = listOf("Ecru-Black Graphic Jacket", "Straight Black Pants", "Canvas Tote Bag"),
        colorDots = listOf(Color(0xFF17181C), Color(0xFFEDE8DF), Color(0xFF575A61))
    ),
    StreetLookItem(
        id = "ys_5",
        imageUrl = "https://images.unsplash.com/photo-1772521234184-3730c30c4cb5?w=900&auto=format&fit=crop&q=80",
        category = InspirationCategory.LAYERING,
        harmonyPercent = 93,
        trTitle = "Varsity Ceket & Beyzbol Şapkası",
        enTitle = "Monochrome Panel Jacket & Cap",
        trPalette = "Siyah-Beyaz Kolej Sokak Kontrastı",
        enPalette = "Black & White College Street Contrast",
        trPieces = listOf("Siyah-Beyaz Kolej Ceket", "Siyah Crewneck", "Fitted Kep Şapka"),
        enPieces = listOf("B&W Varsity Jacket", "Black Crewneck", "Fitted Cap"),
        colorDots = listOf(Color(0xFF141416), Color(0xFFF3F3F1), Color(0xFF696E77))
    ),
    StreetLookItem(
        id = "ys_1",
        imageUrl = "https://images.unsplash.com/photo-1634853166008-2c9e90321c92?w=900&auto=format&fit=crop&q=80",
        category = InspirationCategory.OVERSIZE,
        harmonyPercent = 96,
        trTitle = "All-Black Hoodie & Beyaz Sneaker",
        enTitle = "All-Black Hoodie & Crisp White Sneakers",
        trPalette = "Tam Siyah + Temiz Beyaz Taban Kontrastı",
        enPalette = "Pitch Black + Crisp White Sole Contrast",
        trPieces = listOf("Siyah Heavy Hoodie", "Siyah Jogger Pantolon", "Beyaz Low-Top Sneaker"),
        enPieces = listOf("Black Heavy Hoodie", "Black Joggers", "White Low-Top Sneakers"),
        colorDots = listOf(Color(0xFF151517), Color(0xFF2B2D32), Color(0xFFF6F6F4))
    ),
    StreetLookItem(
        id = "ys_2",
        imageUrl = "https://images.unsplash.com/photo-1773236175203-45a34fb5a53f?w=900&auto=format&fit=crop&q=80",
        category = InspirationCategory.LAYERING,
        harmonyPercent = 94,
        trTitle = "Açık Denim Ceket & Gri Hoodie",
        enTitle = "Light Denim Jacket Over Grey Hoodie",
        trPalette = "Buz Mavisi Denim + Melanj Gri",
        enPalette = "Ice Blue Denim + Heather Grey",
        trPieces = listOf("Açık Yıkama Denim Ceket", "Gri Kapüşonlu Hoodie", "Rahat Kalıp Pantolon"),
        enPieces = listOf("Light-Wash Denim Jacket", "Heather Grey Hoodie", "Relaxed Trousers"),
        colorDots = listOf(Color(0xFF6B8FB5), Color(0xFF9EA3AB), Color(0xFF1E2229))
    ),
    StreetLookItem(
        id = "ys_3",
        imageUrl = "https://images.unsplash.com/photo-1773235892650-b3806bb723e1?w=900&auto=format&fit=crop&q=80",
        category = InspirationCategory.SMART_STREET,
        harmonyPercent = 91,
        trTitle = "Şehir Ekibi · Nötr Katman Uyumu",
        enTitle = "City Crew · Neutral Street Layers",
        trPalette = "Kömür, Taş Grisi & Kemik Tonları",
        enPalette = "Charcoal, Stone Grey & Bone Tones",
        trPieces = listOf("Oversize Üst & Hafif Ceket", "Bol Paça Sokak Pantolonu", "Retro Sneaker"),
        enPieces = listOf("Oversized Top & Light Jacket", "Wide-Leg Street Pants", "Retro Sneakers"),
        colorDots = listOf(Color(0xFF222428), Color(0xFFD6CFC4), Color(0xFF5D6673))
    ),
    StreetLookItem(
        id = "ys_6",
        imageUrl = "https://images.unsplash.com/photo-1590061172593-6cd0c9ef3090?w=900&auto=format&fit=crop&q=80",
        category = InspirationCategory.DENIM_CARGO,
        harmonyPercent = 90,
        trTitle = "Fermuarlı Koyu Ceket & Mavi Denim",
        enTitle = "Dark Zip Jacket & Blue Denim",
        trPalette = "Koyu İndigo + Siyah Ceket Dengesi",
        enPalette = "Deep Indigo Denim + Black Zip Layer",
        trPieces = listOf("Fermuarlı Koyu Ceket", "Rahat Kalıp Mavi Jean", "Sokak Şapkası"),
        enPieces = listOf("Dark Zip Jacket", "Relaxed Blue Jeans", "Street Cap"),
        colorDots = listOf(Color(0xFF1A1C20), Color(0xFF3A5A8C), Color(0xFF8C97A6))
    ),
    StreetLookItem(
        id = "ys_7",
        imageUrl = "https://images.pexels.com/photos/15127546/pexels-photo-15127546.jpeg?auto=compress&cs=tinysrgb&w=900",
        category = InspirationCategory.OVERSIZE,
        harmonyPercent = 95,
        trTitle = "Oversize Siyah Hoodie & Gözlük",
        enTitle = "Oversize Black Hoodie & Shades",
        trPalette = "Mat Siyah Monokrom Sokak Silüeti",
        enPalette = "Matte Black Monochrome Silhouette",
        trPieces = listOf("Drop-Shoulder Siyah Hoodie", "Relaxed Siyah Pantolon", "Koyu Güneş Gözlüğü"),
        enPieces = listOf("Drop-Shoulder Black Hoodie", "Relaxed Black Pants", "Dark Sunglasses"),
        colorDots = listOf(Color(0xFF111214), Color(0xFF282A2E), Color(0xFF4C5058))
    ),
    StreetLookItem(
        id = "ys_8",
        imageUrl = "https://images.pexels.com/photos/4118956/pexels-photo-4118956.jpeg?auto=compress&cs=tinysrgb&w=900",
        category = InspirationCategory.LAYERING,
        harmonyPercent = 95,
        trTitle = "Haki / Kamuflaj Ceket & Beyaz Hoodie",
        enTitle = "Olive Camo Overshirt & White Hoodie",
        trPalette = "Zeytin Yeşili + Temiz Beyaz + Siyah",
        enPalette = "Olive Green + Crisp White + Black",
        trPieces = listOf("Haki Desenli Gömlek Ceket", "Beyaz Kapüşonlu Hoodie", "Siyah Pantolon & Skate Ayakkabı"),
        enPieces = listOf("Olive Patterned Overshirt", "Crisp White Hoodie", "Black Pants & Skate Sneakers"),
        colorDots = listOf(Color(0xFF566041), Color(0xFFF7F7F5), Color(0xFF1B1C1E))
    ),
    StreetLookItem(
        id = "ys_9",
        imageUrl = "https://images.pexels.com/photos/5493535/pexels-photo-5493535.jpeg?auto=compress&cs=tinysrgb&w=900",
        category = InspirationCategory.OVERSIZE,
        harmonyPercent = 87,
        trTitle = "Pastel Pembe Tişört, Şort & Bere",
        enTitle = "Pastel Pink Tee, Black Shorts & Beanie",
        trPalette = "Pudra Pembe + Siyah + Kum Toprak Sneaker",
        enPalette = "Pastel Pink + Black + Earth Sneakers",
        trPieces = listOf("Wash Pastel Pembe Oversize Tişört", "Siyah Sokak Şortu", "Beyaz Bere & Kahve-Krem Sneaker"),
        enPieces = listOf("Washed Pastel Pink Tee", "Black Street Shorts", "White Beanie & Earth Sneakers"),
        colorDots = listOf(Color(0xFFE5A4B8), Color(0xFF1A1B1E), Color(0xFF9C7B5C))
    ),
    StreetLookItem(
        id = "ys_10",
        imageUrl = "https://images.pexels.com/photos/5825331/pexels-photo-5825331.jpeg?auto=compress&cs=tinysrgb&w=900",
        category = InspirationCategory.TRACK_SPORT,
        harmonyPercent = 92,
        trTitle = "Antrasit Fermuarlı Üst & Jogger",
        enTitle = "Charcoal Zip Hoodie & Tapered Joggers",
        trPalette = "Koyu Antrasit + Gümüş Fermuar Detayı",
        enPalette = "Deep Charcoal + Metallic Zip Detail",
        trPieces = listOf("Antrasit Fermuarlı Sweatshirt", "Koyu Daralan Jogger", "Chunky Sokak Sneaker"),
        enPieces = listOf("Charcoal Zip Hoodie", "Tapered Dark Joggers", "Chunky Street Sneakers"),
        colorDots = listOf(Color(0xFF2D3035), Color(0xFF1A1C1F), Color(0xFFB7BCC4))
    ),
    StreetLookItem(
        id = "ys_11",
        imageUrl = "https://images.pexels.com/photos/8126169/pexels-photo-8126169.jpeg?auto=compress&cs=tinysrgb&w=900",
        category = InspirationCategory.DENIM_CARGO,
        harmonyPercent = 89,
        trTitle = "Turuncu, Ekru & Kargo Ekip Stili",
        enTitle = "Terracotta, Ecru & Cargo Crew",
        trPalette = "Sıcak Kiremit + Bej + Kargo Yeşil-Taş",
        enPalette = "Warm Terracotta + Beige + Cargo Tones",
        trPieces = listOf("Bol Kalıp Tişört (Turuncu / Ekru)", "Çok Cepli Kargo Pantolon", "Skate Sneaker"),
        enPieces = listOf("Relaxed Tee (Orange / Ecru)", "Multi-Pocket Cargo Pants", "Skate Sneakers"),
        colorDots = listOf(Color(0xFFD9622B), Color(0xFFE8DFD1), Color(0xFF4A483E))
    ),
    StreetLookItem(
        id = "ys_12",
        imageUrl = "https://images.pexels.com/photos/2229712/pexels-photo-2229712.jpeg?auto=compress&cs=tinysrgb&w=900",
        category = InspirationCategory.DENIM_CARGO,
        harmonyPercent = 93,
        trTitle = "Klasik Mavi Jean Ceket & Siyah Jean",
        enTitle = "Classic Blue Denim Jacket & Black Jeans",
        trPalette = "Orta Yıkama Denim + Siyah + Beyaz",
        enPalette = "Mid-Wash Denim + Black + White",
        trPieces = listOf("Klasik Mavi Denim Ceket", "Siyah Dar/Düz Jean", "Temiz Beyaz Sneaker"),
        enPieces = listOf("Classic Blue Denim Jacket", "Black Straight Jeans", "White Sneakers"),
        colorDots = listOf(Color(0xFF4E6F99), Color(0xFF18191B), Color(0xFFF3F4F6))
    ),
    StreetLookItem(
        id = "ys_13",
        imageUrl = "https://images.pexels.com/photos/6461703/pexels-photo-6461703.jpeg?auto=compress&cs=tinysrgb&w=900",
        category = InspirationCategory.LAYERING,
        harmonyPercent = 88,
        trTitle = "Petrol Yeşili Ceket, Gri Hoodie & Bordo Yaka",
        enTitle = "Teal Jacket, Grey Hoodie & Burgundy Collar",
        trPalette = "Petrol Yeşil + Bordo + Melanj Gri",
        enPalette = "Deep Teal + Burgundy + Heather Grey",
        trPieces = listOf("Petrol Yeşili Hafif Ceket", "Gri Kapüşonlu Katman", "Bordo Polo Alt Katman"),
        enPieces = listOf("Teal Light Jacket", "Grey Hoodie Layer", "Burgundy Under-Polo"),
        colorDots = listOf(Color(0xFF1B575A), Color(0xFF9EA2A8), Color(0xFF7B2838))
    ),
    StreetLookItem(
        id = "ys_18",
        imageUrl = "https://images.pexels.com/photos/12455995/pexels-photo-12455995.jpeg?auto=compress&cs=tinysrgb&w=900",
        category = InspirationCategory.LAYERING,
        harmonyPercent = 94,
        trTitle = "Siyah Bomber Ceket & Beyaz Tişört",
        enTitle = "Black Bomber Jacket & White Crew Tee",
        trPalette = "Siyah Bomber + Beyaz İç + Siyah Jean",
        enPalette = "Black Bomber + Crisp White Tee + Black Denim",
        trPieces = listOf("Siyah Bomber Ceket", "Ağır Gramaj Beyaz Tişört", "Siyah Slim/Straight Jean"),
        enPieces = listOf("Black Bomber Jacket", "Heavyweight White Tee", "Black Straight Jeans"),
        colorDots = listOf(Color(0xFF161719), Color(0xFFF8F8F6), Color(0xFF2D2E33))
    ),
    StreetLookItem(
        id = "ys_20",
        imageUrl = "https://images.pexels.com/photos/16069733/pexels-photo-16069733.jpeg?auto=compress&cs=tinysrgb&w=900",
        category = InspirationCategory.DENIM_CARGO,
        harmonyPercent = 88,
        trTitle = "Kırmızı Bomber & Açık Mavi Jean",
        enTitle = "Statement Red Bomber & Light Blue Jeans",
        trPalette = "Kırmızı Vurgu + Siyah İç + Buz Denim",
        enPalette = "Bold Red + Black Inner + Light Indigo",
        trPieces = listOf("Kırmızı Sokak Bomber Ceket", "Siyah Bisiklet Yaka Tişört", "Açık Yıkama Mavi Jean"),
        enPieces = listOf("Red Street Bomber Jacket", "Black Crewneck Tee", "Light-Wash Blue Jeans"),
        colorDots = listOf(Color(0xFFC42D2D), Color(0xFF17181A), Color(0xFF7A9BC4))
    ),
    StreetLookItem(
        id = "ys_21",
        imageUrl = "https://images.unsplash.com/photo-1742519902469-3a8a1bd54197?w=900&auto=format&fit=crop&q=80",
        category = InspirationCategory.SMART_STREET,
        harmonyPercent = 96,
        trTitle = "Siyah Blazer Altı Beyaz Hoodie",
        enTitle = "Relaxed Black Blazer Over White Hoodie",
        trPalette = "Smart Street: Siyah + Temiz Beyaz",
        enPalette = "Smart Street: Black Tailoring + White Hoodie",
        trPieces = listOf("Rahat Kesim Siyah Blazer", "Beyaz Hoodie", "Güneş Gözlüğü & Kumaş Pantolon"),
        enPieces = listOf("Relaxed Black Blazer", "White Hoodie", "Sunglasses & Tailored Trousers"),
        colorDots = listOf(Color(0xFF16171A), Color(0xFFF6F6F4), Color(0xFF4F535C))
    ),
    StreetLookItem(
        id = "ys_22",
        imageUrl = "https://images.pexels.com/photos/18078019/pexels-photo-18078019.jpeg?auto=compress&cs=tinysrgb&w=900",
        category = InspirationCategory.TRACK_SPORT,
        harmonyPercent = 91,
        trTitle = "Orman Yeşili Eşofman Takımı & Beyaz Tişört",
        enTitle = "Forest Green Tracksuit & Crisp White Tee",
        trPalette = "Koyu Orman Yeşili + Temiz Beyaz İç",
        enPalette = "Deep Forest Green + Crisp White Tee",
        trPieces = listOf("Yeşil Fermuarlı Track Top", "Beyaz İç Tişört", "Uyumlu Tracksuit Altı"),
        enPieces = listOf("Forest Green Track Jacket", "White Base Tee", "Matching Track Pants"),
        colorDots = listOf(Color(0xFF1F4232), Color(0xFFF5F5F2), Color(0xFF282B29))
    ),
    StreetLookItem(
        id = "ys_23",
        imageUrl = "https://images.unsplash.com/photo-1520975916090-3105956dac38?w=900&auto=format&fit=crop&q=80",
        category = InspirationCategory.LAYERING,
        harmonyPercent = 96,
        trTitle = "Siyah Deri Ceket & Beyaz Tişört",
        enTitle = "Black Leather Biker Jacket & White Tee",
        trPalette = "Tam Siyah + Temiz Beyaz Kontrast",
        enPalette = "Matte Black + Crisp White Contrast",
        trPieces = listOf("Siyah Biker Deri Ceket", "Basic Beyaz Tişört", "Siyah Slim Jean"),
        enPieces = listOf("Black Biker Leather Jacket", "Basic White Tee", "Black Slim Jeans"),
        colorDots = listOf(Color(0xFF141517), Color(0xFFF7F7F5), Color(0xFF33363C))
    ),
    StreetLookItem(
        id = "ys_24",
        imageUrl = "https://images.unsplash.com/photo-1558608846-c445477bab6e?w=900&auto=format&fit=crop&q=80",
        category = InspirationCategory.LAYERING,
        harmonyPercent = 91,
        trTitle = "Gri Örgü Hırka & Beyaz Şapka",
        enTitle = "Knit Charcoal Cardigan & White Cap",
        trPalette = "Melanj Gri + Antrasit + Beyaz",
        enPalette = "Heather Grey + Charcoal + Clean White",
        trPieces = listOf("Kalın Örgü Gri Hırka", "Açık Gri Tişört", "Beyaz Kep Şapka"),
        enPieces = listOf("Chunky Knit Grey Cardigan", "Light Grey Tee", "White Baseball Cap"),
        colorDots = listOf(Color(0xFF56595F), Color(0xFFC2C5CC), Color(0xFFF7F7F5))
    ),
    StreetLookItem(
        id = "ys_25",
        imageUrl = "https://images.unsplash.com/photo-1603550576850-71d04699948a?w=900&auto=format&fit=crop&q=80",
        category = InspirationCategory.SMART_STREET,
        harmonyPercent = 93,
        trTitle = "Kahve Yelek, Beyaz Gömlek & Koyu Gri",
        enTitle = "Espresso Waistcoat, White Shirt & Dark Grey",
        trPalette = "Koyu Kahve + Beyaz + Antrasit",
        enPalette = "Deep Espresso + Crisp White + Charcoal",
        trPieces = listOf("Kahverengi Yelek", "Düğmeli Beyaz Gömlek", "Koyu Gri Pantolon & Kasket"),
        enPieces = listOf("Espresso Vest", "Crisp White Shirt", "Dark Grey Pants & Flat Cap"),
        colorDots = listOf(Color(0xFF3D281E), Color(0xFFF6F6F4), Color(0xFF3B4046))
    ),
    StreetLookItem(
        id = "ys_26",
        imageUrl = "https://images.unsplash.com/photo-1516826957135-700dedea698c?w=900&auto=format&fit=crop&q=80",
        category = InspirationCategory.DENIM_CARGO,
        harmonyPercent = 92,
        trTitle = "Pudra Sweatshirt & Buz Mavi Jean",
        enTitle = "Pastel Blush Sweatshirt & Distressed Denim",
        trPalette = "Pudra + Açık Yıkama İndigo + Siyah Bere",
        enPalette = "Pastel Blush + Light Wash Denim + Black Beanie",
        trPieces = listOf("Pudra Crewneck Sweatshirt", "Yırtık Buz Mavi Jean", "Pudra Sneaker & Siyah Bere"),
        enPieces = listOf("Blush Crewneck Sweatshirt", "Distressed Light Jeans", "Blush Sneakers & Black Beanie"),
        colorDots = listOf(Color(0xFFEBD2D6), Color(0xFF6B8FB5), Color(0xFF191A1E))
    ),
    StreetLookItem(
        id = "ys_27",
        imageUrl = "https://images.unsplash.com/photo-1649548110943-c98a9a36eb93?w=900&auto=format&fit=crop&q=80",
        category = InspirationCategory.OVERSIZE,
        harmonyPercent = 94,
        trTitle = "İki Tonlu Siyah & Gri Blok Hoodie",
        enTitle = "Two-Tone Black & Grey Colorblock Hoodie",
        trPalette = "Mat Siyah + Füme Gri Blok Uyumu",
        enPalette = "Matte Black + Slate Grey Colorblock",
        trPieces = listOf("Çift Renk Raglan Hoodie", "Koyu Sokak Pantolonu", "Minimal Sneaker"),
        enPieces = listOf("Two-Tone Raglan Hoodie", "Dark Street Pants", "Clean Sneakers"),
        colorDots = listOf(Color(0xFF1E1E1F), Color(0xFF6B7075), Color(0xFFD5D8DC))
    ),
    StreetLookItem(
        id = "ys_28",
        imageUrl = "https://images.unsplash.com/photo-1598798918315-e954298ef4cb?w=900&auto=format&fit=crop&q=80",
        category = InspirationCategory.DENIM_CARGO,
        harmonyPercent = 90,
        trTitle = "Desenli Sokak Ceket & Haki Jogger",
        enTitle = "Camo Hooded Jacket & Olive Joggers",
        trPalette = "Zeytin Yeşili + Kırık Beyaz + Mint Detay",
        enPalette = "Olive Green + Off-White + Mint Accent",
        trPieces = listOf("Kapüşonlu Desenli Ceket", "Baskılı Beyaz Tişört", "Haki Kargo Jogger & Sneaker"),
        enPieces = listOf("Camo Hooded Zip Jacket", "Graphic White Tee", "Olive Joggers & Light Sneakers"),
        colorDots = listOf(Color(0xFF4B543B), Color(0xFFF1EFE9), Color(0xFFA7D3C5))
    ),
    StreetLookItem(
        id = "ys_29",
        imageUrl = "https://images.unsplash.com/photo-1574789696557-24336404180f?w=900&auto=format&fit=crop&q=80",
        category = InspirationCategory.DENIM_CARGO,
        harmonyPercent = 93,
        trTitle = "Klasik Mavi Kot Ceket & Siyah İç",
        enTitle = "Blue Denim Jacket Over Black Graphic Tee",
        trPalette = "İndigo Denim + Tam Siyah",
        enPalette = "Mid Indigo Denim + Solid Black",
        trPieces = listOf("Mavi Kot Ceket", "Siyah Baskılı Tişört", "Siyah Slim/Straight Jean"),
        enPieces = listOf("Blue Denim Jacket", "Black Graphic Tee", "Black Jeans"),
        colorDots = listOf(Color(0xFF476C9B), Color(0xFF16171A), Color(0xFFECEEF2))
    ),
    StreetLookItem(
        id = "ys_30",
        imageUrl = "https://images.unsplash.com/photo-1652823780977-b22c0ed84c97?w=900&auto=format&fit=crop&q=80",
        category = InspirationCategory.TRACK_SPORT,
        harmonyPercent = 92,
        trTitle = "Oversize Siyah Hoodie, Şort & Koşu Sneaker",
        enTitle = "Oversize Black Hoodie, Shorts & Runners",
        trPalette = "Siyah Monokrom + Neon Sarı Taban Vurgusu",
        enPalette = "Black Monochrome + Volt Runner Highlight",
        trPieces = listOf("Drop-Shoulder Siyah Hoodie", "Siyah Spor Şort", "Beyaz-Neon Koşu Sneaker"),
        enPieces = listOf("Drop-Shoulder Black Hoodie", "Black Athletic Shorts", "White-Volt Runners"),
        colorDots = listOf(Color(0xFF141517), Color(0xFFF4F4F2), Color(0xFFC8E635))
    ),
    StreetLookItem(
        id = "ys_31",
        imageUrl = "https://images.unsplash.com/photo-1542327534-59a1fe8daf73?w=900&auto=format&fit=crop&q=80",
        category = InspirationCategory.OVERSIZE,
        harmonyPercent = 89,
        trTitle = "Kırmızı Statement Hoodie & Denim",
        enTitle = "Bold Red Hoodie & Vintage Wash Denim",
        trPalette = "Canlı Kırmızı + İndigo Jean + Siyah Gözlük",
        enPalette = "Vibrant Red + Indigo Jeans + Dark Shades",
        trPieces = listOf("Oversize Kırmızı Hoodie", "Mavi Vintage Jean", "Siyah Sokak Gözlüğü"),
        enPieces = listOf("Oversize Red Hoodie", "Blue Vintage Jeans", "Black Street Shades"),
        colorDots = listOf(Color(0xFFC8282D), Color(0xFF4A6E94), Color(0xFF19191B))
    ),
    StreetLookItem(
        id = "ys_32",
        imageUrl = "https://images.unsplash.com/photo-1773650783063-a599451b86ce?w=900&auto=format&fit=crop&q=80",
        category = InspirationCategory.LAYERING,
        harmonyPercent = 95,
        trTitle = "Buz Grisi Denim Ceket & Gri Hoodie",
        enTitle = "Ice Grey Denim Jacket & Grey Hoodie",
        trPalette = "Soğuk Buz Grisi + Antrasit Alt",
        enPalette = "Cool Ice Grey + Dark Charcoal Bottoms",
        trPieces = listOf("Açık Gri Kot Ceket", "Melanj Gri Kapüşonlu", "Siyah Bol Pantolon"),
        enPieces = listOf("Ice Grey Denim Jacket", "Heather Grey Hoodie", "Relaxed Black Trousers"),
        colorDots = listOf(Color(0xFFB5BEC4), Color(0xFF6E757C), Color(0xFF1D1F22))
    ),
    StreetLookItem(
        id = "ys_33",
        imageUrl = "https://images.pexels.com/photos/5840463/pexels-photo-5840463.jpeg?auto=compress&cs=tinysrgb&w=900",
        category = InspirationCategory.OVERSIZE,
        harmonyPercent = 95,
        trTitle = "Kum Beji & Kırık Beyaz Fermuarlı Hoodie",
        enTitle = "Sand Beige & Off-White Relaxed Hoodies",
        trPalette = "Nötr Kum Beji + Ekru + Haki/Siyah Alt",
        enPalette = "Neutral Sand Beige + Ecru + Olive/Black",
        trPieces = listOf("Rahat Kesim Bej / Ekru Hoodie", "Bol Kesim Kargo veya Kumaş Pantolon", "Minimal Sokak Stili"),
        enPieces = listOf("Relaxed Beige / Ecru Hoodie", "Wide Olive or Black Trousers", "Minimal Street Fit"),
        colorDots = listOf(Color(0xFFD8C6B4), Color(0xFFF3F1EC), Color(0xFF43483F))
    ),
    StreetLookItem(
        id = "ys_34",
        imageUrl = "https://images.unsplash.com/photo-1564557287817-3785e38ec1f5?w=900&auto=format&fit=crop&q=80",
        category = InspirationCategory.OVERSIZE,
        harmonyPercent = 93,
        trTitle = "Taş Grisi Heavyweight Hoodie & Relaxed Jean",
        enTitle = "Stone Grey Heavyweight Hoodie & Relaxed Jeans",
        trPalette = "Taş Grisi + Açık İndigo Denim",
        enPalette = "Stone Heather Grey + Light Indigo Denim",
        trPieces = listOf("500gsm Taş Grisi Boxy Hoodie", "Açık Mavi Relaxed Jean", "Beyaz Retro Sneaker"),
        enPieces = listOf("Heavyweight Stone Hoodie", "Light Wash Relaxed Jeans", "Clean Retro Sneakers"),
        colorDots = listOf(Color(0xFF8C8F94), Color(0xFF6283AA), Color(0xFFF4F4F2))
    ),
    StreetLookItem(
        id = "ys_35",
        imageUrl = "https://images.unsplash.com/photo-1615397587950-3cbb55f95b77?w=900&auto=format&fit=crop&q=80",
        category = InspirationCategory.OVERSIZE,
        harmonyPercent = 92,
        trTitle = "Kırık Beyaz Grafik Hoodie & Siyah Street Alt",
        enTitle = "Off-White Graphic Hoodie & Black Street Pants",
        trPalette = "Ekru Beyaz + Kömür Siyah Kontrast",
        enPalette = "Off-White + Charcoal Contrast",
        trPieces = listOf("Oversize Tipografik Ekru Hoodie", "Bol Siyah Paraşüt Pantolon", "Chunky Sneaker"),
        enPieces = listOf("Oversize Graphic Ecru Hoodie", "Baggy Black Parachute Pants", "Chunky Sneakers"),
        colorDots = listOf(Color(0xFFF2EFE9), Color(0xFF1A1B1E), Color(0xFF6B6F76))
    ),
    StreetLookItem(
        id = "ys_36",
        imageUrl = "https://images.unsplash.com/photo-1627637454030-5ddd536e06e5?w=900&auto=format&fit=crop&q=80",
        category = InspirationCategory.LAYERING,
        harmonyPercent = 96,
        trTitle = "Çikolata Kahve Kürklü Deri Aviator & Siyah Takım",
        enTitle = "Chocolate Shearling Leather Jacket & Black Base",
        trPalette = "Acı Kahve + Ekru Shearling + Tam Siyah",
        enPalette = "Espresso Brown + Ecru Shearling + All Black",
        trPieces = listOf("Çikolata Kahve Aviator Deri Ceket", "Siyah Bisiklet Yaka Tişört", "Siyah Slim/Straight Pantolon"),
        enPieces = listOf("Chocolate Leather Shearling Jacket", "Black Crewneck Tee", "Black Straight Trousers"),
        colorDots = listOf(Color(0xFF4B2E1E), Color(0xFFEDE4D5), Color(0xFF141517))
    ),
    StreetLookItem(
        id = "ys_37",
        imageUrl = "https://images.unsplash.com/photo-1532332248682-206cc786359f?w=900&auto=format&fit=crop&q=80",
        category = InspirationCategory.LAYERING,
        harmonyPercent = 94,
        trTitle = "Oversize Siyah Shearling Ceket & Beyaz Jean",
        enTitle = "Oversized Black Shearling Jacket & White Jeans",
        trPalette = "Mat Siyah + Optik Beyaz Monokrom",
        enPalette = "Matte Black + Crisp White Contrast",
        trPieces = listOf("Oversize Siyah Biker Deri Ceket", "Beyaz Baskılı Tişört", "Ekru/Beyaz Straight Jean"),
        enPieces = listOf("Oversize Black Shearling Biker", "White Graphic Tee", "White Straight Denim"),
        colorDots = listOf(Color(0xFF151619), Color(0xFFF4F3EF), Color(0xFF7B7F85))
    ),
    StreetLookItem(
        id = "ys_38",
        imageUrl = "https://images.unsplash.com/photo-1620228922597-cca58f177310?w=900&auto=format&fit=crop&q=80",
        category = InspirationCategory.LAYERING,
        harmonyPercent = 93,
        trTitle = "Camel Fermuarlı Blouson Ceket & Beyaz Tişört",
        enTitle = "Camel Zip Blouson Jacket & Crisp White Tee",
        trPalette = "Sıcak Camel + Saf Beyaz + Antrasit",
        enPalette = "Warm Camel + Crisp White + Charcoal",
        trPieces = listOf("Taba/Camel Fermuarlı Ceket", "Beyaz Heavyweight Tişört", "Koyu Antrasit Pantolon"),
        enPieces = listOf("Camel Zip Blouson", "Crisp White Crew Tee", "Charcoal Tailored Pants"),
        colorDots = listOf(Color(0xFFC19363), Color(0xFFF6F6F3), Color(0xFF2E3136))
    ),
    StreetLookItem(
        id = "ys_39",
        imageUrl = "https://images.unsplash.com/photo-1660486044177-45cd45bb5e99?w=900&auto=format&fit=crop&q=80",
        category = InspirationCategory.DENIM_CARGO,
        harmonyPercent = 92,
        trTitle = "Bucket Şapka, Sırt Baskılı Tişört & Siyah Kargo",
        enTitle = "Bucket Hat, Back-Print Oversize Tee & Black Cargo",
        trPalette = "Tam Siyah + Kum/Beyaz Baskı Vurgusu",
        enPalette = "Stealth Black + Off-White Back Print",
        trPieces = listOf("Kanvas Bucket Şapka", "Sırt Baskılı Oversize Tişört", "Çok Cepli Bol Siyah Kargo"),
        enPieces = listOf("Black Bucket Hat", "Back-Print Boxy Tee", "Baggy Black Cargo Pants"),
        colorDots = listOf(Color(0xFF151618), Color(0xFFE7E2D8), Color(0xFF3A3C41))
    ),
    StreetLookItem(
        id = "ys_40",
        imageUrl = "https://images.unsplash.com/photo-1577471444692-4e7c23b51ce4?w=900&auto=format&fit=crop&q=80",
        category = InspirationCategory.DENIM_CARGO,
        harmonyPercent = 91,
        trTitle = "Siyah Şişme Yelek, Uzun Kol & Bol Kargo",
        enTitle = "Black Puffer Vest, Layered Sleeve & Street Cargo",
        trPalette = "Mat Siyah + Antrasit + Gümüş Gözlük",
        enPalette = "Matte Black + Charcoal + Silver Accents",
        trPieces = listOf("Siyah Şişme Puffer Yelek", "İç Katman Uzun Kollu Üst", "Relaxed Kargo Pantolon & Kep"),
        enPieces = listOf("Black Puffer Gilet", "Long-Sleeve Base Layer", "Wide Cargo Pants & Cap"),
        colorDots = listOf(Color(0xFF141518), Color(0xFF33363C), Color(0xFF9EA4AD))
    ),
    StreetLookItem(
        id = "ys_41",
        imageUrl = "https://images.unsplash.com/photo-1516257984-b1b4d707412e?w=900&auto=format&fit=crop&q=80",
        category = InspirationCategory.DENIM_CARGO,
        harmonyPercent = 90,
        trTitle = "Açık Mavi Trucker Kot Ceket & Desenli Gömlek",
        enTitle = "Light Blue Trucker Denim Jacket & Resort Shirt",
        trPalette = "Buz Mavisi Denim + Sıcak Desen",
        enPalette = "Light Wash Denim + Printed Layer",
        trPieces = listOf("Açık Yıkamalı Denim Ceket", "Desenli Kısa Kollu Gömlek", "Siyah veya Açık Mavi Jean"),
        enPieces = listOf("Light Indigo Trucker Jacket", "Patterned Open Shirt", "Relaxed Denim Jeans"),
        colorDots = listOf(Color(0xFF6B93BA), Color(0xFFF3EFE8), Color(0xFF1E2228))
    ),
    StreetLookItem(
        id = "ys_42",
        imageUrl = "https://images.unsplash.com/photo-1541102396743-74c128e1133e?w=900&auto=format&fit=crop&q=80",
        category = InspirationCategory.TRACK_SPORT,
        harmonyPercent = 94,
        trTitle = "Retro Colorblock Track Ceket & Şeritli Eşofman",
        enTitle = "Colorblock Track Jacket, Striped Pants & Sneakers",
        trPalette = "Siyah + Kırmızı Panel + Haki Sneaker",
        enPalette = "Black + Crimson Block + Olive Sneakers",
        trPieces = listOf("Siyah-Kırmızı-Beyaz Track Ceket", "Yan Şeritli Eşofman Altı", "Haki Retro Runner"),
        enPieces = listOf("Colorblock Zip Track Top", "Striped Track Trousers", "Olive Retro Runners"),
        colorDots = listOf(Color(0xFF17181B), Color(0xFFC62E32), Color(0xFF5D6648))
    ),
    StreetLookItem(
        id = "ys_43",
        imageUrl = "https://images.unsplash.com/photo-1552904219-f4b87efe8792?w=900&auto=format&fit=crop&q=80",
        category = InspirationCategory.TRACK_SPORT,
        harmonyPercent = 93,
        trTitle = "Beyaz-Siyah Utility Track Takım & Beyaz Kep",
        enTitle = "White Utility Track Jacket, Black Bottoms & Cap",
        trPalette = "Optik Beyaz + Mat Siyah Blok",
        enPalette = "Crisp White + Matte Black Block",
        trPieces = listOf("Kontrast Fermuarlı Track Ceket", "Siyah Geniş Eşofman Altı", "Minimal Beyaz Kep"),
        enPieces = listOf("Two-Tone Utility Track Top", "Relaxed Black Track Pants", "Crisp White Cap"),
        colorDots = listOf(Color(0xFFF5F5F2), Color(0xFF17181A), Color(0xFF83878E))
    ),
    StreetLookItem(
        id = "ys_44",
        imageUrl = "https://images.unsplash.com/photo-1632053219083-02539aeb98a3?w=900&auto=format&fit=crop&q=80",
        category = InspirationCategory.TRACK_SPORT,
        harmonyPercent = 92,
        trTitle = "Tam Siyah Fermuarlı Modern Eşofman Takımı",
        enTitle = "Stealth All-Black Zip Tracksuit & White Kicks",
        trPalette = "Tam Siyah + Temiz Beyaz Taban",
        enPalette = "Triple Black + Crisp White Sole",
        trPieces = listOf("Dik Yaka Siyah Track Üst", "Siyah Jogger / Track Alt", "Beyaz Low-Top Sneaker"),
        enPieces = listOf("Funnel-Neck Black Track Top", "Matching Black Track Pants", "White Low Sneakers"),
        colorDots = listOf(Color(0xFF131416), Color(0xFF2B2D32), Color(0xFFF4F4F1))
    ),
    StreetLookItem(
        id = "ys_45",
        imageUrl = "https://images.unsplash.com/photo-1715536369811-8052788a3f90?w=900&auto=format&fit=crop&q=80",
        category = InspirationCategory.SMART_STREET,
        harmonyPercent = 96,
        trTitle = "Zeytin Yeşili Uzun Trençkot & Tam Siyah Katman",
        enTitle = "Olive Long Trench Coat Over Sleek Black Base",
        trPalette = "Zeytin Yeşili + Siyah + Gümüş Kolye",
        enPalette = "Olive Moss + Black + Silver Chain",
        trPieces = listOf("Uzun Kesim Zeytin Trençkot", "Siyah Bisiklet Yaka Tişört", "Düz Siyah Kumaş Pantolon"),
        enPieces = listOf("Long Olive Trench Coat", "Fitted Black Tee", "Tailored Black Trousers"),
        colorDots = listOf(Color(0xFF4F5C41), Color(0xFF151618), Color(0xFF9EA5AF))
    ),
    StreetLookItem(
        id = "ys_46",
        imageUrl = "https://images.unsplash.com/photo-1630173250799-2813d34ed14b?w=900&auto=format&fit=crop&q=80",
        category = InspirationCategory.SMART_STREET,
        harmonyPercent = 95,
        trTitle = "Antrasit Blazer Ceket, Beyaz Tişört & Siyah Jean",
        enTitle = "Charcoal Slim Blazer, White Tee & Black Denim",
        trPalette = "Füme Antrasit + Beyaz + Siyah Denim",
        enPalette = "Dark Charcoal + Optic White + Black Denim",
        trPieces = listOf("Yapılı Antrasit Blazer Ceket", "Temiz Beyaz Tişört", "Siyah Straight Jean & Saat"),
        enPieces = listOf("Structured Charcoal Blazer", "Clean White Crew Tee", "Black Straight Jeans & Watch"),
        colorDots = listOf(Color(0xFF2F3238), Color(0xFFF6F6F3), Color(0xFF17181B))
    )
)

private val VERIFIED_WOMEN_STREET_LOOKS = listOf(
    StreetLookItem(
        id = "ys_w1",
        imageUrl = "https://images.unsplash.com/photo-1633292587737-f898a032e562?w=900&auto=format&fit=crop&q=80",
        category = InspirationCategory.OVERSIZE,
        harmonyPercent = 95,
        trTitle = "Oversize Siyah Sırt Baskılı Hoodie",
        enTitle = "Oversized Black Back-Print Hoodie",
        trPalette = "Mat Siyah + Beyaz Tipografi Vurgusu",
        enPalette = "Matte Black + Crisp White Typography",
        trPieces = listOf("Sırt Baskılı Oversize Siyah Hoodie", "Siyah Geniş Paça Pantolon", "Minimal Sneaker"),
        enPieces = listOf("Back-Graphic Oversized Black Hoodie", "Wide Black Pants", "Minimal Sneakers"),
        colorDots = listOf(Color(0xFF151618), Color(0xFFF5F5F3), Color(0xFF4B4E54))
    ),
    StreetLookItem(
        id = "ys_w2",
        imageUrl = "https://images.unsplash.com/photo-1633292750937-120a94f5c2bb?w=900&auto=format&fit=crop&q=80",
        category = InspirationCategory.OVERSIZE,
        harmonyPercent = 94,
        trTitle = "Minimal Baskılı Kapüşonlu Siyah Hoodie",
        enTitle = "Chest-Logo Black Street Hoodie",
        trPalette = "Tam Siyah + Küçük Beyaz Baskı",
        enPalette = "Solid Black + Minimal White Print",
        trPieces = listOf("Bol Kesim Siyah Hoodie", "Relaxed Jean veya Eşofman Altı", "Sokak Sneaker"),
        enPieces = listOf("Relaxed Black Hoodie", "Baggy Jeans or Track Pants", "Street Sneakers"),
        colorDots = listOf(Color(0xFF141416), Color(0xFFF3F3F1), Color(0xFF393C42))
    ),
    StreetLookItem(
        id = "ys_w3",
        imageUrl = "https://images.unsplash.com/photo-1594656375376-64d56e2f3ed1?w=900&auto=format&fit=crop&q=80",
        category = InspirationCategory.OVERSIZE,
        harmonyPercent = 93,
        trTitle = "Melanj Gri Kanguru Cepli Hoodie",
        enTitle = "Heather Grey Oversized Kangaroo Hoodie",
        trPalette = "Melanj Gri Monokrom + Yumuşak Nötr",
        enPalette = "Heather Grey Monochrome + Soft Neutrals",
        trPieces = listOf("Gri Oversize Hoodie", "Rahat Kalıp Denim veya Jogger", "Beyaz Günlük Sneaker"),
        enPieces = listOf("Heather Grey Oversized Hoodie", "Relaxed Denim or Joggers", "White Everyday Sneakers"),
        colorDots = listOf(Color(0xFF9EA2A8), Color(0xFF5D6168), Color(0xFFF4F4F2))
    ),
    StreetLookItem(
        id = "ys_w4",
        imageUrl = "https://images.unsplash.com/photo-1578632140472-6d913e068456?w=900&auto=format&fit=crop&q=80",
        category = InspirationCategory.OVERSIZE,
        harmonyPercent = 91,
        trTitle = "Beyaz Grafik Hoodie · Gece Sokak Stili",
        enTitle = "White Graphic Hoodie · Night Street Look",
        trPalette = "Kırık Beyaz + Kırmızı-Siyah Baskı Kontrastı",
        enPalette = "Off-White + Red & Black Graphic Contrast",
        trPieces = listOf("Beyaz Grafik Baskılı Hoodie", "Koyu Bol Paça Pantolon", "Minimal Takı & Sneaker"),
        enPieces = listOf("White Graphic Pullover Hoodie", "Dark Wide-Leg Pants", "Minimal Jewelry & Sneakers"),
        colorDots = listOf(Color(0xFFF6F5F2), Color(0xFFB8322E), Color(0xFF18191C))
    ),
    StreetLookItem(
        id = "ys_w5",
        imageUrl = "https://images.unsplash.com/photo-1601396344868-31e008cd6e7c?w=900&auto=format&fit=crop&q=80",
        category = InspirationCategory.OVERSIZE,
        harmonyPercent = 92,
        trTitle = "Pastel Lila-Beyaz Günlük Hoodie",
        enTitle = "Pale Lilac-White Sunlit Street Hoodie",
        trPalette = "Açık Pastel Lila + Beyaz + Açık Denim",
        enPalette = "Pale Lilac + Crisp White + Light Denim",
        trPieces = listOf("Pastel İnce Baskılı Hoodie", "Açık Mavi Straight Jean", "Krem Sneaker"),
        enPieces = listOf("Pastel Subtle-Text Hoodie", "Light Blue Straight Jeans", "Cream Sneakers"),
        colorDots = listOf(Color(0xFFE7E2F0), Color(0xFFF7F7F5), Color(0xFF7B9BC2))
    ),
    StreetLookItem(
        id = "ys_w6",
        imageUrl = "https://images.unsplash.com/photo-1721111259873-5a13f7fcd67b?w=900&auto=format&fit=crop&q=80",
        category = InspirationCategory.OVERSIZE,
        harmonyPercent = 90,
        trTitle = "Bordo Oversize Hoodie & Tipografi",
        enTitle = "Burgundy Oversized Back-Type Hoodie",
        trPalette = "Derin Bordo + Beyaz Detay + Siyah",
        enPalette = "Deep Burgundy + White Type + Black",
        trPieces = listOf("Bordo Heavyweight Hoodie", "Siyah Geniş Kesim Pantolon", "Siyah-Beyaz Sneaker"),
        enPieces = listOf("Burgundy Heavyweight Hoodie", "Black Relaxed Pants", "B&W Sneakers"),
        colorDots = listOf(Color(0xFF73232E), Color(0xFFF6F6F4), Color(0xFF1A1B1E))
    ),
    StreetLookItem(
        id = "ys_w7",
        imageUrl = "https://images.unsplash.com/photo-1552699344-8718b46cb5ed?w=900&auto=format&fit=crop&q=80",
        category = InspirationCategory.LAYERING,
        harmonyPercent = 95,
        trTitle = "Siyah Deri Moto Ceket & Boğazlı Kazak",
        enTitle = "Black Leather Moto Jacket & Knit Turtleneck",
        trPalette = "Mat Siyah Deri + Çiçek Desen + Siyah Çorap",
        enPalette = "Matte Black Leather + Floral Accent + Tights",
        trPieces = listOf("Siyah Moto Deri Ceket", "Siyah Boğazlı Triko & Mini Etek", "Siyah Bilek Bot"),
        enPieces = listOf("Black Leather Moto Jacket", "Black Turtleneck & Mini Skirt", "Black Ankle Boots"),
        colorDots = listOf(Color(0xFF161719), Color(0xFF42454B), Color(0xFFB57B84))
    ),
    StreetLookItem(
        id = "ys_w8",
        imageUrl = "https://images.unsplash.com/photo-1562751361-8839562e351b?w=900&auto=format&fit=crop&q=80",
        category = InspirationCategory.LAYERING,
        harmonyPercent = 96,
        trTitle = "Siyah Deri Ceket & Yüksek Bel Camel Pantolon",
        enTitle = "Black Leather Jacket & High-Waisted Camel Trousers",
        trPalette = "Siyah Deri + Sıcak Devetüyü Bej",
        enPalette = "Black Leather + Warm Camel Beige",
        trPieces = listOf("Siyah Deri Ceket", "Siyah İç Üst", "Yüksek Bel Camel Kumaş Pantolon"),
        enPieces = listOf("Black Leather Jacket", "Black Fitted Top", "High-Waisted Camel Trousers"),
        colorDots = listOf(Color(0xFF18181A), Color(0xFFC29B6F), Color(0xFF4F463D))
    ),
    StreetLookItem(
        id = "ys_w9",
        imageUrl = "https://images.unsplash.com/photo-1786053252246-8b92029090a3?w=900&auto=format&fit=crop&q=80",
        category = InspirationCategory.LAYERING,
        harmonyPercent = 94,
        trTitle = "Oversize Deri Ceket & Beyaz Mini Etek",
        enTitle = "Oversized Black Leather Jacket & White Mini",
        trPalette = "Siyah Deri + Temiz Beyaz Kontrastı",
        enPalette = "Black Leather + Crisp White Contrast",
        trPieces = listOf("Oversize Siyah Deri Ceket", "Siyah Crop Top", "Beyaz Mini Etek & Çizme/Sneaker"),
        enPieces = listOf("Oversized Black Leather Jacket", "Black Crop Top", "White Mini Skirt & Boots"),
        colorDots = listOf(Color(0xFF141517), Color(0xFFF6F6F4), Color(0xFF434750))
    ),
    StreetLookItem(
        id = "ys_w10",
        imageUrl = "https://images.unsplash.com/photo-1772449099726-77046f3030b0?w=900&auto=format&fit=crop&q=80",
        category = InspirationCategory.LAYERING,
        harmonyPercent = 91,
        trTitle = "Deri Ceket, Antrasit Crop & Mavi Çanta",
        enTitle = "Black Leather Jacket, Charcoal Crop & Blue Bag",
        trPalette = "Siyah + Füme Gri + Kobalt Mavi Aksesuar",
        enPalette = "Black + Charcoal Grey + Blue Shoulder Bag",
        trPieces = listOf("Siyah Deri Ceket", "Koyu Gri Crop Üst", "Mavi Jean, Şapka & Omuz Çantası"),
        enPieces = listOf("Black Leather Jacket", "Dark Grey Crop Top", "Belted Blue Jeans & Shoulder Bag"),
        colorDots = listOf(Color(0xFF17181B), Color(0xFF565B63), Color(0xFF436A9E))
    ),
    StreetLookItem(
        id = "ys_w11",
        imageUrl = "https://images.unsplash.com/photo-1786052906809-86832c52d2d0?w=900&auto=format&fit=crop&q=80",
        category = InspirationCategory.LAYERING,
        harmonyPercent = 94,
        trTitle = "Deri Ceket & Rahat Kesim Mavi Jean",
        enTitle = "Black Leather Jacket & Relaxed Blue Jeans",
        trPalette = "Tam Siyah Deri + Orta Yıkama İndigo",
        enPalette = "Black Leather + Classic Mid-Wash Indigo",
        trPieces = listOf("Siyah Deri Ceket", "Siyah İç Bluz", "Rahat Kalıp Mavi Denim Pantolon"),
        enPieces = listOf("Black Leather Jacket", "Dark Inner Top", "Relaxed Medium-Wash Jeans"),
        colorDots = listOf(Color(0xFF161719), Color(0xFF4A6E9B), Color(0xFFD9DCE2))
    ),
    StreetLookItem(
        id = "ys_w12",
        imageUrl = "https://images.unsplash.com/photo-1782462238852-ac00d3403c32?w=900&auto=format&fit=crop&q=80",
        category = InspirationCategory.LAYERING,
        harmonyPercent = 95,
        trTitle = "Kısa Biker Deri Ceket & Monokrom Siyah",
        enTitle = "Cropped Black Biker Jacket & Dark Monochrome",
        trPalette = "Kromatik Siyah + Koyu Güneş Gözlüğü",
        enPalette = "Sleek All-Black + Dark Sunglasses",
        trPieces = listOf("Cropped Biker Deri Ceket", "Koyu Boğazlı Kazak", "Siyah Jean & Gözlük"),
        enPieces = listOf("Cropped Biker Leather Jacket", "Dark Turtleneck", "Black Jeans & Shades"),
        colorDots = listOf(Color(0xFF121315), Color(0xFF2D3035), Color(0xFF6B7078))
    ),
    StreetLookItem(
        id = "ys_w13",
        imageUrl = "https://images.unsplash.com/photo-1709542441696-e6f05e31ad12?w=900&auto=format&fit=crop&q=80",
        category = InspirationCategory.DENIM_CARGO,
        harmonyPercent = 92,
        trTitle = "Açık Yıkama Cropped Kot Ceket & Siyah Alt",
        enTitle = "Cropped Light Denim Jacket & Dark Pants",
        trPalette = "Buz Mavisi Denim + Acı Kahve + Siyah",
        enPalette = "Ice Blue Denim + Dark Brown + Black",
        trPieces = listOf("Açık Yıkama Yıpranmış Kot Ceket", "Koyu Kahve İç Üst", "Siyah Bol Pantolon"),
        enPieces = listOf("Cropped Distressed Denim Jacket", "Espresso Top", "Black Relaxed Pants"),
        colorDots = listOf(Color(0xFF7B9DC2), Color(0xFF47332A), Color(0xFF18191B))
    ),
    StreetLookItem(
        id = "ys_w14",
        imageUrl = "https://images.unsplash.com/photo-1774720434656-6177f599d7a9?w=900&auto=format&fit=crop&q=80",
        category = InspirationCategory.DENIM_CARGO,
        harmonyPercent = 93,
        trTitle = "Koyu Denim Ceket & Yüksek Bel Yırtık Jean",
        enTitle = "Dark Denim Jacket & High-Waisted Light Jeans",
        trPalette = "Double Denim: Koyu İndigo + Açık İndigo + Siyah",
        enPalette = "Double Denim: Dark Indigo + Light Wash + Black",
        trPieces = listOf("Koyu Yıkama Kot Ceket", "Baskılı Siyah Tişört", "Yüksek Bel Açık Yıkama Jean"),
        enPieces = listOf("Dark Denim Jacket", "Black Graphic Tee", "High-Waisted Light Jeans"),
        colorDots = listOf(Color(0xFF263B59), Color(0xFF17181A), Color(0xFF88A9CC))
    ),
    StreetLookItem(
        id = "ys_w15",
        imageUrl = "https://images.unsplash.com/photo-1533642128742-a542f568eb53?w=900&auto=format&fit=crop&q=80",
        category = InspirationCategory.DENIM_CARGO,
        harmonyPercent = 94,
        trTitle = "Orta Yıkama Kot Ceket & Bağlamalı Beyaz Tişört",
        enTitle = "Mid-Wash Denim Jacket & Knotted White Tee",
        trPalette = "Klasik Mavi Denim + Temiz Beyaz",
        enPalette = "Classic Blue Denim + Crisp White",
        trPieces = listOf("Klasik Mavi Denim Ceket", "Beyaz Crop/Bağlamalı Tişört", "Mavi Jean"),
        enPieces = listOf("Mid-Wash Denim Jacket", "Knotted White Crop Tee", "Blue Jeans"),
        colorDots = listOf(Color(0xFF4C72A1), Color(0xFFF7F7F5), Color(0xFF344B6E))
    ),
    StreetLookItem(
        id = "ys_w16",
        imageUrl = "https://images.unsplash.com/photo-1752618593064-0840b2dcb368?w=900&auto=format&fit=crop&q=80",
        category = InspirationCategory.DENIM_CARGO,
        harmonyPercent = 95,
        trTitle = "Kısa Denim Ceket, Beyaz Atlet & Takım Jean",
        enTitle = "Cropped Denim Jacket, White Rib Tank & Jeans",
        trPalette = "Mavi Denim Takım + Beyaz Fitilli Atlet",
        enPalette = "Coordinated Blue Denim + White Ribbed Tank",
        trPieces = listOf("Kısa Mavi Kot Ceket", "Beyaz Fitilli Atlet", "Aynı Ton Mavi Jean"),
        enPieces = listOf("Cropped Denim Jacket", "White Ribbed Tank Top", "Matching Blue Jeans"),
        colorDots = listOf(Color(0xFF4D719E), Color(0xFFF6F6F4), Color(0xFF2C3B52))
    ),
    StreetLookItem(
        id = "ys_w17",
        imageUrl = "https://images.unsplash.com/photo-1776153627325-a5950e50b33a?w=900&auto=format&fit=crop&q=80",
        category = InspirationCategory.DENIM_CARGO,
        harmonyPercent = 93,
        trTitle = "Siyah Crop Top & Kemerli Haki Kargo Pantolon",
        enTitle = "Black Tube Crop & Belted Khaki Cargo Pants",
        trPalette = "Siyah Üst + Bej-Haki Kargo Uyumu",
        enPalette = "Jet Black Top + Khaki Cargo Neutral",
        trPieces = listOf("Siyah Straplez Crop Top", "Kemerli Bej/Haki Kargo Pantolon", "Beyaz Sneaker"),
        enPieces = listOf("Black Tube Crop Top", "Belted Khaki Cargo Pants", "Clean White Sneakers"),
        colorDots = listOf(Color(0xFF151618), Color(0xFFC4B39B), Color(0xFFF4F3EF))
    ),
    StreetLookItem(
        id = "ys_w18",
        imageUrl = "https://images.unsplash.com/photo-1794151090161-7d1b63a819d4?w=900&auto=format&fit=crop&q=80",
        category = InspirationCategory.DENIM_CARGO,
        harmonyPercent = 92,
        trTitle = "Lacivert Fit Crop Tişört & Bej Kargo",
        enTitle = "Navy Fitted Crop Tee & Beige Cargo Pants",
        trPalette = "Koyu Lacivert + Kum Beji Kargo",
        enPalette = "Deep Navy + Sand Beige Cargo",
        trPieces = listOf("Lacivert Fitted Crop Tişört", "Geniş Cepli Bej Kargo Pantolon", "Günlük Sneaker"),
        enPieces = listOf("Navy Fitted Crop Tee", "Beige-Khaki Cargo Pants", "Everyday Sneakers"),
        colorDots = listOf(Color(0xFF1F2B44), Color(0xFFCBBBA4), Color(0xFF4F4A42))
    ),
    StreetLookItem(
        id = "ys_w19",
        imageUrl = "https://images.unsplash.com/photo-1697507803481-5724f26a7b50?w=900&auto=format&fit=crop&q=80",
        category = InspirationCategory.TRACK_SPORT,
        harmonyPercent = 93,
        trTitle = "Renk Bloklu Track Ceket & Chunky Sneaker",
        enTitle = "Colorblock Track Jacket & Chunky Sneakers",
        trPalette = "Petrol Yeşil + Turuncu Vurgu + Siyah & Beyaz",
        enPalette = "Teal + Vibrant Orange + Black & White",
        trPieces = listOf("Petrol-Turuncu Colorblock Track Ceket", "Siyah Eşofman Altı", "Chunky Beyaz Sneaker & Gözlük"),
        enPieces = listOf("Teal-Orange Colorblock Track Jacket", "Black Sweatpants", "Chunky White Sneakers & Shades"),
        colorDots = listOf(Color(0xFF1E6A6E), Color(0xFFE0662A), Color(0xFF161719))
    ),
    StreetLookItem(
        id = "ys_w20",
        imageUrl = "https://images.unsplash.com/photo-1722507389991-5147c8ad2050?w=900&auto=format&fit=crop&q=80",
        category = InspirationCategory.TRACK_SPORT,
        harmonyPercent = 94,
        trTitle = "Pastel Pembe Eşofman Takımı & Beyaz Sneaker",
        enTitle = "Pastel Pink Utility Tracksuit & White Sneakers",
        trPalette = "Pastel Pudra Pembe Monokrom + Beyaz Taban",
        enPalette = "Pastel Pink Co-Ord + Crisp White Sneakers",
        trPieces = listOf("Pudra Pembe Utility Track Ceket", "Takım Pembe Jogger Pantolon", "Chunky Beyaz Sneaker"),
        enPieces = listOf("Pastel Pink Track Jacket", "Matching Pink Joggers", "Chunky White Sneakers"),
        colorDots = listOf(Color(0xFFE8B9C6), Color(0xFFF6F6F4), Color(0xFF9C7E88))
    ),
    StreetLookItem(
        id = "ys_w21",
        imageUrl = "https://images.unsplash.com/photo-1647418872644-c8e8c9bc4edc?w=900&auto=format&fit=crop&q=80",
        category = InspirationCategory.TRACK_SPORT,
        harmonyPercent = 95,
        trTitle = "Kahve-Krem Kolej Bomber, Eşofman & Şapka",
        enTitle = "Brown-Cream Varsity Bomber, Sweats & Cap",
        trPalette = "Espresso Kahve + Krem + Kemik High-Top",
        enPalette = "Espresso Brown + Cream + Bone High-Tops",
        trPieces = listOf("Kahve-Krem Varsity Bomber", "Kahve Eşofman Altı & Beyaz Şapka", "Bej-Beyaz High-Top Sneaker"),
        enPieces = listOf("Brown-Cream Varsity Bomber", "Brown Sweatpants & White Cap", "Beige-White High-Top Sneakers"),
        colorDots = listOf(Color(0xFF5A3D2E), Color(0xFFF2EDE3), Color(0xFFB59F88))
    ),
    StreetLookItem(
        id = "ys_w22",
        imageUrl = "https://images.unsplash.com/photo-1637642385320-43f6a96cbf0b?w=900&auto=format&fit=crop&q=80",
        category = InspirationCategory.TRACK_SPORT,
        harmonyPercent = 92,
        trTitle = "Şişme Mont, Beyaz Hoodie & Siyah Bere",
        enTitle = "Puffer Jacket Over White Hoodie & Beanie",
        trPalette = "Siyah + Turuncu Biye + Temiz Beyaz Katman",
        enPalette = "Black + Orange Piping + Crisp White Hoodie",
        trPieces = listOf("Turuncu Detaylı Siyah Şişme Mont", "Beyaz Hoodie", "Siyah Pantolon, Bere & Beyaz Sneaker"),
        enPieces = listOf("Black Puffer with Orange Trim", "White Hoodie", "Black Pants, Beanie & White Sneakers"),
        colorDots = listOf(Color(0xFF161719), Color(0xFFE56426), Color(0xFFF5F5F3))
    ),
    StreetLookItem(
        id = "ys_w23",
        imageUrl = "https://images.unsplash.com/photo-1564099972015-9eb3df8aee8d?w=900&auto=format&fit=crop&q=80",
        category = InspirationCategory.TRACK_SPORT,
        harmonyPercent = 89,
        trTitle = "Buz Mavisi Anorak & Turuncu Kargo Jogger",
        enTitle = "Ice-Blue Anorak Windbreaker & Orange Joggers",
        trPalette = "Soğuk Buz Mavisi + Canlı Turuncu Kontrastı",
        enPalette = "Cool Ice Blue + Bold Orange Contrast",
        trPieces = listOf("Buz Mavisi Pullover Anorak", "Turuncu Kargo Jogger", "Beyaz Koşu Sneaker"),
        enPieces = listOf("Ice-Blue Pullover Anorak", "Bright Orange Cargo Joggers", "White Runners"),
        colorDots = listOf(Color(0xFF9EC0DB), Color(0xFFE86124), Color(0xFFF6F6F4))
    ),
    StreetLookItem(
        id = "ys_w24",
        imageUrl = "https://images.unsplash.com/photo-1590873802674-55bb8f947bc5?w=900&auto=format&fit=crop&q=80",
        category = InspirationCategory.SMART_STREET,
        harmonyPercent = 96,
        trTitle = "Bej Oversize Blazer Takım & Siyah Saten Üst",
        enTitle = "Tailored Beige Blazer Set & Black Camisole",
        trPalette = "Krem Bej Takım + Siyah İnce Katman",
        enPalette = "Tailored Beige Suiting + Sleek Black Base",
        trPieces = listOf("Bej Blazer Ceket", "Siyah Saten Askılı Üst", "Pileli Bej Kumaş Pantolon"),
        enPieces = listOf("Tailored Beige Blazer", "Black Satin Camisole", "Pleated Beige Trousers"),
        colorDots = listOf(Color(0xFFD5C4AD), Color(0xFF17181A), Color(0xFF9C8B76))
    ),
    StreetLookItem(
        id = "ys_w25",
        imageUrl = "https://images.unsplash.com/photo-1596451984287-7a274406cbca?w=900&auto=format&fit=crop&q=80",
        category = InspirationCategory.SMART_STREET,
        harmonyPercent = 95,
        trTitle = "Oversize Siyah Blazer, Boğazlı & Şort",
        enTitle = "Oversized Black Blazer, Turtleneck & Shorts",
        trPalette = "Şık Tam Siyah Doku Katmanlaması",
        enPalette = "Elevated All-Black Texture Layering",
        trPieces = listOf("Oversize Siyah Blazer", "Siyah Boğazlı Kazak", "Suni Deri Şort & Opak Çorap"),
        enPieces = listOf("Oversized Black Blazer", "Black Turtleneck", "Faux-Leather Shorts & Tights"),
        colorDots = listOf(Color(0xFF131416), Color(0xFF2B2D32), Color(0xFF565960))
    ),
    StreetLookItem(
        id = "ys_w26",
        imageUrl = "https://images.unsplash.com/photo-1739945533087-1f80850e7d86?w=900&auto=format&fit=crop&q=80",
        category = InspirationCategory.SMART_STREET,
        harmonyPercent = 93,
        trTitle = "Açık Siyah Blazer, Taba Crop & Mini Etek",
        enTitle = "Open Black Blazer, Tan Crop & Mini Skirt",
        trPalette = "Siyah Blazer + Sıcak Taba Vurgusu",
        enPalette = "Crisp Black Blazer + Warm Tan Contrast",
        trPieces = listOf("Siyah Blazer Ceket", "Taba Straplez Crop Top", "Siyah Mini Etek"),
        enPieces = listOf("Open Black Blazer", "Tan Strapless Crop Top", "Black Mini Skirt"),
        colorDots = listOf(Color(0xFF161719), Color(0xFFAE825D), Color(0xFF423E3B))
    ),
    StreetLookItem(
        id = "ys_w27",
        imageUrl = "https://images.unsplash.com/photo-1731412673619-057b038ac93e?w=900&auto=format&fit=crop&q=80",
        category = InspirationCategory.SMART_STREET,
        harmonyPercent = 95,
        trTitle = "Kemerli Haki Blazer, Siyah Pantolon & Krem Çanta",
        enTitle = "Belted Olive Blazer, Black Trousers & Cream Bag",
        trPalette = "Zeytin Yeşili + Siyah + Krem Mini Çanta",
        enPalette = "Deep Olive + Black + Cream Mini Bag",
        trPieces = listOf("Kemerli Koyu Haki Blazer", "Siyah Düz Kumaş Pantolon", "Krem Mini Omuz Çantası"),
        enPieces = listOf("Belted Dark Olive Blazer", "Black Tailored Trousers", "Cream Mini Shoulder Bag"),
        colorDots = listOf(Color(0xFF464D34), Color(0xFF17181A), Color(0xFFF0EAE0))
    ),
    StreetLookItem(
        id = "ys_w28",
        imageUrl = "https://images.unsplash.com/photo-1762343286858-6344348cef15?w=900&auto=format&fit=crop&q=80",
        category = InspirationCategory.SMART_STREET,
        harmonyPercent = 94,
        trTitle = "Lacivert Oversize Blazer, Siyah Elbise & Çanta",
        enTitle = "Navy Oversized Blazer, Black Dress & Mini Bag",
        trPalette = "Koyu Lacivert + Siyah + Kırık Beyaz Çanta",
        enPalette = "Deep Navy + Black + Off-White Handbag",
        trPieces = listOf("Yapılı Lacivert Oversize Blazer", "Siyah Mini Elbise", "Güneş Gözlüğü & Kırık Beyaz Çanta"),
        enPieces = listOf("Structured Navy Oversized Blazer", "Black Mini Dress", "Sunglasses & Off-White Mini Bag"),
        colorDots = listOf(Color(0xFF1E2D4A), Color(0xFF151618), Color(0xFFF2EFE9))
    ),
    StreetLookItem(
        id = "ys_w29",
        imageUrl = "https://images.unsplash.com/photo-1745825219087-802850857308?w=900&auto=format&fit=crop&q=80",
        category = InspirationCategory.OVERSIZE,
        harmonyPercent = 95,
        trTitle = "Çizgili Kaban, Ekru Hoodie & Siyah Kep",
        enTitle = "Pinstripe Trench Coat, Ecru Hoodie & Black Cap",
        trPalette = "Antrasit Çizgili + Ekru + Siyah Aksesuar",
        enPalette = "Charcoal Pinstripe + Ecru + Sleek Black",
        trPieces = listOf("Oversize Çizgili Kaban", "Krem/Beyaz Hoodie", "Minimal Siyah Kep & Gözlük"),
        enPieces = listOf("Relaxed Pinstripe Coat", "Ecru Oversize Hoodie", "Minimal Black Cap & Sunglasses"),
        colorDots = listOf(Color(0xFF2B2D32), Color(0xFFF3EFE8), Color(0xFF151618))
    ),
    StreetLookItem(
        id = "ys_w30",
        imageUrl = "https://images.unsplash.com/photo-1647218947427-d783309440d2?w=900&auto=format&fit=crop&q=80",
        category = InspirationCategory.OVERSIZE,
        harmonyPercent = 93,
        trTitle = "Boxy Gri Sweatshirt, Bol Siyah Pantolon & Çapraz Çanta",
        enTitle = "Boxy Grey Sweatshirt, Wide Black Pants & Crossbody",
        trPalette = "Melanj Gri + Siyah + Beyaz Sneaker",
        enPalette = "Heather Grey + Black + Clean White Trainers",
        trPieces = listOf("Boxy Gri Sweatshirt", "Siyah Bol Sokak Pantolonu", "Çapraz Göğüs Çantası & Sneaker"),
        enPieces = listOf("Boxy Ash Sweatshirt", "Baggy Black Trousers", "Crossbody Bag & Sneakers"),
        colorDots = listOf(Color(0xFF7B7F86), Color(0xFF17181A), Color(0xFFF5F5F2))
    ),
    StreetLookItem(
        id = "ys_w31",
        imageUrl = "https://images.unsplash.com/photo-1584216338898-f34d78201414?w=900&auto=format&fit=crop&q=80",
        category = InspirationCategory.LAYERING,
        harmonyPercent = 92,
        trTitle = "Petrol Kot Ceket, Ekose Bol Pantolon & Hardal Bere",
        enTitle = "Petrol Denim Jacket, Plaid Wide Pants & Mustard Beanie",
        trPalette = "Petrol İndigo + Ekose + Hardal Vurgusu",
        enPalette = "Petrol Denim + Plaid Check + Mustard Pop",
        trPieces = listOf("Koyu Yıkama Kot Ceket", "Ekose Geniş Paça Pantolon", "Sarı/Hardal Bere & Sneaker"),
        enPieces = listOf("Deep Indigo Denim Jacket", "Checked Wide Trousers", "Mustard Beanie & Trainers"),
        colorDots = listOf(Color(0xFF28475C), Color(0xFFD69F2C), Color(0xFF25282E))
    ),
    StreetLookItem(
        id = "ys_w32",
        imageUrl = "https://images.unsplash.com/photo-1534404483017-8743b4e935cd?w=900&auto=format&fit=crop&q=80",
        category = InspirationCategory.LAYERING,
        harmonyPercent = 91,
        trTitle = "Kiremit Cropped Rüzgarlık & Açık Yıkama Jean",
        enTitle = "Rust Orange Cropped Windbreaker & Light Wash Denim",
        trPalette = "Kiremit / Pas Turuncusu + Beyaz + Açık Mavi",
        enPalette = "Rust Terracotta + White Tee + Ice Blue Denim",
        trPieces = listOf("Kiremit Kısa Rüzgarlık Ceket", "Beyaz Crop Tişört", "Açık Mavi Rahat Jean"),
        enPieces = listOf("Rust Cropped Windbreaker", "Crisp White Base", "Light Wash Straight Jeans"),
        colorDots = listOf(Color(0xFFC25335), Color(0xFFF4F4F1), Color(0xFF6A8EB0))
    ),
    StreetLookItem(
        id = "ys_w33",
        imageUrl = "https://images.unsplash.com/photo-1772835415079-9764e6945493?w=900&auto=format&fit=crop&q=80",
        category = InspirationCategory.LAYERING,
        harmonyPercent = 93,
        trTitle = "Siyah-Kırmızı İki Renkli Sokak Bomber Ceket",
        enTitle = "Black & Crimson Varsity Street Bomber Look",
        trPalette = "Mat Siyah + Kiraz Kırmızısı + Beyaz",
        enPalette = "Matte Black + Crimson Red + Off-White",
        trPieces = listOf("İki Renkli Sokak Bomber Ceket", "Siyah Geniş Pantolon / Etek", "Spor Sokak Çantası"),
        enPieces = listOf("Two-Tone Varsity Bomber", "Wide Black Bottoms", "Sporty Mini Shoulder Bag"),
        colorDots = listOf(Color(0xFF18191B), Color(0xFFAE282D), Color(0xFFF2EFEA))
    ),
    StreetLookItem(
        id = "ys_w34",
        imageUrl = "https://images.unsplash.com/photo-1742980511651-b845fa826f33?w=900&auto=format&fit=crop&q=80",
        category = InspirationCategory.DENIM_CARGO,
        harmonyPercent = 95,
        trTitle = "Krem Uzun Kol Crop & Yıkamalı Baggy Kargo Jean",
        enTitle = "Cream Long-Sleeve Top & Washed Baggy Cargo Denim",
        trPalette = "Ekru Krem + Yıkamalı Füme Denim",
        enPalette = "Soft Cream + Vintage Washed Charcoal Denim",
        trPieces = listOf("Krem Ribanalı Crop Top", "Baggy Yıkamalı Kargo Jean", "Güneş Gözlüğü & Platform Sneaker"),
        enPieces = listOf("Cream Ribbed Long-Sleeve Crop", "Baggy Washed Cargo Jeans", "Retro Sunglasses & Sneakers"),
        colorDots = listOf(Color(0xFFF1ECE1), Color(0xFF474A50), Color(0xFF8C867B))
    ),
    StreetLookItem(
        id = "ys_w35",
        imageUrl = "https://images.unsplash.com/photo-1608370946545-7d93571b5593?w=900&auto=format&fit=crop&q=80",
        category = InspirationCategory.DENIM_CARGO,
        harmonyPercent = 94,
        trTitle = "Siyah Bluz, Rahat Mavi Jean & Taba Kemer",
        enTitle = "Sleek Black Top, Relaxed Blue Jeans & Tan Belt",
        trPalette = "Siyah + İndigo Jean + Sıcak Kahve Kemer",
        enPalette = "Black + Classic Indigo + Cognac Belt",
        trPieces = listOf("Vücuda Oturan Siyah Üst", "Düz Paça Rahat Mavi Jean", "Taba Deri Kemer & Beyaz Sneaker"),
        enPieces = listOf("Fitted Black Top", "Straight Relaxed Blue Jeans", "Cognac Belt & White Kicks"),
        colorDots = listOf(Color(0xFF17181B), Color(0xFF476992), Color(0xFF9C6942))
    ),
    StreetLookItem(
        id = "ys_w36",
        imageUrl = "https://images.unsplash.com/photo-1592595293637-8557fa6d3c64?w=900&auto=format&fit=crop&q=80",
        category = InspirationCategory.DENIM_CARGO,
        harmonyPercent = 92,
        trTitle = "Beyaz Korse Atlet, Yüksek Bel Jean & Chunky Sneaker",
        enTitle = "Structured White Tank, High-Rise Jeans & Chunky Shoes",
        trPalette = "Beyaz + Mavi Denim + Krem Taban",
        enPalette = "Crisp White + Classic Blue Denim + Off-White",
        trPieces = listOf("Yapılı Beyaz Crop Atlet", "Yüksek Bel Geniş Jean", "Chunky Platform Sneaker"),
        enPieces = listOf("Structured White Tank Crop", "High-Rise Wide Jeans", "Chunky Platform Sneakers"),
        colorDots = listOf(Color(0xFFF6F5F0), Color(0xFF53759B), Color(0xFFDDD4C6))
    ),
    StreetLookItem(
        id = "ys_w37",
        imageUrl = "https://images.unsplash.com/photo-1780396508975-2933f9a81dd3?w=900&auto=format&fit=crop&q=80",
        category = InspirationCategory.TRACK_SPORT,
        harmonyPercent = 94,
        trTitle = "Lacivert Oversize Track Takım & Kırmızı Yan Şerit",
        enTitle = "Oversize Navy Tracksuit with Crimson Side Stripes",
        trPalette = "Gece Laciverti + Kırmızı Biye + Beyaz",
        enPalette = "Midnight Navy + Red Piping + White Kicks",
        trPieces = listOf("Oversize Lacivert Track Üst", "Kırmızı Şeritli Bol Eşofman Altı", "Retro Koşu Sneaker"),
        enPieces = listOf("Oversized Navy Track Top", "Red-Striped Baggy Track Pants", "Retro Running Shoes"),
        colorDots = listOf(Color(0xFF1B2B49), Color(0xFFC42F33), Color(0xFFF3F3F0))
    ),
    StreetLookItem(
        id = "ys_w38",
        imageUrl = "https://images.unsplash.com/photo-1779154138210-18cd7f96f7a5?w=900&auto=format&fit=crop&q=80",
        category = InspirationCategory.TRACK_SPORT,
        harmonyPercent = 91,
        trTitle = "Retro Colorblock Eşofman Takımı & Gül Detayı",
        enTitle = "Retro Colorblock Windbreaker Set & Rosewood Trim",
        trPalette = "Siyah + Ekru + Gül/Kırmızı Blok",
        enPalette = "Black + Ivory + Rose/Crimson Panels",
        trPieces = listOf("Vintage Colorblock Track Ceket", "Büzgülü Jogger Eşofman Altı", "Beyaz Sokak Sneaker"),
        enPieces = listOf("Vintage Colorblock Track Jacket", "Elasticated Jogger Pants", "White Street Trainers"),
        colorDots = listOf(Color(0xFF19191C), Color(0xFFF2ECE6), Color(0xFFC75C6B))
    ),
    StreetLookItem(
        id = "ys_w39",
        imageUrl = "https://images.unsplash.com/photo-1768929096133-1748d1fe5944?w=900&auto=format&fit=crop&q=80",
        category = InspirationCategory.TRACK_SPORT,
        harmonyPercent = 93,
        trTitle = "Lacivert Kısa Fermuarlı Track Ceket & Yüksek Bel Tayt",
        enTitle = "Navy Cropped Zip Track Jacket & Sculpt Leggings",
        trPalette = "Lacivert + Köpük Beyazı + Siyah Alt",
        enPalette = "Navy Blue + Crisp White + Black Base",
        trPieces = listOf("Kısa Lacivert Spor Track Ceket", "Yüksek Bel Toparlayıcı Tayt", "Beyaz Chunky Sneaker"),
        enPieces = listOf("Cropped Navy Zip Track Top", "High-Rise Sculpt Leggings", "White Chunky Runners"),
        colorDots = listOf(Color(0xFF213358), Color(0xFFF5F5F3), Color(0xFF151618))
    ),
    StreetLookItem(
        id = "ys_w40",
        imageUrl = "https://images.unsplash.com/photo-1763766274625-3b502e267f26?w=900&auto=format&fit=crop&q=80",
        category = InspirationCategory.SMART_STREET,
        harmonyPercent = 96,
        trTitle = "Bej Uzun Trençkot, Siyah Pileli Mini & Uzun Çizme",
        enTitle = "Beige Long Trench, Black Pleated Mini & Knee Boots",
        trPalette = "Soğuk Bej + Tam Siyah + Deri Çizme",
        enPalette = "Warm Sand Trench + Matte Black + Tall Leather",
        trPieces = listOf("Akışkan Bej Trençkot", "Siyah Pileli Mini Elbise/Etek", "Siyah Uzun Deri Çizme"),
        enPieces = listOf("Draped Sand Trench Coat", "Black Pleated Mini", "Tall Black Leather Boots"),
        colorDots = listOf(Color(0xFFD2BE9E), Color(0xFF151618), Color(0xFF3E352E))
    ),
    StreetLookItem(
        id = "ys_w41",
        imageUrl = "https://images.unsplash.com/photo-1523359346063-d879354c0ea5?w=900&auto=format&fit=crop&q=80",
        category = InspirationCategory.SMART_STREET,
        harmonyPercent = 95,
        trTitle = "Lacivert Ekose Oversize Blazer & Raw Hem Jean",
        enTitle = "Navy Plaid Oversize Blazer & Raw-Hem Indigo Denim",
        trPalette = "Lacivert Ekose + İndigo Jean + Beyaz Tişört",
        enPalette = "Navy Check + Indigo Denim + Clean White",
        trPieces = listOf("Oversize Ekose Lacivert Blazer", "Beyaz Basic Tişört", "Kesik Paça Mavi Jean & Sneaker"),
        enPieces = listOf("Oversized Navy Plaid Blazer", "White Minimal Tee", "Raw-Hem Jeans & White Kicks"),
        colorDots = listOf(Color(0xFF21314F), Color(0xFF476993), Color(0xFFF5F5F2))
    )
).let(::interleaveLookbookCategories)

/**
 * Spreads lookbook items evenly across categories so hoodies/sweatshirts aren't clustered at the top.
 */
private fun interleaveLookbookCategories(items: List<StreetLookItem>): List<StreetLookItem> {
    val categoryOrder = listOf(
        InspirationCategory.SMART_STREET,
        InspirationCategory.LAYERING,
        InspirationCategory.DENIM_CARGO,
        InspirationCategory.TRACK_SPORT,
        InspirationCategory.OVERSIZE
    )
    val buckets = categoryOrder.associateWith { cat ->
        ArrayDeque(items.filter { it.category == cat })
    }
    val mixed = ArrayList<StreetLookItem>(items.size)
    while (mixed.size < items.size) {
        var addedAny = false
        for (cat in categoryOrder) {
            val queue = buckets[cat] ?: continue
            if (queue.isNotEmpty()) {
                mixed.add(queue.removeFirst())
                addedAny = true
            }
        }
        if (!addedAny) break
    }
    if (mixed.size >= 5) {
        val fourth = mixed[3]
        val fifth = mixed[4]
        mixed.removeAt(4)
        mixed.removeAt(3)
        mixed.add(0, fifth)
        mixed.add(0, fourth)
    }
    return mixed
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InspirationScreen(
    onOpenSettings: () -> Unit,
    onOpenProfile: () -> Unit = {},
    profileAvatarBase64: String = "",
    pendingRequestsCount: Int = 0,
    unreadActivityCount: Int = 0,
    onOpenActivity: () -> Unit = {},
    preferredGender: GenderPreference = GenderPreference.UNISEX,
    persistedGender: LookbookGender? = null,
    onSelectGender: ((LookbookGender) -> Unit)? = null,
    items: List<ClothingItem> = emptyList(),
    facePhotoVersion: Int = 0,
    onPickFaceFromGallery: () -> Unit = {},
    onCaptureFaceFromCamera: () -> Unit = {},
    likedLookIds: Set<String> = emptySet(),
    dislikedLookIds: Set<String> = emptySet(),
    visitNonce: Int = 1,
    onRecordLookSwipe: (String, Boolean?) -> Unit = { _, _ -> },
    remoteConfig: RemoteConfigState = RemoteConfigState(),
    onRequireFeature: (AppFeatureKey, () -> Unit) -> Unit = { _, run -> run() },
    listState: LazyListState = rememberLazyListState()
) {
    val context = LocalContext.current
    val lang = LocalAppLanguage.current
    val strings = appStrings()
    val primaryInk = DressKitStyle.primaryInk()
    val secondaryInk = DressKitStyle.secondaryInk()
    val closetItems = remember(items) { items.filter { !it.isWishlist } }

    var localGender by rememberSaveable {
        mutableStateOf(persistedGender ?: if (preferredGender == GenderPreference.FEMALE) LookbookGender.WOMEN else LookbookGender.MEN)
    }
    val selectedGender = persistedGender ?: localGender
    val updateSelectedGender: (LookbookGender) -> Unit = { next ->
        localGender = next
        onSelectGender?.invoke(next)
    }
    var selectedCategory by rememberSaveable { mutableStateOf(InspirationCategory.ALL) }
    var zoomedLook by remember { mutableStateOf<StreetLookItem?>(null) }
    var selectedColorForShop by remember { mutableStateOf<GarmentColor?>(null) }
    var showSwipeDiscovery by rememberSaveable { mutableStateOf(false) }

    var isAnalyzingFace by remember(facePhotoVersion, lang) { mutableStateOf(true) }
    val personalProfile by produceState<PersonalColorProfile?>(
        initialValue = null,
        key1 = facePhotoVersion,
        key2 = lang
    ) {
        isAnalyzingFace = true
        value = withContext(Dispatchers.IO) {
            PersonalColorAnalyzer.analyzeUserPhoto(context, lang)
        }
        isAnalyzingFace = false
    }
    var overrideSeason by remember(facePhotoVersion) { mutableStateOf<ColorSeason?>(null) }
    var showAllSeasons by remember { mutableStateOf(false) }
    val activePersonalProfile = remember(personalProfile, overrideSeason, lang) {
        val base = personalProfile ?: return@remember null
        val ov = overrideSeason ?: return@remember base
        PersonalColorAnalyzer.profileForSeason(ov, base.averageSkinHex, base.faceDetected, base.contrast, lang)
    }

    fun openStoreSearchUrl(url: String) {
        com.dresskit.wardrobe.data.SecurityGuards.openExternalUrlSafely(context, url)
    }

    selectedColorForShop?.let { color ->
        val genderWord = if (selectedGender == LookbookGender.WOMEN) {
            if (lang == AppLanguage.TR) "kadın" else "women"
        } else {
            if (lang == AppLanguage.TR) "erkek" else "men"
        }
        val colorQuery = color.display(lang)
        AlertDialog(
            onDismissRequest = { selectedColorForShop = null },
            containerColor = DressKitStyle.cardSurface(),
            titleContentColor = primaryInk,
            textContentColor = secondaryInk,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ColorDot(color = color.composeColor, size = 16.dp)
                    Text("${color.display(lang)} ${localizedText(lang, "Kombin Önerileri", "Outfit Picks", "Teile", "Pièces", "Prendas", "穿搭推荐")}")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    activePersonalProfile?.summaryText?.let { summary ->
                        Text(text = summary, style = MaterialTheme.typography.bodySmall, color = primaryInk)
                    }
                    Text(
                        text = localizedText(
                            lang,
                            "Mağazalarda Ara",
                            "Search in Stores",
                            "In Shops suchen",
                            "Rechercher en boutique",
                            "Buscar en tiendas",
                            "在商店中搜索"
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = primaryInk
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StoreSearchLinks.buildStoreSearchLinks(
                            genderPrefix = genderWord,
                            rawQuery = colorQuery,
                            googleLabel = "Google Alışveriş"
                        ).forEach { (store, url) ->
                            Surface(
                                shape = RoundedCornerShape(DressKitStyle.chipCorner()),
                                color = DressKitStyle.buttonBg(),
                                modifier = Modifier.clickable { openStoreSearchUrl(url) }
                            ) {
                                Text(
                                    text = store,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = DressKitStyle.buttonFg(),
                                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { selectedColorForShop = null },
                    shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DressKitStyle.buttonBg(),
                        contentColor = DressKitStyle.buttonFg()
                    )
                ) {
                    Text(strings.close)
                }
            }
        )
    }

    val shuffledGenderLooks = remember(selectedGender, visitNonce) {
        val baseLooks = if (selectedGender == LookbookGender.WOMEN) {
            VERIFIED_WOMEN_STREET_LOOKS
        } else {
            VERIFIED_STREET_LOOKS
        }
        val seed = (System.nanoTime() xor (visitNonce.toLong() * 0x5DEECE66DL))
        baseLooks.shuffled(kotlin.random.Random(seed))
    }
    val filtered = remember(shuffledGenderLooks, selectedCategory) {
        if (selectedCategory == InspirationCategory.ALL) {
            shuffledGenderLooks
        } else {
            shuffledGenderLooks.filter { it.category == selectedCategory }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DressKitStyle.canvasBg())
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = localizedText(
                            lang,
                            "Kombin İlhamı",
                            "Street Lookbook",
                            "Streetstyle-Lookbook",
                            "Lookbook Street",
                            "Lookbook Urbano",
                            "穿搭灵感"
                        ),
                        style = MaterialTheme.typography.displaySmall,
                        color = primaryInk
                    )
                    Text(
                        text = "${strings.inspirationSubtitle} · ${filtered.size}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = secondaryInk
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = strings.settingsTitle,
                            tint = primaryInk
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                color = DressKitStyle.secondarySurface(),
                border = BorderStroke(DressKitStyle.borderWidth(), DressKitStyle.borderColor())
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    LookbookGender.entries.forEach { gender ->
                        val isSelected = selectedGender == gender
                        val count = if (gender == LookbookGender.WOMEN) {
                            VERIFIED_WOMEN_STREET_LOOKS.size
                        } else {
                            VERIFIED_STREET_LOOKS.size
                        }
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { updateSelectedGender(gender) },
                            shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                            color = if (isSelected) DressKitStyle.buttonBg() else Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (gender == LookbookGender.WOMEN) {
                                        Icons.Outlined.Female
                                    } else {
                                        Icons.Outlined.Male
                                    },
                                    contentDescription = null,
                                    tint = if (isSelected) DressKitStyle.buttonFg() else primaryInk,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "${gender.label(lang)} ($count)",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                                    ),
                                    color = if (isSelected) DressKitStyle.buttonFg() else primaryInk
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onRequireFeature(AppFeatureKey.STYLE_DISCOVERY) {
                            showSwipeDiscovery = true
                        }
                    },
                shape = RoundedCornerShape(DressKitStyle.chipCorner()),
                color = DressKitStyle.accentSoft(),
                border = BorderStroke(1.dp, DressKitStyle.accentColor().copy(alpha = 0.45f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            tint = DressKitStyle.accentColor(),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = localizedText(
                                lang,
                                "Stil Keşfi · Kaydır & Beğen",
                                "Style Discovery · Swipe Looks",
                                "Style Discovery · Wischen",
                                "Découverte Style · Swiper",
                                "Descubre Estilo · Desliza",
                                "风格发现 · 滑动挑选"
                            ),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = DressKitStyle.accentColor(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "❤️ ${likedLookIds.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = primaryInk,
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            text = "✕ ${dislikedLookIds.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = secondaryInk,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(end = 12.dp)
            ) {
                items(InspirationCategory.entries) { cat ->
                    FilterChipPill(
                        label = cat.label(lang),
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat }
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clipToBounds()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .bouncyOverscroll(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 112.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (remoteConfig.isBannerVisible("lookbook_top")) {
                    item(key = "lookbook_top_banner") {
                        RemoteAdSlotBanner(
                            slot = "lookbook_top",
                            config = remoteConfig,
                            lang = lang
                        )
                    }
                }
                item(key = "face_skin_tone_card") {
                    val prefs = remember { context.getSharedPreferences("inspiration", android.content.Context.MODE_PRIVATE) }
                    var expanded by remember { mutableStateOf(prefs.getBoolean("faceExpanded", true)) }
                    val profile = activePersonalProfile
                    val faceFile = remember(facePhotoVersion) {
                        ImageStorage.getUserFacePhotoFile(context)
                    }
                    Surface(
                        shape = RoundedCornerShape(DressKitStyle.cardCorner()),
                        color = DressKitStyle.cardSurface(),
                        border = BorderStroke(DressKitStyle.borderWidth(), DressKitStyle.borderColor()),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.animateContentSize().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    expanded = !expanded
                                    prefs.edit().putBoolean("faceExpanded", expanded).apply()
                                },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.FaceRetouchingNatural,
                                        contentDescription = null,
                                        tint = DressKitStyle.accentColor(),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = localizedText(
                                            lang,
                                            "Yüz & Ten Rengi Uyum Analizi",
                                            "Face & Skin Tone Color Match",
                                            "Gesichts- & Hautton-Farbanalyse",
                                            "Analyse teint & couleurs",
                                            "Análisis de rostro y tono",
                                            "面容与肤色色彩匹配"
                                        ),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = primaryInk,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (faceFile.exists()) {
                                        val lastMod = faceFile.lastModified()
                                        val request = remember(facePhotoVersion, lastMod) {
                                            ImageRequest.Builder(context)
                                                .data(faceFile)
                                                .memoryCacheKey("face_thumb_${facePhotoVersion}_$lastMod")
                                                .diskCacheKey("face_thumb_${facePhotoVersion}_$lastMod")
                                                .build()
                                        }
                                        AsyncImage(
                                            model = request,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .border(1.dp, DressKitStyle.borderColor(), CircleShape)
                                        )
                                    }
                                    if (profile != null) {
                                        Box(
                                            modifier = Modifier
                                                .size(22.dp)
                                                .clip(CircleShape)
                                                .background(Color(profile.averageSkinHex))
                                                .border(1.dp, DressKitStyle.borderColor(), CircleShape)
                                        )
                                    }
                                    Box(Modifier.size(24.dp).border(1.dp, DressKitStyle.borderColor(), CircleShape), contentAlignment = Alignment.Center) {
                                        Icon(if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                                            localizedText(lang, if (expanded) "Küçült" else "Büyüt", if (expanded) "Collapse" else "Expand"), modifier = Modifier.size(16.dp), tint = secondaryInk)
                                    }
                                }
                            }
                            if (!expanded && profile != null) Text("${profile.seasonTitle} · ${profile.undertone.label(lang)}",
                                style = MaterialTheme.typography.labelLarge, color = DressKitStyle.accentColor(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (expanded) {
                                if (profile != null) {
                                    val matchingWardrobeCount = remember(closetItems, profile) {
                                        closetItems.count { garment ->
                                            garment.colors.any { it in profile.flatteringColors }
                                        }
                                    }
                                    Text(
                                        text = "${profile.seasonTitle} · ${profile.undertone.label(lang)} · ${profile.contrast.label(lang)}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = DressKitStyle.accentColor()
                                    )
                                    Text(
                                        text = profile.summaryText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = primaryInk
                                    )
                                    Text(
                                        text = localizedText(
                                            lang,
                                            "Sana En Çok Yakışacak Renkler (dokun → ara):",
                                            "Most Flattering Colors (tap → search):",
                                            "Beste Farben für deinen Teint (tippen → suchen):",
                                            "Couleurs qui vous subliment (toucher → chercher) :",
                                            "Colores que más te favorecen (toca → buscar):",
                                            "最衬肤色的推荐色彩（点击搜索）："
                                        ),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = secondaryInk
                                    )
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                                        verticalArrangement = Arrangement.spacedBy(7.dp)
                                    ) {
                                        profile.flatteringColors.forEach { color ->
                                            Surface(
                                                shape = RoundedCornerShape(DressKitStyle.chipCorner()),
                                                color = DressKitStyle.secondarySurface(),
                                                border = BorderStroke(1.dp, DressKitStyle.borderColor()),
                                                modifier = Modifier.clickable { selectedColorForShop = color }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                                ) {
                                                    ColorDot(color = color.composeColor, size = 12.dp)
                                                    Text(
                                                        text = color.display(lang),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = primaryInk
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    if (profile.avoidColors.isNotEmpty()) {
                                        Text(
                                            text = "${localizedText(lang, "Kaçınılacak Tonlar", "Avoid Tones")}: ${profile.avoidColors.joinToString(", ") { it.display(lang) }}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = secondaryInk
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(DressKitStyle.chipCorner()),
                                            color = if (showAllSeasons) DressKitStyle.accentSoft() else DressKitStyle.secondarySurface(),
                                            border = BorderStroke(1.dp, if (showAllSeasons) DressKitStyle.accentColor() else DressKitStyle.borderColor()),
                                            modifier = Modifier.clickable { showAllSeasons = !showAllSeasons }
                                        ) {
                                            Text(
                                                text = if (showAllSeasons) {
                                                    localizedText(lang, "12 Sezonu Gizle ▲", "Hide 12 Seasons ▲")
                                                } else {
                                                    localizedText(lang, "Daha Fazla Öneri · Tüm 12 Sezon ▼", "More Options · All 12 Seasons ▼")
                                                },
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                                color = if (showAllSeasons) DressKitStyle.accentColor() else primaryInk,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                            )
                                        }
                                        if (overrideSeason != null) {
                                            Text(
                                                text = localizedText(lang, "Otomatiğe Dön", "Reset Auto"),
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                                color = DressKitStyle.accentColor(),
                                                modifier = Modifier.clickable { overrideSeason = null }
                                            )
                                        }
                                    }
                                    if (showAllSeasons) {
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            ColorSeason.entries.forEach { cs ->
                                                val isSelected = profile.season == cs
                                                FilterChipPill(
                                                    label = cs.shortLabel(lang),
                                                    selected = isSelected,
                                                    onClick = { overrideSeason = if (personalProfile?.season == cs) null else cs }
                                                )
                                            }
                                        }
                                    }
                                    if (closetItems.isNotEmpty()) {
                                        Text(
                                            text = localizedText(
                                                lang,
                                                "Dolabındaki $matchingWardrobeCount parça ten tonunla yüksek uyumlu.",
                                                "$matchingWardrobeCount pieces in your closet match your skin tone.",
                                                "$matchingWardrobeCount Teile in deinem Schrank passen zu deinem Teint.",
                                                "$matchingWardrobeCount pièces de votre dressing subliment votre teint.",
                                                "$matchingWardrobeCount prendas de tu armario combinan con tu tono.",
                                                "衣橱中有 $matchingWardrobeCount 件单品非常衬你的肤色。"
                                            ),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = secondaryInk
                                        )
                                    }
                                } else if (faceFile.exists() && !isAnalyzingFace) {
                                    Text(
                                        text = localizedText(
                                            lang,
                                            "Fotoğraf analiz edilemedi; lütfen daha aydınlık ve net bir fotoğraf dene.",
                                            "Could not analyze photo; please try a brighter, clearer photo.",
                                            "Foto konnte nicht analysiert werden; bitte versuche ein helleres, klareres Foto.",
                                            "Impossible d'analyser la photo ; essayez une photo plus lumineuse et nette.",
                                            "No se pudo analizar la foto; prueba una más clara y luminosa.",
                                            "无法分析该照片，请尝试更清晰明亮的照片。"
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = DressKitStyle.accentColor()
                                    )
                                } else {
                                    Text(
                                        text = localizedText(
                                            lang,
                                            "Kendi fotoğrafını veya boy fotoğrafını yüklediğinde yüzünü ve ten alt tonunu analiz edip sana en çok yakışacak renk paletini otomatik çıkarır.",
                                            "Upload a portrait or full-body photo to analyze your face & skin tone and discover your most flattering color palette.",
                                            "Lade ein Foto hoch, um deinen Hautton zu analysieren und passende Farben zu finden.",
                                            "Ajoutez votre photo pour analyser votre teint et révéler vos couleurs idéales.",
                                            "Sube tu foto para analizar tu rostro y tono de piel y descubrir tus mejores colores.",
                                            "上传你的照片即可分析面容与肤色调，自动推荐最适合你的显白色系。"
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = secondaryInk
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            onRequireFeature(AppFeatureKey.FACE_TONE) {
                                                onPickFaceFromGallery()
                                            }
                                        },
                                        shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                                        border = BorderStroke(1.dp, DressKitStyle.borderColor()),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, tint = primaryInk, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.size(5.dp))
                                        Text(
                                            text = if (profile != null) {
                                                localizedText(lang, "Değiştir", "Change", "Ändern", "Changer", "Cambiar", "更换照片")
                                            } else {
                                                localizedText(lang, "Galeri", "Gallery", "Galerie", "Galerie", "Galería", "从相册选")
                                            },
                                            style = MaterialTheme.typography.labelMedium,
                                            color = primaryInk,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            onRequireFeature(AppFeatureKey.FACE_TONE) {
                                                onCaptureFaceFromCamera()
                                            }
                                        },
                                        shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                                        border = BorderStroke(1.dp, DressKitStyle.borderColor()),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Outlined.PhotoCamera, contentDescription = null, tint = primaryInk, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.size(5.dp))
                                        Text(
                                            text = localizedText(lang, "Kamera", "Camera", "Kamera", "Caméra", "Cámara", "拍照"),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = primaryInk,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                items(filtered, key = { it.id }) { look ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { zoomedLook = look },
                        shape = RoundedCornerShape(DressKitStyle.cardCorner()),
                        color = DressKitStyle.cardSurface(),
                        border = BorderStroke(DressKitStyle.borderWidth(), DressKitStyle.borderColor()),
                        shadowElevation = if (DressKitStyle.isStreet()) 0.dp else 1.dp
                    ) {
                        Column {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(256.dp)
                                    .clip(
                                        RoundedCornerShape(
                                            topStart = DressKitStyle.cardCorner(),
                                            topEnd = DressKitStyle.cardCorner()
                                        )
                                    )
                                    .background(DressKitStyle.photoBoxSurface())
                            ) {
                                AsyncImage(
                                    model = look.imageUrl,
                                    contentDescription = if (lang == AppLanguage.TR) look.trTitle else look.enTitle,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )

                                Surface(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(12.dp),
                                    shape = RoundedCornerShape(DressKitStyle.chipCorner()),
                                    color = DressKitStyle.cardSurface().copy(alpha = 0.92f)
                                ) {
                                    Text(
                                        text = look.category.label(lang),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = primaryInk,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }

                                HarmonyScoreBadge(
                                    score = look.harmonyPercent,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(12.dp)
                                )
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (lang == AppLanguage.TR) look.trTitle else look.enTitle,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = primaryInk,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                        look.colorDots.forEach { dot ->
                                            ColorDot(color = dot, size = 12.dp)
                                        }
                                    }
                                }

                                Text(
                                    text = if (lang == AppLanguage.TR) look.trPalette else look.enPalette,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = DressKitStyle.accentColor()
                                )

                                val pieces = if (lang == AppLanguage.TR) look.trPieces else look.enPieces
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    pieces.forEach { piece ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.AutoAwesome,
                                                contentDescription = null,
                                                tint = secondaryInk,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Text(
                                                text = piece,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = secondaryInk
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSwipeDiscovery) {
        LookbookSwipeDiscoveryDialog(
            selectedGender = selectedGender,
            visitNonce = visitNonce,
            onGenderChange = { updateSelectedGender(it) },
            likedLookIds = likedLookIds,
            dislikedLookIds = dislikedLookIds,
            onRecordLookSwipe = onRecordLookSwipe,
            onInspectLook = { look -> zoomedLook = look },
            onDismiss = { showSwipeDiscovery = false }
        )
    }

    zoomedLook?.let { look ->
        val lookTitle = if (lang == AppLanguage.TR) look.trTitle else look.enTitle
        val lookPalette = if (lang == AppLanguage.TR) look.trPalette else look.enPalette
        val pieces = if (lang == AppLanguage.TR) look.trPieces else look.enPieces
        val genderPrefix = if (selectedGender == LookbookGender.WOMEN) {
            if (lang == AppLanguage.TR) "kadın" else "women"
        } else {
            if (lang == AppLanguage.TR) "erkek" else "men"
        }
        var selectedStorePieceIdx by remember(look.id) { mutableIntStateOf(0) }

        fun openSearchUrl(url: String) {
            com.dresskit.wardrobe.data.SecurityGuards.openExternalUrlSafely(context, url)
        }

        Dialog(
            onDismissRequest = { zoomedLook = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.88f))
                    .clickable { zoomedLook = null },
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 28.dp)
                        .clickable(enabled = false) {},
                    shape = RoundedCornerShape(DressKitStyle.cardCorner()),
                    color = DressKitStyle.cardSurface(),
                    border = BorderStroke(1.dp, DressKitStyle.borderColor())
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(320.dp)
                                .background(DressKitStyle.photoBoxSurface())
                        ) {
                            AsyncImage(
                                model = look.imageUrl,
                                contentDescription = lookTitle,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            HarmonyScoreBadge(
                                score = look.harmonyPercent,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(12.dp)
                            )
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(12.dp),
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.55f)
                            ) {
                                IconButton(onClick = { zoomedLook = null }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = strings.close,
                                        tint = Color.White
                                    )
                                }
                            }
                        }

                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = lookTitle,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = primaryInk
                                )
                                Text(
                                    text = lookPalette,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = DressKitStyle.accentColor()
                                )
                            }

                            Text(
                                text = localizedText(
                                    lang,
                                    "Kombin Parçaları & Benzer Ürün Ara",
                                    "Pieces & Find Similar Products",
                                    "Teile & Ähnliche Produkte",
                                    "Pièces & Articles similaires",
                                    "Prendas y Buscar similares",
                                    "穿搭单品与相似款搜索"
                                ),
                                style = MaterialTheme.typography.labelLarge,
                                color = primaryInk
                            )

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                pieces.forEachIndexed { idx, piece ->
                                    val isSelectedForStores = idx == selectedStorePieceIdx
                                    Surface(
                                        shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                                        color = if (isSelectedForStores) DressKitStyle.accentSoft() else DressKitStyle.secondarySurface(),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isSelectedForStores) DressKitStyle.accentColor() else DressKitStyle.borderColor()
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { selectedStorePieceIdx = idx }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = piece,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = primaryInk,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Surface(
                                                shape = RoundedCornerShape(DressKitStyle.chipCorner()),
                                                color = DressKitStyle.cardSurface(),
                                                border = BorderStroke(1.dp, DressKitStyle.borderColor()),
                                                modifier = Modifier.clickable {
                                                    openSearchUrl(StoreSearchLinks.googleShoppingUrl(genderPrefix, piece))
                                                }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.Search,
                                                        contentDescription = null,
                                                        tint = DressKitStyle.accentColor(),
                                                        modifier = Modifier.size(13.dp)
                                                    )
                                                    Text(
                                                        text = "Google",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = primaryInk
                                                    )
                                                }
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(DressKitStyle.chipCorner()),
                                                color = DressKitStyle.accentSoft(),
                                                modifier = Modifier.clickable {
                                                    openSearchUrl(StoreSearchLinks.trendyolUrl(genderPrefix, piece))
                                                }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.ShoppingBag,
                                                        contentDescription = null,
                                                        tint = DressKitStyle.accentColor(),
                                                        modifier = Modifier.size(13.dp)
                                                    )
                                                    Text(
                                                        text = "Trendyol",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = DressKitStyle.accentColor()
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            val activePieceQuery = pieces.getOrElse(selectedStorePieceIdx) {
                                pieces.firstOrNull() ?: lookTitle
                            }
                            Text(
                                text = localizedText(
                                    lang,
                                    "Mağazalarda Benzer Ürün Ara ($activePieceQuery)",
                                    "Search Stores ($activePieceQuery)",
                                    "In Shops suchen ($activePieceQuery)",
                                    "Chercher en boutique ($activePieceQuery)",
                                    "Buscar en tiendas ($activePieceQuery)",
                                    "在商店搜索相似单品 ($activePieceQuery)"
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = secondaryInk
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                StoreSearchLinks.buildStoreSearchLinks(
                                    genderPrefix = genderPrefix,
                                    rawQuery = activePieceQuery,
                                    googleLabel = "Google Alışveriş"
                                ).forEach { (storeLabel, storeUrl) ->
                                    Surface(
                                        shape = RoundedCornerShape(DressKitStyle.chipCorner()),
                                        color = DressKitStyle.buttonBg(),
                                        modifier = Modifier.clickable { openSearchUrl(storeUrl) }
                                    ) {
                                        Text(
                                            text = storeLabel,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = DressKitStyle.buttonFg(),
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LookbookSwipeDiscoveryDialog(
    selectedGender: LookbookGender,
    visitNonce: Int = 1,
    onGenderChange: (LookbookGender) -> Unit,
    likedLookIds: Set<String>,
    dislikedLookIds: Set<String>,
    onRecordLookSwipe: (String, Boolean?) -> Unit,
    onInspectLook: (StreetLookItem) -> Unit,
    onDismiss: () -> Unit
) {
    val lang = LocalAppLanguage.current
    val strings = appStrings()
    val primaryInk = DressKitStyle.primaryInk()
    val secondaryInk = DressKitStyle.secondaryInk()

    var activeTab by rememberSaveable { mutableIntStateOf(0) } // 0=Explore, 1=Liked, 2=Disliked
    var deckVersion by remember { mutableIntStateOf(0) }
    val allLooks = remember { VERIFIED_STREET_LOOKS + VERIFIED_WOMEN_STREET_LOOKS }
    val basePool = remember(selectedGender) {
        if (selectedGender == LookbookGender.WOMEN) VERIFIED_WOMEN_STREET_LOOKS else VERIFIED_STREET_LOOKS
    }
    val sessionDeck = remember(selectedGender, deckVersion, visitNonce) {
        val unseen = basePool.filter { it.id !in likedLookIds && it.id !in dislikedLookIds }
        val seed = System.nanoTime() xor ((visitNonce.toLong() + deckVersion.toLong() + 1L) * 0x5DEECE66DL)
        unseen.shuffled(kotlin.random.Random(seed))
    }
    var currentIndex by remember(selectedGender, deckVersion, visitNonce) { mutableIntStateOf(0) }
    val undoStack = remember(selectedGender, deckVersion, visitNonce) { mutableStateListOf<Pair<String, Boolean>>() }

    val likedLooks = remember(likedLookIds) {
        allLooks.filter { it.id in likedLookIds }
    }
    val dislikedLooks = remember(dislikedLookIds) {
        allLooks.filter { it.id in dislikedLookIds }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DressKitStyle.canvasBg())
                .systemBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = localizedText(
                                lang,
                                "✨ Stil Keşfi",
                                "✨ Style Discovery",
                                "✨ Style Discovery",
                                "✨ Découverte Style",
                                "✨ Descubre Estilo",
                                "✨ 风格发现"
                            ),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = primaryInk
                        )
                        Text(
                            text = localizedText(
                                lang,
                                "Sağa kaydır beğen ❤️ · Sola kaydır geç ✕",
                                "Swipe right to like ❤️ · Left to pass ✕",
                                "Rechts wischen: Like ❤️ · Links: Nein ✕",
                                "Droite : J'aime ❤️ · Gauche : Passer ✕",
                                "Derecha: Me gusta ❤️ · Izquierda: Pasar ✕",
                                "右滑喜欢 ❤️ · 左滑跳过 ✕"
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = secondaryInk,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, contentDescription = strings.close, tint = primaryInk)
                    }
                }

                // Gender quick switcher inside Discovery
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LookbookGender.entries.forEach { gender ->
                        FilterChipPill(
                            label = gender.label(lang),
                            selected = selectedGender == gender,
                            onClick = { onGenderChange(gender) }
                        )
                    }
                }

                // 3 Tabs: Explore / Liked / Passed
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChipPill(
                            label = localizedText(lang, "Keşfet (${(sessionDeck.size - currentIndex).coerceAtLeast(0)})", "Discover (${(sessionDeck.size - currentIndex).coerceAtLeast(0)})", "Entdecken (${(sessionDeck.size - currentIndex).coerceAtLeast(0)})", "Découvrir (${(sessionDeck.size - currentIndex).coerceAtLeast(0)})", "Explorar (${(sessionDeck.size - currentIndex).coerceAtLeast(0)})", "发现 (${(sessionDeck.size - currentIndex).coerceAtLeast(0)})"),
                            selected = activeTab == 0,
                            onClick = { activeTab = 0 }
                        )
                    }
                    item {
                        FilterChipPill(
                            label = "❤️ ${localizedText(lang, "Beğendiklerim", "Liked", "Favoriten", "Aimés", "Me gusta", "喜欢")} (${likedLooks.size})",
                            selected = activeTab == 1,
                            onClick = { activeTab = 1 }
                        )
                    }
                    item {
                        FilterChipPill(
                            label = "✕ ${localizedText(lang, "Geçtiklerim", "Passed", "Übersprungen", "Passés", "Pasados", "跳过")} (${dislikedLooks.size})",
                            selected = activeTab == 2,
                            onClick = { activeTab = 2 }
                        )
                    }
                }

                when (activeTab) {
                    0 -> {
                        val activeLook = sessionDeck.getOrNull(currentIndex)
                        if (activeLook == null) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                shape = RoundedCornerShape(DressKitStyle.cardCorner()),
                                color = DressKitStyle.cardSurface(),
                                border = BorderStroke(DressKitStyle.borderWidth(), DressKitStyle.borderColor())
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(24.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.AutoAwesome,
                                        contentDescription = null,
                                        tint = DressKitStyle.accentColor(),
                                        modifier = Modifier.size(40.dp)
                                    )
                                    Text(
                                        text = localizedText(
                                            lang,
                                            "Bu kategorideki tüm kombinleri keşfettin!",
                                            "You've swiped through every look here!",
                                            "Du hast alle Looks in dieser Kategorie gesehen!",
                                            "Vous avez découvert tous ces looks !",
                                            "¡Ya viste todos los looks de esta sección!",
                                            "已浏览完当前分类的所有穿搭！"
                                        ),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = primaryInk
                                    )
                                    Text(
                                        text = localizedText(
                                            lang,
                                            "Beğendiklerini görebilir ya da listeyi sıfırlayıp yeniden karıştırabilirsin.",
                                            "View your liked outfits or reset & shuffle to discover again.",
                                            "Sieh dir deine Favoriten an oder mische die Karten neu.",
                                            "Consultez vos coups de cœur ou réinitialisez les cartes.",
                                            "Mira tus favoritos o reinicia las tarjetas.",
                                            "可查看已喜欢的穿搭，或重置列表重新随机探索。"
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = secondaryInk
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        OutlinedButton(
                                            onClick = { activeTab = 1 },
                                            shape = RoundedCornerShape(DressKitStyle.buttonCorner())
                                        ) {
                                            Text("❤️ ${likedLooks.size}")
                                        }
                                        Button(
                                            onClick = {
                                                basePool.forEach { onRecordLookSwipe(it.id, null) }
                                                deckVersion++
                                            },
                                            shape = RoundedCornerShape(DressKitStyle.buttonCorner()),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = DressKitStyle.buttonBg(),
                                                contentColor = DressKitStyle.buttonFg()
                                            )
                                        ) {
                                            Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(localizedText(lang, "Yeniden Karıştır", "Shuffle Again", "Neu mischen", "Remélanger", "Reintentar", "重新洗牌"))
                                        }
                                    }
                                }
                            }
                        } else {
                            val title = if (lang == AppLanguage.TR) activeLook.trTitle else activeLook.enTitle
                            val palette = if (lang == AppLanguage.TR) activeLook.trPalette else activeLook.enPalette
                            var dragX by remember(activeLook.id) { mutableFloatStateOf(0f) }
                            val animatedX by animateFloatAsState(targetValue = dragX, label = "cardSwipeX")
                            val swipeThresholdPx = androidx.compose.ui.platform.LocalDensity.current.run { 95.dp.toPx() }

                            fun swipeCurrent(liked: Boolean) {
                                undoStack.add(activeLook.id to liked)
                                onRecordLookSwipe(activeLook.id, liked)
                                dragX = 0f
                                currentIndex++
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .offset { IntOffset(animatedX.roundToInt(), 0) }
                                        .graphicsLayer {
                                            rotationZ = (animatedX / 30f).coerceIn(-12f, 12f)
                                        }
                                        .pointerInput(activeLook.id) {
                                            detectHorizontalDragGestures(
                                                onDragEnd = {
                                                    when {
                                                        dragX > swipeThresholdPx -> swipeCurrent(true)
                                                        dragX < -swipeThresholdPx -> swipeCurrent(false)
                                                        else -> dragX = 0f
                                                    }
                                                },
                                                onDragCancel = { dragX = 0f }
                                            ) { change, dragAmount ->
                                                change.consume()
                                                dragX += dragAmount
                                            }
                                        }
                                        .clickable { onInspectLook(activeLook) },
                                    shape = RoundedCornerShape(DressKitStyle.cardCorner()),
                                    color = DressKitStyle.cardSurface(),
                                    border = BorderStroke(DressKitStyle.borderWidth(), DressKitStyle.borderColor()),
                                    shadowElevation = 4.dp
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        AsyncImage(
                                            model = activeLook.imageUrl,
                                            contentDescription = title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )

                                        if (animatedX > 36f) {
                                            Surface(
                                                modifier = Modifier
                                                    .align(Alignment.TopStart)
                                                    .padding(18.dp),
                                                shape = RoundedCornerShape(10.dp),
                                                color = Color(0xFF16A34A).copy(alpha = 0.92f)
                                            ) {
                                                Text(
                                                    text = localizedText(lang, "BEĞENDİM ❤️", "LIKED ❤️", "GEFÄLLT ❤️", "J'AIME ❤️", "ME GUSTA ❤️", "喜欢 ❤️"),
                                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = Color.White,
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                                )
                                            }
                                        } else if (animatedX < -36f) {
                                            Surface(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(18.dp),
                                                shape = RoundedCornerShape(10.dp),
                                                color = Color(0xFFDC2626).copy(alpha = 0.92f)
                                            ) {
                                                Text(
                                                    text = localizedText(lang, "GEÇ ✕", "PASS ✕", "NEIN ✕", "PASSER ✕", "PASAR ✕", "跳过 ✕"),
                                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = Color.White,
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                                )
                                            }
                                        }

                                        // Bottom overlay info strip
                                        Surface(
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            shape = RoundedCornerShape(14.dp),
                                            color = DressKitStyle.cardSurface().copy(alpha = 0.90f),
                                            border = BorderStroke(1.dp, DressKitStyle.borderColor())
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(12.dp),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = activeLook.category.label(lang),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = DressKitStyle.accentColor()
                                                    )
                                                    HarmonyScoreBadge(score = activeLook.harmonyPercent)
                                                }
                                                Text(
                                                    text = title,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = primaryInk,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = palette,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = secondaryInk,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Explicit Action Buttons: Pass / Undo / Like
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = { swipeCurrent(false) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(999.dp),
                                    border = BorderStroke(1.dp, Color(0xFFDC2626).copy(alpha = 0.55f)),
                                    contentPadding = PaddingValues(vertical = 12.dp)
                                ) {
                                    Text(
                                        text = "✕ ${localizedText(lang, "Geç", "Pass", "Nein", "Passer", "Pasar", "跳过")}",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = Color(0xFFDC2626),
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        if (currentIndex > 0 && undoStack.isNotEmpty()) {
                                            val (prevId, _) = undoStack.removeAt(undoStack.lastIndex)
                                            onRecordLookSwipe(prevId, null)
                                            currentIndex--
                                        }
                                    },
                                    enabled = currentIndex > 0 && undoStack.isNotEmpty()
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Refresh,
                                        contentDescription = localizedText(lang, "Geri Al", "Undo", "Zurück", "Annuler", "Deshacer", "撤销"),
                                        tint = if (currentIndex > 0 && undoStack.isNotEmpty()) primaryInk else secondaryInk.copy(alpha = 0.4f)
                                    )
                                }
                                Button(
                                    onClick = { swipeCurrent(true) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(999.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF16A34A),
                                        contentColor = Color.White
                                    ),
                                    contentPadding = PaddingValues(vertical = 12.dp)
                                ) {
                                    Text(
                                        text = "❤️ ${localizedText(lang, "Beğen", "Like", "Like", "J'aime", "Gustar", "喜欢")}",
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }

                    1 -> {
                        SwipeHistoryList(
                            looks = likedLooks,
                            isLikedList = true,
                            lang = lang,
                            primaryInk = primaryInk,
                            secondaryInk = secondaryInk,
                            onInspect = onInspectLook,
                            onToggle = { id, toLiked -> onRecordLookSwipe(id, toLiked) },
                            emptyMessage = localizedText(
                                lang,
                                "Henüz beğendiğin kombin yok. Keşfet'ten sağa kaydır!",
                                "No liked looks yet. Swipe right in Discover!",
                                "Noch keine Favoriten. Wische nach rechts!",
                                "Aucun look aimé. Glissez à droite !",
                                "Aún sin favoritos. ¡Desliza a la derecha!",
                                "暂无喜欢的穿搭，去“发现”右滑收藏吧！"
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    else -> {
                        SwipeHistoryList(
                            looks = dislikedLooks,
                            isLikedList = false,
                            lang = lang,
                            primaryInk = primaryInk,
                            secondaryInk = secondaryInk,
                            onInspect = onInspectLook,
                            onToggle = { id, toLiked -> onRecordLookSwipe(id, toLiked) },
                            emptyMessage = localizedText(
                                lang,
                                "Geçtiğin kombin bulunmuyor.",
                                "No passed looks.",
                                "Keine übersprungenen Looks.",
                                "Aucun look passé.",
                                "Sin looks pasados.",
                                "暂无跳过的穿搭。"
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SwipeHistoryList(
    looks: List<StreetLookItem>,
    isLikedList: Boolean,
    lang: AppLanguage,
    primaryInk: Color,
    secondaryInk: Color,
    onInspect: (StreetLookItem) -> Unit,
    onToggle: (String, Boolean?) -> Unit,
    emptyMessage: String,
    modifier: Modifier = Modifier
) {
    if (looks.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                text = emptyMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = secondaryInk
            )
        }
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(looks, key = { it.id }) { look ->
            val title = if (lang == AppLanguage.TR) look.trTitle else look.enTitle
            val palette = if (lang == AppLanguage.TR) look.trPalette else look.enPalette
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onInspect(look) },
                shape = RoundedCornerShape(14.dp),
                color = DressKitStyle.cardSurface(),
                border = BorderStroke(DressKitStyle.borderWidth(), DressKitStyle.borderColor())
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = look.imageUrl,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(72.dp, 92.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = primaryInk,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = palette,
                            style = MaterialTheme.typography.labelSmall,
                            color = secondaryInk,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = { onToggle(look.id, !isLikedList) },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (isLikedList) {
                                        "✕ ${localizedText(lang, "Çıkar", "Move to Pass", "Entfernen", "Retirer", "Quitar", "移除")}"
                                    } else {
                                        "❤️ ${localizedText(lang, "Beğen", "Move to Liked", "Gefällt", "Aimer", "Gustar", "移至喜欢")}"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = DressKitStyle.accentColor()
                                )
                            }
                            TextButton(
                                onClick = { onToggle(look.id, null) },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = localizedText(lang, "Sıfırla", "Reset", "Reset", "Reset", "Reset", "重置"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = secondaryInk
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

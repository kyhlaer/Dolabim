package com.dresskit.wardrobe.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.dresskit.wardrobe.model.AppLanguage
import com.dresskit.wardrobe.ui.components.ColorDot
import com.dresskit.wardrobe.ui.components.FilterChipPill
import com.dresskit.wardrobe.ui.components.HarmonyScoreBadge
import com.dresskit.wardrobe.ui.components.bouncyOverscroll
import com.dresskit.wardrobe.ui.theme.DressKitStyle
import com.dresskit.wardrobe.ui.theme.LocalAppLanguage
import com.dresskit.wardrobe.ui.theme.appStrings

enum class InspirationCategory(val trLabel: String, val enLabel: String) {
    ALL("Tümü", "All"),
    OVERSIZE("Oversize & Hoodie", "Oversize & Hoodie"),
    LAYERING("Katman & Ceket", "Layering & Jackets"),
    DENIM_CARGO("Denim & Kargo", "Denim & Cargo"),
    TRACK_SPORT("Tracksuit & Sport", "Tracksuit & Sport"),
    SMART_STREET("Smart Street", "Smart Street");

    fun label(lang: AppLanguage): String = if (lang == AppLanguage.EN) enLabel else trLabel
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
    )
)

@Composable
fun InspirationScreen(
    onOpenSettings: () -> Unit,
    listState: LazyListState = rememberLazyListState()
) {
    val lang = LocalAppLanguage.current
    val strings = appStrings()
    val primaryInk = DressKitStyle.primaryInk()
    val secondaryInk = DressKitStyle.secondaryInk()

    var selectedCategory by rememberSaveable { mutableStateOf(InspirationCategory.ALL) }
    var zoomedLook by remember { mutableStateOf<StreetLookItem?>(null) }

    val filtered = remember(selectedCategory) {
        if (selectedCategory == InspirationCategory.ALL) {
            VERIFIED_STREET_LOOKS
        } else {
            VERIFIED_STREET_LOOKS.filter { it.category == selectedCategory }
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
                        text = if (lang == AppLanguage.EN) "Street Lookbook" else "Kombin İlhamı",
                        style = MaterialTheme.typography.displaySmall,
                        color = primaryInk
                    )
                    Text(
                        text = "${strings.inspirationSubtitle} · ${filtered.size}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = secondaryInk
                    )
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = strings.settingsTitle,
                        tint = primaryInk
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
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
                                    contentDescription = if (lang == AppLanguage.EN) look.enTitle else look.trTitle,
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
                                        text = if (lang == AppLanguage.EN) look.enTitle else look.trTitle,
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
                                    text = if (lang == AppLanguage.EN) look.enPalette else look.trPalette,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = DressKitStyle.accentColor()
                                )

                                val pieces = if (lang == AppLanguage.EN) look.enPieces else look.trPieces
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

    zoomedLook?.let { look ->
        Dialog(
            onDismissRequest = { zoomedLook = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.92f))
                    .clickable { zoomedLook = null },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AsyncImage(
                        model = look.imageUrl,
                        contentDescription = if (lang == AppLanguage.EN) look.enTitle else look.trTitle,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(440.dp)
                            .clip(RoundedCornerShape(DressKitStyle.cardCorner()))
                    )
                    Text(
                        text = if (lang == AppLanguage.EN) look.enTitle else look.trTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    Text(
                        text = (if (lang == AppLanguage.EN) look.enPieces else look.trPieces).joinToString("  •  "),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.78f)
                    )
                }

                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(18.dp),
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.16f)
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
        }
    }
}

package com.dresskit.wardrobe.data

import android.content.Context
import com.dresskit.wardrobe.model.ClothingCategory
import com.dresskit.wardrobe.model.ClothingItem
import com.dresskit.wardrobe.model.ClothingSubCategory
import com.dresskit.wardrobe.model.GarmentColor
import com.dresskit.wardrobe.model.Outfit
import com.dresskit.wardrobe.model.Season
import com.dresskit.wardrobe.model.StyleTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class WardrobeRepository(private val context: Context) {
    private val dataFile = File(context.filesDir, "wardrobe_data.json")
    private val mutex = Mutex()

    private val _items = MutableStateFlow<List<ClothingItem>>(emptyList())
    val items: StateFlow<List<ClothingItem>> = _items.asStateFlow()

    private val _outfits = MutableStateFlow<List<Outfit>>(emptyList())
    val outfits: StateFlow<List<Outfit>> = _outfits.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!dataFile.exists()) return@withLock
            try {
                val root = JSONObject(dataFile.readText())
                val itemsArray = root.optJSONArray("items") ?: JSONArray()
                val loadedItems = buildList {
                    for (i in 0 until itemsArray.length()) {
                        parseItem(itemsArray.getJSONObject(i))?.let { add(it) }
                    }
                }
                val outfitsArray = root.optJSONArray("outfits") ?: JSONArray()
                val loadedOutfits = buildList {
                    for (i in 0 until outfitsArray.length()) {
                        parseOutfit(outfitsArray.getJSONObject(i))?.let { add(it) }
                    }
                }
                _items.value = loadedItems
                _outfits.value = loadedOutfits
            } catch (_: Exception) {
                // Keep existing state if file was unreadable
            }
        }
    }

    suspend fun addItem(item: ClothingItem) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val updated = listOf(item) + _items.value
            _items.value = updated
            persistLocked(updated, _outfits.value)
        }
    }

    suspend fun updateItem(item: ClothingItem) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val updated = _items.value.map { if (it.id == item.id) item else it }
            _items.value = updated
            persistLocked(updated, _outfits.value)
        }
    }

    suspend fun addPhotoPairsToItem(itemId: String, pairs: List<SavedPhotoPair>) =
        withContext(Dispatchers.IO) {
            if (pairs.isEmpty()) return@withContext
            mutex.withLock {
                val updated = _items.value.map { item ->
                    if (item.id == itemId) {
                        val syncedOriginals = if (item.originalPhotoFileNames.size == item.photoFileNames.size) {
                            item.originalPhotoFileNames + pairs.map { it.originalFileName }
                        } else {
                            item.photoFileNames + pairs.map { it.originalFileName }
                        }
                        item.copy(
                            photoFileNames = item.photoFileNames + pairs.map { it.studioFileName },
                            originalPhotoFileNames = syncedOriginals
                        )
                    } else {
                        item
                    }
                }
                _items.value = updated
                persistLocked(updated, _outfits.value)
            }
        }

    suspend fun removePhotoAtIndex(itemId: String, index: Int) =
        withContext(Dispatchers.IO) {
            val filesToDelete = mutableListOf<String>()
            mutex.withLock {
                val updated = _items.value.map { item ->
                    if (item.id == itemId && index in item.photoFileNames.indices && item.photoFileNames.size > 1) {
                        filesToDelete.add(item.photoFileNames[index])
                        val newStudio = item.photoFileNames.filterIndexed { i, _ -> i != index }
                        val newOrig = if (item.originalPhotoFileNames.size == item.photoFileNames.size) {
                            filesToDelete.add(item.originalPhotoFileNames[index])
                            item.originalPhotoFileNames.filterIndexed { i, _ -> i != index }
                        } else {
                            item.originalPhotoFileNames
                        }
                        item.copy(photoFileNames = newStudio, originalPhotoFileNames = newOrig)
                    } else {
                        item
                    }
                }
                _items.value = updated
                persistLocked(updated, _outfits.value)
            }
            filesToDelete.forEach { ImageStorage.deletePhoto(context, it) }
        }

    suspend fun setCoverPhotoAtIndex(itemId: String, index: Int) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val updated = _items.value.map { item ->
                    if (item.id == itemId && index in item.photoFileNames.indices && index > 0) {
                        val newStudio = listOf(item.photoFileNames[index]) +
                            item.photoFileNames.filterIndexed { i, _ -> i != index }
                        val newOrig = if (item.originalPhotoFileNames.size == item.photoFileNames.size) {
                            listOf(item.originalPhotoFileNames[index]) +
                                item.originalPhotoFileNames.filterIndexed { i, _ -> i != index }
                        } else {
                            item.originalPhotoFileNames
                        }
                        item.copy(photoFileNames = newStudio, originalPhotoFileNames = newOrig)
                    } else {
                        item
                    }
                }
                _items.value = updated
                persistLocked(updated, _outfits.value)
            }
        }

    suspend fun setUseOriginalPhotos(itemId: String, useOriginal: Boolean) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val updated = _items.value.map { item ->
                    if (item.id == itemId) item.copy(useOriginalPhotos = useOriginal) else item
                }
                _items.value = updated
                persistLocked(updated, _outfits.value)
            }
        }

    suspend fun toggleWishlist(itemId: String, moveToWardrobe: Boolean) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val updated = _items.value.map { item ->
                if (item.id == itemId) item.copy(isWishlist = !moveToWardrobe) else item
            }
            _items.value = updated
            persistLocked(updated, _outfits.value)
        }
    }

    suspend fun toggleFavorite(itemId: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val updated = _items.value.map { item ->
                if (item.id == itemId) item.copy(isFavorite = !item.isFavorite) else item
            }
            _items.value = updated
            persistLocked(updated, _outfits.value)
        }
    }

    suspend fun markItemsWorn(itemIds: List<String>) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        mutex.withLock {
            val idSet = itemIds.toSet()
            val updated = _items.value.map { item ->
                if (item.id in idSet) {
                    item.copy(wearCount = item.wearCount + 1, lastWornAt = now)
                } else {
                    item
                }
            }
            _items.value = updated
            persistLocked(updated, _outfits.value)
        }
    }

    suspend fun deleteItem(itemId: String) = withContext(Dispatchers.IO) {
        val filesToDelete = mutableListOf<String>()
        mutex.withLock {
            val target = _items.value.find { it.id == itemId }
            target?.photoFileNames?.let { filesToDelete.addAll(it) }
            target?.originalPhotoFileNames?.let { filesToDelete.addAll(it) }

            val updatedItems = _items.value.filterNot { it.id == itemId }
            val updatedOutfits = _outfits.value
                .map { it.copy(itemIds = it.itemIds.filterNot { id -> id == itemId }) }
                .filter { it.itemIds.isNotEmpty() }
            _items.value = updatedItems
            _outfits.value = updatedOutfits
            persistLocked(updatedItems, updatedOutfits)
        }
        filesToDelete.distinct().forEach { ImageStorage.deletePhoto(context, it) }
    }

    suspend fun addOutfit(outfit: Outfit) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val updated = listOf(outfit) + _outfits.value
            _outfits.value = updated
            persistLocked(_items.value, updated)
        }
    }

    suspend fun updateOutfit(outfit: Outfit) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val updated = _outfits.value.map { if (it.id == outfit.id) outfit else it }
            _outfits.value = updated
            persistLocked(_items.value, updated)
        }
    }

    suspend fun deleteOutfit(outfitId: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val updated = _outfits.value.filterNot { it.id == outfitId }
            _outfits.value = updated
            persistLocked(_items.value, updated)
        }
    }

    fun generateId(): String = UUID.randomUUID().toString()

    private fun persistLocked(items: List<ClothingItem>, outfits: List<Outfit>) {
        val root = JSONObject().apply {
            put("items", JSONArray().apply { items.forEach { put(serializeItem(it)) } })
            put("outfits", JSONArray().apply { outfits.forEach { put(serializeOutfit(it)) } })
        }
        val tmpFile = File(context.filesDir, "wardrobe_data.json.tmp")
        tmpFile.writeText(root.toString())
        if (!tmpFile.renameTo(dataFile)) {
            dataFile.writeText(root.toString())
            tmpFile.delete()
        }
        runCatching {
            dataFile.setReadable(false, false)
            dataFile.setReadable(true, true)
            dataFile.setWritable(false, false)
            dataFile.setWritable(true, true)
        }
    }

    private fun serializeItem(item: ClothingItem): JSONObject = JSONObject().apply {
        put("id", item.id.take(64))
        put("name", item.name.take(120))
        put("category", item.category.name)
        put("subCategory", item.subCategory.name)
        put("styleTag", item.styleTag.name)
        put("seasons", JSONArray().apply { item.seasons.forEach { put(it.name) } })
        put("colors", JSONArray().apply { item.colors.forEach { put(it.name) } })
        put("extractedHexes", JSONArray().apply { item.extractedHexes.forEach { put(it) } })
        put("photoFileNames", JSONArray().apply {
            item.photoFileNames.mapNotNull { ImageStorage.sanitizeFileName(it) }.forEach { put(it) }
        })
        put("originalPhotoFileNames", JSONArray().apply {
            item.originalPhotoFileNames.mapNotNull { ImageStorage.sanitizeFileName(it) }.forEach { put(it) }
        })
        put("useOriginalPhotos", item.useOriginalPhotos)
        put("notes", item.notes.take(500))
        put("isFavorite", item.isFavorite)
        put("isWishlist", item.isWishlist)
        put("priceText", item.priceText.take(40))
        put("productUrl", item.productUrl.take(500))
        put("lastCheckedPrice", item.lastCheckedPrice.coerceAtLeast(0.0))
        put("previousPrice", item.previousPrice.coerceAtLeast(0.0))
        put("priceDropped", item.priceDropped)
        put("wearCount", item.wearCount.coerceAtLeast(0))
        put("lastWornAt", item.lastWornAt.coerceAtLeast(0L))
        put("createdAt", item.createdAt.coerceAtLeast(0L))
    }

    private fun parseItem(obj: JSONObject): ClothingItem? {
        val id = obj.optString("id").takeIf { it.isNotBlank() } ?: return null
        val category = runCatching {
            ClothingCategory.valueOf(obj.optString("category"))
        }.getOrDefault(ClothingCategory.TOP)

        val subCategory = runCatching {
            ClothingSubCategory.valueOf(obj.optString("subCategory"))
        }.getOrElse {
            ClothingSubCategory.forCategory(category).firstOrNull() ?: ClothingSubCategory.TSHIRT
        }

        val styleTag = runCatching {
            StyleTag.valueOf(obj.optString("styleTag"))
        }.getOrDefault(StyleTag.CASUAL)

        val seasonsArr = obj.optJSONArray("seasons") ?: JSONArray()
        val seasons = buildList {
            for (i in 0 until seasonsArr.length()) {
                runCatching { Season.valueOf(seasonsArr.getString(i)) }.getOrNull()?.let { add(it) }
            }
        }.ifEmpty { listOf(Season.ALL) }

        val colorsArr = obj.optJSONArray("colors") ?: JSONArray()
        val colors = buildList {
            for (i in 0 until colorsArr.length()) {
                runCatching { GarmentColor.valueOf(colorsArr.getString(i)) }.getOrNull()?.let { add(it) }
            }
        }

        val hexArr = obj.optJSONArray("extractedHexes") ?: JSONArray()
        val hexes = buildList {
            for (i in 0 until hexArr.length()) {
                add(hexArr.getLong(i))
            }
        }

        val photosArr = obj.optJSONArray("photoFileNames") ?: JSONArray()
        val photos = buildList {
            for (i in 0 until photosArr.length()) {
                ImageStorage.sanitizeFileName(photosArr.optString(i))?.let { add(it) }
            }
        }

        val origPhotosArr = obj.optJSONArray("originalPhotoFileNames") ?: JSONArray()
        val origPhotos = buildList {
            for (i in 0 until origPhotosArr.length()) {
                ImageStorage.sanitizeFileName(origPhotosArr.optString(i))?.let { add(it) }
            }
        }

        return ClothingItem(
            id = id.take(64),
            name = obj.optString("name").take(120),
            category = subCategory.parentCategory,
            subCategory = subCategory,
            styleTag = styleTag,
            seasons = seasons,
            colors = colors,
            extractedHexes = hexes,
            photoFileNames = photos,
            originalPhotoFileNames = origPhotos,
            useOriginalPhotos = obj.optBoolean("useOriginalPhotos", false),
            notes = obj.optString("notes").take(500),
            isFavorite = obj.optBoolean("isFavorite", false),
            isWishlist = obj.optBoolean("isWishlist", false),
            priceText = obj.optString("priceText").take(40),
            productUrl = obj.optString("productUrl").take(500),
            lastCheckedPrice = obj.optDouble("lastCheckedPrice", 0.0).let { if (it.isNaN()) 0.0 else it.coerceAtLeast(0.0) },
            previousPrice = obj.optDouble("previousPrice", 0.0).let { if (it.isNaN()) 0.0 else it.coerceAtLeast(0.0) },
            priceDropped = obj.optBoolean("priceDropped", false),
            wearCount = obj.optInt("wearCount", 0).coerceAtLeast(0),
            lastWornAt = obj.optLong("lastWornAt", 0L).coerceAtLeast(0L),
            createdAt = obj.optLong("createdAt", System.currentTimeMillis()).coerceAtLeast(0L)
        )
    }

    private fun serializeOutfit(outfit: Outfit): JSONObject = JSONObject().apply {
        put("id", outfit.id.take(64))
        put("title", outfit.title.take(120))
        put("itemIds", JSONArray().apply { outfit.itemIds.forEach { put(it.take(64)) } })
        put("season", outfit.season.name)
        put("harmonyScore", outfit.harmonyScore.coerceIn(0, 100))
        put("harmonyNote", outfit.harmonyNote.take(160))
        put("weatherTag", outfit.weatherTag.take(64))
        put("notes", outfit.notes.take(500))
        put("createdAt", outfit.createdAt.coerceAtLeast(0L))
    }

    private fun parseOutfit(obj: JSONObject): Outfit? {
        val id = obj.optString("id").takeIf { it.isNotBlank() } ?: return null
        val idsArr = obj.optJSONArray("itemIds") ?: JSONArray()
        val itemIds = buildList {
            for (i in 0 until idsArr.length()) {
                idsArr.optString(i).takeIf { it.isNotBlank() }?.let { add(it.take(64)) }
            }
        }
        val season = runCatching {
            Season.valueOf(obj.optString("season"))
        }.getOrDefault(Season.ALL)
        return Outfit(
            id = id.take(64),
            title = obj.optString("title").take(120),
            itemIds = itemIds,
            season = season,
            harmonyScore = obj.optInt("harmonyScore", 92).coerceIn(0, 100),
            harmonyNote = obj.optString("harmonyNote").take(160),
            weatherTag = obj.optString("weatherTag").take(64),
            notes = obj.optString("notes").take(500),
            createdAt = obj.optLong("createdAt", System.currentTimeMillis()).coerceAtLeast(0L)
        )
    }
}

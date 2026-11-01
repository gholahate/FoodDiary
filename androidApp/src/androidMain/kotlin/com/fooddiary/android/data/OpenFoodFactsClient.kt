package com.fooddiary.android.data

import com.fooddiary.domain.Macros
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** One product found in Open Food Facts, already converted to what the app needs. */
data class OffProduct(
    val barcode: String,
    /** Name to save in the product base ("Name (Brand)" when a brand is known). */
    val displayName: String,
    /** Package size as printed by OFF, e.g. "400 г" — for display only. */
    val quantityLabel: String?,
    /** Values that OFF doesn't have; they are saved as 0 and the user is warned. */
    val macrosPer100: Macros,
    val missingMacros: List<String>,
    /** Package weight in grams, if known → offered as the measure "Упаковка". */
    val packageGrams: Double?,
    /** Serving weight in grams, if known → offered as the measure "Порция". */
    val servingGrams: Double?,
)

/**
 * Looks products up in Open Food Facts (https://world.openfoodfacts.org), a
 * free open database of packaged food. No account or key is needed; OFF only
 * asks clients to send an identifying User-Agent.
 *
 * Plain HttpURLConnection + org.json (both built into Android), so no extra
 * libraries. Android-only for now; an iOS version would go into shared code
 * behind ProductLookupProvider later.
 */
class OpenFoodFactsClient {

    /**
     * Products with this barcode: empty list = not in OFF.
     * Throws IOException when OFF can't be reached (no internet, server down).
     */
    suspend fun findByBarcode(barcode: String): List<OffProduct> = withContext(Dispatchers.IO) {
        val url = URL("https://world.openfoodfacts.org/api/v2/product/$barcode.json?fields=$FIELDS")
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("User-Agent", "FoodDiary/0.3 (Android; personal food diary)")
            connection.setRequestProperty("Accept", "application/json")

            val status = connection.responseCode
            // OFF answers 404 (with status 0 in the body) for unknown barcodes.
            if (status == HttpURLConnection.HTTP_NOT_FOUND) return@withContext emptyList()
            if (status !in 200..299) throw IOException("Open Food Facts: HTTP $status")

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            if (json.optInt("status", 0) != 1) return@withContext emptyList()
            val product = json.optJSONObject("product") ?: return@withContext emptyList()
            listOf(parseProduct(barcode, product))
        } finally {
            connection.disconnect()
        }
    }

    private fun parseProduct(barcode: String, p: JSONObject): OffProduct {
        val name = listOf("product_name_ru", "product_name", "generic_name_ru", "generic_name")
            .firstNotNullOfOrNull { p.optString(it).trim().takeIf { s -> s.isNotEmpty() } }
            ?: "Без названия ($barcode)"
        val brand = p.optString("brands").split(',').firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
        val displayName = if (brand != null && !name.contains(brand, ignoreCase = true)) "$name ($brand)" else name

        val n = p.optJSONObject("nutriments") ?: JSONObject()
        val kcal = n.number("energy-kcal_100g") ?: n.number("energy_100g")?.let { kJ -> kJ / 4.184 }
        val protein = n.number("proteins_100g")
        val fat = n.number("fat_100g")
        val carb = n.number("carbohydrates_100g")
        val missing = buildList {
            if (kcal == null) add("калории")
            if (protein == null) add("белки")
            if (fat == null) add("жиры")
            if (carb == null) add("углеводы")
        }

        val quantityLabel = p.optString("quantity").trim().takeIf { it.isNotEmpty() }
        val packageGrams = gramsOf(p.number("product_quantity"), p.optString("product_quantity_unit"))
            ?: quantityLabel?.let(::gramsFromText)
        val servingGrams = gramsOf(p.number("serving_quantity"), p.optString("serving_quantity_unit"))
            ?: p.optString("serving_size").takeIf { it.isNotBlank() }?.let(::gramsFromText)

        return OffProduct(
            barcode = barcode,
            displayName = displayName,
            quantityLabel = quantityLabel,
            macrosPer100 = Macros(kcal ?: 0.0, protein ?: 0.0, fat ?: 0.0, carb ?: 0.0),
            missingMacros = missing,
            packageGrams = packageGrams,
            servingGrams = servingGrams,
        )
    }

    /** Only grams are trusted; ml would need the product's density. Empty unit = OFF's default (g). */
    private fun gramsOf(value: Double?, unit: String): Double? {
        if (value == null || value <= 0.0) return null
        return when (unit.trim().lowercase()) {
            "", "g", "г", "гр" -> value
            "kg", "кг" -> value * 1000
            else -> null
        }
    }

    /** "400 г", "0,5 kg", "30g" → grams; anything else (ml, pieces) → null. */
    private fun gramsFromText(text: String): Double? {
        val match = Regex("""(\d+(?:[.,]\d+)?)\s*(кг|kg|гр|г|g)(?!\p{L})""", RegexOption.IGNORE_CASE).find(text)
            ?: return null
        val value = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        val unit = match.groupValues[2].lowercase()
        return if (unit == "кг" || unit == "kg") value * 1000 else value
    }

    private companion object {
        const val FIELDS = "code,product_name,product_name_ru,generic_name,generic_name_ru,brands,quantity," +
            "product_quantity,product_quantity_unit,serving_size,serving_quantity,serving_quantity_unit,nutriments"
    }
}

/** A JSON field that may come as a number or as a numeric string ("12,5" included). */
private fun JSONObject.number(key: String): Double? {
    if (!has(key) || isNull(key)) return null
    return when (val v = opt(key)) {
        is Number -> v.toDouble()
        is String -> v.trim().replace(',', '.').toDoubleOrNull()
        else -> null
    }?.takeIf { !it.isNaN() && it >= 0.0 }
}

/**
 * Pulls a product barcode (EAN-8/13, UPC) out of what the scanner read:
 *  - plain digits from a regular barcode;
 *  - a GS1 code (e.g. the "Честный знак" square code on Russian packages):
 *    "01" + 14-digit GTIN + other data → the GTIN;
 *  - an Open Food Facts link to a product.
 * Returns null if the code doesn't contain a product number (e.g. a QR with
 * a website or plain text).
 */
fun extractProductBarcode(raw: String): String? {
    val text = raw.trim()
        .removePrefix("]d2").removePrefix("]Q3").removePrefix("]C1") // symbology prefixes, if any
        .replace("\u001D", "") // GS1 group separators

    fun normalize(code: String): String =
        // GTIN-14 with a leading zero is just the EAN-13 behind it.
        if (code.length == 14 && code.startsWith("0")) code.drop(1) else code

    if (text.all { it.isDigit() } && text.length in setOf(8, 12, 13, 14)) return normalize(text)
    Regex("""^01(\d{14})""").find(text)?.let { return normalize(it.groupValues[1]) }
    Regex("""openfoodfacts\.org/product/(\d{8,14})""").find(text)?.let { return normalize(it.groupValues[1]) }
    return null
}

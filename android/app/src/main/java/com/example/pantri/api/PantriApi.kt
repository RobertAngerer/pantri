package com.example.pantri.api

import com.example.pantri.BuildConfig
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class Totals(
    val kcal: Double = 0.0,
    val protein_g: Double = 0.0,
    val carbs_g: Double = 0.0,
    val fat_g: Double = 0.0,
    val cost_eur: Double = 0.0
)

data class Goals(
    val kcal: Double = 0.0,
    val protein_g: Double = 0.0,
    val fat_g: Double = 0.0,
    val carbs_g: Double = 0.0
)

data class AppSettings(
    val goal_kcal: Double = 3500.0,
    val goal_protein_g: Double = 200.0,
    val goal_fat_g: Double = 100.0,
    val budget_monthly: Double = 600.0
) {
    val goal_carbs_g: Double
        get() = ((goal_kcal - goal_protein_g * 4 - goal_fat_g * 9) / 4).coerceAtLeast(0.0)
}

data class Remaining(
    val kcal: Double = 0.0,
    val protein_g: Double = 0.0
)

data class FoodItem(
    val name: String = "",
    val quantity: String = "",
    val kcal: Double = 0.0,
    val protein_g: Double = 0.0,
    val carbs_g: Double = 0.0,
    val fat_g: Double = 0.0,
    val cost_eur: Double = 0.0
)

data class Entry(
    val label: String = "",
    val timestamp: String = "",
    val items: List<FoodItem> = emptyList(),
    val totals: Totals = Totals()
)

data class TodayResponse(
    val date: String = "",
    val entries: List<Entry> = emptyList(),
    val day_totals: Totals = Totals(),
    val goals: Goals = Goals(),
    val remaining: Remaining = Remaining()
)

data class DaySummary(
    val date: String = "",
    val day_totals: Totals = Totals(),
    val entry_count: Int = 0
)

data class DayDetail(
    val date: String = "",
    val entries: List<Entry> = emptyList(),
    val day_totals: Totals = Totals(),
    val raw_text: String = ""
)

data class WeightEntry(
    val date: String = "",
    val weight_kg: Double = 0.0
)

data class WeightPostBody(
    val date: String,
    val weight_kg: Double
)

data class FoodInfo(
    val kcal: Int = 0,
    val protein_g: Double = 0.0,
    val carbs_g: Double = 0.0,
    val fat_g: Double = 0.0,
    val cost_eur_per_100g: Double = 0.0,
    val cent_per_g_protein: Double? = null
)

data class OFFNutriments(
    @SerializedName("energy-kcal_100g") val kcal100g: Double? = null,
    val proteins_100g: Double? = null,
    val carbohydrates_100g: Double? = null,
    val fat_100g: Double? = null
)

data class OFFProduct(
    val product_name: String? = null,
    val quantity: String? = null,
    val nutriments: OFFNutriments? = null
)

data class OFFResponse(
    val status: Int = 0,
    val product: OFFProduct? = null
)

data class MealPrepItem(
    val id: Int = 0,
    val food: String = "",
    val initial_g: Double = 0.0,
    val remaining_g: Double = 0.0,
    val created: String = "",
    val active: Boolean = true
)

data class FoodDiaryResult(
    val entries: List<Entry> = emptyList(),
    val day_totals: Totals = Totals()
)

private data class OpenAIMessage(val content: String = "")
private data class OpenAIChoice(val message: OpenAIMessage = OpenAIMessage())
private data class OpenAIResponse(val choices: List<OpenAIChoice> = emptyList())

private data class DayRow(
    val date: String = "",
    val entries: List<Entry> = emptyList(),
    val day_totals: Totals = Totals(),
    val raw_text: String = ""
)

object ApiClient {
    private const val SUPABASE_URL = "https://lhvzpkaekbxkkbnebwqb.supabase.co"
    private const val SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imxodnpwa2Fla2J4a2tibmVid3FiIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzMyMTg2MDIsImV4cCI6MjA4ODc5NDYwMn0.pMGnY80mfz4UAAT-m6OGF51LkgIERrUJ79xxKBT6yEE"
    private var settings = AppSettings()

    fun getSettings(): AppSettings = settings
    fun applySettings(s: AppSettings) { settings = s }

    private val gson = Gson()

    private fun get(path: String): String {
        val url = URL("$SUPABASE_URL/rest/v1/$path")
        val conn = url.openConnection() as HttpURLConnection
        conn.setRequestProperty("apikey", SUPABASE_KEY)
        conn.setRequestProperty("Authorization", "Bearer $SUPABASE_KEY")
        return conn.inputStream.bufferedReader().readText()
    }

    suspend fun getToday(): TodayResponse = withContext(Dispatchers.IO) {
        val today = LocalDate.now().toString()
        val body = get("days?date=eq.$today")
        val rows = gson.fromJson(body, Array<DayRow>::class.java).toList()
        val row = rows.firstOrNull()
        val dt = row?.day_totals ?: Totals()
        val s = settings
        TodayResponse(
            date = today,
            entries = row?.entries ?: emptyList(),
            day_totals = dt,
            goals = Goals(kcal = s.goal_kcal, protein_g = s.goal_protein_g, fat_g = s.goal_fat_g, carbs_g = s.goal_carbs_g),
            remaining = Remaining(
                kcal = s.goal_kcal - dt.kcal,
                protein_g = Math.round((s.goal_protein_g - dt.protein_g) * 10.0) / 10.0
            )
        )
    }

    suspend fun getDays(): List<DaySummary> = withContext(Dispatchers.IO) {
        val body = get("days?select=date,day_totals,entries&order=date.desc")
        val rows = gson.fromJson(body, Array<DayRow>::class.java).toList()
        rows.map { DaySummary(date = it.date, day_totals = it.day_totals, entry_count = it.entries.size) }
    }

    suspend fun getDaysDetailed(): List<DayDetail> = withContext(Dispatchers.IO) {
        val body = get("days?select=date,entries,day_totals,raw_text&order=date.desc")
        val rows = gson.fromJson(body, Array<DayRow>::class.java).toList()
        rows.map { DayDetail(date = it.date, entries = it.entries, day_totals = it.day_totals, raw_text = it.raw_text) }
    }

    suspend fun getDay(day: String): DayDetail = withContext(Dispatchers.IO) {
        val body = get("days?date=eq.$day")
        val rows = gson.fromJson(body, Array<DayRow>::class.java).toList()
        val row = rows.firstOrNull()
        DayDetail(
            date = day,
            entries = row?.entries ?: emptyList(),
            day_totals = row?.day_totals ?: Totals(),
            raw_text = row?.raw_text ?: ""
        )
    }

    suspend fun getWeight(): List<WeightEntry> = withContext(Dispatchers.IO) {
        val body = get("weight?order=date")
        gson.fromJson(body, Array<WeightEntry>::class.java).toList()
    }

    suspend fun loadFoods(): Map<String, FoodInfo> = withContext(Dispatchers.IO) {
        val url = URL("$SUPABASE_URL/storage/v1/object/public/pantri/foods.json")
        val conn = url.openConnection() as HttpURLConnection
        if (conn.responseCode >= 400) {
            val err = conn.errorStream?.bufferedReader()?.readText() ?: "HTTP ${conn.responseCode}"
            throw Exception("Failed to load foods: $err")
        }
        val body = conn.inputStream.bufferedReader().readText()
        val type = object : TypeToken<Map<String, FoodInfo>>() {}.type
        gson.fromJson(body, type)
    }

    suspend fun saveFoods(foods: Map<String, FoodInfo>) = withContext(Dispatchers.IO) {
        val url = URL("$SUPABASE_URL/storage/v1/object/pantri/foods.json")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("apikey", SUPABASE_KEY)
        conn.setRequestProperty("Authorization", "Bearer $SUPABASE_KEY")
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("x-upsert", "true")
        conn.doOutput = true
        conn.outputStream.write(gson.toJson(foods).toByteArray())
        if (conn.responseCode >= 400) {
            val err = conn.errorStream?.bufferedReader()?.readText() ?: "HTTP ${conn.responseCode}"
            throw Exception("Failed to save foods: $err")
        }
    }

    suspend fun loadSettings(): AppSettings = withContext(Dispatchers.IO) {
        try {
            val url = URL("$SUPABASE_URL/storage/v1/object/public/pantri/settings.json")
            val conn = url.openConnection() as HttpURLConnection
            if (conn.responseCode >= 400) return@withContext AppSettings()
            val body = conn.inputStream.bufferedReader().readText()
            val s = gson.fromJson(body, AppSettings::class.java) ?: AppSettings()
            settings = s
            s
        } catch (_: Exception) { AppSettings() }
    }

    suspend fun saveSettings(s: AppSettings) = withContext(Dispatchers.IO) {
        settings = s
        val url = URL("$SUPABASE_URL/storage/v1/object/pantri/settings.json")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("apikey", SUPABASE_KEY)
        conn.setRequestProperty("Authorization", "Bearer $SUPABASE_KEY")
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("x-upsert", "true")
        conn.doOutput = true
        conn.outputStream.write(gson.toJson(s).toByteArray())
        if (conn.responseCode >= 400) {
            val err = conn.errorStream?.bufferedReader()?.readText() ?: "HTTP ${conn.responseCode}"
            throw Exception("Failed to save settings: $err")
        }
    }

    suspend fun postWeight(date: String, weightKg: Double) = withContext(Dispatchers.IO) {
        val url = URL("$SUPABASE_URL/rest/v1/weight")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("apikey", SUPABASE_KEY)
        conn.setRequestProperty("Authorization", "Bearer $SUPABASE_KEY")
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Prefer", "resolution=merge-duplicates")
        conn.doOutput = true
        conn.outputStream.write(gson.toJson(WeightPostBody(date, weightKg)).toByteArray())
        if (conn.responseCode >= 400) {
            val err = conn.errorStream?.bufferedReader()?.readText() ?: "HTTP ${conn.responseCode}"
            throw Exception("Failed to save weight: $err")
        }
    }

    suspend fun getMealPreps(): List<MealPrepItem> = withContext(Dispatchers.IO) {
        val body = get("mealpreps?active=eq.true&order=created.asc")
        gson.fromJson(body, Array<MealPrepItem>::class.java).toList()
    }

    suspend fun lookupBarcode(ean: String): OFFResponse? = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://world.openfoodfacts.org/api/v2/product/$ean.json")
            val conn = url.openConnection() as HttpURLConnection
            val body = conn.inputStream.bufferedReader().readText()
            gson.fromJson(body, OFFResponse::class.java)
        } catch (_: Exception) {
            null
        }
    }

    private const val OPENAI_KEY = BuildConfig.OPENAI_KEY

    private const val SYSTEM_PROMPT = """You are a nutritional data assistant. You receive freeform food diary text and return structured nutritional data as JSON. Always respond with ONLY valid JSON, no markdown fences, no commentary.

Rules:
- The input is plain text. It can be in any format: bullet points, comma-separated, one item per line, shorthand like "200g chicken", "chicken 200g", or just "chicken". Be flexible.
- If no quantity is given, assume a typical single serving.
- Estimate calories, macros (protein, carbs, fat in grams), and cost for each item.
- Use realistic average values for common foods.
- For branded or regional items, make your best estimate based on the food category.
- Costs are in EUR, representing typical German supermarket prices.
- A list of known foods with exact nutritional data will be provided. When a food matches or closely matches a known food (e.g. "chicken" -> "chicken-breast", "pb" -> "peanut-butter", "cereal" -> "apfel-zimt-cereal", "topfen" -> "magertopfen"), use the known food's exact values scaled by quantity. Use the known food's name in the output.
- For foods that don't match any known food, estimate as usual.
- If the user groups items (e.g. with headers, blank lines, or labels like "lunch:", "[dinner]"), preserve those groups. Otherwise put everything in a single entry called "entry 1".
- Include a "timestamp" field in each entry. Use the current time provided in the input.

Respond with this exact JSON structure:
{
  "entries": [
    {
      "label": "entry name",
      "timestamp": "HH:MM",
      "items": [
        {
          "name": "food name",
          "quantity": "200g",
          "kcal": 330,
          "protein_g": 62.0,
          "carbs_g": 0.0,
          "fat_g": 7.2,
          "cost_eur": 2.40
        }
      ],
      "totals": {
        "kcal": 330,
        "protein_g": 62.0,
        "carbs_g": 0.0,
        "fat_g": 7.2,
        "cost_eur": 2.40
      }
    }
  ],
  "day_totals": {
    "kcal": 330,
    "protein_g": 62.0,
    "carbs_g": 0.0,
    "fat_g": 7.2,
    "cost_eur": 2.40
  }
}"""

    private data class ParsedItem(
        val foodKey: String,
        val quantityG: Double
    )

    /**
     * Try to parse food text locally when all items are known foods with gram quantities.
     * Returns null if any item can't be resolved, triggering the OpenAI fallback.
     */
    private fun tryParseLocally(text: String, foods: Map<String, FoodInfo>): FoodDiaryResult? {
        if (foods.isEmpty()) return null

        val lines = text.trim().split("\n").map { it.trim() }.filter { it.isNotBlank() }
        val entries = mutableListOf<Entry>()
        var currentLabel: String? = null
        var currentItems = mutableListOf<ParsedItem>()
        val now = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))

        fun flushEntry() {
            if (currentItems.isEmpty()) return
            val label = currentLabel ?: "entry ${entries.size + 1}"
            val foodItems = currentItems.map { item ->
                val info = foods[item.foodKey]!!
                val scale = item.quantityG / 100.0
                FoodItem(
                    name = item.foodKey,
                    quantity = "${item.quantityG.toInt()}g",
                    kcal = Math.round(info.kcal * scale).toDouble(),
                    protein_g = Math.round(info.protein_g * scale * 10) / 10.0,
                    carbs_g = Math.round(info.carbs_g * scale * 10) / 10.0,
                    fat_g = Math.round(info.fat_g * scale * 10) / 10.0,
                    cost_eur = Math.round(info.cost_eur_per_100g * scale * 100) / 100.0
                )
            }
            val totals = Totals(
                kcal = foodItems.sumOf { it.kcal },
                protein_g = Math.round(foodItems.sumOf { it.protein_g } * 10) / 10.0,
                carbs_g = Math.round(foodItems.sumOf { it.carbs_g } * 10) / 10.0,
                fat_g = Math.round(foodItems.sumOf { it.fat_g } * 10) / 10.0,
                cost_eur = Math.round(foodItems.sumOf { it.cost_eur } * 100) / 100.0
            )
            entries.add(Entry(label = label, timestamp = now, items = foodItems, totals = totals))
            currentItems = mutableListOf()
            currentLabel = null
        }

        for (line in lines) {
            // Detect group headers like "lunch:", "[dinner]", "# breakfast"
            val headerMatch = Regex("""^\[(.+)]$|^#\s*(.+)$|^(.+):$""").find(line.trim())
            if (headerMatch != null) {
                flushEntry()
                currentLabel = (headerMatch.groupValues[1].takeIf { it.isNotBlank() }
                    ?: headerMatch.groupValues[2].takeIf { it.isNotBlank() }
                    ?: headerMatch.groupValues[3]).trim()
                continue
            }

            // Split by comma for multiple items on one line
            val parts = line.split(",").map { it.trim() }.filter { it.isNotBlank() }
            for (part in parts) {
                val parsed = parseItemLocal(part, foods) ?: return null
                currentItems.add(parsed)
            }
        }

        flushEntry()
        if (entries.isEmpty()) return null

        val dayTotals = Totals(
            kcal = entries.sumOf { it.totals.kcal },
            protein_g = Math.round(entries.sumOf { it.totals.protein_g } * 10) / 10.0,
            carbs_g = Math.round(entries.sumOf { it.totals.carbs_g } * 10) / 10.0,
            fat_g = Math.round(entries.sumOf { it.totals.fat_g } * 10) / 10.0,
            cost_eur = Math.round(entries.sumOf { it.totals.cost_eur } * 100) / 100.0
        )

        return FoodDiaryResult(entries = entries, day_totals = dayTotals)
    }

    private fun parseItemLocal(text: String, foods: Map<String, FoodInfo>): ParsedItem? {
        val quantityRegex = Regex("""(\d+(?:\.\d+)?)\s*g\b""", RegexOption.IGNORE_CASE)
        val match = quantityRegex.find(text) ?: return null  // no gram quantity → can't resolve locally

        val quantityG = match.groupValues[1].toDouble()
        val foodName = text.removeRange(match.range).trim()
            .trimStart('-', ' ').trimEnd('-', ' ').lowercase()
        if (foodName.isBlank()) return null

        val key = matchKnownFood(foodName, foods) ?: return null
        return ParsedItem(key, quantityG)
    }

    private fun matchKnownFood(name: String, foods: Map<String, FoodInfo>): String? {
        val n = name.lowercase().trim()

        // 1. Exact match
        if (foods.containsKey(n)) return n

        // 2. Dash/space normalization
        val withDashes = n.replace(" ", "-")
        if (foods.containsKey(withDashes)) return withDashes
        val nSpaces = n.replace("-", " ")
        for (key in foods.keys) {
            if (key.replace("-", " ") == nSpaces) return key
        }

        // 3. Substring: input is a substring of a known food or vice versa
        //    Prefer shortest matching key (most specific)
        val candidates = foods.keys.filter { key ->
            val kSpaces = key.replace("-", " ")
            kSpaces.contains(nSpaces) || nSpaces.contains(kSpaces)
        }
        if (candidates.size == 1) return candidates.first()

        // 4. Common aliases
        val aliases = mapOf(
            "chicken" to "chicken-breast",
            "pb" to "peanut-butter",
            "peanutbutter" to "peanut-butter",
            "peanut butter" to "peanut-butter",
            "cereal" to "apfel-zimt-cereal",
            "topfen" to "magertopfen",
            "quark" to "magertopfen",
            "oats" to "haferflocken",
            "oatmeal" to "haferflocken",
            "eggs" to "egg",
            "rice" to "basmati-rice",
            "milk" to "whole-milk",
            "banana" to "banane",
            "bananas" to "banane",
        )
        val alias = aliases[n]
        if (alias != null && foods.containsKey(alias)) return alias

        return null
    }

    suspend fun parseFoodText(text: String): FoodDiaryResult = withContext(Dispatchers.IO) {
        val foods = try { loadFoods() } catch (_: Exception) { emptyMap() }

        // Fast path: if all items are known foods with gram quantities, skip OpenAI
        val localResult = tryParseLocally(text, foods)
        if (localResult != null) return@withContext localResult

        val now = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))

        val userText = buildString {
            append("Current time: $now\n\n")
            if (foods.isNotEmpty()) {
                append("Known foods (per 100g) — use these exact values when a food matches:\n")
                foods.forEach { (name, info) ->
                    append("- $name: ${info.kcal} kcal, ${info.protein_g}P, ${info.carbs_g}C, ${info.fat_g}F, EUR ${"%.2f".format(info.cost_eur_per_100g)}/100g\n")
                }
                append("\n")
            }
            append("Food diary:\n")
            append(text)
        }

        val messages = listOf(
            mapOf("role" to "system", "content" to SYSTEM_PROMPT),
            mapOf("role" to "user", "content" to userText)
        )
        val requestBody = mapOf(
            "model" to "gpt-4o",
            "max_tokens" to 2048,
            "messages" to messages
        )

        val url = URL("https://api.openai.com/v1/chat/completions")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Authorization", "Bearer $OPENAI_KEY")
        conn.setRequestProperty("Content-Type", "application/json")
        conn.connectTimeout = 30000
        conn.readTimeout = 60000
        conn.doOutput = true
        conn.outputStream.write(gson.toJson(requestBody).toByteArray())

        if (conn.responseCode >= 400) {
            val err = conn.errorStream?.bufferedReader()?.readText() ?: "HTTP ${conn.responseCode}"
            throw Exception("OpenAI error: $err")
        }

        val body = conn.inputStream.bufferedReader().readText()
        val response = gson.fromJson(body, OpenAIResponse::class.java)
        var content = response.choices.firstOrNull()?.message?.content ?: throw Exception("Empty response")

        if (content.startsWith("```")) {
            content = content.removePrefix("```json\n").removePrefix("```\n").removeSuffix("\n```").removeSuffix("```")
        }

        gson.fromJson(content, FoodDiaryResult::class.java)
    }

    suspend fun saveDay(date: String, result: FoodDiaryResult, rawText: String) = withContext(Dispatchers.IO) {
        val row = DayRow(
            date = date,
            entries = result.entries,
            day_totals = result.day_totals,
            raw_text = rawText
        )
        val url = URL("$SUPABASE_URL/rest/v1/days")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("apikey", SUPABASE_KEY)
        conn.setRequestProperty("Authorization", "Bearer $SUPABASE_KEY")
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Prefer", "resolution=merge-duplicates")
        conn.doOutput = true
        conn.outputStream.write(gson.toJson(row).toByteArray())
        if (conn.responseCode >= 400) {
            val err = conn.errorStream?.bufferedReader()?.readText() ?: "HTTP ${conn.responseCode}"
            throw Exception("Failed to save: $err")
        }
    }
}

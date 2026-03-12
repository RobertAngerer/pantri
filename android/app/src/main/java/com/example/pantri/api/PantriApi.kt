package com.example.pantri.api

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

data class Totals(
    val kcal: Double = 0.0,
    val protein_g: Double = 0.0,
    val carbs_g: Double = 0.0,
    val fat_g: Double = 0.0,
    val cost_eur: Double = 0.0
)

data class Goals(
    val kcal: Double = 0.0,
    val protein_g: Double = 0.0
)

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

private data class DayRow(
    val date: String = "",
    val entries: List<Entry> = emptyList(),
    val day_totals: Totals = Totals(),
    val raw_text: String = ""
)

object ApiClient {
    private const val SUPABASE_URL = "https://lhvzpkaekbxkkbnebwqb.supabase.co"
    private const val SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imxodnpwa2Fla2J4a2tibmVid3FiIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzMyMTg2MDIsImV4cCI6MjA4ODc5NDYwMn0.pMGnY80mfz4UAAT-m6OGF51LkgIERrUJ79xxKBT6yEE"
    private const val GOAL_KCAL = 3500.0
    private const val GOAL_PROTEIN = 200.0

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
        TodayResponse(
            date = today,
            entries = row?.entries ?: emptyList(),
            day_totals = dt,
            goals = Goals(kcal = GOAL_KCAL, protein_g = GOAL_PROTEIN),
            remaining = Remaining(
                kcal = GOAL_KCAL - dt.kcal,
                protein_g = Math.round((GOAL_PROTEIN - dt.protein_g) * 10.0) / 10.0
            )
        )
    }

    suspend fun getDays(): List<DaySummary> = withContext(Dispatchers.IO) {
        val body = get("days?select=date,day_totals,entries&order=date.desc")
        val rows = gson.fromJson(body, Array<DayRow>::class.java).toList()
        rows.map { DaySummary(date = it.date, day_totals = it.day_totals, entry_count = it.entries.size) }
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
}

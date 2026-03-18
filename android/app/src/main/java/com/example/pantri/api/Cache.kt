package com.example.pantri.api

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object Cache {
    private const val PREFS = "pantri_cache"
    private val gson = Gson()
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    fun saveToday(data: TodayResponse) {
        prefs.edit().putString("today_${data.date}", gson.toJson(data)).apply()
        prefs.edit().putString("latest_today", gson.toJson(data)).apply()
    }

    fun loadToday(): TodayResponse? {
        val json = prefs.getString("latest_today", null) ?: return null
        return try { gson.fromJson(json, TodayResponse::class.java) } catch (_: Exception) { null }
    }

    fun saveDays(days: List<DaySummary>) {
        prefs.edit().putString("days_list", gson.toJson(days)).apply()
    }

    fun loadDays(): List<DaySummary> {
        val json = prefs.getString("days_list", null) ?: return emptyList()
        return try {
            gson.fromJson(json, object : TypeToken<List<DaySummary>>() {}.type)
        } catch (_: Exception) { emptyList() }
    }

    fun saveDay(date: String, detail: DayDetail) {
        prefs.edit().putString("day_$date", gson.toJson(detail)).apply()
    }

    fun loadDay(date: String): DayDetail? {
        val json = prefs.getString("day_$date", null) ?: return null
        return try { gson.fromJson(json, DayDetail::class.java) } catch (_: Exception) { null }
    }

    fun saveWeight(entries: List<WeightEntry>) {
        prefs.edit().putString("weight_entries", gson.toJson(entries)).apply()
    }

    fun loadWeight(): List<WeightEntry> {
        val json = prefs.getString("weight_entries", null) ?: return emptyList()
        return try {
            gson.fromJson(json, object : TypeToken<List<WeightEntry>>() {}.type)
        } catch (_: Exception) { emptyList() }
    }

    fun saveMealPreps(items: List<MealPrepItem>) {
        prefs.edit().putString("mealpreps", gson.toJson(items)).apply()
    }

    fun loadMealPreps(): List<MealPrepItem> {
        val json = prefs.getString("mealpreps", null) ?: return emptyList()
        return try {
            gson.fromJson(json, object : TypeToken<List<MealPrepItem>>() {}.type)
        } catch (_: Exception) { emptyList() }
    }

    fun saveSettings(settings: AppSettings) {
        prefs.edit().putString("settings", gson.toJson(settings)).apply()
    }

    fun loadSettings(): AppSettings {
        val json = prefs.getString("settings", null) ?: return AppSettings()
        return try { gson.fromJson(json, AppSettings::class.java) } catch (_: Exception) { AppSettings() }
    }
}

package com.example.pantri.api

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

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
    val day_totals: Totals = Totals()
)

data class WeightEntry(
    val date: String = "",
    val weight_kg: Double = 0.0
)

data class WeightPostBody(
    val date: String,
    val weight_kg: Double
)

interface PantriApi {
    @GET("api/today")
    suspend fun getToday(): TodayResponse

    @GET("api/days")
    suspend fun getDays(): List<DaySummary>

    @GET("api/days/{day}")
    suspend fun getDay(@Path("day") day: String): DayDetail

    @GET("api/weight")
    suspend fun getWeight(): List<WeightEntry>

    @POST("api/weight")
    suspend fun postWeight(@Body body: WeightPostBody): Map<String, Any>
}

object ApiClient {
    // Use your machine's local IP for physical device, or 10.0.2.2 for emulator
    private const val BASE_URL = "http://192.168.8.222:8000/"

    val api: PantriApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PantriApi::class.java)
    }
}

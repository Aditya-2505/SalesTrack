package com.yourcompany.salestrack.supabase

import com.yourcompany.salestrack.model.DailySummary
import com.yourcompany.salestrack.model.Trip
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class TripsRepository {

    private fun getTodayDateString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date())
    }

    private fun getCurrentIsoTimestamp(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    suspend fun getLastOdometerReading(userId: String): Int {
        return try {
            withContext(Dispatchers.IO) {
                val trips = Supabase.client.from("trips")
                    .select {
                        filter {
                            eq("user_id", userId)
                        }
                        order(column = "timestamp", order = Order.DESCENDING)
                        limit(1)
                    }
                    .decodeList<Trip>()
                
                if (trips.isNotEmpty()) trips[0].kmReading else 0
            }
        } catch (e: Exception) {
            e.printStackTrace()
            0
        }
    }

    suspend fun getTodayTrips(userId: String): List<Trip> {
        val today = getTodayDateString()
        return try {
            withContext(Dispatchers.IO) {
                Supabase.client.from("trips")
                    .select {
                        filter {
                            eq("user_id", userId)
                            eq("date", today)
                        }
                        order(column = "timestamp", order = Order.ASCENDING)
                    }
                    .decodeList<Trip>()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun getTodaySummary(userId: String): DailySummary? {
        val today = getTodayDateString()
        return try {
            withContext(Dispatchers.IO) {
                Supabase.client.from("daily_summary")
                    .select {
                        filter {
                            eq("user_id", userId)
                            eq("date", today)
                        }
                    }
                    .decodeSingleOrNull<DailySummary>()
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun saveTrip(userId: String, type: String, kmReading: Int, photoUrl: String?): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                val today = getTodayDateString()
                val timestampIso = getCurrentIsoTimestamp()
                
                // 1. Get today's current summary to figure out times_out and trip_number
                val existingSummary = getTodaySummary(userId)
                
                val currentTimesOut = existingSummary?.timesOut ?: 0
                val newTimesOut = if (type == "out") currentTimesOut + 1 else currentTimesOut
                val tripNum = if (type == "out") newTimesOut else currentTimesOut

                // 2. Insert new trip
                val trip = Trip(
                    userId = userId,
                    date = today,
                    type = type,
                    kmReading = kmReading,
                    photoUrl = photoUrl,
                    tripNumber = tripNum,
                    timestamp = timestampIso
                )
                Supabase.client.from("trips").insert(trip)

                // 3. Update or Insert Daily Summary
                if (existingSummary == null) {
                    val summary = DailySummary(
                        userId = userId,
                        date = today,
                        startKm = kmReading,
                        endKm = kmReading,
                        totalKmDriven = 0,
                        timesOut = newTimesOut,
                        status = "active"
                    )
                    Supabase.client.from("daily_summary").insert(summary)
                } else {
                    val totalKm = kmReading - existingSummary.startKm
                    val updatedSummary = existingSummary.copy(
                        endKm = kmReading,
                        totalKmDriven = if (totalKm > 0) totalKm else 0,
                        timesOut = newTimesOut
                    )
                    Supabase.client.from("daily_summary").update(updatedSummary) {
                        filter {
                            eq("user_id", userId)
                            eq("date", today)
                        }
                    }
                }
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun completeDay(userId: String): Boolean {
        val today = getTodayDateString()
        return try {
            withContext(Dispatchers.IO) {
                val existingSummary = getTodaySummary(userId) ?: return@withContext false
                val completedSummary = existingSummary.copy(status = "completed")
                Supabase.client.from("daily_summary").update(completedSummary) {
                    filter {
                        eq("user_id", userId)
                        eq("date", today)
                    }
                }
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun getSalespersonHistory(userId: String): List<DailySummary> {
        return try {
            withContext(Dispatchers.IO) {
                Supabase.client.from("daily_summary")
                    .select {
                        filter {
                            eq("user_id", userId)
                        }
                        order(column = "date", order = Order.DESCENDING)
                    }
                    .decodeList<DailySummary>()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun getTripsForDate(userId: String, date: String): List<Trip> {
        return try {
            withContext(Dispatchers.IO) {
                Supabase.client.from("trips")
                    .select {
                        filter {
                            eq("user_id", userId)
                            eq("date", date)
                        }
                        order(column = "timestamp", order = Order.ASCENDING)
                    }
                    .decodeList<Trip>()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun getTodaySummariesForAll(): List<DailySummary> {
        val today = getTodayDateString()
        return try {
            withContext(Dispatchers.IO) {
                Supabase.client.from("daily_summary")
                    .select {
                        filter {
                            eq("date", today)
                        }
                    }
                    .decodeList<DailySummary>()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun getWeeklySummaries(userId: String): List<DailySummary> {
        return try {
            withContext(Dispatchers.IO) {
                Supabase.client.from("daily_summary")
                    .select {
                        filter {
                            eq("user_id", userId)
                        }
                        order(column = "date", order = Order.DESCENDING)
                        limit(7)
                    }
                    .decodeList<DailySummary>()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun getReportSummaries(startDate: String, endDate: String, userId: String?): List<DailySummary> {
        return try {
            withContext(Dispatchers.IO) {
                Supabase.client.from("daily_summary")
                    .select {
                        filter {
                            gte("date", startDate)
                            lte("date", endDate)
                            if (userId != null) {
                                eq("user_id", userId)
                            }
                        }
                        order(column = "date", order = Order.DESCENDING)
                    }
                    .decodeList<DailySummary>()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}

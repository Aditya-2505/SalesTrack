package com.yourcompany.salestrack.supabase

import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class StorageRepository {

    suspend fun uploadSpeedometerPhoto(userId: String, imageBytes: ByteArray): String? {
        return try {
            withContext(Dispatchers.IO) {
                val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                val timestamp = SimpleDateFormat("HHmmss", Locale.getDefault()).format(Date())
                val path = "$userId/$date/$timestamp.jpg"

                val bucket = Supabase.client.storage.from("speedometer-photos")
                
                // Upload data as JPEG byte array
                bucket.upload(path, imageBytes) {
                    upsert = true
                }

                // Retrieve public URL
                bucket.publicUrl(path)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

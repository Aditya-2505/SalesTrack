package com.yourcompany.salestrack.gemini

import android.util.Base64
import com.yourcompany.salestrack.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class GeminiClient {

    private val geminiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"

    suspend fun extractOdometerReading(imageBytes: ByteArray): String {
        return withContext(Dispatchers.IO) {
            val apiKey = BuildConfig.GEMINI_API_KEY
            if (apiKey.isEmpty() || apiKey.startsWith("your_")) {
                return@withContext "UNCLEAR" // Fallback if API key is not set
            }

            val base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
            
            val prompt = "You are reading a vehicle odometer from a photo taken by a salesperson. Extract only the total odometer reading — the large number showing total kilometers driven, usually labeled ODO or KM. Return ONLY the number with no units, no spaces, no punctuation, and no explanation. If the image is unclear, not a speedometer, or the number cannot be read confidently, return the single word UNCLEAR and nothing else. Example of correct output: 84523"

            val jsonBody = JSONObject().apply {
                val contentsArray = org.json.JSONArray().apply {
                    put(JSONObject().apply {
                        val partsArray = org.json.JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", prompt)
                            })
                            put(JSONObject().apply {
                                put("inlineData", JSONObject().apply {
                                    put("mimeType", "image/jpeg")
                                    put("data", base64Image)
                                })
                            })
                        }
                        put("parts", partsArray)
                    })
                }
                put("contents", contentsArray)
            }

            var connection: HttpURLConnection? = null
            try {
                val url = URL("$geminiUrl?key=$apiKey")
                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                connection.connectTimeout = 30000
                connection.readTimeout = 30000

                val os = connection.outputStream
                val writer = OutputStreamWriter(os, "UTF-8")
                writer.write(jsonBody.toString())
                writer.flush()
                writer.close()
                os.close()

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val responseString = connection.inputStream.bufferedReader().use { it.readText() }
                    
                    val responseJson = JSONObject(responseString)
                    val candidates = responseJson.getJSONArray("candidates")
                    if (candidates.length() > 0) {
                        val content = candidates.getJSONObject(0).getJSONObject("content")
                        val parts = content.getJSONArray("parts")
                        if (parts.length() > 0) {
                            return@withContext parts.getJSONObject(0).getString("text").trim()
                        }
                    }
                }
                "UNCLEAR"
            } catch (e: Exception) {
                e.printStackTrace()
                "UNCLEAR"
            } finally {
                connection?.disconnect()
            }
        }
    }
}

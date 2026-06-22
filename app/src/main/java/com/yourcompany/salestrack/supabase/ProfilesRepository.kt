package com.yourcompany.salestrack.supabase

import com.yourcompany.salestrack.model.Profile
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ProfilesRepository {

    suspend fun getProfile(userId: String): Profile? {
        return try {
            withContext(Dispatchers.IO) {
                Supabase.client.from("profiles")
                    .select {
                        filter {
                            eq("id", userId)
                        }
                    }
                    .decodeSingleOrNull<Profile>()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun createProfile(profile: Profile): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                Supabase.client.from("profiles")
                    .insert(profile)
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun getAllProfiles(): List<Profile> {
        return try {
            withContext(Dispatchers.IO) {
                Supabase.client.from("profiles")
                    .select()
                    .decodeList<Profile>()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}

package com.example.backgroundodometer

import android.content.Context
import java.util.UUID

data class BikeProfile(val id: String, val name: String)

object BikeProfileManager {
    private const val PREFS = "background_odometer"
    private const val IDS_KEY = "bike_profile_ids"
    private const val ACTIVE_KEY = "active_bike_profile"
    private const val NAME_PREFIX = "bike_profile_name_"
    private const val LEGACY_ID = "default"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun ensureInitialized(context: Context) {
        val p = prefs(context)
        if (!p.contains(IDS_KEY)) {
            // Keep the existing database intact as the first bike profile.
            p.edit()
                .putString(IDS_KEY, LEGACY_ID)
                .putString(NAME_PREFIX + LEGACY_ID, "My Bike")
                .putString(ACTIVE_KEY, LEGACY_ID)
                .apply()
        }
        val ids = p.getString(IDS_KEY, LEGACY_ID).orEmpty()
        if (ids.isBlank()) {
            p.edit().putString(IDS_KEY, LEGACY_ID).apply()
        }
        if (p.getString(ACTIVE_KEY, null).isNullOrBlank()) {
            p.edit().putString(ACTIVE_KEY, LEGACY_ID).apply()
        }
    }

    fun getProfiles(context: Context): List<BikeProfile> {
        ensureInitialized(context)
        val p = prefs(context)
        return p.getString(IDS_KEY, LEGACY_ID).orEmpty()
            .split("|").filter { it.isNotBlank() }.distinct()
            .map { id -> BikeProfile(id, p.getString(NAME_PREFIX + id, "Bike") ?: "Bike") }
    }

    fun getActiveProfile(context: Context): BikeProfile {
        ensureInitialized(context)
        val p = prefs(context)
        val id = p.getString(ACTIVE_KEY, LEGACY_ID) ?: LEGACY_ID
        return getProfiles(context).firstOrNull { it.id == id }
            ?: BikeProfile(LEGACY_ID, "My Bike")
    }

    fun createProfile(context: Context, name: String): BikeProfile? {
        val clean = name.trim()
        if (clean.isEmpty()) return null
        ensureInitialized(context)
        val p = prefs(context)
        val id = UUID.randomUUID().toString().replace("-", "").take(12)
        val ids = getProfiles(context).map { it.id } + id
        p.edit()
            .putString(IDS_KEY, ids.joinToString("|"))
            .putString(NAME_PREFIX + id, clean)
            .apply()
        return BikeProfile(id, clean)
    }

    fun renameProfile(context: Context, id: String, name: String): Boolean {
        val clean = name.trim()
        if (clean.isEmpty() || getProfiles(context).none { it.id == id }) return false
        prefs(context).edit().putString(NAME_PREFIX + id, clean).apply()
        return true
    }

    fun setActiveProfile(context: Context, id: String): Boolean {
        if (getProfiles(context).none { it.id == id }) return false
        prefs(context).edit().putString(ACTIVE_KEY, id).apply()
        return true
    }

    fun databaseName(context: Context): String {
        val id = getActiveProfile(context).id
        // The default profile deliberately reuses the original DB filename.
        return if (id == LEGACY_ID) "background_odometer.db"
        else "background_odometer_$id.db"
    }
}

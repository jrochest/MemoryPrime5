package com.md.workers

import android.content.Context
import android.net.Uri
import androidx.core.content.edit

object BackupPreferences {
    const val REQUEST_CODE_FOR_LOCATION_1 = 69
    const val REQUEST_CODE_FOR_LOCATION_2 = 70
    const val REQUEST_CODE_FOR_LOCATION_3 = 71
    const val REQUEST_CODE_FOR_LOCATION_4 = 72

    const val BACKUP_LOCATION_KEY_1 = "backup_location_key"
    const val BACKUP_LOCATION_KEY_2 = "backup_location_key_2"
    const val BACKUP_LOCATION_KEY_3 = "backup_location_key_3"
    const val BACKUP_LOCATION_KEY_4 = "backup_location_key_4"

    const val BACKUP_LOCATION_FILE = "backup_locations_prefs"

    val requestCodeToKey = mapOf(
            REQUEST_CODE_FOR_LOCATION_1 to BACKUP_LOCATION_KEY_1,
            REQUEST_CODE_FOR_LOCATION_2 to BACKUP_LOCATION_KEY_2,
            REQUEST_CODE_FOR_LOCATION_3 to BACKUP_LOCATION_KEY_3,
            REQUEST_CODE_FOR_LOCATION_4 to BACKUP_LOCATION_KEY_4
    )

    fun isBackupFresh(context: Context, key: String) : Boolean {
        return context.getSharedPreferences(BACKUP_LOCATION_FILE, Context.MODE_PRIVATE).getBoolean(key + "is_stale", false)
    }

    fun markBackupFresh(context: Context, key: String, newValue: Boolean) {
        context.getSharedPreferences(BACKUP_LOCATION_FILE, Context.MODE_PRIVATE).edit {
            putBoolean(key + "is_stale", newValue)
        }
    }

    fun getBackupLocations(context: Context): MutableMap<String, Uri> {
        val sharedPref = context.getSharedPreferences(BACKUP_LOCATION_FILE, Context.MODE_PRIVATE)

        val backupLocations = mutableMapOf<String, Uri>()
        requestCodeToKey.values.forEach { locationKey ->
            if (!isBackupFresh(context, locationKey)) {
                sharedPref.getString(locationKey, null)?.let {
                    backupLocations.put(locationKey, Uri.parse(it))
                }
            }
        }
        return backupLocations
    }

    fun markAllStale(context: Context) {
        requestCodeToKey.values.forEach { key ->
            markBackupFresh(context, key, false)
        }
    }
}
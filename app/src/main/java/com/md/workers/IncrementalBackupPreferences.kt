package com.md.workers

import android.content.Context
import android.net.Uri

object IncrementalBackupPreferences {
    const val REQUEST_CODE_FOR_LOCATION_1 = 169
    const val REQUEST_CODE_FOR_LOCATION_2 = 170
    const val REQUEST_CODE_FOR_LOCATION_3 = 171
    const val REQUEST_CODE_FOR_LOCATION_4 = 172

    const val BACKUP_LOCATION_KEY_1 = "backup_location_key"
    const val BACKUP_LOCATION_KEY_2 = "backup_location_key_2"
    const val BACKUP_LOCATION_KEY_3 = "backup_location_key_3b"
    const val BACKUP_LOCATION_KEY_4 = "backup_location_key_4"

    const val BACKUP_LOCATION_FILE = "incremental_backup_locations_prefs"

    val requestCodeToKey = mapOf(
            REQUEST_CODE_FOR_LOCATION_1 to BACKUP_LOCATION_KEY_1,
            REQUEST_CODE_FOR_LOCATION_2 to BACKUP_LOCATION_KEY_2,
            REQUEST_CODE_FOR_LOCATION_3 to BACKUP_LOCATION_KEY_3,
            REQUEST_CODE_FOR_LOCATION_4 to BACKUP_LOCATION_KEY_4
    )

    fun getBackupLocations(context: Context): MutableMap<String, Uri> {
        val sharedPref = context.getSharedPreferences(BACKUP_LOCATION_FILE, Context.MODE_PRIVATE)

        val backupLocations = mutableMapOf<String, Uri>()
        requestCodeToKey.values.forEach { locationKey ->
                sharedPref.getString(locationKey, null)?.let {
                    backupLocations.put(locationKey, Uri.parse(it))
                }
        }
        return backupLocations
    }
}
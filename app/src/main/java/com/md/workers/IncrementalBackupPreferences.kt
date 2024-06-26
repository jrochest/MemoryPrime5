package com.md.workers

import android.content.Context
import android.net.Uri
import java.net.URLDecoder

object IncrementalBackupPreferences {


    data class BackupLocation(
        val requestCode: Int,
        val locationKey: String,
        val labelForUi: String,
    )


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
    val location1 = BackupLocation(
        REQUEST_CODE_FOR_LOCATION_1,
        BACKUP_LOCATION_KEY_1, "Backup location 1"
    )
    val location2 = BackupLocation(
        REQUEST_CODE_FOR_LOCATION_2,
        BACKUP_LOCATION_KEY_2, "Backup location 2"
    )
    val location3 = BackupLocation(
        REQUEST_CODE_FOR_LOCATION_3,
        BACKUP_LOCATION_KEY_3, "Backup location 3"
    )
    val location4 = BackupLocation(
        REQUEST_CODE_FOR_LOCATION_4,
        BACKUP_LOCATION_KEY_4, "Backup location 4"
    )


    fun getBackupLocationsFromPrefs(context: Context): MutableMap<String, Uri> {
        val sharedPref = context.getSharedPreferences(BACKUP_LOCATION_FILE, Context.MODE_PRIVATE)

        val backupLocations = mutableMapOf<String, Uri>()
        requestCodeToKey.values.forEach { locationKey ->
            sharedPref.getString(locationKey, null)?.let {
                backupLocations.put(locationKey, Uri.parse(it))
            }
        }
        return backupLocations
    }

    fun simplifyName(backupLocation: String): String {
        val decoded: String = URLDecoder.decode(backupLocation, "UTF-8")
        return decoded + "\n" + decoded.substringAfterLast("/")
    }
}
package com.md.modesetters

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.widget.Button
import android.widget.ToggleButton
import com.md.CategorySingleton
import com.md.ModeHandler
import com.md.R
import com.md.workers.BackupPreferences
import com.md.workers.BackupPreferences.REQUEST_CODE_FOR_LOCATION_1
import com.md.workers.BackupPreferences.REQUEST_CODE_FOR_LOCATION_2
import com.md.workers.BackupPreferences.REQUEST_CODE_FOR_LOCATION_3
import com.md.workers.BackupPreferences.REQUEST_CODE_FOR_LOCATION_4
import com.md.workers.BackupToUsbManager.openZipFileDocument
import com.md.workers.IncrementalBackupManager
import com.md.workers.IncrementalBackupPreferences

object SettingModeSetter : ModeSetter(), ItemDeletedHandler {
    fun setup(memoryDroid: Activity?, modeHand: ModeHandler?) {
        parentSetup(memoryDroid, modeHand)
    }

    override fun setupModeImpl(context: Activity) {
        commonSetup(context, R.layout.settings)
        refreshSettings(context)
    }

     fun refreshSettings(activity: Activity) {
        val markButton = activity.findViewById<ToggleButton>(R.id.look_ahead) ?: return


         val instance = CategorySingleton.getInstance()
        markButton.isChecked = instance.lookAheadDays != 0
        markButton.setOnClickListener {
            val checked = markButton.isChecked
            instance.lookAheadDays = if (checked) 1 else 0
        }

        val repeatButton = activity.findViewById<ToggleButton>(R.id.repeat)
        repeatButton.isChecked = instance.shouldRepeat()
        repeatButton.setOnClickListener { instance.setRepeat(!repeatButton.isChecked) }

        activity.findViewById<Button>(R.id.external_backup_directory).apply {
            specifyNewBackupLocation("Backup location 1", activity, REQUEST_CODE_FOR_LOCATION_1)
        }

        activity.findViewById<Button>(R.id.external_backup_directory_2).apply {
            specifyNewBackupLocation("Backup location 2", activity, REQUEST_CODE_FOR_LOCATION_2)
        }

        activity.findViewById<Button>(R.id.external_backup_directory_3).apply {
            specifyNewBackupLocation("Backup location 3", activity, REQUEST_CODE_FOR_LOCATION_3)
        }
        activity.findViewById<Button>(R.id.external_backup_directory_4).apply {
            specifyNewBackupLocation("Backup location 4", activity, REQUEST_CODE_FOR_LOCATION_4)
        }

         activity.findViewById<Button>(R.id.incremental_backup_directory).apply {
             specifyNewIncrementalBackupLocation("Incremental Backup location 1", activity, IncrementalBackupPreferences.REQUEST_CODE_FOR_LOCATION_1)
         }

         activity.findViewById<Button>(R.id.incremental_backup_directory_2).apply {
             specifyNewIncrementalBackupLocation("Incremental Backup location 2", activity, IncrementalBackupPreferences.REQUEST_CODE_FOR_LOCATION_2)
         }

         activity.findViewById<Button>(R.id.incremental_backup_directory_3).apply {
             specifyNewIncrementalBackupLocation("Incremental Backup location 3", activity, IncrementalBackupPreferences.REQUEST_CODE_FOR_LOCATION_3)
         }
         activity.findViewById<Button>(R.id.incremental_backup_directory_4).apply {
             specifyNewIncrementalBackupLocation("Incremental Backup location 4", activity, IncrementalBackupPreferences.REQUEST_CODE_FOR_LOCATION_4)
         }
    }

    private fun Button.specifyNewIncrementalBackupLocation(backupLocationName: String, activity: Activity, requestCode: Int) {
        val key = IncrementalBackupPreferences.requestCodeToKey[requestCode]
        val prefFile = context.getSharedPreferences(IncrementalBackupPreferences.BACKUP_LOCATION_FILE, Context.MODE_PRIVATE)
        val backupLocation = prefFile.getString(key, null)

        if (backupLocation != null) {
            setText(backupLocationName + ":\n" + IncrementalBackupPreferences.simplifyName(backupLocation) + "\n")
        } else {
            setText("$backupLocationName: Tap to set")
        }
        setOnClickListener { IncrementalBackupManager.openBackupDir(activity, requestCode) }
        addLongClickToClear(prefFile, key)
    }

    private fun Button.addLongClickToClear(prefFile: SharedPreferences, key: String?) {
        setOnLongClickListener {
            AlertDialog.Builder(context).setMessage("Stop backing up to this location?")
                    .setCancelable(true).setPositiveButton("Yes") { dialog, _ ->
                        prefFile.edit().remove(key).apply()
                        setText("Cleared!: Tap to set")
                    }
                    .setNegativeButton("No") { dialog, _ -> }.create().show()
            true
        }
    }

    private fun Button.specifyNewBackupLocation(backupLocationName: String, activity: Activity, requestCode: Int) {
        val key = BackupPreferences.requestCodeToKey[requestCode]
        val prefFile = context.getSharedPreferences(BackupPreferences.BACKUP_LOCATION_FILE, Context.MODE_PRIVATE)
        val backupLocation = prefFile.getString(key, null)
        if (backupLocation != null) {
            setText(backupLocationName + ":\n" + BackupPreferences.simplifyName(backupLocation) + "\n")
        } else {
            setText("$backupLocationName: Tap to set")
        }
        setOnClickListener { openZipFileDocument(activity, requestCode) }
        addLongClickToClear(prefFile, key)
    }
}
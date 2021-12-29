package com.md

import android.app.Activity
import android.content.Context
import android.os.PowerManager
import android.view.Menu
import android.view.MenuItem
import androidx.core.content.ContextCompat.getSystemService
import com.md.modesetters.*
import com.md.utils.ToastSingleton
import com.md.workers.BackupToUsbManager
import com.md.workers.IncrementalBackupManager.createAndWriteZipBackToPreviousLocation
import java.io.File

class ActivityHelper {
    private var activity: Activity? = null

    var timerManager = TimerManager()
    fun commonActivitySetup(activity: Activity?) {

        this.activity = activity


        val theFile = File(DbContants.getDatabasePath())
        val parentFile = File(theFile.parent)
        if (!parentFile.exists()) {
            parentFile.mkdirs()
        }
        DbNoteEditor.instance!!.setContext(activity)
        ToastSingleton.getInstance().context = activity

        // Init the db with this:
        DbNoteEditor.instance!!.first
    }

    var wakeLock: PowerManager.WakeLock? = null

    fun acquireWakeLock() {
        releaseWakeLockifPresent()
        val activity = activity ?: return
        wakeLock =
            (activity.getSystemService(Context.POWER_SERVICE) as PowerManager).run {
                newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MP::backup").apply {
                    // Keep screen on for 10 minutes max.
                    acquire(60_000 * 10)
                }
            }
    }

    private fun releaseWakeLockifPresent() {
        if (wakeLock != null) {
            wakeLock?.release()
            wakeLock = null
        }
    }

    fun createCommonMenu(menu: Menu, activity: SpacedRepeaterActivity) {
        val inflater = activity.menuInflater
        inflater.inflate(R.menu.menu, menu)
        val quitMenuItem = menu.findItem(R.id.dimMenuItem)
        quitMenuItem.setOnMenuItemClickListener {
            activity.maybeDim()
            true
        }
        menu.findItem(R.id.backup_previous_location).setOnMenuItemClickListener { item: MenuItem? ->
            BackupToUsbManager.createAndWriteZipBackToPreviousLocation(activity, activity.contentResolver, true)
            true
        }
        menu.findItem(R.id.incremental_backup).setOnMenuItemClickListener { item: MenuItem? ->
            createAndWriteZipBackToPreviousLocation(
                    activity, activity.contentResolver, true, false)
            true
        }
        menu.findItem(R.id.slow_incremental_backup).setOnMenuItemClickListener { item: MenuItem? ->
            val item = item ?: return@setOnMenuItemClickListener true
            if (!item.isEnabled) return@setOnMenuItemClickListener true

            item.isEnabled = false
            item.setIcon(R.drawable.greysave)

            createAndWriteZipBackToPreviousLocation(
                    activity, activity.contentResolver, true, true) { success ->
                releaseWakeLockifPresent()
                if (success) {
                    item.setIcon(android.R.drawable.ic_menu_save)
                } else {
                    item.setIcon(android.R.drawable.ic_popup_sync)
                }

                item.isEnabled = true
            }
            true
        }
        menu.findItem(R.id.restore).setOnMenuItemClickListener { item: MenuItem? ->
            RestoreFromZipManager.openZipFileDocument(activity)
            true
        }
        menu.findItem(R.id.incremental_restore).setOnMenuItemClickListener { item: MenuItem? ->
            RestoreFromIncrementalDirectoryManager.openZipFileDocument(activity)
            true
        }
        menu.findItem(R.id.small_timer).setOnMenuItemClickListener {
            timerManager.addTimer(7, 30)
            true
        }
        menu.findItem(R.id.medium_timer).setOnMenuItemClickListener {
            timerManager.addTimer(9, 30)
            true
        }
        menu.findItem(R.id.large_timer).setOnMenuItemClickListener {
            timerManager.addTimer(10, 120)
            true
        }
        menu.findItem(R.id.cancel_timer).setOnMenuItemClickListener {
            timerManager.cancelTimer()
            true
        }
        addMenu(menu, R.id.creationModeMenuItem, CreateModeSetter.getInstance(), activity)
        addMenu(menu, R.id.browseDeckModeMenuItem, BrowsingModeSetter.getInstance(), activity)
        addMenu(menu, R.id.learningModeMenuItem, LearningModeSetter.instance, activity)
        addMenu(menu, R.id.selectDeckModeMenuItem, DeckChooseModeSetter, activity)
        addMenu(menu, R.id.settings, SettingModeSetter, activity)
        addMenu(menu, R.id.clean_up_files, CleanUpAudioFilesModeSetter.getInstance(), activity)
    }

    private fun addMenu(menu: Menu, item: Int, ms: ModeSetter,
                        activity: Activity) {
        val findItem = menu.findItem(item)
        findItem.setOnMenuItemClickListener {
            if (ms is CreateModeSetter) {
                ms.setNote(null)
            }
            ms.switchMode(activity)
            true
        }
    }
}
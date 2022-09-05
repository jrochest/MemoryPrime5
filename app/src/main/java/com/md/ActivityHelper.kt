package com.md

import android.app.Activity
import android.view.Menu
import android.view.MenuItem
import android.view.WindowManager
import com.md.modesetters.*
import com.md.utils.ToastSingleton
import com.md.workers.BackupToUsbManager
import com.md.workers.IncrementalBackupManager.createAndWriteZipBackToPreviousLocation
import java.io.File

class ActivityHelper {
    private var activity: Activity? = null

    var timerManager = TimerManager()
    fun commonActivitySetup(activity: SpacedRepeaterActivity?) {

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
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            createAndWriteZipBackToPreviousLocation(
                    activity, activity.contentResolver, true, true) { success ->
                // This works while only one features needs a screen lock.
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
        addMenu(menu, R.id.creationModeMenuItem, CreateModeSetter, activity)
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
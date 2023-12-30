package com.md

import android.app.Activity
import android.view.Menu
import android.view.MenuItem
import android.view.WindowManager
import com.md.modesetters.*
import com.md.utils.ToastSingleton
import com.md.workers.BackupToUsbManager
import com.md.workers.IncrementalBackupManager.createAndWriteZipBackToPreviousLocation
import com.md.composeModes.ComposeModeSetter
import dagger.Lazy
import dagger.hilt.android.scopes.ActivityScoped
import java.io.File
import javax.inject.Inject

@ActivityScoped
class ActivityHelper @Inject constructor(
    val activity: Activity,
    private val composeModeSetter: Lazy<ComposeModeSetter>,
    private val learningModeSetter: Lazy<LearningModeSetter>) {
    fun commonActivitySetup() {
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
                    activity, activity.contentResolver, shouldSpeak = true, runExtraValidation = false
            )
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
        addMenu(menu, R.id.creationModeMenuItem, CreateModeSetter, activity)
        addMenu(menu, R.id.browseDeckModeMenuItem, BrowsingModeSetter.getInstance(), activity)
        addMenu(menu, R.id.learningModeMenuItem, learningModeSetter.get(), activity)
        addMenu(menu, R.id.selectDeckModeMenuItem, DeckChooseModeSetter, activity)
        addMenu(menu, R.id.settings, SettingModeSetter, activity)
        addMenu(menu, R.id.working_memory, composeModeSetter.get(), activity)
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
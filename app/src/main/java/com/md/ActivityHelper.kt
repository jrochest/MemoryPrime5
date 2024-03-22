package com.md

import android.app.Activity
import android.view.Menu
import android.view.MenuItem
import android.view.WindowManager
import com.md.modesetters.*
import com.md.utils.ToastSingleton
import com.md.workers.IncrementalBackupManager.createAndWriteZipBackToPreviousLocation
import dagger.hilt.android.scopes.ActivityScoped
import java.io.File
import javax.inject.Inject

@ActivityScoped
class ActivityHelper @Inject constructor(
    val activity: Activity
) {
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


}
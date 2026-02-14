package com.md

import android.app.Activity
import com.md.utils.ToastSingleton
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

        // Init the db with this:
        DbNoteEditor.instance!!.setContext(activity)
        // TODO Convert ToastSingleton to use dagger injection.
        ToastSingleton.getInstance().context = activity

        // Run one-time FSRS migration for any unmigrated notes.
        val report = com.md.fsrs.FsrsMigrationRunner.migrateAll(activity)
        android.util.Log.i("FSRS", report.toLogString())
        if (report.totalMigrated > 0) {
            val msg = "FSRS migration: ${report.totalMigrated} notes migrated" +
                    if (report.totalFailed > 0) " (${report.totalFailed} failed)" else ""
            ToastSingleton.getInstance().msg(msg)
        }
    }


}
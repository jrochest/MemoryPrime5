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
        DbNoteEditor.instance!!.setContext(activity)
        // TODO Convert ToastSingleton to use dagger injection.
        ToastSingleton.getInstance().context = activity

        // Init the db with this:
        DbNoteEditor.instance!!.first
    }


}
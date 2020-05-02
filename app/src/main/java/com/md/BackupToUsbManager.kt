package com.md

import android.app.Activity
import android.content.Intent
import androidx.core.app.ActivityCompat.startActivityForResult


object BackupToUsbManager {
    fun backupToUsb(activity: Activity) {

        /*
        val intentGallery = Intent(Intent.ACTION_CREATE_DOCUMENT)
       // intentGallery.addCategory(Intent.CATEGORY_BROWSABLE)
        //intentGallery.type = MIME_TYPE_DIR
        intentGallery.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intentGallery.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        *
        startActivityForResult(activity, intentGallery, 69, null)




         */

        val exportIntent = Intent(Intent.ACTION_CREATE_DOCUMENT)
        exportIntent.addCategory(Intent.CATEGORY_OPENABLE)
        exportIntent.type = "application/zip"
        val filename = "memprime.zip"
        exportIntent.putExtra(Intent.EXTRA_TITLE, filename)
        startActivityForResult(activity, exportIntent, 69, null)

    }
}


package com.md

import android.util.Log
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ZipManager {
    fun zip(_files: Array<String>, dest: FileOutputStream) {
        try {
            var origin: BufferedInputStream
            val out = ZipOutputStream(BufferedOutputStream(dest))
            val data = ByteArray(BUFFER)
            for (i in _files.indices) {
                Log.v("Compress", "Adding: " + _files[i])
                try {
                    val fi = FileInputStream(_files[i])
                    origin = BufferedInputStream(fi, BUFFER)
                    val entry = ZipEntry(_files[i].substring(_files[i].lastIndexOf("/") + 1))
                    out.putNextEntry(entry)
                    var count: Int = 0
                    while (origin.read(data, 0, BUFFER).also { count = it } != -1) {
                        out.write(data, 0, count)
                    }
                    origin.close()
                } catch (e: FileNotFoundException){
                    Log.e("Compress", "failed to open " + _files[i])
                }
            }
            out.flush()
            out.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun unzip(_zipFile: String?, _targetLocation: String) {
        //create target location folder if not exist
        dirChecker(_targetLocation)
        try {
            val fin = FileInputStream(_zipFile)
            val zin = ZipInputStream(fin)
            var ze: ZipEntry? = null
            while (zin.getNextEntry().also({ ze = it }) != null) {

                val ze = ze ?: continue
                //create dir if required while unzipping
                if (ze.isDirectory()) {
                    dirChecker(ze.getName())
                } else {
                    val fout = FileOutputStream(_targetLocation + ze.getName())
                    var c: Int = zin.read()
                    while (c != -1) {
                        fout.write(c)
                        c = zin.read()
                    }
                    zin.closeEntry()
                    fout.close()
                }
            }
            zin.close()
        } catch (e: Exception) {
            println(e)
        }
    }

    private fun dirChecker(dir: String) {
        val f = File(dir)
        if (!f.isDirectory()) {
            f.mkdirs()
        }
    }
    private const val BUFFER = 80000
}
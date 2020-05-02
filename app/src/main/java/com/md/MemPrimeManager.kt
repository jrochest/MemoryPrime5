package com.md

import android.util.Log
import com.md.utils.ToastSingleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object MemPrimeManager {

    private fun convertToZipDir(absoluteDir: File) : String {
        val relativeDir = absoluteDir.path.replaceBefore(
                "com.md.MemoryPrime", "")
        return "$relativeDir/"
    }

    private fun convertToZipFile(absoluteFile: File) : String {
        return absoluteFile.path.replaceBefore("com.md.MemoryPrime", "")
    }

    suspend fun zip(files: MutableList<File>, dirs: MutableList<File>, dest: FileOutputStream, toneManager: ToneManager) {
        try {
            var origin: BufferedInputStream
            val out = ZipOutputStream(BufferedOutputStream(dest))

            dirs.forEach {
                out.putNextEntry(ZipEntry(convertToZipDir(it)))
            }

            var count = 0
            val data = ByteArray(BUFFER)
            val size = files.size
            files.forEach { file ->
                Log.v("Compress", "Adding: " + file)
                try {
                    val fi = FileInputStream(file)
                    origin = BufferedInputStream(fi, BUFFER)
                    val entry = ZipEntry(convertToZipFile(file))
                    out.putNextEntry(entry)
                    while (true) {
                        val count = origin.read(data, 0, BUFFER)
                        if (count == -1) {
                            break
                        }
                        out.write(data, 0, count)
                    }
                    origin.close()

                    count++

                    if (count % 1000 == 0) {
                        GlobalScope.launch(Dispatchers.Main) {
                            ToastSingleton.getInstance().msg("Memprime backed up " + count + " of " + size)
                        }
                    }

                } catch (e: FileNotFoundException){
                    Log.e("Compress", "failed to open " + file)

                    GlobalScope.launch(Dispatchers.Main) {
                        toneManager.errorTone()
                        ToastSingleton.getInstance().error("error backing up" + file)
                    }

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
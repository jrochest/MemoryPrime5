package com.md

import android.util.Log
import com.md.modesetters.TtsSpeaker
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

    fun zip(files: MutableList<File>, dirs: MutableList<File>, dest: FileOutputStream) : Boolean {
        try {
            var origin: BufferedInputStream
            val out = ZipOutputStream(BufferedOutputStream(dest))

            dirs.forEach {
                out.putNextEntry(ZipEntry(convertToZipDir(it)))
            }

            var fileCount = 0
            val data = ByteArray(BUFFER)
            val size = files.size
            files.forEach { file ->
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

                    fileCount++
                    if (fileCount % 2000 == 0) {
                        GlobalScope.launch(Dispatchers.Main) {
                            ToastSingleton.getInstance().msg("Memprime backed up " + fileCount + " of " + size)
                        }
                    }
                } catch (e: FileNotFoundException){
                    Log.e("Compress", "failed to open " + file)

                    GlobalScope.launch(Dispatchers.Main) {
                        TtsSpeaker.speak("error backing up" + file)
                    }

                }
            }
            out.flush()
            out.close()
            return true
        } catch (e: Exception) {
            GlobalScope.launch(Dispatchers.Main) {
                TtsSpeaker.speak("error backing up: $e")
            }
            return false
        }
    }

    private const val BUFFER = 800000
}
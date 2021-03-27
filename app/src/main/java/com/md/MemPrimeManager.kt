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

    fun zip(files: List<File>, dirs: List<File>, dest: FileOutputStream) : Boolean {
        try {

            val out = ZipOutputStream(BufferedOutputStream(dest))

            dirs.forEach {
                out.putNextEntry(ZipEntry(convertToZipDir(it)))
            }

            var fileCount = 0
            val data = ByteArray(BUFFER)
            val size = files.size
            files.forEach { file ->
                try {
                    println("zipping $file")
                    val fi = FileInputStream(file)
                    val origin = BufferedInputStream(fi, BUFFER)
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
                } catch (e: FileNotFoundException){
                    Log.e("Compress", "failed to open " + e)

                    GlobalScope.launch(Dispatchers.Main) {
                        TtsSpeaker.speak("error backing up" + file)
                    }

                }
            }
            out.flush()
            out.close()
            return true
        } catch (e: Exception) {
            Log.e("Compress", "failed to open " + e)
            GlobalScope.launch(Dispatchers.Main) {
                TtsSpeaker.speak("error backing up: $e")
            }
            return false
        }
    }

    private const val BUFFER = 800000
}
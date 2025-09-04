package org.dslul.openboard.inputmethod.latin.utils

import android.content.Context
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

object CrashLogger {
    private const val TAG = "LancarCrash"
    private const val FILE_NAME = "crash_last.txt"

    fun logException(context: Context, tag: String, throwable: Throwable?) {
        try {
            val sw = StringWriter()
            throwable?.printStackTrace(PrintWriter(sw))
            val text = "[" + System.currentTimeMillis() + "] " + tag + "\n" + (sw.toString())
            Log.e(TAG, text)
            val out = File(context.cacheDir, FILE_NAME)
            out.writeText(text)
        } catch (_: Throwable) {
            // ignore
        }
    }
}



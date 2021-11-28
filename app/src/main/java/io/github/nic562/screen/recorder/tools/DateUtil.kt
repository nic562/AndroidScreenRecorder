package io.github.nic562.screen.recorder.tools

import android.text.format.DateFormat
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

/**
 * Created by Nic on 2020/1/3.
 */
object DateUtil {
    private const val DATETIME_FORMATTER_STR = "yyyy-MM-dd HH:mm:ss"

    fun dateTimeToStr(long: Long, fmt: String = DATETIME_FORMATTER_STR): String {
        return DateFormat.format(fmt, long).toString()
    }

    fun strToDateTime(str: String, fmt: String = DATETIME_FORMATTER_STR): Date? {
        return try {
            SimpleDateFormat(fmt, Locale.getDefault()).parse(str)
        } catch (e: Exception) {
            Log.e("DateUtil", "parse datetime error:", e)
            null
        }
    }
}
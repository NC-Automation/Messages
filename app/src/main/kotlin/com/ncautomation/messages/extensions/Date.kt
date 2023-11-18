package com.ncautomation.messages.extensions

import android.text.format.DateFormat
import android.text.format.DateUtils
import com.simplemobiletools.commons.extensions.isThisYear
import java.util.*

fun Date.format(pattern: String): String {
    return DateFormat.format(pattern, this).toString()
}

fun Int.formatDateOrTime(hideTimeAtOtherDays: Boolean = true, showYearEvenIfCurrent: Boolean = false): String {
    val cal = Calendar.getInstance(Locale.ENGLISH)
    cal.timeInMillis = this * 1000L

    return if (DateUtils.isToday(this * 1000L)) {
        DateFormat.format("h:mm a", cal).toString()
    } else {
        var format = "MMM d yyyy"  //context.baseConfig.dateFormat
        if (!showYearEvenIfCurrent && isThisYear()) {
            format = "MMM d" // format.replace("y", "").trim().trim('-').trim('.').trim('/')
        }

        if (!hideTimeAtOtherDays) {
            format += ", h:mm a"
        }

        DateFormat.format(format, cal).toString()
    }
}

package com.ncautomation.commons.extensions

import android.content.Context
import com.ncautomation.commons.models.FileDirItem

fun FileDirItem.isRecycleBinPath(context: Context): Boolean {
    return path.startsWith(context.recycleBinPath)
}

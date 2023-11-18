package com.ncautomation.messages

import android.app.Application
import com.simplemobiletools.commons.extensions.checkUseEnglish

class App : Application() {
    private var activeThreadId: Long? = null

    fun getActiveThreadId(): Long? {
        return activeThreadId
    }

    fun setActiveThreadId(activeThreadId: Long?) {
        this.activeThreadId = activeThreadId
    }
    public
    override fun onCreate() {
        super.onCreate()
        checkUseEnglish()
    }
}

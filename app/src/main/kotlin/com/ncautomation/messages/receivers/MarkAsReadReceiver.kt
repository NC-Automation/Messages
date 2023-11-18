package com.ncautomation.messages.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.simplemobiletools.commons.extensions.notificationManager
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.ncautomation.messages.extensions.conversationsDB
import com.ncautomation.messages.extensions.markThreadMessagesRead
import com.ncautomation.messages.extensions.updateUnreadCountBadge
import com.ncautomation.messages.helpers.MARK_AS_READ
import com.ncautomation.messages.helpers.THREAD_ID
import com.ncautomation.messages.helpers.refreshMessages

class MarkAsReadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            MARK_AS_READ -> {
                val threadId = intent.getLongExtra(THREAD_ID, 0L)
                context.notificationManager.cancel(threadId.hashCode())
                ensureBackgroundThread {
                    context.markThreadMessagesRead(threadId)
                    context.conversationsDB.markRead(threadId)
                    context.updateUnreadCountBadge(context.conversationsDB.getUnreadConversations())
                    refreshMessages()
                }
            }
        }
    }
}

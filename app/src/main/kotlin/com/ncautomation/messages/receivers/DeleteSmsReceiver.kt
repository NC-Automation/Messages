package com.ncautomation.messages.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ncautomation.commons.extensions.notificationManager
import com.ncautomation.commons.helpers.ensureBackgroundThread
import com.ncautomation.messages.extensions.conversationsDB
import com.ncautomation.messages.extensions.deleteMessage
import com.ncautomation.messages.extensions.updateLastConversationMessage
import com.ncautomation.messages.extensions.updateUnreadCountBadge
import com.ncautomation.messages.helpers.IS_MMS
import com.ncautomation.messages.helpers.MESSAGE_ID
import com.ncautomation.messages.helpers.THREAD_ID
import com.ncautomation.messages.helpers.refreshMessages

class DeleteSmsReceiver: BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val threadId = intent.getLongExtra(THREAD_ID, 0L)
        val messageId = intent.getLongExtra(MESSAGE_ID, 0L)
        val isMms = intent.getBooleanExtra(IS_MMS, false)
        context.notificationManager.cancel(threadId.hashCode())
        ensureBackgroundThread {
            context.deleteMessage(messageId, isMms)
            context.updateUnreadCountBadge(context.conversationsDB.getUnreadConversations())
            context.updateLastConversationMessage(threadId)
            refreshMessages()
        }
    }
}

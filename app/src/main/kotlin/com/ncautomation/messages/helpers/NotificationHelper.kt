package com.ncautomation.messages.helpers

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager.IMPORTANCE_HIGH
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.net.toUri
import com.ncautomation.commons.extensions.getProperPrimaryColor
import com.ncautomation.commons.extensions.notificationManager
import com.ncautomation.commons.helpers.*
import com.ncautomation.messages.R
import com.ncautomation.messages.activities.ThreadActivity
import com.ncautomation.messages.extensions.config
import com.ncautomation.messages.extensions.conversationsDB
import com.ncautomation.messages.extensions.messagesDB
import com.ncautomation.messages.extensions.subscriptionManagerCompat
import com.ncautomation.messages.messaging.isShortCodeWithLetters
import com.ncautomation.messages.models.Conversation
import com.ncautomation.messages.models.SIMCard
import com.ncautomation.messages.receivers.DeleteSmsReceiver
import com.ncautomation.messages.receivers.DirectReplyReceiver
import com.ncautomation.messages.receivers.MarkAsReadReceiver

class NotificationHelper(private val context: Context) {

    private val notificationManager = context.notificationManager
    private val soundUri get() = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    private val user = Person.Builder()
        .setName(context.getString(R.string.me))
        .build()

    @SuppressLint("NewApi")
    fun showMessageNotification(
        messageId: Long,
        address: String,
        body: String,
        threadId: Long,
        bitmap: Bitmap?,
        sender: String?,
        alertOnlyOnce: Boolean = false
    ) {

        ensureBackgroundThread {
            var conversation: Conversation? = context.conversationsDB.getConversationWithThreadId(threadId)
            var app = (context.applicationContext as com.ncautomation.messages.App)
            var activeThread = app?.getActiveThreadId()
            var subscriptionId = context.messagesDB.getSubscriptionId(messageId)
            createChannels()
            var channelId = when {
                subscriptionId != null -> "${NOTIFICATION_CHANNEL}_sim_${subscriptionId}"
                else -> NOTIFICATION_CHANNEL
            }
            if (activeThread == conversation?.threadId) channelId = "${NOTIFICATION_CHANNEL}_foreground"

            if (isOreoPlus() && conversation?.customNotification == true) {
                channelId = getConversationChannel(conversation!!)
            }
            val notificationId = threadId.hashCode()
            var soundUri2 = soundUri
            if (!isOreoPlus() && conversation?.customNotification == true){
                soundUri2 = conversation!!.sound!!.toUri()
            }

            val contentIntent = Intent(context, ThreadActivity::class.java).apply {
                putExtra(THREAD_ID, threadId)
            }
            val contentPendingIntent =
                PendingIntent.getActivity(context, notificationId, contentIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)

            val markAsReadIntent = Intent(context, MarkAsReadReceiver::class.java).apply {
                action = MARK_AS_READ
                putExtra(THREAD_ID, threadId)
            }
            val markAsReadPendingIntent =
                PendingIntent.getBroadcast(context, notificationId, markAsReadIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)

            val deleteSmsIntent = Intent(context, DeleteSmsReceiver::class.java).apply {
                putExtra(THREAD_ID, threadId)
                putExtra(MESSAGE_ID, messageId)
            }
            val deleteSmsPendingIntent =
                PendingIntent.getBroadcast(context, notificationId, deleteSmsIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)

            var replyAction: NotificationCompat.Action? = null
            val isNoReplySms = isShortCodeWithLetters(address)
            if (isNougatPlus() && !isNoReplySms) {
                val replyLabel = context.getString(R.string.reply)
                val remoteInput = RemoteInput.Builder(REPLY)
                    .setLabel(replyLabel)
                    .build()

                val replyIntent = Intent(context, DirectReplyReceiver::class.java).apply {
                    putExtra(THREAD_ID, threadId)
                    putExtra(THREAD_NUMBER, address)
                }

                val replyPendingIntent =
                    PendingIntent.getBroadcast(
                        context.applicationContext,
                        notificationId,
                        replyIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                    )
                replyAction = NotificationCompat.Action.Builder(R.drawable.ic_send_vector, replyLabel, replyPendingIntent)
                    .addRemoteInput(remoteInput)
                    .build()
            }

            val largeIcon = bitmap ?: if (sender != null) {
                SimpleContactsHelper(context).getContactLetterIcon(sender)
            } else {
                null
            }

            val builder = NotificationCompat.Builder(context, channelId).apply {
                when (context.config.lockScreenVisibilitySetting) {
                    LOCK_SCREEN_SENDER_MESSAGE -> {
                        setLargeIcon(largeIcon)
                        setStyle(getMessagesStyle(address, body, notificationId, sender))
                    }

                    LOCK_SCREEN_SENDER -> {
                        setContentTitle(sender)
                        setLargeIcon(largeIcon)
                        val summaryText = context.getString(R.string.new_message)
                        setStyle(NotificationCompat.BigTextStyle().setSummaryText(summaryText).bigText(body))
                    }
                }
                color = context.getProperPrimaryColor()
                setSmallIcon(R.drawable.ic_messenger)
                setContentIntent(contentPendingIntent)
                priority = NotificationCompat.PRIORITY_MAX
                setDefaults(Notification.DEFAULT_LIGHTS)
                setCategory(Notification.CATEGORY_MESSAGE)
                setAutoCancel(true)
                setOnlyAlertOnce(alertOnlyOnce)
                setSound(soundUri2, AudioManager.STREAM_NOTIFICATION)
            }

            if (replyAction != null && context.config.lockScreenVisibilitySetting == LOCK_SCREEN_SENDER_MESSAGE) {
                builder.addAction(replyAction)
            }

            builder.addAction(com.ncautomation.commons.R.drawable.ic_check_vector, context.getString(R.string.mark_as_read), markAsReadPendingIntent)
                .setChannelId(channelId)
            if (isNoReplySms) {
                builder.addAction(
                    com.ncautomation.commons.R.drawable.ic_delete_vector,
                    context.getString(com.ncautomation.commons.R.string.delete),
                    deleteSmsPendingIntent
                ).setChannelId(channelId)
            }
            if (conversation?.customNotification == true && !isOreoPlus() && conversation?.vibrate == false) {
                builder.setVibrate(null)
            }
            notificationManager.notify(notificationId, builder.build())
        }
    }

    @SuppressLint("NewApi")
    fun showSendingFailedNotification(recipientName: String, threadId: Long) {
        maybeCreateChannel(name = context.getString(R.string.message_not_sent_short))

        val notificationId = generateRandomId().hashCode()
        val intent = Intent(context, ThreadActivity::class.java).apply {
            putExtra(THREAD_ID, threadId)
        }
        val contentPendingIntent = PendingIntent.getActivity(context, notificationId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)

        val summaryText = String.format(context.getString(R.string.message_sending_error), recipientName)
        val largeIcon = SimpleContactsHelper(context).getContactLetterIcon(recipientName)
        val builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL)
            .setContentTitle(context.getString(R.string.message_not_sent_short))
            .setContentText(summaryText)
            .setColor(context.getProperPrimaryColor())
            .setSmallIcon(R.drawable.ic_messenger)
            .setLargeIcon(largeIcon)
            .setStyle(NotificationCompat.BigTextStyle().bigText(summaryText))
            .setContentIntent(contentPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(Notification.DEFAULT_LIGHTS)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setChannelId(NOTIFICATION_CHANNEL)

        notificationManager.notify(notificationId, builder.build())
    }

    fun getConversationChannel(conversation:Conversation): String {
        createChannels()
        if (isOreoPlus()) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setLegacyStreamType(AudioManager.STREAM_NOTIFICATION)
                .build()

            val importance = IMPORTANCE_HIGH
            val id = "${NOTIFICATION_CHANNEL}_${conversation.threadId}"
            NotificationChannel(id, "From (${conversation.title})", importance).apply {
                setBypassDnd(false)
                enableLights(true)
                setSound(soundUri, audioAttributes)
                enableVibration(true)
                notificationManager.createNotificationChannel(this)
            }
            return id
        }
        return NOTIFICATION_CHANNEL
    }

    fun deleteChannel(id:Long){
        if (isOreoPlus()) {
            try {
                notificationManager.deleteNotificationChannel("${NOTIFICATION_CHANNEL}_${id}")
            } catch (e:Exception) {}
        }
    }
    private fun maybeCreateChannel(name: String, conversation:Conversation? = null) {
        if (isOreoPlus()) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setLegacyStreamType(AudioManager.STREAM_NOTIFICATION)
                .build()

            val id = NOTIFICATION_CHANNEL
            val importance = IMPORTANCE_HIGH
            NotificationChannel(id, name, importance).apply {
                setBypassDnd(false)
                enableLights(true)
                setSound(soundUri, audioAttributes)
                notificationManager.createNotificationChannel(this)
            }
        }
    }

    fun createChannels() {
        if (isOreoPlus()) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setLegacyStreamType(AudioManager.STREAM_NOTIFICATION)
                .build()

            var id = NOTIFICATION_CHANNEL
            val importance = IMPORTANCE_HIGH
            //at some point it would be nice to support a separate channel for foreground syncs
            NotificationChannel(id + "_foreground", "Foreground syncs", importance).apply {
                setBypassDnd(false)
                enableLights(true)
                setSound(null, audioAttributes)
                enableVibration(false)
                notificationManager.createNotificationChannel(this)
            }
            NotificationChannel(id, "General notifications", importance).apply {
                setBypassDnd(false)
                enableLights(true)
                setSound(soundUri, audioAttributes)
                enableVibration(true)
                notificationManager.createNotificationChannel(this)
            }
            getAvailableSIMs().forEach {sim ->
                NotificationChannel("${id}_sim_${sim.subscriptionId}", "Messages Sim ${sim.label}", importance).apply {
                    setBypassDnd(false)
                    enableLights(true)
                    setSound(soundUri, audioAttributes)
                    enableVibration(true)
                    notificationManager.createNotificationChannel(this)

                }
            }
        }
    }

    val availableSIMCards = ArrayList<SIMCard>()
    @SuppressLint("MissingPermission")
    fun getAvailableSIMs() : List<SIMCard> {
        if (availableSIMCards.size > 0) return  availableSIMCards
        val availableSIMs = context.subscriptionManagerCompat().activeSubscriptionInfoList ?: return ArrayList<SIMCard>()
        var sims = ArrayList<SIMCard>()
        if (availableSIMs.size > 0) {
            availableSIMs.forEachIndexed { index, subscriptionInfo ->
                var label = subscriptionInfo.displayName?.toString() ?: ""
                if (subscriptionInfo.number?.isNotEmpty() == true) {
                    label += " (${subscriptionInfo.number})"
                }
                val simCard = SIMCard(index + 1, subscriptionInfo.subscriptionId, label)
                sims.add(simCard)
            }
        }
        return sims
    }
    private fun getMessagesStyle(address: String, body: String, notificationId: Int, name: String?): NotificationCompat.MessagingStyle {
        val sender = if (name != null) {
            Person.Builder()
                .setName(name)
                .setKey(address)
                .build()
        } else {
            null
        }

        return NotificationCompat.MessagingStyle(user).also { style ->
            getOldMessages(notificationId).forEach {
                style.addMessage(it)
            }
            val newMessage = NotificationCompat.MessagingStyle.Message(body, System.currentTimeMillis(), sender)
            style.addMessage(newMessage)
        }
    }

    private fun getOldMessages(notificationId: Int): List<NotificationCompat.MessagingStyle.Message> {
        if (!isNougatPlus()) {
            return emptyList()
        }
        val currentNotification = notificationManager.activeNotifications.find { it.id == notificationId }
        return if (currentNotification != null) {
            val activeStyle = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(currentNotification.notification)
            activeStyle?.messages.orEmpty()
        } else {
            emptyList()
        }
    }
}

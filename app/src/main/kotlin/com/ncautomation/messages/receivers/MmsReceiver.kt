package com.ncautomation.messages.receivers

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.telephony.SmsManager
import android.webkit.MimeTypeMap
import com.bumptech.glide.Glide
import com.klinker.android.send_message.MmsReceivedReceiver
import com.ncautomation.commons.extensions.isNumberBlocked
import com.ncautomation.commons.extensions.normalizePhoneNumber
import com.ncautomation.commons.extensions.showErrorToast
import com.ncautomation.commons.helpers.ensureBackgroundThread
import com.ncautomation.messages.R
import com.ncautomation.messages.extensions.*
import com.ncautomation.messages.helpers.refreshMessages
import com.ncautomation.messages.messaging.sendMessageCompat
import com.ncautomation.messages.models.Conversation
import com.ncautomation.messages.models.Message
import java.io.File

// more info at https://github.com/klinker41/android-smsmms
class MmsReceiver : MmsReceivedReceiver() {

    override fun isAddressBlocked(context: Context, address: String): Boolean {
        val normalizedAddress = address.normalizePhoneNumber()
        return context.isNumberBlocked(normalizedAddress)
    }

    override fun onMessageReceived(context: Context, messageUri: Uri) {
        val mms = context.getLatestMMS() ?: return
        val address = mms.getSender()?.phoneNumbers?.first()?.normalizedNumber ?: ""

        val size = context.resources.getDimension(R.dimen.notification_large_icon_size).toInt()
        ensureBackgroundThread {
            val glideBitmap = try {
                Glide.with(context)
                    .asBitmap()
                    .load(mms.attachment!!.attachments.first().getUri())
                    .centerCrop()
                    .into(size, size)
                    .get()
            } catch (e: Exception) {
                null
            }

            Handler(Looper.getMainLooper()).post {
                context.showReceivedMessageNotification(mms.id, address, mms.body, mms.threadId, glideBitmap)
                val conversation = context.getConversations(mms.threadId).firstOrNull() ?: return@post
                ensureBackgroundThread {
                    context.insertOrUpdateConversation(conversation)
                    context.updateUnreadCountBadge(context.conversationsDB.getUnreadConversations())
                    refreshMessages()
                    autoForwardMessage(context, mms, conversation)
                }
            }
        }
    }

    private fun autoForwardMessage(context: Context, mms: Message, conversation: Conversation){
        if (context.config.autoForwardMms && !context.config.autoForwardDest.isNullOrBlank()){
            val addresses = listOf(context.config.autoForwardDest?:"")
            val subId = SmsManager.getDefaultSmsSubscriptionId()
            val attachments = mms.attachment!!.attachments;
            attachments.forEach {
                if (it.filename.isNullOrEmpty()){
                    val uri = it.getUri()
                    val mimeTypeMap = MimeTypeMap.getSingleton()
                    val extension = mimeTypeMap.getExtensionFromMimeType(it.mimetype)
                    val filename = File(uri.path).name + "." + extension
                    it.filename = filename
                }
            }
            context.sendMessageCompat(mms.body, addresses, subId, attachments, null,"Auto Forwarded Text")
        }
    }

    override fun onError(context: Context, error: String) = context.showErrorToast(context.getString(R.string.couldnt_download_mms))
}

package com.simplemobiletools.smsmessenger.activities

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.core.content.res.ResourcesCompat
import com.simplemobiletools.commons.dialogs.RadioGroupDialog
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.NavigationIcon
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.helpers.isRPlus
import com.simplemobiletools.commons.models.RadioItem
import com.simplemobiletools.commons.models.SimpleContact
import com.simplemobiletools.smsmessenger.adapters.ContactsAdapter
import com.simplemobiletools.smsmessenger.databinding.ActivityConversationDetailsBinding
import com.simplemobiletools.smsmessenger.dialogs.RenameConversationDialog
import com.simplemobiletools.smsmessenger.extensions.*
import com.simplemobiletools.smsmessenger.helpers.*
import com.simplemobiletools.smsmessenger.models.Conversation


class ConversationDetailsActivity : SimpleActivity() {

    private var threadId: Long = 0L
    private var conversation: Conversation? = null
    private lateinit var participants: ArrayList<SimpleContact>

    private val binding by viewBinding(ActivityConversationDetailsBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        isMaterialActivity = true
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        updateMaterialActivityViews(
            mainCoordinatorLayout = binding.conversationDetailsCoordinator,
            nestedView = binding.participantsRecyclerview,
            useTransparentNavigation = true,
            useTopSearchMenu = false
        )
        setupMaterialScrollListener(scrollingView = binding.participantsRecyclerview, toolbar = binding.conversationDetailsToolbar)

        threadId = intent.getLongExtra(THREAD_ID, 0L)
        ensureBackgroundThread {
            conversation = conversationsDB.getConversationWithThreadId(threadId)
            participants = if (conversation != null && conversation!!.isScheduled) {
                val message = messagesDB.getThreadMessages(conversation!!.threadId).firstOrNull()
                message?.participants ?: arrayListOf()
            } else {
                getThreadParticipants(threadId, null)
            }
            runOnUiThread {
                setupTextViews()
                setupParticipants()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        setupToolbar(binding.conversationDetailsToolbar, NavigationIcon.Arrow)
        updateTextColors(binding.conversationDetailsHolder)

        val primaryColor = getProperPrimaryColor()
        binding.conversationNameHeading.setTextColor(primaryColor)
        binding.membersHeading.setTextColor(primaryColor)
    }

    private fun setDetails(customNotification: Boolean, groupSendType: Int = SEND_TYPE_DEFAULT){
        ensureBackgroundThread {
            conversation = setConversationDetails(conversation!!, customNotification, groupSendType)
            runOnUiThread{
                binding.notificationsSwitch.isChecked = customNotification
                binding.customizeNotifications.beVisibleIf(customNotification)
                binding.groupSendMethod.text = when (groupSendType) {
                    SEND_TYPE_MMS -> "MMS"
                    SEND_TYPE_SMS -> "SMS"
                    else -> "Default (${ if (config.sendGroupMessageMMS) "MMS" else "SMS" })"
                }
            }
        }
    }

    private fun setupTextViews() {
        binding.conversationName.apply {
            ResourcesCompat.getDrawable(resources, com.simplemobiletools.commons.R.drawable.ic_edit_vector, theme)?.apply {
                applyColorFilter(getProperTextColor())
                setCompoundDrawablesWithIntrinsicBounds(null, null, this, null)
            }

            text = conversation?.title
            setOnClickListener {
                RenameConversationDialog(this@ConversationDetailsActivity, conversation!!) { title ->
                    text = title
                    ensureBackgroundThread {
                        conversation = renameConversation(conversation!!, newTitle = title)
                    }
                }
            }
        }
        binding.customizeNotifications.setOnClickListener{
             val notificationHelper = NotificationHelper(this)
             var channelid = notificationHelper.getConversationChannel(conversation!!)
            val intent: Intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, channelid)
            startActivity(intent)
        }
        binding.notificationsSwitch.beVisibleIf(isRPlus())
        binding.customizeNotifications.beVisibleIf(isRPlus() && conversation!!.customNotification)
        binding.notificationsSwitch.isChecked = conversation!!.customNotification
        binding.notificationsSwitch.setOnCheckedChangeListener { _, isChecked ->
            conversation!!.customNotification = isChecked
            setDetails(isChecked, conversation!!.groupSendType)
            if (!isChecked) NotificationHelper(this@ConversationDetailsActivity).deleteChannel(conversation!!.threadId)
        }

        binding.groupSendMethodHolder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(SEND_TYPE_DEFAULT, "Default (${ if (config.sendGroupMessageMMS) "MMS" else "SMS" })"),
                RadioItem(SEND_TYPE_MMS, "MMS"),
                RadioItem(SEND_TYPE_SMS, "SMS"),
            )

            RadioGroupDialog(this@ConversationDetailsActivity, items, conversation!!.groupSendType) {
                setDetails(conversation!!.customNotification, it as Int)
            }
        }
        binding.groupSendMethod.text = when (conversation?.groupSendType) {
            SEND_TYPE_MMS -> "MMS"
            SEND_TYPE_SMS -> "SMS"
            else -> "Default (${ if (config.sendGroupMessageMMS) "MMS" else "SMS" })"
        }
    }

    private fun setupParticipants() {
        val adapter = ContactsAdapter(this, participants, binding.participantsRecyclerview) {
            val contact = it as SimpleContact
            val address = contact.phoneNumbers.first().normalizedNumber
            getContactFromAddress(address) { simpleContact ->
                if (simpleContact != null) {
                    startContactDetailsIntent(simpleContact)
                }
            }
        }
        binding.participantsRecyclerview.adapter = adapter
    }
}

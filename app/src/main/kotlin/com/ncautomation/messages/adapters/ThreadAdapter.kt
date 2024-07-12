package com.ncautomation.messages.adapters

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.util.Size
import android.util.TypedValue
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.content.res.AppCompatResources
import androidx.constraintlayout.widget.ConstraintSet
import androidx.recyclerview.widget.DiffUtil
import androidx.viewbinding.ViewBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.FitCenter
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import com.ncautomation.commons.adapters.MyRecyclerViewListAdapter
import com.ncautomation.commons.dialogs.ConfirmationDialog
import com.ncautomation.commons.extensions.*
import com.ncautomation.commons.helpers.SimpleContactsHelper
import com.ncautomation.commons.helpers.ensureBackgroundThread
import com.ncautomation.commons.views.MyRecyclerView
import com.ncautomation.messages.R
import com.ncautomation.messages.activities.NewConversationActivity
import com.ncautomation.messages.activities.SimpleActivity
import com.ncautomation.messages.activities.ThreadActivity
import com.ncautomation.messages.activities.VCardViewerActivity
import com.ncautomation.messages.databinding.*
import com.ncautomation.messages.dialogs.DeleteConfirmationDialog
import com.ncautomation.messages.dialogs.MessageDetailsDialog
import com.ncautomation.messages.dialogs.SelectTextDialog
import com.ncautomation.messages.extensions.*
import com.ncautomation.messages.helpers.*
import com.ncautomation.messages.models.Attachment
import com.ncautomation.messages.models.Message
import com.ncautomation.messages.models.ThreadItem
import com.ncautomation.messages.models.ThreadItem.*
import java.util.Calendar
import java.util.Locale

class ThreadAdapter(
    activity: SimpleActivity,
    recyclerView: MyRecyclerView,
    itemClick: (Any) -> Unit,
    val isRecycleBin: Boolean,
    val deleteMessages: (messages: List<Message>, toRecycleBin: Boolean, fromRecycleBin: Boolean) -> Unit
) : MyRecyclerViewListAdapter<ThreadItem>(activity, recyclerView, ThreadItemDiffCallback(), itemClick) {
    private var fontSize = activity.getTextSize()
    public var messageFontSize = activity.config.messageFontSize // getTextSize()

    @SuppressLint("MissingPermission")
    private val hasMultipleSIMCards = (activity.subscriptionManagerCompat().activeSubscriptionInfoList?.size ?: 0) > 1
    private val maxChatBubbleWidth = activity.usableScreenSize.x * 0.8f

    init {
        setupDragListener(true)
        setHasStableIds(true)
    }

    override fun getActionMenuId() = R.menu.cab_thread

    override fun prepareActionMode(menu: Menu) {
        val isOneItemSelected = isOneItemSelected()

        val selectedItem = getSelectedItems().firstOrNull() as? Message
        val stared = (activity as ThreadActivity).isStared(selectedItem?.id?.toString()?:"")
        val hasText = selectedItem?.body != null && selectedItem.body != ""
        menu.apply {
            findItem(R.id.cab_copy_to_clipboard).isVisible = isOneItemSelected && hasText
            findItem(R.id.cab_save_as).isVisible = isOneItemSelected && selectedItem?.attachment?.attachments?.size == 1
            findItem(R.id.cab_share).isVisible = isOneItemSelected && hasText
            findItem(R.id.cab_forward_message).isVisible = isOneItemSelected
            findItem(R.id.cab_select_text).isVisible = isOneItemSelected && hasText
            findItem(R.id.cab_properties).isVisible = isOneItemSelected
            findItem(R.id.cab_restore).isVisible = isRecycleBin
            findItem(R.id.cab_star).isVisible = isOneItemSelected
        }
    }

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_copy_to_clipboard -> copyToClipboard()
            R.id.cab_save_as -> saveAs()
            R.id.cab_share -> shareText()
            R.id.cab_forward_message -> forwardMessage()
            R.id.cab_select_text -> selectText()
            R.id.cab_delete -> askConfirmDelete()
            R.id.cab_restore -> askConfirmRestore()
            R.id.cab_select_all -> selectAll()
            R.id.cab_properties -> showMessageDetails()
            R.id.cab_star -> starMessage()
        }
    }

    override fun getSelectableItemCount() = currentList.filterIsInstance<Message>().size

    override fun getIsItemSelectable(position: Int) = !isThreadDateTime(position)

    override fun getItemSelectionKey(position: Int) = (currentList.getOrNull(position) as? Message)?.hashCode()

    override fun getItemKeyPosition(key: Int) = currentList.indexOfFirst { (it as? Message)?.hashCode() == key }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = when (viewType) {
            THREAD_LOADING -> ItemThreadLoadingBinding.inflate(layoutInflater, parent, false)
            THREAD_DATE_TIME -> ItemThreadDateTimeBinding.inflate(layoutInflater, parent, false)
            THREAD_SENT_MESSAGE_ERROR -> ItemThreadErrorBinding.inflate(layoutInflater, parent, false)
            THREAD_SENT_MESSAGE_SENT -> ItemThreadSuccessBinding.inflate(layoutInflater, parent, false)
            THREAD_SENT_MESSAGE_SENDING -> ItemThreadSendingBinding.inflate(layoutInflater, parent, false)
            else -> ItemMessageBinding.inflate(layoutInflater, parent, false)
        }

        return ThreadViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val isClickable = item is ThreadError || item is Message
        val isLongClickable = item is Message
        holder.bindView(item, isClickable, isLongClickable) { itemView, _ ->
            when (item) {
                is ThreadLoading -> setupThreadLoading(itemView)
                is ThreadDateTime -> setupDateTime(itemView, item)
                is ThreadError -> setupThreadError(itemView)
                is ThreadSent -> setupThreadSuccess(itemView, item.delivered)
                is ThreadSending -> setupThreadSending(itemView)
                is Message -> setupView(holder, itemView, item)
            }
        }
        bindViewHolder(holder)
    }

    override fun getItemId(position: Int): Long {
        return when (val item = getItem(position)) {
            is Message -> Message.getStableId(item)
            else -> item.hashCode().toLong()
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (val item = getItem(position)) {
            is ThreadLoading -> THREAD_LOADING
            is ThreadDateTime -> THREAD_DATE_TIME
            is ThreadError -> THREAD_SENT_MESSAGE_ERROR
            is ThreadSent -> THREAD_SENT_MESSAGE_SENT
            is ThreadSending -> THREAD_SENT_MESSAGE_SENDING
            is Message -> if (item.isReceivedMessage()) THREAD_RECEIVED_MESSAGE else THREAD_SENT_MESSAGE
        }
    }

    private fun copyToClipboard() {
        val firstItem = getSelectedItems().firstOrNull() as? Message ?: return
        activity.copyToClipboard(firstItem.body)
    }

    private fun saveAs() {
        val firstItem = getSelectedItems().firstOrNull() as? Message ?: return
        val attachment = firstItem.attachment?.attachments?.first() ?: return
        (activity as ThreadActivity).saveMMS(attachment.mimetype, attachment.uriString)
    }

    private fun shareText() {
        val firstItem = getSelectedItems().firstOrNull() as? Message ?: return
        activity.shareTextIntent(firstItem.body)
    }

    private fun selectText() {
        val firstItem = getSelectedItems().firstOrNull() as? Message ?: return
        if (firstItem.body.trim().isNotEmpty()) {
            SelectTextDialog(activity, firstItem.body)
        }
    }

    private fun showMessageDetails() {
        val message = getSelectedItems().firstOrNull() as? Message ?: return
        MessageDetailsDialog(activity, message)
    }

    private fun starMessage() {
        val message = getSelectedItems().firstOrNull() as? Message ?: return
        (activity as ThreadActivity).starMessage(message.id.toString())
    }

    private fun askConfirmDelete() {
        val itemsCnt = selectedKeys.size

        // not sure how we can get UnknownFormatConversionException here, so show the error and hope that someone reports it
        val items = try {
            resources.getQuantityString(R.plurals.delete_messages, itemsCnt, itemsCnt)
        } catch (e: Exception) {
            activity.showErrorToast(e)
            return
        }

        val baseString = if (activity.config.useRecycleBin && !isRecycleBin) {
            com.ncautomation.commons.R.string.move_to_recycle_bin_confirmation
        } else {
            com.ncautomation.commons.R.string.deletion_confirmation
        }
        val question = String.format(resources.getString(baseString), items)

        DeleteConfirmationDialog(activity, question, activity.config.useRecycleBin && !isRecycleBin) { skipRecycleBin ->
            ensureBackgroundThread {
                val messagesToRemove = getSelectedItems()
                if (messagesToRemove.isNotEmpty()) {
                    val toRecycleBin = !skipRecycleBin && activity.config.useRecycleBin && !isRecycleBin
                    deleteMessages(messagesToRemove.filterIsInstance<Message>(), toRecycleBin, false)
                }
            }
        }
    }

    private fun askConfirmRestore() {
        val itemsCnt = selectedKeys.size

        // not sure how we can get UnknownFormatConversionException here, so show the error and hope that someone reports it
        val items = try {
            resources.getQuantityString(R.plurals.delete_messages, itemsCnt, itemsCnt)
        } catch (e: Exception) {
            activity.showErrorToast(e)
            return
        }

        val baseString = R.string.restore_confirmation
        val question = String.format(resources.getString(baseString), items)

        ConfirmationDialog(activity, question) {
            ensureBackgroundThread {
                val messagesToRestore = getSelectedItems()
                if (messagesToRestore.isNotEmpty()) {
                    deleteMessages(messagesToRestore.filterIsInstance<Message>(), false, true)
                }
            }
        }
    }

    private fun forwardMessage() {
        val message = getSelectedItems().firstOrNull() as? Message ?: return
        val attachment = message.attachment?.attachments?.firstOrNull()
        Intent(activity, NewConversationActivity::class.java).apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, message.body)

            if (attachment != null) {
                putExtra(Intent.EXTRA_STREAM, attachment.getUri())
                putExtra(Intent.EXTRA_REFERRER, "MessageForward")
            }

            activity.startActivity(this)
        }
    }

    private fun getSelectedItems() = currentList.filter { selectedKeys.contains((it as? Message)?.hashCode() ?: 0) } as ArrayList<ThreadItem>

    private fun isThreadDateTime(position: Int) = currentList.getOrNull(position) is ThreadDateTime

    fun updateMessages(newMessages: ArrayList<ThreadItem>, scrollPosition: Int = -1) {
        val latestMessages = newMessages.toMutableList()
        submitList(latestMessages) {
            if (scrollPosition != -1) {
                recyclerView.scrollToPosition(scrollPosition)
            }
        }
    }

    private fun setupView(holder: ViewHolder, view: View, message: Message) {
        val stared = (activity as ThreadActivity).isStared(message.id.toString())
        ItemMessageBinding.bind(view).apply {
            threadMessageHolder.id = message.id.toInt()
            threadMessageHolder.isSelected = selectedKeys.contains(message.hashCode())
            threadMessageBody.apply {
                text = message.body
                textSize = messageFontSize // setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)
                beVisibleIf(message.body.isNotEmpty())
                setOnLongClickListener {
                    holder.viewLongClicked()
                    true
                }

                setOnClickListener {
                    holder.viewClicked(message)
                }
            }
            threadMessageStar.beVisibleIf(stared)
            threadMessageSenderName.setTextColor(textColor)
            threadMessageTime.setTextColor(textColor)
            threadMessageMms.setTextColor(textColor)
            if (message.isReceivedMessage()) {
                setupReceivedMessageView(messageBinding = this, message = message)
            } else {
                setupSentMessageView(messageBinding = this, message = message)
            }

            if (message.attachment?.attachments?.isNotEmpty() == true) {
                threadMessageAttachmentsHolder.beVisible()
                threadMessageAttachmentsHolder.removeAllViews()
                val showImages = false
                for (attachment in message.attachment.attachments) {
                    val mimetype = attachment.mimetype
                    when {
                        (mimetype.isImageMimeType() || mimetype.isVideoMimeType()) && showImages-> setupImageView(holder, binding = this, message, attachment)
                        mimetype.isVCardMimeType() -> setupVCardView(holder, threadMessageAttachmentsHolder, message, attachment)
                        else -> setupFileView(holder, threadMessageAttachmentsHolder, message, attachment)
                    }
                    //threadMessagePlayOutline.beVisibleIf(mimetype.startsWith("video/"))
                }
            } else {
                threadMessageAttachmentsHolder.beGone()
                threadMessagePlayOutline.beGone()
            }
        }
    }

    private fun setupReceivedMessageView(messageBinding: ItemMessageBinding, message: Message) {
        messageBinding.apply {
            with(ConstraintSet()) {
                clone(threadMessageHolder)
                clear(threadMessageWrapper.id, ConstraintSet.END)
                connect(threadMessageWrapper.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
                applyTo(threadMessageHolder)
            }

            threadMessageSenderPhoto.beVisible()
            threadMessageSenderPhoto.setOnClickListener {
                val contact = message.getSender()!!
                activity.getContactFromAddress(contact.phoneNumbers.first().normalizedNumber) {
                    if (it != null) {
                        activity.startContactDetailsIntent(it)
                    }
                }
            }
            threadMessageSenderPhoto.beGone()
            threadMessageSenderName.beGoneIf(message.participants.count() == 1)
            threadMessageSenderName.text = message.senderName
            threadMessageMms.beGoneIf(!message.isMMS)

            try {
                var sims = (activity as ThreadActivity).getAvailableSIMs()
                var sim = sims.firstOrNull() { it.subscriptionId == message.subscriptionId}
                threadMessageSimNumber.text = (sim?.id?:9).toString()
                var color = (activity as ThreadActivity).getSimColor(threadMessageSimNumber.text.toInt())
                threadMessageSimIcon.setColorFilter(color)
            } catch (e:Exception) {}

            val cal = Calendar.getInstance(Locale.ENGLISH)
            cal.timeInMillis = message.date * 1000L
            threadMessageTime.text = DateFormat.format("h:mm a", cal).toString()
            threadMessageBody.apply {
                background = AppCompatResources.getDrawable(activity, R.drawable.item_received_background)
                setTextColor(textColor)
                setLinkTextColor(activity.getProperPrimaryColor())
            }

            if (!activity.isFinishing && !activity.isDestroyed) {
                val contactLetterIcon = SimpleContactsHelper(activity).getContactLetterIcon(message.senderName)
                val placeholder = BitmapDrawable(activity.resources, contactLetterIcon)

                val options = RequestOptions()
                    .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                    .error(placeholder)
                    .centerCrop()

                Glide.with(activity)
                    .load(message.senderPhotoUri)
                    .placeholder(placeholder)
                    .apply(options)
                    .apply(RequestOptions.circleCropTransform())
                    .into(threadMessageSenderPhoto)
            }
        }
    }

    fun Int.formatTime(context: Context, hideTimeAtOtherDays: Boolean, showYearEvenIfCurrent: Boolean): String {
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

    fun Int.formatDateOrTime(format: String): String {
        val cal = Calendar.getInstance(Locale.ENGLISH)
        cal.timeInMillis = this * 1000L
        return DateFormat.format(format, cal).toString()
    }

    private fun setupSentMessageView(messageBinding: ItemMessageBinding, message: Message) {
        messageBinding.apply {
            with(ConstraintSet()) {
                clone(threadMessageHolder)
                clear(threadMessageWrapper.id, ConstraintSet.START)
                connect(threadMessageWrapper.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
                applyTo(threadMessageHolder)
            }

            val primaryColor = activity.getProperPrimaryColor()
            val contrastColor = primaryColor.getContrastColor()

            threadMessageWrapper.rotationY = 180f
            threadMessageBody.rotationY = 180f
            threadMessageTimeHolder.rotationY = 180f
            threadMessageSenderPhoto.beGone()
            threadMessageSenderName.beGone()
            threadMessageMms.beGoneIf(!message.isMMS)
            try {
                var sims = (activity as ThreadActivity).getAvailableSIMs()
                var sim = sims.firstOrNull() { it.subscriptionId == message.subscriptionId}
                threadMessageSimNumber.text = (sim?.id?:9).toString()
                var color = (activity as ThreadActivity).getSimColor(threadMessageSimNumber.text.toInt())
                threadMessageSimIcon.setColorFilter(color)
            } catch (e:Exception) {}

            val cal = Calendar.getInstance(Locale.ENGLISH)
            cal.timeInMillis = message.date * 1000L
            threadMessageTime.text = DateFormat.format("h:mm a", cal).toString()



            threadMessageBody.apply {
                background = AppCompatResources.getDrawable(activity, R.drawable.item_sent_background)
                background.applyColorFilter(primaryColor)
                setTextColor(contrastColor)
                setLinkTextColor(contrastColor)

                if (message.isScheduled) {
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                    val scheduledDrawable = AppCompatResources.getDrawable(activity, com.ncautomation.commons.R.drawable.ic_clock_vector)?.apply {
                        applyColorFilter(contrastColor)
                        val size = lineHeight
                        setBounds(0, 0, size, size)
                    }

                    setCompoundDrawables(null, null, scheduledDrawable, null)
                } else {
                    typeface = Typeface.DEFAULT
                    setCompoundDrawables(null, null, null, null)
                }
            }
        }
    }

    private fun setupImageView(holder: ViewHolder, binding: ItemMessageBinding, message: Message, attachment: Attachment) = binding.apply {
        val mimetype = attachment.mimetype
        val uri = attachment.getUri()
        //val uri = Uri.parse("android.resource://your.package.name/" + R.drawable.ic_image_vector);
        val imageView = ItemAttachmentImageBinding.inflate(layoutInflater)
//        imageView.root.maxWidth = 96
//        imageView.root.maxHeight = 96
        threadMessageAttachmentsHolder.addView(imageView.root)
        if (!message.isReceivedMessage()){
            threadMessageAttachmentsHolder.rotationY = 180f
        }
        val placeholderDrawable = ColorDrawable(Color.TRANSPARENT)
        val isTallImage = attachment.height > attachment.width
        val transformation = if (isTallImage) CenterCrop() else FitCenter()
        val options = RequestOptions()
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .placeholder(placeholderDrawable)
            .transform(transformation)

        var builder = Glide.with(root.context)
            .load(uri)
            .apply(options)
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean): Boolean {
                    threadMessagePlayOutline.beGone()
                    threadMessageAttachmentsHolder.removeView(imageView.root)
                    return false
                }

                override fun onResourceReady(dr: Drawable, a: Any, t: Target<Drawable>, d: DataSource, i: Boolean) = false
            })

        // limit attachment sizes to avoid causing OOM
        var wantedAttachmentSize = Size(attachment.width, attachment.height)
        if (wantedAttachmentSize.width > maxChatBubbleWidth) {
            val newHeight = wantedAttachmentSize.height / (wantedAttachmentSize.width / maxChatBubbleWidth)
            wantedAttachmentSize = Size(maxChatBubbleWidth.toInt(), newHeight.toInt())
        }
        //wantedAttachmentSize = Size(24,24)

        builder = if (isTallImage) {
            builder.override(wantedAttachmentSize.width, wantedAttachmentSize.width)
        } else {
            builder.override(wantedAttachmentSize.width, wantedAttachmentSize.height)
        }

        try {
            builder.into(imageView.attachmentImage)
        } catch (ignore: Exception) {
        }

        imageView.attachmentImage.setOnClickListener {
            if (actModeCallback.isSelectable) {
                holder.viewClicked(message)
            } else {
                activity.launchViewIntent(uri, mimetype, attachment.filename)
            }
        }
        imageView.root.setOnLongClickListener {
            holder.viewLongClicked()
            true
        }
    }

    private fun setupVCardView(holder: ViewHolder, parent: LinearLayout, message: Message, attachment: Attachment) {
        val uri = attachment.getUri()
        val vCardView = ItemAttachmentVcardBinding.inflate(layoutInflater).apply {
            setupVCardPreview(
                activity = activity,
                uri = uri,
                onClick = {
                    if (actModeCallback.isSelectable) {
                        holder.viewClicked(message)
                    } else {
                        val intent = Intent(activity, VCardViewerActivity::class.java).also {
                            it.putExtra(EXTRA_VCARD_URI, uri)
                        }
                        activity.startActivity(intent)
                    }
                },
                onLongClick = { holder.viewLongClicked() }
            )
            if (!message.isReceivedMessage()) {
                vcardAttachmentHolder.rotationY = 180f
            }
        }.root

        parent.addView(vCardView)
    }

    private fun setupFileView(holder: ViewHolder, parent: LinearLayout, message: Message, attachment: Attachment) {
        val mimetype = attachment.mimetype
        //if the attachment is a text file that we sent then don't show it.
        if (!message.isReceivedMessage() && mimetype == "application/txt" && attachment.filename == "text.txt") return
        val uri = attachment.getUri()
        val attachmentView = ItemAttachmentDocumentBinding.inflate(layoutInflater).apply {
            setupDocumentPreview(
                uri = uri,
                title = attachment.filename,
                mimeType = attachment.mimetype,
                onClick = {
                    if (actModeCallback.isSelectable) {
                        holder.viewClicked(message)
                    } else {
                        activity.launchViewIntent(uri, mimetype, attachment.filename)
                    }
                },
                onLongClick = { holder.viewLongClicked() }
            )
            if (!message.isReceivedMessage()){
                documentAttachmentHolder.rotationY = 180f
            }
        }.root

        parent.addView(attachmentView)
    }

    private fun setupDateTime(view: View, dateTime: ThreadDateTime) {
        ItemThreadDateTimeBinding.bind(view).apply {
            threadDateTime.apply {
                var format = "EEEE, MMMM d"
                if (!dateTime.date.isThisYear()) format += ", yyyy"
                text = dateTime.date.formatDateOrTime(format)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)
            }
            threadDateTime.setTextColor(textColor)
        }
    }

    private fun setupThreadSuccess(view: View, isDelivered: Boolean) {
        ItemThreadSuccessBinding.bind(view).apply {
            threadSuccess.setImageResource(if (isDelivered) R.drawable.ic_check_double_vector else com.ncautomation.commons.R.drawable.ic_check_vector)
            threadSuccess.applyColorFilter(textColor)
        }
    }

    private fun setupThreadError(view: View) {
        val binding = ItemThreadErrorBinding.bind(view)
        binding.threadError.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)
    }

    private fun setupThreadSending(view: View) {
        ItemThreadSendingBinding.bind(view).threadSending.apply {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)
            setTextColor(textColor)
        }
    }

    private fun setupThreadLoading(view: View) {
        val binding = ItemThreadLoadingBinding.bind(view)
        binding.threadLoading.setIndicatorColor(properPrimaryColor)
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        if (!activity.isDestroyed && !activity.isFinishing) {
            val binding = (holder as ThreadViewHolder).binding
            if (binding is ItemMessageBinding) {
                Glide.with(activity).clear(binding.threadMessageSenderPhoto)
            }
        }
    }

    inner class ThreadViewHolder(val binding: ViewBinding) : ViewHolder(binding.root)
}

private class ThreadItemDiffCallback : DiffUtil.ItemCallback<ThreadItem>() {

    override fun areItemsTheSame(oldItem: ThreadItem, newItem: ThreadItem): Boolean {
        if (oldItem::class.java != newItem::class.java) return false
        return when (oldItem) {
            is ThreadLoading -> oldItem.id == (newItem as ThreadLoading).id
            is ThreadDateTime -> oldItem.date == (newItem as ThreadDateTime).date
            is ThreadError -> oldItem.messageId == (newItem as ThreadError).messageId
            is ThreadSent -> oldItem.messageId == (newItem as ThreadSent).messageId
            is ThreadSending -> oldItem.messageId == (newItem as ThreadSending).messageId
            is Message -> Message.areItemsTheSame(oldItem, newItem as Message)
        }
    }

    override fun areContentsTheSame(oldItem: ThreadItem, newItem: ThreadItem): Boolean {
        if (oldItem::class.java != newItem::class.java) return false
        return when (oldItem) {
            is ThreadLoading, is ThreadSending -> true
            is ThreadDateTime -> oldItem.simID == (newItem as ThreadDateTime).simID
            is ThreadError -> oldItem.messageText == (newItem as ThreadError).messageText
            is ThreadSent -> oldItem.delivered == (newItem as ThreadSent).delivered
            is Message -> Message.areContentsTheSame(oldItem, newItem as Message)
        }
    }
}
package com.simplemobiletools.smsmessenger.dialogs

import android.net.Uri
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.smsmessenger.activities.SimpleActivity
import com.simplemobiletools.smsmessenger.databinding.DialogExportMessagesProgressBinding
import com.simplemobiletools.smsmessenger.extensions.config
import com.simplemobiletools.smsmessenger.helpers.MessagesReader
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ExportMessagesProgressDialog(
    private val activity: SimpleActivity,
    private val uri: Uri,
) {
    private val config = activity.config
    public var status: TextView
    public var isCanceled: Boolean = false

    init {
        val binding = DialogExportMessagesProgressBinding.inflate(activity.layoutInflater).apply {
            exportMessagesStatus.text = "Starting Export..."
        }
        status = binding.exportMessagesStatus
        activity.getAlertDialogBuilder()
            .setNegativeButton(com.simplemobiletools.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this, 0,"Exporting Messages") { alertDialog ->
                    alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                        isCanceled = true
                        alertDialog.cancel()
                    }
                    alertDialog.setCanceledOnTouchOutside(false)
                    exportMessages(alertDialog)
                }
            }

    }

    private fun exportMessages(alertDialog: AlertDialog) {

        Thread() {
            run() {
                ensureBackgroundThread {
                    try {

                        MessagesReader(activity).getMessagesToExport(config.exportSms, config.exportMms, this, config.exportAttachments) { messagesToExport ->
                            if (messagesToExport.isEmpty()) {
                                activity.toast(com.simplemobiletools.commons.R.string.no_entries_for_exporting)
                                alertDialog.cancel()
                                return@getMessagesToExport
                            }
                            if (isCanceled) return@getMessagesToExport
                            status.text = "Preparing messages for export..."
                            val json = Json { encodeDefaults = true }
                            if (isCanceled) return@getMessagesToExport
                            //var jsonList = ArrayList<String>()
                            var cnt = messagesToExport.count()
                            var num = 0

                            val outputStream = activity.contentResolver.openOutputStream(uri)!!
                            outputStream.use {
                                for (messagesBackup in messagesToExport) {
                                    num++
                                    status.text = "Writing to file\nMessage $num of $cnt"
                                    Log.d("Tag", num.toString())
                                    if (isCanceled) return@getMessagesToExport
                                    var jsonString = if (num == 1) "[" else "\n,"
                                    jsonString += json.encodeToString(messagesBackup)

                                    it.write(jsonString.toByteArray())
                                }
                                it.write("]".toByteArray())
                            }
                           // val jsonString = "[${jsonList.joinToString(",")}]"
                            //val jsonString = json.encodeToString(messagesToExport)
                            if (isCanceled) return@getMessagesToExport

                            //status.text = "Saving to file..."
//                            val outputStream = activity.contentResolver.openOutputStream(uri)!!
//                            outputStream.use {
//
//
//                                it.write(jsonString.toByteArray())
//                            }
                            activity.toast(com.simplemobiletools.commons.R.string.exporting_successful)
                        }
                        alertDialog.cancel()
                    } catch (e: Exception) {
                        activity.showErrorToast(e)
                    }
                }

            }
        }.start()
    }
}

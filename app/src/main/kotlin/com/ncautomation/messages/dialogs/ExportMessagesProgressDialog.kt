package com.ncautomation.messages.dialogs

import android.net.Uri
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.ncautomation.commons.extensions.*
import com.ncautomation.commons.helpers.ensureBackgroundThread
import com.ncautomation.messages.activities.SimpleActivity
import com.ncautomation.messages.databinding.DialogExportMessagesProgressBinding
import com.ncautomation.messages.extensions.config
import com.ncautomation.messages.helpers.MessagesReader
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
            .setNegativeButton(com.ncautomation.commons.R.string.cancel, null)
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
                                activity.toast(com.ncautomation.commons.R.string.no_entries_for_exporting)
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
                            activity.toast(com.ncautomation.commons.R.string.exporting_successful)
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

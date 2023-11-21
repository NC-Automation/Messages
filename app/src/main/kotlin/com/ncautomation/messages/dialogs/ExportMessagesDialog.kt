package com.ncautomation.messages.dialogs

import androidx.appcompat.app.AlertDialog
import com.ncautomation.commons.extensions.*
import com.ncautomation.messages.R
import com.ncautomation.messages.activities.SimpleActivity
import com.ncautomation.messages.databinding.DialogExportMessagesBinding
import com.ncautomation.messages.extensions.config

class ExportMessagesDialog(
    private val activity: SimpleActivity,
    private val callback: (fileName: String) -> Unit,
) {
    private val config = activity.config

    init {
        val binding = DialogExportMessagesBinding.inflate(activity.layoutInflater).apply {
            exportSmsCheckbox.isChecked = config.exportSms
            exportMmsCheckbox.isChecked = config.exportMms
            exportAttachmentsCheckbox.isChecked = config.exportAttachments
            exportMessagesFilename.setText(
                activity.getString(R.string.messages) + "_" + activity.getCurrentFormattedDateTime()
            )
        }

        activity.getAlertDialogBuilder()
            .setPositiveButton(com.ncautomation.commons.R.string.ok, null)
            .setNegativeButton(com.ncautomation.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this, R.string.export_messages) { alertDialog ->
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        config.exportSms = binding.exportSmsCheckbox.isChecked
                        config.exportMms = binding.exportMmsCheckbox.isChecked
                        config.exportAttachments = binding.exportAttachmentsCheckbox.isChecked
                        val filename = binding.exportMessagesFilename.value
                        when {
                            filename.isEmpty() -> activity.toast(com.ncautomation.commons.R.string.empty_name)
                            filename.isAValidFilename() -> {
                                callback(filename)
                                alertDialog.dismiss()
                            }

                            else -> activity.toast(com.ncautomation.commons.R.string.invalid_name)
                        }
                    }
                }
            }
    }
}

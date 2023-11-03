package com.simplemobiletools.smsmessenger.dialogs

import android.content.Context
import android.content.res.ColorStateList
import android.net.Uri
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import com.simplemobiletools.commons.dialogs.dialogTextColor
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.smsmessenger.R
import com.simplemobiletools.smsmessenger.activities.SimpleActivity
import com.simplemobiletools.smsmessenger.databinding.DialogImportMessagesBinding
import com.simplemobiletools.smsmessenger.extensions.config
import com.simplemobiletools.smsmessenger.helpers.MessagesImporter
import com.simplemobiletools.smsmessenger.models.ImportResult
import com.simplemobiletools.smsmessenger.models.MessagesBackup

class ImportMessagesDialog(
    private val activity: SimpleActivity,
    private val uri: Uri,
) {

    private val config = activity.config
    private lateinit var importer: MessagesImporter
    private var binding: DialogImportMessagesBinding
    private lateinit var alert: AlertDialog
    public var isCanceled: Boolean = false

    init {
        var ignoreClicks = false
        var importComplete = false
        binding = DialogImportMessagesBinding.inflate(activity.layoutInflater).apply {
            importSmsCheckbox.isChecked = config.importSms
            importMmsCheckbox.isChecked = config.importMms
        }
        var dlg = this
        activity.getAlertDialogBuilder()
            .setPositiveButton(com.simplemobiletools.commons.R.string.ok, null)
            .setNegativeButton(com.simplemobiletools.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this, R.string.import_messages) { alertDialog ->
                    importer = MessagesImporter(activity, dlg)
                    alert = alertDialog
                    alertDialog.setCanceledOnTouchOutside(false)
                    alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                        isCanceled = true
                        alertDialog.dismiss()
                    }
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        if (importComplete){
                            alertDialog.dismiss()
                            return@setOnClickListener
                        }
                        if (ignoreClicks) {
                            return@setOnClickListener
                        }

                        if (!binding.importSmsCheckbox.isChecked && !binding.importMmsCheckbox.isChecked) {
                            activity.toast(R.string.no_option_selected)
                            return@setOnClickListener
                        }
                        ignoreClicks = true

                        alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).beGone()
                        config.importSms = binding.importSmsCheckbox.isChecked
                        config.importMms = binding.importMmsCheckbox.isChecked
                        binding.importSmsCheckbox.beGone()
                        binding.importMmsCheckbox.beGone()
                        binding.importStatus1.text = "Reading messages from file."
                        binding.importStatus1.beVisible()
                        binding.importStatus1.setTextColor(activity.getProperTextColor())
                        binding.importStatus2.setTextColor(activity.getProperTextColor())
                        binding.importProgressbar.beVisible()

                        ensureBackgroundThread {
                            importer.importMessages(uri)
                            importComplete = true
                            activity.runOnUiThread {
                                alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).beVisible()
                                alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE).beGone()
                                binding.importProgressbar.beGone()
                            }
                        }
                    }
                }
            }
    }

    fun setStatus(status1:String, status2: String = ""){
        activity.runOnUiThread {
            binding.importStatus1.beGoneIf(status1.isNullOrEmpty())
            binding.importStatus1.text = status1
            binding.importStatus2.beGoneIf(status2.isNullOrEmpty())
            binding.importStatus2.text = status2
        }
    }

    fun setProgress(progress:Int, max: Int) {
        activity.runOnUiThread {
            binding.importProgressbar.isIndeterminate = false
            binding.importProgressbar.progress = progress
            binding.importProgressbar.max = max
        }
    }

    private fun handleParseResult(result: ImportResult) {
        activity.toast(
            when (result) {
                ImportResult.IMPORT_OK -> com.simplemobiletools.commons.R.string.importing_successful
                ImportResult.IMPORT_PARTIAL -> com.simplemobiletools.commons.R.string.importing_some_entries_failed
                ImportResult.IMPORT_FAIL -> com.simplemobiletools.commons.R.string.importing_failed
                else -> com.simplemobiletools.commons.R.string.no_items_found
            }
        )
    }
}

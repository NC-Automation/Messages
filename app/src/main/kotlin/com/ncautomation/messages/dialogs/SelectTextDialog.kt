package com.ncautomation.messages.dialogs

import com.ncautomation.commons.activities.BaseSimpleActivity
import com.ncautomation.commons.extensions.getAlertDialogBuilder
import com.ncautomation.commons.extensions.setupDialogStuff
import com.ncautomation.messages.databinding.DialogSelectTextBinding

// helper dialog for selecting just a part of a message, not copying the whole into clipboard
class SelectTextDialog(val activity: BaseSimpleActivity, val text: String) {
    init {
        val binding = DialogSelectTextBinding.inflate(activity.layoutInflater).apply {
            dialogSelectTextValue.text = text
        }

        activity.getAlertDialogBuilder()
            .setPositiveButton(com.ncautomation.commons.R.string.ok) { _, _ -> { } }
            .apply {
                activity.setupDialogStuff(binding.root, this)
            }
    }
}

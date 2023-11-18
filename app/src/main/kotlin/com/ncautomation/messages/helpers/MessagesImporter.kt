package com.ncautomation.messages.helpers

import android.net.Uri
import android.util.Log
import android.util.Xml
import com.simplemobiletools.commons.extensions.showErrorToast
import com.simplemobiletools.commons.extensions.toast
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.ncautomation.messages.activities.SimpleActivity
import com.ncautomation.messages.dialogs.ImportMessagesDialog
import com.ncautomation.messages.extensions.config
import com.ncautomation.messages.models.*
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream


class MessagesImporter(private val activity: SimpleActivity, private val dialog:ImportMessagesDialog) {

    private val messageWriter = MessagesWriter(activity)
    private val config = activity.config
    private var messagesImported = 0
    private var messagesFailed = 0

    fun importMessages(uri: Uri) {
        try {
            val fileType = activity.contentResolver.getType(uri).orEmpty()
            val isXml = isXmlMimeType(fileType) || (uri.path?.endsWith("txt") == true && isFileXml(uri))
            if (isXml) {
                activity.toast(com.simplemobiletools.commons.R.string.importing)
                getInputStreamFromUri(uri)!!.importXml()
            } else {
                importJson(uri)
            }
        } catch (e: Exception) {
            activity.showErrorToast(e)
        }
    }

    private fun importJson(uri: Uri) {
        try {
            var messages = mutableListOf<MessagesBackup>()
            var lastLine = ""
            var count = 1
            dialog.setStatus("Reading messages from file...")
            activity.contentResolver.openInputStream(uri)!!.use { inputStream ->
                inputStream.bufferedReader().forEachLine { line ->
                    if (dialog.isCanceled){
                        return@forEachLine
                    }
                    Log.d("tag", messages.count().toString())
                    if (line.startsWith("[")) {
                        lastLine = line.trimStart('[').trimEnd(']')
                    }
                    else
                    {
                        if (line.startsWith(",{\"subscription") || line.startsWith(",{\"creator")){
                            //this is a new line
                            if (lastLine.endsWith('}')) {
                                dialog.setStatus("Reading messages from file (${count})...")
                                count++
                                var message = Json.decodeFromString<MessagesBackup>(lastLine)
                                messages.add(message)
                            }
                            lastLine = line.trimStart(',').trimEnd(']')
                        }
                        else {
                            //this is part of the same object
                            lastLine += lastLine
                        }
                    }
                }
            }
            if (lastLine.startsWith("{") || lastLine.endsWith("}")){
                var message = Json.decodeFromString<MessagesBackup>(lastLine)
                messages.add(message)
            }
            if (dialog.isCanceled) return

            val deserializedList = messages.toList()
            if (deserializedList.isEmpty()) {
                activity.toast(com.simplemobiletools.commons.R.string.no_entries_for_importing)
                return
            }
            restoreMessages(messages)

        } catch (e: SerializationException) {
            activity.toast(com.simplemobiletools.commons.R.string.invalid_file_format)
        } catch (e: IllegalArgumentException) {
            activity.toast(com.simplemobiletools.commons.R.string.invalid_file_format)
        } catch (e: Exception) {
            activity.showErrorToast(e)
        }
    }

    private fun restoreMessages(messagesBackup: List<MessagesBackup>) {
        ensureBackgroundThread {
            try {
                val sms = messagesBackup.count { it.backupType == BackupType.SMS }
                val mms = messagesBackup.count { it.backupType == BackupType.MMS }
                var total = 0
                if (config.importSms) total+=sms
                if (config.importMms) total+=mms
                dialog.setProgress(0, total)

                messagesBackup.forEach { message ->
                    if (dialog.isCanceled){
                        return@ensureBackgroundThread
                    }
                    try {
                        if (message.backupType == BackupType.SMS && config.importSms) {
                            dialog.setStatus("Importing message ${messagesImported + 1} of ${total}")
                            dialog.setProgress(messagesImported + 1, total)
                            messageWriter.writeSmsMessage(message as SmsBackup)
                            messagesImported++
                        } else if (message.backupType == BackupType.MMS && config.importMms) {
                            dialog.setStatus("Importing message ${messagesImported + 1} of ${total}")
                            dialog.setProgress(messagesImported + 1, total)
                            messageWriter.writeMmsMessage(message as MmsBackup)
                            messagesImported++
                        }
                    } catch (e: Exception) {
                        activity.showErrorToast(e)
                        messagesFailed++
                    }
                }
                refreshMessages()
            } catch (e: Exception) {
                activity.showErrorToast(e)
            }
            var result = "Imported ${messagesImported} messages."
            if (messagesFailed > 0) result += "\n${messagesFailed} messages failed to import."
            dialog.setStatus("Import Completed!", result)
        }
    }

    private fun InputStream.importXml() {
        try {
            bufferedReader().use { reader ->
                val xmlParser = Xml.newPullParser().apply {
                    setInput(reader)
                }

                xmlParser.nextTag()
                xmlParser.require(XmlPullParser.START_TAG, null, "smses")

                var depth = 1
                while (depth != 0) {
                    when (xmlParser.next()) {
                        XmlPullParser.END_TAG -> depth--
                        XmlPullParser.START_TAG -> depth++
                    }

                    if (xmlParser.eventType != XmlPullParser.START_TAG) {
                        continue
                    }

                    try {
                        if (xmlParser.name == "sms") {
                            if (config.importSms) {
                                val message = xmlParser.readSms()
                                messageWriter.writeSmsMessage(message)
                                messagesImported++
                            } else {
                                xmlParser.skip()
                            }
                        } else {
                            xmlParser.skip()
                        }
                    } catch (e: Exception) {
                        activity.showErrorToast(e)
                        messagesFailed++
                    }
                }
                refreshMessages()
            }
            when {
                messagesFailed > 0 && messagesImported > 0 -> activity.toast(com.simplemobiletools.commons.R.string.importing_some_entries_failed)
                messagesFailed > 0 -> activity.toast(com.simplemobiletools.commons.R.string.importing_failed)
                else -> activity.toast(com.simplemobiletools.commons.R.string.importing_successful)
            }
        } catch (_: Exception) {
            activity.toast(com.simplemobiletools.commons.R.string.invalid_file_format)
        }
    }

    private fun XmlPullParser.readSms(): SmsBackup {
        require(XmlPullParser.START_TAG, null, "sms")

        return SmsBackup(
            subscriptionId = 0,
            address = getAttributeValue(null, "address"),
            body = getAttributeValue(null, "body"),
            date = getAttributeValue(null, "date").toLong(),
            dateSent = getAttributeValue(null, "date").toLong(),
            locked = getAttributeValue(null, "locked").toInt(),
            protocol = getAttributeValue(null, "protocol"),
            read = getAttributeValue(null, "read").toInt(),
            status = getAttributeValue(null, "status").toInt(),
            type = getAttributeValue(null, "type").toInt(),
            serviceCenter = getAttributeValue(null, "service_center")
        )
    }

    private fun XmlPullParser.skip() {
        if (eventType != XmlPullParser.START_TAG) {
            throw IllegalStateException()
        }
        var depth = 1
        while (depth != 0) {
            when (next()) {
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.START_TAG -> depth++
            }
        }
    }

    private fun getInputStreamFromUri(uri: Uri): InputStream? {
        return try {
            activity.contentResolver.openInputStream(uri)
        } catch (e: Exception) {
            null
        }
    }

    private fun isFileXml(uri: Uri): Boolean {
        val inputStream = getInputStreamFromUri(uri)
        return inputStream?.bufferedReader()?.use { reader ->
            reader.readLine()?.startsWith("<?xml") ?: false
        } ?: false
    }

    private fun isXmlMimeType(mimeType: String): Boolean {
        return mimeType.equals("application/xml", ignoreCase = true) || mimeType.equals("text/xml", ignoreCase = true)
    }

    private fun isJsonMimeType(mimeType: String): Boolean {
        return mimeType.equals("application/json", ignoreCase = true)
    }
}

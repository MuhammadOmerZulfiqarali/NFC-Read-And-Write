@file:Suppress("DEPRECATION")

package com.example.nfcreadandwrite

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.FormatException
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.IOException
import java.io.UnsupportedEncodingException

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "NFCReadWrite"
        const val ERROR_DETECTED = "No NFC Tag Detected"
        const val WRITE_SUCCESS = "Text Written Successfully!"
        const val WRITE_ERROR = "Error during Writing, Try Again!"
    }

    private lateinit var nfcAdapter: NfcAdapter
    private lateinit var pendingIntent: PendingIntent
    private lateinit var writingTagFilters: Array<IntentFilter>
    private var isWriteMode: Boolean = false
    private var detectedTag: Tag? = null
    private lateinit var context: Context
    private lateinit var editMessage: TextView
    private lateinit var nfcContents: TextView
    private lateinit var activateButton: Button

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        editMessage = findViewById(R.id.edit_message)
        nfcContents = findViewById(R.id.nfc_contents)
        activateButton = findViewById(R.id.ActivateButton)
        context = this

        activateButton.setOnClickListener {
            if (detectedTag == null) {
                showToast(ERROR_DETECTED)
            } else {
                try {
                    writeToTag("PlainText|${editMessage.text}", detectedTag!!)
                    showToast(WRITE_SUCCESS)
                } catch (e: Exception) {
                    handleWriteError(e)
                }
            }
        }

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        readFromIntent(intent)

        pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE
        )

        val tagDetected = IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        writingTagFilters = arrayOf(tagDetected)
    }

    private fun readFromIntent(intent: Intent) {
        val action = intent.action
        if (action in listOf(
                NfcAdapter.ACTION_TAG_DISCOVERED,
                NfcAdapter.ACTION_TECH_DISCOVERED,
                NfcAdapter.ACTION_NDEF_DISCOVERED
            )
        ) {
            val rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
            if (!rawMsgs.isNullOrEmpty()) {
                val msgs = rawMsgs[0] as? NdefMessage
                buildTagViews(msgs)
            } else {
                Log.d(TAG, "No NDEF messages found in the intent.")
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun buildTagViews(msgs: NdefMessage?) {
        if (msgs == null) {
            Log.d(TAG, "NDEF message is null.")
            return
        }

        val payload = msgs.records[0].payload
        val textEncoding = if ((payload[0].toInt() and 128) == 0) "UTF-8" else "UTF-16"
        val languageCodeLength = payload[0].toInt() and 63

        try {
            val text = String(
                payload,
                languageCodeLength + 1,
                payload.size - languageCodeLength - 1,
                charset(textEncoding)
            )
            nfcContents.text = "NFC Content: $text"
        } catch (e: UnsupportedEncodingException) {
            Log.e(TAG, "Unsupported Encoding: ${e.message}")
        }
    }

    @Throws(IOException::class, FormatException::class)
    private fun writeToTag(text: String, tag: Tag) {
        val records = arrayOf(createTextRecord(text))
        val message = NdefMessage(records)

        val ndef = Ndef.get(tag)
        ndef.connect()
        ndef.writeNdefMessage(message)
        ndef.close()
    }

    @Throws(UnsupportedEncodingException::class)
    private fun createTextRecord(text: String): NdefRecord {
        val lang = "en"
        val textBytes = text.toByteArray()
        val langBytes = lang.toByteArray(Charsets.US_ASCII)
        val langLength = langBytes.size
        val textLength = textBytes.size
        val payload = ByteArray(1 + langLength + textLength)

        payload[0] = langLength.toByte()
        System.arraycopy(langBytes, 0, payload, 1, langLength)
        System.arraycopy(textBytes, 0, payload, 1 + langLength, textLength)

        return NdefRecord(NdefRecord.TNF_WELL_KNOWN, NdefRecord.RTD_TEXT, ByteArray(0), payload)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readFromIntent(intent)
        if (intent.action == NfcAdapter.ACTION_TAG_DISCOVERED) {
            detectedTag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        }
    }

    override fun onPause() {
        super.onPause()
        disableWriteMode()
    }

    override fun onResume() {
        super.onResume()
        enableWriteMode()
    }

    private fun enableWriteMode() {
        isWriteMode = true
        nfcAdapter.enableForegroundDispatch(this, pendingIntent, writingTagFilters, null)
    }

    private fun disableWriteMode() {
        isWriteMode = false
        nfcAdapter.disableForegroundDispatch(this)
    }

    private fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    private fun handleWriteError(e: Exception) {
        showToast(WRITE_ERROR)
        Log.e(TAG, "Write Error: ${e.message}")
        e.printStackTrace()
    }
}

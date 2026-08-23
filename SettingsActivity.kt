package com.suchaowut.speechnotification

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.edit
import java.util.*

class SettingsActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var thaiVoices = listOf<Voice>()
    private lateinit var spinnerVoices: Spinner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        // ตั้งค่าปุ่ม Back
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeButtonEnabled(true)

        toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        tts = TextToSpeech(this, this)
        val prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)

        spinnerVoices = findViewById(R.id.spinnerVoices)
        val seekRate = findViewById<SeekBar>(R.id.seekRate)
        val seekPitch = findViewById<SeekBar>(R.id.seekPitch)
        val editTestMessage = findViewById<EditText>(R.id.editTestMessage)
        val btnPreview = findViewById<Button>(R.id.btnPreview)
        val btnSave = findViewById<Button>(R.id.btnSaveSettings)

        // โหลดค่าที่บันทึกไว้
        seekRate.progress = (prefs.getFloat("tts_rate", 1.0f) * 100).toInt()
        seekPitch.progress = (prefs.getFloat("tts_pitch", 1.0f) * 100).toInt()
        editTestMessage.setText(prefs.getString("test_message", "ทดสอบเสียงพูด"))

        btnPreview.setOnClickListener {
            if (thaiVoices.isNotEmpty()) {
                tts?.voice = thaiVoices[spinnerVoices.selectedItemPosition]
            }
            tts?.setSpeechRate(seekRate.progress / 100f)
            tts?.setPitch(seekPitch.progress / 100f)

            val message = editTestMessage.text.toString()
            if (message.isNotEmpty()) {
                tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "PreviewID")
            } else {
                Toast.makeText(this, "กรุณาใส่ข้อความทดสอบ", Toast.LENGTH_SHORT).show()
            }
        }

        btnSave.setOnClickListener {
            prefs.edit {
                if (thaiVoices.isNotEmpty()) {
                    putString("selected_voice", thaiVoices[spinnerVoices.selectedItemPosition].name)
                }
                putFloat("tts_rate", seekRate.progress / 100f)
                putFloat("tts_pitch", seekPitch.progress / 100f)
                putString("test_message", editTestMessage.text.toString())
            }
            Toast.makeText(this, "บันทึกการตั้งค่าแล้ว", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val locale = Locale.forLanguageTag("th-TH")
            val result = tts?.setLanguage(locale)
            
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Toast.makeText(this, "ภาษาไทยไม่รองรับบนเครื่องนี้", Toast.LENGTH_LONG).show()
            } else {
                // กรองเฉพาะเสียงภาษาไทย
                thaiVoices = tts?.voices?.filter { it.locale.language == "th" } ?: listOf()
                if (thaiVoices.isNotEmpty()) {
                    val voiceNames = thaiVoices.mapIndexed { i, _ -> "โทนเสียงที่ ${i + 1}" }
                    val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, voiceNames)
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    spinnerVoices.adapter = adapter
                    
                    // เลือกเสียงที่เคยบันทึกไว้
                    val prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
                    val savedVoiceName = prefs.getString("selected_voice", "")
                    val savedIndex = thaiVoices.indexOfFirst { it.name == savedVoiceName }
                    if (savedIndex != -1) {
                        spinnerVoices.setSelection(savedIndex)
                    }
                }
            }
        } else {
            Toast.makeText(this, "ไม่สามารถเริ่มต้นระบบเสียงได้", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}

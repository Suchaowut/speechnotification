package com.suchaowut.speechnotification

import android.app.TimePickerDialog
import android.content.Context
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar

class TimerActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_timer) // อย่าลืมสร้าง XML ชื่อนี้

        val toolbar = findViewById<Toolbar>(R.id.toolbarTimer)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        val prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val btnStart = findViewById<Button>(R.id.btnStartTime)
        val btnEnd = findViewById<Button>(R.id.btnEndTime)

        fun updateLabels() {
            btnStart.text = "เริ่ม: ${String.format("%02d:%02d", prefs.getInt("start_h", 6), prefs.getInt("start_m", 0))}"
            btnEnd.text = "จบ: ${String.format("%02d:%02d", prefs.getInt("end_h", 20), prefs.getInt("end_m", 0))}"
        }
        updateLabels()

        btnStart.setOnClickListener {
            TimePickerDialog(this, { _, h, m ->
                prefs.edit().putInt("start_h", h).putInt("start_m", m).apply()
                updateLabels()
            }, prefs.getInt("start_h", 8), 0, true).show()
        }

        btnEnd.setOnClickListener {
            TimePickerDialog(this, { _, h, m ->
                prefs.edit().putInt("end_h", h).putInt("end_m", m).apply()
                updateLabels()
            }, prefs.getInt("end_h", 20), 0, true).show()
        }
    }
}
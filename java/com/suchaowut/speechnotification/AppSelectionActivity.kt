package com.suchaowut.speechnotification

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.speech.tts.TextToSpeech
import java.util.Locale

class AppSelectionActivity : AppCompatActivity() {
    private var tts: TextToSpeech? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_selection)

        // ตั้งค่า Toolbar พร้อมปุ่มย้อนกลับ
        val toolbar = findViewById<Toolbar>(R.id.toolbarApps)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("th", "TH")
            }
        }

        val prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val rv = findViewById<RecyclerView>(R.id.rvAllApps)
        val pm = packageManager

        // ดึงเฉพาะแอปที่มีหน้าเปิด (Launcher) เพื่อความง่ายต่อผู้ใช้
        val intent = Intent(Intent.ACTION_MAIN, null)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos = pm.queryIntentActivities(intent, 0)

        val apps = resolveInfos.map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .sortedBy { it.loadLabel(pm).toString() }

        rv.layoutManager = LinearLayoutManager(this)
        // ใช้ AppAdapter ตัวเดิมที่คุณมีอยู่แล้วได้เลย
        rv.adapter = AppAdapter(apps, pm, prefs, tts)
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
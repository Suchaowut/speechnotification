package com.suchaowut.speechnotification

import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.speech.tts.TextToSpeech
import android.util.Log // เพิ่ม Import สำหรับ Log
import java.util.*

class MyNotificationListener : NotificationListenerService(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(this, this)

        // เช็ค Master Switch ก่อนเริ่ม Foreground
        val prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("master_switch", true)) {
            startAsForeground()
        }
    }

    private fun startAsForeground() {
        val channelId = "speech_service_channel"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(channelId, "Notification Reader", android.app.NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(android.app.NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification = androidx.core.app.NotificationCompat.Builder(this, channelId)
            .setContentTitle("ระบบอ่านแจ้งเตือนเปิดอยู่")
            .setContentText("กำลังรออ่านการแจ้งเตือนใหม่...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true) // ทำให้ Notification ปัดทิ้งไม่ได้ขณะทำงาน
            .build()

        startForeground(1, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val isMasterOn = prefs.getBoolean("master_switch", true)

        if (!isMasterOn) {
            // หยุดการทำงาน Foreground และเอา Notification ออกทันที
            stopForeground(true)
            stopSelf()
            return START_NOT_STICKY
        }

        startAsForeground()
        return START_STICKY
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val packageName = sbn?.packageName ?: return

        // --- ส่วนที่เพิ่ม: แสดง Log เพื่อดู Package Name ของแอปที่แจ้งเตือนเข้ามา ---
        Log.d("SpeechNoti", "---------------------------------------")
        Log.d("SpeechNoti", "ตรวจพบการแจ้งเตือนจากแอป: $packageName")

        val prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)

        // ตรวจสอบ Master Switch
        if (!prefs.getBoolean("master_switch", true)) {
            Log.d("SpeechNoti", "ระบบถูกปิด (Master Switch Off): กำลังหยุด Service")
            stopForeground(true)
            stopSelf()
            return
        }

        // ตรวจสอบว่าแอปนี้ถูกเปิดใช้งานในรายชื่อแอปหรือไม่
        if (!prefs.getBoolean("app_enabled_$packageName", false)) {
            Log.d("SpeechNoti", "แอป $packageName ไม่ได้ถูกเปิดใช้งานในรายชื่อแอป (Skip)")
            return
        }

        val extras = sbn.notification.extras
        val title = extras.getString("android.title") ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""

        Log.d("SpeechNoti", "หัวข้อ: $title | เนื้อหา: $text")

        // --- ส่วนการกรองรายชื่อเฉพาะ LINE ---
        val isLine = (packageName == "com.linecorp.line.android" || packageName == "jp.naver.line.android")

        if (isLine) {
            val whitelistString = prefs.getString("whitelist_$packageName", "") ?: ""

            // 1. ถ้าไม่ได้ตั้ง Whitelist ไว้เลย (ว่างเปล่า) ให้ return (ไม่พูดเลยสักคน)
            // หรือถ้าอยากให้อ่านทุกคนถ้าว่างไว้ ให้ลบ if นี้ออกครับ
            if (whitelistString.isEmpty()) {
                Log.d("SpeechNoti", "Whitelist ว่างเปล่า: ข้ามการอ่านตามนโยบายความเป็นส่วนตัว")
                return
            }

            val allowedNames = whitelistString.split(",").map { it.trim() }.filter { it.isNotEmpty() }

            // 2. ตรวจสอบชื่อผู้ส่งให้แม่นยำขึ้น
            val isAllowed = allowedNames.any { name ->
                title.equals(name, ignoreCase = true) || title.contains(name, ignoreCase = true)
            }

            if (!isAllowed) {
                Log.d("SpeechNoti", "ข้าม: ชื่อ '$title' ไม่อยู่ในรายชื่อ $allowedNames")
                return
            }
        }

        val template = prefs.getString("custom_template_$packageName", "\$title แจ้งว่า \$text")
        val finalSpeech = template?.replace("\$title", title)?.replace("\$text", text) ?: ""

        Log.d("SpeechNoti", "กำลังพูดว่า: $finalSpeech")

        tts?.let { serviceTts ->
            serviceTts.setSpeechRate(prefs.getFloat("tts_rate", 1.0f))
            serviceTts.setPitch(prefs.getFloat("tts_pitch", 1.0f))
            serviceTts.speak(finalSpeech, TextToSpeech.QUEUE_FLUSH, null, "NotiID")
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) tts?.language = Locale("th", "TH")
    }

    override fun onDestroy() {
        Log.d("SpeechNoti", "Service ถูกทำลาย (Destroyed)")
        tts?.shutdown()
        super.onDestroy()
    }
}
package com.suchaowut.speechnotification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.navigation.NavigationView
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import android.content.ComponentName
class MainActivity : AppCompatActivity() {
    private var tts: android.speech.tts.TextToSpeech? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // โค้ดขอสิทธิ์ส่งแจ้งเตือนสำหรับ Android 13 ขึ้นไป
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    this, android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                androidx.core.app.ActivityCompat.requestPermissions(
                    this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101
                )
            }
        }

        tts = android.speech.tts.TextToSpeech(this) { status ->
            if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                tts?.language = java.util.Locale("th", "TH")
            }
        }

        val prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)

        // ใน onCreate
        val drawerLayout = findViewById<DrawerLayout>(R.id.drawer_layout)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        val navView = findViewById<NavigationView>(R.id.nav_view)

        // ตั้งค่าปุ่ม Hamburger
        val toggle = ActionBarDrawerToggle(
            this,
            drawerLayout,
            toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        // ภายใน onCreate ของ MainActivity
        val switchMaster = findViewById<SwitchCompat>(R.id.switchMaster)

        // 1. โหลดสถานะเดิมมาแสดง (ถ้าไม่มีข้อมูลให้เป็น true ไว้ก่อน)
        switchMaster.isChecked = prefs.getBoolean("master_switch", true)

        // 2. บันทึกค่าทันทีเมื่อมีการเปลี่ยนสถานะ
        switchMaster.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("master_switch", isChecked).apply()

            if (isChecked) {
                // เมื่อเปิดสวิตช์: สั่งให้ Service เริ่มทำงานเบื้องหลังใหม่
                val serviceIntent = Intent(this, MyNotificationListener::class.java)
                startService(serviceIntent)
                Toast.makeText(this, "เปิดระบบอ่านแจ้งเตือนแล้ว", Toast.LENGTH_SHORT).show()
            } else {
                // เมื่อปิดสวิตช์: สั่งหยุด Service เพื่อไม่ให้รันเบื้องหลัง
                val serviceIntent = Intent(this, MyNotificationListener::class.java)
                stopService(serviceIntent)
                Toast.makeText(this, "ปิดระบบอ่านแจ้งเตือนและงานเบื้องหลังแล้ว", Toast.LENGTH_SHORT).show()
            }
        }

        navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home -> drawerLayout.closeDrawer(GravityCompat.START)
                R.id.nav_settings -> startActivity(Intent(this, SettingsActivity::class.java))

                // เปิดหน้าเลือกแอป
                R.id.nav_apps -> startActivity(Intent(this, AppSelectionActivity::class.java))

                // เปิดหน้าตั้งเวลา
                R.id.nav_timer -> startActivity(Intent(this, TimerActivity::class.java))
            }
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

// --- นำไปเรียกใช้ ---
        loadDashboardApps()

        // ปุ่มขอสิทธิ์
        findViewById<Button>(R.id.btnPermission).setOnClickListener {
            startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
        }


        // รายชื่อแอป
        // ใน MainActivity.kt ตรงส่วนรายชื่อแอป
        val rv = findViewById<RecyclerView>(R.id.rvApps)
        val pm = packageManager

// เปลี่ยนวิธีดึงเป็นดึงจาก Intent Launcher เพื่อให้เห็นแอปธนาคารชัดเจนขึ้น
        val intent = Intent(Intent.ACTION_MAIN, null)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos = pm.queryIntentActivities(intent, 0)


// กรองเอาเฉพาะข้อมูล ApplicationInfo และลบตัวที่ซ้ำออก
        val apps = resolveInfos.map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .sortedBy { it.loadLabel(pm).toString() } // เรียงชื่อตามตัวอักษรให้หาง่าย

        // ใน MainActivity.kt ตรงส่วนแสดง RecyclerView
        val enabledApps = apps.filter { app ->
            prefs.getBoolean("app_enabled_${app.packageName}", false)
        }
        rv.adapter = AppAdapter(enabledApps, pm, prefs, tts)
    }

    override fun onResume() {
        super.onResume()
        val btnPermission = findViewById<Button>(R.id.btnPermission)

        if (isNotificationServiceEnabled()) {
            // ถ้าเปิดสิทธิ์แล้ว: เปลี่ยนปุ่มเป็นสีเขียวและบอกว่าเรียบร้อย
            btnPermission.text = "สิทธิ์การอ่านแจ้งเตือน: เปิดใช้งานแล้ว ✅"
            btnPermission.setBackgroundColor(android.graphics.Color.parseColor("#4CAF50")) // สีเขียว
            btnPermission.isEnabled = false // ปิดปุ่มไว้ไม่ให้กดซ้ำ
        } else {
            // ถ้ายังไม่เปิด: ใช้สีส้มเพื่อให้สะดุดตา
            btnPermission.text = "1. คลิกเพื่อเปิดสิทธิ์ในการอ่านการแจ้งเตือน (ยังไม่ได้เปิด)"
            btnPermission.setBackgroundColor(android.graphics.Color.parseColor("#FFA500")) // สีส้ม
            btnPermission.isEnabled = true
        }
        loadDashboardApps()

        val navView = findViewById<com.google.android.material.navigation.NavigationView>(R.id.nav_view)
        navView.setCheckedItem(R.id.nav_home)
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val pkgName = packageName
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        if (flat != null) {
            val names = flat.split(":")
            for (name in names) {
                val componentName = ComponentName.unflattenFromString(name)
                if (componentName != null) {
                    if (pkgName == componentName.packageName) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun loadDashboardApps() {
        val rv = findViewById<RecyclerView>(R.id.rvApps)
        val pm = packageManager
        val prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)

        val intent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
        val allApps = pm.queryIntentActivities(intent, 0)
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }

        // กรองเฉพาะแอปที่ถูกติ๊กเปิดไว้ (Boolean เป็น true)
        val enabledApps = allApps.filter { app ->
            prefs.getBoolean("app_enabled_${app.packageName}", false)
        }

        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = AppAdapter(enabledApps, pm, prefs, tts)
    }

    private fun sendTestNotification() {
        val prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
        // ดึงข้อความทดสอบที่เซฟไว้ ถ้าไม่มีให้ใช้ค่าเริ่มต้น
        val messageToShow = prefs.getString("test_message", "test")

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "test_channel_id"

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Test", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        val builder = androidx.core.app.NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("ระบบทดสอบเสียง")
            .setContentText(messageToShow) // ใช้ข้อความที่ดึงมาจากหน้าตั้งค่า
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        notificationManager.notify(101, builder.build())
    }
}


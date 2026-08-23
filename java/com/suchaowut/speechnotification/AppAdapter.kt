package com.suchaowut.speechnotification

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.speech.tts.TextToSpeech
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog // สำคัญมาก
import androidx.recyclerview.widget.RecyclerView

class AppAdapter(
    private val apps: List<ApplicationInfo>,
    private val pm: android.content.pm.PackageManager,
    private val prefs: SharedPreferences,
    private val tts: TextToSpeech?
) : RecyclerView.Adapter<AppAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgIcon: ImageView = view.findViewById(R.id.imgIcon)
        val txtAppName: TextView = view.findViewById(R.id.txtAppName)
        val swEnable: Switch = view.findViewById(R.id.swEnable)
        val btnSettingsItem: ImageButton = view.findViewById(R.id.btnSettingsItem) // ปุ่มใหม่
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = apps[position]
        holder.txtAppName.text = app.loadLabel(pm)
        holder.imgIcon.setImageDrawable(app.loadIcon(pm))

        val keyEnabled = "app_enabled_${app.packageName}"
        holder.swEnable.setOnCheckedChangeListener(null)
        holder.swEnable.isChecked = prefs.getBoolean(keyEnabled, false)

        holder.swEnable.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(keyEnabled, isChecked).apply()
        }

        // เมื่อกดปุ่มฟันเฟือง (ตั้งค่าข้อความ)
        holder.btnSettingsItem.setOnClickListener {
            showCustomMessageDialog(holder.itemView.context, app, tts)
        }
    }

    private fun showCustomMessageDialog(context: Context, app: ApplicationInfo, tts: TextToSpeech?) {
        val keyMsg = "custom_template_${app.packageName}"
        val keyWhitelist = "whitelist_${app.packageName}"

        // ตรวจสอบชื่อ Package ของ LINE (แก้จากเดิมให้แม่นยำขึ้น)
        val isLine = app.packageName == "com.linecorp.line.android" || app.packageName == "jp.naver.line.android"

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 40)
        }

        // android.util.Log.d("SpeechNoti", "ตรวจพบแอป: ${app.packageName}")

        // --- ส่วนที่ 1: รูปแบบคำพูด (แสดงทุกแอป) ---
        layout.addView(TextView(context).apply { text = "รูปแบบคำพูด:" })
        val inputTemplate = EditText(context).apply {
            setText(prefs.getString(keyMsg, "\$title แจ้งว่า \$text"))
        }
        layout.addView(inputTemplate)

        // --- ส่วนที่ 2: Whitelist (แสดงเฉพาะแอป LINE เท่านั้น) ---
        var inputWhitelist: EditText? = null // ประกาศตัวแปรไว้ด้านนอกเพื่อให้ PositiveButton เรียกใช้ได้

        if (isLine) {
            layout.addView(TextView(context).apply {
                text = "\nอ่านเฉพาะชื่อเหล่านี้ (คั่นด้วยคอมมา ,):"
                setTextColor(android.graphics.Color.parseColor("#008000")) // สีเขียว LINE
            })
            inputWhitelist = EditText(context).apply {
                setText(prefs.getString(keyWhitelist, ""))
                hint = "เช่น: แม่, ลูกชาย, กลุ่มครอบครัว"
            }
            layout.addView(inputWhitelist) // ต้องมีบรรทัดนี้ ช่องถึงจะโผล่ครับ
        }

        // --- ส่วนที่ 3: ปุ่มลองฟังเสียง ---
        val btnTest = Button(context).apply {
            text = "🔊 ลองฟังเสียงตัวอย่าง"
            setOnClickListener {
                // ดึงการตั้งค่าเสียงที่ผู้ใช้ตั้งไว้มาใช้งาน
                val rate = prefs.getFloat("tts_rate", 1.0f)
                val pitch = prefs.getFloat("tts_pitch", 1.0f)
                val savedVoiceName = prefs.getString("selected_voice", "")

                tts?.apply {
                    setSpeechRate(rate)
                    setPitch(pitch)

                    // ตั้งค่าโทนเสียงที่เลือกไว้ (ถ้ามี)
                    if (!savedVoiceName.isNullOrEmpty()) {
                        voices?.find { it.name == savedVoiceName }?.let {
                            voice = it
                        }
                    }
                }

                val testMsg = inputTemplate.text.toString()
                    .replace("\$title", "title")
                    .replace("\$text", "text")
                tts?.speak(testMsg, TextToSpeech.QUEUE_FLUSH, null, "TestID")
            }
        }
        layout.addView(btnTest)

        AlertDialog.Builder(context)
            .setTitle("ตั้งค่าแอป ${app.loadLabel(pm)}")
            .setView(layout)
            .setPositiveButton("บันทึก") { _, _ ->
                val editor = prefs.edit()
                editor.putString(keyMsg, inputTemplate.text.toString())

                // บันทึกค่า Whitelist เฉพาะแอป LINE
                if (isLine && inputWhitelist != null) {
                    editor.putString(keyWhitelist, inputWhitelist.text.toString())
                }
                editor.apply()
                Toast.makeText(context, "บันทึกแล้ว", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("ยกเลิก", null)
            .show()
    }

    override fun getItemCount() = apps.size
}

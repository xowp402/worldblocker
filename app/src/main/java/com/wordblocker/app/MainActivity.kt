package com.wordblocker.app

import android.app.Activity
import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.*

class MainActivity : Activity() {

    private lateinit var wordsInput: EditText
    private lateinit var daysInput: EditText
    private lateinit var status: TextView

    private fun prefs() = getSharedPreferences("blocker", Context.MODE_PRIVATE)
    private fun isLocked() = System.currentTimeMillis() < prefs().getLong("lock_end", 0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        status = TextView(this).apply { textSize = 16f }
        root.addView(status)

        root.addView(TextView(this).apply {
            text = "\nکلمه‌ها و سایت‌های ممنوع (هر مورد در یک خط):"
        })
        wordsInput = EditText(this).apply {
            minLines = 6
            gravity = android.view.Gravity.TOP
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setText(prefs().getString("words", ""))
        }
        root.addView(wordsInput)

        root.addView(button("ذخیره‌ی لیست") { saveWords() })
        root.addView(button("۱) روشن کردن Accessibility") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        root.addView(button("۲) فعال کردن Device Admin") { enableAdmin() })

        root.addView(TextView(this).apply { text = "\nتعداد روز قفل (حداکثر ۱۸۰):" })
        daysInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText("30")
        }
        root.addView(daysInput)
        root.addView(button("۳) شروع قفل (غیرقابل لغو)") { confirmLock() })

        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        setOnClickListener { onClick() }
    }

    private fun saveWords() {
        val new = wordsInput.text.toString().split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        val old = (prefs().getString("words", "") ?: "").split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        // در دوره‌ی قفل فقط می‌شود کلمه اضافه کرد، نه حذف
        val final = if (isLocked()) (old + new).distinct() else new.distinct()
        prefs().edit().putString("words", final.joinToString("\n")).apply()
        wordsInput.setText(final.joinToString("\n"))
        Toast.makeText(this, "ذخیره شد", Toast.LENGTH_SHORT).show()
    }

    private fun enableAdmin() {
        val comp = ComponentName(this, AdminReceiver::class.java)
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, comp)
            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "برای سخت‌تر شدن حذف برنامه در دوره‌ی قفل")
        }
        startActivity(intent)
    }

    private fun confirmLock() {
        if (isLocked()) {
            Toast.makeText(this, "قفل از قبل فعال است", Toast.LENGTH_SHORT).show()
            return
        }
        val days = (daysInput.text.toString().toIntOrNull() ?: 30).coerceIn(1, 180)
        AlertDialog.Builder(this)
            .setTitle("شروع قفل")
            .setMessage("بعد از تأیید، تا $days روز نمی‌توانی لیست را کوتاه کنی یا برنامه را غیرفعال کنی. مطمئنی؟")
            .setPositiveButton("بله، شروع کن") { _, _ ->
                prefs().edit().putLong("lock_end", System.currentTimeMillis() + days * 86400000L).apply()
                refreshStatus()
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun refreshStatus() {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?.contains(packageName) == true
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = dpm.isAdminActive(ComponentName(this, AdminReceiver::class.java))
        val left = prefs().getLong("lock_end", 0L) - System.currentTimeMillis()
        val lockText = if (left > 0) "فعال، ${left / 86400000L + 1} روز مانده" else "غیرفعال"
        status.text = "Accessibility: ${if (enabled) "روشن ✅" else "خاموش ❌"}\n" +
                "Device Admin: ${if (admin) "فعال ✅" else "غیرفعال ❌"}\n" +
                "قفل: $lockText"
    }
}

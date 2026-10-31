package com.wordblocker.app

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

class BlockerService : AccessibilityService() {

    private var lastAction = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return

        val now = System.currentTimeMillis()
        if (now - lastAction < 1500) return

        val prefs = getSharedPreferences("blocker", Context.MODE_PRIVATE)
        val locked = now < prefs.getLong("lock_end", 0L)

        val sb = StringBuilder()
        event.text?.forEach { sb.append(it).append(' ') }
        rootInActiveWindow?.let { collect(it, sb, 0) }
        val screen = sb.toString().lowercase()

        // محافظت در دوره‌ی قفل: صفحه‌های تنظیمات مربوط به این برنامه
        if (locked &&
            (pkg.contains("settings")  pkg.contains("packageinstaller")  pkg.contains("permissioncontroller")) &&
            screen.contains("wordblocker")
        ) {
            trigger("در دوره‌ی قفل نمی‌توانی این برنامه را تغییر بدهی")
            return
        }

        // مسدودسازی کلمه‌ها فقط وقتی اینترنت وصل است
        if (!isOnline()) return

        val words = (prefs.getString("words", "") ?: "")
            .split("\n")
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }

        if (words.any { screen.contains(it) }) {
            trigger("عبارت ممنوع تشخیص داده شد")
        }
    }

    private fun trigger(message: String) {
        lastAction = System.currentTimeMillis()
        performGlobalAction(GLOBAL_ACTION_HOME)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun collect(node: AccessibilityNodeInfo, sb: StringBuilder, depth: Int) {
        if (depth > 30 || sb.length > 30000) return
        node.text?.let { sb.append(it).append(' ') }
        node.contentDescription?.let { sb.append(it).append(' ') }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collect(child, sb, depth + 1)
        }
    }

    private fun isOnline(): Boolean {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    override fun onInterrupt() {}
}

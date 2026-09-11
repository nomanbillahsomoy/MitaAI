package com.noman.mita

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.AlarmClock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ActionHandler(private val context: Context) {

    private var isTorchOn = false

    fun toggleFlashlight(enable: Boolean): String {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
                ?: return "ক্যামেরা ম্যানেজার খুঁজে পাওয়া যায়নি।"

            for (cameraId in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                val hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                if (hasFlash) {
                    cameraManager.setTorchMode(cameraId, enable)
                    isTorchOn = enable
                    return if (enable) "ফ্ল্যাশলাইট জ্বালিয়ে দিয়েছি, বন্ধু!" else "ফ্ল্যাশলাইট বন্ধ করে দিয়েছি।"
                }
            }
            "ডিভাইসে কোনো ফ্ল্যাশলাইট পাওয়া যায়নি।"
        } catch (e: CameraAccessException) {
            "টর্চ ব্যবহারে সমস্যা হয়েছে: ${e.localizedMessage}"
        } catch (e: Exception) {
            "ফ্ল্যাশলাইট অন করতে পারিনি: ${e.localizedMessage}"
        }
    }

    fun setAlarm(hour: Int, minute: Int, message: String): String {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, message.ifEmpty { "মিতা অ্যালার্ম" })
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            "ঠিক আছে বন্ধু, ${String.format(Locale.getDefault(), "%02d:%02d", hour, minute)} এ অ্যালার্ম সেট করে দিলাম!"
        } catch (e: Exception) {
            "অ্যালার্ম সেট করতে পারিনি: ${e.localizedMessage}"
        }
    }

    fun setTimer(seconds: Int, message: String): String {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                putExtra(AlarmClock.EXTRA_MESSAGE, message.ifEmpty { "মিতা টাইমার" })
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            val minutes = seconds / 60
            val secRem = seconds % 60
            val timeText = if (minutes > 0) "$minutes মিনিট $secRem সেকেন্ড" else "$seconds সেকেন্ড"
            "$timeText এর জন্য টাইমার শুরু করে দিয়েছি!"
        } catch (e: Exception) {
            "টাইমার সেট করা সম্ভব হয়নি: ${e.localizedMessage}"
        }
    }

    fun createKeepNote(content: String): String {
        return try {
            // First try Google Keep explicit package
            val keepIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                `package` = "com.google.android.keep"
                putExtra(Intent.EXTRA_TEXT, content)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            if (keepIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(keepIntent)
                "গুগল কিপে তোমার নোটটি সেভ করার জন্য পাঠিয়ে দিয়েছি!"
            } else {
                // Fallback to any note/sharing app
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, content)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(Intent.createChooser(shareIntent, "নোট সংরক্ষণ করুন").apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
                "নোটটি সেভ করার জন্য নোটস অ্যাপে পাঠিয়ে দিয়েছি!"
            }
        } catch (e: Exception) {
            "নোট সেভ করতে পারিনি: ${e.localizedMessage}"
        }
    }

    fun dialCall(phoneNumber: String): String {
        return try {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:${phoneNumber.trim()}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            "$phoneNumber নাম্বারে ডায়াল করছি।"
        } catch (e: Exception) {
            "কল ডায়াল করতে সমস্যা হয়েছে: ${e.localizedMessage}"
        }
    }

    fun getDeviceInfo(): String {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val batteryPct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        val currentTime = SimpleDateFormat("hh:mm a, dd MMMM yyyy", Locale("bn", "BD")).format(Date())
        return "এখন সময় $currentTime। ফোনের ব্যাটারি প্রায় $batteryPct% চার্জ আছে।"
    }
}

package com.example.pantri

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import java.util.Calendar

class WeightReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("weight_reminder", "Weight Reminder", NotificationManager.IMPORTANCE_DEFAULT)
            nm.createNotificationChannel(channel)
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("navigate_to", "weight")
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, "weight_reminder")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Pantri")
            .setContentText("Time to log your weight")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        nm.notify(1001, notification)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences("pantri_settings", Context.MODE_PRIVATE)
            if (prefs.getBoolean("weight_reminder_enabled", false)) {
                val hour = prefs.getInt("weight_reminder_hour", 8)
                val minute = prefs.getInt("weight_reminder_minute", 0)
                WeightReminderScheduler.schedule(context, hour, minute)
            }
        }
    }
}

object WeightReminderScheduler {
    fun schedule(context: Context, hour: Int, minute: Int) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, WeightReminderReceiver::class.java)
        val pi = PendingIntent.getBroadcast(context, 1001, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(Calendar.getInstance())) add(Calendar.DAY_OF_MONTH, 1)
        }

        am.setRepeating(AlarmManager.RTC_WAKEUP, cal.timeInMillis, AlarmManager.INTERVAL_DAY, pi)

        val prefs = context.getSharedPreferences("pantri_settings", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("weight_reminder_enabled", true)
            .putInt("weight_reminder_hour", hour)
            .putInt("weight_reminder_minute", minute)
            .apply()
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, WeightReminderReceiver::class.java)
        val pi = PendingIntent.getBroadcast(context, 1001, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        am.cancel(pi)

        context.getSharedPreferences("pantri_settings", Context.MODE_PRIVATE)
            .edit().putBoolean("weight_reminder_enabled", false).apply()
    }

    fun isEnabled(context: Context): Boolean {
        return context.getSharedPreferences("pantri_settings", Context.MODE_PRIVATE)
            .getBoolean("weight_reminder_enabled", false)
    }

    fun getHour(context: Context): Int {
        return context.getSharedPreferences("pantri_settings", Context.MODE_PRIVATE)
            .getInt("weight_reminder_hour", 8)
    }

    fun getMinute(context: Context): Int {
        return context.getSharedPreferences("pantri_settings", Context.MODE_PRIVATE)
            .getInt("weight_reminder_minute", 0)
    }
}

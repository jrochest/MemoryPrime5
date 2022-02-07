package com.md

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.PendingIntent.CanceledException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.app.BundleCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.md.modesetters.TtsSpeaker
import kotlinx.coroutines.*
import java.time.Duration


object MemPrimeNotificationManager {

    private var currentTimerJob: Job? = null
    val CHANNEL_ID = "VERY_IMPORTANT_CHANNEL"
    private val notificationId: Int = 1

    private var createdChannel = false

    const val requestStartTimer = 1


    init {

    }

    fun createChannel(context: Context) {


        if (createdChannel) return

        createdChannel = true

        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        val name = "Shortcuts"
        val descriptionText = "Timers and buttons"
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = descriptionText
            enableVibration(false)
            enableLights(false)
        }
        // Register the channel with the system
        val notificationManager: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)



        updateNotification(context)
    }

    private fun updateNotification(context: Context) {
        val intent = Intent(context, SpacedRepeaterActivity::class.java)
        intent.putExtra(Extras.Keys.IS_TIMER_REQUEST.name, true)
        val startTimer =
            PendingIntent.getActivity(
                context,
                requestStartTimer,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        var builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.brain_icon)
            .setContentTitle("MemPrime")
            .setContentText("Timers")
            .setSilent(true)
            .addAction(0, currentTimerPhase, startTimer)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        with(NotificationManagerCompat.from(context)) {
            // notificationId is a unique int for each notification that you must define
            notify(notificationId, builder.build())
        }
    }

    private var currentTimerPhase: String = "10 5 10 timer"

    fun startTimer(context: Context) {
        if (currentTimerJob != null) {
            currentTimerJob?.cancel()
            currentTimerJob = null
            return
        }
        currentTimerJob = GlobalScope.launch(Dispatchers.Main) {
            currentTimerPhase = "Eat for 10 minutes"
            TtsSpeaker.speak(currentTimerPhase)

            TtsSpeaker.speak("eat slowly eat slowly", true)
            updateNotification(context)
            delay(Duration.ofMinutes(10).toMillis())
            TtsSpeaker.speak("eat slowly eat slowly", true)

            currentTimerPhase = "Break for 5 minutes"
            updateNotification(context)
            TtsSpeaker.speak(currentTimerPhase)
            delay(Duration.ofMinutes(5).toMillis())


            currentTimerPhase = "Eat for 10 minutes"
            updateNotification(context)
            TtsSpeaker.speak(currentTimerPhase)
            delay(Duration.ofMinutes(10).toMillis())

            currentTimerPhase = "10 5 10 timer"
            updateNotification(context)
            currentTimerJob = null
        }
    }


}

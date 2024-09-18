package com.kitty.analysis
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.kitty.analysis.MainActivity.Symbol
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.DecimalFormat

class KittyService : Service() {

    private val CHANNEL_ID = "KittyServiceChannel"
    private var serviceJob: Job? = null
    private lateinit var notificationManager: NotificationManager
    private var notificationId = 1  // ID for the notification, keeps the same to update it
    private var isInitial = true

    var actualPrice: Double = 0.0
    var entryPrice: Double = 0.0
    var factorRisk: Double = 0.0
    var gap: Double = 0.0

    override fun onCreate() {
        super.onCreate()
        // Create the notification channel for the foreground service
        createNotificationChannel()
        startForeground(notificationId, getNotification("BTC Analysis", "Service is running."))
        startRepeatingTask()

        // Initialize the NotificationManager
        notificationManager = getSystemService(NotificationManager::class.java)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        // Stop the task when the service is destroyed
        stopRepeatingTask()
    }

    // Method to start the repeating task
    private fun startRepeatingTask() {
        serviceJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                val urlString = "https://fapi.binance.com/fapi/v1/ticker/price?symbol=BTCUSDT"
                var data: Symbol? = null
                try {
                    val url = URL(urlString)
                    val connectionSymbol = url.openConnection() as HttpURLConnection
                    connectionSymbol.requestMethod = "GET"
                    connectionSymbol.connect()

                    val readerSymbol = BufferedReader(InputStreamReader(connectionSymbol.inputStream))
                    data = Gson().fromJson(readerSymbol, Symbol::class.java)
                    if (isInitial) {
                        entryPrice = data.price!!.toDouble()
                        isInitial = false
                    }
                    actualPrice = data.price!!.toDouble()
                    gap = actualPrice - entryPrice

                    if (gap >= 25) {
                        entryPrice += gap
                    }
                    if (Math.abs(gap) > factorRisk) {
                        if (gap > 0) {
                            performTask("sell sell")
                        } else {
                            performTask("buy buy")
                        }
                    }
                    readerSymbol.close()
                } catch (e: Exception) {
                    e.printStackTrace() // Log the exception
                }

                delay(3000L)  // Update every 3 seconds
            }
        }
    }

    // Method to stop the repeating task
    private fun stopRepeatingTask() {
        serviceJob?.cancel()
    }

    // Example task: Update the notification
    private fun performTask(content: String) {
        // Example: Print log
        Log.e("Kitty", "meow")

        // Show or update the notification
        val notification = getNotification("BTC Analysis", content)
        notificationManager.notify(notificationId, notification)  // Update notification
    }

    // Notification for the foreground service
    private fun getNotification(title: String, content: String): Notification {
        val soundUri: Uri = Uri.parse("android.resource://${applicationContext.packageName}/" + R.raw.btc_alarm)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setSound(soundUri)  // Custom sound
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val soundUri: Uri = Uri.parse("android.resource://${applicationContext.packageName}/" + R.raw.btc_alarm)
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Foreground Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                setSound(soundUri, attributes)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }
}

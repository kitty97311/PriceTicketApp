package com.kitty.analysis

import android.Manifest
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.DecimalFormat
import java.util.Timer
import java.util.TimerTask

class MainActivity : ComponentActivity(), View.OnClickListener {

    lateinit var symbolText: TextView
    lateinit var entryText: TextView
    lateinit var gapText: TextView
    lateinit var riskText: TextView
    lateinit var riskFactorInput: EditText
    lateinit var notifyButton: Button

    var actualPrice: Double = 0.0
    var entryPrice: Double = 0.0
    var factorRisk: Double = 0.0
    var gap: Double = 0.0
    var isInitial = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (arePermissionsGranted()) {
            // All required permissions are granted, proceed with functionality
        } else {
            // Request multiple permissions
            requestPermissions()
        }

        val channelId = "price_alerts_channel"
        val channelName: CharSequence = "Price Alerts"
        val channelDescription = "Notifications for price alerts"
        val importance = NotificationManager.IMPORTANCE_DEFAULT

        val channel = NotificationChannel(channelId, channelName, importance)
        channel.description = channelDescription

        val notificationManager = getSystemService(
            NotificationManager::class.java
        )
        notificationManager.createNotificationChannel(channel)

        symbolText = findViewById(R.id.symbolText)
        entryText = findViewById(R.id.entryText)
        gapText = findViewById(R.id.gapText)
        riskText = findViewById(R.id.riskText)
        riskFactorInput = findViewById(R.id.riskFactorInput)
        factorRisk = getRiskFactor()
        riskFactorInput.setText(factorRisk.toString())
        notifyButton = findViewById(R.id.notifyButton)
        notifyButton.setOnClickListener(this)
        notifyButton.isActivated = getRiskFactorOn()
        notifyButton.text = if (notifyButton.isActivated) "Turn off Notify" else "Turn on Notify"

        display()
    }

    private fun showInputDialog() {
        // Create an EditText for user input
        val editText = EditText(this)

        // Create the AlertDialog
        val dialog = AlertDialog.Builder(this)
            .setTitle("Risk Factor")
            .setMessage("Please enter risk factor.")
            .setView(editText)
            .setPositiveButton("OK") { dialogInterface, _ ->
                if (editText.text == null) Toast.makeText(this@MainActivity, "Input risk factor", Toast.LENGTH_SHORT).show()
                else {
                    factorRisk = editText.text.toString().toDouble()
                    setRiskFactor(true)
                    dialogInterface.dismiss()
                    display()
                }
            }
            .setNegativeButton("Cancel") { dialogInterface, _ ->
                dialogInterface.dismiss()
            }
            .create()

        // Show the dialog
        dialog.show()
    }

    private fun setRiskFactor(on_off: Boolean) {
        val sharedPreferences = getSharedPreferences("kitty_pref", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putFloat("risk_factor", factorRisk.toFloat())
        editor.putBoolean("risk_factor_on", on_off)
        editor.apply()
    }

    private fun getRiskFactor(): Double {
        val sharedPreferences = getSharedPreferences("kitty_pref", Context.MODE_PRIVATE)
        return sharedPreferences.getFloat("risk_factor", 5.0f).toDouble()
    }

    private fun getRiskFactorOn(): Boolean {
        val sharedPreferences = getSharedPreferences("kitty_pref", Context.MODE_PRIVATE)
        return sharedPreferences.getBoolean("risk_factor_on", false)
    }

    private lateinit var thread: Thread

    private fun display() {
        riskText.text = factorRisk.toString()
        // Crear un hilo para actualizar la información en tiempo real
        thread = Thread {
            while (true) {

                getPriceSymbol()

                try {
                    Thread.sleep(500) // Esperar 0 segundo antes de la siguiente actualización
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
            }
        }
        thread.start()
//        repeatInstructions(this)
    }

    fun repeatInstructions(context: Context) {
        val timer = Timer()
        val task: TimerTask = object : TimerTask() {
            override fun run() {
                try {
                    getPriceSymbol()
                    gap = actualPrice - entryPrice

                    if (gap >= 25) {
                        entryPrice += gap
                    }
                    if (Math.abs(gap) > factorRisk) {
                        if (gap > 0) {
                            sendNotification(context, "Sell Alert", "sell sell sell...")
                        } else {
                            sendNotification(context, "Buy Alert", "buy buy buy...")
                        }
                    }

                } catch (e: Exception) {
                    Log.e("NotificationError", "Error occurred: ${e.message}")
                }
            }
        }
        timer.schedule(task, 0, 1000)
    }

    fun getPriceSymbol() {
        CoroutineScope(Dispatchers.IO).launch {
            val urlString = "https://fapi.binance.com/fapi/v1/ticker/price?symbol=BTCUSDT"
            var data: Symbol? = null
            try {
                val url = URL(urlString)
                val connectionSymbol = url.openConnection() as HttpURLConnection
                connectionSymbol.requestMethod = "GET"
                connectionSymbol.connect()

                val readerSymbol = BufferedReader(InputStreamReader(connectionSymbol.inputStream))
                data = Gson().fromJson(readerSymbol, Symbol::class.java)
                readerSymbol.close()
            } catch (e: Exception) {
                e.printStackTrace() // Log the exception
            }

            if (isInitial) {
                entryPrice = data?.price!!.toDouble()
                isInitial = false
            }
            actualPrice = data?.price!!.toDouble()
            gap = actualPrice - entryPrice

            if (gap >= 25) {
                entryPrice += gap
            }

            // Switch to the Main thread to update the UI
            withContext(Dispatchers.Main) {
                symbolText.text = data?.price
                entryText.text = entryPrice.toString()
                gapText.text = DecimalFormat("#.###").format(gap)
            }
        }
    }

    private fun sendNotification(context: Context, title: String, message: String) {
        // Get the custom sound URI
        val soundUri = Uri.parse("android.resource://" + packageName + "/" + R.raw.btc_alarm)

        // Create notification channel for Android 8+ (Oreo and above)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build()

            val channel = NotificationChannel(
                "price_alerts_channel",  // Channel ID
                "Price Alerts",          // Channel Name
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                setSound(soundUri, audioAttributes) // Set custom sound for the channel
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }

        // Create the notification with custom sound
        val builder: NotificationCompat.Builder =
            NotificationCompat.Builder(this, "price_alerts_channel")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(message)
                .setSound(soundUri) // Set custom sound here
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        val notificationManager = NotificationManagerCompat.from(this)

        // Check for notification permission (Android 13+)
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // If permission is not granted, log the error
            Log.e("Permission is not granted", "Failed to send notification")
            return
        }

        // Display the notification
        notificationManager.notify(1, builder.build())
    }

    override fun onClick(p0: View?) {
        if (p0 == null) return
        if (p0.id == R.id.notifyButton) {
            if (riskFactorInput.text == null) Toast.makeText(this@MainActivity, "Input risk factor", Toast.LENGTH_SHORT).show()
            else toggleNotify()
        }
    }

    private fun toggleNotify() {
        factorRisk = riskFactorInput.text.toString().toDouble()
        if (notifyButton.isActivated) {
            Toast.makeText(this, "Notification is turned off!", Toast.LENGTH_SHORT).show()
            notifyButton.text = "Turn on Notify"
            notifyButton.isActivated = false
            stopService(Intent(this, KittyService::class.java))
        } else {
            Toast.makeText(this, "Notification is turned on!", Toast.LENGTH_SHORT).show()
            notifyButton.text = "Turn off Notify"
            notifyButton.isActivated = true
            startForegroundService(Intent(this, KittyService::class.java)) // Use startService() for Android < 8.0
        }
        setRiskFactor(notifyButton.isActivated)
        thread.interrupt()
        display()
    }

    private fun arePermissionsGranted(): Boolean {
        return listOf(
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.VIBRATE,
            // Add more permissions as needed
        ).all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        val requestPermissionsLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            // Handle the result for each permission
            permissions.entries.forEach { entry ->
                val permissionName = entry.key
                val isGranted = entry.value
                if (isGranted) {
                    // Permission granted, handle accordingly
                } else {
                    // Permission denied, handle accordingly
                }
            }
        }

        // Only request permissions on Android 13+ that require runtime permission
        val permissionsToRequest = mutableListOf<String>().apply {
            add(Manifest.permission.POST_NOTIFICATIONS)
            add(Manifest.permission.VIBRATE)
            // Add more permissions as needed
        }

        requestPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
    }

    class Symbol {
        var nameSymbol: String? = "kitty"
        var price: String? = "0"

        override fun toString(): String {
            return "Tracker{" +
                    "symbol='" + nameSymbol + '\'' +
                    ", price='" + price + '\'' +
                    '}'
        }
    }

}

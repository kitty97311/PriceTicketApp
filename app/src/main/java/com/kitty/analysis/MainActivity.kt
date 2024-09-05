package com.kitty.analysis

import android.Manifest
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
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

    var actualPrice: Double = 0.0
    var entryPrice: Double = 0.0
    var factorRisk: Double = 0.0
    var gap: Double = 0.0

    private lateinit var requestPermissionsLauncher: ActivityResultLauncher<Array<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        requestPermissions()
//        requestPermissionsLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
//            permissions.entries.forEach { entry ->
//                val permissionName = entry.key
//                val isGranted = entry.value
//                if (isGranted) {
//                    Toast.makeText(this, "$permissionName granted", Toast.LENGTH_SHORT).show()
//                } else {
//                    Toast.makeText(this, "$permissionName denied", Toast.LENGTH_SHORT).show()
//                }
//            }
//        }




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

        showInputDialog()

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
                    setRiskFactor()
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

    private fun setRiskFactor() {
        val sharedPreferences = getSharedPreferences("kitty_pref", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putFloat("risk_factor", factorRisk.toFloat())
        editor.apply()
    }

    private fun getRiskFactor(): Float {
        val sharedPreferences = getSharedPreferences("kitty_pref", Context.MODE_PRIVATE)
        return sharedPreferences.getFloat("risk_factor", 0.0f)
    }

    private fun display() {
        getPriceSymbol(0)
        // Crear un hilo para actualizar la información en tiempo real
        Thread {
            while (true) {

                getPriceSymbol(1)

                try {
                    Thread.sleep(500) // Esperar 0 segundo antes de la siguiente actualización
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
            }
        }.start()
        repeatInstructions(this)
    }

    fun repeatInstructions(context: Context) {
        val timer = Timer()
        val task: TimerTask = object : TimerTask() {
            override fun run() {
                try {
                    getPriceSymbol(2)
                    gap = actualPrice - entryPrice

                    if (gap >= 25) {
                        entryPrice += gap
                    }

                    if (actualPrice <= (entryPrice - factorRisk)) {
                        sendNotification(context, "Buy Alert", "buy buy buy...")
                    } else if (actualPrice >= (entryPrice + factorRisk)) {
                        sendNotification(context, "Sell Alert", "Up sell sell sell...")
                    }
                } catch (e: Exception) {
                    Log.e("NotificationError", "Error occurred: ${e.message}")
                }
            }
        }
        timer.schedule(task, 0, 1000)
    }

    fun getPriceSymbol(mode: Int) {
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

            // Switch to the Main thread to update the UI
            withContext(Dispatchers.Main) {
                if (mode == 0)
                    entryPrice = data?.price!!.toDouble()
                else if (mode ==1) {
                    symbolText.text = data?.price

                    entryText.text = entryPrice.toString()
                    gapText.text = DecimalFormat("#.###").format(gap)
                    riskText.text = factorRisk.toString()
                }
                else if (mode == 2)
                    actualPrice = data?.price!!.toDouble()

            }
        }
    }

    private fun sendNotification(context: Context, title: String, message: String) {
        val builder: NotificationCompat.Builder =
            NotificationCompat.Builder(this, "price_alerts_channel")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        val notificationManager = NotificationManagerCompat.from(this)
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            Log.e("Permission is not granted", "Failed")
            return
        }
        notificationManager.notify(1, builder.build())

    }

    override fun onClick(p0: View?) {
        if (p0 == null) return
        if (p0.id == R.id.notifyButton) {

        }
    }

    private fun requestPermissions() {
        // Check for permissions
        val permissionsToRequest = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Request permissions if needed
        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            Toast.makeText(this, "All permissions already granted", Toast.LENGTH_SHORT).show()
        }
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

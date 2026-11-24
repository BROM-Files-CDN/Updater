package com.brom.updater

import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class MainActivity : AppCompatActivity() {

    // ⚠️ REPLACE THIS WITH YOUR NEW GITHUB REPO JSON URL
    private val updateJsonUrl = "https://raw.githubusercontent.com/BROM-Files-CDN/Updates-CDN/main/latest.json"
    
    private val client = OkHttpClient()
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnCheckUpdates = findViewById<Button>(R.id.btnCheckUpdates)

        btnCheckUpdates.setOnClickListener {
            Toast.makeText(this, "Checking for updates...", Toast.LENGTH_SHORT).show()
            btnCheckUpdates.isEnabled = false
            
            CoroutineScope(Dispatchers.IO).launch {
                checkForUpdates()
            }
        }
    }

    private fun checkForUpdates() {
        try {
            val request = Request.Builder().url(updateJsonUrl).build()
            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val jsonString = response.body?.string()
                val json = JSONObject(jsonString!!)
                
                val newVersion = json.getString("latest_version")
                val downloadUrl = json.getString("download_url")
                val changelog = json.getString("changelog")
                val requiresFormat = json.optBoolean("requires_format_data", false)

                // ⚠️ Update this with your actual current ROM version
                val currentVersion = "1.4" 

                if (newVersion > currentVersion) {
                    if (requiresFormat) {
                        // Update found, but requires data format!
                        mainHandler.post {
                            showFormatDataWarning(newVersion, changelog, downloadUrl)
                        }
                    } else {
                        // Standard update
                        showToastOnUI("Update found: v$newVersion\nDownloading...")
                        downloadUpdate(downloadUrl, requiresFormat)
                    }
                } else {
                    showToastOnUI("You are on the latest version (v$currentVersion).")
                }
            } else {
                showToastOnUI("Server error: ${response.code}")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            showToastOnUI("Failed to check for updates. Check your internet.")
        }
    }

    private fun showFormatDataWarning(version: String, changelog: String, downloadUrl: String) {
        findViewById<Button>(R.id.btnCheckUpdates).isEnabled = true
        
        AlertDialog.Builder(this)
            .setTitle("⚠️ WARNING: Data Format Required")
            .setMessage("Version $version is available.\n\nChangelog:\n$changelog\n\n⚠️ This update REQUIRES a full data format. You will lose all internal storage. Do you want to download the update anyway?")
            .setPositiveButton("Download") { _, _ ->
                showToastOnUI("Downloading v$version...")
                CoroutineScope(Dispatchers.IO).launch {
                    downloadUpdate(downloadUrl, true)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun downloadUpdate(downloadUrl: String, requiresFormat: Boolean) {
        try {
            val request = Request.Builder().url(downloadUrl).build()
            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val downloadDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                val apkFile = File(downloadDir, "BROM_Update.zip")
                
                val inputStream: InputStream = response.body!!.byteStream()
                val outputStream = FileOutputStream(apkFile)

                val buffer = ByteArray(8192)
                var bytesRead: Int

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }

                outputStream.flush()
                outputStream.close()
                inputStream.close()

                showToastOnUI("Download complete!")
                
                // Proceed based on the flag
                if (requiresFormat) {
                    // Because a data format wipes /sdcard/, we CANNOT automate TWRP safely.
                    // The user must manually flash it.
                    mainHandler.post {
                        AlertDialog.Builder(this)
                            .setTitle("Manual Flash Required")
                            .setMessage("The update has been downloaded to /sdcard/Download/BROM_Update.zip.\n\nBecause this update requires formatting data, please reboot to TWRP, move the file to a USB OTG drive (if possible), format data, and flash the zip.")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                } else {
                    // Safe to automate
                    triggerRecoveryUpdate(apkFile.absolutePath)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            showToastOnUI("Download failed: ${e.message}")
        }
    }

    private fun triggerRecoveryUpdate(zipFilePath: String) {
        try {
            val command = arrayOf(
                "su", "-c", 
                "echo 'install $zipFilePath\nwipe cache\nreboot' > /cache/recovery/openrecoveryscript && chmod 664 /cache/recovery/openrecoveryscript && reboot recovery"
            )
            
            Runtime.getRuntime().exec(command)
            
        } catch (e: Exception) {
            e.printStackTrace()
            showToastOnUI("Root failed. Did you grant Superuser access?")
        }
    }

    private fun showToastOnUI(message: String) {
        mainHandler.post {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            findViewById<Button>(R.id.btnCheckUpdates).isEnabled = true
        }
    }
}

package com.brom.updater

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnCheckUpdates = findViewById<Button>(R.id.btnCheckUpdates)

        btnCheckUpdates.setOnClickListener {
            Toast.makeText(this, "Checking for updates...", Toast.LENGTH_SHORT).show()
            // For now, let's pretend the download finished and trigger the reboot
            triggerRecoveryUpdate() 
        }
    }

    private fun triggerRecoveryUpdate() {
        try {
            val updateZipPath = "/sdcard/Download/BROM_Update.zip"
            
            // Write TWRP script and reboot
            val command = arrayOf(
                "su", "-c", 
                "echo 'install $updateZipPath\nwipe cache\nreboot' > /cache/recovery/openrecoveryscript && reboot recovery"
            )
            
            Runtime.getRuntime().exec(command)

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Root failed. Is Magisk installed?", Toast.LENGTH_LONG).show()
        }
    }
}

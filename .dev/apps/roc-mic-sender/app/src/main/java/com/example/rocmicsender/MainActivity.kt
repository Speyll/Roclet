package com.example.rocmicsender

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.rocmicsender.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isBound = false
    private var serviceBinder: MicSenderService.LocalBinder? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            serviceBinder = binder as MicSenderService.LocalBinder
            isBound = true
            updateUi()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            isBound = false
            serviceBinder = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnStart.setOnClickListener { startStreaming() }
        binding.btnStop.setOnClickListener { stopStreaming() }

        updateUi()
    }

    override fun onStart() {
        super.onStart()
        bindService(Intent(this, MicSenderService::class.java), serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }

    private fun startStreaming() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1001)
            return
        }

        val ip = binding.etIp.text.toString().trim()
        if (ip.isEmpty()) {
            binding.tvStatus.text = "Please enter destination IP"
            return
        }

        val intent = Intent(this, MicSenderService::class.java).apply {
            putExtra("DEST_IP", ip)
            putExtra("ACTION", "START")
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        binding.tvStatus.text = "Starting..."
        updateUi()
    }

    private fun stopStreaming() {
        val intent = Intent(this, MicSenderService::class.java).apply {
            putExtra("ACTION", "STOP")
        }
        stopService(intent)
        binding.tvStatus.text = "Stopped"
        updateUi()
    }

    private fun updateUi() {
        val running = isBound && serviceBinder?.isRunning == true
        binding.btnStart.isEnabled = !running
        binding.btnStop.isEnabled = running
        binding.etIp.isEnabled = !running
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startStreaming()
        } else {
            binding.tvStatus.text = "Microphone permission required"
        }
    }
}

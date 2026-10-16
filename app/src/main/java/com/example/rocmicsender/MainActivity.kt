package com.example.rocmicsender

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
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
            updateUi()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.etIp.setText(
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(PREF_DEST_IP, "")
        )

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
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), PERM_REQUEST_CODE)
            return
        }

        val ip = binding.etIp.text.toString().trim()
        if (ip.isEmpty()) {
            binding.tvStatus.text = getString(R.string.status_enter_ip)
            return
        }

        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_DEST_IP, ip)
            .apply()

        val intent = Intent(this, MicSenderService::class.java).apply {
            putExtra(MicSenderService.EXTRA_DEST_IP, ip)
            putExtra(MicSenderService.EXTRA_ACTION, MicSenderService.ACTION_START)
        }

        ContextCompat.startForegroundService(this, intent)
        binding.tvStatus.text = getString(R.string.status_streaming)
        updateUi()
    }

    private fun stopStreaming() {
        val intent = Intent(this, MicSenderService::class.java).apply {
            putExtra(MicSenderService.EXTRA_ACTION, MicSenderService.ACTION_STOP)
        }
        startService(intent)
        binding.tvStatus.text = getString(R.string.status_idle)
        updateUi()
    }

    private fun updateUi() {
        val running = isBound && serviceBinder?.isRunning == true
        binding.btnStart.isEnabled = !running
        binding.btnStop.isEnabled = running
        binding.etIp.isEnabled = !running
        if (running) {
            binding.tvStatus.text = getString(R.string.status_streaming)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERM_REQUEST_CODE) {
            val audioGranted = permissions.indices.any { idx ->
                permissions[idx] == Manifest.permission.RECORD_AUDIO &&
                    grantResults.getOrNull(idx) == PackageManager.PERMISSION_GRANTED
            }
            if (audioGranted) {
                startStreaming()
            } else {
                binding.tvStatus.text = getString(R.string.status_mic_permission_required)
            }
        }
    }

    companion object {
        private const val PERM_REQUEST_CODE = 2001
        private const val PREFS_NAME = "roc_mic_prefs"
        private const val PREF_DEST_IP = "dest_ip"
    }
}

package com.roclet.mic

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.roclet.mic.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isBound = false
    private var serviceBinder: MicSenderService.LocalBinder? = null
    private var pendingAction = false
    private var settingsExpanded = false

    private val stateListener = MicSenderService.StateListener { state, error ->
        render(state, error)
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val localBinder = binder as MicSenderService.LocalBinder
            serviceBinder = localBinder
            isBound = true
            localBinder.addStateListener(stateListener)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            serviceBinder?.removeStateListener(stateListener)
            isBound = false
            serviceBinder = null
            render(MicSenderService.State.STOPPED, null)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        configureEdgeToEdge()

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        binding.etIp.setText(prefs.getString(PREF_DEST_IP, ""))
        binding.etSourcePort.setText(
            prefs.getString(PREF_SOURCE_PORT, RocAudioSender.DEFAULT_SOURCE_PORT.toString())
        )
        binding.etRepairPort.setText(
            prefs.getString(PREF_REPAIR_PORT, RocAudioSender.DEFAULT_REPAIR_PORT.toString())
        )

        settingsExpanded = savedInstanceState?.getBoolean(STATE_SETTINGS_EXPANDED) ?: false
        applySettingsExpanded()

        binding.ivSettings.setOnClickListener {
            settingsExpanded = !settingsExpanded
            applySettingsExpanded()
            updateDestSummary()
        }
        binding.btnMic.setOnClickListener { onMicClicked() }

        render(MicSenderService.State.STOPPED, null)
    }

    private fun configureEdgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        configureSystemBarColors()
        applySystemBarIconAppearance()

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            insets
        }
    }

    @Suppress("DEPRECATION")
    private fun configureSystemBarColors() {
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
    }

    private fun applySystemBarIconAppearance() {
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val controller = WindowInsetsControllerCompat(window, binding.root)
        controller.isAppearanceLightStatusBars = !night
        controller.isAppearanceLightNavigationBars = !night
    }

    override fun onStart() {
        super.onStart()
        bindService(
            Intent(this, MicSenderService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )
    }

    override fun onStop() {
        super.onStop()
        pendingAction = false
        if (isBound) {
            serviceBinder?.removeStateListener(stateListener)
            unbindService(serviceConnection)
            isBound = false
            serviceBinder = null
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_SETTINGS_EXPANDED, settingsExpanded)
    }

    private fun onMicClicked() {
        if (pendingAction) return
        when (serviceBinder?.state ?: MicSenderService.State.STOPPED) {
            MicSenderService.State.STOPPED, MicSenderService.State.ERROR -> requestStart()
            MicSenderService.State.STREAMING -> requestStop()
            MicSenderService.State.STARTING, MicSenderService.State.STOPPING -> Unit
        }
    }

    private fun requestStart() {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            pendingAction = true
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), PERM_REQUEST_CODE)
            return
        }

        val ip = binding.etIp.text.toString().trim()
        if (ip.isEmpty()) {
            showError(getString(R.string.status_enter_ip))
            return
        }

        binding.etSourcePort.error = null
        binding.etRepairPort.error = null

        val sourcePort = parsePort(binding.etSourcePort) ?: return
        val repairPort = parsePort(binding.etRepairPort) ?: return
        if (sourcePort == repairPort) {
            binding.etRepairPort.error = getString(R.string.error_ports_must_differ)
            return
        }

        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_DEST_IP, ip)
            .putString(PREF_SOURCE_PORT, sourcePort.toString())
            .putString(PREF_REPAIR_PORT, repairPort.toString())
            .apply()

        val intent = Intent(this, MicSenderService::class.java).apply {
            putExtra(MicSenderService.EXTRA_DEST_IP, ip)
            putExtra(MicSenderService.EXTRA_SOURCE_PORT, sourcePort)
            putExtra(MicSenderService.EXTRA_REPAIR_PORT, repairPort)
            putExtra(MicSenderService.EXTRA_ACTION, MicSenderService.ACTION_START)
        }

        pendingAction = true
        binding.btnMic.isEnabled = false
        binding.btnMic.alpha = DISABLED_ALPHA
        ContextCompat.startForegroundService(this, intent)
    }

    private fun requestStop() {
        val intent = Intent(this, MicSenderService::class.java).apply {
            putExtra(MicSenderService.EXTRA_ACTION, MicSenderService.ACTION_STOP)
        }
        pendingAction = true
        binding.btnMic.isEnabled = false
        binding.btnMic.alpha = DISABLED_ALPHA
        startService(intent)
    }

    private fun render(state: MicSenderService.State, error: String?) {
        pendingAction = false

        setFieldsEnabled(
            state == MicSenderService.State.STOPPED || state == MicSenderService.State.ERROR
        )

        when (state) {
            MicSenderService.State.STOPPED -> {
                binding.btnMic.isEnabled = true
                tintMic(R.color.mic_ready)
                binding.btnMic.contentDescription = getString(R.string.cd_start_stream)
                setStatus(R.string.state_stopped, R.color.text_primary)
            }
            MicSenderService.State.STARTING -> {
                binding.btnMic.isEnabled = false
                tintMic(R.color.mic_transitional)
                binding.btnMic.contentDescription = getString(R.string.cd_starting)
                setStatus(R.string.state_starting, R.color.text_secondary)
            }
            MicSenderService.State.STREAMING -> {
                binding.btnMic.isEnabled = true
                tintMic(R.color.mic_streaming)
                binding.btnMic.contentDescription = getString(R.string.cd_stop_stream)
                setStatus(R.string.state_streaming, R.color.status_streaming)
            }
            MicSenderService.State.STOPPING -> {
                binding.btnMic.isEnabled = false
                tintMic(R.color.mic_transitional)
                binding.btnMic.contentDescription = getString(R.string.cd_stopping)
                setStatus(R.string.state_stopping, R.color.text_secondary)
            }
            MicSenderService.State.ERROR -> {
                binding.btnMic.isEnabled = true
                tintMic(R.color.mic_ready)
                binding.btnMic.contentDescription = getString(R.string.cd_start_stream)
                binding.tvStatus.text = error ?: getString(R.string.error_start_failed)
                binding.tvStatus.setTextColor(
                    ContextCompat.getColor(this, R.color.status_error)
                )
            }
        }

        binding.btnMic.alpha = if (binding.btnMic.isEnabled) 1f else DISABLED_ALPHA
        updateDestSummary()
    }

    private fun tintMic(colorRes: Int) {
        binding.btnMic.backgroundTintList =
            ColorStateList.valueOf(ContextCompat.getColor(this, colorRes))
    }

    private fun setStatus(textRes: Int, colorRes: Int) {
        binding.tvStatus.setText(textRes)
        binding.tvStatus.setTextColor(ContextCompat.getColor(this, colorRes))
    }

    private fun showError(message: String) {
        binding.tvStatus.text = message
        binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.status_error))
    }

    private fun setFieldsEnabled(enabled: Boolean) {
        binding.etIp.isEnabled = enabled
        binding.etSourcePort.isEnabled = enabled
        binding.etRepairPort.isEnabled = enabled
    }

    private fun applySettingsExpanded() {
        binding.settingsContainer.visibility = if (settingsExpanded) View.VISIBLE else View.GONE
        binding.ivSettings.contentDescription = getString(
            if (settingsExpanded) R.string.settings_expanded else R.string.settings_collapsed
        )
    }

    private fun updateDestSummary() {
        val ip = binding.etIp.text.toString().trim()
        val source = binding.etSourcePort.text.toString().trim()
        val repair = binding.etRepairPort.text.toString().trim()
        binding.tvDestSummary.text = if (ip.isEmpty()) {
            getString(R.string.dest_summary_none)
        } else {
            ip
        }
        binding.tvPortSummary.text = if (ip.isEmpty()) {
            ""
        } else {
            getString(R.string.dest_ports_format, source, repair)
        }
    }

    private fun parsePort(field: EditText): Int? {
        val port = field.text.toString().trim().toIntOrNull()
        if (port == null || port !in 1..65535) {
            field.error = getString(R.string.error_invalid_port)
            return null
        }
        return port
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
            pendingAction = false
            if (audioGranted) {
                requestStart()
            } else {
                showError(getString(R.string.status_mic_permission_required))
            }
        }
    }

    companion object {
        private const val PERM_REQUEST_CODE = 2001
        private const val PREFS_NAME = "roc_mic_prefs"
        private const val PREF_DEST_IP = "dest_ip"
        private const val PREF_SOURCE_PORT = "source_port"
        private const val PREF_REPAIR_PORT = "repair_port"
        private const val STATE_SETTINGS_EXPANDED = "settings_expanded"
        private const val DISABLED_ALPHA = 0.6f
    }
}

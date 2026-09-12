package com.example.hanotifier

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: NotificationAdapter
    private lateinit var tvStatus: android.widget.TextView

    private var normalToolbarTitle: CharSequence = ""

    private lateinit var selectionBackCallback: OnBackPressedCallback

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val status = intent?.getStringExtra(HaWebSocketService.EXTRA_STATUS) ?: return
            tvStatus.text = status
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setSupportActionBar(findViewById(R.id.toolbar))
        normalToolbarTitle = supportActionBar?.title ?: "HaNotifier"

        tvStatus = findViewById(R.id.tvStatus)
        recyclerView = findViewById(R.id.recyclerView)

        adapter = NotificationAdapter(emptyList()) { selectedCount ->
            updateSelectionMode(selectedCount)
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        AppDatabase.getInstance(this).notificationDao().getAllLive().observe(this) { list ->
            adapter.updateData(list)

            if (list.isNotEmpty()) {
                recyclerView.scrollToPosition(0)
            }
        }

        selectionBackCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                adapter.clearSelection()
            }
        }

        onBackPressedDispatcher.addCallback(
            this,
            selectionBackCallback
        )

        requestNotificationPermissionIfNeeded()

        lifecycleScope.launch {
            HistoryCleaner.cleanupIfNeeded(applicationContext)
        }

        if (Prefs.isConfigured(this)) {
            val last = Prefs.lastStatus(this)
            tvStatus.text = last.ifBlank { "conectando..." }
            startHaService()
        } else {
            tvStatus.text = "desconectado — toque no ⚙ para configurar a conexão"
        }
    }

    override fun onResume() {
        super.onResume()

        val filter = IntentFilter(HaWebSocketService.ACTION_STATUS)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                statusReceiver,
                filter,
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(statusReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(statusReceiver)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        updateMenuVisibility(menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        updateMenuVisibility(menu)
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                return true
            }

            R.id.action_delete -> {
                deleteSelectedNotifications()
                return true
            }
        }

        return super.onOptionsItemSelected(item)
    }

    private fun updateSelectionMode(selectedCount: Int) {
        val selectionMode = selectedCount > 0

        selectionBackCallback.isEnabled = selectionMode

        supportActionBar?.title = if (selectionMode) {
            if (selectedCount == 1) {
                "1 selecionada"
            } else {
                "$selectedCount selecionadas"
            }
        } else {
            normalToolbarTitle
        }

        invalidateOptionsMenu()
    }

    private fun updateMenuVisibility(menu: Menu?) {
        menu ?: return

        val selectionMode = adapter.getSelectedIds().isNotEmpty()

        menu.findItem(R.id.action_settings)?.isVisible = !selectionMode
        menu.findItem(R.id.action_delete)?.isVisible = selectionMode
    }

    private fun deleteSelectedNotifications() {
        val selectedIds = adapter.getSelectedIds()

        if (selectedIds.isEmpty()) {
            return
        }

        lifecycleScope.launch {
            AppDatabase.getInstance(this@MainActivity)
                .notificationDao()
                .deleteByIds(selectedIds)

            adapter.clearSelection()
        }
    }

    private fun startHaService() {
        val intent = Intent(this, HaWebSocketService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (
                ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    1
                )
            }
        }
    }
}

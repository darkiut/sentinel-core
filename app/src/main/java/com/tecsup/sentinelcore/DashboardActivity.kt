package com.tecsup.sentinelcore

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton

class DashboardActivity : AppCompatActivity() {

    private lateinit var tvUserEmail: TextView
    private lateinit var tvBlockedCount: TextView
    private lateinit var tvTotalEvents: TextView

    private lateinit var cardRedTeam: MaterialCardView
    private lateinit var cardBlueTeam: MaterialCardView
    private lateinit var cardEventLog: MaterialCardView
    private lateinit var cardSettings: MaterialCardView
    private lateinit var fabStats: FloatingActionButton

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        val userEmail = intent.getStringExtra("USER_EMAIL") ?: "usuario@ejemplo.com"

        initViews()
        setupUserInfo(userEmail)
        setupClickListeners()
        loadEventStats()
        animateCardsOnLoad()
        startFabPulseAnimation()
        startStatsUpdater()
        setupBackPressHandler()
    }

    private fun initViews() {
        tvUserEmail = findViewById(R.id.tvUserEmail)
        tvBlockedCount = findViewById(R.id.tvBlockedCount)
        tvTotalEvents = findViewById(R.id.tvTotalEvents)

        cardRedTeam = findViewById(R.id.cardRedTeam)
        cardBlueTeam = findViewById(R.id.cardBlueTeam)
        cardEventLog = findViewById(R.id.cardEventLog)
        cardSettings = findViewById(R.id.cardSettings)
        fabStats = findViewById(R.id.fabStats)
    }

    private fun setupUserInfo(email: String) {
        tvUserEmail.text = email
    }

    private fun setupClickListeners() {
        cardRedTeam.setOnClickListener { view ->
            animateCardClick(view)
            startActivity(Intent(this, RedTeamActivity::class.java))
        }

        cardBlueTeam.setOnClickListener { view ->
            animateCardClick(view)
            startActivity(Intent(this, BlueTeamActivity::class.java))
        }

        cardEventLog.setOnClickListener { view ->
            animateCardClick(view)
            startActivity(Intent(this, EventLogActivity::class.java))
        }

        cardSettings.setOnClickListener { view ->
            animateCardClick(view)
            val intent = Intent(this, SettingsActivity::class.java)
            intent.putExtra("USER_EMAIL", tvUserEmail.text.toString())
            startActivity(intent)
        }

        fabStats.setOnClickListener {
            showStatsDialog()
        }
    }

    private fun loadEventStats() {
        try {
            val events = EventLogActivity.getEvents()

            val blockedCount = events.count {
                it.type == EventType.ATTACK_BLOCKED || it.type == EventType.FIREWALL_BLOCK
            }
            val totalEvents = events.size

            tvBlockedCount.text = "🛡️ $blockedCount bloqueados"
            tvTotalEvents.text = "📊 $totalEvents eventos"
        } catch (e: Exception) {
            tvBlockedCount.text = "🛡️ 0 bloqueados"
            tvTotalEvents.text = "📊 0 eventos"
        }
    }

    private fun animateCardClick(view: android.view.View) {
        view.animate()
            .scaleX(0.95f)
            .scaleY(0.95f)
            .setDuration(100)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(100)
                    .start()
            }
            .start()
    }

    private fun animateCardsOnLoad() {
        val cards = listOf(cardRedTeam, cardBlueTeam, cardEventLog, cardSettings)

        cards.forEachIndexed { index, card ->
            card.alpha = 0f
            card.translationY = 50f

            card.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(400)
                .setStartDelay((index * 80).toLong())
                .start()
        }
    }

    private fun startFabPulseAnimation() {
        val scaleX = ObjectAnimator.ofFloat(fabStats, "scaleX", 1f, 1.1f, 1f)
        val scaleY = ObjectAnimator.ofFloat(fabStats, "scaleY", 1f, 1.1f, 1f)

        scaleX.duration = 1500
        scaleY.duration = 1500
        scaleX.repeatCount = ObjectAnimator.INFINITE
        scaleY.repeatCount = ObjectAnimator.INFINITE

        scaleX.start()
        scaleY.start()
    }

    private fun startStatsUpdater() {
        val statsRunnable = object : Runnable {
            override fun run() {
                loadEventStats()
                handler.postDelayed(this, 3000)
            }
        }
        handler.post(statsRunnable)
    }

    private fun showStatsDialog() {
        try {
            val events = EventLogActivity.getEvents()

            val attacksSent = events.count { it.type == EventType.ATTACK_SENT }
            val attacksBlocked = events.count { it.type == EventType.ATTACK_BLOCKED }
            val defensesActivated = events.count { it.type == EventType.DEFENSE_ACTIVATED }
            val totalEvents = events.size

            val message = """
                📊 Estadísticas del Sistema
                
                🔴 Ataques enviados: $attacksSent
                🛡️ Ataques bloqueados: $attacksBlocked
                ✅ Defensas activadas: $defensesActivated
                📋 Total de eventos: $totalEvents
            """.trimIndent()

            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Estadísticas Detalladas")
                .setMessage(message)
                .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                .setNeutralButton("Ver Log") { _, _ ->
                    startActivity(Intent(this, EventLogActivity::class.java))
                }
                .show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error al cargar estadísticas", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                androidx.appcompat.app.AlertDialog.Builder(this@DashboardActivity)
                    .setTitle("Salir de Sentinel Core")
                    .setMessage("¿Deseas cerrar la aplicación?")
                    .setPositiveButton("Salir") { _, _ ->
                        finish()
                        finishAffinity()
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
        })
    }

    override fun onResume() {
        super.onResume()
        loadEventStats()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}

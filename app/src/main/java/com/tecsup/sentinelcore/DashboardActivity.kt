package com.tecsup.sentinelcore

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        val userEmail = intent.getStringExtra("USER_EMAIL") ?: "usuario@ejemplo.com"

        initViews()
        setupUserInfo(userEmail)
        setupClickListeners()
        loadEventStats()
        animateCardsOnLoad()
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
            // Si tienes StatsActivity, descomentar la siguiente línea:
            // startActivity(Intent(this, StatsActivity::class.java))

            // Mientras tanto, mostrar un mensaje
            Toast.makeText(this, "Estadísticas próximamente", Toast.LENGTH_SHORT).show()
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
            // Si hay error al cargar eventos, mostrar valores por defecto
            tvBlockedCount.text = "🛡️ 0 bloqueados"
            tvTotalEvents.text = "📊 0 eventos"
        }
    }

    private fun animateCardClick(view: android.view.View) {
        // Efecto de escala al hacer clic
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
        // Animar cards al cargar
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

    override fun onResume() {
        super.onResume()
        loadEventStats() // Actualizar estadísticas cuando volvamos
    }
}

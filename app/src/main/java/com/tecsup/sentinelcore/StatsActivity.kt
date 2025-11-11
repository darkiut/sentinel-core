package com.tecsup.sentinelcore

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.TextView
import com.google.android.material.progressindicator.LinearProgressIndicator

class StatsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stats_simple)

        setupToolbar()
        loadStats()
    }

    private fun setupToolbar() {
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun loadStats() {
        val events = EventLogActivity.getEvents()

        val enviados = events.count { it.type == EventType.ATTACK_SENT }
        val bloqueados = events.count { it.type == EventType.ATTACK_BLOCKED }
        val defensa = events.count { it.type == EventType.DEFENSE_ACTIVATED }
        val intrusiones = events.count { it.type == EventType.INTRUSION_DETECTED }
        val firewall = events.count { it.type == EventType.FIREWALL_BLOCK }
        val total = events.size

        // Actualizar TextViews
        findViewById<TextView>(R.id.tvDetectados).text = "Ataques Enviados: $enviados"
        findViewById<TextView>(R.id.tvBloqueados).text = "Bloqueados: $bloqueados"
        findViewById<TextView>(R.id.tvFirewall).text = "Firewall Block: $firewall"
        findViewById<TextView>(R.id.tvPhishing).text = "Intrusiones: $intrusiones"
        findViewById<TextView>(R.id.tvTotal).text = "Total de Eventos: $total"

        // Actualizar barras de progreso
        val maxValue = maxOf(enviados, bloqueados, firewall, intrusiones, 1)

        findViewById<LinearProgressIndicator>(R.id.progressDetectados).progress =
            if (maxValue > 0) (enviados * 100 / maxValue) else 0

        findViewById<LinearProgressIndicator>(R.id.progressBloqueados).progress =
            if (maxValue > 0) (bloqueados * 100 / maxValue) else 0

        findViewById<LinearProgressIndicator>(R.id.progressFirewall).progress =
            if (maxValue > 0) (firewall * 100 / maxValue) else 0

        findViewById<LinearProgressIndicator>(R.id.progressPhishing).progress =
            if (maxValue > 0) (intrusiones * 100 / maxValue) else 0
    }
}

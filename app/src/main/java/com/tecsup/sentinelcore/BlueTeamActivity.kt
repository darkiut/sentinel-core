package com.tecsup.sentinelcore

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BlueTeamActivity : AppCompatActivity() {

    // Vistas
    private lateinit var tvDefenseStatus: TextView
    private lateinit var tvBlockedAttacks: TextView
    private lateinit var btnToggleDefense: MaterialButton

    private lateinit var switchFirewall: SwitchMaterial
    private lateinit var tvFirewallStatus: TextView
    private lateinit var tvBlockedIps: TextView

    private lateinit var switchIDS: SwitchMaterial
    private lateinit var tvIDSStatus: TextView
    private lateinit var tvIDSDetections: TextView

    private lateinit var switchAntiDeauth: SwitchMaterial
    private lateinit var tvAntiDeauthStatus: TextView
    private lateinit var tvDeauthBlocked: TextView

    private lateinit var tvTraffic: TextView
    private lateinit var tvThreats: TextView
    private lateinit var tvDefenseLog: TextView

    // Variables de estado
    private var defenseActive = false
    private var firewallEnabled = false
    private var idsEnabled = false
    private var antiDeauthEnabled = false

    private var blockedAttacks = 0
    private var blockedIps = mutableListOf<String>()
    private var idsDetections = 0
    private var deauthBlocked = 0
    private var threatCount = 0

    private val handler = Handler(Looper.getMainLooper())
    private var monitoringRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_blue_team)

        initViews()
        setupToolbar()
        setupListeners()
    }

    private fun initViews() {
        tvDefenseStatus = findViewById(R.id.tvDefenseStatus)
        tvBlockedAttacks = findViewById(R.id.tvBlockedAttacks)
        btnToggleDefense = findViewById(R.id.btnToggleDefense)

        switchFirewall = findViewById(R.id.switchFirewall)
        tvFirewallStatus = findViewById(R.id.tvFirewallStatus)
        tvBlockedIps = findViewById(R.id.tvBlockedIps)

        switchIDS = findViewById(R.id.switchIDS)
        tvIDSStatus = findViewById(R.id.tvIDSStatus)
        tvIDSDetections = findViewById(R.id.tvIDSDetections)

        switchAntiDeauth = findViewById(R.id.switchAntiDeauth)
        tvAntiDeauthStatus = findViewById(R.id.tvAntiDeauthStatus)
        tvDeauthBlocked = findViewById(R.id.tvDeauthBlocked)

        tvTraffic = findViewById(R.id.tvTraffic)
        tvThreats = findViewById(R.id.tvThreats)
        tvDefenseLog = findViewById(R.id.tvDefenseLog)
    }

    private fun setupToolbar() {
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupListeners() {
        // Activar/Desactivar defensa general
        btnToggleDefense.setOnClickListener {
            if (defenseActive) {
                deactivateDefense()
            } else {
                activateDefense()
            }
        }

        // Switch Firewall
        switchFirewall.setOnCheckedChangeListener { _, isChecked ->
            firewallEnabled = isChecked
            if (isChecked) {
                tvFirewallStatus.text = "🟢 Activo"
                findViewById<android.view.View>(R.id.layoutFirewallConfig).visibility =
                    android.view.View.VISIBLE
                logDefense("✅ Firewall activado")
                Toast.makeText(this, "Firewall activado", Toast.LENGTH_SHORT).show()
            } else {
                tvFirewallStatus.text = "⚪ Desactivado"
                findViewById<android.view.View>(R.id.layoutFirewallConfig).visibility =
                    android.view.View.GONE
                logDefense("⏹️ Firewall desactivado")
            }
        }

        // Switch IDS
        switchIDS.setOnCheckedChangeListener { _, isChecked ->
            idsEnabled = isChecked
            if (isChecked) {
                tvIDSStatus.text = "🟢 Activo"
                tvIDSDetections.visibility = android.view.View.VISIBLE
                logDefense("✅ IDS/IPS activado")
                Toast.makeText(this, "IDS/IPS activado", Toast.LENGTH_SHORT).show()
                startIDSMonitoring()
            } else {
                tvIDSStatus.text = "⚪ Desactivado"
                tvIDSDetections.visibility = android.view.View.GONE
                logDefense("⏹️ IDS/IPS desactivado")
            }
        }

        // Switch Anti-Deauth
        switchAntiDeauth.setOnCheckedChangeListener { _, isChecked ->
            antiDeauthEnabled = isChecked
            if (isChecked) {
                tvAntiDeauthStatus.text = "🟢 Activo"
                tvDeauthBlocked.visibility = android.view.View.VISIBLE
                logDefense("✅ Protección Anti-Deauth activada")
                Toast.makeText(this, "Anti-Deauth activado", Toast.LENGTH_SHORT).show()
            } else {
                tvAntiDeauthStatus.text = "⚪ Desactivado"
                tvDeauthBlocked.visibility = android.view.View.GONE
                logDefense("⏹️ Protección Anti-Deauth desactivada")
            }
        }
    }

    private fun activateDefense() {
        defenseActive = true
        tvDefenseStatus.text = "🟢 Activo"
        btnToggleDefense.text = "Desactivar Defensa"
        btnToggleDefense.setBackgroundColor(getColor(R.color.warning))

        logDefense("🛡️ SISTEMA DE DEFENSA ACTIVADO")

        // Registrar evento
        EventLogActivity.addEvent(
            SecurityEvent(
                type = EventType.DEFENSE_ACTIVATED,
                title = "Sistema de Defensa Activado",
                description = "Todas las protecciones están ahora activas",
                sourceIp = "Sistema",
                status = EventStatus.SUCCESS
            )
        )

        Toast.makeText(this, "Sistema de defensa activado", Toast.LENGTH_SHORT).show()
        startNetworkMonitoring()
    }

    private fun deactivateDefense() {
        defenseActive = false
        tvDefenseStatus.text = "⚪ Inactivo"
        btnToggleDefense.text = "Activar Defensa"
        btnToggleDefense.setBackgroundColor(getColor(R.color.blue_team_dark))

        // Desactivar todos los sistemas
        switchFirewall.isChecked = false
        switchIDS.isChecked = false
        switchAntiDeauth.isChecked = false

        logDefense("⏹️ Sistema de defensa desactivado")
        stopNetworkMonitoring()
    }

    private fun startNetworkMonitoring() {
        monitoringRunnable = object : Runnable {
            override fun run() {
                // Simular detección de tráfico
                val traffic = (10..100).random()
                tvTraffic.text = "$traffic KB/s"

                // Simular detección aleatoria de amenazas (para demostración)
                if (defenseActive && (0..100).random() > 90) {
                    detectThreat()
                }

                handler.postDelayed(this, 2000) // Actualizar cada 2 segundos
            }
        }
        handler.post(monitoringRunnable!!)
    }

    private fun stopNetworkMonitoring() {
        monitoringRunnable?.let {
            handler.removeCallbacks(it)
        }
        tvTraffic.text = "0 KB/s"
    }

    private fun startIDSMonitoring() {
        // Simular detección de intrusiones
        handler.postDelayed({
            if (idsEnabled) {
                idsDetections++
                tvIDSDetections.text = "Detecciones: $idsDetections"
                logDefense("🔍 IDS detectó actividad sospechosa")
            }
        }, 5000)
    }

    private fun detectThreat() {
        threatCount++
        tvThreats.text = "$threatCount"

        val attackTypes = listOf("Deauth", "Port Scan", "Packet Flood", "Man-in-the-Middle")
        val attackType = attackTypes.random()
        val sourceIp = "192.168.1.${(1..254).random()}"

        logDefense("⚠️ Amenaza detectada: $attackType desde $sourceIp")

        // Registrar detección
        EventLogActivity.addEvent(
            SecurityEvent(
                type = EventType.INTRUSION_DETECTED,
                title = "$attackType Detectado",
                description = "IDS detectó un intento de $attackType",
                sourceIp = sourceIp,
                status = EventStatus.DETECTED
            )
        )

        when (attackType) {
            "Deauth" -> {
                if (antiDeauthEnabled) {
                    blockDeauthAttack(sourceIp)
                } else {
                    logDefense("⚠️ Anti-Deauth desactivado - Ataque no bloqueado")
                }
            }
            "Packet Flood" -> {
                if (firewallEnabled) {
                    blockIpAddress(sourceIp)
                } else {
                    logDefense("⚠️ Firewall desactivado - Ataque no bloqueado")
                }
            }
            else -> {
                if (idsEnabled) {
                    logDefense("🛡️ IDS bloqueó el ataque")
                    blockedAttacks++
                    updateBlockedCount()

                    EventLogActivity.addEvent(
                        SecurityEvent(
                            type = EventType.ATTACK_BLOCKED,
                            title = "$attackType Bloqueado",
                            description = "IDS/IPS neutralizó el ataque exitosamente",
                            sourceIp = sourceIp,
                            status = EventStatus.BLOCKED
                        )
                    )
                }
            }
        }
    }

    private fun blockDeauthAttack(sourceIp: String) {
        deauthBlocked++
        blockedAttacks++
        tvDeauthBlocked.text = "Ataques bloqueados: $deauthBlocked"
        updateBlockedCount()
        logDefense("🛡️ Ataque Deauth bloqueado desde $sourceIp")

        // Registrar evento
        EventLogActivity.addEvent(
            SecurityEvent(
                type = EventType.ATTACK_BLOCKED,
                title = "Ataque Deauth Bloqueado",
                description = "Protección Anti-Deauth neutralizó el ataque",
                sourceIp = sourceIp,
                status = EventStatus.BLOCKED
            )
        )

        Toast.makeText(this, "Ataque Deauth bloqueado!", Toast.LENGTH_SHORT).show()
    }

    private fun blockIpAddress(ip: String) {
        if (!blockedIps.contains(ip)) {
            blockedIps.add(ip)
            blockedAttacks++
            updateBlockedCount()

            val ipsText = blockedIps.joinToString("\n")
            tvBlockedIps.text = ipsText

            logDefense("🚫 IP bloqueada: $ip")

            // Registrar evento
            EventLogActivity.addEvent(
                SecurityEvent(
                    type = EventType.FIREWALL_BLOCK,
                    title = "IP Bloqueada por Firewall",
                    description = "Firewall bloqueó la IP $ip por actividad maliciosa",
                    sourceIp = ip,
                    status = EventStatus.BLOCKED
                )
            )

            Toast.makeText(this, "IP $ip bloqueada", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateBlockedCount() {
        tvBlockedAttacks.text = "$blockedAttacks"
    }

    private fun logDefense(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val logMessage = "[$timestamp] $message\n"

        handler.post {
            tvDefenseLog.append(logMessage)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopNetworkMonitoring()
    }
}
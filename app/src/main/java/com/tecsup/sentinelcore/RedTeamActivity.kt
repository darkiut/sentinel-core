package com.tecsup.sentinelcore

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.Formatter
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RedTeamActivity : AppCompatActivity() {

    // --- VISTAS ---
    private lateinit var btnOpenWifi: MaterialButton
    private lateinit var tvWifiStatus: TextView
    private lateinit var tvWifiName: TextView
    private lateinit var tvLocalIp: TextView
    private lateinit var tvEsp32Status: TextView
    private lateinit var etEsp32Ip: TextInputEditText
    private lateinit var btnScanDevices: MaterialButton
    private lateinit var fabSendRemote: ExtendedFloatingActionButton
    private lateinit var etTargetMac: TextInputEditText
    private lateinit var btnDeauth: MaterialButton
    private lateinit var etSsidName: TextInputEditText
    private lateinit var btnCaptivePortal: MaterialButton
    private lateinit var tvCapturedData: TextView
    private lateinit var etTargetIp: TextInputEditText
    private lateinit var btnPacketFlood: MaterialButton
    private lateinit var tvPacketsSent: TextView
    private lateinit var tvActivityLog: TextView

    // --- VARIABLES DE ESTADO ---
    private var deauthActive = false
    private var packetFloodActive = false
    private var captivePortalActive = false

    // [NUEVO] Contador de ataques enviados
    private var attacksSentCount = 0
    private lateinit var prefs: SharedPreferences

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_red_team)

        // Inicializar Memoria
        prefs = getSharedPreferences("RedTeamPrefs", Context.MODE_PRIVATE)

        initViews()
        setupToolbar()

        // Restaurar datos guardados (Contador)
        restoreData()

        loadNetworkInfo()
        setupListeners()

        tvEsp32Status.text = "☁️ Modo Híbrido Activo"

        // Iniciar sistemas
        NetworkAttackManager.initMqtt(this)
        val user = FirebaseAuth.getInstance().currentUser
        if (user != null) {
            logActivity("✅ Operador: ${user.email}")
            NetworkAttackManager.registerDevice { logActivity("📡 Enlazado a Operaciones") }
        }
    }

    // --- PERSISTENCIA DE DATOS ---
    private fun restoreData() {
        attacksSentCount = prefs.getInt("attacks_sent", 0)
        updateCountersUI()
    }

    private fun saveData() {
        prefs.edit().putInt("attacks_sent", attacksSentCount).apply()
    }

    private fun updateCountersUI() {
        try {
            // Actualizamos el texto en pantalla
            tvPacketsSent.text = "Ataques Enviados: $attacksSentCount"
        } catch (e: Exception) {}
    }

    // --- LÓGICA DE LISTENERS ---
    private fun setupListeners() {
        btnOpenWifi.setOnClickListener { startActivity(Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)) }

        fabSendRemote.setOnClickListener {
            if (!isInternetAvailable()) {
                Toast.makeText(this, "Sin Internet", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            logActivity("🔍 Escaneando objetivos...")
            loadOnlineDevicesForSimulation()
        }

        // 1. DEAUTH
        btnDeauth.setOnClickListener {
            val targetMac = etTargetMac.text.toString().trim()
            val macFinal = if (targetMac.isEmpty()) "FF:FF:FF:FF:FF:FF" else targetMac

            if (deauthActive) {
                deauthActive = false
                btnDeauth.text = "Iniciar Deauth"
                btnDeauth.setBackgroundColor(getColor(R.color.redteam_accent))
                NetworkAttackManager.sendRealAttack(this, "STOP_ATTACK", "ALL")
                logActivity("⏹️ Deauth detenido")
            } else {
                deauthActive = true
                btnDeauth.text = "Detener Deauth"
                btnDeauth.setBackgroundColor(getColor(R.color.warning))

                logActivity("🚨 Lanzando Deauth a $macFinal")

                // Acción Real
                NetworkAttackManager.sendRealAttack(this, "REAL_DEAUTH", macFinal)

                // Acción Simulada + Registro
                enviarSimulacionGenerica("Deauth WiFi", "Ataque de desconexión")
                registrarEvento("Deauth Lanzado", "Target: $macFinal")
            }
        }

        // 2. EVIL TWIN
        try {
            btnCaptivePortal.setOnClickListener {
                val ssidName = etSsidName.text.toString().trim()
                val nameFinal = if (ssidName.isEmpty()) "WiFi_Gratis_Seguro" else ssidName

                if (captivePortalActive) {
                    captivePortalActive = false
                    btnCaptivePortal.text = "Activar Portal"
                    btnCaptivePortal.setBackgroundColor(getColor(R.color.redteam_accent))
                    NetworkAttackManager.sendRealAttack(this, "STOP_PORTAL", "ALL")
                    logActivity("⏹️ Portal apagado")
                } else {
                    captivePortalActive = true
                    btnCaptivePortal.text = "Detener Portal"
                    btnCaptivePortal.setBackgroundColor(getColor(R.color.warning))

                    logActivity("🎣 Evil Twin: $nameFinal")

                    // Acción Real
                    NetworkAttackManager.sendRealAttack(this, "START_EVIL_TWIN", nameFinal)
                    NetworkAttackManager.publishLog("Evil Twin iniciado: $nameFinal")

                    // Acción Simulada + Registro
                    enviarSimulacionGenerica("Rogue AP Detectado", "Red sospechosa: $nameFinal")
                    registrarEvento("Evil Twin Activado", "SSID: $nameFinal")
                }
            }
        } catch (e: Exception) {}

        // 3. PACKET FLOOD
        btnPacketFlood.setOnClickListener {
            val targetIp = etTargetIp.text.toString().trim()
            val ipFinal = if (targetIp.isEmpty()) "192.168.1.1" else targetIp

            if (packetFloodActive) {
                packetFloodActive = false
                btnPacketFlood.text = "Iniciar Flood"
                btnPacketFlood.setBackgroundColor(getColor(R.color.redteam_accent))
                NetworkAttackManager.sendRealAttack(this, "STOP_ATTACK", "ALL")
                logActivity("⏹️ Flood detenido")
            } else {
                packetFloodActive = true
                btnPacketFlood.text = "Detener Flood"
                btnPacketFlood.setBackgroundColor(getColor(R.color.warning))

                logActivity("🌊 Flood UDP a $ipFinal")

                // Acción Real
                NetworkAttackManager.sendRealAttack(this, "REAL_FLOOD", ipFinal)
                NetworkAttackManager.sendRealAttack(this, "FLOOD", "SIM_TRIGGER")

                // Acción Simulada + Registro
                enviarSimulacionGenerica("Packet Flood", "Saturación UDP")
                registrarEvento("Packet Flood", "Target: $ipFinal")
            }
        }

        btnScanDevices.setOnClickListener {
            logActivity("🔍 Escaneando...")
            handler.postDelayed({ logActivity("✅ Escaneo finalizado") }, 1000)
        }
    }

    // --- REGISTRO DE EVENTOS Y CONTADORES ---

    private fun registrarEvento(titulo: String, desc: String) {
        // 1. Aumentar contador
        attacksSentCount++
        saveData()
        updateCountersUI()

        // 2. Guardar en Historial (EventLogActivity)
        try {
            EventLogActivity.addEvent(
                SecurityEvent(
                    type = EventType.ATTACK_SENT,
                    title = titulo,
                    description = desc,
                    sourceIp = "Localhost",
                    status = EventStatus.SUCCESS
                )
            )
        } catch (e: Exception) { }
    }

    private fun enviarSimulacionGenerica(titulo: String, desc: String) {
        var comandoMqtt = "GENERIC_ATTACK"
        if (titulo.contains("Flood")) comandoMqtt = "FLOOD"
        if (titulo.contains("Deauth")) comandoMqtt = "DEAUTH"
        if (titulo.contains("Rogue") || titulo.contains("Twin")) comandoMqtt = "EVIL_TWIN"

        NetworkAttackManager.sendRealAttack(this, comandoMqtt, "BROADCAST_ALERT")

        val myId = FirebaseAuth.getInstance().currentUser?.uid
        NetworkAttackManager.getOnlineDevices { devices ->
            val target = devices.firstOrNull { it["userId"] != myId }
            if (target != null) {
                NetworkAttackManager.sendAttack(target["userId"] as String, EventType.ATTACK_SENT, titulo, desc, {}, {})
            }
        }
    }

    private fun loadOnlineDevicesForSimulation() {
        val currentUser = FirebaseAuth.getInstance().currentUser?.uid
        NetworkAttackManager.getOnlineDevices { devices ->
            val others = devices.filter { it["userId"] != currentUser }
            if (others.isEmpty()) Toast.makeText(this, "No hay víctimas", Toast.LENGTH_SHORT).show()
            else showDeviceSelectionDialog(others)
        }
    }

    private fun showDeviceSelectionDialog(devices: List<Map<String, Any>>) {
        val names = devices.map { "Usuario ...${(it["userId"] as String).takeLast(5)}" }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Elegir Objetivo").setItems(names) { _, w ->
            showSimulationAttackTypeDialog(devices[w]["userId"] as String)
        }.show()
    }

    private fun showSimulationAttackTypeDialog(targetId: String) {
        val options = arrayOf("Phishing", "DDoS", "Ransomware")
        AlertDialog.Builder(this).setTitle("Payload").setItems(options) { _, w ->
            val type = options[w]
            NetworkAttackManager.sendAttack(targetId, EventType.ATTACK_SENT, type, "Simulación",
                { logActivity("✅ Simulación enviada") }, { logActivity("❌ Error") }
            )
            NetworkAttackManager.sendRealAttack(this, type.toUpperCase(), "SIMULATED")

            // Registrar también este ataque específico
            registrarEvento(type, "Simulación dirigida")

        }.show()
    }

    private fun isInternetAvailable(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun loadNetworkInfo() {
        try {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ip = Formatter.formatIpAddress(wm.connectionInfo.ipAddress)
            tvLocalIp.text = if (ip != "0.0.0.0") ip else "..."

            // Mostrar SSID solo si hay permiso/GPS, si no, mensaje genérico
            var ssid = wm.connectionInfo.ssid
            if (ssid != null && ssid.startsWith("\"")) ssid = ssid.substring(1, ssid.length - 1)
            val nombreWifi = if (ssid == "<unknown ssid>") "WiFi (Sin GPS)" else ssid

            if (isInternetAvailable()) {
                tvWifiStatus.text = "🟢 Online"
                tvWifiStatus.setTextColor(getColor(android.R.color.holo_green_dark))
                tvWifiName.text = nombreWifi
            } else {
                tvWifiStatus.text = "🔴 Offline"
                tvWifiStatus.setTextColor(getColor(android.R.color.holo_red_dark))
                tvWifiName.text = "Sin Red"
            }
        } catch (e: Exception) { tvLocalIp.text = "-" }
    }

    private fun logActivity(msg: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        handler.post { tvActivityLog.append("[$time] $msg\n") }
        NetworkAttackManager.publishLog(msg)
    }

    private fun initViews() {
        btnOpenWifi = findViewById(R.id.btnOpenWifi); tvWifiStatus = findViewById(R.id.tvWifiStatus); tvWifiName = findViewById(R.id.tvWifiName); tvLocalIp = findViewById(R.id.tvLocalIp); tvEsp32Status = findViewById(R.id.tvEsp32Status); etEsp32Ip = findViewById(R.id.etEsp32Ip); btnScanDevices = findViewById(R.id.btnScanDevices); fabSendRemote = findViewById(R.id.fabSendRemote); etTargetMac = findViewById(R.id.etTargetMac); btnDeauth = findViewById(R.id.btnDeauth); etTargetIp = findViewById(R.id.etTargetIp); btnPacketFlood = findViewById(R.id.btnPacketFlood); tvPacketsSent = findViewById(R.id.tvPacketsSent); tvActivityLog = findViewById(R.id.tvActivityLog); tvActivityLog.movementMethod = android.text.method.ScrollingMovementMethod.getInstance()
        try { etSsidName = findViewById(R.id.etSsidName); btnCaptivePortal = findViewById(R.id.btnCaptivePortal); tvCapturedData = findViewById(R.id.tvCapturedData) } catch (e: Exception) {}
    }

    private fun setupToolbar() { val t = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar); t.setNavigationOnClickListener { finish() } }
}
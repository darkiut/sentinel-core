package com.tecsup.sentinelcore

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.Formatter
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BlueTeamActivity : AppCompatActivity() {

    // Vistas
    private lateinit var btnOpenWifi: MaterialButton
    private lateinit var tvWifiStatus: TextView
    private lateinit var tvWifiName: TextView
    private lateinit var tvLocalIp: TextView
    private lateinit var tvEsp32Status: TextView
    private lateinit var etEsp32Ip: TextInputEditText
    private lateinit var btnConnectEsp32: MaterialButton
    private lateinit var btnToggleDefense: MaterialButton

    private lateinit var switchFirewall: SwitchMaterial
    private lateinit var tvFirewallStatus: TextView

    private lateinit var switchIDS: SwitchMaterial
    private lateinit var tvIDSStatus: TextView

    private lateinit var switchAntiDeauth: SwitchMaterial
    private lateinit var tvAntiDeauthStatus: TextView

    private lateinit var tvActivityLog: TextView

    // Variables de estado
    private var esp32Connected = false
    private var esp32Socket: Socket? = null
    private var firewallActive = false
    private var idsActive = false
    private var antiDeauthActive = false

    private val handler = Handler(Looper.getMainLooper())

    // NUEVO: SharedPreferences para guardar estado
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_blue_team)

        // NUEVO: Inicializar SharedPreferences
        prefs = getSharedPreferences("BlueTeamPrefs", Context.MODE_PRIVATE)

        initViews()
        setupToolbar()
        checkPermissions()
        loadNetworkInfo()

        // NUEVO: Restaurar estado de defensas ANTES de setupListeners
        restoreDefenseState()

        setupListeners()

        // Escuchar ataques entrantes
        listenForIncomingAttacks()
    }

    private fun initViews() {
        btnOpenWifi = findViewById(R.id.btnOpenWifi)
        tvWifiStatus = findViewById(R.id.tvWifiStatus)
        tvWifiName = findViewById(R.id.tvWifiName)
        tvLocalIp = findViewById(R.id.tvLocalIp)
        tvEsp32Status = findViewById(R.id.tvEsp32Status)
        etEsp32Ip = findViewById(R.id.etEsp32Ip)
        btnConnectEsp32 = findViewById(R.id.btnConnectEsp32)
        btnToggleDefense = findViewById(R.id.btnToggleDefense)

        switchFirewall = findViewById(R.id.switchFirewall)
        tvFirewallStatus = findViewById(R.id.tvFirewallStatus)

        switchIDS = findViewById(R.id.switchIDS)
        tvIDSStatus = findViewById(R.id.tvIDSStatus)

        switchAntiDeauth = findViewById(R.id.switchAntiDeauth)
        tvAntiDeauthStatus = findViewById(R.id.tvAntiDeauthStatus)

        tvActivityLog = findViewById(R.id.tvActivityLog)

        // Habilitar scroll en el TextView
        tvActivityLog.movementMethod = android.text.method.ScrollingMovementMethod.getInstance()

        // Permitir scroll independiente sin mover toda la pantalla
        tvActivityLog.setOnTouchListener { v, event ->
            v.parent.requestDisallowInterceptTouchEvent(true)
            when (event.action and android.view.MotionEvent.ACTION_MASK) {
                android.view.MotionEvent.ACTION_UP -> {
                    v.parent.requestDisallowInterceptTouchEvent(false)
                }
            }
            false
        }
    }

    private fun setupToolbar() {
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun checkPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val permissions = arrayOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            )

            val permissionsNeeded = permissions.filter {
                checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED
            }

            if (permissionsNeeded.isNotEmpty()) {
                requestPermissions(permissionsNeeded.toTypedArray(), 1001)
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001) {
            if (grantResults.isNotEmpty() &&
                grantResults.all { it == android.content.pm.PackageManager.PERMISSION_GRANTED }) {
                loadNetworkInfo()
                Toast.makeText(this, "Permisos concedidos", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Permisos necesarios para funcionar", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loadNetworkInfo() {
        try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

            val wifiInfo = wifiManager.connectionInfo
            val ssid = wifiInfo.ssid.replace("\"", "")

            // Actualizar estado WiFi y nombre por separado
            if (ssid.isNotEmpty() && ssid != "<unknown ssid>") {
                tvWifiStatus.text = "🟢 Conectado"
                tvWifiStatus.setTextColor(getColor(R.color.success))
                tvWifiName.text = "($ssid)"
                logActivity("Red WiFi conectada: $ssid")
            } else {
                tvWifiStatus.text = "❌ No conectado"
                tvWifiStatus.setTextColor(getColor(R.color.redteam))
                tvWifiName.text = ""
                logActivity("Sin conexión WiFi")
            }

            val ipAddress = wifiInfo.ipAddress
            val formattedIp = Formatter.formatIpAddress(ipAddress)
            tvLocalIp.text = formattedIp

        } catch (e: Exception) {
            tvWifiStatus.text = "❌ Error"
            tvWifiStatus.setTextColor(getColor(R.color.redteam))
            tvWifiName.text = ""
            logActivity("Error al cargar info de red: ${e.message}")
        }
    }

    // ========== NUEVO: GUARDAR Y RESTAURAR ESTADO ==========
    private fun restoreDefenseState() {
        // Restaurar estado de las defensas desde SharedPreferences
        firewallActive = prefs.getBoolean("firewall_active", false)
        idsActive = prefs.getBoolean("ids_active", false)
        antiDeauthActive = prefs.getBoolean("anti_deauth_active", false)

        // Aplicar el estado a los switches (sin disparar los listeners todavía)
        switchFirewall.isChecked = firewallActive
        switchIDS.isChecked = idsActive
        switchAntiDeauth.isChecked = antiDeauthActive

        // Actualizar los TextViews de estado manualmente
        if (firewallActive) {
            tvFirewallStatus.text = "✅ Activo"
            tvFirewallStatus.setTextColor(getColor(R.color.blueteam))
        }

        if (idsActive) {
            tvIDSStatus.text = "✅ Activo"
            tvIDSStatus.setTextColor(getColor(R.color.blueteam))
        }

        if (antiDeauthActive) {
            tvAntiDeauthStatus.text = "✅ Activo"
            tvAntiDeauthStatus.setTextColor(getColor(R.color.blueteam))
        }

        // Actualizar el botón de defensa general
        updateDefenseButton()

        logActivity("🔄 Estado de defensas restaurado")
    }

    private fun saveDefenseState() {
        // Guardar el estado actual en SharedPreferences
        prefs.edit().apply {
            putBoolean("firewall_active", firewallActive)
            putBoolean("ids_active", idsActive)
            putBoolean("anti_deauth_active", antiDeauthActive)
            apply()
        }
    }

    private fun updateDefenseButton() {
        if (firewallActive || idsActive || antiDeauthActive) {
            btnToggleDefense.text = "Desactivar Defensa"
            btnToggleDefense.setBackgroundColor(getColor(R.color.warning))
        } else {
            btnToggleDefense.text = "Activar Defensa"
            btnToggleDefense.setBackgroundColor(getColor(R.color.blue_team_dark))
        }
    }

    private fun setupListeners() {
        // Botón para abrir configuración WiFi
        btnOpenWifi.setOnClickListener {
            val intent = Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)
            startActivity(intent)
        }

        // Botón para activar/desactivar todas las defensas
        btnToggleDefense.setOnClickListener {
            if (firewallActive || idsActive || antiDeauthActive) {
                deactivateAllDefenses()
            } else {
                activateAllDefenses()
            }
        }

        // Conectar con ESP32
        btnConnectEsp32.setOnClickListener {
            val esp32Ip = etEsp32Ip.text.toString().trim()
            if (esp32Ip.isEmpty()) {
                Toast.makeText(this, "Ingresa la IP del ESP32", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (esp32Connected) {
                disconnectEsp32()
            } else {
                connectToEsp32(esp32Ip)
            }
        }

        // Firewall
        switchFirewall.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                activateFirewall()
            } else {
                deactivateFirewall()
            }
        }

        // IDS
        switchIDS.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                activateIDS()
            } else {
                deactivateIDS()
            }
        }

        // Anti-Deauth
        switchAntiDeauth.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                activateAntiDeauth()
            } else {
                deactivateAntiDeauth()
            }
        }
    }

    // ========== CONEXIÓN ESP32 ==========
    private fun connectToEsp32(ip: String) {
        logActivity("Conectando a ESP32: $ip")
        btnConnectEsp32.isEnabled = false

        Thread {
            try {
                esp32Socket = Socket(ip, 80)
                esp32Connected = true

                runOnUiThread {
                    tvEsp32Status.text = "🟢 Conectado"
                    btnConnectEsp32.text = "Desconectar"
                    btnConnectEsp32.isEnabled = true
                    logActivity("✅ Conectado al ESP32 exitosamente")
                    Toast.makeText(this, "Conectado al ESP32", Toast.LENGTH_SHORT).show()

                    startEsp32Listener()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    tvEsp32Status.text = "🔴 Error de conexión"
                    btnConnectEsp32.isEnabled = true
                    logActivity("❌ Error al conectar: ${e.message}")
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun disconnectEsp32() {
        try {
            esp32Socket?.close()
            esp32Connected = false
            tvEsp32Status.text = "⚪ Desconectado"
            btnConnectEsp32.text = "Conectar"
            logActivity("Desconectado del ESP32")
        } catch (e: Exception) {
            logActivity("Error al desconectar: ${e.message}")
        }
    }

    private fun startEsp32Listener() {
        Thread {
            try {
                val reader = BufferedReader(InputStreamReader(esp32Socket?.getInputStream()))
                while (esp32Connected) {
                    val line = reader.readLine() ?: break
                    runOnUiThread {
                        processEsp32Message(line)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    if (esp32Connected) {
                        logActivity("Conexión perdida con ESP32")
                        disconnectEsp32()
                    }
                }
            }
        }.start()
    }

    private fun sendCommandToEsp32(command: String) {
        if (!esp32Connected) return

        Thread {
            try {
                val writer = PrintWriter(esp32Socket?.getOutputStream(), true)
                writer.println(command)
                runOnUiThread {
                    logActivity("Comando enviado: $command")
                }
            } catch (e: Exception) {
                runOnUiThread {
                    logActivity("Error al enviar comando: ${e.message}")
                }
            }
        }.start()
    }

    private fun processEsp32Message(message: String) {
        logActivity("ESP32: $message")

        when {
            message.contains("ATTACK_DETECTED") -> {
                logActivity("⚠️ Ataque detectado!")

                EventLogActivity.addEvent(
                    SecurityEvent(
                        type = EventType.ATTACK_DETECTED,
                        title = "Ataque Detectado",
                        description = "El sistema detectó actividad sospechosa",
                        sourceIp = "Desconocido",
                        status = EventStatus.DETECTED
                    )
                )
            }
            message.contains("FIREWALL_BLOCK") -> {
                logActivity("🛡️ Firewall bloqueó tráfico")

                EventLogActivity.addEvent(
                    SecurityEvent(
                        type = EventType.FIREWALL_BLOCK,
                        title = "Firewall Bloqueó Tráfico",
                        description = "Paquetes sospechosos bloqueados",
                        sourceIp = "Atacante",
                        status = EventStatus.BLOCKED
                    )
                )
            }
        }
    }

    // ========== DEFENSAS ==========
    private fun activateFirewall() {
        firewallActive = true
        tvFirewallStatus.text = "✅ Activo"
        tvFirewallStatus.setTextColor(getColor(R.color.blueteam))

        logActivity("🛡️ Firewall activado")
        sendCommandToEsp32("ACTIVATE_FIREWALL")

        // NUEVO: Guardar estado
        saveDefenseState()
        updateDefenseButton()

        EventLogActivity.addEvent(
            SecurityEvent(
                type = EventType.DEFENSE_ACTIVATED,
                title = "Firewall Activado",
                description = "Sistema de firewall está protegiendo la red",
                sourceIp = tvLocalIp.text.toString(),
                status = EventStatus.SUCCESS
            )
        )

        Toast.makeText(this, "Firewall activado", Toast.LENGTH_SHORT).show()
    }

    private fun deactivateFirewall() {
        firewallActive = false
        tvFirewallStatus.text = "❌ Inactivo"
        tvFirewallStatus.setTextColor(getColor(R.color.gray))

        logActivity("⏹️ Firewall desactivado")
        sendCommandToEsp32("DEACTIVATE_FIREWALL")

        // NUEVO: Guardar estado
        saveDefenseState()
        updateDefenseButton()
    }

    private fun activateIDS() {
        idsActive = true
        tvIDSStatus.text = "✅ Activo"
        tvIDSStatus.setTextColor(getColor(R.color.blueteam))

        logActivity("🔍 IDS/IPS activado")
        sendCommandToEsp32("ACTIVATE_IDS")

        // NUEVO: Guardar estado
        saveDefenseState()
        updateDefenseButton()

        EventLogActivity.addEvent(
            SecurityEvent(
                type = EventType.DEFENSE_ACTIVATED,
                title = "IDS/IPS Activado",
                description = "Sistema de detección de intrusiones en línea",
                sourceIp = tvLocalIp.text.toString(),
                status = EventStatus.SUCCESS
            )
        )

        Toast.makeText(this, "IDS/IPS activado", Toast.LENGTH_SHORT).show()
    }

    private fun deactivateIDS() {
        idsActive = false
        tvIDSStatus.text = "❌ Inactivo"
        tvIDSStatus.setTextColor(getColor(R.color.gray))

        logActivity("⏹️ IDS/IPS desactivado")
        sendCommandToEsp32("DEACTIVATE_IDS")

        // NUEVO: Guardar estado
        saveDefenseState()
        updateDefenseButton()
    }

    private fun activateAntiDeauth() {
        antiDeauthActive = true
        tvAntiDeauthStatus.text = "✅ Activo"
        tvAntiDeauthStatus.setTextColor(getColor(R.color.blueteam))

        logActivity("🚫 Anti-Deauth activado")
        sendCommandToEsp32("ACTIVATE_ANTI_DEAUTH")

        // NUEVO: Guardar estado
        saveDefenseState()
        updateDefenseButton()

        EventLogActivity.addEvent(
            SecurityEvent(
                type = EventType.DEFENSE_ACTIVATED,
                title = "Anti-Deauth Activado",
                description = "Protección contra ataques de desautenticación WiFi",
                sourceIp = tvLocalIp.text.toString(),
                status = EventStatus.SUCCESS
            )
        )

        Toast.makeText(this, "Anti-Deauth activado", Toast.LENGTH_SHORT).show()
    }

    private fun deactivateAntiDeauth() {
        antiDeauthActive = false
        tvAntiDeauthStatus.text = "❌ Inactivo"
        tvAntiDeauthStatus.setTextColor(getColor(R.color.gray))

        logActivity("⏹️ Anti-Deauth desactivado")
        sendCommandToEsp32("DEACTIVATE_ANTI_DEAUTH")

        // NUEVO: Guardar estado
        saveDefenseState()
        updateDefenseButton()
    }

    // ========== ACTIVAR/DESACTIVAR TODAS LAS DEFENSAS ==========
    private fun activateAllDefenses() {
        // Activar todos los switches
        switchFirewall.isChecked = true
        switchIDS.isChecked = true
        switchAntiDeauth.isChecked = true

        logActivity("🛡️ Sistema de defensa COMPLETO activado")
        Toast.makeText(this, "✅ Todas las defensas activadas", Toast.LENGTH_SHORT).show()
    }

    private fun deactivateAllDefenses() {
        // Desactivar todos los switches
        switchFirewall.isChecked = false
        switchIDS.isChecked = false
        switchAntiDeauth.isChecked = false

        logActivity("⏹️ Sistema de defensa desactivado")
        Toast.makeText(this, "❌ Todas las defensas desactivadas", Toast.LENGTH_SHORT).show()
    }

    // ========== ESCUCHAR ATAQUES REMOTOS ==========
    private fun listenForIncomingAttacks() {
        NetworkAttackManager.listenForIncomingAttacks { attackData ->
            val attackTitle = attackData["title"] as? String ?: "Ataque desconocido"
            val attackDescription = attackData["description"] as? String ?: ""
            val attackerId = attackData["attackerId"] as? String ?: "Desconocido"

            logActivity("🚨 ATAQUE RECIBIDO: $attackTitle")
            logActivity("Atacante: $attackerId")
            logActivity("Descripción: $attackDescription")

            // Registrar el ataque en el log
            EventLogActivity.addEvent(
                SecurityEvent(
                    type = EventType.ATTACK_BLOCKED,
                    title = "Ataque Bloqueado",
                    description = "$attackTitle de $attackerId",
                    sourceIp = attackerId,
                    status = EventStatus.BLOCKED
                )
            )

            // Mostrar notificación al usuario
            Toast.makeText(this, "⚠️ Ataque detectado: $attackTitle", Toast.LENGTH_LONG).show()
        }
    }

    // ========== UTILIDADES ==========
    private fun logActivity(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val logMessage = "[$timestamp] $message\n"
        handler.post {
            tvActivityLog.append(logMessage)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        disconnectEsp32()
    }

    override fun onResume() {
        super.onResume()
        loadNetworkInfo()
    }
}

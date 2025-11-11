package com.tecsup.sentinelcore

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.Formatter
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RedTeamActivity : AppCompatActivity() {

    // Vistas
    private lateinit var tvWifiName: TextView
    private lateinit var tvLocalIp: TextView
    private lateinit var tvEsp32Status: TextView
    private lateinit var etEsp32Ip: TextInputEditText
    private lateinit var btnConnectEsp32: MaterialButton

    private lateinit var etTargetMac: TextInputEditText
    private lateinit var btnDeauth: MaterialButton

    private lateinit var etSsidName: TextInputEditText
    private lateinit var btnCaptivePortal: MaterialButton
    private lateinit var tvCapturedData: TextView

    private lateinit var etTargetIp: TextInputEditText
    private lateinit var btnPacketFlood: MaterialButton
    private lateinit var tvPacketsSent: TextView

    private lateinit var tvActivityLog: TextView

    // Variables de estado
    private var esp32Connected = false
    private var esp32Socket: Socket? = null
    private var deauthActive = false
    private var captivePortalActive = false
    private var packetFloodActive = false

    private var packetCount = 0
    private var capturedDataCount = 0

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_red_team)

        initViews()
        setupToolbar()
        checkPermissions()
        loadNetworkInfo()
        setupListeners()
    }

    private fun initViews() {
        tvWifiName = findViewById(R.id.tvWifiName)
        tvLocalIp = findViewById(R.id.tvLocalIp)
        tvEsp32Status = findViewById(R.id.tvEsp32Status)
        etEsp32Ip = findViewById(R.id.etEsp32Ip)
        btnConnectEsp32 = findViewById(R.id.btnConnectEsp32)

        etTargetMac = findViewById(R.id.etTargetMac)
        btnDeauth = findViewById(R.id.btnDeauth)

        etSsidName = findViewById(R.id.etSsidName)
        btnCaptivePortal = findViewById(R.id.btnCaptivePortal)
        tvCapturedData = findViewById(R.id.tvCapturedData)

        etTargetIp = findViewById(R.id.etTargetIp)
        btnPacketFlood = findViewById(R.id.btnPacketFlood)
        tvPacketsSent = findViewById(R.id.tvPacketsSent)

        tvActivityLog = findViewById(R.id.tvActivityLog)
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

            // Obtener nombre del WiFi
            val wifiInfo = wifiManager.connectionInfo
            val ssid = wifiInfo.ssid.replace("\"", "")
            tvWifiName.text = if (ssid.isNotEmpty() && ssid != "<unknown ssid>") ssid else "No conectado"

            // Obtener IP local
            val ipAddress = wifiInfo.ipAddress
            val formattedIp = Formatter.formatIpAddress(ipAddress)
            tvLocalIp.text = formattedIp

            logActivity("Red detectada: $ssid ($formattedIp)")
        } catch (e: Exception) {
            logActivity("Error al cargar info de red: ${e.message}")
        }
    }

    private fun setupListeners() {
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

        // Ataque Deauth
        btnDeauth.setOnClickListener {
            if (!esp32Connected) {
                Toast.makeText(this, "Conecta primero el ESP32", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (deauthActive) {
                stopDeauthAttack()
            } else {
                val targetMac = etTargetMac.text.toString().trim()
                startDeauthAttack(targetMac)
            }
        }

        // Captive Portal
        btnCaptivePortal.setOnClickListener {
            if (!esp32Connected) {
                Toast.makeText(this, "Conecta primero el ESP32", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (captivePortalActive) {
                stopCaptivePortal()
            } else {
                val ssidName = etSsidName.text.toString().trim()
                startCaptivePortal(ssidName)
            }
        }

        // Packet Flood
        btnPacketFlood.setOnClickListener {
            if (!esp32Connected) {
                Toast.makeText(this, "Conecta primero el ESP32", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (packetFloodActive) {
                stopPacketFlood()
            } else {
                val targetIp = etTargetIp.text.toString().trim()
                startPacketFlood(targetIp)
            }
        }
    }

    // ========== CONEXIÓN ESP32 ==========
    private fun connectToEsp32(ip: String) {
        logActivity("Conectando a ESP32: $ip")
        btnConnectEsp32.isEnabled = false

        Thread {
            try {
                // Intentar conectar al ESP32 en el puerto 80
                esp32Socket = Socket(ip, 80)
                esp32Connected = true

                runOnUiThread {
                    tvEsp32Status.text = "🟢 Conectado"
                    btnConnectEsp32.text = "Desconectar"
                    btnConnectEsp32.isEnabled = true
                    logActivity("✅ Conectado al ESP32 exitosamente")
                    Toast.makeText(this, "Conectado al ESP32", Toast.LENGTH_SHORT).show()

                    // Iniciar listener para recibir datos del ESP32
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
            message.contains("DEAUTH_SUCCESS") -> {
                logActivity("✅ Ataque Deauth ejecutado")
            }
            message.contains("CAPTIVE_PORTAL_ACTIVE") -> {
                logActivity("✅ Captive Portal activo")
            }
            message.contains("CREDENTIALS_CAPTURED") -> {
                capturedDataCount++
                tvCapturedData.text = "Datos capturados: $capturedDataCount"
                logActivity("🎯 Credenciales capturadas!")

                val data = message.substringAfter("DATA:", "")
                if (data.isNotEmpty()) {
                    logActivity("📋 $data")

                    // Registrar evento de credenciales capturadas
                    EventLogActivity.addEvent(
                        SecurityEvent(
                            type = EventType.ATTACK_SENT,
                            title = "Credenciales Capturadas",
                            description = "Captive Portal capturó: $data",
                            sourceIp = "Víctima",
                            status = EventStatus.SUCCESS
                        )
                    )
                }
            }
            message.contains("PACKET_SENT") -> {
                packetCount++
                tvPacketsSent.text = "Paquetes enviados: $packetCount"
            }
        }
    }

    // ========== ATAQUES ==========
    private fun startDeauthAttack(targetMac: String) {
        if (targetMac.isEmpty()) {
            Toast.makeText(this, "Ingresa una MAC objetivo", Toast.LENGTH_SHORT).show()
            return
        }

        deauthActive = true
        btnDeauth.text = "Detener Deauth"
        btnDeauth.setBackgroundColor(getColor(R.color.warning))

        logActivity("🚨 Iniciando Deauth Attack")
        logActivity("Objetivo: $targetMac")

        sendCommandToEsp32("DEAUTH:$targetMac")

        // Registrar evento
        EventLogActivity.addEvent(
            SecurityEvent(
                type = EventType.ATTACK_SENT,
                title = "Ataque Deauth Enviado",
                description = "Paquetes de desautenticación enviados a $targetMac",
                sourceIp = tvLocalIp.text.toString(),
                status = EventStatus.SUCCESS
            )
        )

        Toast.makeText(this, "Ataque Deauth iniciado", Toast.LENGTH_SHORT).show()
    }

    private fun stopDeauthAttack() {
        deauthActive = false
        btnDeauth.text = "Iniciar Ataque Deauth"
        btnDeauth.setBackgroundColor(getColor(R.color.red_team_accent))

        sendCommandToEsp32("STOP_DEAUTH")
        logActivity("⏹️ Ataque Deauth detenido")
    }

    private fun startCaptivePortal(ssidName: String) {
        if (ssidName.isEmpty()) {
            Toast.makeText(this, "Ingresa un nombre de SSID", Toast.LENGTH_SHORT).show()
            return
        }

        captivePortalActive = true
        btnCaptivePortal.text = "Detener Portal"
        btnCaptivePortal.setBackgroundColor(getColor(R.color.warning))

        logActivity("🚨 Iniciando Captive Portal")
        logActivity("SSID falso: $ssidName")

        sendCommandToEsp32("CAPTIVE_PORTAL:$ssidName")

        // Registrar evento
        EventLogActivity.addEvent(
            SecurityEvent(
                type = EventType.ATTACK_SENT,
                title = "Captive Portal Activado",
                description = "Portal falso '$ssidName' creado para capturar credenciales",
                sourceIp = tvLocalIp.text.toString(),
                status = EventStatus.SUCCESS
            )
        )

        Toast.makeText(this, "Captive Portal iniciado", Toast.LENGTH_SHORT).show()
    }

    private fun stopCaptivePortal() {
        captivePortalActive = false
        btnCaptivePortal.text = "Activar Captive Portal"
        btnCaptivePortal.setBackgroundColor(getColor(R.color.red_team_accent))

        sendCommandToEsp32("STOP_CAPTIVE")
        logActivity("⏹️ Captive Portal detenido")
    }

    private fun startPacketFlood(targetIp: String) {
        if (targetIp.isEmpty()) {
            Toast.makeText(this, "Ingresa una IP objetivo", Toast.LENGTH_SHORT).show()
            return
        }

        packetFloodActive = true
        btnPacketFlood.text = "Detener Flood"
        btnPacketFlood.setBackgroundColor(getColor(R.color.warning))
        packetCount = 0

        logActivity("🚨 Iniciando Packet Flood")
        logActivity("Objetivo: $targetIp")

        sendCommandToEsp32("FLOOD:$targetIp")

        // Registrar evento
        EventLogActivity.addEvent(
            SecurityEvent(
                type = EventType.ATTACK_SENT,
                title = "Packet Flood Iniciado",
                description = "Enviando flood de paquetes a $targetIp",
                sourceIp = tvLocalIp.text.toString(),
                status = EventStatus.SUCCESS
            )
        )

        Toast.makeText(this, "Packet Flood iniciado", Toast.LENGTH_SHORT).show()
    }

    private fun stopPacketFlood() {
        packetFloodActive = false
        btnPacketFlood.text = "Iniciar Flood"
        btnPacketFlood.setBackgroundColor(getColor(R.color.red_team_accent))

        sendCommandToEsp32("STOP_FLOOD")
        logActivity("⏹️ Packet Flood detenido. Total: $packetCount paquetes")
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
}
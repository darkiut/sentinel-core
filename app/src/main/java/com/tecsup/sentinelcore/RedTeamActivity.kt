package com.tecsup.sentinelcore

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.Formatter
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
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
    private lateinit var btnOpenWifi: MaterialButton
    private lateinit var tvWifiStatus: TextView  // NUEVO: indicador de estado WiFi
    private lateinit var tvWifiName: TextView
    private lateinit var tvLocalIp: TextView
    private lateinit var tvEsp32Status: TextView
    private lateinit var etEsp32Ip: TextInputEditText
    private lateinit var btnConnectEsp32: MaterialButton
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

        // Registrar dispositivo en red
        NetworkAttackManager.registerDevice { deviceId ->
            logActivity("Dispositivo registrado: $deviceId")
        }
    }

    private fun initViews() {
        btnOpenWifi = findViewById(R.id.btnOpenWifi)
        tvWifiStatus = findViewById(R.id.tvWifiStatus)
        tvWifiName = findViewById(R.id.tvWifiName)
        tvLocalIp = findViewById(R.id.tvLocalIp)
        tvEsp32Status = findViewById(R.id.tvEsp32Status)
        etEsp32Ip = findViewById(R.id.etEsp32Ip)
        btnConnectEsp32 = findViewById(R.id.btnConnectEsp32)
        btnScanDevices = findViewById(R.id.btnScanDevices)
        fabSendRemote = findViewById(R.id.fabSendRemote)

        etTargetMac = findViewById(R.id.etTargetMac)
        btnDeauth = findViewById(R.id.btnDeauth)

        etSsidName = findViewById(R.id.etSsidName)
        btnCaptivePortal = findViewById(R.id.btnCaptivePortal)
        tvCapturedData = findViewById(R.id.tvCapturedData)

        etTargetIp = findViewById(R.id.etTargetIp)
        btnPacketFlood = findViewById(R.id.btnPacketFlood)
        tvPacketsSent = findViewById(R.id.tvPacketsSent)

        tvActivityLog = findViewById(R.id.tvActivityLog)

        // SOLUCIÓN DEFINITIVA: Habilitar scroll independiente en el TextView
        tvActivityLog.movementMethod = android.text.method.ScrollingMovementMethod()
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

            // ACTUALIZACIÓN: Ahora se maneja el estado WiFi y el nombre por separado
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

    // ========== NUEVA FUNCIÓN: Verificar si hay conexión WiFi ==========
    private fun isWifiConnected(): Boolean {
        val status = tvWifiStatus.text.toString()
        return status.contains("Conectado")
    }

    private fun setupListeners() {
        // Botón para abrir configuración WiFi
        btnOpenWifi.setOnClickListener {
            val intent = Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)
            startActivity(intent)
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

        // Escanear dispositivos en red
        btnScanDevices.setOnClickListener {
            logActivity("🔍 Escaneando dispositivos en red...")
            scanLocalNetwork()
        }

        // Botón flotante para enviar a dispositivo remoto
        fabSendRemote.setOnClickListener {
            logActivity("🔍 Buscando dispositivos en red...")
            loadOnlineDevices()
        }

        // ========== ATAQUE DEAUTH (requiere ESP32) ==========
        btnDeauth.setOnClickListener {
            // Este ataque REQUIERE ESP32 porque es de capa física WiFi
            if (!esp32Connected) {
                Toast.makeText(this, "⚠️ Deauth requiere ESP32 conectado", Toast.LENGTH_LONG).show()
                logActivity("❌ Deauth Attack requiere ESP32")
                return@setOnClickListener
            }

            if (deauthActive) {
                stopDeauthAttack()
            } else {
                val targetMac = etTargetMac.text.toString().trim()
                startDeauthAttack(targetMac)
            }
        }

        // ========== CAPTIVE PORTAL (requiere ESP32) ==========
        btnCaptivePortal.setOnClickListener {
            // Este ataque REQUIERE ESP32 porque crea un AP falso
            if (!esp32Connected) {
                Toast.makeText(this, "⚠️ Captive Portal requiere ESP32 conectado", Toast.LENGTH_LONG).show()
                logActivity("❌ Captive Portal requiere ESP32")
                return@setOnClickListener
            }

            if (captivePortalActive) {
                stopCaptivePortal()
            } else {
                val ssidName = etSsidName.text.toString().trim()
                startCaptivePortal(ssidName)
            }
        }

        // ========== PACKET FLOOD (puede ser local O con ESP32) ==========
        btnPacketFlood.setOnClickListener {
            val targetIp = etTargetIp.text.toString().trim()

            // Validar primero que haya WiFi
            if (!isWifiConnected()) {
                Toast.makeText(this, "⚠️ Conéctate a una red WiFi primero", Toast.LENGTH_LONG).show()
                logActivity("❌ Sin conexión WiFi")
                return@setOnClickListener
            }

            if (targetIp.isEmpty()) {
                Toast.makeText(this, "Ingresa una IP objetivo", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (packetFloodActive) {
                stopPacketFlood()
            } else {
                // Si hay ESP32, úsalo; si no, ataque local
                if (esp32Connected) {
                    startPacketFlood(targetIp)  // Con ESP32
                } else {
                    startLocalPacketFlood(targetIp)  // Local desde el teléfono
                }
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

    // ========== ATAQUES CON ESP32 ==========
    private fun startDeauthAttack(targetMac: String) {
        if (targetMac.isEmpty()) {
            Toast.makeText(this, "Ingresa una MAC objetivo", Toast.LENGTH_SHORT).show()
            return
        }

        deauthActive = true
        btnDeauth.text = "Detener Deauth"
        btnDeauth.setBackgroundColor(getColor(R.color.warning))

        logActivity("🚨 Iniciando Deauth Attack (ESP32)")
        logActivity("Objetivo: $targetMac")

        sendCommandToEsp32("DEAUTH:$targetMac")

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
        btnDeauth.setBackgroundColor(getColor(R.color.redteam_accent))

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

        logActivity("🚨 Iniciando Captive Portal (ESP32)")
        logActivity("SSID falso: $ssidName")

        sendCommandToEsp32("CAPTIVE_PORTAL:$ssidName")

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
        btnCaptivePortal.setBackgroundColor(getColor(R.color.redteam_accent))

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

        logActivity("🚨 Iniciando Packet Flood (ESP32)")
        logActivity("Objetivo: $targetIp")

        sendCommandToEsp32("FLOOD:$targetIp")

        EventLogActivity.addEvent(
            SecurityEvent(
                type = EventType.ATTACK_SENT,
                title = "Packet Flood Iniciado",
                description = "Enviando flood de paquetes a $targetIp via ESP32",
                sourceIp = tvLocalIp.text.toString(),
                status = EventStatus.SUCCESS
            )
        )

        Toast.makeText(this, "Packet Flood iniciado (ESP32)", Toast.LENGTH_SHORT).show()
    }

    private fun stopPacketFlood() {
        packetFloodActive = false
        btnPacketFlood.text = "Iniciar Flood"
        btnPacketFlood.setBackgroundColor(getColor(R.color.redteam_accent))

        if (esp32Connected) {
            sendCommandToEsp32("STOP_FLOOD")
        }
        logActivity("⏹️ Packet Flood detenido. Total: $packetCount paquetes")
    }

    // ========== ATAQUE LOCAL (SIN ESP32) ==========
    private fun startLocalPacketFlood(targetIp: String) {
        packetFloodActive = true
        btnPacketFlood.text = "Detener Flood"
        btnPacketFlood.setBackgroundColor(getColor(R.color.warning))
        packetCount = 0

        logActivity("🚨 Iniciando Packet Flood LOCAL (desde el teléfono)")
        logActivity("Objetivo: $targetIp")
        logActivity("⚠️ Modo local: prueba educativa")

        Thread {
            try {
                for (i in 1..100) {
                    if (!packetFloodActive) break

                    val reachable = isReachable(targetIp, 50)

                    runOnUiThread {
                        packetCount++
                        tvPacketsSent.text = "Paquetes enviados: $packetCount"
                        if (i % 10 == 0) {
                            logActivity("📤 Paquetes enviados: $packetCount")
                        }
                    }

                    Thread.sleep(50)
                }

                runOnUiThread {
                    logActivity("✅ Flood local completado: $packetCount paquetes")
                    Toast.makeText(this, "Flood local finalizado", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    logActivity("❌ Error en flood local: ${e.message}")
                }
            }
        }.start()

        EventLogActivity.addEvent(
            SecurityEvent(
                type = EventType.ATTACK_SENT,
                title = "Packet Flood Local",
                description = "Flood ejecutado localmente hacia $targetIp",
                sourceIp = tvLocalIp.text.toString(),
                status = EventStatus.SUCCESS
            )
        )

        Toast.makeText(this, "⚠️ Flood local iniciado (modo prueba)", Toast.LENGTH_LONG).show()
    }

    // ========== ATAQUES DE RED FIREBASE ==========
    private fun loadOnlineDevices() {
        NetworkAttackManager.getOnlineDevices { devices ->
            if (devices.isEmpty()) {
                logActivity("No hay dispositivos en línea")
                Toast.makeText(this, "No hay dispositivos conectados", Toast.LENGTH_SHORT).show()
            } else {
                logActivity("Dispositivos en línea: ${devices.size}")
                showDeviceSelectionDialog(devices)
            }
        }
    }

    private fun showDeviceSelectionDialog(devices: List<Map<String, Any>>) {
        val deviceNames = devices.mapIndexed { index, device ->
            val userId = device["userId"] as? String ?: "Unknown"
            val status = device["status"] as? String ?: "offline"
            "$index: Usuario $userId ($status)"
        }.toTypedArray()

        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle("Seleccionar objetivo")
        builder.setItems(deviceNames) { _, which ->
            val targetDevice = devices[which]
            val targetUserId = targetDevice["userId"] as? String

            if (targetUserId != null) {
                showAttackTypeDialog(targetUserId)
            }
        }
        builder.setNegativeButton("Cancelar", null)
        builder.show()
    }

    private fun showAttackTypeDialog(targetUserId: String) {
        val attackTypes = arrayOf(
            "Deauth Attack",
            "Packet Flood",
            "Port Scan",
            "Phishing"
        )

        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle("Tipo de ataque")
        builder.setItems(attackTypes) { _, which ->
            when (which) {
                0 -> sendNetworkAttack(targetUserId, EventType.ATTACK_SENT, "Deauth Attack", "Ataque de desautenticación WiFi")
                1 -> sendNetworkAttack(targetUserId, EventType.ATTACK_SENT, "Packet Flood", "Inundación de paquetes")
                2 -> sendNetworkAttack(targetUserId, EventType.ATTACK_SENT, "Port Scan", "Escaneo de puertos")
                3 -> sendNetworkAttack(targetUserId, EventType.ATTACK_SENT, "Phishing", "Intento de phishing")
            }
        }
        builder.setNegativeButton("Cancelar", null)
        builder.show()
    }

    private fun sendNetworkAttack(
        targetUserId: String,
        attackType: EventType,
        title: String,
        description: String
    ) {
        logActivity("🚀 Enviando $title a $targetUserId")

        NetworkAttackManager.sendAttack(
            targetUserId = targetUserId,
            attackType = attackType,
            attackTitle = title,
            attackDescription = description,
            onSuccess = {
                runOnUiThread {
                    logActivity("✅ Ataque enviado exitosamente")
                    Toast.makeText(this, "Ataque enviado", Toast.LENGTH_SHORT).show()

                    EventLogActivity.addEvent(
                        SecurityEvent(
                            type = EventType.ATTACK_SENT,
                            title = title,
                            description = "Ataque enviado a dispositivo remoto",
                            sourceIp = tvLocalIp.text.toString(),
                            status = EventStatus.SUCCESS
                        )
                    )
                }
            },
            onError = { error ->
                runOnUiThread {
                    logActivity("❌ Error: $error")
                    Toast.makeText(this, "Error: $error", Toast.LENGTH_SHORT).show()
                }
            }
        )
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

    // ========== ESCANEO DE RED LOCAL ==========
    private fun getHostNameOrSimple(ip: String): String {
        val aliases = mapOf(
            "192.168.1.100" to "ESP32 Dormitorio",
            "192.168.1.10" to "Mi PC",
            "192.168.1.22" to "Mi Celular"
        )
        aliases[ip]?.let { return it }

        return try {
            val addr = java.net.InetAddress.getByName(ip)
            val host = addr.hostName
            if (host == ip) "Dispositivo ($ip)" else host
        } catch (e: Exception) {
            "Desconocido ($ip)"
        }
    }

    private fun scanLocalNetwork() {
        logActivity("🔍 Escaneando red local...")
        Toast.makeText(this, "Escaneando red WiFi...", Toast.LENGTH_SHORT).show()

        Thread {
            try {
                val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                val dhcpInfo = wifiManager.dhcpInfo

                val gateway = Formatter.formatIpAddress(dhcpInfo.gateway)
                val subnet = gateway.substringBeforeLast(".")

                val devicesFound = mutableListOf<String>()

                for (i in 1..254) {
                    val host = "$subnet.$i"
                    if (isReachable(host, 100)) {
                        devicesFound.add(host)
                        val friendlyName = getHostNameOrSimple(host)
                        runOnUiThread {
                            logActivity("✅ Dispositivo encontrado: $friendlyName")
                        }
                    }
                }

                runOnUiThread {
                    if (devicesFound.isEmpty()) {
                        logActivity("❌ No se encontraron dispositivos")
                        Toast.makeText(this, "No se encontraron dispositivos", Toast.LENGTH_SHORT).show()
                    } else {
                        logActivity("✅ Dispositivos encontrados: ${devicesFound.size}")
                        showLocalDevicesDialog(devicesFound)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    logActivity("❌ Error al escanear: ${e.message}")
                }
            }
        }.start()
    }

    private fun isReachable(host: String, timeout: Int): Boolean {
        return try {
            val address = java.net.InetAddress.getByName(host)
            address.isReachable(timeout)
        } catch (e: Exception) {
            false
        }
    }

    private fun showLocalDevicesDialog(devices: List<String>) {
        val names = devices.map { ip -> getHostNameOrSimple(ip) }.toTypedArray()

        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle("Dispositivos en Red WiFi (${devices.size})")
        builder.setItems(names) { _, which ->
            val selectedIp = devices[which]
            etEsp32Ip.setText(selectedIp)
            logActivity("📍 IP seleccionada: $selectedIp")
            Toast.makeText(this, "IP seleccionada: $selectedIp", Toast.LENGTH_SHORT).show()
        }
        builder.setNegativeButton("Cancelar", null)
        builder.show()
    }
}

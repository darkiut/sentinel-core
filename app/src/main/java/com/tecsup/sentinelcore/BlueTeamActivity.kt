package com.tecsup.sentinelcore

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.format.Formatter
import android.util.Log
import android.view.MotionEvent
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BlueTeamActivity : AppCompatActivity() {

    // --- VISTAS ---
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
    private lateinit var tvAttacksBlocked: TextView

    // --- VARIABLES DE ESTADO ---
    private var esp32Socket: Socket? = null
    private var firewallActive = false
    private var idsActive = false
    private var antiDeauthActive = false
    private var attacksBlockedCount = 0 // Contador de ataques

    private var isAlertShowing = false
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefs: SharedPreferences
    private var isActivityVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_blue_team)

        // Inicializar Memoria (Prefs)
        prefs = getSharedPreferences("BlueTeamPrefs", Context.MODE_PRIVATE)

        createNotificationChannel()
        checkNotificationPermission()

        initViews()
        setupToolbar()

        // RESTAURAR DATOS GUARDADOS (Logs y Contadores)
        restoreData()

        loadNetworkInfo()
        setupListeners()

        val user = FirebaseAuth.getInstance().currentUser
        if (user != null) {
            logActivity("🛡️ Sistema Iniciado (Usuario Activo)")
            NetworkAttackManager.registerDevice { }
            NetworkAttackManager.initMqtt(this)
            startListeningForAttacks()
        } else {
            logActivity("⚠️ Modo Offline (Sin Usuario)")
        }
    }

    override fun onResume() {
        super.onResume()
        isActivityVisible = true
        loadNetworkInfo()
        // Asegurar que el contador esté actualizado si volvimos de otra pantalla
        attacksBlockedCount = prefs.getInt("blocked_count", 0)
        updateCountersUI()

        NetworkAttackManager.initMqtt(this)
    }

    override fun onPause() {
        super.onPause()
        isActivityVisible = false
    }

    // --- PERSISTENCIA DE DATOS (NUEVO) ---
    private fun restoreData() {
        // 1. Restaurar Switches
        firewallActive = prefs.getBoolean("fw", false)
        idsActive = prefs.getBoolean("ids", false)
        antiDeauthActive = prefs.getBoolean("ad", false)

        switchFirewall.isChecked = firewallActive
        switchIDS.isChecked = idsActive
        switchAntiDeauth.isChecked = antiDeauthActive

        updateUI("FW", firewallActive)
        updateUI("IDS", idsActive)
        updateUI("AD", antiDeauthActive)

        // 2. Restaurar Contadores
        attacksBlockedCount = prefs.getInt("blocked_count", 0)
        updateCountersUI()

        // 3. Restaurar Log (Últimos mensajes)
        val savedLog = prefs.getString("activity_log", "") ?: ""
        if (savedLog.isNotEmpty()) {
            tvActivityLog.text = savedLog
            // Scrollear al final
            tvActivityLog.post {
                val scrollAmount = tvActivityLog.layout.getLineTop(tvActivityLog.lineCount) - tvActivityLog.height
                if (scrollAmount > 0) tvActivityLog.scrollTo(0, scrollAmount)
            }
        }
    }

    // Función para guardar datos clave
    private fun saveData() {
        val editor = prefs.edit()
        editor.putBoolean("fw", firewallActive)
        editor.putBoolean("ids", idsActive)
        editor.putBoolean("ad", antiDeauthActive)
        editor.putInt("blocked_count", attacksBlockedCount)
        // Guardamos el log también
        editor.putString("activity_log", tvActivityLog.text.toString())
        editor.apply()
    }

    // --- LÓGICA DE DEFENSA ---

    private fun startListeningForAttacks() {
        NetworkAttackManager.listenForIncomingAttacks { attackData ->
            processAttack(attackData["title"] as? String, attackData["description"] as? String, attackData["attackerId"] as? String)
        }
        NetworkAttackManager.setMessageListener { message ->
            runOnUiThread {
                val msg = message.uppercase()
                if (msg.contains("CREDENTIALS") || msg.contains("DATOS CAPTURADOS")) {
                    if (!isAlertShowing) {
                        logActivity("💀 ¡DATOS EXFILTRADOS!")
                        vibratePhone()
                        showCompromisedScreen("Credenciales interceptadas.")
                    }
                } else if (msg.contains("FLOOD") || msg.contains("DEAUTH") || msg.contains("EVIL") || msg.contains("TWIN")) {
                    processAttack("Amenaza Hardware", "Firma: $message", "Sensor")
                }
            }
        }
    }

    private fun processAttack(title: String?, desc: String?, source: String?) {
        runOnUiThread {
            val t = title ?: "Amenaza"; val d = desc ?: ""; val s = source ?: "?"
            if (!isActivityVisible) sendNotification(t, d)

            if (firewallActive || idsActive || antiDeauthActive) {
                logActivity("⚡ AUTO-BLOQUEO: $t")
                Toast.makeText(this, "🛡️ Bloqueado", Toast.LENGTH_SHORT).show()
                stopPhysicalAttack()

                // Incrementar y Guardar
                attacksBlockedCount++
                saveData()
                updateCountersUI()
            } else {
                if (!isAlertShowing) {
                    logActivity("⚠️ ALERTA: $t")
                    if (isActivityVisible) mostrarAlertaDeAtaque(t, d, s)
                }
            }
        }
    }

    private fun stopPhysicalAttack() {
        NetworkAttackManager.sendRealAttack(this, "STOP_PORTAL", "ALL")
        NetworkAttackManager.sendRealAttack(this, "STOP_ATTACK", "ALL")
        logActivity("📡 Orden de parada enviada")
    }

    private fun mostrarAlertaDeAtaque(titulo: String, desc: String, source: String) {
        isAlertShowing = true
        val builder = AlertDialog.Builder(this)
        builder.setTitle("⚠️ ¡AMENAZA DETECTADA!")
        builder.setMessage("$titulo\n\n¿Bloquear ahora?")
        builder.setCancelable(false)
        builder.setPositiveButton("BLOQUEAR") { d, _ ->
            logActivity("✅ Bloqueo manual")
            registrarEvento(titulo, "Bloqueo Manual", source, EventStatus.BLOCKED)
            stopPhysicalAttack()

            // Incrementar y Guardar
            attacksBlockedCount++
            saveData()
            updateCountersUI()

            isAlertShowing = false; d.dismiss()
        }
        builder.setNegativeButton("IGNORAR") { d, _ ->
            logActivity("⚠️ Riesgo aceptado")
            isAlertShowing = false; d.dismiss()
        }
        builder.show()
    }

    private fun showCompromisedScreen(detalles: String) {
        isAlertShowing = true
        val builder = AlertDialog.Builder(this)
        builder.setTitle("💀 SISTEMA COMPROMETIDO")
        builder.setMessage("¡ALERTA CRÍTICA!\n$detalles\n\n¿Neutralizar ahora?")
        builder.setCancelable(false)
        builder.setPositiveButton("NEUTRALIZAR") { d, _ ->
            logActivity("🛡️ Limpiando sistema...")
            stopPhysicalAttack()
            isAlertShowing = false; d.dismiss()
        }
        val dialog = builder.create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.RED)
    }

    // --- RED Y UI ---

    private fun logActivity(msg: String) {
        val t = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        handler.post {
            tvActivityLog.append("[$t] $msg\n")
            // Guardar cada vez que hay un log nuevo para no perder datos si la app crashea
            prefs.edit().putString("activity_log", tvActivityLog.text.toString()).apply()
        }
    }

    private fun updateCountersUI() {
        try {
            tvAttacksBlocked.text = attacksBlockedCount.toString()
        } catch (e: Exception) { }
    }

    private fun registrarEvento(t: String, d: String, s: String, st: EventStatus) {
        try {
            val type = if (st == EventStatus.BLOCKED) EventType.ATTACK_BLOCKED else EventType.ATTACK_DETECTED
            EventLogActivity.addEvent(SecurityEvent(type = type, title = t, description = d, sourceIp = s, status = st))
        } catch (e: Exception) {
            Log.e("BlueTeam", "Error guardando evento: ${e.message}")
        }
    }

    // --- CONFIGURACIÓN ESTÁNDAR (Sin cambios lógicos) ---

    private fun loadNetworkInfo() {
        try {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val info = wm.connectionInfo
            val ip = Formatter.formatIpAddress(info.ipAddress)
            tvLocalIp.text = if(ip!="0.0.0.0") ip else "..."
            var ssid = info.ssid
            if (ssid != null && ssid.startsWith("\"")) ssid = ssid.substring(1, ssid.length - 1)
            if (isInternetAvailable()) {
                tvWifiStatus.text = "🟢 Protegido"
                tvWifiStatus.setTextColor(getColor(android.R.color.holo_green_dark))
                tvWifiName.text = if (ssid == "<unknown ssid>" || ssid.isEmpty()) "WiFi Activo" else ssid
            } else {
                tvWifiStatus.text = "🔴 Offline"
                tvWifiStatus.setTextColor(getColor(android.R.color.holo_red_dark))
                tvWifiName.text = "Sin Red"
            }
        } catch(e:Exception){ tvLocalIp.text = "-" }
    }

    private fun isInternetAvailable(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val n = cm.activeNetwork ?: return false
        val c = cm.getNetworkCapabilities(n) ?: return false
        return c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun initViews() {
        btnOpenWifi = findViewById(R.id.btnOpenWifi); tvWifiStatus = findViewById(R.id.tvWifiStatus); tvWifiName = findViewById(R.id.tvWifiName); tvLocalIp = findViewById(R.id.tvLocalIp); tvEsp32Status = findViewById(R.id.tvEsp32Status); etEsp32Ip = findViewById(R.id.etEsp32Ip); btnConnectEsp32 = findViewById(R.id.btnConnectEsp32); btnToggleDefense = findViewById(R.id.btnToggleDefense); switchFirewall = findViewById(R.id.switchFirewall); tvFirewallStatus = findViewById(R.id.tvFirewallStatus); switchIDS = findViewById(R.id.switchIDS); tvIDSStatus = findViewById(R.id.tvIDSStatus); switchAntiDeauth = findViewById(R.id.switchAntiDeauth); tvAntiDeauthStatus = findViewById(R.id.tvAntiDeauthStatus); tvActivityLog = findViewById(R.id.tvActivityLog); tvActivityLog.movementMethod = android.text.method.ScrollingMovementMethod.getInstance()
        tvActivityLog.setOnTouchListener { v, event -> v.parent.requestDisallowInterceptTouchEvent(true); if ((event.action and MotionEvent.ACTION_MASK) == MotionEvent.ACTION_UP) v.parent.requestDisallowInterceptTouchEvent(false); false }
        try { tvAttacksBlocked = findViewById(R.id.tvAttacksBlocked) } catch (e: Exception) { }
    }

    private fun setupToolbar() { val t = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar); t.setNavigationOnClickListener { finish() } }
    private fun createNotificationChannel() { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("SECURITY_ALERTS", "Alertas", NotificationManager.IMPORTANCE_HIGH)) }
    private fun checkNotificationPermission() { if (Build.VERSION.SDK_INT >= 33) { if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001) } }
    private fun sendNotification(t: String, b: String) { try { val p = PendingIntent.getActivity(this, 0, Intent(this, BlueTeamActivity::class.java), PendingIntent.FLAG_IMMUTABLE); NotificationManagerCompat.from(this).notify(1001, NotificationCompat.Builder(this, "SECURITY_ALERTS").setSmallIcon(android.R.drawable.stat_sys_warning).setContentTitle("🚨 $t").setContentText(b).setPriority(NotificationCompat.PRIORITY_HIGH).setContentIntent(p).setAutoCancel(true).build()) } catch (e: SecurityException) {} }
    private fun vibratePhone() { val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator; if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) v.vibrate(VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE)) else v.vibrate(1000) }
    private fun updateUI(n: String, a: Boolean) { if(n=="FW") { tvFirewallStatus.text=if(a)"✅ Activo" else "⚪ Inactivo"; tvFirewallStatus.setTextColor(if(a) getColor(android.R.color.holo_green_dark) else getColor(android.R.color.darker_gray)) }; if(n=="IDS") { tvIDSStatus.text=if(a)"✅ Activo" else "⚪ Inactivo"; tvIDSStatus.setTextColor(if(a) getColor(android.R.color.holo_green_dark) else getColor(android.R.color.darker_gray)) }; if(n=="AD") { tvAntiDeauthStatus.text=if(a)"✅ Activo" else "⚪ Inactivo"; tvAntiDeauthStatus.setTextColor(if(a) getColor(android.R.color.holo_green_dark) else getColor(android.R.color.darker_gray)) }; if(firewallActive||idsActive||antiDeauthActive) { btnToggleDefense.text="Desactivar Todo"; btnToggleDefense.setBackgroundColor(getColor(R.color.warning)) } else { btnToggleDefense.text="Activar Defensa"; btnToggleDefense.setBackgroundColor(getColor(R.color.blue_team_dark)) }; saveData() }
    private fun setupListeners() { btnOpenWifi.setOnClickListener { startActivity(Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)) }; btnToggleDefense.setOnClickListener { if(firewallActive||idsActive||antiDeauthActive) {firewallActive=false;idsActive=false;antiDeauthActive=false} else {firewallActive=true;idsActive=true;antiDeauthActive=true}; switchFirewall.isChecked=firewallActive; switchIDS.isChecked=idsActive; switchAntiDeauth.isChecked=antiDeauthActive }; switchFirewall.setOnCheckedChangeListener { _, c -> firewallActive=c; updateUI("FW", c) }; switchIDS.setOnCheckedChangeListener { _, c -> idsActive=c; updateUI("IDS", c) }; switchAntiDeauth.setOnCheckedChangeListener { _, c -> antiDeauthActive=c; updateUI("AD", c) }; btnConnectEsp32.setOnClickListener { Toast.makeText(this, "Conexión auto", Toast.LENGTH_SHORT).show() } }
    override fun onDestroy() { super.onDestroy(); try { esp32Socket?.close() } catch (e: Exception) {} }
}
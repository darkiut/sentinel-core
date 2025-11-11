package com.tecsup.sentinelcore

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText

class SettingsActivity : AppCompatActivity() {

    // Vistas
    private lateinit var cardProfile: MaterialCardView
    private lateinit var tvUserName: TextView
    private lateinit var tvUserEmail: TextView

    private lateinit var etEsp32Ip: TextInputEditText
    private lateinit var etEsp32Port: TextInputEditText
    private lateinit var btnSaveIp: MaterialButton
    private lateinit var btnTestConnection: MaterialButton

    private lateinit var switchNotifications: SwitchMaterial
    private lateinit var switchSounds: SwitchMaterial
    private lateinit var switchAutoConnect: SwitchMaterial

    private lateinit var tvAppVersion: TextView
    private lateinit var tvEsp32Status: TextView
    private lateinit var tvTotalEvents: TextView

    private lateinit var btnClearEvents: MaterialButton
    private lateinit var btnLogout: MaterialButton

    // SharedPreferences para guardar configuración
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Inicializar SharedPreferences
        prefs = getSharedPreferences("SentinelCorePrefs", Context.MODE_PRIVATE)

        initViews()
        setupToolbar()
        loadSettings()
        setupListeners()
        updateInfo()
    }

    private fun initViews() {
        cardProfile = findViewById(R.id.cardProfile)
        tvUserName = findViewById(R.id.tvUserName)
        tvUserEmail = findViewById(R.id.tvUserEmail)

        etEsp32Ip = findViewById(R.id.etEsp32Ip)
        etEsp32Port = findViewById(R.id.etEsp32Port)
        btnSaveIp = findViewById(R.id.btnSaveIp)
        btnTestConnection = findViewById(R.id.btnTestConnection)

        switchNotifications = findViewById(R.id.switchNotifications)
        switchSounds = findViewById(R.id.switchSounds)
        switchAutoConnect = findViewById(R.id.switchAutoConnect)

        tvAppVersion = findViewById(R.id.tvAppVersion)
        tvEsp32Status = findViewById(R.id.tvEsp32Status)
        tvTotalEvents = findViewById(R.id.tvTotalEvents)

        btnClearEvents = findViewById(R.id.btnClearEvents)
        btnLogout = findViewById(R.id.btnLogout)
    }

    private fun setupToolbar() {
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun loadSettings() {
        // Cargar configuración guardada
        val savedIp = prefs.getString("esp32_ip", "192.168.4.1")
        val savedPort = prefs.getString("esp32_port", "80")
        val notificationsEnabled = prefs.getBoolean("notifications_enabled", true)
        val soundsEnabled = prefs.getBoolean("sounds_enabled", true)
        val autoConnectEnabled = prefs.getBoolean("auto_connect_enabled", false)

        etEsp32Ip.setText(savedIp)
        etEsp32Port.setText(savedPort)
        switchNotifications.isChecked = notificationsEnabled
        switchSounds.isChecked = soundsEnabled
        switchAutoConnect.isChecked = autoConnectEnabled

        // Cargar información del usuario (del intent o SharedPreferences)
        val userEmail = intent.getStringExtra("USER_EMAIL")
            ?: prefs.getString("user_email", "usuario@ejemplo.com")
        val userName = prefs.getString("user_name", "Usuario")

        tvUserEmail.text = userEmail
        tvUserName.text = userName
    }

    private fun setupListeners() {
        // Editar perfil
        cardProfile.setOnClickListener {
            startActivity(Intent(this, EditProfileActivity::class.java))
        }


        // Guardar IP del ESP32
        btnSaveIp.setOnClickListener {
            val ip = etEsp32Ip.text.toString().trim()
            val port = etEsp32Port.text.toString().trim()

            if (validateIp(ip) && validatePort(port)) {
                saveEsp32Config(ip, port)
            }
        }

        // Probar conexión con ESP32
        btnTestConnection.setOnClickListener {
            val ip = etEsp32Ip.text.toString().trim()
            val port = etEsp32Port.text.toString().trim()

            if (validateIp(ip) && validatePort(port)) {
                testEsp32Connection(ip, port)
            }
        }

        // Switches de preferencias
        switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("notifications_enabled", isChecked).apply()
            val status = if (isChecked) "activadas" else "desactivadas"
            Toast.makeText(this, "Notificaciones $status", Toast.LENGTH_SHORT).show()
        }

        switchSounds.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("sounds_enabled", isChecked).apply()
            val status = if (isChecked) "activados" else "desactivados"
            Toast.makeText(this, "Sonidos $status", Toast.LENGTH_SHORT).show()
        }

        switchAutoConnect.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("auto_connect_enabled", isChecked).apply()
            val status = if (isChecked) "activada" else "desactivada"
            Toast.makeText(this, "Auto-conexión $status", Toast.LENGTH_SHORT).show()
        }

        // Limpiar eventos
        btnClearEvents.setOnClickListener {
            showClearEventsDialog()
        }

        // Cerrar sesión
        btnLogout.setOnClickListener {
            showLogoutDialog()
        }
    }

    private fun validateIp(ip: String): Boolean {
        if (ip.isEmpty()) {
            Toast.makeText(this, "Ingresa una IP válida", Toast.LENGTH_SHORT).show()
            return false
        }

        val ipPattern = "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
        if (!ip.matches(ipPattern.toRegex())) {
            Toast.makeText(this, "Formato de IP inválido", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }

    private fun validatePort(port: String): Boolean {
        if (port.isEmpty()) {
            Toast.makeText(this, "Ingresa un puerto válido", Toast.LENGTH_SHORT).show()
            return false
        }

        val portNumber = port.toIntOrNull()
        if (portNumber == null || portNumber < 1 || portNumber > 65535) {
            Toast.makeText(this, "Puerto debe estar entre 1 y 65535", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }

    private fun saveEsp32Config(ip: String, port: String) {
        prefs.edit().apply {
            putString("esp32_ip", ip)
            putString("esp32_port", port)
            apply()
        }

        Toast.makeText(this, "Configuración guardada", Toast.LENGTH_SHORT).show()
    }

    private fun testEsp32Connection(ip: String, port: String) {
        btnTestConnection.isEnabled = false
        btnTestConnection.text = "Probando..."

        // Simular prueba de conexión (en un caso real usarías un Thread o Coroutine)
        Thread {
            try {
                // Aquí irían los comandos reales para probar la conexión
                Thread.sleep(2000) // Simular delay

                runOnUiThread {
                    btnTestConnection.isEnabled = true
                    btnTestConnection.text = "Probar Conexión"

                    // Simular resultado (en producción verificarías la conexión real)
                    val success = (0..10).random() > 3 // 70% de éxito simulado

                    if (success) {
                        Toast.makeText(this, "✅ Conexión exitosa con $ip:$port", Toast.LENGTH_LONG).show()
                        tvEsp32Status.text = "Conectado"
                        tvEsp32Status.setTextColor(getColor(R.color.success))
                    } else {
                        Toast.makeText(this, "❌ No se pudo conectar con $ip:$port", Toast.LENGTH_LONG).show()
                        tvEsp32Status.text = "Error de conexión"
                        tvEsp32Status.setTextColor(getColor(R.color.red_team_dark))
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    btnTestConnection.isEnabled = true
                    btnTestConnection.text = "Probar Conexión"
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun updateInfo() {
        // Versión de la app
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            tvAppVersion.text = packageInfo.versionName
        } catch (e: Exception) {
            tvAppVersion.text = "1.0.0"
        }

        // Total de eventos
        val totalEvents = EventLogActivity.getEvents().size
        tvTotalEvents.text = totalEvents.toString()

        // Estado ESP32 (por defecto desconectado)
        tvEsp32Status.text = "Desconectado"
        tvEsp32Status.setTextColor(getColor(R.color.red_team_dark))
    }

    private fun showClearEventsDialog() {
        val totalEvents = EventLogActivity.getEvents().size

        if (totalEvents == 0) {
            Toast.makeText(this, "No hay eventos para eliminar", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Limpiar Eventos")
            .setMessage("¿Estás seguro de eliminar todos los $totalEvents eventos registrados?\n\nEsta acción no se puede deshacer.")
            .setPositiveButton("Eliminar") { _, _ ->
                EventLogActivity.clearEvents()
                tvTotalEvents.text = "0"
                Toast.makeText(this, "Eventos eliminados", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showLogoutDialog() {
        AlertDialog.Builder(this)
            .setTitle("Cerrar Sesión")
            .setMessage("¿Estás seguro de que quieres cerrar sesión?")
            .setPositiveButton("Sí") { _, _ ->
                performLogout()
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun performLogout() {
        // Cerrar sesión en Firebase
        com.google.firebase.auth.FirebaseAuth.getInstance().signOut()

        // Limpiar configuración del usuario
        prefs.edit().apply {
            remove("user_email")
            remove("user_name")
            putBoolean("is_logged_in", false)
            apply()
        }

        // Volver al login
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()

        Toast.makeText(this, "Sesión cerrada", Toast.LENGTH_SHORT).show()
    }


    override fun onResume() {
        super.onResume()
        // Actualizar información cuando volvamos a esta pantalla
        updateInfo()
    }
}
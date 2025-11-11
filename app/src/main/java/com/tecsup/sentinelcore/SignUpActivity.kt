package com.tecsup.sentinelcore

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth

class SignUpActivity : AppCompatActivity() {

    private lateinit var etEmail: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var etConfirmPassword: TextInputEditText
    private lateinit var btnSignUp: MaterialButton
    private lateinit var btnBackToLogin: MaterialButton

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        auth = FirebaseAuth.getInstance()

        initViews()
        setupListeners()
    }

    private fun initViews() {
        etEmail = findViewById(R.id.etSignUpEmail)
        etPassword = findViewById(R.id.etSignUpPassword)
        etConfirmPassword = findViewById(R.id.etConfirmPassword)
        btnSignUp = findViewById(R.id.btnSignUp)
        btnBackToLogin = findViewById(R.id.btnBackToLogin)
    }

    private fun setupListeners() {
        btnSignUp.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()
            val confirmPassword = etConfirmPassword.text.toString().trim()

            if (validateInputs(email, password, confirmPassword)) {
                registerUser(email, password)
            }
        }

        btnBackToLogin.setOnClickListener {
            finish()
        }
    }

    private fun validateInputs(email: String, password: String, confirmPassword: String): Boolean {
        if (email.isEmpty()) {
            Toast.makeText(this, "Por favor ingresa tu correo", Toast.LENGTH_SHORT).show()
            return false
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Correo electrónico inválido", Toast.LENGTH_SHORT).show()
            return false
        }

        if (password.isEmpty()) {
            Toast.makeText(this, "Por favor ingresa una contraseña", Toast.LENGTH_SHORT).show()
            return false
        }

        if (password.length < 6) {
            Toast.makeText(this, "La contraseña debe tener al menos 6 caracteres", Toast.LENGTH_SHORT).show()
            return false
        }

        if (confirmPassword.isEmpty()) {
            Toast.makeText(this, "Por favor confirma tu contraseña", Toast.LENGTH_SHORT).show()
            return false
        }

        if (password != confirmPassword) {
            Toast.makeText(this, "Las contraseñas no coinciden", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }

    private fun registerUser(email: String, password: String) {
        btnSignUp.isEnabled = false
        btnSignUp.text = "Registrando..."

        val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    val uid = user?.uid ?: ""

                    // Crear objeto User
                    val newUser = User(
                        uid = uid,
                        email = email,
                        name = email.substringBefore("@"), // Usar el nombre del email por defecto
                        phone = "",
                        photoUrl = "",
                        role = "user"
                    )

                    // Guardar en Firestore
                    db.collection("users").document(uid).set(newUser)
                        .addOnSuccessListener {
                            Toast.makeText(this, "¡Cuenta creada exitosamente!", Toast.LENGTH_SHORT).show()

                            val intent = Intent(this, DashboardActivity::class.java)
                            intent.putExtra("USER_EMAIL", email)
                            startActivity(intent)
                            finish()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "Error al guardar datos: ${e.message}", Toast.LENGTH_LONG).show()
                            btnSignUp.isEnabled = true
                            btnSignUp.text = "Crear Cuenta"
                        }
                } else {
                    val errorMessage = when {
                        task.exception?.message?.contains("already in use") == true ->
                            "Este correo ya está registrado."
                        task.exception?.message?.contains("weak password") == true ->
                            "La contraseña es muy débil."
                        else -> "Error en registro: ${task.exception?.message}"
                    }
                    Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()

                    btnSignUp.isEnabled = true
                    btnSignUp.text = "Crear Cuenta"
                }
            }
    }

}

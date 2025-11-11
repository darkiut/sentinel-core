package com.tecsup.sentinelcore

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class EditProfileActivity : AppCompatActivity() {

    private lateinit var etName: TextInputEditText
    private lateinit var etPhone: TextInputEditText
    private lateinit var etEmail: TextInputEditText
    private lateinit var btnSave: MaterialButton
    private lateinit var btnCancel: MaterialButton

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var currentUser: User? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        initViews()
        setupToolbar()
        loadUserData()
        setupListeners()
    }

    private fun initViews() {
        etName = findViewById(R.id.etName)
        etPhone = findViewById(R.id.etPhone)
        etEmail = findViewById(R.id.etEmail)
        btnSave = findViewById(R.id.btnSave)
        btnCancel = findViewById(R.id.btnCancel)
    }

    private fun setupToolbar() {
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        toolbar?.setNavigationOnClickListener {
            finish()
        }
    }

    private fun loadUserData() {
        val currentUserAuth = auth.currentUser
        if (currentUserAuth != null) {
            db.collection("users").document(currentUserAuth.uid).get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        currentUser = document.toObject(User::class.java)
                        displayUserData()
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error al cargar datos: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun displayUserData() {
        currentUser?.let {
            etName.setText(it.name)
            etPhone.setText(it.phone)
            etEmail.setText(it.email)
        }
    }

    private fun setupListeners() {
        btnSave.setOnClickListener {
            saveUserData()
        }

        btnCancel.setOnClickListener {
            finish()
        }
    }

    private fun saveUserData() {
        val name = etName.text.toString().trim()
        val phone = etPhone.text.toString().trim()

        if (name.isEmpty()) {
            Toast.makeText(this, "El nombre no puede estar vacío", Toast.LENGTH_SHORT).show()
            return
        }

        btnSave.isEnabled = false
        btnSave.text = "Guardando..."

        val currentUserAuth = auth.currentUser
        if (currentUserAuth != null) {
            val updatedUser = mapOf(
                "name" to name,
                "phone" to phone,
                "updatedAt" to System.currentTimeMillis()
            )

            db.collection("users").document(currentUserAuth.uid).update(updatedUser)
                .addOnSuccessListener {
                    Toast.makeText(this, "Perfil actualizado", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error al guardar: ${e.message}", Toast.LENGTH_SHORT).show()
                    btnSave.isEnabled = true
                    btnSave.text = "Guardar"
                }
        }
    }
}

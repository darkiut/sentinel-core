package com.tecsup.sentinelcore

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

object NetworkAttackManager {

    private val database = FirebaseDatabase.getInstance()
    private val attacksRef = database.getReference("attacks")
    private val devicesRef = database.getReference("devices")

    // Registrar dispositivo en línea
    fun registerDevice(onComplete: (String) -> Unit) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val deviceId = userId

        val deviceData = mapOf(
            "userId" to userId,
            "status" to "online",
            "timestamp" to System.currentTimeMillis()
        )

        devicesRef.child(deviceId).setValue(deviceData)
            .addOnSuccessListener {
                onComplete(deviceId)
            }
    }

    // Enviar ataque a un dispositivo específico
    fun sendAttack(
        targetUserId: String,
        attackType: EventType,
        attackTitle: String,
        attackDescription: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        val attackData = mapOf(
            "attackerId" to userId,
            "targetId" to targetUserId,
            "type" to attackType.name,
            "title" to attackTitle,
            "description" to attackDescription,
            "timestamp" to System.currentTimeMillis(),
            "status" to "sent"
        )

        val attackKey = attacksRef.push().key ?: return

        attacksRef.child(attackKey).setValue(attackData)
            .addOnSuccessListener {
                onSuccess()
            }
            .addOnFailureListener { e ->
                onError(e.message ?: "Error desconocido")
            }
    }

    // Escuchar ataques entrantes
    fun listenForIncomingAttacks(onAttackReceived: (Map<String, Any>) -> Unit) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        attacksRef.orderByChild("targetId").equalTo(userId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    for (attackSnapshot in snapshot.children) {
                        val attackData = attackSnapshot.value as? Map<String, Any>
                        if (attackData != null && attackData["status"] == "sent") {
                            onAttackReceived(attackData)

                            // Marcar como recibido
                            attackSnapshot.ref.child("status").setValue("received")
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    // Manejar error
                }
            })
    }

    // Obtener dispositivos en línea
    fun getOnlineDevices(onDevicesLoaded: (List<Map<String, Any>>) -> Unit) {
        devicesRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val devices = mutableListOf<Map<String, Any>>()

                for (deviceSnapshot in snapshot.children) {
                    val deviceData = deviceSnapshot.value as? Map<String, Any>
                    if (deviceData != null) {
                        devices.add(deviceData)
                    }
                }

                onDevicesLoaded(devices)
            }

            override fun onCancelled(error: DatabaseError) {
                onDevicesLoaded(emptyList())
            }
        })
    }
}

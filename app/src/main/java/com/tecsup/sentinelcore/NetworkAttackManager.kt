package com.tecsup.sentinelcore

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import info.mqtt.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.*

object NetworkAttackManager {
    private val database = FirebaseDatabase.getInstance()
    private val attacksRef = database.getReference("attacks")
    private val devicesRef = database.getReference("devices")

    private var mqttClient: MqttAndroidClient? = null

    // [CORRECCIÓN CRÍTICA] APUNTANDO A TU SERVIDOR VPS PRIVADO
    private const val MQTT_BROKER_URL = "tcp://38.250.161.205:1883"

    private const val TOPIC_COMMANDS = "sentinel/commands"
    private const val TOPIC_LOGS = "sentinel/logs"

    private var onMessageReceived: ((String) -> Unit)? = null
    fun setMessageListener(listener: (String) -> Unit) {
        onMessageReceived = listener
    }

    fun initMqtt(context: Context) {
        try {
            if (mqttClient != null && mqttClient!!.isConnected) return

            // ID Único para evitar desconexiones
            val clientId = "SentinelApp_" + System.currentTimeMillis() + "_" + (0..999).random()
            mqttClient = MqttAndroidClient(context.applicationContext, MQTT_BROKER_URL, clientId)

            val options = MqttConnectOptions()
            options.isCleanSession = true
            options.isAutomaticReconnect = true
            options.connectionTimeout = 30
            options.keepAliveInterval = 60

            mqttClient!!.connect(options, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.d("MQTT", "✅ CONECTADO A VPS (38.250...)")
                    // Suscribirse para escuchar al ESP32 y Web
                    subscribeToTopic(TOPIC_COMMANDS)
                    subscribeToTopic(TOPIC_LOGS)
                }
                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.e("MQTT", "❌ Error conexión VPS: ${exception?.message}")
                }
            })

            mqttClient!!.setCallback(object : MqttCallback {
                override fun connectionLost(cause: Throwable?) {}
                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    val msg = message?.toString() ?: return
                    onMessageReceived?.invoke(msg)
                }
                override fun deliveryComplete(token: IMqttDeliveryToken?) {}
            })
        } catch (e: Exception) { Log.e("MQTT", "Error init: ${e.message}") }
    }

    private fun subscribeToTopic(topic: String) {
        try { mqttClient?.subscribe(topic, 0) } catch (e: Exception) {}
    }

    // Función para enviar ataques reales (Hardware/Node-RED)
    fun sendRealAttack(context: Context, command: String, target: String) {
        if (mqttClient == null || !mqttClient!!.isConnected) {
            initMqtt(context)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                sendPayload(command, target)
            }, 2000)
            return
        }
        sendPayload(command, target)
    }

    private fun sendPayload(command: String, target: String) {
        try {
            val payload = "{\"action\": \"$command\", \"target\": \"$target\"}"
            val message = MqttMessage(payload.toByteArray())
            message.qos = 1
            if (mqttClient != null && mqttClient!!.isConnected) {
                mqttClient!!.publish(TOPIC_COMMANDS, message, null, null)
            }
        } catch (e: Exception) { }
    }

    // Función para enviar Logs a Node-RED (Terminal Hacker)
    fun publishLog(message: String) {
        try {
            if (mqttClient != null && mqttClient!!.isConnected) {
                val msg = MqttMessage(message.toByteArray())
                mqttClient!!.publish(TOPIC_LOGS, msg)
            }
        } catch (e: Exception) {}
    }

    // --- FUNCIONES FIREBASE (Simulación) ---
    fun listenForIncomingAttacks(onAttackReceived: (Map<String, Any>) -> Unit) {
        val myUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        attacksRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (child in snapshot.children) {
                    val data = child.value as? Map<String, Any>
                    if (data != null) {
                        val tid = data["targetId"] as? String
                        val stat = data["status"] as? String
                        if (tid == myUserId && stat == "sent") {
                            onAttackReceived(data)
                            child.ref.child("status").setValue("received")
                        }
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    fun sendAttack(targetUserId: String, type: EventType, title: String, desc: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val data = mapOf("attackerId" to uid, "targetId" to targetUserId, "type" to type.name, "title" to title, "description" to desc, "timestamp" to System.currentTimeMillis(), "status" to "sent")
        attacksRef.push().setValue(data).addOnSuccessListener { onSuccess() }.addOnFailureListener { onError(it.message ?: "") }
    }

    fun registerDevice(onComplete: (String) -> Unit) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        devicesRef.child(uid).setValue(mapOf("userId" to uid, "status" to "online", "ts" to System.currentTimeMillis())).addOnSuccessListener { onComplete(uid) }
    }

    fun getOnlineDevices(onLoaded: (List<Map<String, Any>>) -> Unit) {
        devicesRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                val list = mutableListOf<Map<String, Any>>()
                for (c in s.children) { c.value?.let { list.add(it as Map<String, Any>) } }
                onLoaded(list)
            }
            override fun onCancelled(e: DatabaseError) { onLoaded(emptyList()) }
        })
    }
}
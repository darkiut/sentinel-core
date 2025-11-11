package com.tecsup.sentinelcore

data class SecurityEvent(
    val id: Long = System.currentTimeMillis(),
    val type: EventType,
    val title: String,
    val description: String,
    val sourceIp: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: EventStatus
)

enum class EventType {
    ATTACK_SENT,      // Ataque enviado (Red Team)
    ATTACK_BLOCKED,   // Ataque bloqueado (Blue Team)
    DEFENSE_ACTIVATED, // Defensa activada
    INTRUSION_DETECTED, // Intrusión detectada
    FIREWALL_BLOCK    // Bloqueo de firewall
}

enum class EventStatus {
    SUCCESS,   // Exitoso
    BLOCKED,   // Bloqueado
    FAILED,    // Fallido
    DETECTED   // Detectado
}
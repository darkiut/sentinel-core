package com.tecsup.sentinelcore

data class User(
    val uid: String = "",
    val email: String = "",
    val name: String = "",
    val phone: String = "",
    val photoUrl: String = "",
    val role: String = "user",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

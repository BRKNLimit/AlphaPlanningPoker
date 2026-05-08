package com.teamalpha.models

import io.ktor.server.auth.*
import io.ktor.server.websocket.*
import kotlinx.serialization.Serializable

@Serializable
enum class UserStatus { PENDING, APPROVED }

@Serializable
data class User(
    val username: String,
    val passwordHash: String, // Simplified for this prototype
    var status: UserStatus = UserStatus.PENDING,
    val isAdmin: Boolean = false
)

@Serializable
data class UserSession(val username: String) : Principal

@Serializable
data class Participant(
    val id: String,
    val name: String,
    var vote: String? = null,
    val isHost: Boolean = false
)

@Serializable
data class Room(
    val id: String,
    val participants: MutableMap<String, Participant> = mutableMapOf(),
    var isRevealed: Boolean = false
)

@Serializable
data class ClientMessage(
    val type: String,
    val roomId: String? = null,
    val name: String? = null,
    val vote: String? = null,
    val username: String? = null,
    val password: String? = null,
    val targetUsername: String? = null // For admin actions
)

class Connection(val session: DefaultWebSocketServerSession) {
    var participantId: String = ""
    var roomId: String = ""
    var username: String = ""
}

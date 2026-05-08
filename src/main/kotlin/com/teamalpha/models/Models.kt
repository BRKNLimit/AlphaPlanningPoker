package com.teamalpha.models

import io.ktor.server.websocket.*
import kotlinx.serialization.Serializable

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
    val vote: String? = null
)

class Connection(val session: DefaultWebSocketServerSession) {
    var participantId: String = ""
    var roomId: String = ""
}

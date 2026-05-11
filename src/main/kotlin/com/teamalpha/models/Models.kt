package com.teamalpha.models

import io.ktor.server.websocket.*
import kotlinx.serialization.Serializable

@Serializable
data class Participant(
    val id: String,
    val name: String,
    var vote: String? = null,
    val isHost: Boolean = false,
    var isFoil: Boolean = false
)

@Serializable
data class Room(
    val id: String,
    val name: String,
    val participants: Map<String, Participant>,
    var isRevealed: Boolean = false,
    var consensusValue: String? = null
)

@Serializable
data class ClientMessage(
    val type: String? = null,
    val roomId: String? = null,
    val vote: String? = null,
    val username: String? = null,
    val reaction: String? = null,
    val emoji: String? = null,
    val pId: String? = null,
    val yourId: String? = null
)

class Connection(val session: DefaultWebSocketServerSession) {
    var participantId: String = ""
    var roomId: String = ""
    var username: String = ""
}

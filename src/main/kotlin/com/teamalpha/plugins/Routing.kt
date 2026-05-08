package com.teamalpha.plugins

import com.teamalpha.RoomManager
import com.teamalpha.models.ClientMessage
import com.teamalpha.models.Connection
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.json.Json

fun Application.configureRouting(roomManager: RoomManager) {
    routing {
        staticResources("/", "static", index = "index.html")

        webSocket("/poker") {
            val connection = Connection(this)
            try {
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        val msg = Json.decodeFromString<ClientMessage>(text)
                        when (msg.type) {
                            "join" -> roomManager.join(connection, msg.roomId ?: "Alpha", msg.name ?: "Alpha-Guest")
                            "vote" -> roomManager.vote(connection.roomId, connection.participantId, msg.vote ?: "")
                            "reveal" -> roomManager.reveal(connection.roomId)
                            "reset" -> roomManager.reset(connection.roomId)
                        }
                    }
                }
            } finally {
                roomManager.disconnect(connection)
            }
        }
    }
}

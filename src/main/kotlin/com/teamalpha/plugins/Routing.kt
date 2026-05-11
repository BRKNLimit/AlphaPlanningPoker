package com.teamalpha.plugins

import com.teamalpha.RoomManager
import com.teamalpha.models.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.json.Json

fun Application.configureRouting(roomManager: RoomManager) {
    routing {
        static("/") {
            resources("static")
            defaultResource("static/index.html")
        }

        webSocket("/poker") {
            val connection = Connection(this)
            
            try {
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        val msg = Json.decodeFromString<ClientMessage>(text)
                        when (msg.type) {
                            "join" -> {
                                connection.username = msg.username ?: "Unknown Player"
                                roomManager.join(connection, msg.roomId ?: "", connection.username)
                            }
                            "vote" -> roomManager.vote(connection.roomId, connection.participantId, msg.vote ?: "", msg.isAllIn ?: false)
                            "reveal" -> roomManager.reveal(connection.roomId)
                            "reset" -> roomManager.reset(connection.roomId)
                            "reaction" -> roomManager.reaction(connection.roomId, msg.reaction ?: "🤡")
                        }
                    }
                }
            } catch (e: Exception) {
                println("WS Error: ${e.message}")
            } finally {
                if (connection.roomId.isNotEmpty()) {
                    roomManager.disconnect(connection)
                }
            }
        }
    }
}

package com.teamalpha.plugins

import com.teamalpha.RoomManager
import com.teamalpha.UserManager
import com.teamalpha.models.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.json.Json

fun Application.configureRouting(roomManager: RoomManager, userManager: UserManager) {
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
                            "join" -> roomManager.join(connection, msg.roomId ?: "Alpha", msg.username ?: "Player", 0)
                            "vote" -> roomManager.vote(connection.roomId, connection.participantId, msg.vote ?: "")
                            "reveal" -> roomManager.reveal(connection.roomId)
                            "reset" -> roomManager.reset(connection.roomId)
                            "reaction" -> roomManager.reaction(connection.roomId, msg.reaction ?: "🤡")
                        }
                    }
                }
            } catch (e: Exception) {
                println("WS Error: ${e.message}")
            } finally {
                roomManager.disconnect(connection)
            }
        }
    }
}

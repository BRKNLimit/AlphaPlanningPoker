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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

fun Application.configureRouting(roomManager: RoomManager, userManager: UserManager) {
    routing {
        staticResources("/", "static", index = "index.html")

        post("/login") {
            try {
                val msg = call.receive<ClientMessage>()
                println("LOGIN ATTEMPT: ${msg.username}")
                val user = userManager.authenticate(msg.username ?: "", msg.password ?: "")
                if (user != null) {
                    if (user.status == UserStatus.APPROVED) {
                        call.sessions.set(UserSession(user.username))
                        call.respond(mapOf("status" to "OK", "isAdmin" to user.isAdmin))
                    } else {
                        call.respond(mapOf("status" to "PENDING", "message" to "User not yet approved by admin"))
                    }
                } else {
                    call.respond(mapOf("status" to "ERROR", "message" to "Invalid username or password"))
                }
            } catch (e: Exception) {
                println("LOGIN EXCEPTION: ${e.message}")
                call.respond(mapOf("status" to "ERROR", "message" to "Server Error: ${e.message}"))
            }
        }

        post("/register") {
            try {
                val msg = call.receive<ClientMessage>()
                val success = userManager.register(msg.username ?: "", msg.password ?: "")
                if (success) {
                    call.respond(mapOf("status" to "OK"))
                } else {
                    call.respond(mapOf("status" to "ERROR", "message" to "User already exists"))
                }
            } catch (e: Exception) {
                application.log.error("Registration error", e)
                call.respond(mapOf("status" to "ERROR", "message" to e.message))
            }
        }

        authenticate("auth-session") {
            get("/admin/users") {
                val session = call.sessions.get<UserSession>()
                val user = userManager.getAllUsers().find { it.username == session?.username }
                if (user?.isAdmin == true) {
                    call.respond(userManager.getAllUsers())
                } else {
                    call.respond(mapOf("error" to "Forbidden"))
                }
            }

            post("/admin/approve") {
                val session = call.sessions.get<UserSession>()
                val user = userManager.getAllUsers().find { it.username == session?.username }
                if (user?.isAdmin == true) {
                    val msg = call.receive<ClientMessage>()
                    userManager.approveUser(msg.targetUsername ?: "")
                    call.respond(mapOf("status" to "OK"))
                } else {
                    call.respond(mapOf("status" to "Forbidden"))
                }
            }

            webSocket("/poker") {
                val session = call.sessions.get<UserSession>() ?: return@webSocket close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "No session"))
                val user = userManager.getAllUsers().find { it.username == session.username }
                if (user?.status != UserStatus.APPROVED) return@webSocket close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Not approved"))

                val connection = Connection(this)
                connection.username = user.username
                
                try {
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            val text = frame.readText()
                            val msg = Json.decodeFromString<ClientMessage>(text)
                            when (msg.type) {
                                "join" -> roomManager.join(connection, msg.roomId ?: "Alpha", user.username, msg.chips ?: 10)
                                "vote" -> roomManager.vote(connection.roomId, connection.participantId, msg.vote ?: "", msg.wager ?: 0, msg.isAllIn ?: false)
                                "reveal" -> roomManager.reveal(connection.roomId)
                                "reset" -> roomManager.reset(connection.roomId)
                                "reaction" -> roomManager.reaction(connection.roomId, msg.reaction ?: "🤡")
                            }
                        }
                    }
                } finally {
                    roomManager.disconnect(connection)
                }
            }
        }
    }
}

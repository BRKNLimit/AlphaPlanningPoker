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

        post("/login") {
            try {
                val msg = call.receive<ClientMessage>()
                val user = userManager.authenticate(msg.username ?: "", msg.password ?: "")
                if (user != null) {
                    if (user.status == UserStatus.APPROVED) {
                        call.sessions.set(UserSession(user.username))
                        call.respond(AuthResponse("OK", isAdmin = user.isAdmin))
                    } else {
                        call.respond(AuthResponse("PENDING", message = "Wait for admin approval"))
                    }
                } else {
                    call.respond(AuthResponse("ERROR", message = "Invalid credentials"))
                }
            } catch (e: Exception) {
                call.respond(AuthResponse("ERROR", message = e.message))
            }
        }

        post("/register") {
            try {
                val msg = call.receive<ClientMessage>()
                val success = userManager.register(msg.username ?: "", msg.password ?: "")
                if (success) call.respond(AuthResponse("OK")) 
                else call.respond(AuthResponse("ERROR", message = "User already exists"))
            } catch (e: Exception) {
                call.respond(AuthResponse("ERROR", message = e.message))
            }
        }

        authenticate("auth-session") {
            get("/admin/users") {
                val session = call.sessions.get<UserSession>()
                val user = userManager.getUser(session?.username ?: "")
                if (user?.isAdmin == true) {
                    call.respond(userManager.getAllUsersDTO())
                } else {
                    call.respond(AuthResponse("ERROR", message = "Unauthorized"))
                }
            }

            post("/admin/approve") {
                val session = call.sessions.get<UserSession>()
                val user = userManager.getUser(session?.username ?: "")
                if (user?.isAdmin == true) {
                    val msg = call.receive<ClientMessage>()
                    userManager.approveUser(msg.targetUsername ?: "")
                    call.respond(AuthResponse("OK"))
                } else {
                    call.respond(AuthResponse("ERROR", message = "Unauthorized"))
                }
            }

            webSocket("/poker") {
                val session = call.sessions.get<UserSession>()
                if (session == null) {
                    close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "No session"))
                    return@webSocket
                }
                
                val user = userManager.getUser(session.username)
                if (user == null || user.status != UserStatus.APPROVED) {
                    close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Not approved"))
                    return@webSocket
                }

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
                } catch (e: Exception) {
                    println("WS Error: ${e.message}")
                } finally {
                    roomManager.disconnect(connection)
                }
            }
        }
    }
}

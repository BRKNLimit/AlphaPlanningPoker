package com.teamalpha

import com.teamalpha.models.*
import io.ktor.serialization.kotlinx.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Duration
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import kotlinx.serialization.encodeToString

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

class RoomManager {
    private val rooms = ConcurrentHashMap<String, Room>()
    private val connections = ConcurrentHashMap<String, MutableSet<Connection>>()

    suspend fun join(connection: Connection, roomId: String, name: String) {
        val room = rooms.computeIfAbsent(roomId) { Room(it) }
        val pId = UUID.randomUUID().toString()
        val isHost = room.participants.isEmpty()
        val participant = Participant(pId, name, isHost = isHost)
        
        room.participants[pId] = participant
        connection.participantId = pId
        connection.roomId = roomId
        
        connections.computeIfAbsent(roomId) { CopyOnWriteArraySet() }.add(connection)
        broadcast(roomId)
    }

    suspend fun vote(roomId: String, pId: String, vote: String) {
        val room = rooms[roomId] ?: return
        room.participants[pId]?.vote = vote
        broadcast(roomId)
    }

    suspend fun reveal(roomId: String) {
        val room = rooms[roomId] ?: return
        room.isRevealed = true
        broadcast(roomId)
    }

    suspend fun reset(roomId: String) {
        val room = rooms[roomId] ?: return
        room.isRevealed = false
        room.participants.values.forEach { it.vote = null }
        broadcast(roomId)
    }

    suspend fun disconnect(connection: Connection) {
        val roomId = connection.roomId
        val pId = connection.participantId
        val room = rooms[roomId] ?: return
        
        room.participants.remove(pId)
        connections[roomId]?.remove(connection)
        
        if (room.participants.isEmpty()) {
            rooms.remove(roomId)
            connections.remove(roomId)
        } else {
            // Reassign host if needed
            if (room.participants.values.none { it.isHost }) {
                val nextHost = room.participants.values.firstOrNull()
                if (nextHost != null) {
                    room.participants[nextHost.id] = nextHost.copy(isHost = true)
                }
            }
            broadcast(roomId)
        }
    }

    private suspend fun broadcast(roomId: String) {
        val room = rooms[roomId] ?: return
        val message = Json.encodeToString(room)
        connections[roomId]?.forEach { 
            try {
                it.session.send(Frame.Text(message))
            } catch (e: Exception) {
                // Connection might be closed
            }
        }
    }
}

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    val roomManager = RoomManager()

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

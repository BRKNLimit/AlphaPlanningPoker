package com.teamalpha

import com.teamalpha.models.*
import io.ktor.websocket.*
import kotlinx.serialization.json.Json
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import kotlinx.serialization.encodeToString

class RoomManager {
    private val rooms = ConcurrentHashMap<String, Room>() // ID to Room
    private val nameToId = ConcurrentHashMap<String, String>() // Name to ID
    private val connections = ConcurrentHashMap<String, MutableSet<Connection>>()

    suspend fun join(connection: Connection, roomNameOrId: String, username: String) {
        // Find existing room by ID or Name
        var room = rooms[roomNameOrId] ?: rooms[nameToId[roomNameOrId]]
        
        if (room == null) {
            // Create new room
            val id = generateUniqueId()
            val name = if (roomNameOrId.startsWith("#")) "New Room" else roomNameOrId
            room = Room(id, name)
            rooms[id] = room
            nameToId[name] = id
        }

        val pId = UUID.randomUUID().toString()
        val isHost = room.participants.isEmpty()
        val participant = Participant(pId, username, isHost = isHost)
        
        room.participants[pId] = participant
        connection.participantId = pId
        connection.roomId = room.id
        
        connections.computeIfAbsent(room.id) { CopyOnWriteArraySet() }.add(connection)
        broadcast(room.id)
    }

    private fun generateUniqueId(): String {
        var id: String
        do {
            id = "#" + (1000..9999).random().toString()
        } while (rooms.containsKey(id))
        return id
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
            nameToId.remove(room.name)
            connections.remove(roomId)
        } else {
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
                // Connection closed
            }
        }
    }
}

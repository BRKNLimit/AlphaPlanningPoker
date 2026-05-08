package com.teamalpha

import com.teamalpha.models.*
import io.ktor.websocket.*
import kotlinx.serialization.json.Json
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import kotlinx.serialization.encodeToString

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

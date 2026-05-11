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
    
    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }

    suspend fun join(connection: Connection, roomNameOrId: String, username: String, initialChips: Int) {
        val sanitizedRoomId = roomNameOrId.trim()
        if (sanitizedRoomId.isEmpty()) return

        var room = rooms[sanitizedRoomId] ?: rooms[nameToId[sanitizedRoomId]]
        
        if (room == null) {
            val id = generateUniqueId()
            val name = if (sanitizedRoomId.startsWith("#")) "New Room" else sanitizedRoomId
            room = Room(id, name, participants = ConcurrentHashMap())
            rooms[id] = room
            nameToId[name] = id
        }

        val pId = UUID.randomUUID().toString()
        val isHost = room.participants.isEmpty()
        val participant = Participant(pId, username, isHost = isHost, chips = initialChips)
        
        room.participants[pId] = participant
        connection.participantId = pId
        connection.roomId = room.id
        connection.username = username
        
        connections.computeIfAbsent(room.id) { CopyOnWriteArraySet() }.add(connection)
        
        // Send welcome to the joining client
        try {
            connection.session.send(Frame.Text(json.encodeToString(ClientMessage(type = "welcome", yourId = pId))))
        } catch (e: Exception) {
            println("Error sending welcome: ${e.message}")
        }
        
        broadcast(room.id)
    }

    private fun generateUniqueId(): String {
        var id: String
        do {
            id = "#" + (1000..9999).random().toString()
        } while (rooms.containsKey(id))
        return id
    }

    suspend fun vote(roomId: String?, pId: String?, vote: String) {
        if (roomId == null || pId == null) return
        val room = rooms[roomId] ?: return
        val p = room.participants[pId] ?: return
        
        p.vote = vote
        p.isFoil = (1..100).random() <= 5 // 5% Foil chance
        
        broadcast(roomId)
    }

    suspend fun reaction(roomId: String?, reaction: String) {
        if (roomId == null) return
        broadcastRaw(roomId, json.encodeToString(ClientMessage(type = "reaction", emoji = reaction)))
    }

    suspend fun reveal(roomId: String?) {
        if (roomId == null) return
        val room = rooms[roomId] ?: return
        room.isRevealed = true
        
        // Calculate consensus (Exclude Hosts)
        val voters = room.participants.values.filter { !it.isHost }
        val votes = voters.mapNotNull { it.vote }
        
        if (votes.isNotEmpty() && votes.all { it == votes[0] }) {
            room.consensusValue = votes[0]
            broadcastRaw(roomId, json.encodeToString(ClientMessage(type = "cleanSweep", vote = votes[0])))
        } else {
            room.consensusValue = null
        }

        broadcast(roomId)
    }

    suspend fun reset(roomId: String?) {
        if (roomId == null) return
        val room = rooms[roomId] ?: return
        room.isRevealed = false
        room.consensusValue = null
        room.participants.values.forEach { 
            it.vote = null 
            it.currentWager = 0
            it.isAllIn = false
            it.isFoil = false
        }
        broadcast(roomId)
    }

    suspend fun disconnect(connection: Connection) {
        val roomId = connection.roomId
        val pId = connection.participantId
        
        if (roomId.isEmpty() || pId.isEmpty()) return
        
        val room = rooms[roomId] ?: return
        
        room.participants.remove(pId)
        connections[roomId]?.remove(connection)
        
        if (room.participants.isEmpty()) {
            rooms.remove(roomId)
            nameToId.remove(room.name)
            connections.remove(roomId)
        } else {
            if (room.participants.values.none { it.isHost }) {
                val nextHostId = room.participants.keys.firstOrNull()
                if (nextHostId != null) {
                    val p = room.participants[nextHostId]
                    if (p != null) {
                        room.participants[nextHostId] = p.copy(isHost = true)
                    }
                }
            }
            broadcast(roomId)
        }
    }

    private suspend fun broadcast(roomId: String) {
        val room = rooms[roomId] ?: return
        val message = json.encodeToString(room)
        broadcastRaw(roomId, message)
    }

    private suspend fun broadcastRaw(roomId: String, message: String) {
        connections[roomId]?.forEach { 
            try {
                it.session.send(Frame.Text(message))
            } catch (e: Exception) {
                // Connection might be dead
            }
        }
    }
}

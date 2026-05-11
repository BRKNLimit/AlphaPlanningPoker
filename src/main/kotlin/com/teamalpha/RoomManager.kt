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
    private val nameToId = ConcurrentHashMap<String, String>()
    private val connections = ConcurrentHashMap<String, MutableSet<Connection>>()
    
    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun join(connection: Connection, roomNameOrId: String, username: String) {
        val input = roomNameOrId.trim()
        if (input.isEmpty()) return

        // 1. Resolve or Create Room
        var room = rooms[input] ?: rooms[nameToId[input]]
        
        if (room == null) {
            val newId = generateUniqueId()
            val newName = if (input.startsWith("#")) "New Room" else input
            room = Room(id = newId, name = newName, participants = ConcurrentHashMap<String, Participant>())
            
            rooms[newId] = room
            nameToId[newName] = newId
        }

        // 2. Room is now guaranteed non-null and has a non-null ID
        val targetRoomId = room.id
        val newParticipantId = UUID.randomUUID().toString()
        val isFirst = room.participants.isEmpty()
        
        val participant = Participant(id = newParticipantId, name = username, isHost = isFirst)
        
        // 3. Update Maps (Safe casts to ConcurrentHashMap for internal mutation)
        (room.participants as MutableMap<String, Participant>)[newParticipantId] = participant
        
        // 4. Update Connection Object
        connection.participantId = newParticipantId
        connection.roomId = targetRoomId
        connection.username = username
        
        // 5. Manage Connection Pool
        connections.computeIfAbsent(targetRoomId) { CopyOnWriteArraySet() }.add(connection)
        
        // 6. Notify Client
        try {
            val welcomeMsg = json.encodeToString(ClientMessage(type = "welcome", yourId = newParticipantId))
            connection.session.send(Frame.Text(welcomeMsg))
        } catch (e: Exception) {
            println("Welcome Send Fail: ${e.message}")
        }
        
        broadcast(targetRoomId)
    }

    private fun generateUniqueId(): String {
        var id: String
        do {
            id = "#" + (1000..9999).random().toString()
        } while (rooms.containsKey(id))
        return id
    }

    suspend fun vote(rId: String?, pId: String?, voteValue: String) {
        if (rId.isNullOrEmpty() || pId.isNullOrEmpty()) return
        
        val room = rooms[rId] ?: return
        val p = room.participants[pId] ?: return
        
        p.vote = voteValue
        p.isFoil = (1..100).random() <= 5
        
        broadcast(rId)
    }

    suspend fun reaction(rId: String?, emoji: String) {
        if (rId.isNullOrEmpty()) return
        val msg = json.encodeToString(ClientMessage(type = "reaction", emoji = emoji))
        broadcastRaw(rId, msg)
    }

    suspend fun reveal(rId: String?) {
        if (rId.isNullOrEmpty()) return
        val room = rooms[rId] ?: return
        room.isRevealed = true
        
        val voters = room.participants.values.filter { !it.isHost }
        val votes = voters.mapNotNull { it.vote }
        
        if (votes.isNotEmpty() && votes.all { it == votes[0] }) {
            room.consensusValue = votes[0]
            val sweepMsg = json.encodeToString(ClientMessage(type = "cleanSweep", vote = votes[0]))
            broadcastRaw(rId, sweepMsg)
        } else {
            room.consensusValue = null
        }

        broadcast(rId)
    }

    suspend fun reset(rId: String?) {
        if (rId.isNullOrEmpty()) return
        val room = rooms[rId] ?: return
        room.isRevealed = false
        room.consensusValue = null
        room.participants.values.forEach { 
            it.vote = null 
            it.isFoil = false
        }
        broadcast(rId)
    }

    suspend fun disconnect(connection: Connection) {
        val rId = connection.roomId
        val pId = connection.participantId
        
        if (rId.isEmpty() || pId.isEmpty()) return
        
        val room = rooms[rId] ?: return
        
        (room.participants as MutableMap<String, Participant>).remove(pId)
        connections[rId]?.remove(connection)
        
        if (room.participants.isEmpty()) {
            rooms.remove(rId)
            nameToId.remove(room.name)
            connections.remove(rId)
        } else {
            if (room.participants.values.none { it.isHost }) {
                val nextHostId = room.participants.keys.firstOrNull()
                if (nextHostId != null) {
                    val p = room.participants[nextHostId]
                    if (p != null) {
                        (room.participants as MutableMap<String, Participant>)[nextHostId] = p.copy(isHost = true)
                    }
                }
            }
            broadcast(rId)
        }
    }

    private suspend fun broadcast(rId: String) {
        if (rId.isEmpty()) return
        val room = rooms[rId] ?: return
        try {
            broadcastRaw(rId, json.encodeToString(room))
        } catch (e: Exception) {
            println("Broadcast Serial Fail: ${e.message}")
        }
    }

    private suspend fun broadcastRaw(rId: String, message: String) {
        if (rId.isEmpty()) return
        connections[rId]?.forEach { 
            try {
                it.session.send(Frame.Text(message))
            } catch (e: Exception) {}
        }
    }
}

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

    suspend fun join(connection: Connection, roomNameOrId: String, username: String, initialChips: Int) {
        var room = rooms[roomNameOrId] ?: rooms[nameToId[roomNameOrId]]
        
        if (room == null) {
            val id = generateUniqueId()
            val name = if (roomNameOrId.startsWith("#")) "New Room" else roomNameOrId
            room = Room(id, name)
            rooms[id] = room
            nameToId[name] = id
        }

        val pId = UUID.randomUUID().toString()
        val isHost = room.participants.isEmpty()
        val participant = Participant(pId, username, isHost = isHost, chips = initialChips)
        
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

    suspend fun vote(roomId: String, pId: String, vote: String, wager: Int, isAllIn: Boolean) {
        val room = rooms[roomId] ?: return
        val p = room.participants[pId] ?: return
        
        p.vote = vote
        p.currentWager = wager
        p.isAllIn = isAllIn
        p.isFoil = (1..100).random() == 1 // 1% Foil chance
        
        broadcast(roomId)
        
        if (isAllIn) {
            broadcastRaw(roomId, """{"type":"allInSlam","pId":"$pId"}""")
        }
    }

    suspend fun reaction(roomId: String, reaction: String) {
        broadcastRaw(roomId, """{"type":"reaction","emoji":"$reaction"}""")
    }

    suspend fun reveal(roomId: String) {
        val room = rooms[roomId] ?: return
        room.isRevealed = true
        
        // Calculate consensus and payouts
        val votes = room.participants.values.mapNotNull { it.vote }
        if (votes.isNotEmpty() && votes.all { it == votes[0] }) {
            room.consensusValue = votes[0]
            broadcastRaw(roomId, """{"type":"cleanSweep","value":"${votes[0]}"}""")
        } else {
            room.consensusValue = null
        }

        // Handle Gambling Payouts (simple version: most frequent vote wins)
        val winningVote = votes.groupBy { it }.maxByOrNull { it.value.size }?.key
        room.participants.values.forEach { p ->
            if (p.vote == winningVote && winningVote != null) {
                p.chips += p.currentWager * 2
            } else {
                p.chips -= p.currentWager
            }
        }
        
        broadcast(roomId)
    }

    suspend fun reset(roomId: String) {
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
        broadcastRaw(roomId, message)
    }

    private suspend fun broadcastRaw(roomId: String, message: String) {
        connections[roomId]?.forEach { 
            try {
                it.session.send(Frame.Text(message))
            } catch (e: Exception) {}
        }
    }
}

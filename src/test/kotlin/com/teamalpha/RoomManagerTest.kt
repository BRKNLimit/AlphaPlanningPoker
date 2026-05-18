package com.teamalpha

import io.ktor.client.plugins.websocket.*
import io.ktor.serialization.kotlinx.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlin.test.*
import com.teamalpha.models.*
import io.ktor.client.request.*
import io.ktor.websocket.*

class RoomManagerTest {
    @Test
    fun testRoomCreationAndJoin() = testApplication {
        application {
            module()
        }
        val client = createClient {
            install(WebSockets) {
                contentConverter = KotlinxWebsocketSerializationConverter(Json)
            }
        }

        client.webSocket("/poker") {
            sendSerialized(ClientMessage(type = "join", username = "HostPlayer", roomId = ""))
            val welcome = receiveDeserialized<ClientMessage>()
            assertEquals("welcome", welcome.type)
            assertNotNull(welcome.yourId)
            
            // Should also receive room update
            val roomUpdate = receiveDeserialized<Room>()
            assertEquals("Alpha Session", roomUpdate.name)
            assertEquals(1, roomUpdate.participants.size)
            assertTrue(roomUpdate.participants.values.first().isHost)
        }
    }

    @Test
    fun testVotingAndConsensus() = testApplication {
        application {
            module()
        }
        val client = createClient {
            install(WebSockets) {
                contentConverter = KotlinxWebsocketSerializationConverter(Json)
            }
        }

        // Host
        val host = client.webSocketSession("/poker")
        host.sendSerialized(ClientMessage(type = "join", username = "Host", roomId = ""))
        host.receiveDeserialized<ClientMessage>() // welcome
        val initialRoom = host.receiveDeserialized<Room>()
        val roomId = initialRoom.id

        // Player 1
        val p1 = client.webSocketSession("/poker")
        p1.sendSerialized(ClientMessage(type = "join", username = "P1", roomId = roomId))
        p1.receiveDeserialized<ClientMessage>() // welcome
        p1.receiveDeserialized<Room>() // Room update for P1 join
        host.receiveDeserialized<Room>() // Room update for P1 join (host sees it too)

        // Vote
        p1.sendSerialized(ClientMessage(type = "vote", vote = "8"))
        p1.receiveDeserialized<Room>() // My own vote update
        host.receiveDeserialized<Room>() // Host sees vote update

        // Reveal
        host.sendSerialized(ClientMessage(type = "reveal"))
        
        // Clean sweep message
        val sweep = p1.receiveDeserialized<ClientMessage>()
        assertEquals("cleanSweep", sweep.type)
        assertEquals("8", sweep.vote)

        // Room update (revealed)
        val revealedRoom = p1.receiveDeserialized<Room>()
        assertTrue(revealedRoom.isRevealed)
        assertEquals("8", revealedRoom.consensusValue)
    }
}

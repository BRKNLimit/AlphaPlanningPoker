package com.teamalpha.models

import kotlinx.serialization.Serializable

@Serializable
sealed class Message {
    @Serializable
    data class Join(val roomId: String, val name: String) : Message()
    @Serializable
    data class Vote(val value: String) : Message()
    @Serializable
    object Reveal : Message()
    @Serializable
    object Reset : Message()
    @Serializable
    data class Update(val room: Room) : Message()
    @Serializable
    data class Error(val message: String) : Message()
}

package com.teamalpha.models

import kotlinx.serialization.Serializable

@Serializable
data class Participant(
    val id: String,
    val name: String,
    var vote: String? = null,
    val isHost: Boolean = false
)

@Serializable
data class Room(
    val id: String,
    val participants: MutableMap<String, Participant> = mutableMapOf(),
    var isRevealed: Boolean = false
)

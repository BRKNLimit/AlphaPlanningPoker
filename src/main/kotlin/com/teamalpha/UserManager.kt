package com.teamalpha

import com.teamalpha.models.User
import com.teamalpha.models.UserDTO
import com.teamalpha.models.UserStatus
import java.util.concurrent.ConcurrentHashMap

class UserManager {
    private val users = ConcurrentHashMap<String, User>()

    init {
        users["admin"] = User("Admin", "Admin1234", UserStatus.APPROVED, isAdmin = true)
    }

    fun register(username: String, passwordHash: String): Boolean {
        val lower = username.lowercase().trim()
        if (users.containsKey(lower)) return false
        users[lower] = User(username, passwordHash, UserStatus.PENDING)
        return true
    }

    fun authenticate(username: String, passwordHash: String): User? {
        val lower = username.lowercase().trim()
        val user = users[lower] ?: return null
        return if (user.passwordHash == passwordHash.trim()) user else null
    }

    fun getAllUsersDTO(): List<UserDTO> {
        return users.values.map { UserDTO(it.username, it.status, it.isAdmin) }
    }

    fun approveUser(username: String) {
        users[username.lowercase().trim()]?.status = UserStatus.APPROVED
    }

    fun getUser(username: String): User? {
        return users[username.lowercase().trim()]
    }
}

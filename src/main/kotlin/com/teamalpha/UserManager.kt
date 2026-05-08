package com.teamalpha

import com.teamalpha.models.User
import com.teamalpha.models.UserStatus
import java.util.concurrent.ConcurrentHashMap

class UserManager {
    private val users = ConcurrentHashMap<String, User>()

    init {
        // Seed Admin
        users["admin"] = User("Admin", "Admin1234", UserStatus.APPROVED, isAdmin = true)
    }

    fun register(username: String, passwordHash: String): Boolean {
        val lowerUser = username.lowercase()
        if (users.containsKey(lowerUser)) return false
        users[lowerUser] = User(username, passwordHash, UserStatus.PENDING)
        return true
    }

    fun authenticate(username: String, passwordHash: String): User? {
        val user = users[username.lowercase()] ?: return null
        if (user.passwordHash == passwordHash) return user
        return null
    }

    fun getPendingUsers(): List<User> {
        return users.values.filter { it.status == UserStatus.PENDING }
    }

    fun approveUser(username: String) {
        users[username.lowercase()]?.status = UserStatus.APPROVED
    }

    fun rejectUser(username: String) {
        users.remove(username.lowercase())
    }

    fun getAllUsers(): List<User> {
        return users.values.toList()
    }
}

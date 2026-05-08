package com.teamalpha

import com.teamalpha.models.User
import com.teamalpha.models.UserStatus
import java.util.concurrent.ConcurrentHashMap

class UserManager {
    private val users = ConcurrentHashMap<String, User>()

    init {
        // Seed Admin
        users["Admin"] = User("Admin", "Admin1234", UserStatus.APPROVED, isAdmin = true)
    }

    fun register(username: String, passwordHash: String): Boolean {
        if (users.containsKey(username)) return false
        users[username] = User(username, passwordHash, UserStatus.PENDING)
        return true
    }

    fun authenticate(username: String, passwordHash: String): User? {
        val user = users[username] ?: return null
        if (user.passwordHash == passwordHash) return user
        return null
    }

    fun getPendingUsers(): List<User> {
        return users.values.filter { it.status == UserStatus.PENDING }
    }

    fun approveUser(username: String) {
        users[username]?.status = UserStatus.APPROVED
    }

    fun rejectUser(username: String) {
        users.remove(username)
    }

    fun getAllUsers(): List<User> {
        return users.values.toList()
    }
}

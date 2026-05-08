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
        val lower = username.lowercase().trim()
        val pass = passwordHash.trim()
        println("AUTH ATTEMPT: User='$username' (lower='$lower') Pass='$pass'")
        val user = users[lower]
        if (user == null) {
            println("AUTH FAIL: User '$lower' not found in ${users.keys}")
            return null
        }
        if (user.passwordHash == pass) {
            println("AUTH SUCCESS: $lower")
            return user
        }
        println("AUTH FAIL: Password mismatch for '$lower'. Expected '${user.passwordHash}' but got '$pass'")
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

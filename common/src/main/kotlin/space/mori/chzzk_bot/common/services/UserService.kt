package space.mori.chzzk_bot.common.services

import org.jetbrains.exposed.dao.load
import org.jetbrains.exposed.sql.transactions.transaction
import space.mori.chzzk_bot.common.models.User
import space.mori.chzzk_bot.common.models.Users

object UserService {
    fun saveUser(username: String, naverId: String): User {
        return transaction {
            User.new {
                this.username = username
                this.naverId = naverId
            }
        }
    }

    fun updateUser(user: User, chzzkId: String, username: String): User {
        return transaction {
            user.token = chzzkId
            user.username = username

            user
        }
    }

    fun updateUser(user: User, discordID: Long): User {
        return transaction {
            user.discord = discordID
            user.load(User::subordinates, User::managers)
            user
        }
    }

    fun getUser(id: Int): User? {
        return transaction {
            val user = User.find{ Users.id eq id }.firstOrNull()
            user?.load(User::subordinates, User::managers)
            user
        }
    }

    fun getUser(discordID: Long): User? {
        return transaction {
            val user = User.find{ Users.discord eq discordID }.firstOrNull()
            user?.load(User::subordinates, User::managers)
            user
        }
    }

    fun getUser(chzzkID: String): User? {
        return transaction {
            val user = User.find{ Users.token eq chzzkID }.firstOrNull()
            user?.load(User::subordinates, User::managers)
            user
        }
    }

    fun getUserWithGuildId(discordGuildId: Long): User? {
        return transaction {
            val user = User.find { Users.liveAlertGuild eq discordGuildId }.firstOrNull()
            user?.load(User::subordinates, User::managers)
            user
        }
    }

    fun getUserWithNaverId(naverId: String): User? {
        return transaction {
            val user = User.find{ Users.naverId eq naverId }.firstOrNull()
            user?.load(User::subordinates, User::managers)
            user
        }
    }

    fun getAllUsers(): List<User> {
        return transaction {
            User.all().toList()
        }
    }

    fun updateLiveAlert(user: User, guildId: Long, channelId: Long, alertMessage: String?): User {
        return transaction {
            user.liveAlertGuild = guildId
            user.liveAlertChannel = channelId
            user.liveAlertMessage = alertMessage ?: ""

            user.load(User::subordinates, User::managers)

            user
        }
    }
}
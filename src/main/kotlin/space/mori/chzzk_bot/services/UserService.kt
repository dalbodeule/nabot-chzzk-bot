package space.mori.chzzk_bot.services

import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import space.mori.chzzk_bot.models.User
import space.mori.chzzk_bot.models.Users

object UserService {
    fun saveUser(username: String, token: String, discordID: Long): User {
        return transaction {
            User.new {
                this.username = username
                this.token = token
                this.discord = discordID
            }
        }
    }

    fun getUser(id: Int): User? {
        return transaction {
            User.findById(id)
        }
    }

    fun getUser(discordID: Long): User? {
        return transaction {
            val users = User.find(Users.discord eq discordID)

            users.firstOrNull()
        }
    }

    fun getUser(chzzkID: String): User? {
        return transaction {
            val users = User.find(Users.token eq chzzkID)

            users.firstOrNull()
        }
    }

    fun getAllUsers(): List<User> {
        return transaction {
            User.all().toList()
        }
    }

    fun updateLiveAlert(id: Int, guildId: Long, channelId: Long, alertMessage: String?): User {
        return transaction {
            val updated = Users.update({ Users.id eq id }) {
                it[liveAlertGuild] = guildId
                it[liveAlertChannel] = channelId
                it[liveAlertMessage] = alertMessage ?: ""
            }

            if(updated == 0) throw RuntimeException("User not found! $id")
            val users = User.find { Users.id eq id }

            return@transaction users.first()
        }
    }
}
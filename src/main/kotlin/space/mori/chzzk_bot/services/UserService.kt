package space.mori.chzzk_bot.services

import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import space.mori.chzzk_bot.models.User
import space.mori.chzzk_bot.models.Users

object UserService {
    fun saveUser(username: String, token: String, discordID: Long): User {
        return transaction {
            return@transaction User.new {
                this.username = username
                this.token = token
                this.discord = discordID
            }
        }
    }

    fun getUser(id: Int): User? {
        return transaction {
            return@transaction User.findById(id)
        }
    }

    fun getUser(discordID: Long): User? {
        return transaction {
            val users = User.find(Users.discord eq discordID)

            return@transaction users.firstOrNull()
        }
    }

    fun getUser(chzzkID: String): User? {
        return transaction {
            val users = User.find(Users.token eq chzzkID)

            return@transaction users.firstOrNull()
        }
    }

    fun getAllUsers(): List<User> {
        return transaction {
            return@transaction User.all().toList()
        }
    }
}
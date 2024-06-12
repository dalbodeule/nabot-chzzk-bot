package space.mori.chzzk_bot.services

import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import space.mori.chzzk_bot.models.User
import space.mori.chzzk_bot.models.Users

class UserService {
    fun saveUser(user: User) {
        User.new {
            username = user.username
            token = user.token
            discord = user.discord
        }
    }

    fun getUser(id: Int): User? {
        return User.findById(id)
    }

    fun getUser(discordID: Long): User? {
        val users = User.find(Users.discord eq discordID)

        return users.firstOrNull()
    }
}
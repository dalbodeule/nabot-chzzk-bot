package space.mori.chzzk_bot.common.services

import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import space.mori.chzzk_bot.common.models.SongConfig
import space.mori.chzzk_bot.common.models.SongConfigs
import space.mori.chzzk_bot.common.models.User

object SongConfigService {
    private fun initConfig(user: User): SongConfig {
        return transaction {
            SongConfig.new {
                this.user = user
            }
        }
    }

    fun getConfig(user: User): SongConfig {
        return transaction {
            var songConfig = SongConfig.find(SongConfigs.user eq user.id).firstOrNull()
            if (songConfig == null) {
                songConfig = initConfig(user)
            }
            songConfig
        }

    }

    fun getConfig(token: String): SongConfig? {
        return transaction {
            SongConfig.find(SongConfigs.token eq token).firstOrNull()
        }
    }
    fun getUserByToken(token: String): User? {
        return transaction {
            val songConfig = SongConfig.find(SongConfigs.token eq token).firstOrNull()
            if(songConfig == null) null
            else UserService.getUser(songConfig.user.discord)
        }
    }

    fun updatePersonalLimit(user: User, limit: Int): SongConfig {
        return transaction {
            var songConfig = SongConfig.find(SongConfigs.user eq user.id).firstOrNull()
            if (songConfig == null) {
                songConfig = initConfig(user)
            }
            songConfig.personalLimit = limit
            songConfig
        }
    }
    fun updateQueueLimit(user: User, limit: Int): SongConfig {
        return transaction {
            var songConfig = SongConfig.find(SongConfigs.user eq user.id).firstOrNull()
            if (songConfig == null) {
                songConfig = initConfig(user)
            }
            songConfig.queueLimit = limit
            songConfig
        }
    }

    fun updateSession(user: User, token: String?, password: String?): SongConfig {
        return transaction {
            var songConfig = SongConfig.find(SongConfigs.user eq user.id).firstOrNull()
            if (songConfig == null) {
                songConfig = initConfig(user)
            }
            songConfig.token = token
            songConfig.password = password

            songConfig
        }
    }

    fun updateStreamerOnly(user: User, config: Boolean): SongConfig {
        return transaction {
            var songConfig = SongConfig.find(SongConfigs.user eq user.id).firstOrNull()
            if (songConfig == null) {
                songConfig = initConfig(user)
            }
            songConfig.streamerOnly = config

            songConfig
        }
    }
}
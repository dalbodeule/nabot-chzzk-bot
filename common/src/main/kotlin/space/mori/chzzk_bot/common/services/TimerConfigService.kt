package space.mori.chzzk_bot.common.services

import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import space.mori.chzzk_bot.common.events.TimerType
import space.mori.chzzk_bot.common.models.TimerConfig
import space.mori.chzzk_bot.common.models.TimerConfigs
import space.mori.chzzk_bot.common.models.User

object TimerConfigService {
    fun saveConfig(user: User, timerConfig: TimerType) {
        return transaction {
            TimerConfig.new {
                this.user = user
                this.option = timerConfig.value
            }
        }
    }

    fun updateConfig(user: User, timerConfig: TimerType) {
        return transaction {
            val updated = TimerConfigs.update({
                TimerConfigs.user eq user.id
            }) {
                it[option] = timerConfig.value
            }

            if (updated == 0) throw RuntimeException("TimerConfig not found! ${user.username}")

            TimerConfig.find { TimerConfigs.user eq user.id }.first()
        }
    }

    fun getConfig(user: User): TimerConfig? {
        return transaction {
            TimerConfig.find(TimerConfigs.user eq user.id).firstOrNull()
        }
    }

    fun saveOrUpdateConfig(user: User, timerConfig: TimerType) {
        return if (getConfig(user) == null) {
            saveConfig(user, timerConfig)
        } else {
            updateConfig(user, timerConfig)
        }
    }
}
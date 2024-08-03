package space.mori.chzzk_bot.common.services

import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import space.mori.chzzk_bot.common.models.LiveStatus
import space.mori.chzzk_bot.common.models.LiveStatuses
import space.mori.chzzk_bot.common.models.User

object LiveStatusService {
    fun updateOrCreate(user: User, status: Boolean): LiveStatus {
        return transaction {
            return@transaction when(val liveStatus = LiveStatus.find(LiveStatuses.user eq user.id).firstOrNull()) {
                null -> LiveStatus.new {
                    this.user = user
                    this.status = status
                }
                else -> {
                    liveStatus.status = status
                    liveStatus
                }
            }
        }
    }

    fun getLiveStatus(user: User): LiveStatus? {
        return transaction {
            LiveStatus.find(LiveStatuses.user eq user.id).firstOrNull()
        }
    }
}
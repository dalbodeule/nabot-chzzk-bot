package space.mori.chzzk_bot.models

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.javatime.date

object DailyCounters: IntIdTable("daily_counters") {
    val name = varchar("name", 255)
    val userId = varchar("user_id", 64)
    val value = integer("value")
    val updatedAt = date("updated_at")
    val user = reference("streamer", Users)
}

class DailyCounter(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<DailyCounter>(DailyCounters)

    var name by DailyCounters.name
    var userId by DailyCounters.userId
    var value by DailyCounters.value
    var updatedAt by DailyCounters.updatedAt
    var user by User referencedOn DailyCounters.user
}
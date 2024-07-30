package space.mori.chzzk_bot.common.models

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable

object PersonalCounters: IntIdTable("personal_counters") {
    val name = varchar("name", 255)
    val userId = varchar("user_id", 64)
    val value = integer("value")
    val user = reference("streamer", Users)
}

class PersonalCounter(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<PersonalCounter>(PersonalCounters)

    var name by PersonalCounters.name
    var userId by PersonalCounters.userId
    var value by PersonalCounters.value
    var user by User referencedOn PersonalCounters.user
}
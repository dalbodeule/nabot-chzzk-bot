package space.mori.chzzk_bot.models

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable

object Counters: IntIdTable("counters") {
    val name = varchar("name", 255)
    val value = integer("value")
    val user = reference("streamer", Users)
}

class Counter(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<Counter>(Counters)

    var name by Counters.name
    var value by Counters.value
    var user by User referencedOn Counters.user
}
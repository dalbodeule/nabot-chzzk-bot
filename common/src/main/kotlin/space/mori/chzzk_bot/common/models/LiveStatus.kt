package space.mori.chzzk_bot.common.models

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption

object LiveStatuses: IntIdTable("live_statuses") {
    val user = reference("user", Users, onDelete = ReferenceOption.CASCADE)
    val status = bool("status")
}

class LiveStatus(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<LiveStatus>(LiveStatuses)

    var user by User referencedOn LiveStatuses.user
    var status by LiveStatuses.status
}
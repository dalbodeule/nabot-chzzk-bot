package space.mori.chzzk_bot.common.models

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption

object SongConfigs: IntIdTable("song_config") {
    val user = reference("user", Users, onDelete = ReferenceOption.CASCADE)
    val option = integer("option")
}
class SongConfig(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<TimerConfig>(TimerConfigs)

    var user by User referencedOn TimerConfigs.user
    var option by TimerConfigs.option
}
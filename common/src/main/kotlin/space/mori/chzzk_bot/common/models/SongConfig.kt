package space.mori.chzzk_bot.common.models

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption

object SongConfigs: IntIdTable("song_config") {
    val user = reference("user", Users, onDelete = ReferenceOption.CASCADE)
    val token = varchar("token", 64).nullable()
    val streamerOnly = bool("streamer_only").default(false)
    val queueLimit = integer("queue_limit").default(50)
    val personalLimit = integer("personal_limit").default(5)
}
class SongConfig(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<SongConfig>(SongConfigs)

    var user by User referencedOn SongConfigs.user
    var token by SongConfigs.token
    var streamerOnly by SongConfigs.streamerOnly
    var queueLimit by SongConfigs.queueLimit
    var personalLimit by SongConfigs.personalLimit
}
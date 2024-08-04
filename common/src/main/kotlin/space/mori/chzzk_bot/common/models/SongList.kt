package space.mori.chzzk_bot.common.models

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.javatime.datetime

object SongLists: IntIdTable("song_list") {
    val user = reference("user", Users)
    val uid = varchar("uid", 64)
    val url = varchar("url", 128)
    val name = text("name")
    val author = text("author")
    val time = integer("time")
    val created_at = datetime("created_at")
}

class SongList(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<SongList>(SongLists)

    var url by SongLists.url
    var name by SongLists.name
    var author by SongLists.author
    var time by SongLists.time
    var created_at by SongLists.created_at

    var user by User referencedOn SongLists.user
    var uid by SongLists.uid
}
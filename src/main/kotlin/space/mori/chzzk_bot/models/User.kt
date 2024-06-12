package space.mori.chzzk_bot.models

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable


object Users: IntIdTable("users") {
    val username = varchar("username", 255)
    val token = varchar("token", 64)
    val discord = long("discord")
}

class User(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<User>(Users)

    var username by Users.username
    var token by Users.token
    var discord by Users.discord
}
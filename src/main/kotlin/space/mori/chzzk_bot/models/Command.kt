package space.mori.chzzk_bot.models

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption

object Commands: IntIdTable("commands") {
    val user = reference("user", Users, onDelete = ReferenceOption.CASCADE)
    val command = varchar("command", 255)
    val content = text("content")
}

class Command(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<Command>(Commands)

    var user by User referencedOn Commands.user
    var command by Commands.command
    var content by Commands.content
}
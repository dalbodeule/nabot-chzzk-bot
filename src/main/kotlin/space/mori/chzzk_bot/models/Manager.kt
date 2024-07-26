package space.mori.chzzk_bot.models

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable

object Managers: IntIdTable("manager") {
    val user = reference("user", Users)
    val managerId = long("manager_id")
    val discordGuildId = long("discord_guild_id")
    var lastUserName = varchar("last_user_name", 255).nullable()
}

class Manager(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<Manager>(Managers)

    var user by User referencedOn Managers.user
    var managerId by Managers.managerId
    var discordGuildId by Managers.discordGuildId
    var lastUserName by Managers.lastUserName
}
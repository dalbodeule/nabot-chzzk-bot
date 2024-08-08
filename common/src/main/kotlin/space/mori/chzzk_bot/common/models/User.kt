package space.mori.chzzk_bot.common.models

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable


object Users: IntIdTable("users") {
    val username = varchar("username", 255)
    val token = varchar("token", 64).nullable()
    val discord = long("discord").nullable()
    val naverId = varchar("naver_id", 128)
    val liveAlertGuild = long("live_alert_guild").nullable()
    val liveAlertChannel = long("live_alert_channel").nullable()
    val liveAlertMessage = text("live_alert_message").nullable()
}

class User(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<User>(Users)

    var username by Users.username
    var token by Users.token
    var discord by Users.discord
    var naverId by Users.naverId
    var liveAlertGuild by Users.liveAlertGuild
    var liveAlertChannel by Users.liveAlertChannel
    var liveAlertMessage by Users.liveAlertMessage
}
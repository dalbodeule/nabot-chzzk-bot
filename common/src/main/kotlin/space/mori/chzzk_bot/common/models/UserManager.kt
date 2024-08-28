package space.mori.chzzk_bot.common.models

import org.jetbrains.exposed.sql.Table

object UserManagers: Table("user_managers") {
    val user = reference("user_id", Users)
    val manager = reference("manager_id", Users)
}
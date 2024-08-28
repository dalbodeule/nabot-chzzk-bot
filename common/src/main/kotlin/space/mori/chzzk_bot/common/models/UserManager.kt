package space.mori.chzzk_bot.common.models

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption

object UserManagers: IntIdTable("user_managers") {
    val user = reference("user_id", Users, ReferenceOption.CASCADE)
    val manager = reference("manager_id", Users, ReferenceOption.CASCADE)
}
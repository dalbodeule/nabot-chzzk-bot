package space.mori.chzzk_bot.webserver

import io.ktor.server.sessions.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import space.mori.chzzk_bot.common.models.Session
import space.mori.chzzk_bot.common.models.Sessions as SessionTable

class MariadbSessionStorage: SessionStorage {
    override suspend fun invalidate(id: String) {
        return transaction {
            val session = Session.find(
                SessionTable.key eq id
            ).firstOrNull()
            session?.delete()
        }
    }

    override suspend fun read(id: String): String {
        return transaction {
            val session = Session.find(SessionTable.key eq id).firstOrNull()
                ?: throw NoSuchElementException("Session $id not found")
            session.value
        }
    }

    override suspend fun write(id: String, value: String) {
        return transaction {
            val session = Session.find(SessionTable.key eq id).firstOrNull()

            if (session == null) {
                Session.new {
                    this.key = id
                    this.value = value
                }
            } else {
                session.value = value
            }
        }
    }
}
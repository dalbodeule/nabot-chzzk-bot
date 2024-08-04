package space.mori.chzzk_bot.common.services

import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import space.mori.chzzk_bot.common.models.SongList
import space.mori.chzzk_bot.common.models.SongLists
import space.mori.chzzk_bot.common.models.User

object SongListService {
    fun saveSong(user: User, uid: String, url: String, name: String, author: String, time: Int, reqName: String) {
        return transaction {
            SongList.new {
                this.user = user
                this.uid = uid
                this.url = url
                this.name = name
                this.author = author
                this.time = time
                this.reqName = reqName
            }
        }
    }

    fun getSong(user: User, uid: String): List<SongList> {
        return transaction {
            SongList.find(
                (SongLists.user eq user.id) and
                    (SongLists.uid eq uid)
            ).toList()
        }
    }

    fun getSong(user: User): List<SongList> {
        return transaction {
            SongList.find(SongLists.user eq user.id).toList().sortedBy { it.created_at }
        }
    }

    fun deleteSong(user: User, uid: String, name: String): SongList {
        return transaction {
            val songRow = SongList.find(
                (SongLists.user eq user.id) and
                    (SongLists.uid eq uid) and
                    (SongLists.name eq name)
            ).firstOrNull()

            songRow ?: throw RuntimeException("Song not found! ${user.username} / $uid / $name")

            songRow.delete()
            songRow
        }
    }

    fun deleteUser(user: User): Boolean {
        return transaction {
            val songRow = SongList.find(SongLists.user eq user.id).toList()

            songRow.forEach { it.delete() }
            true
        }
    }
}
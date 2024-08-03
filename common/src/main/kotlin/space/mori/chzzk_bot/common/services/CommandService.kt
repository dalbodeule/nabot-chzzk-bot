package space.mori.chzzk_bot.common.services

import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import space.mori.chzzk_bot.common.models.Command
import space.mori.chzzk_bot.common.models.Commands
import space.mori.chzzk_bot.common.models.User

object CommandService {
    fun saveCommand(user: User, command: String, content: String, failContent: String): Command {
        return transaction {
            Command.new {
                this.user = user
                this.command = command
                this.content = content
                this.failContent = failContent
            }
        }
    }

    fun removeCommand(user: User, command: String): Command? {
        return transaction {
            val commandRow = Command.find(Commands.user eq user.id and(Commands.command eq command)).firstOrNull()

            commandRow ?: throw RuntimeException("Command not found! $command")
            commandRow.delete()

           commandRow
        }
    }

    fun updateCommand(user: User, command: String, content: String, failContent: String): Command {
        return transaction {
            val updated = Commands.update({Commands.user eq user.id and(Commands.command eq command)}) {
                it[Commands.content] = content
                it[Commands.failContent] = failContent
            }

            if(updated == 0) throw RuntimeException("Command not found! $command")

            Command.find(Commands.user eq user.id and(Commands.command eq command)).first()
        }
    }

    fun getCommand(id: Int): Command? {
        return transaction {
            Command.findById(id)
        }
    }

    fun getCommands(user: User): List<Command> {
        return transaction {
            Command.find(Commands.user eq user.id)
                .toList()
        }
    }
}
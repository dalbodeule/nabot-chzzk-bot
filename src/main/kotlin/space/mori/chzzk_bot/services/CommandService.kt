package space.mori.chzzk_bot.services

import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import space.mori.chzzk_bot.models.Command
import space.mori.chzzk_bot.models.Commands
import space.mori.chzzk_bot.models.User

object CommandService {
    fun saveCommand(user: User, command: String, content: String): Command {
        return transaction {
            return@transaction Command.new {
                this.user = user
                this.command = command
                this.content = content
            }
        }
    }

    fun removeCommand(user: User, command: String): Command? {
        return transaction {
            val commandRow = Command.find(Commands.user eq user.id and(Commands.command eq command)).firstOrNull()

            commandRow ?: throw RuntimeException("Command not found! $command")
            commandRow.delete()

            return@transaction commandRow
        }
    }

    fun getCommand(id: Int): Command? {
        return transaction {
            return@transaction Command.findById(id)
        }
    }

    fun getCommands(user: User): List<Command> {
        return transaction {
            return@transaction Command.find(Commands.user eq user.id)
                .toList()
        }
    }
}
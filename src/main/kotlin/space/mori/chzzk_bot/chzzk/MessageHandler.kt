package space.mori.chzzk_bot.chzzk

import org.slf4j.Logger
import space.mori.chzzk_bot.models.User
import space.mori.chzzk_bot.services.CommandService
import space.mori.chzzk_bot.services.CounterService
import space.mori.chzzk_bot.services.UserService
import xyz.r2turntrue.chzzk4j.chat.ChatMessage
import xyz.r2turntrue.chzzk4j.chat.ChzzkChat
import xyz.r2turntrue.chzzk4j.types.channel.ChzzkChannel

class MessageHandler(
    private val channel: ChzzkChannel,
    private val logger: Logger,
    private val listener: ChzzkChat
) {
    private val commands = mutableMapOf<String, (msg: ChatMessage, user: User) -> Unit>()

    private val counterPattern = Regex("<counter:([^>]+)>")
    private val personalCounterPattern = Regex("<counter_personal:([^>]+)>")
    private val dailyCounterPattern = Regex("<daily_counter:([^>]+)>")
    private val namePattern = Regex("<name>")

    init {
        reloadCommand()
    }

    internal fun reloadCommand() {
        val user = UserService.getUser(channel.channelId)
            ?: throw RuntimeException("User not found. it's bug? ${channel.channelName} - ${channel.channelId}")
        val commands = CommandService.getCommands(user)

        commands.map {
            this.commands.put(it.command.lowercase()) { msg, user ->
                logger.debug("${channel.channelName} - ${it.command} - ${it.content}/${it.failContent}")

                val result = replaceCounters(Pair(it.content, it.failContent), user, msg.userId, msg.profile?.nickname ?: "")
                listener.sendChat(result)
            }
        }
    }

    internal fun handle(msg: ChatMessage, user: User) {
        val commandKey = msg.content.split(' ')[0]

        commands[commandKey.lowercase()]?.let { it(msg, user) }
    }

    private fun replaceCounters(chat: Pair<String, String>, user: User, userId: String, userName: String): String {
        var result = chat.first
        var isFail = false

        result = counterPattern.replace(result) {
            val name = it.groupValues[1]
            CounterService.updateCounterValue(name, 1, user).toString()
        }

        result = personalCounterPattern.replace(result) {
            val name = it.groupValues[1]
            CounterService.updatePersonalCounterValue(name, userId, 1, user).toString()
        }

        result = dailyCounterPattern.replace(result) {
            val name = it.groupValues[1]
            val dailyCounter = CounterService.getDailyCounterValue(name, userId, user)

            return@replace if(dailyCounter.second)
                CounterService.updateDailyCounterValue(name, userId, 1, user).first.toString()
            else {
                isFail = true
                dailyCounter.first.toString()
            }
        }

        if(isFail) {
            result = chat.second
            result = dailyCounterPattern.replace(result) {
                val name = it.groupValues[1]
                val dailyCounter = CounterService.getDailyCounterValue(name, userId, user)

                dailyCounter.first.toString()
            }
        }

        result = namePattern.replace(result, userName)

        return result
    }
}
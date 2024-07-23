package space.mori.chzzk_bot.chzzk

import org.slf4j.Logger
import space.mori.chzzk_bot.models.User
import space.mori.chzzk_bot.services.CommandService
import space.mori.chzzk_bot.services.CounterService
import space.mori.chzzk_bot.services.UserService
import xyz.r2turntrue.chzzk4j.chat.ChatMessage
import xyz.r2turntrue.chzzk4j.chat.ChzzkChat
import xyz.r2turntrue.chzzk4j.types.channel.ChzzkChannel
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit


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
    private val followPattern = Regex("<following>")
    private val daysPattern = """<days:(\d{4})-(\d{2})-(\d{2})>""".toRegex()

    init {
        reloadCommand()
    }

    internal fun reloadCommand() {
        val user = UserService.getUser(channel.channelId)
            ?: throw RuntimeException("User not found. it's bug? ${channel.channelName} - ${channel.channelId}")
        val commands = CommandService.getCommands(user)
        val manageCommands = mapOf("!명령어추가" to this::manageAddCommand, "!명령어삭제" to this::manageRemoveCommand, "!명령어수정" to this::manageUpdateCommand)

        manageCommands.forEach { (commandName, command) ->
            this.commands[commandName] = command
        }

        commands.map {
            this.commands.put(it.command.lowercase()) { msg, user ->
                logger.debug("${channel.channelName} - ${it.command} - ${it.content}/${it.failContent}")

                val result = replaceCounters(Pair(it.content, it.failContent), user, msg, listener, msg.profile?.nickname ?: "")
                listener.sendChat(result)
            }
        }
    }

    private fun manageAddCommand(msg: ChatMessage, user: User) {
        if (msg.profile?.userRoleCode == "common_user") {
            listener.sendChat("매니저만 명령어를 추가할 수 있습니다.")
            return
        }

        val parts = msg.content.split(" ", limit = 3)
        if (parts.size < 3) {
            listener.sendChat("명령어 추가 형식은 '!명령어추가 명령어 내용'입니다.")
            return
        }
        if (commands.containsKey(parts[1])) {
            listener.sendChat("${parts[1]} 명령어는 이미 있는 명령어입니다.")
            return
        }
        val command = parts[1]
        val content = parts[2]
        CommandService.saveCommand(user, command, content, "")
        listener.sendChat("명령어 '$command' 추가되었습니다.")
    }

    private fun manageUpdateCommand(msg: ChatMessage, user: User) {
        if (msg.profile?.userRoleCode == "common_user") {
            listener.sendChat("매니저만 명령어를 추가할 수 있습니다.")
            return
        }

        val parts = msg.content.split(" ", limit = 3)
        if (parts.size < 3) {
            listener.sendChat("명령어 수정 형식은 '!명령어수정 명령어 내용'입니다.")
            return
        }
        if (!commands.containsKey(parts[1])) {
            listener.sendChat("${parts[1]} 명령어는 없는 명령어입니다.")
            return
        }
        val command = parts[1]
        val content = parts[2]
        CommandService.updateCommand(user, command, content, "")
        listener.sendChat("명령어 '$command' 수정되었습니다.")
    }

    private fun manageRemoveCommand(msg: ChatMessage, user: User) {
        if (msg.profile?.userRoleCode == "common_user") {
            listener.sendChat("매니저만 명령어를 삭제할 수 있습니다.")
            return
        }

        val parts = msg.content.split(" ", limit = 2)
        if (parts.size < 2) {
            listener.sendChat("명령어 삭제 형식은 '!명령어삭제 명령어'입니다.")
            return
        }
        val command = parts[1]
        CommandService.removeCommand(user, command)
        listener.sendChat("명령어 '$command' 삭제되었습니다.")
    }

    internal fun handle(msg: ChatMessage, user: User) {
        val commandKey = msg.content.split(' ')[0]

        commands[commandKey.lowercase()]?.let { it(msg, user) }
    }

    private fun replaceCounters(chat: Pair<String, String>, user: User, msg: ChatMessage, listener: ChzzkChat, userName: String): String {
        var result = chat.first
        var isFail = false

        result = dailyCounterPattern.replace(result) {
            val name = it.groupValues[1]
            val dailyCounter = CounterService.getDailyCounterValue(name, msg.userId, user)

            return@replace if(dailyCounter.second)
                CounterService.updateDailyCounterValue(name, msg.userId, 1, user).first.toString()
            else {
                isFail = true
                dailyCounter.first.toString()
            }
        }

        if(isFail && chat.second != "") {
            result = chat.second
            result = dailyCounterPattern.replace(result) {
                val name = it.groupValues[1]
                val dailyCounter = CounterService.getDailyCounterValue(name, msg.userId, user)

                dailyCounter.first.toString()
            }
        }

        result = followPattern.replace(result) {
            try {
                val followingDate = getFollowDate(listener.chatId, msg.userId)
                    .content.streamingProperty.following?.followDate

                return@replace when (followingDate) {
                    null -> "0"
                    else -> {
                        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                        val pastDate = LocalDateTime.parse(followingDate, formatter)
                        val today = LocalDateTime.now()
                        val period = ChronoUnit.DAYS.between(pastDate, today)

                        "$period"
                    }
                }
            } catch (e: Exception) {
                logger.error(e.message)
                "0"
            }
        }

        result = daysPattern.replace(result) {
            val (year, month, day) = it.destructured
            val pastDate = LocalDateTime.of(year.toInt(), month.toInt(), day.toInt(), 0, 0, 0)
            val today = LocalDateTime.now()

            val daysBetween = ChronoUnit.DAYS.between(pastDate, today)

            daysBetween.toString()
        }

        result = counterPattern.replace(result) {
            val name = it.groupValues[1]
            CounterService.updateCounterValue(name, 1, user).toString()
        }

        result = personalCounterPattern.replace(result) {
            val name = it.groupValues[1]
            CounterService.updatePersonalCounterValue(name, msg.userId, 1, user).toString()
        }

        result = namePattern.replace(result, userName)

        return result
    }
}
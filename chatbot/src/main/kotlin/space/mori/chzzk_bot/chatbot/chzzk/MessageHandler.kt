package space.mori.chzzk_bot.chatbot.chzzk

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject
import space.mori.chzzk_bot.common.events.CoroutinesEventBus
import space.mori.chzzk_bot.common.events.TimerEvent
import space.mori.chzzk_bot.common.events.TimerType
import space.mori.chzzk_bot.common.models.User
import space.mori.chzzk_bot.common.services.CommandService
import space.mori.chzzk_bot.common.services.CounterService
import space.mori.chzzk_bot.common.services.TimerConfigService
import space.mori.chzzk_bot.common.services.UserService
import space.mori.chzzk_bot.common.utils.getUptime
import xyz.r2turntrue.chzzk4j.chat.ChatMessage
import xyz.r2turntrue.chzzk4j.chat.ChzzkChat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit


class MessageHandler(
    private val handler: UserHandler
) {
    private val commands = mutableMapOf<String, (msg: ChatMessage, user: User) -> Unit>()

    private val counterPattern = Regex("<counter:([^>]+)>")
    private val personalCounterPattern = Regex("<counter_personal:([^>]+)>")
    private val dailyCounterPattern = Regex("<daily_counter:([^>]+)>")
    private val namePattern = Regex("<name>")
    private val followPattern = Regex("<following>")
    private val daysPattern = """<days:(\d{4})-(\d{2})-(\d{2})>""".toRegex()

    private val channel = handler.channel
    private val logger = handler.logger
    private val listener = handler.listener

    private val dispatcher: CoroutinesEventBus by inject(CoroutinesEventBus::class.java)

    init {
        reloadCommand()
    }

    internal fun reloadCommand() {
        val user = UserService.getUser(channel.channelId)
            ?: throw RuntimeException("User not found. it's bug? ${channel.channelName} - ${channel.channelId}")
        val commands = CommandService.getCommands(user)
        val manageCommands = mapOf("!명령어추가" to this::manageAddCommand, "!명령어삭제" to this::manageRemoveCommand, "!명령어수정" to this::manageUpdateCommand, "!시간" to this::timerCommand)

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

    private fun timerCommand(msg: ChatMessage, user: User) {
        if (msg.profile?.userRoleCode == "common_user") {
            listener.sendChat("매니저만 이 명령어를 사용할 수 있습니다.")
            return
        }

        val parts = msg.content.split(" ", limit = 3)
        if (parts.size < 2) {
            listener.sendChat("타이머 명령어 형식을 잘 찾아봐주세요!")
            return
        }

        val command = parts[1]
        when (parts[1]) {
            "업타임" -> {
                logger.debug("${user.token} / 업타임")

                CoroutineScope(Dispatchers.Default).launch {
                    dispatcher.post(
                        TimerEvent(
                            user.token,
                            TimerType.UPTIME,
                            getUptime(handler.streamStartTime!!)
                        )
                    )
                }
            }
            "삭제" -> {
                logger.debug("${user.token} / 삭제")
                CoroutineScope(Dispatchers.Default).launch {
                    dispatcher.post(TimerEvent(user.token, TimerType.REMOVE, ""))
                }
            }
            "설정" -> {
                when (parts[2]) {
                    "업타임" -> {
                        TimerConfigService.saveOrUpdateConfig(user, TimerType.UPTIME)
                        listener.sendChat("기본 타이머 설정이 업타임으로 바뀌었습니다.")
                    }
                    "삭제" -> {
                        TimerConfigService.saveOrUpdateConfig(user, TimerType.REMOVE)
                        listener.sendChat("기본 타이머 설정이 삭제로 바뀌었습니다.")
                    }
                    else -> listener.sendChat("!타이머 설정 (업타임/삭제) 형식으로 써주세요!")
                }
            }
            else -> {
                logger.debug("${user.token} / 그외")
                try {
                    val time = command.toInt()
                    val currentTime = LocalDateTime.now()
                    val timestamp = currentTime.plus(time.toLong(), ChronoUnit.MINUTES)

                    CoroutineScope(Dispatchers.Default).launch {
                        dispatcher.post(TimerEvent(user.token, TimerType.TIMER, timestamp.toString()))
                    }
                } catch (e: NumberFormatException) {
                    listener.sendChat("!타이머/숫자 형식으로 적어주세요! 단위: 분")
                } catch (e: Exception) {
                    listener.sendChat("타이머 설정 중 오류가 발생했습니다.")
                    logger.error("Error processing timer command: ${e.message}", e)
                }
            }
        }
    }

    internal fun handle(msg: ChatMessage, user: User) {
        val commandKey = msg.content.split(' ')[0]

        commands[commandKey.lowercase()]?.let { it(msg, user) }
    }

    private fun replaceCounters(chat: Pair<String, String>, user: User, msg: ChatMessage, listener: ChzzkChat, userName: String): String {
        var result = chat.first
        var isFail = false

        // Replace dailyCounterPattern
        result = dailyCounterPattern.replace(result) { matchResult ->
            val name = matchResult.groupValues[1]
            val dailyCounter = CounterService.getDailyCounterValue(name, msg.userId, user)

            if (dailyCounter.second) {
                CounterService.updateDailyCounterValue(name, msg.userId, 1, user).first.toString()
            } else {
                isFail = true
                dailyCounter.first.toString()
            }
        }

        // Handle fail case
        if (isFail && chat.second.isNotEmpty()) {
            result = chat.second
            result = dailyCounterPattern.replace(result) { matchResult ->
                val name = matchResult.groupValues[1]
                val dailyCounter = CounterService.getDailyCounterValue(name, msg.userId, user)
                dailyCounter.first.toString()
            }
        }

        // Replace followPattern
        result = followPattern.replace(result) { _ ->
            try {
                val followingDate = getFollowDate(listener.chatId, msg.userId)
                    .content.streamingProperty.following?.followDate

                val period = followingDate?.let {
                    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    val pastDate = LocalDateTime.parse(it, formatter)
                    val today = LocalDateTime.now()
                    ChronoUnit.DAYS.between(pastDate, today)
                } ?: 0

                period.toString()
            } catch (e: Exception) {
                logger.error(e.message)
                "0"
            }
        }

        // Replace daysPattern
        result = daysPattern.replace(result) { matchResult ->
            val (year, month, day) = matchResult.destructured
            val pastDate = LocalDateTime.of(year.toInt(), month.toInt(), day.toInt(), 0, 0, 0)
            val today = LocalDateTime.now()

            val daysBetween = ChronoUnit.DAYS.between(pastDate, today)
            daysBetween.toString()
        }

        // Replace counterPattern
        result = counterPattern.replace(result) { matchResult ->
            val name = matchResult.groupValues[1]
            CounterService.updateCounterValue(name, 1, user).toString()
        }

        // Replace personalCounterPattern
        result = personalCounterPattern.replace(result) { matchResult ->
            val name = matchResult.groupValues[1]
            CounterService.updatePersonalCounterValue(name, msg.userId, 1, user).toString()
        }

        // Replace namePattern
        result = namePattern.replace(result, userName)

        return result
    }

}
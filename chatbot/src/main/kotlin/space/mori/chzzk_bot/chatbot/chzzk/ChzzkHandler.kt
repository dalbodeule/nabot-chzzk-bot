package space.mori.chzzk_bot.chatbot.chzzk

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import space.mori.chzzk_bot.chatbot.chzzk.Connector.getChannel
import space.mori.chzzk_bot.chatbot.discord.Discord
import space.mori.chzzk_bot.chatbot.utils.refreshAccessToken
import space.mori.chzzk_bot.common.events.*
import space.mori.chzzk_bot.common.models.User
import space.mori.chzzk_bot.common.services.LiveStatusService
import space.mori.chzzk_bot.common.services.TimerConfigService
import space.mori.chzzk_bot.common.services.UserService
import space.mori.chzzk_bot.common.utils.*
import xyz.r2turntrue.chzzk4j.ChzzkClient
import xyz.r2turntrue.chzzk4j.session.ChzzkSessionBuilder
import xyz.r2turntrue.chzzk4j.session.ChzzkSessionSubscriptionType
import xyz.r2turntrue.chzzk4j.session.ChzzkUserSession
import xyz.r2turntrue.chzzk4j.session.event.SessionChatMessageEvent
import xyz.r2turntrue.chzzk4j.types.channel.ChzzkChannel
import xyz.r2turntrue.chzzk4j.types.channel.live.ChzzkLiveDetail
import java.lang.Exception
import java.net.SocketTimeoutException
import java.time.LocalDateTime
import java.nio.charset.Charset

object ChzzkHandler {
    private val handlers = mutableListOf<UserHandler>()
    private val logger = LoggerFactory.getLogger(this::class.java)
    lateinit var botUid: String
    @Volatile private var running: Boolean = false
    private val dispatcher: CoroutinesEventBus by inject(CoroutinesEventBus::class.java)

    fun addUser(chzzkChannel: ChzzkChannel, user: User) {
        handlers.add(UserHandler(chzzkChannel, logger, user, streamStartTime = LocalDateTime.now()))
    }

    fun enable() {
        botUid = Connector.client.fetchLoggedUser().userId
        UserService.getAllUsers().map {
            if(!it.isDisabled)
                try {
                    getChannel(it.token)?.let { token -> addUser(token, it) }
                } catch(e: Exception) {
                    logger.info("Exception: ${it.token}(${it.username}) not found. ${e.stackTraceToString()}")
                }
        }

        handlers.forEach { handler ->
            val streamInfo = Connector.getLive(handler.channel.channelId)
            if (streamInfo?.isOnline == true) handler.isActive(true, streamInfo)
        }

        dispatcher.subscribe(UserRegisterEvent::class) {
            val channel = getChannel(it.chzzkId)
            val user = UserService.getUser(it.chzzkId)
            if(channel != null && user != null) {
                addUser(channel, user)
            }
        }

        dispatcher.subscribe(CommandReloadEvent::class) {
            handlers.firstOrNull { handlers -> handlers.channel.channelId == it.uid }?.reloadCommand()
        }

        dispatcher.subscribe(BotEnabledEvent::class) {
            if(it.isDisabled) {
                handlers.removeIf { handlers -> handlers.channel.channelId == it.chzzkId }
            } else {
                val channel = getChannel(it.chzzkId)
                val user = UserService.getUser(it.chzzkId)
                if(channel != null && user != null) {
                    addUser(channel, user)
                }
            }
        }
    }

    fun disable() {
        handlers.forEach { handler ->
            handler.disable()
        }
    }

    internal fun reloadCommand(chzzkChannel: ChzzkChannel) {
        val handler = handlers.firstOrNull { it.channel.channelId == chzzkChannel.channelId }
        if (handler != null)
            handler.reloadCommand()
        else
            throw RuntimeException("${chzzkChannel.channelName} doesn't have handler")
    }

    internal fun reloadUser(chzzkChannel: ChzzkChannel, user: User) {
        val handler = handlers.firstOrNull { it.channel.channelId == chzzkChannel.channelId }
        if (handler != null)
            handler.reloadUser(user)
        else
            throw RuntimeException("${chzzkChannel.channelName} doesn't have handler")
    }

    fun runStreamInfo() {
        running = true

        val threadRunner1 = Runnable {
            logger.info("Thread 1 started!")
            while (running) {
                handlers.forEach {
                    if (!running) return@forEach
                    try {
                        val streamInfo = Connector.getLive(it.channel.channelId)
                        if (streamInfo?.isOnline == true && !it.isActive) {
                            try {
                                it.isActive(true, streamInfo)
                            } catch(e: Exception) {
                                logger.info("Exception: ${e.stackTraceToString()}")
                            }
                        }
                        if (streamInfo?.isOnline == false && it.isActive) it.isActive(false, streamInfo)
                    } catch (e: SocketTimeoutException) {
                        logger.info("Thread 1 Timeout: ${it.channel.channelName} / ${e.stackTraceToString()}")
                    } catch (e: Exception) {
                        logger.info("Thread 1 Exception: ${it.channel.channelName} / ${e.stackTraceToString()}")
                    } finally {
                        Thread.sleep(5000)
                    }
                }
                Thread.sleep(60000)
            }
        }

        val threadRunner2 = Runnable {
            logger.info("Thread 2 started!")
            logger.info("Thread 2 started!")
            while (running) {
                handlers.forEach {
                    if (!running) return@forEach
                    try {
                        val streamInfo = Connector.getLive(it.channel.channelId)
                        if (streamInfo?.isOnline == true && !it.isActive) {
                            try {
                                it.isActive(true, streamInfo)
                            } catch(e: Exception) {
                                logger.info("Exception: ${e.stackTraceToString()}")
                            }
                        }
                        if (streamInfo?.isOnline == false && it.isActive) it.isActive(false, streamInfo)
                    } catch (e: SocketTimeoutException) {
                        logger.info("Thread 1 Timeout: ${it.channel.channelName} / ${e.stackTraceToString()}")
                    } catch (e: Exception) {
                        logger.info("Thread 1 Exception: ${it.channel.channelName} / ${e.stackTraceToString()}")
                    } finally {
                        Thread.sleep(5000)
                    }
                }
                Thread.sleep(60000)
            }
        }

        fun startThread(name: String, runner: Runnable) {
            Thread({
                while(running) {
                    try {
                        val thread = Thread(runner, name)
                        thread.start()
                        thread.join()
                    } catch(e: Exception) {
                        logger.error("Thread $name Exception: ${e.stackTraceToString()}")
                    }
                    if(running) {
                        logger.info("Thread $name restart in 5 seconds")
                        Thread.sleep(5000)
                    }
                }
            }, "${name}-runner").start()
        }

        // 첫 번째 스레드 시작
        startThread("Chzzk-StreamInfo-1", threadRunner1)

        // 85초 대기 후 두 번째 스레드 시작
        CoroutineScope(Dispatchers.Default).launch {
            delay(95000) // start with 95 secs after.
            if (running) {
                startThread("Chzzk-StreamInfo-2", threadRunner2)
            }
        }
    }

    fun stopStreamInfo() {
        running = false
    }
}

@OptIn(DelicateCoroutinesApi::class)
class UserHandler(
    val channel: ChzzkChannel,
    val logger: Logger,
    private var user: User,
    var streamStartTime: LocalDateTime?,
) {
    var messageHandler: MessageHandler
    var client: ChzzkClient
    var listener: ChzzkUserSession
    var chatChannelId: String?

    private val dispatcher: CoroutinesEventBus by inject(CoroutinesEventBus::class.java)
    private var _isActive: Boolean
        get() = LiveStatusService.getLiveStatus(user)?.status ?: false
        set(value) {
            LiveStatusService.updateOrCreate(user, value)
        }

    init {
        val user = UserService.getUser(channel.channelId)

        if(user?.accessToken == null || user.refreshToken == null) {
            throw RuntimeException("AccessToken or RefreshToken is not valid.")
        }
        try {
            println(user.refreshToken)

            val tokens = user.refreshToken?.let { token -> Connector.client.refreshAccessToken(token)}
            if(tokens == null) {
                throw RuntimeException("AccessToken is not valid.")
            }
            client = Connector.getClient(tokens.first, tokens.second)
            UserService.setRefreshToken(user, tokens.first, tokens.second)
            chatChannelId = getChzzkChannelId(channel.channelId)

            client.loginAsync().join()
            listener = ChzzkSessionBuilder(client).buildUserSession()
            listener.createAndConnectAsync().join()
            messageHandler = MessageHandler(this@UserHandler)

            listener.on(SessionChatMessageEvent::class.java) {
                messageHandler.handle(it.message, user)
            }

            GlobalScope.launch {
                val timer = TimerConfigService.getConfig(user)
                if (timer?.option == TimerType.UPTIME.value)
                    dispatcher.post(
                        TimerEvent(
                            channel.channelId,
                            TimerType.UPTIME,
                            getUptime(streamStartTime!!)
                        )
                    )
                else dispatcher.post(
                    TimerEvent(
                        channel.channelId,
                        TimerType.entries.firstOrNull { it.value == timer?.option } ?: TimerType.REMOVE,
                        null
                    )
                )
            }
            
        } catch(e: Exception) {
            logger.error("Exception(${user.username}): ${e.stackTraceToString()}")
            throw RuntimeException("Exception: ${e.stackTraceToString()}")
        }
    }

    internal fun disable() {
        listener.disconnectAsync().join()
        _isActive = false
    }

    internal fun reloadCommand() {
        messageHandler.reloadCommand()
    }

    internal fun reloadUser(user: User) {
        this.user = user
    }

    internal val isActive: Boolean
        get() = _isActive

    internal fun isActive(value: Boolean, status: ChzzkLiveDetail) {
        if(value) {
            CoroutineScope(Dispatchers.Default).launch {
                logger.info("${user.username} is live.")

                reloadUser(UserService.getUser(user.id.value)!!)

                logger.info("ChzzkChat connecting... ${channel.channelName} - ${channel.channelId}")
                listener.subscribeAsync(ChzzkSessionSubscriptionType.CHAT).join()

                streamStartTime = LocalDateTime.now()

                if(!_isActive) {
                    _isActive = true
                    when(TimerConfigService.getConfig(UserService.getUser(channel.channelId)!!)?.option) {
                        TimerType.UPTIME.value -> dispatcher.post(
                            TimerEvent(
                                channel.channelId,
                                TimerType.UPTIME,
                                getUptime(streamStartTime!!)
                            )
                        )

                        else -> dispatcher.post(
                            TimerEvent(
                                channel.channelId,
                                TimerType.REMOVE,
                                ""
                            )
                        )
                    }
                    delay(5000L)
                    try {
                        if(!user.isDisableStartupMsg)
                            sendChat("${user.username} 님! 오늘도 열심히 방송하세요!")
                        Discord.sendDiscord(user, status)
                    } catch(e: Exception) {
                        logger.info("Stream on logic has some error: ${e.stackTraceToString()}")
                    }
                }
            }
        } else {
            logger.info("${user.username} is offline.")
            streamStartTime = null
            listener.disconnectAsync().join()
            _isActive = false

            CoroutineScope(Dispatchers.Default).launch {
                val events = listOf(
                    TimerEvent(
                        channel.channelId,
                        TimerType.STREAM_OFF,
                        null
                    ),
                    SongEvent(
                        channel.channelId,
                        SongType.STREAM_OFF,
                        null,
                        null,
                        null,
                        null,
                    )
                )

                events.forEach { dispatcher.post(it) }
            }
        }
    }

    private fun String.limitUtf8Length(maxBytes: Int): String {
        val bytes = this.toByteArray(Charset.forName("UTF-8"))
        if (bytes.size <= maxBytes) return this
        var truncatedString = this
        while (truncatedString.toByteArray(Charset.forName("UTF-8")).size > maxBytes) {
            truncatedString = truncatedString.substring(0, truncatedString.length - 1)
        }
        return truncatedString
    }

    @OptIn(DelicateCoroutinesApi::class)
    internal fun sendChat(msg: String) {
        GlobalScope.launch {
            delay(100L)
            client.sendChatToLoggedInChannel(msg.limitUtf8Length(100))
        }
    }
}

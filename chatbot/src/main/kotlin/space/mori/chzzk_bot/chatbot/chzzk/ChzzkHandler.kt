package space.mori.chzzk_bot.chatbot.chzzk

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import space.mori.chzzk_bot.chatbot.chzzk.Connector.chzzk
import space.mori.chzzk_bot.chatbot.chzzk.Connector.getChannel
import space.mori.chzzk_bot.chatbot.discord.Discord
import space.mori.chzzk_bot.common.events.*
import space.mori.chzzk_bot.common.models.User
import space.mori.chzzk_bot.common.services.LiveStatusService
import space.mori.chzzk_bot.common.services.TimerConfigService
import space.mori.chzzk_bot.common.services.UserService
import space.mori.chzzk_bot.common.utils.*
import xyz.r2turntrue.chzzk4j.chat.ChatEventListener
import xyz.r2turntrue.chzzk4j.chat.ChatMessage
import xyz.r2turntrue.chzzk4j.chat.ChzzkChat
import xyz.r2turntrue.chzzk4j.types.channel.ChzzkChannel
import java.lang.Exception
import java.net.SocketTimeoutException
import java.time.LocalDateTime

object ChzzkHandler {
    private val handlers = mutableListOf<UserHandler>()
    private val logger = LoggerFactory.getLogger(this::class.java)
    lateinit var botUid: String
    @Volatile private var running: Boolean = false
    private val dispatcher: CoroutinesEventBus by inject(CoroutinesEventBus::class.java)

    fun addUser(chzzkChannel: ChzzkChannel, user: User) {
        handlers.add(UserHandler(chzzkChannel, logger, user, streamStartTime = null))
    }

    fun enable() {
        botUid = chzzk.loggedUser.userId
        UserService.getAllUsers().map {
            if(it.token != null)
                chzzk.getChannel(it.token)?.let { token -> addUser(token, it) }
        }

        handlers.forEach { handler ->
            val streamInfo = getStreamInfo(handler.listener.channelId)
            if (streamInfo.content?.status == "OPEN") handler.isActive(true, streamInfo)
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
            while (running) {
                handlers.forEach {
                    if (!running) return@forEach
                    try {
                        val streamInfo = getStreamInfo(it.channel.channelId)
                        if (streamInfo.content?.status == "OPEN" && !it.isActive) it.isActive(true, streamInfo)
                        if (streamInfo.content?.status == "CLOSE" && it.isActive) it.isActive(false, streamInfo)
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
            while (running) {
                handlers.forEach {
                    if (!running) return@forEach
                    try {
                        val streamInfo = getStreamInfo(it.channel.channelId)
                        if (streamInfo.content?.status == "OPEN" && !it.isActive) it.isActive(true, streamInfo)
                        if (streamInfo.content?.status == "CLOSE" && it.isActive) it.isActive(false, streamInfo)
                    } catch (e: SocketTimeoutException) {
                        logger.info("Thread 2 Timeout: ${it.channel.channelName} / ${e.stackTraceToString()}")
                    } catch (e: Exception) {
                        logger.info("Thread 2 Exception: ${it.channel.channelName} / ${e.stackTraceToString()}")
                    } finally {
                        Thread.sleep(5000)
                    }
                }
                Thread.sleep(60000)
            }
        }

        fun startThread(name: String, runner: Runnable) {
            CoroutineScope(Dispatchers.Default).launch {
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
                        delay(5000)
                    }
                }
            }
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

class UserHandler(
    val channel: ChzzkChannel,
    val logger: Logger,
    private var user: User,
    var streamStartTime: LocalDateTime?,
) {
    private lateinit var messageHandler: MessageHandler
    private val dispatcher: CoroutinesEventBus by inject(CoroutinesEventBus::class.java)
    private var _isActive: Boolean
        get() = LiveStatusService.getLiveStatus(user)?.status ?: false
        set(value) {
            LiveStatusService.updateOrCreate(user, value)
        }

    var listener: ChzzkChat = chzzk.chat(channel.channelId)
        .withAutoReconnect(true)
        .withChatListener(object : ChatEventListener {
            override fun onConnect(chat: ChzzkChat, isReconnecting: Boolean) {
                logger.info("ChzzkChat connected. ${channel.channelName} - ${channel.channelId} / reconnected: $isReconnecting")
                messageHandler = MessageHandler(this@UserHandler)
            }

            override fun onError(ex: Exception) {
                logger.info("ChzzkChat error. ${channel.channelName} - ${channel.channelId}")
                logger.debug(ex.stackTraceToString())
            }

            override fun onChat(msg: ChatMessage) {
                if(!_isActive) return
                messageHandler.handle(msg, user)
            }

            override fun onConnectionClosed(code: Int, reason: String?, remote: Boolean, tryingToReconnect: Boolean) {
                logger.info("ChzzkChat closed. ${channel.channelName} - ${channel.channelId}")
                logger.info("Reason: $reason / $tryingToReconnect")
            }
        })
        .build()

    internal fun disable() {
        listener.closeAsync()
    }

    internal fun reloadCommand() {
        messageHandler.reloadCommand()
    }

    internal fun reloadUser(user: User) {
        this.user = user
    }

    internal val isActive: Boolean
        get() = _isActive

    internal fun isActive(value: Boolean, status: IData<IStreamInfo?>) {
        if(value) {
            logger.info("${user.username} is live.")

            logger.info("ChzzkChat connecting... ${channel.channelName} - ${channel.channelId}")
            listener.connectBlocking()

            streamStartTime = status.content?.openDate?.let { convertChzzkDateToLocalDateTime(it) }

            CoroutineScope(Dispatchers.Default).launch {
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
                        listener.sendChat("${user.username} 님! 오늘도 열심히 방송하세요!")
                        Discord.sendDiscord(user, status)
                    } catch(e: Exception) {
                        logger.info("Stream on logic has some error: ${e.stackTraceToString()}")
                    }
                }
            }
        } else {
            logger.info("${user.username} is offline.")
            streamStartTime = null
            listener.closeAsync()
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
                        null,
                        null
                    )
                )

                events.forEach { dispatcher.post(it) }
            }
        }
    }
}
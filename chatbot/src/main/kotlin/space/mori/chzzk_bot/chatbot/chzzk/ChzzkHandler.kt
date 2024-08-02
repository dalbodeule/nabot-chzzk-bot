package space.mori.chzzk_bot.chatbot.chzzk

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import space.mori.chzzk_bot.chatbot.chzzk.Connector.chzzk
import space.mori.chzzk_bot.chatbot.discord.Discord
import space.mori.chzzk_bot.common.events.CoroutinesEventBus
import space.mori.chzzk_bot.common.events.TimerEvent
import space.mori.chzzk_bot.common.events.TimerType
import space.mori.chzzk_bot.common.models.User
import space.mori.chzzk_bot.common.services.UserService
import space.mori.chzzk_bot.common.utils.getUptime
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
    @Volatile private var running: Boolean = false

    fun addUser(chzzkChannel: ChzzkChannel, user: User) {
        handlers.add(UserHandler(chzzkChannel, logger, user, streamStartTime = null))
    }

    fun enable() {
        UserService.getAllUsers().map {
            chzzk.getChannel(it.token)?.let { token -> addUser(token, it) }
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
        val thread = Thread({
            while(running) {
                handlers.forEach {
                    if (!running) return@forEach
                    try {
                        val streamInfo = getStreamInfo(it.channel.channelId)
                        if (streamInfo.content.status == "OPEN" && !it.isActive) it.isActive(true, streamInfo)
                        if (streamInfo.content.status == "CLOSE" && it.isActive) it.isActive(false, streamInfo)
                    } catch(e: SocketTimeoutException) {
                        logger.info("Timeout: ${it.channel.channelName} / ${e.stackTraceToString()}")
                    } catch (e: Exception) {
                        logger.info("Exception: ${it.channel.channelName} / ${e.stackTraceToString()}")
                    } finally {
                        Thread.sleep(5000)
                    }
                }
                Thread.sleep(60000)
            }
        }, "Chzzk-StreamInfo")

        thread.start()
    }

    fun stopStreamInfo() {
        running = false
    }
}

class UserHandler(
    val channel: ChzzkChannel,
    val logger: Logger,
    private var user: User,
    private var _isActive: Boolean = false,
    var streamStartTime: LocalDateTime?,
) {
    private lateinit var messageHandler: MessageHandler
    private val dispatcher: CoroutinesEventBus by inject(CoroutinesEventBus::class.java)

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

    internal fun isActive(value: Boolean, status: IData<IStreamInfo>) {
        _isActive = value
        if(value) {
            logger.info("${user.username} is live.")

            logger.info("ChzzkChat connecting... ${channel.channelName} - ${channel.channelId}")
            listener.connectBlocking()

            streamStartTime = LocalDateTime.now()

            CoroutineScope(Dispatchers.Default).launch {
                dispatcher.post(TimerEvent(
                    channel.channelId,
                    TimerType.UPTIME,
                    getUptime(streamStartTime!!)
                ))
                delay(5000L)
                listener.sendChat("${user.username} 님! 오늘도 열심히 방송하세요!")
                Discord.sendDiscord(user, status)
            }
        } else {
            logger.info("${user.username} is offline.")
            streamStartTime = null
            listener.closeAsync()

            CoroutineScope(Dispatchers.Default).launch {
                dispatcher.post(TimerEvent(
                    channel.channelId,
                    TimerType.STREAM_OFF,
                    ""
                ))
            }
        }
    }
}
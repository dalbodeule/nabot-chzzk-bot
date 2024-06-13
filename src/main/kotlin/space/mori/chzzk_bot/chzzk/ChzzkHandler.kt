package space.mori.chzzk_bot.chzzk

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import space.mori.chzzk_bot.chzzk.Connector.chzzk
import space.mori.chzzk_bot.models.User
import space.mori.chzzk_bot.services.UserService
import xyz.r2turntrue.chzzk4j.chat.ChatEventListener
import xyz.r2turntrue.chzzk4j.chat.ChatMessage
import xyz.r2turntrue.chzzk4j.chat.ChzzkChat
import xyz.r2turntrue.chzzk4j.types.channel.ChzzkChannel
import java.lang.Exception

object ChzzkHandler {
    private val handlers = mutableListOf<UserHandler>()
    private val logger = LoggerFactory.getLogger(this::class.java)

    internal fun addUser(chzzkChannel: ChzzkChannel, user: User) {
        handlers.add(UserHandler(chzzkChannel, logger, user))
    }

    internal fun enable() {
        UserService.getAllUsers().map {
            chzzk.getChannel(it.token)?.let { token -> addUser(token, it)}
        }
    }

    internal fun disable() {
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
}

class UserHandler(
    val channel: ChzzkChannel, private val logger: Logger, private val user: User
) {
    private lateinit var messageHandler: MessageHandler

    private var listener: ChzzkChat = chzzk.chat(channel.channelId)
        .withAutoReconnect(true)
        .withChatListener(object : ChatEventListener {
            override fun onConnect(chat: ChzzkChat, isReconnecting: Boolean) {
                logger.info("ChzzkChat connected. ${channel.channelName} - ${channel.channelId} / reconnected: $isReconnecting")
                messageHandler = MessageHandler(channel, logger, chat)
            }

            override fun onError(ex: Exception) {
                logger.info("ChzzkChat error. ${channel.channelName} - ${channel.channelId}")
                logger.debug(ex.stackTraceToString())
            }

            override fun onChat(msg: ChatMessage) {
                messageHandler.handle(msg, user)
            }

            override fun onConnectionClosed(code: Int, reason: String?, remote: Boolean, tryingToReconnect: Boolean) {
                logger.info("ChzzkChat closed. ${channel.channelName} - ${channel.channelId}")
                logger.info("Reason: $reason / $tryingToReconnect")
            }
        })
        .build()

    init {
        logger.info("ChzzkChat connecting... ${channel.channelName} - ${channel.channelId}")
        listener.connectBlocking()
    }

    internal fun disable() {
        listener.closeBlocking()
    }

    internal fun reloadCommand() {
        messageHandler.reloadCommand()
    }
}
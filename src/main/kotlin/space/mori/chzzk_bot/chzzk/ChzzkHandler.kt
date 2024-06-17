package space.mori.chzzk_bot.chzzk

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import space.mori.chzzk_bot.chzzk.Connector.chzzk
import space.mori.chzzk_bot.discord
import space.mori.chzzk_bot.models.User
import space.mori.chzzk_bot.services.UserService
import xyz.r2turntrue.chzzk4j.chat.ChatEventListener
import xyz.r2turntrue.chzzk4j.chat.ChatMessage
import xyz.r2turntrue.chzzk4j.chat.ChzzkChat
import xyz.r2turntrue.chzzk4j.types.channel.ChzzkChannel
import java.lang.Exception
import java.time.Instant

object ChzzkHandler {
    private val handlers = mutableListOf<UserHandler>()
    private val logger = LoggerFactory.getLogger(this::class.java)
    @Volatile private var running: Boolean = false

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

    internal fun reloadUser(chzzkChannel: ChzzkChannel, user: User) {
        val handler = handlers.firstOrNull { it.channel.channelId == chzzkChannel.channelId }
        if (handler != null)
            handler.reloadUser(user)
        else
            throw RuntimeException("${chzzkChannel.channelName} doesn't have handler")
    }

    internal fun runStreamInfo() {
        running = true
        Thread {
            while(running) {
                handlers.forEach {
                    if (!running) return@forEach
                    val streamInfo = getStreamInfo(it.channel.channelId)
                    if(streamInfo.content.status == "OPEN" && !it.isActive) it.isActive(true, streamInfo)
                    if(streamInfo.content.status == "CLOSED" && it.isActive) it.isActive(false, streamInfo)
                    Thread.sleep(5000)
                }
                Thread.sleep(60000)
            }
        }.start()
    }

    internal fun stopStreamInfo() {
        running = false
    }
}

class UserHandler(
    val channel: ChzzkChannel,
    private val logger: Logger,
    private var user: User,
    private var _isActive: Boolean = false
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
        listener.connectAsync()
    }

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
            if(user.liveAlertMessage != "" && user.liveAlertGuild != null && user.liveAlertChannel != null) {
                val channel = discord.getChannel(user.liveAlertGuild!!, user.liveAlertGuild!!) ?: throw RuntimeException("${user.liveAlertChannel} is not valid.")

                val embed = EmbedBuilder()
                embed.setTitle(status.content.liveTitle, "https://chzzk.naver.com/live/${user.token}")
                embed.setDescription("${user.username} 님이 방송을 시작했습니다.")
                embed.setUrl(status.content.channel.channelImageUrl)
                embed.setTimestamp(Instant.now())
                embed.setAuthor(user.username, "https://chzzk.naver.com/live/${user.token}", status.content.channel.channelImageUrl)
                embed.addField("카테고리", status.content.liveCategoryValue, true)
                embed.addField("태그", status.content.tags.joinToString(", "), true)
                embed.setImage(status.content.liveImageUrl)

                channel.sendMessage(
                    MessageCreateBuilder()
                        .setContent(user.liveAlertMessage)
                        .setEmbeds(embed.build())
                        .build()
                ).queue()
            }
        } else {
            logger.info("${user.username} is offline.")
        }
    }
}
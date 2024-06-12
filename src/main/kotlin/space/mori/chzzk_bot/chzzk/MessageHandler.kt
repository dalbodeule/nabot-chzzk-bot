package space.mori.chzzk_bot.chzzk

import org.slf4j.Logger
import space.mori.chzzk_bot.services.CommandService
import space.mori.chzzk_bot.services.UserService
import xyz.r2turntrue.chzzk4j.chat.ChatMessage
import xyz.r2turntrue.chzzk4j.chat.ChzzkChat
import xyz.r2turntrue.chzzk4j.types.channel.ChzzkChannel
class MessageHandler(
    private val channel: ChzzkChannel,
    private val logger: Logger,
    private val listener: ChzzkChat
) {
    private val commands = mutableMapOf<String, () -> Unit>()

    init {
        val user = UserService.getUser(channel.channelId)
            ?: throw RuntimeException("User not found. it's bug? ${channel.channelName} - ${channel.channelId}")
        val commands = CommandService.getCommands(user)

        commands.map {
            this.commands.put(it.command.lowercase()) {
                logger.debug("${channel.channelName} - ${it.command} - ${it.content}")
                listener.sendChat(it.content)
            }
        }
    }

    internal fun handle(msg: ChatMessage) {
        val commandKey = msg.content.split(' ')[0]

        commands[commandKey.lowercase()]?.let { it() }
    }
}
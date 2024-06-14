package space.mori.chzzk_bot.discord.commands

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.build.Commands
import org.slf4j.LoggerFactory
import space.mori.chzzk_bot.discord.CommandInterface

object PingCommand: CommandInterface {
    private val logger = LoggerFactory.getLogger(this::class.java)
    override val name = "ping"
    override val command = Commands.slash(name, "봇이 살아있을까요?")

    override fun run(event: SlashCommandInteractionEvent, bot: JDA) {
        event.hook.sendMessage("${event.user.asMention} Pong!").queue()
    }
}
package space.mori.chzzk_bot.discord.commands

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.build.Commands
import space.mori.chzzk_bot.discord.Command
import space.mori.chzzk_bot.discord.CommandInterface

@Command()
object Ping: CommandInterface {
    override val command = Commands.slash("ping", "봇이 살아있을까요?")
    override val name = "ping"

    override fun run(event: SlashCommandInteractionEvent, bot: JDA) {
        event.hook.sendMessage("${event.user.asMention} Pong!").queue()
    }
}
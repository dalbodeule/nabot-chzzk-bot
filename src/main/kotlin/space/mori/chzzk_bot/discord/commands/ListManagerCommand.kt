package space.mori.chzzk_bot.discord.commands

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.build.Commands
import org.slf4j.LoggerFactory
import space.mori.chzzk_bot.discord.CommandInterface
import space.mori.chzzk_bot.services.ManagerService
import space.mori.chzzk_bot.services.UserService

object ListManagerCommand : CommandInterface {
    private val logger = LoggerFactory.getLogger(this::class.java)
    override val name: String = "listmanager"
    override val command = Commands.slash(name, "매니저 목록을 확인합니다.")

    override fun run(event: SlashCommandInteractionEvent, bot: JDA) {
        try {
            val managers = event.guild?.idLong?.let { ManagerService.getAllUsers(it) }

            if(managers == null) {
                event.channel.sendMessage("여기에서는 사용할 수 없습니다.")
                return
            }

            val user = UserService.getUserWithGuildId(event.guild!!.idLong)

            val embed = EmbedBuilder()
            embed.setTitle("${user!!.username} 매니저 목록")
            embed.setDescription("매니저 목록입니다.")

            var idx = 1

            managers.forEach {
                embed.addField("${idx++}", it.lastUserName ?: it.managerId.toString(), true)
            }

            event.channel.sendMessageEmbeds(embed.build()).queue()
        } catch (e: Exception) {
            event.hook.sendMessage("에러가 발생했습니다.").queue()
            logger.debug(e.stackTraceToString())
        }
    }
}
package space.mori.chzzk_bot.discord.commands

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import org.slf4j.LoggerFactory
import space.mori.chzzk_bot.chzzk.ChzzkHandler
import space.mori.chzzk_bot.chzzk.Connector
import space.mori.chzzk_bot.discord.CommandInterface
import space.mori.chzzk_bot.services.CommandService
import space.mori.chzzk_bot.services.UserService

object RemoveCommand : CommandInterface {
    private val logger = LoggerFactory.getLogger(this::class.java)
    override val name: String = "remove"
    override val command = Commands.slash(name, "명령어를 삭제합니다.")
        .addOptions(OptionData(OptionType.STRING, "label", "삭제할 명령어를 입력하세요.", true))

    override fun run(event: SlashCommandInteractionEvent, bot: JDA) {
        val label = event.getOption("label")?.asString

        if(label == null) {
            event.hook.sendMessage("명령어는 필수 입력입니다.").queue()
            return
        }

        val user = UserService.getUser(event.user.idLong)
        if(user == null) {
            event.hook.sendMessage("치지직 계정을 찾을 수 없습니다.").queue()
            return
        }
        val chzzkChannel = Connector.getChannel(user.token)

        try {
            CommandService.removeCommand(user, label)
            try {
                ChzzkHandler.reloadCommand(chzzkChannel!!)
            } catch (_: Exception) {}
            event.hook.sendMessage("삭제가 완료되었습니다. $label").queue()
        } catch (e: Exception) {
            event.hook.sendMessage("에러가 발생했습니다.").queue()
            logger.debug(e.stackTraceToString())
        }
    }
}
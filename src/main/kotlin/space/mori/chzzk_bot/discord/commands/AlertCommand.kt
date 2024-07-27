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
import space.mori.chzzk_bot.services.ManagerService
import space.mori.chzzk_bot.services.UserService

object AlertCommand : CommandInterface {
    private val logger = LoggerFactory.getLogger(this::class.java)
    override val name: String = "alert"
    override val command = Commands.slash(name, "명령어를 추가합니다.")
        .addOptions(OptionData(OptionType.CHANNEL, "channel", "알림을 보낼 채널을 입력하세요."))
        .addOptions(OptionData(OptionType.STRING, "content", "표시될 텍스트를 입력하세요. 비워두면 알람이 취소됩니다."))

    override fun run(event: SlashCommandInteractionEvent, bot: JDA) {
        val channel = event.getOption("channel")?.asChannel
        val content = event.getOption("content")?.asString

        var user = UserService.getUser(event.user.idLong)
        val manager = event.guild?.idLong?.let { ManagerService.getUser(it, event.user.idLong) }
        if(user == null && manager == null) {
            event.hook.sendMessage("당신은 이 명령어를 사용할 수 없습니다.").queue()
            return
        }

        if (user == null) {
            user = manager!!.user
            ManagerService.updateManager(user, event.user.idLong, event.user.effectiveName)
        }

        val chzzkChannel = Connector.getChannel(user.token)

        try {
            val newUser = UserService.updateLiveAlert(user.id.value, channel?.guild?.idLong ?: 0L, channel?.idLong ?: 0L, content ?: "")
            try {
                ChzzkHandler.reloadUser(chzzkChannel!!, newUser)
            } catch (_: Exception) {}
            event.hook.sendMessage("업데이트가 완료되었습니다.").queue()
        } catch (e: Exception) {
            event.hook.sendMessage("에러가 발생했습니다.").queue()
            logger.debug(e.stackTraceToString())
        }
    }
}
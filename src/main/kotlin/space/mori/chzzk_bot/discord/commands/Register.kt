package space.mori.chzzk_bot.discord.commands

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import org.slf4j.LoggerFactory
import space.mori.chzzk_bot.chzzk.ChzzkHandler
import space.mori.chzzk_bot.chzzk.Connector
import space.mori.chzzk_bot.discord.Command
import space.mori.chzzk_bot.discord.CommandInterface
import space.mori.chzzk_bot.services.UserService

@Command
object Register: CommandInterface {
    private val logger = LoggerFactory.getLogger(this::class.java)
    override val name = "register"
    override val command =  Commands.slash(name, "치지직 계정을 등록합니다.")
        .addOptions(
            OptionData(
                OptionType.STRING,
                "chzzk_id",
                "36da10b7c35800f298e9c565a396bafd 형식으로 입력해주세요.",
                true
            )
        )

    override fun run(event: SlashCommandInteractionEvent, bot: JDA) {
        val chzzkID = event.getOption("chzzk_id")?.asString
        if(chzzkID == null) {
            event.hook.sendMessage("치지직 계정은 필수 입력입니다.").queue()
            return
        }

        val chzzkChannel = Connector.getChannel(chzzkID)
        if (chzzkChannel == null) {
            event.hook.sendMessage("치지직 계정을 찾을 수 없습니다.").queue()
            return
        }

        try {
            val user = UserService.saveUser(chzzkChannel.channelName, chzzkChannel.channelId, event.user.idLong)
            ChzzkHandler.addUser(chzzkChannel, user)
            event.hook.sendMessage("등록이 완료되었습니다. `${chzzkChannel.channelId}` - `${chzzkChannel.channelName}`")
        } catch(e: Exception) {
            event.hook.sendMessage("에러가 발생했습니다.").queue()
            logger.debug(e.stackTraceToString())
        }
    }
}
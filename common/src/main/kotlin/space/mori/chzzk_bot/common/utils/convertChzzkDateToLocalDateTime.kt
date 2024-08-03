package space.mori.chzzk_bot.common.utils

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

val logger: Logger = LoggerFactory.getLogger("convertChzzkDateToLocalDateTime")

fun convertChzzkDateToLocalDateTime(chzzkDate: String): LocalDateTime? {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    return try {
        LocalDateTime.parse(chzzkDate, formatter)
    } catch(e: DateTimeParseException) {
        logger.debug("Error to parsing date", e)
        null
    }
}
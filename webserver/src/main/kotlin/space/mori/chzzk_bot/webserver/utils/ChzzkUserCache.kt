package space.mori.chzzk_bot.webserver.utils

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import space.mori.chzzk_bot.common.utils.IData
import space.mori.chzzk_bot.common.utils.IStreamInfo
import space.mori.chzzk_bot.common.utils.getStreamInfo
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

object ChzzkUsercache {
    private val cache = ConcurrentHashMap<String, CachedUser>()
    private const val EXP_SECONDS = 600L
    private val mutex = Mutex()
    private val logger = LoggerFactory.getLogger(this::class.java)

    suspend fun getCachedUser(id: String): IData<IStreamInfo?>? {
        val now = Instant.now()
        var user = cache[id]

        if(user == null || user.timestamp.plusSeconds(EXP_SECONDS).isBefore(now)) {
            mutex.withLock {
                if(user == null || user?.timestamp?.plusSeconds(EXP_SECONDS)?.isBefore(now) != false) {
                    user = CachedUser(getStreamInfo(id))
                    user?.let { cache[id] = user!! }
                }
            }
        }

        return cache[id]?.user
    }
}

data class CachedUser(
    val user: IData<IStreamInfo?>,
    val timestamp: Instant = Instant.now(),
)
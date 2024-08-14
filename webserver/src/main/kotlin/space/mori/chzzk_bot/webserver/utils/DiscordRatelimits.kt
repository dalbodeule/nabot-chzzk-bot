package space.mori.chzzk_bot.webserver.utils

import java.time.Duration
import java.time.Instant

object DiscordRatelimits {
    private var rateLimit = RateLimit(0, 5, Instant.now())

    fun isLimited(): Boolean {
        return rateLimit.remainin == 0
    }

    fun getRateReset(): Long {
        val now = Instant.now()
        val resetInstant = rateLimit.resetAfter
        return if (resetInstant.isAfter(now)) {
            Duration.between(now, resetInstant).toMillis()
        } else {
            0L // 이미 Rate Limit이 해제된 경우, 대기 시간은 0
        }
    }

    private fun setRateLimit(rateLimit: RateLimit) {
        this.rateLimit = rateLimit
    }

    fun setRateLimit(limit: Int?, remaining: Int?, resetAfter: Long?) {
        return setRateLimit(RateLimit(limit ?: 0, remaining ?: 0, Instant.now().plusSeconds(resetAfter ?: 0L)))
    }
}

data class RateLimit(
    val limit: Int,
    val remainin: Int,
    val resetAfter: Instant,
)
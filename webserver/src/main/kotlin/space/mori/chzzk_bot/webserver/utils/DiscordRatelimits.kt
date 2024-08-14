package space.mori.chzzk_bot.webserver.utils

object DiscordRatelimits {
    private var rateLimit = RateLimit(0, 5, 0L)

    fun isLimited(): Boolean {
        return rateLimit.remainin == 0
    }

    fun getRateReset() = (rateLimit.resetAfter * 1000) + 300L

    private fun setRateLimit(rateLimit: RateLimit) {
        this.rateLimit = rateLimit
    }

    fun setRateLimit(limit: Int?, remaining: Int?, resetAfter: Long?) {
        return setRateLimit(RateLimit(limit ?: 0, remaining ?: 0, resetAfter ?: 0L))
    }
}

data class RateLimit(
    val limit: Int,
    val remainin: Int,
    val resetAfter: Long,
)
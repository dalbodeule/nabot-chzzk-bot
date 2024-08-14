package space.mori.chzzk_bot.webserver.utils

object DiscordRatelimits {
    private var rateLimit = RateLimit(0, 0, 0L)

    fun getRateLimit(): Boolean {
        return rateLimit.remainin != 0
    }

    fun getRateReset() = rateLimit.resetAfter

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
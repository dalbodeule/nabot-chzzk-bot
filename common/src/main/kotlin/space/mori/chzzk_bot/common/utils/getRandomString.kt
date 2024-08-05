package space.mori.chzzk_bot.common.utils

fun getRandomString(length: Int): String {
    val charPool = ('a'..'z') + ('0'..'9')
    return (1..length)
        .map { kotlin.random.Random.nextInt(0, charPool.size) }
        .map(charPool::get)
        .joinToString("")
}
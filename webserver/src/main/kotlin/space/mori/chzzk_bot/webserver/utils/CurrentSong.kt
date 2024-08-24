package space.mori.chzzk_bot.webserver.utils

import space.mori.chzzk_bot.common.models.User
import space.mori.chzzk_bot.common.utils.YoutubeVideo
import java.util.concurrent.ConcurrentHashMap

object CurrentSong {
    private val currentSong = ConcurrentHashMap<String, YoutubeVideo>()

    fun setSong(user: User, song: YoutubeVideo?) {
        if(song == null) {
            currentSong.remove(user.token ?: "")
        } else {
            currentSong[user.token ?: ""] = song
        }
    }

    fun getSong(user: User) = currentSong[user.token ?: ""]
}
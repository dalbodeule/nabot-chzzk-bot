package space.mori.chzzk_bot.common.utils

import com.google.gson.JsonObject
import io.github.cdimascio.dotenv.dotenv
import okhttp3.HttpUrl
import okhttp3.Request
import java.io.IOException

data class YoutubeVideo(
    val url: String,
    val name: String,
    val author: String,
    val length: Int
)

val regex = ".*(?:youtu.be/|v/|u/\\w/|embed/|watch\\?v=|&v=)([^#&?]*).*".toRegex()
val durationRegex = """PT(\d+H)?(\d+M)?(\d+S)?""".toRegex()

val dotenv = dotenv {
    ignoreIfMissing = true
}


fun getYoutubeVideoId(url: String): String? {
    val matchResult = regex.find(url)

    return matchResult?.groups?.get(1)?.value
}

fun parseDuration(duration: String): Int {
    println(duration)
    val matchResult = durationRegex.find(duration)
    val (hours, minutes, seconds) = matchResult?.destructured ?: return 0

    val hourInSec = hours.dropLast(1).toIntOrNull()?.times(3600) ?: 0
    val minutesInSec = minutes.dropLast(1).toIntOrNull()?.times(60) ?: 0
    val totalSeconds = seconds.dropLast(1).toIntOrNull() ?: 0

    return hourInSec + minutesInSec + totalSeconds
}

fun getYoutubeVideo(url: String): YoutubeVideo? {
    val videoId = getYoutubeVideoId(url)

    val api = HttpUrl.Builder()
        .scheme("https")
        .host("www.googleapis.com")
        .addPathSegment("youtube")
        .addPathSegment("v3")
        .addPathSegment("videos")
        .addQueryParameter("id", videoId)
        .addQueryParameter("key", dotenv["YOUTUBE_API_KEY"])
        .addQueryParameter("part", "snippet,contentDetails,status")
        .build()


    val request = Request.Builder()
        .url(api)
        .build()

    client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) throw IOException("Unexpected code $response")

        val responseBody = response.body?.string()
        val json = gson.fromJson(responseBody, JsonObject::class.java)
        val items = json.getAsJsonArray("items")

        if (items == null || items.size() == 0) return null

        println(json)

        val item = items[0].asJsonObject
        val snippet = item.getAsJsonObject("snippet")
        val contentDetail = item.getAsJsonObject("contentDetails")
        val status = item.getAsJsonObject("status")

        if (!status.get("embeddable").asBoolean) return null

        val duration = contentDetail.get("duration").asString
        val length = parseDuration(duration)

        return YoutubeVideo(
            "https://www.youtube.com/watch?v=$videoId",
            snippet.get("title").asString,
            snippet.get("channelTitle").asString,
            length
        )
    }
}
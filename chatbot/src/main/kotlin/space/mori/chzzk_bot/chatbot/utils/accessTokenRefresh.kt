package space.mori.chzzk_bot.chatbot.utils

import com.google.gson.Gson
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import space.mori.chzzk_bot.chatbot.chzzk.dotenv
import space.mori.chzzk_bot.common.utils.client
import xyz.r2turntrue.chzzk4j.ChzzkClient
import java.io.IOException

val client = OkHttpClient.Builder()
    .addNetworkInterceptor { chain ->
        chain.proceed(
            chain.request()
                .newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                .build()
        )
    }
    .build()
val gson = Gson()

data class RefreshTokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Int,
    val tokenType: String = "Bearer",
    val scope: String
)

fun ChzzkClient.refreshAccessToken(refreshToken: String): Pair<String, String> {
    val url = "https://openapi.chzzk.naver.com/auth/v1/token"
    val request = Request.Builder()
        .url(url)
        .header("Content-Type", "application/json")
        .post(gson.toJson(mapOf(
            "grantType" to "refresh_token",
            "refreshToken" to refreshToken,
            "clientId" to dotenv["NAVER_CLIENT_ID"],
            "clientSecret" to dotenv["NAVER_CLIENT_SECRET"]
        )).toRequestBody("application/json; charset=utf-8".toMediaType()))
        .build()

    client.newCall(request).execute().use { response ->
        try {
            if(!response.isSuccessful) throw IOException("Unexpected code ${response.code}")
            val body = response.body?.string()
            val data = gson.fromJson(body, RefreshTokenResponse::class.java)

            return Pair(data.accessToken, data.refreshToken)
        } catch(e: Exception) {
            throw e
        }
    }
}
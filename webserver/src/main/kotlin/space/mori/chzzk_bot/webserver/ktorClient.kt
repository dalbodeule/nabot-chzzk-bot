import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

@OptIn(ExperimentalSerializationApi::class)
val applicationHttpClient = HttpClient(CIO) {
    install(ContentNegotiation) {
        json(json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            explicitNulls = false
        })
    }
}
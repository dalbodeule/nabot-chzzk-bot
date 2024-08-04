package space.mori.chzzk_bot.webserver.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.java.KoinJavaComponent.inject
import space.mori.chzzk_bot.common.events.CoroutinesEventBus
import space.mori.chzzk_bot.common.events.GetUserEvents
import space.mori.chzzk_bot.common.events.GetUserType
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

@Serializable
data class GetUserDTO(
    val uid: String,
    val nickname: String,
    val isStreamOn: Boolean,
    val avatarUrl: String,
)

fun GetUserEvents.toDTO(): GetUserDTO {
    return GetUserDTO(
        this.uid!!,
        this.nickname!!,
        this.isStreamOn!!,
        this.avatarUrl!!
    )
}

fun Routing.apiRoutes() {
    val dispatcher: CoroutinesEventBus by inject(CoroutinesEventBus::class.java)
    val callMap = ConcurrentHashMap<String, ConcurrentLinkedQueue<ApplicationCall>>()

    fun addCall(uid: String, call: ApplicationCall) {
        callMap.computeIfAbsent(uid) { ConcurrentLinkedQueue() }.add(call)
    }

    fun removeCall(uid: String, call: ApplicationCall) {
        callMap[uid]?.remove(call)
        if(callMap[uid]?.isEmpty() == true) {
            callMap.remove(uid)
        }
    }

    route("/") {
        get {
            call.respondText("Hello World!", status = HttpStatusCode.OK)
        }
    }
    route("/health") {
        get {
            call.respondText("OK", status= HttpStatusCode.OK)
        }
    }
    route("/user") {
        get {
            call.respondText("Require UID", status = HttpStatusCode.NotFound)
        }
        get("{uid}") {
            val uid = call.parameters["uid"]
            if(uid != null) {
                addCall(uid, call)
                if(!callMap.containsKey(uid)) {
                    CoroutineScope(Dispatchers.Default).launch {
                        dispatcher.post(GetUserEvents(GetUserType.REQUEST, null, null, null, null))
                    }
                }
            }
        }
    }

    dispatcher.subscribe(GetUserEvents::class) {
        if(it.type == GetUserType.REQUEST) return@subscribe

        CoroutineScope(Dispatchers.Default). launch {
            if (it.type == GetUserType.NOTFOUND) {
                callMap[it.uid]?.forEach { call ->
                    call.respondText("User not found", status = HttpStatusCode.NotFound)
                    removeCall(it.uid ?: "", call)
                }
                return@launch
            }
            callMap[it.uid]?.forEach { call ->
                call.respond(HttpStatusCode.OK, it.toDTO())
                removeCall(it.uid ?: "", call)
            }
        }
    }
}
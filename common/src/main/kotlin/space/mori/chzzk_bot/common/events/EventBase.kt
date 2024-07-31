package space.mori.chzzk_bot.common.events

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlin.reflect.KClass

interface Event

interface EventBus {
    suspend fun <T: Event> post(event: T)
    fun <T: Event> subscribe(eventClass: KClass<T>, listener: (T) -> Unit)
}

class CoroutinesEventBus: EventBus {
    private val _events = MutableSharedFlow<Event>()
    val events: SharedFlow<Event> get() = _events

    override suspend fun<T: Event> post(event: T) = _events.emit(event)

    override fun <T: Event> subscribe(eventClass: KClass<T>, listener: (T) -> Unit) {
        CoroutineScope(Dispatchers.Default).launch {
            events.filterIsInstance(eventClass)
                .collect {
                    listener(it)
                }
        }
    }
}

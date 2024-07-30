package space.mori.chzzk_bot.common.events

interface Event

interface EventHandler<E: Event> {
    suspend fun handle(event: E)
}

object EventDispatcher {
    private val handlers = mutableMapOf<Class<out Event>, MutableList<EventHandler<out Event>>>()

    fun <E : Event> register(eventClass: Class<E>, handler: EventHandler<E>) {
        handlers.computeIfAbsent(eventClass) { mutableListOf() }.add(handler)
    }

    suspend fun <E : Event> dispatch(event: E) {
        handlers[event::class.java]?.forEach { (it as EventHandler<E>).handle(event) }
    }
}
package im.yaHustler.wyvern.event

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

abstract class Event

abstract class CancellableEvent : Event() {
    var isCancelled = false
        private set

    fun cancel() {
        isCancelled = true
    }
}

object Priority {
    const val HIGHEST = 200
    const val HIGH = 100
    const val NORMAL = 0
    const val LOW = -100
    const val LOWEST = -200
}

class Listener<T : Event>(
    val type: Class<T>,
    val priority: Int,
    private val handler: (T) -> Unit,
) {
    @Suppress("UNCHECKED_CAST")
    internal fun invoke(event: Event) = handler(event as T)
}

interface Listenable {
    val listeners: MutableList<Listener<*>>
}

inline fun <reified T : Event> Listenable.on(
    priority: Int = Priority.NORMAL,
    noinline handler: (T) -> Unit,
): Listener<T> = Listener(T::class.java, priority, handler).also(listeners::add)

object EventBus {
    private val registry = ConcurrentHashMap<Class<*>, CopyOnWriteArrayList<Listener<*>>>()

    fun subscribe(owner: Listenable) = owner.listeners.forEach(::subscribe)

    fun unsubscribe(owner: Listenable) = owner.listeners.forEach(::unsubscribe)

    fun subscribe(listener: Listener<*>) {
        val list = registry.computeIfAbsent(listener.type) { CopyOnWriteArrayList() }
        if (listener in list) return
        val index = list.indexOfFirst { it.priority < listener.priority }
        if (index == -1) list.add(listener) else list.add(index, listener)
    }

    fun unsubscribe(listener: Listener<*>) {
        registry[listener.type]?.remove(listener)
    }

    fun <T : Event> post(event: T): T {
        val list = registry[event.javaClass] ?: return event
        for (listener in list) {
            listener.invoke(event)
        }
        return event
    }
}

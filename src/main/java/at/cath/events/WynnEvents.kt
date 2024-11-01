package at.cath.events

import kotlinx.serialization.Serializable
import net.fabricmc.fabric.api.event.Event
import net.fabricmc.fabric.api.event.EventFactory
import net.minecraft.text.Text
import net.minecraft.util.ActionResult


@Serializable
sealed interface WynnEvent {
    val rawMsg: Text
}

fun interface EventMatcher<T : WynnEvent> {
    fun parse(message: Text): T?
}

object WynnEventRegistry {
    private val matchers = mutableMapOf<Class<out WynnEvent>, EventMatcher<*>>()

    fun <T : WynnEvent> register(eventClass: Class<T>, matcher: EventMatcher<T>) {
        matchers[eventClass] = matcher
    }

    fun matchEvent(message: Text): WynnEvent? = matchers.firstNotNullOfOrNull { (_, matcher) ->
        matcher.parse(message)
    }
}

fun interface WynnEventDispatcher {
    companion object {
        val EVENT: Event<WynnEventDispatcher> = EventFactory.createArrayBacked(
            WynnEventDispatcher::class.java
        ) { listeners ->
            WynnEventDispatcher { event: WynnEvent ->
                for (listener in listeners) {
                    listener.onWynnEvent(event)
                }
                ActionResult.PASS
            }
        }
    }

    fun onWynnEvent(event: WynnEvent)
}

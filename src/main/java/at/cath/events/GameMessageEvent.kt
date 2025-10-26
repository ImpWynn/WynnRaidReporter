package at.cath.events

import net.fabricmc.fabric.api.event.Event
import net.fabricmc.fabric.api.event.EventFactory


fun interface GameMessageEvent {

    companion object {
        val EVENT: Event<GameMessageEvent> = EventFactory.createArrayBacked(
            GameMessageEvent::class.java
        ) { listeners ->
            GameMessageEvent { message ->
                for (listener in listeners) {
                    listener.onMessage(message)
                }
            }
        }
    }

    fun onMessage(message: String)
}
package at.cath.events

import net.fabricmc.fabric.api.event.Event
import net.fabricmc.fabric.api.event.EventFactory
import net.minecraft.text.Text


fun interface AnyClientMessageEvent {

    companion object {
        val EVENT: Event<AnyClientMessageEvent> = EventFactory.createArrayBacked(
            AnyClientMessageEvent::class.java
        ) { listeners ->
            AnyClientMessageEvent { message ->
                for (listener in listeners) {
                    listener.onMessage(message)
                }
            }
        }
    }

    fun onMessage(message: Text)
}
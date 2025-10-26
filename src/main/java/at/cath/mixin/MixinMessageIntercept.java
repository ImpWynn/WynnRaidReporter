package at.cath.mixin;

import at.cath.events.GameMessageEvent;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(ClientPlayNetworkHandler.class)
public class MixinMessageIntercept {

    @Unique
    private static final String NEWLINE_REGEX = "\\s+";
    @Unique
    private static final String NON_PRINTABLE_REGEX = "\\p{C}";
    @Unique
    private static final String MULTI_SPACE_REGEX = " +";

    @Inject(method = "onGameMessage", at = @At("TAIL"))
    public void onGameMessage(GameMessageS2CPacket packet, CallbackInfo ci) {
        // this is a very rough cut, but we don't really care as long as the raid completion msg is identifiable
        String literalMsg = packet.content().getString()
                .replaceAll(NEWLINE_REGEX, " ")
                .replaceAll(NON_PRINTABLE_REGEX, "")
                .replaceAll(MULTI_SPACE_REGEX, " ")
                .trim();

       GameMessageEvent.Companion.getEVENT().invoker().onMessage(literalMsg);
    }
}
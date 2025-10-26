package at.cath.mixin;

import at.cath.events.GameMessageEvent;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;


@Mixin(ClientPlayNetworkHandler.class)
public class MixinMessageIntercept {

    @Unique
    private static final String NEWLINE_REGEX = "\\s+";
    @Unique
    private static final String NON_PRINTABLE_REGEX = "\\p{C}";
    @Unique
    private static final String MULTI_SPACE_REGEX = " +";

    @Unique
    private static String extractHoverName(String msg, Style style) {
        HoverEvent hoverEvent = style.getHoverEvent();
        if (hoverEvent == null) return null;

        Text hoverText = hoverEvent.getValue(HoverEvent.Action.SHOW_TEXT);
        if (hoverText == null) return null;

        String hoverString = hoverText.getString();
        int lastSpaceIndex = hoverString.lastIndexOf(' ');

        String hoverName;
        if (lastSpaceIndex == -1) {
            hoverName = msg;
        } else {
            hoverName = hoverString.substring(lastSpaceIndex + 1);
        }

        return hoverName;
    }

    @Inject(method = "onGameMessage", at = @At("TAIL"))
    public void onGameMessage(GameMessageS2CPacket packet, CallbackInfo ci) {
        StringBuilder stringBuilder = new StringBuilder();
        packet.content().visit((style, string) -> {
            String hoverName = extractHoverName(string, style);

            if (hoverName != null) stringBuilder.append(hoverName);
            else stringBuilder.append(string);

            return Optional.empty();
        }, Style.EMPTY);

        // this is a very rough cut, but we don't really care as long as the raid completion msg is identifiable
        String literalMsg = stringBuilder.toString()
                .replaceAll(NEWLINE_REGEX, " ")
                .replaceAll(NON_PRINTABLE_REGEX, "")
                .replaceAll(MULTI_SPACE_REGEX, " ")
                .trim();

        GameMessageEvent.Companion.getEVENT().invoker().onMessage(literalMsg);
    }
}
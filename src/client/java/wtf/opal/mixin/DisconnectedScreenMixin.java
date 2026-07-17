package wtf.opal.mixin;

import net.minecraft.client.gui.screen.DisconnectedScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wtf.opal.event.EventDispatcher;
import wtf.opal.event.impl.game.server.ServerDisconnectEvent;

import java.lang.reflect.Field;

@Mixin(DisconnectedScreen.class)
public final class DisconnectedScreenMixin {

    private DisconnectedScreenMixin() {
    }

    @Inject(
            method = "init",
            at = @At("HEAD")
    )
    private void injectDisconnectEvent(CallbackInfo ci) {
        String reason = extractReason((DisconnectedScreen) (Object) this);
        EventDispatcher.dispatch(new ServerDisconnectEvent(reason));
    }

    private static String extractReason(DisconnectedScreen screen) {
        try {
            Field reasonField = DisconnectedScreen.class.getDeclaredField("reason");
            reasonField.setAccessible(true);
            Object reason = reasonField.get(screen);
            if (reason instanceof net.minecraft.text.Text text) {
                return text.getString();
            }
        } catch (Exception ignored) {
        }
        return "";
    }
}

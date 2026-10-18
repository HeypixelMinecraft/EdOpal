package wtf.opal.mixin;

import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wtf.opal.client.feature.helper.impl.AltManagerScreen;

import static wtf.opal.client.Constants.mc;

@Mixin(MultiplayerScreen.class)
public abstract class MultiplayerScreenMixin {

    private MultiplayerScreenMixin() {
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void addAccountButton(CallbackInfo ci) {
        ButtonWidget accountButton = ButtonWidget.builder(Text.literal("Account"), button -> {
            mc.setScreen(new AltManagerScreen());
        }).dimensions(10, 10, 80, 20).build();

        MultiplayerScreen screen = (MultiplayerScreen) (Object) this;
        try {
            java.lang.reflect.Method[] methods = net.minecraft.client.gui.screen.Screen.class.getDeclaredMethods();
            for (java.lang.reflect.Method method : methods) {
                if (method.getName().equals("addDrawableChild") && method.getParameterCount() == 1) {
                    method.setAccessible(true);
                    method.invoke(screen, accountButton);
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
package wtf.opal.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wtf.opal.client.OpalClient;
import wtf.opal.client.feature.module.impl.utility.inventory.ChestStealerModule;
import wtf.opal.utility.player.InventoryUtility;

import java.awt.*;

import static wtf.opal.client.Constants.mc;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin<T extends ScreenHandler> {

    @Shadow @Final protected T handler;

    @Inject(
            method = "drawSlot",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawStackOverlay(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/item/ItemStack;IILjava/lang/String;)V")
    )
    private void hookStackOverlay(DrawContext context, Slot slot, CallbackInfo ci) {
        if (!(mc.currentScreen instanceof GenericContainerScreen container)) return;
        if (!(this.handler instanceof GenericContainerScreenHandler)) return;
        if (mc.player.getInventory() == null || slot.inventory == mc.player.getInventory()) return;

        if (!container.getTitle().getString().toLowerCase().contains("chest")) return;

        final ItemStack stack = slot.getStack();
        if (stack.isEmpty()) return;

        final ChestStealerModule chestStealer = OpalClient.getInstance()
                .getModuleRepository()
                .getModule(ChestStealerModule.class);

        if (!chestStealer.isEnabled() || !chestStealer.getHighlight().getValue()) return;

        if (chestStealer.getSkipTrash().getValue()) {
            if (InventoryUtility.isNotSpecialItem(stack)) return;
        }

        final int color = new Color(200, 200, 200, 50).getRGB();
        context.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, color);
    }

}

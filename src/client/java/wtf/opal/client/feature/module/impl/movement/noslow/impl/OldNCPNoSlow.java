package wtf.opal.client.feature.module.impl.movement.noslow.impl;

import wtf.opal.client.feature.module.impl.movement.noslow.NoSlowModule;
import wtf.opal.client.feature.module.property.impl.mode.ModuleMode;
import wtf.opal.event.impl.game.input.MoveInputEvent;
import wtf.opal.event.impl.game.player.movement.SlowdownEvent;
import wtf.opal.event.subscriber.Subscribe;

import static wtf.opal.client.Constants.mc;

public final class OldNCPNoSlow extends ModuleMode<NoSlowModule> {

    public OldNCPNoSlow(final NoSlowModule module) {
        super(module);
    }

    @Subscribe
    public void onSlowdown(final SlowdownEvent event) {
        event.setCancelled();
    }

    @Subscribe
    public void onMoveInput(final MoveInputEvent event) {
        if (mc.player == null || mc.currentScreen != null || mc.getOverlay() != null) {
            return;
        }

        if (mc.player.isUsingItem() && module.getAction() != NoSlowModule.Action.NONE) {
            float multiplier = 1.0F;

            if (module.getAction() == NoSlowModule.Action.BLOCKABLE) {
                multiplier = 1.0F;
            } else if (module.getAction() == NoSlowModule.Action.USEABLE) {
                multiplier = 1.0F;
            } else if (module.getAction() == NoSlowModule.Action.BOW) {
                multiplier = 1.0F;
            }

            event.setForward(event.getForward() * multiplier);
            event.setSideways(event.getSideways() * multiplier);

            if (module.isSprintingAllowed() && mc.player.getHungerManager().getFoodLevel() > 6 && mc.player.isOnGround()) {
                mc.player.setSprinting(true);
            }
        }
    }

    @Override
    public Enum<?> getEnumValue() {
        return NoSlowModule.Mode.OLDNCP;
    }

}
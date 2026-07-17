package wtf.opal.client.feature.module.impl.combat.velocity.impl;

import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import wtf.opal.client.feature.helper.impl.player.packet.blockage.block.holder.BlockHolder;
import wtf.opal.client.feature.helper.impl.player.packet.blockage.impl.InboundNetworkBlockage;
import wtf.opal.client.feature.helper.impl.player.packet.blockage.impl.OutboundNetworkBlockage;
import wtf.opal.client.feature.module.impl.combat.velocity.Velocity2Mode;
import wtf.opal.client.feature.module.impl.combat.velocity.Velocity2Module;
import wtf.opal.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.opal.client.feature.module.property.impl.number.BoundedNumberProperty;
import wtf.opal.client.feature.module.property.impl.number.NumberProperty;
import wtf.opal.event.impl.game.PreGameTickEvent;
import wtf.opal.event.impl.game.input.MoveInputEvent;
import wtf.opal.event.impl.game.packet.ReceivePacketEvent;
import wtf.opal.event.impl.game.packet.SendPacketEvent;
import wtf.opal.event.impl.game.player.movement.knockback.VelocityUpdateEvent;
import wtf.opal.event.subscriber.Subscribe;
import wtf.opal.mixin.LivingEntityAccessor;

import java.util.ArrayDeque;
import java.util.Deque;

import static wtf.opal.client.Constants.mc;

public final class Velocity2LagMode extends Velocity2Mode {

    private final NumberProperty horizontal = new NumberProperty("Horizontal", "%", 0, 0, 100, 1).hideIf(() -> this.module.getActiveMode() != this);
    private final NumberProperty vertical = new NumberProperty("Vertical", "%", 100, 0, 100, 1).hideIf(() -> this.module.getActiveMode() != this);
    private final BoundedNumberProperty breadDelay = new BoundedNumberProperty("BreadDelay", "ticks", 1, 1, 0, 5, 1).hideIf(() -> this.module.getActiveMode() != this);
    private final BoundedNumberProperty breadCount = new BoundedNumberProperty("BreadCount", "packets", 2, 2, 0, 3, 1).hideIf(() -> this.module.getActiveMode() != this);
    private final BooleanProperty jumpReset = new BooleanProperty("JumpReset", false).hideIf(() -> this.module.getActiveMode() != this);
    private final BooleanProperty considerExplosion = new BooleanProperty("ConsiderExplosion", true).hideIf(() -> this.module.getActiveMode() != this);

    public Velocity2LagMode(Velocity2Module module) {
        super(module);
        module.addProperties(horizontal, vertical, breadDelay, breadCount, jumpReset, considerExplosion);
    }

    private final BlockHolder inboundBlockHolder = new BlockHolder(InboundNetworkBlockage.get());
    private final Deque<net.minecraft.network.packet.c2s.common.CommonPongC2SPacket> queuedPongs = new ArrayDeque<>();

    private boolean shouldLag = false;
    private int lagTicks = 0;
    private boolean shouldJump = false;

    private boolean breadDelayed = false;
    private int breadDelayTicks = 0;
    private int breadsToDelay = 0;
    private int breadsDelayed = 0;

    private boolean velocityReceived = false;
    private int velocityTick = 0;

    @Subscribe
    public void onReceivePacket(final ReceivePacketEvent event) {
        if (mc.player == null) return;

        if (event.getPacket() instanceof EntityVelocityUpdateS2CPacket velocity) {
            if (velocity.getEntityId() == mc.player.getId()) {
                if (!isExplosionVelocity(velocity) || considerExplosion.getValue()) {
                    velocityReceived = true;
                    velocityTick = mc.player.age;

                    int bd = breadDelay.getValue().first.intValue();
                    int bc = breadCount.getValue().first.intValue();

                    if (bd > 0 && bc > 0) {
                        shouldLag = true;
                        lagTicks = bd;

                        breadDelayed = true;
                        breadDelayTicks = bd;
                        breadsToDelay = bc;
                        breadsDelayed = 0;
                    }
                }
            }
        }
    }

    @Subscribe
    public void onSendPacket(final SendPacketEvent event) {
        if (breadDelayed && breadsDelayed < breadsToDelay && event.getPacket() instanceof net.minecraft.network.packet.c2s.common.CommonPongC2SPacket pong) {
            event.setCancelled();
            queuedPongs.add(pong);
            breadsDelayed++;
        }
    }

    @Subscribe
    public void onVelocityUpdate(final VelocityUpdateEvent event) {
        if (mc.player == null) {
            return;
        }

        if (event.isExplosion() && !considerExplosion.getValue()) {
            return;
        }

        final double h = horizontal.getValue() / 100;
        final double v = vertical.getValue() / 100;

        event.setCancelled();

        if (h == 0 && v == 0) {
            return;
        }

        mc.player.setVelocity(
                event.getVelocityX() * h,
                event.getVelocityY() * v,
                event.getVelocityZ() * h
        );
    }

    @Subscribe
    public void onPreGameTick(final PreGameTickEvent event) {
        if (mc.player == null || mc.currentScreen != null) {
            resetAll();
            return;
        }

        if (shouldLag) {
            inboundBlockHolder.block(null, InboundNetworkBlockage.VISUAL_VALIDATOR);
            lagTicks--;

            if (lagTicks <= 0) {
                shouldLag = false;
                inboundBlockHolder.release();
                shouldJump = true;
            }
        }

        if (breadDelayed) {
            breadDelayTicks--;

            if (breadDelayTicks <= 0) {
                breadDelayed = false;
                while (!queuedPongs.isEmpty()) {
                    OutboundNetworkBlockage.sendPacketDirect(queuedPongs.poll());
                }
            }
        }

        if (!shouldLag && !breadDelayed) {
            inboundBlockHolder.release();
        }

        if (velocityReceived && mc.player.age - velocityTick > 20) {
            velocityReceived = false;
        }
    }

    @Subscribe
    public void onMoveInput(final MoveInputEvent event) {
        if (jumpReset.getValue() && shouldJump && mc.player.isOnGround() && mc.player.isSprinting()) {
            ((LivingEntityAccessor) mc.player).setJumpingCooldown(0);
            event.setJump(true);
            shouldJump = false;
        }
    }

    private boolean isExplosionVelocity(EntityVelocityUpdateS2CPacket velocity) {
        return false;
    }

    private void resetAll() {
        shouldLag = false;
        breadDelayed = false;
        velocityReceived = false;
        lagTicks = 0;
        breadDelayTicks = 0;
        breadsToDelay = 0;
        breadsDelayed = 0;
        velocityTick = 0;
        shouldJump = false;
        queuedPongs.clear();
        inboundBlockHolder.release();
    }

    @Override
    public void onDisable() {
        resetAll();
        super.onDisable();
    }

    @Override
    public String getSuffix() {
        int bd = breadDelay.getValue().first.intValue();
        int bc = breadCount.getValue().first.intValue();
        return horizontal.getValue().intValue() + " " + vertical.getValue().intValue() + (bd > 0 ? " Bread " + bd + "t " + bc + "x" : "");
    }

    @Override
    public Enum<?> getEnumValue() {
        return Velocity2Module.Mode.LAG;
    }
}
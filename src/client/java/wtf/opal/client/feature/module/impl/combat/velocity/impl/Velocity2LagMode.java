package wtf.opal.client.feature.module.impl.combat.velocity.impl;

import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import wtf.opal.client.feature.helper.impl.player.packet.blockage.block.holder.BlockHolder;
import wtf.opal.client.feature.helper.impl.player.packet.blockage.impl.InboundNetworkBlockage;
import wtf.opal.client.feature.helper.impl.player.packet.blockage.impl.OutboundNetworkBlockage;
import wtf.opal.client.feature.module.impl.combat.velocity.Velocity2Mode;
import wtf.opal.client.feature.module.impl.combat.velocity.Velocity2Module;
import wtf.opal.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.opal.client.feature.module.property.impl.number.BoundedNumberProperty;
import wtf.opal.event.impl.game.PreGameTickEvent;
import wtf.opal.event.impl.game.input.MoveInputEvent;
import wtf.opal.event.impl.game.packet.ReceivePacketEvent;
import wtf.opal.event.impl.game.packet.SendPacketEvent;
import wtf.opal.event.subscriber.Subscribe;

import java.util.ArrayDeque;
import java.util.Deque;

import static wtf.opal.client.Constants.mc;

public final class Velocity2LagMode extends Velocity2Mode {

    private final BoundedNumberProperty lagTime = new BoundedNumberProperty("LagTime", "ticks", 5, 5, 1, 20, 1).hideIf(() -> this.module.getActiveMode() != this);
    private final BoundedNumberProperty breadDelay = new BoundedNumberProperty("BreadDelay", "ticks", 3, 3, 0, 10, 1).hideIf(() -> this.module.getActiveMode() != this);
    private final BoundedNumberProperty breadCount = new BoundedNumberProperty("BreadCount", "packets", 2, 2, 1, 5, 1).hideIf(() -> this.module.getActiveMode() != this);
    private final BooleanProperty jumpReset = new BooleanProperty("JumpReset", false).hideIf(() -> this.module.getActiveMode() != this);
    private final BooleanProperty considerExplosion = new BooleanProperty("ConsiderExplosion", true).hideIf(() -> this.module.getActiveMode() != this);
    private final BooleanProperty delayBread = new BooleanProperty("DelayBread", true).hideIf(() -> this.module.getActiveMode() != this);

    public Velocity2LagMode(Velocity2Module module) {
        super(module);
        module.addProperties(lagTime, breadDelay, breadCount, jumpReset, considerExplosion, delayBread);
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

    @Subscribe
    public void onReceivePacket(final ReceivePacketEvent event) {
        if (mc.player == null) return;

        if (event.getPacket() instanceof EntityVelocityUpdateS2CPacket velocity) {
            if (velocity.getEntityId() == mc.player.getId()) {
                if (!isExplosionVelocity(velocity) || considerExplosion.getValue()) {
                    shouldLag = true;
                    lagTicks = lagTime.getValue().first.intValue();

                    if (delayBread.getValue()) {
                        breadDelayed = true;
                        breadDelayTicks = breadDelay.getValue().first.intValue();
                        breadsToDelay = breadCount.getValue().first.intValue();
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
    }

    @Subscribe
    public void onMoveInput(final MoveInputEvent event) {
        if (jumpReset.getValue() && shouldJump && mc.player.isOnGround() && mc.player.isSprinting()) {
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
        lagTicks = 0;
        breadDelayTicks = 0;
        breadsToDelay = 0;
        breadsDelayed = 0;
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
        return "Lag " + lagTime.getValue().first.intValue() + "t " + (delayBread.getValue() ? "Bread " + breadDelay.getValue().first.intValue() + "t " + breadCount.getValue().first.intValue() + "x" : "");
    }

    @Override
    public Enum<?> getEnumValue() {
        return Velocity2Module.Mode.LAG;
    }
}
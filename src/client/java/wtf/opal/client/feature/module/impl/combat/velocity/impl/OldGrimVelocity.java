package wtf.opal.client.feature.module.impl.combat.velocity.impl;

import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.hit.BlockHitResult;
import wtf.opal.client.feature.helper.impl.player.packet.blockage.block.holder.BlockHolder;
import wtf.opal.client.feature.helper.impl.player.packet.blockage.impl.InboundNetworkBlockage;
import wtf.opal.client.feature.module.impl.combat.velocity.VelocityMode;
import wtf.opal.client.feature.module.impl.combat.velocity.VelocityModule;
import wtf.opal.event.impl.game.packet.InstantaneousReceivePacketEvent;
import wtf.opal.event.impl.game.packet.ReceivePacketEvent;
import wtf.opal.event.impl.game.PreGameTickEvent;
import wtf.opal.event.subscriber.Subscribe;
import wtf.opal.utility.player.RaycastUtility;
import wtf.opal.utility.player.RotationUtility;

import static wtf.opal.client.Constants.mc;

public final class OldGrimVelocity extends VelocityMode {

    private final BlockHolder iBlockHolder = new BlockHolder(InboundNetworkBlockage.get());

    private boolean cancelNextVelocity = false;
    private boolean delay = false;
    private boolean needClick = false;
    private boolean waitForUpdate = false;
    private boolean waitForPing = false; // we don't have explicit ping hook, kept for parity
    private BlockHitResult hitResult = null;
    private boolean shouldSkip = false;
    private int freezeTicks = 0;
    private static final int MAX_FREEZE_TICKS = 20;

    public OldGrimVelocity(VelocityModule module) {
        super(module);
    }

    @Subscribe
    public void onInstantReceive(InstantaneousReceivePacketEvent event) {
        if (event.getPacket() instanceof EntityVelocityUpdateS2CPacket velocity) {
            if (mc.player == null || velocity.getEntityId() != mc.player.getId()) return;

            // mark that next velocity should be cancelled only when damage packet detected
            // here we approximate: if player.hurtTime just set or velocity applied
            if (!delay) {
                // start blocking inbound packets to queue visuals
                iBlockHolder.block(null, InboundNetworkBlockage.VISUAL_VALIDATOR);
                delay = false; // keep as is until velocity packet handled
            }
        } else if (event.getPacket() instanceof GameJoinS2CPacket) {
            // reset
            cancelNextVelocity = false;
            delay = false;
            needClick = false;
            waitForUpdate = false;
            hitResult = null;
            shouldSkip = false;
            freezeTicks = 0;
        }
    }

    @Subscribe
    public void onReceive(ReceivePacketEvent event) {
        if (event.getPacket() instanceof EntityVelocityUpdateS2CPacket v) {
            if (mc.player != null && v.getEntityId() == mc.player.getId()) {
                // cancel the velocity packet and start our delay flow
                event.setCancelled();
                delay = true;
                cancelNextVelocity = false;
                needClick = true;
                // start blocking inbound visuals
                iBlockHolder.block(null, InboundNetworkBlockage.VISUAL_VALIDATOR);
            }
        }
    }

    @Subscribe
    public void onPreGameTick(PreGameTickEvent event) {
        if (needClick && !shouldSkip && !mc.player.isUsingItem()) {
            // perform raycast downwards to find block adjacent
            HitResult hit = RaycastUtility.raycastBlock(mc.player.getBlockInteractionRange(), 1.0F, false, mc.player.getYaw(), 90.0F);
            if (hit instanceof BlockHitResult result) {
                if (result.getBlockPos().offset(result.getSide()).equals(mc.player.getBlockPos())) {
                    hitResult = result;
                }
            }
        }

        if (hitResult != null) {
            delay = false;
            // flush queued inbound packets
            iBlockHolder.release();

            // try use item on the block
            mc.getNetworkHandler().sendPacket(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, hitResult, 0));
            mc.player.swingHand(Hand.MAIN_HAND);
            // optionally send a swing packet to ensure server-side processing
            mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));

            freezeTicks = 0;
            waitForUpdate = true;
            hitResult = null;
            needClick = false;
        }

        if (waitForUpdate) {
            // block player movement processing by cancelling tick-related actions — here we approximate by increasing freezeTicks
            freezeTicks++;
            if (freezeTicks > MAX_FREEZE_TICKS) {
                waitForUpdate = false;
                waitForPing = false;
                needClick = false;
            }
        }

        shouldSkip = false;
    }

    @Override
    public void onDisable() {
        iBlockHolder.release();
        super.onDisable();
    }

    @Override
    public Enum<?> getEnumValue() {
        return VelocityModule.Mode.OLDGRIM;
    }
}

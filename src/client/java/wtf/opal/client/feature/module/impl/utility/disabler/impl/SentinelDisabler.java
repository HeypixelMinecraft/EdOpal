package wtf.opal.client.feature.module.impl.utility.disabler.impl;

import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.common.CommonPongC2SPacket;
import net.minecraft.network.packet.c2s.common.KeepAliveC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import wtf.opal.client.feature.module.impl.utility.disabler.DisablerModule;
import wtf.opal.client.feature.module.property.impl.mode.ModuleMode;
import wtf.opal.event.impl.game.packet.InstantaneousSendPacketEvent;
import wtf.opal.event.impl.game.packet.ReceivePacketEvent;
import wtf.opal.event.impl.game.PreGameTickEvent;
import wtf.opal.event.subscriber.Subscribe;
import wtf.opal.utility.misc.time.Stopwatch;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static wtf.opal.client.Constants.mc;

public final class SentinelDisabler extends ModuleMode<DisablerModule> {
    private final Queue<PacketEntry> packetQueue = new ConcurrentLinkedQueue<>();
    private boolean lag = false;
    private final Stopwatch disableTimer = new Stopwatch();
    private final Stopwatch waitTimer = new Stopwatch();
    private boolean waiting = false;

    public SentinelDisabler(DisablerModule module) {
        super(module);
    }

    @Override
    public Enum<?> getEnumValue() {
        return DisablerModule.Mode.SENTINEL;
    }

    @Override
    public void onEnable() {
        packetQueue.clear();
        lag = false;
        waiting = false;
        disableTimer.reset();
        waitTimer.reset();
    }

    @Override
    public void onDisable() {
        while (!packetQueue.isEmpty()) {
            PacketEntry entry = packetQueue.poll();
            if (mc.getNetworkHandler() != null) {
                mc.getNetworkHandler().sendPacket(entry.getPacket());
            }
        }
        super.onDisable();
    }

    @Subscribe
    public void onReceivePacket(final ReceivePacketEvent event) {
        if (mc.player == null) return;
        Packet<?> packet = event.getPacket();
        if (packet instanceof PlayerPositionLookS2CPacket) {
            waitTimer.reset();
            lag = true;

            while (!packetQueue.isEmpty()) {
                PacketEntry entry = packetQueue.poll();
                if (mc.getNetworkHandler() != null) {
                    mc.getNetworkHandler().sendPacket(entry.getPacket());
                }
            }
        }
    }

    @Subscribe
    public void onInstantaneousSendPacket(final InstantaneousSendPacketEvent event) {
        if (mc.player == null) return;
        if (isWaiting()) return;

        Packet<?> packet = event.getPacket();

        if (packet instanceof ClientCommandC2SPacket pac) {
            if (pac.getMode() == ClientCommandC2SPacket.Mode.START_SPRINTING) {
                event.setCancelled();
            }
        }

        if (packet instanceof KeepAliveC2SPacket || packet instanceof CommonPongC2SPacket) {
            packetQueue.add(new PacketEntry(packet));
            event.setCancelled();
        }
    }

    private boolean isWaiting() {
        if (mc.player == null) return false;
        return lag && !waitTimer.hasTimeElapsed(5000L);
    }

    @Subscribe
    public void onPreGameTick(final PreGameTickEvent event) {
        if (mc.player == null) return;

        boolean waiting = isWaiting();
        if (this.waiting && !waiting) {
            disableTimer.reset();
        }
        this.waiting = waiting;

        while (!packetQueue.isEmpty()) {
            PacketEntry entry = packetQueue.peek();

            if (entry.getTimer().hasTimeElapsed(2000L)) {
                packetQueue.poll();
                if (mc.getNetworkHandler() != null) {
                    mc.getNetworkHandler().sendPacket(entry.getPacket());
                }
            } else {
                break;
            }
        }
    }

    private static class PacketEntry {
        private final Packet<?> packet;
        private final Stopwatch timer;

        public PacketEntry(Packet<?> packet) {
            this.packet = packet;
            this.timer = new Stopwatch();
        }

        public Packet<?> getPacket() {
            return packet;
        }

        public Stopwatch getTimer() {
            return timer;
        }
    }
}
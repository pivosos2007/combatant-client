package combatant.client.util.player;

import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.events.impl.PacketEvent;
import combatant.client.events.impl.WorldChangeEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;
import java.util.UUID;
import net.minecraft.world.entity.player.Player;

public final class TeleportImpulseTracker {
    public static final TeleportImpulseTracker INSTANCE = new TeleportImpulseTracker();

    public enum Kind { PEARL, CHORUS, COMMAND, GENERIC, CORRECTION }

    public record Applied(long sequence, Kind kind, Vec3 from, Vec3 to, Vec3 target, int tick) { }

    private int pearlTick = Integer.MIN_VALUE;
    private int chorusTick = Integer.MIN_VALUE;
    private int commandTick = Integer.MIN_VALUE;
    private int targetPearlTick = Integer.MIN_VALUE;
    private int previousTeleportTick = Integer.MIN_VALUE;
    private Vec3 previousOrigin;
    private Vec3 targetPearl;
    private UUID targetPearlOwner;
    private boolean targetPearlRequireLanding = true;
    private UUID activeTargetOwner;
    private Vec3 activeTargetImpact;
    private boolean activeTargetRequireLanding;
    private int activeTargetUntil = Integer.MIN_VALUE;
    private Vec3 pendingOrigin;
    private ClientboundPlayerPositionPacket pendingPacket;
    private long sequence;
    private Applied latest;

    private TeleportImpulseTracker() { }

    @EventHandler
    private void onTick(GameTickEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        if (player.isUsingItem() && player.getUseItem().is(Items.CHORUS_FRUIT)) {
            chorusTick = player.tickCount;
        }
    }

    @EventHandler
    private void onSend(PacketEvent.Send event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        if (event.getPacket() instanceof ServerboundUseItemPacket packet) {
            ItemStack item = packet.hand() == InteractionHand.MAIN_HAND
                    ? player.getMainHandItem() : player.getOffhandItem();
            if (item.is(Items.ENDER_PEARL)) pearlTick = player.tickCount;
            else if (item.is(Items.CHORUS_FRUIT)) chorusTick = player.tickCount;
        } else if (event.getPacket() instanceof ServerboundChatCommandPacket packet
                && isTeleportCommand(packet.command())) {
            commandTick = player.tickCount;
        }
    }

    @EventHandler(priority = -100)
    private void onReceive(PacketEvent.Receive event) {
        if (!(event.getPacket() instanceof ClientboundPlayerPositionPacket packet) || event.isCancelled()) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        pendingPacket = packet;
        pendingOrigin = player.position();
    }

    @EventHandler
    private void onReceivePost(PacketEvent.ReceivePost event) {
        if (!(event.getPacket() instanceof ClientboundPlayerPositionPacket packet)) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        Vec3 from = pendingPacket == packet && pendingOrigin != null ? pendingOrigin : player.position();
        pendingPacket = null;
        pendingOrigin = null;
        applied(from, player.position(), player.tickCount, false);
    }

    @EventHandler
    private void onWorldChange(WorldChangeEvent event) {
        pearlTick = Integer.MIN_VALUE;
        chorusTick = Integer.MIN_VALUE;
        commandTick = Integer.MIN_VALUE;
        targetPearlTick = Integer.MIN_VALUE;
        previousTeleportTick = Integer.MIN_VALUE;
        previousOrigin = null;
        targetPearl = null;
        targetPearlOwner = null;
        activeTargetOwner = null;
        activeTargetImpact = null;
        activeTargetUntil = Integer.MIN_VALUE;
        pendingOrigin = null;
        pendingPacket = null;
        latest = null;
    }

    public void markTargetPearl(Vec3 target, UUID ownerId, int tick, boolean requireLanding) {
        if (target == null || ownerId == null) return;
        targetPearl = target;
        targetPearlOwner = ownerId;
        targetPearlRequireLanding = requireLanding;
        targetPearlTick = tick;
        pearlTick = tick;
    }

    public Vec3 targetAfterLanding(int tick) {
        if (activeTargetOwner == null || activeTargetImpact == null || tick > activeTargetUntil) return null;
        LocalPlayer player = Minecraft.getInstance().player;
        Player target = player == null ? null : player.level().getPlayerByUUID(activeTargetOwner);
        if (target == null || !target.isAlive()
                || (activeTargetRequireLanding && !target.onGround())
                || target.position().distanceToSqr(activeTargetImpact) > 49.0D) return null;
        return target.position();
    }

    public void markChorus(int tick) {
        chorusTick = tick;
    }

    public void manualChorusTeleport(Vec3 from, Vec3 to, int tick) {
        chorusTick = tick;
        pendingPacket = null;
        pendingOrigin = null;
        applied(from, to, tick, true);
    }

    public Applied latest() {
        return latest;
    }

    private void applied(Vec3 from, Vec3 to, int tick, boolean chorusRelease) {
        if (from == null || to == null || !Double.isFinite(to.x)
                || !Double.isFinite(to.y) || !Double.isFinite(to.z)) return;
        double distance = from.distanceTo(to);
        boolean recent = previousTeleportTick != Integer.MIN_VALUE && tick >= previousTeleportTick
                && tick - previousTeleportTick < 25;
        boolean rollback = previousOrigin != null && previousTeleportTick != Integer.MIN_VALUE
                && tick >= previousTeleportTick && tick - previousTeleportTick < 100
                && to.distanceToSqr(previousOrigin) < 6.25;
        Kind kind;
        if (distance < 0.75 || recent || rollback) {
            kind = Kind.CORRECTION;
        } else if (inWindow(pearlTick, tick, 100)) {
            kind = Kind.PEARL;
        } else if (chorusRelease || inWindow(chorusTick, tick, 35)) {
            kind = Kind.CHORUS;
        } else if (inWindow(commandTick, tick, 120)) {
            kind = Kind.COMMAND;
        } else if (distance >= 3.0) {
            kind = Kind.GENERIC;
        } else {
            kind = Kind.CORRECTION;
        }
        if (kind == Kind.PEARL && inWindow(targetPearlTick, tick, 100)
                && targetPearl != null && targetPearlOwner != null
                && to.distanceToSqr(targetPearl) < 144.0D) {
            activeTargetOwner = targetPearlOwner;
            activeTargetImpact = targetPearl;
            activeTargetRequireLanding = targetPearlRequireLanding;
            activeTargetUntil = tick + 12;
        } else {
            activeTargetOwner = null;
            activeTargetImpact = null;
            activeTargetUntil = Integer.MIN_VALUE;
        }
        Vec3 target = targetAfterLanding(tick);
        latest = new Applied(++sequence, kind, from, to, target, tick);
        previousOrigin = from;
        previousTeleportTick = tick;
        if (kind != Kind.CORRECTION) {
            pearlTick = Integer.MIN_VALUE;
            chorusTick = Integer.MIN_VALUE;
            commandTick = Integer.MIN_VALUE;
            targetPearlTick = Integer.MIN_VALUE;
            targetPearl = null;
            targetPearlOwner = null;
        }
    }

    private static boolean inWindow(int since, int tick, int window) {
        return since != Integer.MIN_VALUE && tick >= since && tick - since <= window;
    }

    private static boolean isTeleportCommand(String raw) {
        if (raw == null || raw.isBlank()) return false;
        String command = raw.strip().toLowerCase(Locale.ROOT);
        int split = command.indexOf(' ');
        String name = split < 0 ? command : command.substring(0, split);
        int namespace = name.indexOf(':');
        if (namespace >= 0) name = name.substring(namespace + 1);
        return switch (name) {
            case "tp", "teleport", "tpaccept", "tpyes", "home", "spawn", "warp", "back",
                    "rtp", "wild", "randomtp", "hub", "lobby", "island", "return", "goto",
                    "travel", "visit" -> true;
            default -> false;
        };
    }
}

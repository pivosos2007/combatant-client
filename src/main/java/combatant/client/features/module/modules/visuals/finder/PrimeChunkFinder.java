/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.visuals.finder;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.RGBAColorValue;
import combatant.client.events.EventHandler;
import combatant.client.events.Events;
import combatant.client.events.impl.PacketEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.WorldPhase;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.util.finder.FinderAlerts;
import combatant.client.util.finder.FinderRender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.level.block.DaylightDetectorBlock;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.PoweredBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.RedstoneTorchBlock;
import net.minecraft.world.level.block.TargetBlock;
import net.minecraft.world.level.block.TripWireHookBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Flags chunks where someone is building below Y=0 by watching the server's light and block updates.
 *
 * <p>Deepslate bases are invisible to xray-hiding servers, but the server keeps sending block light for them. Each
 * chunk's light is remembered from its first packet; a later light update that changes a nibble below Y=0 and that
 * is not explained by natural sources (amethyst, redstone ore, torches and glow berries) means a new light source,
 * so the chunk is flagged. Redstone components appearing or disappearing below Y=0 flag their chunk too. Chunks you
 * have stood in yourself are ignored, since your own torches would otherwise light them up.</p>
 */
@ModuleInfo(
        id = "primechunkfinder",
        displayName = "PrimeChunkFinder",
        aliases = {"DeepslateFinder", "LightChangeFinder"},
        category = ModuleCategory.VISUALS, subcategory = ModuleSubcategory.ESP,
        description = "module.primechunkfinder.description")
public final class PrimeChunkFinder extends Module {

    private static final int SECTION_BYTES = 2048;
    private static final int MAX_FLAGGED = 256;
    private static final int MAX_TRACKED_CHUNKS = 5000;
    private static final int SNAPSHOT_RADIUS = 32;
    private static final int MAX_EXPLAIN_CHECKS = 48;
    private static final int AMETHYST_LIGHT = 5;
    private static final int ORE_LIGHT = 9;
    private static final int TORCH_LIGHT = 14;
    private static final long GONE_TORCH_MS = 5000L;
    private static final long REDSTONE_ALERT_COOLDOWN_MS = 15000L;
    private static final double PLATE_Y = 63.0;
    private static final byte[] ZERO_SECTION = new byte[SECTION_BYTES];

    private final Minecraft mc = Minecraft.getInstance();

    private final RGBAColorValue fillColor = color("fill_color", "#40D8D8FF");
    private final RGBAColorValue lineColor = color("line_color", "#FFD8D8FF");
    private final BooleanValue tracers = bool("tracers", true);
    private final BooleanValue chatAlerts = bool("chat_alerts", true);
    private final BooleanValue soundAlerts = bool("sound_alerts", true);
    private final BooleanValue watchRedstone = bool("watch_redstone", true);

    // Per chunk: light section Y -> last known 2048-byte block-light nibble array. Access-ordered so trimming drops
    // the chunks seen least recently.
    private final LinkedHashMap<Long, Map<Integer, byte[]>> chunkLight = new LinkedHashMap<>(256, 0.75f, true);
    private final LinkedHashSet<Long> flagged = new LinkedHashSet<>();
    private final java.util.HashSet<Long> passed = new java.util.HashSet<>();
    private final Map<Long, Long> redstoneNotified = new HashMap<>();
    // Torches broken a moment ago: the light they used to emit is still in the next packet's diff.
    private final Map<Long, long[]> goneTorches = new HashMap<>();
    private final Object packetListener = new PacketListener();

    private ClientLevel lastLevel;

    @Override
    public void onEnable() {
        reset();
        snapshotLoadedChunks();
        Events.BUS.registerOwned(this, packetListener);
    }

    @Override
    public void onDisable() {
        Events.BUS.unregister(packetListener);
        reset();
    }

    private void reset() {
        chunkLight.clear();
        flagged.clear();
        passed.clear();
        redstoneNotified.clear();
        goneTorches.clear();
        lastLevel = null;
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            reset();
            return;
        }
        if (level != lastLevel) {
            reset();
            lastLevel = level;
            snapshotLoadedChunks();
        }
        passed.add(mc.player.chunkPosition().pack());
        long now = System.currentTimeMillis();
        goneTorches.values().removeIf(entry -> entry[0] < now);
    }

    // ---- packets -------------------------------------------------------------------------------------------

    private final class PacketListener {
        @EventHandler
        private void onPacketReceive(PacketEvent.Receive event) {
            Packet<?> packet = event.getPacket();
            if (!(packet instanceof ClientboundLevelChunkWithLightPacket
                    || packet instanceof ClientboundLightUpdatePacket
                    || packet instanceof ClientboundBlockUpdatePacket
                    || packet instanceof ClientboundSectionBlocksUpdatePacket)) {
                return;
            }
            // Queued before the client applies the packet, so the world still shows the old state in here.
            mc.execute(() -> handle(packet));
        }
    }

    private void handle(Packet<?> packet) {
        ClientLevel level = mc.level;
        if (!isEnabled() || level == null) return;
        if (packet instanceof ClientboundLevelChunkWithLightPacket chunk) {
            baseline(level, chunk.x(), chunk.z(), chunk.lightData());
        } else if (packet instanceof ClientboundLightUpdatePacket update) {
            detectLightChanges(level, update.x(), update.z(), update.lightData());
        } else if (packet instanceof ClientboundBlockUpdatePacket update) {
            blockChanged(level, update.getPos(), level.getBlockState(update.getPos()), update.getBlockState());
        } else if (packet instanceof ClientboundSectionBlocksUpdatePacket section) {
            section.runUpdates((pos, state) -> blockChanged(level, pos, level.getBlockState(pos), state));
        }
    }

    // ---- light ---------------------------------------------------------------------------------------------

    private void snapshotLoadedChunks() {
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) return;
        ChunkPos center = mc.player.chunkPosition();
        var engine = level.getLightEngine().getLayerListener(net.minecraft.world.level.LightLayer.BLOCK);
        for (int cx = center.x() - SNAPSHOT_RADIUS; cx <= center.x() + SNAPSHOT_RADIUS; cx++) {
            for (int cz = center.z() - SNAPSHOT_RADIUS; cz <= center.z() + SNAPSHOT_RADIUS; cz++) {
                if (level.getChunkSource().getChunk(cx, cz, false) == null) continue;
                Map<Integer, byte[]> sections = new HashMap<>();
                for (int y = level.getMinSectionY() - 1; y <= level.getMaxSectionY() + 1; y++) {
                    var layer = engine.getDataLayerData(net.minecraft.core.SectionPos.of(cx, y, cz));
                    if (layer != null && !layer.isEmpty()) sections.put(y, layer.getData().clone());
                }
                chunkLight.put(ChunkPos.pack(cx, cz), sections);
            }
        }
        trimCache();
    }

    private void baseline(ClientLevel level, int cx, int cz, ClientboundLightUpdatePacketData data) {
        Map<Integer, byte[]> sections = new HashMap<>();
        applySections(level, data, sections, cx, cz, null);
        chunkLight.put(ChunkPos.pack(cx, cz), sections);
        trimCache();
    }

    private void detectLightChanges(ClientLevel level, int cx, int cz, ClientboundLightUpdatePacketData data) {
        long key = ChunkPos.pack(cx, cz);
        Map<Integer, byte[]> known = chunkLight.get(key);
        boolean compare = known != null && !isOwnChunk(key);
        if (known == null) known = new HashMap<>();
        Change change = new Change();
        applySections(level, data, known, cx, cz, compare ? change : null);
        chunkLight.put(key, known);
        trimCache();
        if (change.count > 0) flag(key, change.x, change.y, change.z, "light change " + change.oldLevel + " -> " + change.newLevel);
    }

    private void applySections(ClientLevel level, ClientboundLightUpdatePacketData data, Map<Integer, byte[]> store,
                               int cx, int cz, Change change) {
        BitSet inited = data.blockYMask();
        List<byte[]> nibbles = data.blockUpdates();
        int listIndex = 0;
        int[] checks = {0};
        for (int bit = inited.nextSetBit(0); bit >= 0 && listIndex < nibbles.size(); bit = inited.nextSetBit(bit + 1)) {
            byte[] fresh = nibbles.get(listIndex++);
            int sectionY = level.getMinSectionY() - 1 + bit;
            if (fresh.length == SECTION_BYTES) update(level, store, cx, cz, sectionY, fresh.clone(), change, checks);
        }
        BitSet empty = data.emptyBlockYMask();
        for (int bit = empty.nextSetBit(0); bit >= 0; bit = empty.nextSetBit(bit + 1)) {
            update(level, store, cx, cz, level.getMinSectionY() - 1 + bit, ZERO_SECTION, change, checks);
        }
    }

    private void update(ClientLevel level, Map<Integer, byte[]> store, int cx, int cz, int sectionY, byte[] fresh,
                        Change change, int[] checks) {
        byte[] old = store.get(sectionY);
        if (old != null && change != null && (sectionY << 4) < 0) compare(level, old, fresh, cx, cz, sectionY, change, checks);
        store.put(sectionY, fresh);
    }

    private void compare(ClientLevel level, byte[] old, byte[] fresh, int cx, int cz, int sectionY, Change change, int[] checks) {
        for (int i = 0; i < SECTION_BYTES; i++) {
            int diff = (old[i] ^ fresh[i]) & 0xFF;
            if (diff == 0) continue;
            for (int half = 0; half < 2; half++) {
                if ((diff & (half == 0 ? 0x0F : 0xF0)) == 0) continue;
                int nibble = (i << 1) + half;
                int oldLevel = half == 0 ? old[i] & 15 : (old[i] >>> 4) & 15;
                int newLevel = half == 0 ? fresh[i] & 15 : (fresh[i] >>> 4) & 15;
                int x = (cx << 4) + (nibble & 15);
                int z = (cz << 4) + ((nibble >>> 4) & 15);
                int y = (sectionY << 4) + (nibble >>> 8);
                if (y >= 0 || checks[0] >= MAX_EXPLAIN_CHECKS) continue;
                checks[0]++;
                if (!explained(level, x, y, z, Math.max(oldLevel, newLevel))) change.add(x, y, z, oldLevel, newLevel);
            }
        }
    }

    /** True when a natural (or ignored) light source close enough could account for light {@code level} at the spot. */
    private boolean explained(ClientLevel level, int x, int y, int z, int lightLevel) {
        int reach = TORCH_LIGHT - lightLevel;
        if (reach < 0) return false;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -reach; dx <= reach; dx++) {
            int rx = reach - Math.abs(dx);
            for (int dy = -rx; dy <= rx; dy++) {
                int ry = rx - Math.abs(dy);
                for (int dz = -ry; dz <= ry; dz++) {
                    int distance = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
                    cursor.set(x + dx, y + dy, z + dz);
                    BlockState state = level.getBlockState(cursor);
                    int source = sourceLight(state, level, cursor);
                    if (source > 0 && distance <= source - lightLevel) return true;
                }
            }
        }
        long now = System.currentTimeMillis();
        for (Map.Entry<Long, long[]> entry : goneTorches.entrySet()) {
            long[] value = entry.getValue();
            if (value[0] < now) continue;
            BlockPos pos = BlockPos.of(entry.getKey());
            int distance = Math.abs(pos.getX() - x) + Math.abs(pos.getY() - y) + Math.abs(pos.getZ() - z);
            if (distance <= value[1] - lightLevel) return true;
        }
        return false;
    }

    private static int sourceLight(BlockState state, ClientLevel level, BlockPos pos) {
        if (isTorchLike(state)) return TORCH_LIGHT;
        if (isAmethyst(state)) return AMETHYST_LIGHT;
        if (isRedstoneOre(state) && exposed(level, pos)) return ORE_LIGHT;
        return 0;
    }

    private static boolean exposed(ClientLevel level, BlockPos pos) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.values()) {
            BlockState neighbour = level.getBlockState(cursor.setWithOffset(pos, direction));
            if (neighbour.isAir() || !neighbour.canOcclude()) return true;
        }
        return false;
    }

    private static boolean isTorchLike(BlockState state) {
        return state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH) || state.is(Blocks.SOUL_TORCH)
                || state.is(Blocks.SOUL_WALL_TORCH) || state.is(Blocks.COPPER_TORCH) || state.is(Blocks.COPPER_WALL_TORCH)
                || CaveVines.hasGlowBerries(state);
    }

    private static boolean isAmethyst(BlockState state) {
        return state.is(Blocks.AMETHYST_CLUSTER) || state.is(Blocks.LARGE_AMETHYST_BUD)
                || state.is(Blocks.MEDIUM_AMETHYST_BUD) || state.is(Blocks.SMALL_AMETHYST_BUD)
                || state.is(Blocks.BUDDING_AMETHYST) || state.is(Blocks.AMETHYST_BLOCK);
    }

    private static boolean isRedstoneOre(BlockState state) {
        return state.is(Blocks.REDSTONE_ORE) || state.is(Blocks.DEEPSLATE_REDSTONE_ORE);
    }

    // ---- redstone ------------------------------------------------------------------------------------------

    private void blockChanged(ClientLevel level, BlockPos pos, BlockState old, BlockState fresh) {
        if (old == fresh) return;
        if (isTorchLike(old) && (!isTorchLike(fresh) || fresh.getLightEmission() < old.getLightEmission())) {
            goneTorches.put(pos.asLong(), new long[]{System.currentTimeMillis() + GONE_TORCH_MS, TORCH_LIGHT});
        }
        if (!watchRedstone.get() || pos.getY() >= 0) return;
        if (!isRedstone(fresh) && !isRedstone(old)) return;
        long key = ChunkPos.pack(pos);
        if (isOwnChunk(key)) return;
        long now = System.currentTimeMillis();
        boolean quiet = now - redstoneNotified.getOrDefault(key, Long.MIN_VALUE / 2) >= REDSTONE_ALERT_COOLDOWN_MS;
        if (quiet) redstoneNotified.put(key, now);
        flag(key, pos.getX(), pos.getY(), pos.getZ(), "redstone", quiet);
    }

    private static boolean isRedstone(BlockState state) {
        Block block = state.getBlock();
        return state.is(Blocks.REDSTONE_WIRE) || state.is(Blocks.PISTON) || state.is(Blocks.STICKY_PISTON)
                || state.is(Blocks.PISTON_HEAD) || state.is(Blocks.MOVING_PISTON)
                || block instanceof DiodeBlock || block instanceof RedstoneTorchBlock
                || block instanceof RedstoneLampBlock || block instanceof ObserverBlock
                || block instanceof DispenserBlock || block instanceof HopperBlock
                || block instanceof NoteBlock || block instanceof DaylightDetectorBlock
                || block instanceof LeverBlock || block instanceof ButtonBlock
                || block instanceof BasePressurePlateBlock || block instanceof TripWireHookBlock
                || block instanceof TargetBlock || block instanceof CrafterBlock || block instanceof PoweredBlock;
    }

    // ---- bookkeeping / render ------------------------------------------------------------------------------

    private boolean isOwnChunk(long key) {
        return passed.contains(key) || (mc.player != null && mc.player.chunkPosition().pack() == key);
    }

    private void flag(long key, int x, int y, int z, String why) {
        flag(key, x, y, z, why, true);
    }

    private void flag(long key, int x, int y, int z, String why, boolean alert) {
        boolean fresh = flagged.add(key);
        if (!fresh) {
            // Keep the most recently active chunk last so trimming drops the stalest.
            flagged.remove(key);
            flagged.add(key);
        }
        while (flagged.size() > MAX_FLAGGED) {
            Iterator<Long> oldest = flagged.iterator();
            oldest.next();
            oldest.remove();
        }
        if (alert && (fresh || why.equals("redstone"))) {
            FinderAlerts.found(getDisplayName(), why, x, y, z, chatAlerts.get(), soundAlerts.get());
        }
    }

    private void trimCache() {
        while (chunkLight.size() > MAX_TRACKED_CHUNKS) {
            Iterator<Long> oldest = chunkLight.keySet().iterator();
            oldest.next();
            oldest.remove();
        }
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.END_MAIN;
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        ClientLevel level = mc.level;
        if (!isEnabled() || renderer == null || level == null || flagged.isEmpty()) return;
        FinderRender draw = FinderRender.begin(renderer, 1.5f);
        int fill = fillColor.getArgb();
        int line = lineColor.getArgb();
        for (long key : flagged) {
            int cx = ChunkPos.getX(key);
            int cz = ChunkPos.getZ(key);
            if (level.getChunkSource().getChunk(cx, cz, false) == null) continue;
            draw.chunkPlate(cx, cz, PLATE_Y, fill, line);
            if (tracers.get()) draw.tracer(new Vec3((cx << 4) + 8.0, PLATE_Y, (cz << 4) + 8.0), line);
        }
    }

    private static final class Change {
        int count;
        int x;
        int y;
        int z;
        int oldLevel;
        int newLevel;

        void add(int x, int y, int z, int oldLevel, int newLevel) {
            if (count == 0 || Math.abs(newLevel - oldLevel) > Math.abs(this.newLevel - this.oldLevel)) {
                this.x = x;
                this.y = y;
                this.z = z;
                this.oldLevel = oldLevel;
                this.newLevel = newLevel;
            }
            count++;
        }
    }
}

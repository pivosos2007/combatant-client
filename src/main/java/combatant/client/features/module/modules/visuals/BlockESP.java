/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.visuals;

import com.mojang.blaze3d.vertex.PoseStack;
import combatant.client.config.values.*;
import combatant.client.features.module.*;
import combatant.client.features.module.Module;
import combatant.client.util.block.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import combatant.client.config.common.CommonSettingSchemas;
import combatant.client.config.values.*;
import combatant.client.features.gui.clickgui.settings.TextListSetting;
import combatant.client.features.module.*;
import combatant.client.render.engine.RenderState;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.util.block.*;
import combatant.client.util.logging.DebugLog;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

//todo Description
@ModuleInfo(
        id = "blockesp",
        displayName = "BlockESP",
        aliases = {"xray", "oreesp", "blocks"},
        category = ModuleCategory.VISUALS
)
public class BlockESP extends Module {

    private static final int SECTION_SCAN_BUDGET_PER_TICK = 10;
    private static final int SECTION_SCAN_BURST_BUDGET_PER_TICK = 64;
    private static final int SECTION_SCAN_BURST_TICKS = 4;
    private static final int SECTION_SCAN_REFRESH_TICKS = 40;
    private static final int SODIUM_CANDIDATE_DRAIN_BUDGET_PER_TICK = 4096;
    private static final int RECONCILE_BUDGET_PER_TICK = 512;
    private static final int FULL_RECONCILE_REFRESH_TICKS = 20;
    private final Minecraft mc = Minecraft.getInstance();
    private final BlockDeobfuscationScanner deobfuscationScanner = new BlockDeobfuscationScanner();
    private final WorldSectionBlockScanner sectionScanner = new WorldSectionBlockScanner();

    private final EnumValue<BlockScanMode> modeValue =
            enumCommon(
                    "blockEspMode",
                    "mode",
                    CommonSettingSchemas.ESP_SCAN_MODE,
                    BlockScanMode.LOS,
                    BlockScanMode.values()
            );

    private final BooleanValue deobfuscationValue =
            bool(
                    "blockEspDeobfuscation",
                    "deobfuscation",
                    false
            );

    private final BooleanValue limitDistanceValue =
            boolCommon(
                    "blockEspLimitDistance",
                    "limit_distance",
                    CommonSettingSchemas.ESP_LIMIT_DISTANCE,
                    true
            );

    private final NumberValue<Integer> maxDistanceValue =
            visibleWhen(numCommon(
                    "blockEspMaxDistance",
                    "max_distance",
                    CommonSettingSchemas.ESP_MAX_DISTANCE,
                    64,
                    1,
                    256
            ), limitDistanceValue::get);

    private final NumberValue<Integer> maxTargetsValue =
            numCommon(
                    "blockEspMaxTargets",
                    "max_targets",
                    CommonSettingSchemas.ESP_MAX_TARGETS,
                    128,
                    1,
                    4096
            );

    private final NumberValue<Integer> deobfuscationRadiusValue =
            visibleWhen(num(
                    "blockEspDeobfuscationRadius",
                    "deobfuscation_radius",
                    5,
                    1,
                    64
            ), deobfuscationValue::get);

    private final NumberValue<Integer> deobfuscationUpValue =
            visibleWhen(num(
                    "blockEspDeobfuscationUp",
                    "deobfuscation_up",
                    5,
                    1,
                    32
            ), deobfuscationValue::get);

    private final NumberValue<Integer> deobfuscationDownValue =
            visibleWhen(num(
                    "blockEspDeobfuscationDown",
                    "deobfuscation_down",
                    5,
                    1,
                    32
            ), deobfuscationValue::get);

    private final BooleanValue deobfuscationFastValue =
            visibleWhen(bool(
                    "blockEspDeobfuscationFast",
                    "deobfuscation_fast",
                    false
            ), deobfuscationValue::get);

    private final ItemIdSetValue searchBlocksValue =
            itemList(
                    "blockEspSearchBlocks",
                    "search_blocks",
                    TextListSetting.PickerMode.BLOCKS
            );

    private final ItemIdSetValue transparentBlocksValue =
            common(
                    itemList(
                            "blockEspTransparentBlocks",
                            "transparent_blocks",
                            TextListSetting.PickerMode.BLOCKS
                    ),
                    CommonSettingSchemas.ESP_TRANSPARENT_BLOCKS.commonI18nKey()
            );

    private final BlockScanContext scanContext = new BlockScanContext(mc, searchBlocksValue, transparentBlocksValue);

    private final BooleanValue useTracersValue =
            boolCommon(
                    "blockEspUseTracers",
                    "use_tracers",
                    CommonSettingSchemas.ESP_USE_TRACERS,
                    false
            );

    private final EnumValue<BlockOutlineShapeMode> outlineShapeMode =
            enumCommon(
                    "blockEspOutlineShape",
                    "outline_shape",
                    CommonSettingSchemas.ESP_OUTLINE_SHAPE,
                    BlockOutlineShapeMode.FULL_BLOCK,
                    BlockOutlineShapeMode.values()
            );

    private final NumberValue<Float> lineWidth =
            numCommon(
                    "blockEspLineWidth",
                    "line_width",
                    CommonSettingSchemas.ESP_LINE_WIDTH,
                    1.5f,
                    0.5f,
                    4.0f
            );

    private final RGBColorValue visibleColor =
            colorNoAlpha(
                    "blockEspVisibleColor",
                    "visible_color",
                    "#FF0000"
            );

    private final RGBColorValue chunkColor =
            colorNoAlpha(
                    "blockEspChunkColor",
                    "chunk_color",
                    "#FFFF00"
            );

    private final BooleanValue clusterTracersValue =
            visibleWhen(bool(
                    "blockEspClusterTracers",
                    "cluster_tracers",
                    false
            ), useTracersValue::get);
    private final Map<BlockPos, BlockState> observedTargetStates = new ConcurrentHashMap<>();
    private final Set<Long> loggedTargets = ConcurrentHashMap.newKeySet();
    private final ArrayDeque<BlockPos> reconcileQueue = new ArrayDeque<>();
    private final Set<Long> queuedReconcileTargets = ConcurrentHashMap.newKeySet();
    private volatile Map<BlockPos, Target> renderTargets = new ConcurrentHashMap<>();
    private ClientLevel lastWorld;
    private BlockScanMode lastMode;
    private int lastSearchBlocksHash;
    private int lastScanSignature = Integer.MIN_VALUE;
    private int sectionScanRefreshTicks;
    private int sectionScanBurstTicks;
    private int fastSnapshotGeneration;
    private int fullReconcileRefreshTicks;

    @Override
    public void onEnable() {
        resetRuntimeState();
        scanContext.refresh();
        lastSearchBlocksHash = currentSearchBlocksHash();
        publishFastScanSnapshot();
        requestLocalSectionScan(true);
    }

    @Override
    public void onDisable() {
        BlockEspSodiumCandidateCollector.disable();
        resetRuntimeState();
    }

    @Override
    public void onTick() {
        if (!isEnabled() || mc.level == null) {
            BlockEspSodiumCandidateCollector.disable();
            return;
        }

        if (lastWorld != mc.level) {
            if (lastWorld != null) {
                resetRuntimeState();
                RenderSectionBlockScanner.clear();
            }
            lastWorld = mc.level;
        }

        scanContext.refresh();
        if (mc.player == null) {
            BlockEspSodiumCandidateCollector.disable();
            return;
        }
        publishFastScanSnapshot();

        int searchBlocksHash = currentSearchBlocksHash();
        if (lastSearchBlocksHash != searchBlocksHash) {
            clearObservedTargets();
            deobfuscationScanner.reset();
            sectionScanner.reset();
            lastSearchBlocksHash = searchBlocksHash;
            fastSnapshotGeneration++;
            publishFastScanSnapshot();
            requestLocalSectionScan(true);
        }

        BlockScanMode mode = modeValue.get();
        if (lastMode != mode) {
            renderTargets = new ConcurrentHashMap<>();
            loggedTargets.clear();
            reconcileQueue.clear();
            queuedReconcileTargets.clear();
            lastMode = mode;
            requestLocalSectionScan(true);
        }

        drainSodiumCandidateQueue();
        maintainSectionScanQueue();
        drainSectionScanQueue();

        if (deobfuscationValue.get()) {
            deobfuscationScanner.tick(
                    mc,
                    1,
                    deobfuscationRadiusValue.get(),
                    deobfuscationUpValue.get(),
                    deobfuscationDownValue.get(),
                    deobfuscationFastValue.get()
            );
            mergeDeobfuscatedTargets();
        }

        maintainReconcileQueue();
        drainReconcileQueue("cached", RECONCILE_BUDGET_PER_TICK);
    }

    public void acceptSodiumBlockState(int x, int y, int z, BlockState state) {
        if (!isEnabled() || !scanContext.isConfiguredTarget(state)) {
            return;
        }
        acceptObservedCandidate(BlockPos.asLong(x, y, z), state, "sodium-direct");
    }

    public void acceptWorldBlockUpdate(BlockPos pos, BlockState state) {
        if (!isEnabled()) {
            return;
        }
        acceptBlockUpdate(pos, state);
    }

    private void acceptBlockUpdate(BlockPos pos, BlockState state) {
        if (pos == null || state == null) {
            return;
        }

        sectionScanner.forget(pos);
        scanContext.refresh();
        boolean target = scanContext.isConfiguredTarget(state);
        boolean deobfuscatedPositive = deobfuscationScanner.hasPositiveState(pos);
        deobfuscationScanner.acceptBlockUpdate(pos, state, scanContext::isConfiguredTarget);

        if (!target && deobfuscatedPositive) {
            BlockState positiveState = deobfuscationScanner.getPositiveState(pos);
            if (scanContext.isConfiguredTarget(positiveState)) {
                recordTargetCandidate(pos, positiveState);
                updateObservedTarget(pos, positiveState, "deobfuscation-update");
            }
            return;
        }

        if (!target) {
            removeTargetCandidate(pos);
            return;
        }

        recordTargetCandidate(pos, state);
        updateObservedTarget(pos, state, "world-update");
        if (deobfuscationValue.get()) {
            DebugLog.info("[BlockESP] deobfuscated %s at %s", BuiltInRegistries.BLOCK.getKey(state.getBlock()), pos.toShortString());
        }
    }

    private void resetRuntimeState() {
        clearObservedTargets();
        lastMode = null;
        lastWorld = mc.level;
        lastSearchBlocksHash = currentSearchBlocksHash();
        lastScanSignature = Integer.MIN_VALUE;
        sectionScanRefreshTicks = 0;
        sectionScanBurstTicks = 0;
        fullReconcileRefreshTicks = 0;
        fastSnapshotGeneration++;
        BlockEspSodiumCandidateCollector.clear();
        deobfuscationScanner.reset();
        sectionScanner.reset();
    }

    private void clearObservedTargets() {
        renderTargets = new ConcurrentHashMap<>();
        observedTargetStates.clear();
        loggedTargets.clear();
        reconcileQueue.clear();
        queuedReconcileTargets.clear();
        BlockEspSodiumCandidateCollector.clear();
    }

    private void recordTargetCandidate(BlockPos rawPos, BlockState state) {
        if (rawPos == null || state == null) {
            return;
        }

        BlockPos pos = rawPos.immutable();
        RenderSectionBlockScanner.recordBlock(pos, state);
        observedTargetStates.put(pos, state);
        enqueueReconcile(pos);
    }

    private void removeTargetCandidate(BlockPos pos) {
        if (pos == null) {
            return;
        }

        observedTargetStates.remove(pos);
        renderTargets.remove(pos);
        loggedTargets.remove(pos.asLong());
        queuedReconcileTargets.remove(pos.asLong());
        sectionScanner.forget(pos);
        if (mc.level != null) {
            RenderSectionBlockScanner.recordBlock(pos, mc.level.getBlockState(pos));
        }
    }

    private void updateObservedTarget(BlockPos pos, BlockState state, String source) {
        if (mc.level == null || mc.player == null || !withinDistance(pos)) {
            renderTargets.remove(pos);
            return;
        }

        boolean visible = scanContext.hasTransparentNeighbor(pos);
        if (!acceptsMode(pos, visible)) {
            renderTargets.remove(pos);
            return;
        }
        if (!renderTargets.containsKey(pos) && renderTargets.size() >= maxTargetsValue.get()) {
            return;
        }

        Target target = new Target();
        target.pos = pos;
        target.state = state;
        target.box = new AABB(pos);
        target.visible = visible;
        renderTargets.put(pos, target);

        if (loggedTargets.add(pos.asLong())) {
            DebugLog.info(
                    "[BlockESP] detected %s at %s visible=%s source=%s mode=%s",
                    BuiltInRegistries.BLOCK.getKey(state.getBlock()),
                    pos.toShortString(),
                    visible,
                    source,
                    modeValue.get().getId()
            );
        }
    }

    private void drainSodiumCandidateQueue() {
        BlockEspSodiumCandidateCollector.drain(
                SODIUM_CANDIDATE_DRAIN_BUDGET_PER_TICK,
                candidate -> {
                    if (candidate.generation() == fastSnapshotGeneration) {
                        acceptObservedCandidate(candidate.packedPos(), candidate.state(), candidate.source().name().toLowerCase());
                    }
                }
        );
    }

    private void acceptObservedCandidate(long packedPos, BlockState hintedState, String source) {
        if (mc.level == null || mc.player == null) {
            return;
        }

        BlockPos pos = BlockPos.of(packedPos);
        if (!withinDistance(pos)) {
            removeTargetCandidate(pos);
            return;
        }

        BlockState currentState = mc.level.getBlockState(pos);
        BlockState state = scanContext.isConfiguredTarget(currentState) ? currentState : hintedState;
        if (!scanContext.isConfiguredTarget(state)) {
            removeTargetCandidate(pos);
            return;
        }

        recordTargetCandidate(pos, state);
        updateObservedTarget(pos, state, source);
    }

    private void enqueueReconcile(BlockPos pos) {
        if (pos == null) {
            return;
        }

        long key = pos.asLong();
        if (queuedReconcileTargets.add(key)) {
            reconcileQueue.addLast(pos.immutable());
        }
    }

    private void maintainReconcileQueue() {
        if (++fullReconcileRefreshTicks < FULL_RECONCILE_REFRESH_TICKS) {
            return;
        }

        fullReconcileRefreshTicks = 0;
        for (BlockPos pos : observedTargetStates.keySet()) {
            enqueueReconcile(pos);
        }
    }

    private void drainReconcileQueue(String source, int maxEntries) {
        int processed = 0;
        while (processed < maxEntries && !reconcileQueue.isEmpty()) {
            BlockPos pos = reconcileQueue.removeFirst();
            queuedReconcileTargets.remove(pos.asLong());

            BlockState state = observedTargetStates.get(pos);
            if (state == null) {
                processed++;
                continue;
            }

            if (mc.level != null && withinDistance(pos)) {
                BlockState currentState = mc.level.getBlockState(pos);
                if (scanContext.isConfiguredTarget(currentState)) {
                    if (currentState != state) {
                        observedTargetStates.put(pos, currentState);
                        state = currentState;
                    }
                } else if (!deobfuscationScanner.hasPositiveState(pos)) {
                    removeTargetCandidate(pos);
                    processed++;
                    continue;
                }
            }

            updateObservedTarget(pos, state, source);
            processed++;
        }
    }

    private void maintainSectionScanQueue() {
        if (mc.level == null || mc.player == null) {
            return;
        }

        int signature = currentScanSignature();
        if (signature != lastScanSignature || ++sectionScanRefreshTicks >= SECTION_SCAN_REFRESH_TICKS) {
            lastScanSignature = signature;
            sectionScanRefreshTicks = 0;
            requestLocalSectionScan(false);
        }
    }

    private void requestLocalSectionScan(boolean burst) {
        if (mc.level == null || mc.player == null) {
            return;
        }

        int radius = scanBlockRadius();
        int minY;
        int maxY;
        if (limitDistanceValue.get()) {
            int eyeY = (int) Math.floor(mc.player.getEyePosition().y);
            minY = eyeY - radius;
            maxY = eyeY + radius;
        } else {
            minY = mc.level.getMinY();
            maxY = mc.level.getMaxY();
        }

        sectionScanner.enqueueAround(mc, radius, minY, maxY);
        if (burst) {
            sectionScanBurstTicks = SECTION_SCAN_BURST_TICKS;
        }
    }

    private void drainSectionScanQueue() {
        int budget = sectionScanBurstTicks > 0 ? SECTION_SCAN_BURST_BUDGET_PER_TICK : SECTION_SCAN_BUDGET_PER_TICK;
        if (sectionScanBurstTicks > 0) {
            sectionScanBurstTicks--;
        }

        sectionScanner.drain(
                mc,
                budget,
                scanContext::isConfiguredTarget,
                (pos, state) -> acceptObservedCandidate(pos.asLong(), state, "section")
        );
    }

    private void publishFastScanSnapshot() {
        if (!isEnabled() || mc.player == null) {
            BlockEspSodiumCandidateCollector.disable();
            return;
        }

        BlockEspSodiumCandidateCollector.publishSnapshot(
                true,
                scanContext.targetBlocksSnapshot(),
                limitDistanceValue.get(),
                scanBlockRadius(),
                mc.player.getBlockX(),
                mc.player.getBlockY(),
                mc.player.getBlockZ(),
                fastSnapshotGeneration
        );
    }

    private int scanBlockRadius() {
        return limitDistanceValue.get() ? Math.max(1, maxDistanceValue.get()) : 256;
    }

    private int currentSearchBlocksHash() {
        Set<String> targets = searchBlocksValue.get();
        return targets == null ? 0 : targets.hashCode();
    }

    private int currentScanSignature() {
        int centerChunkX = SectionPos.blockToSectionCoord(mc.player.getBlockX());
        int centerChunkZ = SectionPos.blockToSectionCoord(mc.player.getBlockZ());
        int centerSectionY = SectionPos.blockToSectionCoord(mc.player.getBlockY());
        int result = 17;
        result = 31 * result + centerChunkX;
        result = 31 * result + centerChunkZ;
        result = 31 * result + centerSectionY;
        result = 31 * result + (limitDistanceValue.get() ? 1 : 0);
        result = 31 * result + maxDistanceValue.get();
        result = 31 * result + currentSearchBlocksHash();
        return result;
    }

    private void mergeDeobfuscatedTargets() {
        for (RenderSectionBlockScanner.ScanResult result : deobfuscationScanner.snapshot(
                mc,
                scanContext::isConfiguredTarget,
                null,
                maxTargetsValue.get()
        )) {
            acceptObservedCandidate(result.pos().asLong(), result.state(), "deobfuscation");
        }
    }

    private boolean withinDistance(BlockPos pos) {
        if (!limitDistanceValue.get() || mc.player == null) {
            return true;
        }

        Vec3 eye = mc.player.getEyePosition();
        double dx = pos.getX() + 0.5 - eye.x;
        double dy = pos.getY() + 0.5 - eye.y;
        double dz = pos.getZ() + 0.5 - eye.z;
        int maxDistance = maxDistanceValue.get();
        return dx * dx + dy * dy + dz * dz <= (double) maxDistance * (double) maxDistance;
    }

    private boolean isCurrentTargetBlock(BlockPos pos) {
        if (mc.level == null || pos == null) {
            return false;
        }
        return scanContext.isConfiguredTarget(mc.level.getBlockState(pos));
    }

    private boolean acceptsMode(BlockPos pos, boolean visible) {
        BlockScanMode mode = modeValue.get();
        int maxRange = limitDistanceValue.get() ? maxDistanceValue.get() : 256;
        return switch (mode) {
            case LEGIT -> visible && mc.player != null
                    && scanContext.hasPath(mc.player.getEyePosition(), pos, limitDistanceValue.get(), maxRange);
            case LOS, LOS_UNLOCKED -> visible;
            case AGGRESSIVE -> true;
        };
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.END_MAIN;
    }

    @Override
    public void onRenderWorld(PoseStack matrices, SubmitNodeCollector consumers) {
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!isEnabled() || mc.level == null || mc.player == null) {
            return;
        }

        float previousLineWidth = RenderState.lineWidth;
        RenderState.lineWidth = Math.max(0.5f, lineWidth.get());
        try {
            for (Target target : renderTargets.values()) {
                if (!withinDistance(target.pos)) {
                    continue;
                }

                BlockState state = target.state;
                if (mc.level != null) {
                    BlockState current = mc.level.getBlockState(target.pos);
                    if (!scanContext.isConfiguredTarget(current)) {
                        removeTargetCandidate(target.pos);
                        continue;
                    }
                    state = current;
                    if (!state.equals(target.state)) {
                        observedTargetStates.put(target.pos, state);
                        target.state = state;
                    }
                }

                int color = 0xFF000000 | ((target.visible ? visibleColor.getArgb() : chunkColor.getArgb()) & 0x00FFFFFF);
                BlockOutlineRenderer.render(renderer, mc.level, target.pos, state, outlineShapeMode.get(), color);
            }
        } finally {
            RenderState.lineWidth = previousLineWidth;
        }

        renderBlockTracers(renderer, tickDelta);
    }

    private void drawBox(Renderer3D renderer, AABB box, int r, int g, int b, int a) {
        double x1 = box.minX;
        double y1 = box.minY;
        double z1 = box.minZ;
        double x2 = box.maxX;
        double y2 = box.maxY;
        double z2 = box.maxZ;

        line(renderer, x1, y1, z1, x2, y1, z1, r, g, b, a);
        line(renderer, x2, y1, z1, x2, y1, z2, r, g, b, a);
        line(renderer, x2, y1, z2, x1, y1, z2, r, g, b, a);
        line(renderer, x1, y1, z2, x1, y1, z1, r, g, b, a);

        line(renderer, x1, y2, z1, x2, y2, z1, r, g, b, a);
        line(renderer, x2, y2, z1, x2, y2, z2, r, g, b, a);
        line(renderer, x2, y2, z2, x1, y2, z2, r, g, b, a);
        line(renderer, x1, y2, z2, x1, y2, z1, r, g, b, a);

        line(renderer, x1, y1, z1, x1, y2, z1, r, g, b, a);
        line(renderer, x2, y1, z1, x2, y2, z1, r, g, b, a);
        line(renderer, x2, y1, z2, x2, y2, z2, r, g, b, a);
        line(renderer, x1, y1, z2, x1, y2, z2, r, g, b, a);
    }

    private void line(Renderer3D renderer,
                      double x1, double y1, double z1,
                      double x2, double y2, double z2,
                      int r, int g, int b, int a) {
        renderer.line(x1, y1, z1, x2, y2, z2, r, g, b, a);
    }

    private void renderBlockTracers(Renderer3D renderer, float tickDelta) {
        Tracers tracers = Modules.get(Tracers.class);
        if (!useTracersValue.get() || tracers == null) {
            return;
        }

        if (!clusterTracersValue.get()) {
            for (Target target : renderTargets.values()) {
                if (!withinDistance(target.pos) || !isCurrentTargetBlock(target.pos)) {
                    continue;
                }
                int color = target.visible ? visibleColor.getArgb() : chunkColor.getArgb();
                tracers.render3DTracersRaw(renderer, tickDelta, List.of(Vec3.atCenterOf(target.pos)), color);
            }
            return;
        }

        List<List<Target>> clusters = buildClusters(renderTargets.values().stream()
                .filter(target -> withinDistance(target.pos) && isCurrentTargetBlock(target.pos))
                .toList());
        for (List<Target> cluster : clusters) {
            boolean anyVisible = cluster.stream().anyMatch(target -> target.visible);
            int color = anyVisible ? visibleColor.getArgb() : chunkColor.getArgb();
            tracers.render3DTracersRaw(renderer, tickDelta, List.of(clusterCenter(cluster)), color);
        }
    }

    private List<List<Target>> buildClusters(Collection<Target> targets) {
        Map<BlockPos, Target> map = new HashMap<>();
        for (Target target : targets) {
            map.put(target.pos, target);
        }

        Set<BlockPos> visited = new HashSet<>();
        List<List<Target>> clusters = new ArrayList<>();

        for (Target target : targets) {
            if (visited.contains(target.pos)) {
                continue;
            }

            List<Target> cluster = new ArrayList<>();
            ArrayDeque<BlockPos> queue = new ArrayDeque<>();

            queue.add(target.pos);
            visited.add(target.pos);

            while (!queue.isEmpty()) {
                BlockPos current = queue.poll();
                Target currentTarget = map.get(current);
                if (currentTarget != null) {
                    cluster.add(currentTarget);
                }

                for (BlockPos neighbor : new BlockPos[]{
                        current.above(), current.below(),
                        current.north(), current.south(),
                        current.east(), current.west()
                }) {
                    if (!visited.contains(neighbor) && map.containsKey(neighbor)) {
                        visited.add(neighbor);
                        queue.add(neighbor);
                    }
                }
            }

            clusters.add(cluster);
        }

        return clusters;
    }

    private Vec3 clusterCenter(List<Target> cluster) {
        double x = 0.0;
        double y = 0.0;
        double z = 0.0;
        for (Target target : cluster) {
            Vec3 center = Vec3.atCenterOf(target.pos);
            x += center.x;
            y += center.y;
            z += center.z;
        }
        int size = cluster.size();
        return new Vec3(x / size, y / size, z / size);
    }

    public static class Target {
        BlockPos pos;
        BlockState state;
        AABB box;
        boolean visible;
    }
}

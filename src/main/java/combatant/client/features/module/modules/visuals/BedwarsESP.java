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
import combatant.client.features.module.modules.misc.DefineTarget;
import combatant.client.render.engine.RenderState;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.util.block.*;
import combatant.client.util.block.bed.BedBlockUtil;
import combatant.client.util.block.bed.BedwarsTeamColorUtil;
import combatant.client.util.block.bed.SelfBedMode;
import combatant.client.util.block.bed.SelfBedTracker;
import combatant.client.util.logging.DebugLog;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

//todo Description
@ModuleInfo(
        id = "bedwarsesp",
        displayName = "BedwarsESP",
        category = ModuleCategory.VISUALS
)
public class BedwarsESP extends Module {
//todo не доделан

    private static final int SECTION_SCAN_BUDGET_PER_TICK = 4;
    private static final int SECTION_SCAN_REFRESH_TICKS = 40;

    private final Minecraft mc = Minecraft.getInstance();
    private final WorldSectionBlockScanner sectionScanner = new WorldSectionBlockScanner();

    private final EnumValue<BlockScanMode> modeValue =
            enumCommon(
                    "bedwars_esp_mode",
                    "mode",
                    CommonSettingSchemas.ESP_SCAN_MODE,
                    BlockScanMode.LOS,
                    BlockScanMode.LEGIT,
                    BlockScanMode.LOS,
                    BlockScanMode.LOS_UNLOCKED,
                    BlockScanMode.AGGRESSIVE
            );

    private final BooleanValue limitDistanceValue =
            boolCommon(
                    "bedwars_esp_limit_distance",
                    "limit_distance",
                    CommonSettingSchemas.ESP_LIMIT_DISTANCE,
                    true
            );

    private final NumberValue<Integer> maxDistanceValue =
            visibleWhen(numCommon(
                    "bedwars_esp_max_distance",
                    "max_distance",
                    CommonSettingSchemas.ESP_MAX_DISTANCE,
                    64,
                    1,
                    256
            ), limitDistanceValue::get);

    private final NumberValue<Integer> maxTargetsValue =
            numCommon(
                    "bedwars_esp_max_targets",
                    "max_targets",
                    CommonSettingSchemas.ESP_MAX_TARGETS,
                    32,
                    1,
                    256
            );

    private final ItemIdSetValue transparentBlocksValue =
            common(
                    itemList(
                            "bedwars_esp_transparent_blocks",
                            "transparent_blocks",
                            TextListSetting.PickerMode.BLOCKS
                    ),
                    CommonSettingSchemas.ESP_TRANSPARENT_BLOCKS.commonI18nKey()
            );

    private final BlockScanContext scanContext = new BlockScanContext(mc, null, transparentBlocksValue);

    private final BooleanValue useTracersValue =
            boolCommon(
                    "bedwars_esp_use_tracers",
                    "use_tracers",
                    CommonSettingSchemas.ESP_USE_TRACERS,
                    false
            );

    private final NumberValue<Float> lineWidth =
            numCommon(
                    "bedwars_esp_line_width",
                    "line_width",
                    CommonSettingSchemas.ESP_LINE_WIDTH,
                    1.5f,
                    0.5f,
                    4.0f
            );

    private final EnumValue<BlockOutlineShapeMode> outlineShapeMode =
            enumCommon(
                    "bedwars_esp_outline_shape",
                    "outline_shape",
                    CommonSettingSchemas.ESP_OUTLINE_SHAPE,
                    BlockOutlineShapeMode.FULL_BLOCK,
                    BlockOutlineShapeMode.values()
            );

    private final EnumValue<SelfBedMode> selfBedMode =
            enumMode(
                    "bedwars_esp_self_bed",
                    SelfBedMode.NONE,
                    SelfBedMode.values()
            );
    private final NumberValue<Float> spawnBedDistance =
            visibleWhen(num(
                    "bedwars_esp_spawn_bed_distance",
                    "spawn_bed_distance",
                    24.0f,
                    16.0f,
                    48.0f
            ), () -> selfBedMode.get() == SelfBedMode.SPAWN_LOCATION);

    private final NumberValue<Integer> maxLayers =
            num(
                    "bedwars_esp_max_layers",
                    "max_layers",
                    1,
                    1,
                    5
            );

    private final BooleanValue showOpeningsValue =
            bool(
                    "bedwars_esp_show_shell",
                    "show_openings",
                    true
            );

    private final RGBAColorValue selfBedColor =
            color(
                    "bedwars_esp_self_bed_color",
                    "self_bed_color",
                    "#8026FF71"
            );

    private final RGBAColorValue otherBedColor =
            color(
                    "bedwars_esp_other_bed_color",
                    "other_bed_color",
                    "#80FF6262"
            );

    private final RGBAColorValue selfOpeningColor =
            visibleWhen(color(
                    "bedwars_esp_self_shell_color",
                    "self_opening_color",
                    "#4026BFFF"
            ), showOpeningsValue::get);

    private final RGBAColorValue otherOpeningColor =
            visibleWhen(color(
                    "bedwars_esp_other_shell_color",
                    "other_opening_color",
                    "#40FFC247"
            ), showOpeningsValue::get);
    private final Map<BlockPos, BlockState> observedBedStates = new ConcurrentHashMap<>();
    private final Map<BlockPos, BlockPos> bedPartToKey = new ConcurrentHashMap<>();
    private final Set<Long> loggedBeds = ConcurrentHashMap.newKeySet();
    private volatile Map<BlockPos, BedRenderTarget> renderTargets = new ConcurrentHashMap<>();
    private ClientLevel lastWorld;
    private BlockScanMode lastMode;
    private int lastScanSignature = Integer.MIN_VALUE;
    private int sectionScanRefreshTicks;

    private static AABB createFaceBox(BlockPos bedPos, net.minecraft.core.Direction face) {
        final double inset = 0.18;
        final double depth = 0.045;
        final double eps = 0.0025;
        double minX = bedPos.getX();
        double minY = bedPos.getY();
        double minZ = bedPos.getZ();
        double maxX = minX + 1.0;
        double maxY = minY + 1.0;
        double maxZ = minZ + 1.0;

        return switch (face) {
            case DOWN -> new AABB(minX + inset, minY - eps, minZ + inset, maxX - inset, minY + depth, maxZ - inset);
            case UP -> new AABB(minX + inset, maxY - depth, minZ + inset, maxX - inset, maxY + eps, maxZ - inset);
            case NORTH -> new AABB(minX + inset, minY + inset, minZ - eps, maxX - inset, maxY - inset, minZ + depth);
            case SOUTH -> new AABB(minX + inset, minY + inset, maxZ - depth, maxX - inset, maxY - inset, maxZ + eps);
            case WEST -> new AABB(minX - eps, minY + inset, minZ + inset, minX + depth, maxY - inset, maxZ - inset);
            case EAST -> new AABB(maxX - depth, minY + inset, minZ + inset, maxX + eps, maxY - inset, maxZ - inset);
        };
    }

    private static int multiplyAlpha(int argb, float factor) {
        int alpha = (argb >>> 24) & 0xFF;
        int nextAlpha = Math.max(0, Math.min(255, Math.round(alpha * factor)));
        return (argb & 0x00FFFFFF) | (nextAlpha << 24);
    }

    private static int emphasizeAlpha(int argb) {
        int alpha = (argb >>> 24) & 0xFF;
        int nextAlpha = Math.max(alpha, 170);
        return (argb & 0x00FFFFFF) | (Math.min(255, nextAlpha) << 24);
    }

    private static void addFilledBox(Renderer3D renderer, AABB box, int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;

        double minX = box.minX;
        double minY = box.minY;
        double minZ = box.minZ;
        double maxX = box.maxX;
        double maxY = box.maxY;
        double maxZ = box.maxZ;

        renderer.quad(minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a);
        renderer.quad(minX, maxY, minZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, r, g, b, a);
        renderer.quad(minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a);
        renderer.quad(minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ, maxX, minY, minZ, r, g, b, a);
        renderer.quad(maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, maxX, minY, maxZ, r, g, b, a);
        renderer.quad(minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ, r, g, b, a);
    }

    private static void addOutlineBox(Renderer3D renderer, AABB box, int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;

        double minX = box.minX;
        double minY = box.minY;
        double minZ = box.minZ;
        double maxX = box.maxX;
        double maxY = box.maxY;
        double maxZ = box.maxZ;

        renderer.line(minX, minY, minZ, maxX, minY, minZ, r, g, b, a);
        renderer.line(maxX, minY, minZ, maxX, minY, maxZ, r, g, b, a);
        renderer.line(maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a);
        renderer.line(minX, minY, maxZ, minX, minY, minZ, r, g, b, a);

        renderer.line(minX, maxY, minZ, maxX, maxY, minZ, r, g, b, a);
        renderer.line(maxX, maxY, minZ, maxX, maxY, maxZ, r, g, b, a);
        renderer.line(maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a);
        renderer.line(minX, maxY, maxZ, minX, maxY, minZ, r, g, b, a);

        renderer.line(minX, minY, minZ, minX, maxY, minZ, r, g, b, a);
        renderer.line(maxX, minY, minZ, maxX, maxY, minZ, r, g, b, a);
        renderer.line(maxX, minY, maxZ, maxX, maxY, maxZ, r, g, b, a);
        renderer.line(minX, minY, maxZ, minX, maxY, maxZ, r, g, b, a);
    }

    @Override
    public void onEnable() {
        resetRuntimeState();
        scanContext.refresh();
        requestLocalSectionScan();
    }

    @Override
    public void onTick() {
        if (!isEnabled() || mc.level == null) {
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
            return;
        }

        BlockScanMode mode = modeValue.get();
        if (lastMode != mode) {
            clearRenderedBeds();
            lastMode = mode;
            requestLocalSectionScan();
        }

        maintainSectionScanQueue();
        drainSectionScanQueue();

        reconcileObservedBeds("cached");
    }

    @Override
    public void onDisable() {
        resetRuntimeState();
    }

    private void resetRuntimeState() {
        clearTargets();
        lastMode = null;
        lastWorld = mc.level;
        lastScanSignature = Integer.MIN_VALUE;
        sectionScanRefreshTicks = 0;
        sectionScanner.reset();
    }

    private void clearTargets() {
        clearRenderedBeds();
        observedBedStates.clear();
    }

    private void clearRenderedBeds() {
        renderTargets = new ConcurrentHashMap<>();
        bedPartToKey.clear();
        loggedBeds.clear();
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
        if (!isEnabled() || mc.level == null || mc.player == null) return;

        float previousLineWidth = RenderState.lineWidth;
        RenderState.lineWidth = Math.max(0.5f, lineWidth.get());
        try {
            DefineTarget defineTarget = Modules.get(DefineTarget.class);
            boolean useTeamColors = defineTarget != null && defineTarget.useBedwarsTeamColors();
            for (BedRenderTarget target : renderTargets.values()) {
                int bedColor = target.self() ? selfBedColor.getArgb() : otherBedColor.getArgb();
                if (useTeamColors && target.teamColorRgb() != -1) {
                    bedColor = BedwarsTeamColorUtil.replaceRgb(bedColor, target.teamColorRgb());
                }

                for (BlockPos bedPart : target.bedParts()) {
                    BlockState state = mc.level.getBlockState(bedPart);
                    if (!BedBlockUtil.isBed(state)) {
                        continue;
                    }
                    BlockOutlineRenderer.render(renderer, mc.level, bedPart, state, outlineShapeMode.get(), emphasizeAlpha(bedColor));
                }

                if (!showOpeningsValue.get()) {
                    continue;
                }

                int openingColor = target.self() ? selfOpeningColor.getArgb() : otherOpeningColor.getArgb();
                if (useTeamColors && target.teamColorRgb() != -1) {
                    openingColor = BedwarsTeamColorUtil.replaceRgb(openingColor, target.teamColorRgb());
                }

                for (FaceMarker opening : target.openingMarkers()) {
                    addFilledBox(renderer, opening.box(), multiplyAlpha(openingColor, 0.85f));
                    addOutlineBox(renderer, opening.box(), emphasizeAlpha(openingColor));
                }
            }
        } finally {
            RenderState.lineWidth = previousLineWidth;
        }

        renderTracers(renderer, tickDelta);
    }

    public void acceptSodiumBlockState(int x, int y, int z, BlockState state) {
        if (!isEnabled() || !BedBlockUtil.isBed(state)) {
            return;
        }
        recordObservedBlock(new BlockPos(x, y, z), state, false);
    }

    public void acceptWorldBlockUpdate(BlockPos pos, BlockState state) {
        if (!isEnabled()) {
            return;
        }
        recordObservedBlock(pos, state, true);
    }

    private void recordObservedBlock(BlockPos rawPos, BlockState state) {
        recordObservedBlock(rawPos, state, false);
    }

    private void recordObservedBlock(BlockPos rawPos, BlockState state, boolean forgetScannedSection) {
        if (rawPos == null || state == null) {
            return;
        }

        BlockPos pos = rawPos.immutable();
        if (forgetScannedSection) {
            sectionScanner.forget(pos);
        }
        if (!BedBlockUtil.isBed(state)) {
            observedBedStates.remove(pos);
            removeBedByPart(pos);
            return;
        }

        RenderSectionBlockScanner.recordBlock(pos, state);
        observedBedStates.put(pos, state);
    }

    private void updateObservedBed(BlockPos pos, BlockState state, String source) {
        if (mc.level == null || mc.player == null || !withinDistance(pos)) {
            removeBedByPart(pos);
            return;
        }

        boolean visible = scanContext.hasTransparentNeighbor(pos);
        if (!acceptsMode(pos, visible)) {
            removeBedByPart(pos);
            return;
        }

        BlockPos key = BedBlockUtil.canonicalBedPos(pos, state);
        if (key == null) {
            removeBedByPart(pos);
            return;
        }
        key = key.immutable();
        if (!renderTargets.containsKey(key) && renderTargets.size() >= maxTargetsValue.get()) {
            return;
        }

        Map<BlockPos, BedRenderTarget> built = buildBedTargets(List.of(
                new RenderSectionBlockScanner.ScanResult(pos, state, visible)
        ));
        BedRenderTarget target = built.get(key);
        if (target == null) {
            return;
        }

        BedRenderTarget previous = renderTargets.put(key, target);
        if (previous != null) {
            for (BlockPos bedPart : previous.bedParts()) {
                bedPartToKey.remove(bedPart);
            }
        }
        for (BlockPos bedPart : target.bedParts()) {
            bedPartToKey.put(bedPart, key);
        }

        if (loggedBeds.add(key.asLong())) {
            DebugLog.info(
                    "[BedwarsESP] detected %s at %s visible=%s source=%s mode=%s",
                    BuiltInRegistries.BLOCK.getKey(state.getBlock()),
                    key.toShortString(),
                    visible,
                    source,
                    modeValue.get().getId()
            );
        }
    }

    private void reconcileObservedBeds(String source) {
        for (Map.Entry<BlockPos, BlockState> entry : observedBedStates.entrySet()) {
            BlockPos pos = entry.getKey();
            BlockState state = entry.getValue();
            if (mc.level != null && withinDistance(pos)) {
                BlockState currentState = mc.level.getBlockState(pos);
                if (BedBlockUtil.isBed(currentState)) {
                    if (currentState != state) {
                        observedBedStates.put(pos, currentState);
                        state = currentState;
                    }
                } else {
                    observedBedStates.remove(pos);
                    removeBedByPart(pos);
                    continue;
                }
            }
            updateObservedBed(pos, state, source);
        }
    }

    private void removeBedByPart(BlockPos pos) {
        BlockPos key = bedPartToKey.remove(pos);
        if (key == null) {
            return;
        }

        BedRenderTarget removed = renderTargets.remove(key);
        loggedBeds.remove(key.asLong());
        if (removed != null) {
            for (BlockPos bedPart : removed.bedParts()) {
                bedPartToKey.remove(bedPart);
            }
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
            requestLocalSectionScan();
        }
    }

    private void requestLocalSectionScan() {
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
    }

    private void drainSectionScanQueue() {
        sectionScanner.drain(
                mc,
                SECTION_SCAN_BUDGET_PER_TICK,
                BedBlockUtil::isBed,
                this::recordObservedBlock
        );
    }

    private int scanBlockRadius() {
        return limitDistanceValue.get() ? Math.max(1, maxDistanceValue.get()) : 256;
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
        result = 31 * result + maxLayers.get();
        return result;
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

    private boolean acceptsMode(BlockPos pos, boolean visible) {
        int maxRange = limitDistanceValue.get() ? maxDistanceValue.get() : 256;
        return switch (modeValue.get()) {
            case LEGIT -> visible && mc.player != null
                    && scanContext.hasPath(mc.player.getEyePosition(), pos, limitDistanceValue.get(), maxRange);
            case LOS, LOS_UNLOCKED -> visible;
            case AGGRESSIVE -> true;
        };
    }

    private Map<BlockPos, BedRenderTarget> buildBedTargets(Collection<RenderSectionBlockScanner.ScanResult> scanResults) {
        Map<BlockPos, MutableBedTarget> grouped = new HashMap<>();
        for (RenderSectionBlockScanner.ScanResult result : scanResults) {
            BlockState state = result.state();
            BlockPos key = BedBlockUtil.canonicalBedPos(result.pos(), state);
            if (key == null) {
                continue;
            }

            MutableBedTarget target = grouped.computeIfAbsent(key, ignored -> new MutableBedTarget());
            target.sourcePos = result.pos();
            target.sourceState = state;
            target.visible |= result.visible();
            Integer teamColorRgb = BedwarsTeamColorUtil.getBedTeamRgb(state);
            if (teamColorRgb != null) {
                target.teamColorRgb = teamColorRgb;
            }
            target.self |= SelfBedTracker.INSTANCE.isSelfBed(
                    selfBedMode.get(),
                    state,
                    result.pos(),
                    mc.player,
                    spawnBedDistance.get()
            );
            target.bedParts.add(result.pos().immutable());

            net.minecraft.core.Direction direction = BedBlockUtil.anotherBedPartDirection(state);
            if (direction != null) {
                target.bedParts.add(result.pos().relative(direction).immutable());
            }
        }

        Map<BlockPos, BedRenderTarget> next = new HashMap<>(grouped.size());
        for (Map.Entry<BlockPos, MutableBedTarget> entry : grouped.entrySet()) {
            MutableBedTarget value = entry.getValue();
            if (value.sourcePos == null || value.sourceState == null || value.bedParts.isEmpty()) {
                continue;
            }

            List<AABB> bedBoxes = new ArrayList<>(value.bedParts.size());
            double cx = 0.0;
            double cy = 0.0;
            double cz = 0.0;
            for (BlockPos bedPart : value.bedParts) {
                bedBoxes.add(new AABB(bedPart));
                Vec3 center = Vec3.atCenterOf(bedPart);
                cx += center.x;
                cy += center.y;
                cz += center.z;
            }
            Vec3 tracerCenter = new Vec3(cx / value.bedParts.size(), cy / value.bedParts.size(), cz / value.bedParts.size());

            List<FaceMarker> openingMarkers = List.of();
            if (showOpeningsValue.get()) {
                LinkedHashSet<BlockPos> shellPositions = new LinkedHashSet<>();
                List<BedBlockUtil.LayeredBlockPos> layerPositions = BedBlockUtil.searchBedLayer(
                        value.sourcePos,
                        value.sourceState,
                        maxLayers.get()
                );
                layerPositions.sort(Comparator.comparingInt(BedBlockUtil.LayeredBlockPos::layer));
                for (BedBlockUtil.LayeredBlockPos layerPos : layerPositions) {
                    shellPositions.add(layerPos.pos());
                }
                openingMarkers = collectOpeningMarkers(value.bedParts, shellPositions);
            }

            next.put(entry.getKey(), new BedRenderTarget(
                    List.copyOf(value.bedParts),
                    List.copyOf(bedBoxes),
                    openingMarkers,
                    tracerCenter,
                    value.self,
                    value.visible,
                    value.teamColorRgb
            ));
        }
        return next;
    }

    private List<FaceMarker> collectOpeningMarkers(Collection<BlockPos> bedParts, Collection<BlockPos> shellPositions) {
        if (bedParts.isEmpty()) {
            return List.of();
        }

        Set<BlockPos> bedSet = new HashSet<>(bedParts);
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (BlockPos pos : bedParts) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        for (BlockPos pos : shellPositions) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }

        minX--;
        minY--;
        minZ--;
        maxX++;
        maxY++;
        maxZ++;

        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> reachable = new HashSet<>();
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                seedOpeningBoundary(queue, reachable, bedSet, mutable.set(x, y, minZ));
                seedOpeningBoundary(queue, reachable, bedSet, mutable.set(x, y, maxZ));
            }
        }
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                seedOpeningBoundary(queue, reachable, bedSet, mutable.set(x, minY, z));
                seedOpeningBoundary(queue, reachable, bedSet, mutable.set(x, maxY, z));
            }
        }
        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                seedOpeningBoundary(queue, reachable, bedSet, mutable.set(minX, y, z));
                seedOpeningBoundary(queue, reachable, bedSet, mutable.set(maxX, y, z));
            }
        }

        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();
            for (net.minecraft.core.Direction direction : BlockScanContext.DIRECTIONS) {
                mutable.set(current).move(direction);
                if (mutable.getX() < minX || mutable.getX() > maxX
                        || mutable.getY() < minY || mutable.getY() > maxY
                        || mutable.getZ() < minZ || mutable.getZ() > maxZ) {
                    continue;
                }
                seedOpeningBoundary(queue, reachable, bedSet, mutable);
            }
        }

        LinkedHashSet<FaceKey> openings = new LinkedHashSet<>();
        for (BlockPos airPos : reachable) {
            for (net.minecraft.core.Direction direction : BlockScanContext.DIRECTIONS) {
                BlockPos neighbor = airPos.relative(direction);
                if (!bedSet.contains(neighbor)) {
                    continue;
                }
                openings.add(new FaceKey(neighbor.immutable(), direction.getOpposite()));
            }
        }

        if (openings.isEmpty()) {
            return List.of();
        }

        List<FaceMarker> result = new ArrayList<>(openings.size());
        for (FaceKey opening : openings) {
            result.add(new FaceMarker(createFaceBox(opening.pos(), opening.face())));
        }
        return List.copyOf(result);
    }

    private void seedOpeningBoundary(ArrayDeque<BlockPos> queue,
                                     Set<BlockPos> reachable,
                                     Set<BlockPos> bedSet,
                                     BlockPos pos) {
        if (bedSet.contains(pos) || !scanContext.isTransparent(pos)) {
            return;
        }
        BlockPos immutable = pos.immutable();
        if (reachable.add(immutable)) {
            queue.addLast(immutable);
        }
    }

    private void renderTracers(Renderer3D renderer, float tickDelta) {
        Tracers tracers = Modules.get(Tracers.class);
        if (!useTracersValue.get() || tracers == null) return;

        DefineTarget defineTarget = Modules.get(DefineTarget.class);
        boolean useTeamColors = defineTarget != null && defineTarget.useBedwarsTeamColors();
        for (BedRenderTarget target : renderTargets.values()) {
            int color = target.self() ? selfBedColor.getArgb() : otherBedColor.getArgb();
            if (useTeamColors && target.teamColorRgb() != -1) {
                color = BedwarsTeamColorUtil.replaceRgb(color, target.teamColorRgb());
            }
            tracers.render3DTracersRaw(renderer, tickDelta, List.of(target.tracerCenter()), color);
        }
    }

    private static final class MutableBedTarget {
        private final LinkedHashSet<BlockPos> bedParts = new LinkedHashSet<>();
        private BlockPos sourcePos;
        private BlockState sourceState;
        private boolean self;
        private boolean visible;
        private int teamColorRgb = -1;
    }

    private record FaceKey(BlockPos pos, net.minecraft.core.Direction face) {
    }

    private record FaceMarker(AABB box) {
    }

    private record BedRenderTarget(
            List<BlockPos> bedParts,
            List<AABB> bedBoxes,
            List<FaceMarker> openingMarkers,
            Vec3 tracerCenter,
            boolean self,
            boolean visible,
            int teamColorRgb
    ) {
    }
}

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.visuals;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.RGBAColorValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.PacketEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.WorldPhase;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.util.finder.FinderRender;
import combatant.client.util.finder.LoadedChunkScanner;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.EnchantingTableBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.entity.TrappedChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Highlights storage-like block entities by type, with per-type colour and tracer. Containers the
 * player interacted with this session are dimmed so unlooted ones stand out. Uses the shared
 * chunk scanner and {@link FinderRender} batches, like the finder modules.
 */
@ModuleInfo(
        id = "storageesp",
        displayName = "StorageESP",
        aliases = {"ChestESP", "ContainerESP", "StorageFinder"},
        category = ModuleCategory.VISUALS, subcategory = ModuleSubcategory.ESP,
        description = "module.storageesp.description")
public final class StorageESP extends Module {

    private static final double INSET = 1.0 / 16.0;
    private static final double CHEST_HEIGHT = 14.0 / 16.0;
    private static final int OPENED_LIMIT = 4096;

    public enum RenderMode {
        BOX,
        OUTLINE,
        FILL
    }

    private enum Kind {
        CHEST("chest", "#FFFFA52E"),
        TRAPPED_CHEST("trapped_chest", "#FFFF4A3D"),
        ENDER_CHEST("ender_chest", "#FFB45CFF"),
        SHULKER("shulker", "#FFFF7BE0"),
        BARREL("barrel", "#FFC98B4D"),
        SPAWNER("spawner", "#FF4DD0FF"),
        FURNACE("furnace", "#FF9AA5B1"),
        HOPPER("hopper", "#FF6C7A89"),
        ENCHANTING_TABLE("enchanting_table", "#FF5CE8C8");

        final String id;
        final String defaultColor;

        Kind(String id, String defaultColor) {
            this.id = id;
            this.defaultColor = defaultColor;
        }
    }

    private final EnumValue<RenderMode> renderMode = enumMode("render_mode", RenderMode.BOX);
    private final NumberValue<Integer> maxDistance = num("max_distance", 96, 8, 512);
    private final NumberValue<Integer> lineWidth = num("line_width", 2, 1, 6);
    private final BooleanValue dimOpened = bool("dim_opened", true);
    private final NumberValue<Integer> openedOpacity =
            visibleWhen(num("opened_opacity", 30, 5, 90), () -> dimOpened.get());

    private final Map<Kind, BooleanValue> enabled = new EnumMap<>(Kind.class);
    private final Map<Kind, RGBAColorValue> colors = new EnumMap<>(Kind.class);
    private final Map<Kind, BooleanValue> tracers = new EnumMap<>(Kind.class);
    private final BooleanValue shulkerDyeColor;

    private final Minecraft mc = Minecraft.getInstance();
    private final LoadedChunkScanner<List<Found>> scanner = new LoadedChunkScanner<>(
            StorageESP::findStorage,
            () -> Math.min(mc.options.renderDistance().get(), 16),
            8,
            1500L);
    private final LongOpenHashSet opened = new LongOpenHashSet();
    private Level openedLevel;

    {
        for (Kind kind : Kind.values()) {
            BooleanValue toggle = bool(kind.id, true);
            enabled.put(kind, toggle);
            colors.put(kind, visibleWhen(color(kind.id + "_color", kind.defaultColor), () -> toggle.get()));
            tracers.put(kind, visibleWhen(bool(kind.id + "_tracer", false), () -> toggle.get()));
        }
        BooleanValue shulkerToggle = enabled.get(Kind.SHULKER);
        shulkerDyeColor = visibleWhen(bool("shulker_dye_color", true), () -> shulkerToggle.get());
    }

    @Override
    public void onEnable() {
        scanner.clear();
        opened.clear();
        openedLevel = null;
    }

    @Override
    public void onDisable() {
        scanner.clear();
        opened.clear();
        openedLevel = null;
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        scanner.onPacket(event.getPacket());
        // Spawner and shulker data can arrive after the chunk itself.
        if (event.getPacket() instanceof ClientboundBlockEntityDataPacket data) {
            scanner.markDirty(data.getPos().getX() >> 4, data.getPos().getZ() >> 4);
        }
    }

    // The client never learns which block a container screen belongs to, so remember the block
    // the player right-clicked while it holds a block entity.
    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (!(event.getPacket() instanceof ServerboundUseItemOnPacket use)) return;
        ClientLevel level = mc.level;
        if (level == null || opened.size() >= OPENED_LIMIT) return;
        BlockPos pos = use.hitResult().getBlockPos();
        if (level.getBlockEntity(pos) != null) opened.add(pos.asLong());
    }

    @Override
    public void onTick() {
        if (!isEnabled() || mc.level == null || mc.player == null) return;
        if (openedLevel != mc.level) {
            opened.clear();
            openedLevel = mc.level;
        }
        scanner.tick(mc);
    }

    private static List<Found> findStorage(ClientLevel level, LevelChunk chunk) {
        List<Found> out = null;
        for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
            BlockEntity be = entry.getValue();
            Kind kind = classify(be);
            if (kind == null) continue;
            BlockPos pos = entry.getKey().immutable();
            BlockState state = be.getBlockState();
            AABB box = new AABB(pos);
            long partner = Long.MIN_VALUE;
            int dye = 0;
            if (kind == Kind.CHEST || kind == Kind.TRAPPED_CHEST || kind == Kind.ENDER_CHEST) {
                box = chestBox(pos);
                if (state.hasProperty(ChestBlock.TYPE) && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
                    BlockPos other = pos.relative(ChestBlock.getConnectedDirection(state));
                    partner = other.asLong();
                    // A double chest is one box, drawn from its left half.
                    if (state.getValue(ChestBlock.TYPE) != ChestType.LEFT) continue;
                    box = box.minmax(chestBox(other));
                }
            } else if (kind == Kind.SHULKER && be instanceof ShulkerBoxBlockEntity shulker) {
                DyeColor color = shulker.getColor();
                if (color != null) dye = 0xFF000000 | color.getTextureDiffuseColor();
            }
            if (out == null) out = new ArrayList<>();
            out.add(new Found(pos.asLong(), partner, kind, box, dye));
        }
        return out;
    }

    private static AABB chestBox(BlockPos pos) {
        return new AABB(pos.getX() + INSET, pos.getY(), pos.getZ() + INSET,
                pos.getX() + 1.0 - INSET, pos.getY() + CHEST_HEIGHT, pos.getZ() + 1.0 - INSET);
    }

    private static Kind classify(BlockEntity be) {
        // Subclasses first: trapped chests extend chests.
        if (be instanceof TrappedChestBlockEntity) return Kind.TRAPPED_CHEST;
        if (be instanceof ChestBlockEntity) return Kind.CHEST;
        if (be instanceof EnderChestBlockEntity) return Kind.ENDER_CHEST;
        if (be instanceof ShulkerBoxBlockEntity) return Kind.SHULKER;
        if (be instanceof BarrelBlockEntity) return Kind.BARREL;
        if (be instanceof SpawnerBlockEntity) return Kind.SPAWNER;
        if (be instanceof AbstractFurnaceBlockEntity) return Kind.FURNACE;
        if (be instanceof HopperBlockEntity || be instanceof DispenserBlockEntity) return Kind.HOPPER;
        if (be instanceof EnchantingTableBlockEntity) return Kind.ENCHANTING_TABLE;
        return null;
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.END_MAIN;
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        LocalPlayer player = mc.player;
        if (!isEnabled() || renderer == null || player == null) return;

        double max = maxDistance.get();
        double maxSq = max * max;
        RenderMode mode = renderMode.get();
        boolean dim = dimOpened.get();
        float dimFactor = openedOpacity.get() / 100.0f;
        boolean dye = shulkerDyeColor.get();
        FinderRender draw = FinderRender.begin(renderer, lineWidth.get());

        for (List<Found> chunk : scanner.results().values()) {
            for (Found found : chunk) {
                if (!enabled.get(found.kind).get()) continue;
                Vec3 center = found.box.getCenter();
                if (player.distanceToSqr(center) > maxSq) continue;

                int base = found.kind == Kind.SHULKER && dye && found.dye != 0
                        ? found.dye
                        : colors.get(found.kind).getArgb();
                if (dim && (opened.contains(found.pos) || opened.contains(found.partner))) {
                    base = scaleAlpha(base, dimFactor);
                }

                int line = mode == RenderMode.FILL ? 0 : base;
                int fill = switch (mode) {
                    case OUTLINE -> 0;
                    case BOX -> scaleAlpha(base, 0.2f);
                    case FILL -> scaleAlpha(base, 0.45f);
                };
                draw.box(found.box, fill, line);
                if (tracers.get(found.kind).get()) draw.tracer(center, base);
            }
        }
    }

    private static int scaleAlpha(int argb, float factor) {
        int a = Math.round(((argb >>> 24) & 0xFF) * factor);
        return (argb & 0x00FFFFFF) | (Math.max(0, Math.min(255, a)) << 24);
    }

    private record Found(long pos, long partner, Kind kind, AABB box, int dye) {
    }
}

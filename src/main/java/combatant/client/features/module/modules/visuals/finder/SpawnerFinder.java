/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.visuals.finder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.RGBAColorValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.PacketEvent;
import combatant.client.features.module.HudPhase;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.WorldPhase;
import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.render.engine.text.BuiltinFontCatalog;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.render.helpers.MatteHudStyle;
import combatant.client.render.helpers.ScreenProjection;
import combatant.client.util.finder.FinderAlerts;
import combatant.client.util.finder.FinderRender;
import combatant.client.util.finder.LoadedChunkScanner;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Spawner ESP with type labels, the 16-block activation ring and alerts for new finds.
 * Merges WaterClient's SpawnerNotifier and 67Client's SpawnerNametags.
 */
@ModuleInfo(
        id = "spawnerfinder",
        displayName = "SpawnerFinder",
        aliases = {"SpawnerESP", "SpawnerNotifier", "SpawnerNametags"},
        category = ModuleCategory.VISUALS, subcategory = ModuleSubcategory.ESP,
        description = "module.spawnerfinder.description")
public final class SpawnerFinder extends Module {

    // Vanilla spawners only run while a player is within this many blocks.
    private static final double ACTIVATION_RANGE = 16.0;
    private static final float LABEL_HEIGHT = 17.0f;
    private static final float LABEL_PAD_X = 4.0f;
    private static final float LABEL_RADIUS = 3.6f;
    private static final float LABEL_TEXT_SCALE = 0.8f;

    private final NumberValue<Integer> maxDistance = num("max_distance", 128, 16, 512);
    private final BooleanValue nametags = bool("nametags", true);
    private final BooleanValue activationRing = bool("activation_ring", true);
    private final BooleanValue tracers = bool("tracers", false);
    private final BooleanValue trialSpawners = bool("trial_spawners", true);
    private final BooleanValue chatAlerts = bool("chat_alerts", true);
    private final RGBAColorValue boxColor = color("box_color", "#FFFF5050");
    private final RGBAColorValue ringColor = color("ring_color", "#90FF5050");
    // Shows at a glance which spawners are running because you stand in their range.
    private final RGBAColorValue activeColor = color("active_color", "#FF50FF50");

    private final Minecraft mc = Minecraft.getInstance();
    private final LoadedChunkScanner<List<Found>> scanner = new LoadedChunkScanner<>(
            SpawnerFinder::findSpawners,
            () -> Math.min(mc.options.renderDistance().get(), 16),
            8,
            1500L);
    private final Set<Long> alerted = new HashSet<>();
    private final List<Label> labels = new ArrayList<>();

    @Override
    public void onEnable() {
        scanner.clear();
        alerted.clear();
    }

    @Override
    public void onDisable() {
        scanner.clear();
        alerted.clear();
        labels.clear();
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        scanner.onPacket(event.getPacket());
        // The spawned mob type arrives in block-entity data, sometimes after the chunk itself.
        if (event.getPacket() instanceof ClientboundBlockEntityDataPacket data) {
            scanner.markDirty(data.getPos().getX() >> 4, data.getPos().getZ() >> 4);
        }
    }

    @Override
    public void onTick() {
        if (!isEnabled() || mc.level == null || mc.player == null) return;
        scanner.tick(mc);

        for (List<Found> spawners : scanner.results().values()) {
            for (Found spawner : spawners) {
                if (!shown(spawner)) continue;
                if (alerted.add(spawner.pos.asLong())) {
                    FinderAlerts.found(getDisplayName(), spawner.type + " spawner",
                            spawner.pos.getX(), spawner.pos.getY(), spawner.pos.getZ(),
                            chatAlerts.get(), false);
                }
            }
        }
    }

    private static List<Found> findSpawners(ClientLevel level, LevelChunk chunk) {
        List<Found> out = null;
        for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
            String type = null;
            boolean trial = false;
            if (entry.getValue() instanceof SpawnerBlockEntity spawner) {
                Entity display = spawner.getSpawner().getOrCreateDisplayEntity(level, entry.getKey());
                type = display != null ? display.getType().getDescription().getString() : "Empty";
            } else if (entry.getValue() instanceof TrialSpawnerBlockEntity) {
                type = "Trial";
                trial = true;
            }
            if (type == null) continue;
            if (out == null) out = new ArrayList<>();
            out.add(new Found(entry.getKey().immutable(), type, trial));
        }
        return out;
    }

    private boolean shown(Found spawner) {
        return !spawner.trial || trialSpawners.get();
    }

    private boolean inRange(LocalPlayer player, BlockPos pos) {
        double max = maxDistance.get();
        return player.distanceToSqr(Vec3.atCenterOf(pos)) <= max * max;
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.END_MAIN;
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        LocalPlayer player = mc.player;
        if (!isEnabled() || renderer == null || player == null) return;
        FinderRender draw = FinderRender.begin(renderer, 1.5f);
        double activeSq = ACTIVATION_RANGE * ACTIVATION_RANGE;
        for (List<Found> spawners : scanner.results().values()) {
            for (Found spawner : spawners) {
                if (!shown(spawner) || !inRange(player, spawner.pos)) continue;
                BlockPos p = spawner.pos;
                boolean active = player.distanceToSqr(Vec3.atCenterOf(p)) <= activeSq;
                int box = active ? activeColor.getArgb() : boxColor.getArgb();
                int fill = (box & 0x00FFFFFF) | 0x30000000;
                draw.blockBox(p.getX(), p.getY(), p.getZ(), fill, box);
                if (activationRing.get()) draw.ring(Vec3.atCenterOf(p), ACTIVATION_RANGE, ringColor.getArgb());
                if (tracers.get()) draw.tracer(Vec3.atCenterOf(p), box);
            }
        }
    }

    @Override
    public HudPhase getHudPhase() {
        return HudPhase.BEFORE_MISC_OVERLAYS;
    }

    // Plates go in the main pass and text in the foreground pass, the same split Predictions
    // uses so labels sit above other ESP plates.
    @Override
    public void onRenderHudEngine(Renderer2D renderer, TextRenderer textRenderer, GuiGraphicsExtractor ctx, float tickDelta) {
        labels.clear();
        LocalPlayer player = mc.player;
        if (!isEnabled() || !nametags.get() || player == null) return;

        TextRenderer font = BuiltinFontCatalog.ONEST_MEDIUM.renderer(textRenderer);
        for (List<Found> spawners : scanner.results().values()) {
            for (Found spawner : spawners) {
                if (!shown(spawner) || !inRange(player, spawner.pos)) continue;
                Vec3 screen = ScreenProjection.worldToScreen(Vec3.atCenterOf(spawner.pos).add(0.0, 0.9, 0.0), tickDelta);
                if (screen == null) continue;

                int distance = (int) Math.sqrt(player.distanceToSqr(Vec3.atCenterOf(spawner.pos)));
                String text = spawner.type + " · " + distance + "m";
                float textWidth = (float) font.getWidth(text) * LABEL_TEXT_SCALE;
                float width = textWidth + LABEL_PAD_X * 2.0f;
                float x = (float) screen.x - width * 0.5f;
                float y = (float) screen.y - LABEL_HEIGHT;
                MatteHudStyle.drawEspMattePlate(renderer, x, y, width, LABEL_HEIGHT, LABEL_RADIUS, 1.0f);

                float textHeight = (float) font.getHeight() * LABEL_TEXT_SCALE;
                labels.add(new Label(text, x + LABEL_PAD_X, y + (LABEL_HEIGHT - textHeight) * 0.5f));
            }
        }
    }

    @Override
    public void onRenderHudEngineForeground(Renderer2D renderer, TextRenderer textRenderer, GuiGraphicsExtractor ctx, float tickDelta) {
        if (!isEnabled() || labels.isEmpty()) return;
        TextRenderer font = BuiltinFontCatalog.ONEST_MEDIUM.renderer(textRenderer);
        font.begin(LABEL_TEXT_SCALE);
        for (Label label : labels) {
            font.render(label.text, label.x, label.y, new RenderColor(0xFFFFFFFF), false);
        }
        font.end();
    }

    private record Found(BlockPos pos, String type, boolean trial) {
    }

    private record Label(String text, float x, float y) {
    }
}

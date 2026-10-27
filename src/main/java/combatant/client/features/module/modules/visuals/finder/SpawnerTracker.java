/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.visuals.finder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.RGBAColorValue;
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
import java.util.function.Supplier;

/**
 * Spawner ESP with type labels, the 16-block activation ring and alerts for new finds.
 * Merges WaterClient's SpawnerNotifier and 67Client's SpawnerNametags.
 */
public final class SpawnerTracker {

    /** Settings the owning module declares; read live so edits apply immediately. */
    public record Settings(NumberValue<Integer> maxDistance,
                           BooleanValue nametags,
                           BooleanValue activationRing,
                           BooleanValue tracers,
                           BooleanValue trialSpawners,
                           BooleanValue chatAlerts,
                           RGBAColorValue boxColor,
                           RGBAColorValue ringColor,
                           RGBAColorValue activeColor) {
    }

    // Vanilla spawners only run while a player is within this many blocks.
    private static final double ACTIVATION_RANGE = 16.0;
    private static final float LABEL_HEIGHT = 17.0f;
    private static final float LABEL_PAD_X = 4.0f;
    private static final float LABEL_RADIUS = 3.6f;
    private static final float LABEL_TEXT_SCALE = 0.8f;

    private final Minecraft mc = Minecraft.getInstance();
    private final Settings settings;
    private final Supplier<String> alertSource;
    private final LoadedChunkScanner<List<Found>> scanner = new LoadedChunkScanner<>(
            SpawnerTracker::findSpawners,
            () -> Math.min(mc.options.renderDistance().get(), 16),
            8,
            1500L);
    private final Set<Long> alerted = new HashSet<>();
    private final List<Label> labels = new ArrayList<>();

    public SpawnerTracker(Settings settings, Supplier<String> alertSource) {
        this.settings = settings;
        this.alertSource = alertSource;
    }

    public void reset() {
        scanner.clear();
        alerted.clear();
        labels.clear();
    }

    public void onPacket(Packet<?> packet) {
        scanner.onPacket(packet);
        // The spawned mob type arrives in block-entity data, sometimes after the chunk itself.
        if (packet instanceof ClientboundBlockEntityDataPacket data) {
            scanner.markDirty(data.getPos().getX() >> 4, data.getPos().getZ() >> 4);
        }
    }

    public void tick() {
        if (mc.level == null || mc.player == null) return;
        scanner.tick(mc);

        for (List<Found> spawners : scanner.results().values()) {
            for (Found spawner : spawners) {
                if (!shown(spawner)) continue;
                if (alerted.add(spawner.pos.asLong())) {
                    FinderAlerts.found(alertSource.get(), spawner.type + " spawner",
                            spawner.pos.getX(), spawner.pos.getY(), spawner.pos.getZ(),
                            settings.chatAlerts().get(), false);
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
        return !spawner.trial || settings.trialSpawners().get();
    }

    private boolean inRange(LocalPlayer player, BlockPos pos) {
        double max = settings.maxDistance().get();
        return player.distanceToSqr(Vec3.atCenterOf(pos)) <= max * max;
    }

    public void render(Renderer3D renderer, ShaderBoxModule style) {
        LocalPlayer player = mc.player;
        if (renderer == null || player == null) return;
        FinderRender draw = FinderRender.begin(renderer, 1.5f);
        double activeSq = ACTIVATION_RANGE * ACTIVATION_RANGE;
        for (List<Found> spawners : scanner.results().values()) {
            for (Found spawner : spawners) {
                if (!shown(spawner) || !inRange(player, spawner.pos)) continue;
                BlockPos p = spawner.pos;
                boolean active = player.distanceToSqr(Vec3.atCenterOf(p)) <= activeSq;
                int box = active ? settings.activeColor().getArgb() : settings.boxColor().getArgb();
                if (!style.drawShaderBox(renderer, new AABB(p), box)) {
                    int fill = (box & 0x00FFFFFF) | 0x30000000;
                    draw.blockBox(p.getX(), p.getY(), p.getZ(), fill, box);
                }
                if (settings.activationRing().get()) {
                    draw.ring(Vec3.atCenterOf(p), ACTIVATION_RANGE, settings.ringColor().getArgb());
                }
                if (settings.tracers().get()) draw.tracer(Vec3.atCenterOf(p), box);
            }
        }
    }

    // Plates go in the main pass and text in the foreground pass, the same split Predictions
    // uses so labels sit above other ESP plates.
    public void renderHud(Renderer2D renderer, TextRenderer textRenderer, float tickDelta) {
        labels.clear();
        LocalPlayer player = mc.player;
        if (!settings.nametags().get() || player == null) return;

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

    public void renderHudForeground(Renderer2D renderer, TextRenderer textRenderer) {
        if (labels.isEmpty()) return;
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

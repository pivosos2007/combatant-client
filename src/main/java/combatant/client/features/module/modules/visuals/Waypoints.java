/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.visuals;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.Notifier;
import combatant.client.features.module.WorldPhase;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.render.engine.text.VanillaTextRenderer;
import combatant.client.render.helpers.ScreenProjection;
import combatant.client.render.helpers.ScreenSpaceOverlay2D;
import combatant.client.util.screen.ClientScreen;
import combatant.client.util.waypoint.WaypointStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Renders saved per-world waypoints as a world beacon beam and a projected name/distance label.
 * Points are managed with {@code @waypoint}, the {@code add_here} keybind and the optional death auto-record.
 */
@ModuleInfo(
        id = "waypoints",
        displayName = "Waypoints",
        aliases = {"WaypointESP", "Beacons"},
        category = ModuleCategory.VISUALS,
        subcategory = ModuleSubcategory.ESP,
        description = "module.waypoints.description")
public final class Waypoints extends Module {
    private static final String ACTION_ADD_HERE = "add_here";
    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";
    private static final double BEAM_MAX_DISTANCE = 512.0;
    private static final double BEAM_HEIGHT = 320.0;

    private final Minecraft mc = Minecraft.getInstance();
    private final BooleanValue beam = bool("waypointsBeam", "beam", true);
    private final BooleanValue box = bool("waypointsBox", "box", true);
    private final BooleanValue labels = bool("waypointsLabels", "labels", true);
    private final BooleanValue distanceLabel = visibleWhen(bool("waypointsDistance", "show_distance", true), labels::get);
    private final BooleanValue convertDimensions = bool("waypointsConvertDimensions", "convert_dimensions", true);
    private final BooleanValue recordDeath = bool("waypointsRecordDeath", "record_death", false);
    private final NumberValue<Double> maxDistance = num("waypointsMaxDistance", "max_distance", 5000.0, 50.0, 100000.0);

    private boolean wasDead;

    public Waypoints() {
        action(ACTION_ADD_HERE, "NONE");
    }

    @Override
    public void onEnable() {
        wasDead = false;
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            wasDead = false;
            return;
        }

        boolean dead = player.isDeadOrDying();
        if (dead && !wasDead && recordDeath.get()) {
            WaypointStore store = WaypointStore.get();
            String name = store.uniqueName(mc, I18n.get("waypoints.death_name"));
            store.add(mc, new WaypointStore.Waypoint(name, player.getX(), player.getY(), player.getZ(),
                    mc.level.dimension().identifier().toString(), 0xFFFF5555, System.currentTimeMillis()));
        }
        wasDead = dead;

        if (isActionPressedOnce(ACTION_ADD_HERE) && ClientScreen.current() == null && !dead) {
            WaypointStore store = WaypointStore.get();
            String name = store.uniqueName(mc, I18n.get("waypoints.default_name"));
            boolean saved = store.add(mc, new WaypointStore.Waypoint(name, player.getX(), player.getY(), player.getZ(),
                    mc.level.dimension().identifier().toString(), WaypointStore.DEFAULT_COLOR,
                    System.currentTimeMillis()));
            if (saved) Notifier.success(I18n.get("notification.waypoints.added", name));
        }
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.END_MAIN;
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!isEnabled() || mc.player == null || mc.level == null || !(beam.get() || box.get())) return;

        Vec3 eyes = mc.player.getEyePosition(tickDelta);
        double maxSq = maxDistance.get() * maxDistance.get();
        double beamSq = BEAM_MAX_DISTANCE * BEAM_MAX_DISTANCE;
        String dimension = mc.level.dimension().identifier().toString();

        for (WaypointStore.Waypoint wp : WaypointStore.get().list(mc)) {
            Vec3 pos = resolve(wp, dimension);
            if (pos == null) continue;
            double distSq = eyes.distanceToSqr(pos);
            if (distSq > maxSq || distSq > beamSq) continue;

            int argb = opaque(wp.color());
            int fill = (argb & 0x00FFFFFF) | 0x44000000;
            if (box.get()) {
                AABB bounds = new AABB(pos.x - 0.5, pos.y, pos.z - 0.5, pos.x + 0.5, pos.y + 1.0, pos.z + 0.5);
                renderer.filledBox(bounds, fill, Renderer3D.DepthMode.NONE);
                renderer.outlineBox(bounds, argb, 2.0f, Renderer3D.DepthMode.NONE);
            }
            if (beam.get()) {
                AABB column = new AABB(pos.x - 0.1, pos.y, pos.z - 0.1, pos.x + 0.1, pos.y + BEAM_HEIGHT, pos.z + 0.1);
                renderer.filledBox(column, (argb & 0x00FFFFFF) | 0x66000000);
                renderer.outlineBox(column, (argb & 0x00FFFFFF) | 0xAA000000, 1.5f);
            }
        }
    }

    @Override
    public void onRenderHudEngine(Renderer2D renderer, TextRenderer textRenderer, GuiGraphicsExtractor ctx, float tickDelta) {
        if (!isEnabled() || mc.player == null || mc.level == null || !labels.get()) return;
        List<WaypointStore.Waypoint> all = WaypointStore.get().list(mc);
        if (all.isEmpty()) return;

        Vec3 eyes = mc.player.getEyePosition(tickDelta);
        double maxSq = maxDistance.get() * maxDistance.get();
        String dimension = mc.level.dimension().identifier().toString();
        double[] screen = new double[3];

        TextRenderer font = VanillaTextRenderer.INSTANCE;
        boolean started = false;
        if (!font.isBuilding()) {
            font.begin(ScreenSpaceOverlay2D.VANILLA_TEXT_SCALE, true, false);
            started = true;
        }
        try {
            List<ScreenSpaceOverlay2D.LabelEntry> entries = new ArrayList<>();
            for (WaypointStore.Waypoint wp : all) {
                Vec3 pos = resolve(wp, dimension);
                if (pos == null) continue;
                double distSq = eyes.distanceToSqr(pos);
                if (distSq > maxSq) continue;
                if (!ScreenProjection.project(pos.x, pos.y + 1.5, pos.z, screen)) continue;

                String text = distanceLabel.get()
                        ? String.format(Locale.ROOT, "%s [%dm]", wp.name(), (int) Math.round(Math.sqrt(distSq)))
                        : wp.name();
                double x = screen[0] - font.getWidth(text, false) / 2.0;
                entries.add(ScreenSpaceOverlay2D.labelAt(text, x, screen[1], opaque(wp.color())));
            }
            if (!entries.isEmpty()) ScreenSpaceOverlay2D.renderLabels(font, entries, false);
        } finally {
            if (started) font.end();
        }
        Renderer2D.flushBatch(Renderer2D.FlushReason.EXPLICIT);
    }

    /** Position of the waypoint in the player's current dimension, or null when it is not shown here. */
    private Vec3 resolve(WaypointStore.Waypoint wp, String currentDimension) {
        double x = wp.x();
        double z = wp.z();
        if (!wp.dimension().equals(currentDimension)) {
            if (!convertDimensions.get()) return null;
            if (wp.dimension().equals(OVERWORLD) && currentDimension.equals(NETHER)) {
                x /= 8.0;
                z /= 8.0;
            } else if (wp.dimension().equals(NETHER) && currentDimension.equals(OVERWORLD)) {
                x *= 8.0;
                z *= 8.0;
            } else {
                return null;
            }
        }
        return new Vec3(x, wp.y(), z);
    }

    private static int opaque(int argb) {
        return argb | 0xFF000000;
    }
}

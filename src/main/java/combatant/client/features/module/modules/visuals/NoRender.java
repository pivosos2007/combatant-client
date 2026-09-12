/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.visuals;

import net.minecraft.client.Camera;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import combatant.client.config.values.BooleanMapValue;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.SetValue;
import combatant.client.compat.sodiumextra.SodiumExtraNoRenderCompat;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.gui.clickgui.settings.TextListSetting;

import java.util.Optional;

//todo Description
@ModuleInfo(
        id = "norender",
        displayName = "NoRender",
        category = ModuleCategory.VISUALS
)
public class NoRender extends Module {

    private static final String SETTING_TOGGLES = "toggles";
    private static final String SETTING_WORLD_TOGGLES = "world_toggles";
    private static final String SETTING_ENTITY_TOGGLES = "entity_toggles";
    private static final String SETTING_PARTICLE_TOGGLES = "particle_toggles";
    private static final String SETTING_PARTICLE_CLASSES = "particle_classes";
    private static final String SETTING_VIEW_OBSTRUCTION_FADE_LIVING_ENTITIES = "view_obstruction_fade_living_entities";
    private static final String SETTING_VIEW_OBSTRUCTION_FADE_STRENGTH = "view_obstruction_fade_strength";
    private static final String SETTING_FIRE_ONLY_IF_RESISTANT = "fire_only_if_resistant";
    private static final String SETTING_HIDE_SCOREBOARD = "hide_scoreboard";
    private static final String KEY_VIEW_OBSTRUCTION_FADE = "view_obstruction_fade";
    private static final String KEY_SCREEN_DARKENING = "screen_darkening";
    private static final String KEY_TOAST_HINTS = "toast_hints";
    private static final double VIEW_OBSTRUCTION_FADE_DISTANCE = 0.82;
    private static final double VIEW_OBSTRUCTION_BOX_EXPAND_XZ = 0.18;
    private static final double VIEW_OBSTRUCTION_BOX_EXPAND_Y = 0.28;
    private static final float VIEW_OBSTRUCTION_MIN_ALPHA_WEAK = 0.72f;
    private static final float VIEW_OBSTRUCTION_MIN_ALPHA_STRONG = 0.22f;
    private final BooleanMapValue toggles = group(
            "norender_toggles",
            SETTING_TOGGLES,
            new java.util.LinkedHashMap<>() {{
                put("totem_overlay", false);
                put("eat_particles", false);
                put("hit_particles", false);
                put("sweep_particles", false);
                put("fire_overlay", true);
                put("camera_shake", true);
                put("view_bob", true);
                put("bad_effects", false);
                put("block_overlay", false);
                put(KEY_SCREEN_DARKENING, false);
                put(KEY_TOAST_HINTS, false);
                put(KEY_VIEW_OBSTRUCTION_FADE, false);
            }}
    );
    private final BooleanMapValue worldToggles = group(
            "norender_world_toggles",
            SETTING_WORLD_TOGGLES,
            new java.util.LinkedHashMap<>() {{
                put("sky", false);
                put("sun", false);
                put("moon", false);
                put("stars", false);
                put("weather", false);
            }}
    );
    private final BooleanMapValue entityToggles = group(
            "norender_entity_toggles",
            SETTING_ENTITY_TOGGLES,
            new java.util.LinkedHashMap<>() {{
                put("item_frames", false);
                put("armor_stands", false);
                put("paintings", false);
                put("moving_pistons", false);
                put("beacon_beams", false);
                put("enchanting_table_book", false);
                put("item_frame_name_tags", false);
                put("player_name_tags", false);
            }}
    );
    private final BooleanMapValue particleToggles = group(
            "norender_particle_toggles",
            SETTING_PARTICLE_TOGGLES,
            new java.util.LinkedHashMap<>() {{
                put("all_particles", false);
                put("rain_splash_particles", false);
                put("block_break_particles", false);
                put("block_breaking_particles", false);
            }}
    );
    private final SetValue particleClasses = textList(
            "norender_particle_classes",
            SETTING_PARTICLE_CLASSES,
            TextListSetting.PickerMode.PARTICLES
    );

    private final BooleanValue viewObstructionFadeLivingEntities =
            visibleWhen(bool("norender_view_obstruction_fade_living_entities", SETTING_VIEW_OBSTRUCTION_FADE_LIVING_ENTITIES, true),
                    this::viewObstructionFadeEnabled);
    private final NumberValue<Integer> viewObstructionFadeStrength =
            visibleWhen(num("norender_view_obstruction_fade_strength", SETTING_VIEW_OBSTRUCTION_FADE_STRENGTH, 55, 0, 100),
                    this::viewObstructionFadeEnabled);
    private final BooleanValue fireOnlyIfResistant =
            bool("fire_only_if_resistant", SETTING_FIRE_ONLY_IF_RESISTANT, false);
    private final BooleanValue hideScoreboard =
            bool("hide_scoreboard", SETTING_HIDE_SCOREBOARD, false);

    public static boolean shouldFadeSoftBlock(BlockState state) {
        return false;
    }

    private static double combatant$squaredDistanceToBox(Vec3 point, AABB box) {
        double nearestX = Mth.clamp(point.x, box.minX, box.maxX);
        double nearestY = Mth.clamp(point.y, box.minY, box.maxY);
        double nearestZ = Mth.clamp(point.z, box.minZ, box.maxZ);
        double dx = point.x - nearestX;
        double dy = point.y - nearestY;
        double dz = point.z - nearestZ;
        return dx * dx + dy * dy + dz * dz;
    }

    public boolean off(String key) {
        if (!isEnabled()) return false;

        return toggles.get(key);
    }

    public boolean offWorld(String key) {
        return isEnabled() && worldToggles.get(key);
    }

    public boolean offEntity(String key) {
        return isEnabled() && entityToggles.get(key);
    }

    public boolean offParticle(String key) {
        return isEnabled() && particleToggles.get(key);
    }

    public boolean shouldHideParticle(Particle particle) {
        if (!isEnabled() || particle == null) return false;
        if (particleToggles.get("all_particles")) return true;

        java.util.Set<String> selected = particleClasses.get();
        if (selected == null || selected.isEmpty()) return false;

        Class<?> current = particle.getClass();
        while (current != null && Particle.class.isAssignableFrom(current)) {
            if (selected.contains(current.getName())) return true;
            if (current == Particle.class) break;
            current = current.getSuperclass();
        }
        return false;
    }

    private void syncSodiumExtraNoRenderOwnership() {
        SodiumExtraNoRenderCompat.syncMigratedOptions(
                worldToggles.getAll(),
                entityToggles.getAll(),
                particleToggles.getAll()
        );
    }

    @Override
    public void onEnable() {
        syncSodiumExtraNoRenderOwnership();
    }

    @Override
    public void onTick() {
        // Combatant is authoritative while NoRender is enabled. Keep overlapping
        // Sodium Extra render switches mirrored from the module tick so its hooks
        // cannot override Combatant in the opposite direction.
        syncSodiumExtraNoRenderOwnership();
    }

    @Override
    public void onDisable() {
        SodiumExtraNoRenderCompat.releaseMigratedOptions();
    }

    public boolean fireOnlyWhenResistant() {
        return fireOnlyIfResistant.get();
    }

    public boolean hideScoreboard() {
        return isEnabled() && hideScoreboard.get();
    }

    public boolean hideScoreboardRaw() {
        return hideScoreboard.get();
    }

    public void setHideScoreboardRaw(boolean value) {
        if (hideScoreboard.get() == value) {
            return;
        }
        hideScoreboard.set(value);
        saveConfig();
    }

    public boolean viewObstructionFadeEnabled() {
        return isEnabled() && toggles.get(KEY_VIEW_OBSTRUCTION_FADE);
    }

    public boolean screenDarkeningDisabled() {
        return isEnabled() && toggles.get(KEY_SCREEN_DARKENING);
    }

    public boolean hideToastHints() {
        return isEnabled() && toggles.get(KEY_TOAST_HINTS);
    }

    public boolean fadeAdditionalLivingEntities() {
        return viewObstructionFadeEnabled() && viewObstructionFadeLivingEntities.get();
    }

    public boolean fadeSoftBlocks() {
        return false;
    }

    public boolean shouldFadeEntity(Entity entity) {
        Minecraft client = Minecraft.getInstance();
        if (!viewObstructionFadeEnabled() || !(entity instanceof LivingEntity living) || client.player == null) {
            return false;
        }
        if (entity == client.player || living.isInvisible()) {
            return false;
        }
        if (entity instanceof Player) {
            return true;
        }
        return fadeAdditionalLivingEntities();
    }

    public float getEntityFadeAlpha(Entity entity) {
        Minecraft client = Minecraft.getInstance();
        if (client.gameRenderer == null || client.gameRenderer.mainCamera() == null) {
            return 1.0f;
        }
        Camera camera = client.gameRenderer.mainCamera();
        Vec3 cameraPos = camera.position();
        AABB box = entity.getBoundingBox().inflate(VIEW_OBSTRUCTION_BOX_EXPAND_XZ, VIEW_OBSTRUCTION_BOX_EXPAND_Y, VIEW_OBSTRUCTION_BOX_EXPAND_XZ);
        double obstructionDistance = Math.sqrt(combatant$squaredDistanceToBox(cameraPos, box));

        Vec3 rayEnd = cameraPos.add(Vec3.directionFromRotation(camera.xRot(), camera.yRot()).scale(VIEW_OBSTRUCTION_FADE_DISTANCE));
        Optional<Vec3> hit = box.clip(cameraPos, rayEnd);
        if (hit.isPresent()) {
            obstructionDistance = Math.min(obstructionDistance, cameraPos.distanceTo(hit.get()));
        }

        if (box.contains(cameraPos)) {
            obstructionDistance = 0.0;
        }

        if (obstructionDistance >= VIEW_OBSTRUCTION_FADE_DISTANCE) {
            return 1.0f;
        }
        float t = Mth.clamp((float) (obstructionDistance / VIEW_OBSTRUCTION_FADE_DISTANCE), 0.0f, 1.0f);
        t = t * t * (3.0f - 2.0f * t);
        return Mth.lerp(t, getViewObstructionMinAlpha(), 1.0f);
    }

    private float getViewObstructionMinAlpha() {
        float strength = Mth.clamp(viewObstructionFadeStrength.get(), 0, 100) / 100.0f;
        return Mth.lerp(strength, VIEW_OBSTRUCTION_MIN_ALPHA_WEAK, VIEW_OBSTRUCTION_MIN_ALPHA_STRONG);
    }
}

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import combatant.client.config.values.*;
import combatant.client.features.module.*;
import combatant.client.features.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import combatant.client.config.values.*;
import combatant.client.features.module.*;
import combatant.client.features.module.modules.visuals.ESP;
import combatant.client.features.relations.CategoryRules;
import combatant.client.features.relations.CategoryType;
import combatant.client.features.relations.EntityFilters;
import combatant.client.mixininterface.IEntity;
import combatant.client.render.engine.RenderState;
import combatant.client.render.engine.depth.PreTranslucentDepth;
import combatant.client.render.engine.pipeline.CombatantRenderPipelines;
import combatant.client.render.engine.renderer.MeshRenderer;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.render.engine.uniform.MeshBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


//todo Description
@ModuleInfo(
        id = "hitbox",
        displayName = "Hitbox",
        category = ModuleCategory.COMBAT
)
public class Hitbox extends Module {

    private static final String ACTION_SUPPRESS_HITBOX = "suppress_hitbox";
    private static final long HIT_FLASH_DURATION_MS = 2000;
    private static final MeshBuilder HITBOX_TRIS = new MeshBuilder(CombatantRenderPipelines.WORLD_COLORED_LIQUID_IGNORE);
    private static final MeshBuilder HITBOX_LINES = new MeshBuilder(CombatantRenderPipelines.WORLD_COLORED_LINES_DEPTH);
    private final Minecraft mc = Minecraft.getInstance();
    private final NumberValue<Float> padding =
            num(
                    "hitboxPadding",
                    "padding",
                    2.0f,
                    0.2f,
                    3.0f
            );
    private final BooleanValue softDisable =
            bool(
                    "hitboxSoftDisable",
                    "soft_disable_hold",
                    true
            );
    private final BooleanValue renderEnabled =
            bool(
                    "hitboxRenderEnabled",
                    "render_hitboxes",
                    true
            );
    private final NumberValue<Integer> renderDistance =
            visibleWhen(num(
                    "hitboxRenderDistance",
                    "render_distance",
                    48,
                    8,
                    160
            ), renderEnabled::get);
    private final BooleanMapValue ignoreGroups =
            visibleWhen(group(
                    "hitboxIgnoreGroups",
                    "ignore_categories",
                    ignoreCategoryDefaults()
            ), renderEnabled::get);
    private final RGBColorValue hitboxColor =
            visibleWhen(colorNoAlpha(
                    "hitboxColor",
                    "hitbox_color",
                    "#78C8FF"
            ), renderEnabled::get);
    private final Map<Integer, Long> hitFlashMs = new ConcurrentHashMap<>();
    private final List<AABB> precomputedBoxes = new ArrayList<>();
    private final List<Entity> precomputedEntities = new ArrayList<>();

    {
        setDefaultBind("G"); // toggle padding/module
        addAction(ACTION_SUPPRESS_HITBOX, "MOUSE3", BindMode.HOLD);
    }

    private static Map<String, Boolean> ignoreCategoryDefaults() {
        return Map.of(
                "friends", false,
                "staff", false,
                "enemies", false,
                "entities", false
        );
    }

    // ======================================================
    //                      STATE
    // ======================================================

    private static PoseStack buildTransformStack() {
        Matrix4f mv = new Matrix4f(RenderSystem.getModelViewMatrixCopy());
        Quaternionf camRot = new Quaternionf(RenderState.cameraRotation).conjugate();
        Matrix4f rotation = new Matrix4f().rotation(camRot);
        Matrix4f transform = mv.invert().mul(rotation);
        PoseStack stack = new PoseStack();
        stack.last().pose().set(transform);
        return stack;
    }

    private static void addFilledBox(MeshBuilder mesh, AABB box, int r, int g, int b, int a) {
        double minX = box.minX;
        double minY = box.minY;
        double minZ = box.minZ;
        double maxX = box.maxX;
        double maxY = box.maxY;
        double maxZ = box.maxZ;

        quad(mesh, minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a);
        quad(mesh, minX, maxY, minZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, r, g, b, a);
        quad(mesh, minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a);
        quad(mesh, minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ, maxX, minY, minZ, r, g, b, a);
        quad(mesh, maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, maxX, minY, maxZ, r, g, b, a);
        quad(mesh, minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ, r, g, b, a);
    }

    private static void addOutlineBox(MeshBuilder mesh, AABB box, int r, int g, int b, int a) {
        double minX = box.minX;
        double minY = box.minY;
        double minZ = box.minZ;
        double maxX = box.maxX;
        double maxY = box.maxY;
        double maxZ = box.maxZ;

        line(mesh, minX, minY, minZ, maxX, minY, minZ, r, g, b, a);
        line(mesh, maxX, minY, minZ, maxX, minY, maxZ, r, g, b, a);
        line(mesh, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a);
        line(mesh, minX, minY, maxZ, minX, minY, minZ, r, g, b, a);

        line(mesh, minX, maxY, minZ, maxX, maxY, minZ, r, g, b, a);
        line(mesh, maxX, maxY, minZ, maxX, maxY, maxZ, r, g, b, a);
        line(mesh, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a);
        line(mesh, minX, maxY, maxZ, minX, maxY, minZ, r, g, b, a);

        line(mesh, minX, minY, minZ, minX, maxY, minZ, r, g, b, a);
        line(mesh, maxX, minY, minZ, maxX, maxY, minZ, r, g, b, a);
        line(mesh, maxX, minY, maxZ, maxX, maxY, maxZ, r, g, b, a);
        line(mesh, minX, minY, maxZ, minX, maxY, maxZ, r, g, b, a);
    }

    private static void quad(MeshBuilder mesh,
                             double x1, double y1, double z1,
                             double x2, double y2, double z2,
                             double x3, double y3, double z3,
                             double x4, double y4, double z4,
                             int r, int g, int b, int a) {
        mesh.ensureQuadCapacity();
        int i1 = mesh.vec3(x1, y1, z1).color(r, g, b, a).next();
        int i2 = mesh.vec3(x2, y2, z2).color(r, g, b, a).next();
        int i3 = mesh.vec3(x3, y3, z3).color(r, g, b, a).next();
        int i4 = mesh.vec3(x4, y4, z4).color(r, g, b, a).next();
        mesh.quad(i1, i2, i3, i4);
    }

    private static void line(MeshBuilder mesh,
                             double x1, double y1, double z1,
                             double x2, double y2, double z2,
                             int r, int g, int b, int a) {
        mesh.ensureLineCapacity();
        int i1 = mesh.vec3(x1, y1, z1).color(r, g, b, a).next();
        int i2 = mesh.vec3(x2, y2, z2).color(r, g, b, a).next();
        mesh.line(i1, i2);
    }

    // ======================================================
    //                  FILTERING
    // ======================================================

    public static double boxDistanceSq(AABB box, Vec3 p) {
        double dx = 0.0;
        if (p.x < box.minX) dx = box.minX - p.x;
        else if (p.x > box.maxX) dx = p.x - box.maxX;

        double dy = 0.0;
        if (p.y < box.minY) dy = box.minY - p.y;
        else if (p.y > box.maxY) dy = p.y - box.maxY;

        double dz = 0.0;
        if (p.z < box.minZ) dz = box.minZ - p.z;
        else if (p.z > box.maxZ) dz = p.z - box.maxZ;

        return dx * dx + dy * dy + dz * dz;
    }

    public boolean isSoftDisabledNow() {
        return softDisable.get() && isActionHeld(ACTION_SUPPRESS_HITBOX);
    }

    // ======================================================
    //                     RENDER
    // ======================================================

    public boolean isActiveForHitbox() {
        return isEnabled() && !isSoftDisabledNow();
    }

    public double getPadding() {
        return padding.get();
    }

    public boolean shouldRender() {
        return isActiveForHitbox() && renderEnabled.get();
    }

    public int getRenderDistance() {
        return renderDistance.get();
    }

    public boolean isAllowed(Entity e) {
        if (e instanceof Player p) {
            String name = p.getGameProfile().name();
            if (name != null) {
                CategoryType type = CategoryRules.determine(name);
                if (type == CategoryType.BEDWARS_SELF) return false;
                if (ignoreGroups.get("friends") && type == CategoryType.FRIEND) return false;
                if (ignoreGroups.get("staff") && type == CategoryType.STAFF) return false;
                if (ignoreGroups.get("enemies")
                        && (type == CategoryType.ENEMY || type == CategoryType.BEDWARS_ENEMY)) return false;
            }
        }

        var id = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
        return id == null || !ignoreGroups.get("entities") || !EntityFilters.get().isIgnoredEntity(id.toString());
    }

    /**
     * True when the entity should be skipped entirely (raycast + render).
     */
    public boolean shouldIgnore(Entity e) {
        return !isAllowed(e);
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.END_MAIN;
    }

    @Override
    public void onRenderWorld(PoseStack matrices, net.minecraft.client.renderer.SubmitNodeCollector consumers, float tickDelta) {
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!shouldRender() || mc.level == null || mc.player == null) return;

        Vec3 cam = mc.gameRenderer.mainCamera().position();
        precomputeBoxes(mc, tickDelta, cam);

        if (precomputedBoxes.isEmpty()) return;

        MeshBuilder tris = HITBOX_TRIS;
        MeshBuilder lines = HITBOX_LINES;
        lines.begin();
        tris.begin();

        for (int i = 0; i < precomputedBoxes.size(); i++) {
            AABB box = precomputedBoxes.get(i);
            Entity e = precomputedEntities.get(i);

            float flash = getHitFlashFactor(e);
            float[] rgba = resolveColor(e);

            float r = rgba[0], g = rgba[1], b = rgba[2];
            float fillA = Math.min(1.0f, (0.18f + 0.12f * flash) * 1.17f); //Р°Р»СЊС„Р° С‚СѓС‚
            float lineA = Math.min(1.0f, 0.65f + 0.25f * flash);

            r = r * (1 - flash) + flash;
            g = g * (1 - flash) + 0.5f * flash;
            b = b * (1 - flash) + 0.0f * flash;

            int ri = (int) (r * 255f);
            int gi = (int) (g * 255f);
            int bi = (int) (b * 255f);
            int fa = (int) (fillA * 255f);
            int la = (int) (lineA * 255f);

            addOutlineBox(lines, box, ri, gi, bi, la);
            addFilledBox(tris, box, ri, gi, bi, fa);
        }

        tris.end();
        lines.end();

        PoseStack transform = buildTransformStack();
        var fb = mc.gameRenderer.mainRenderTarget();
        var depthView = PreTranslucentDepth.getDepthView();
        var colorView = fb.getColorTextureView();
        if (depthView == null) {
            return;
        }

        if (tris.getIndicesCount() > 0) {
            MeshRenderer.begin()
                    .attachments(colorView, depthView)
                    .pipeline(CombatantRenderPipelines.WORLD_COLORED_LIQUID_IGNORE)
                    .mesh(tris, transform)
                    .end();
        }

        if (lines.getIndicesCount() > 0) {
            MeshRenderer.begin()
                    .attachments(colorView, depthView)
                    .pipeline(CombatantRenderPipelines.WORLD_COLORED_LINES_DEPTH)
                    .mesh(lines, transform)
                    .end();
        }
    }

    private void precomputeBoxes(Minecraft mc, float tickDelta, Vec3 cam) {
        precomputedBoxes.clear();
        precomputedEntities.clear();

        double maxDist = getRenderDistance();
        double maxDistSq = maxDist * maxDist;

        AABB search = new AABB(
                cam.x - maxDist, cam.y - maxDist, cam.z - maxDist,
                cam.x + maxDist, cam.y + maxDist, cam.z + maxDist
        );

        List<Entity> list = mc.level.getEntities(mc.player, search,
                e -> e instanceof LivingEntity && e.isAlive() && isAllowed(e) && !isRenderedByEspReplacement(e));

        for (Entity e : list) {
            Vec3 pos = obtainEntityLerpedPos(e, tickDelta);

            double dx = pos.x - cam.x;
            double dy = pos.y - cam.y;
            double dz = pos.z - cam.z;
            if (dx * dx + dy * dy + dz * dz > maxDistSq) continue;

            AABB box = e.getBoundingBox()
                    .move(pos.subtract(e.position()))
                    .inflate(getPadding());

            precomputedBoxes.add(box);
            precomputedEntities.add(e);
        }
    }

    private Vec3 obtainEntityLerpedPos(Entity e, float f) {
        try {
            return e.getPosition(f);
        } catch (NoSuchMethodError ex) {
            if (e instanceof IEntity access) {
                Vec3 last = access.get$InstantRenderPos();
                return last.lerp(e.position(), f);
            }
            return e.position();
        }
    }

    private boolean isRenderedByEspReplacement(Entity e) {
        ESP esp = Modules.get(ESP.class);
        return esp != null && esp.shouldRenderHitboxReplacement(e);
    }

    private float[] resolveColor(Entity e) {
        return argbToRgba(hitboxColor.getArgb());
    }

    // ======================================================
    //                 HIT FLASH SUPPORT
    // ======================================================

    private float[] argbToRgba(int argb) {
        float a = ((argb >> 24) & 255) / 255f;
        float r = ((argb >> 16) & 255) / 255f;
        float g = ((argb >> 8) & 255) / 255f;
        float b = (argb & 255) / 255f;
        return new float[]{r, g, b, a};
    }

    public void markHit(Entity e) {
        if (e != null) hitFlashMs.put(e.getId(), System.currentTimeMillis());

    }

    // ======================================================
    //             Utility for other components
    // ======================================================

    private float getHitFlashFactor(Entity e) {
        Long t0 = hitFlashMs.get(e.getId());
        if (t0 == null) return 0f;

        long dt = System.currentTimeMillis() - t0;
        if (dt >= HIT_FLASH_DURATION_MS) {
            hitFlashMs.remove(e.getId());
            return 0f;
        }

        float x = 1f - (float) dt / HIT_FLASH_DURATION_MS;
        return x * x * (3f - 2f * x);
    }

    /**
     * Render/visual replacement predicate: active Hitbox + the same category/entity filters,
     * but without the interaction distance check used by raycast expansion.
     */
    public boolean isReplacementCandidate(Entity e) {
        if (!(e instanceof LivingEntity living) || !living.isAlive()) {
            return false;
        }
        return isActiveForHitbox() && isAllowed(e);
    }

    public boolean shouldExpandFor(Entity e) {
        if (!(e instanceof LivingEntity living) || !living.isAlive()) {
            return false;
        }

        return isEnabled() && !isSoftDisabledNow() && isAllowed(e) && withinMaxDistance(e);
    }

    private boolean withinMaxDistance(Entity e) {
        if (mc.player == null) return true;
        Vec3 eye = mc.player.getEyePosition(1.0F);
        return boxDistanceSq(e.getBoundingBox(), eye) <= 36.0;
    }

    public void updateForLogic(float tickDelta) {
        if (mc.player == null || mc.level == null) return;

        Vec3 eye = mc.player.getEyePosition(tickDelta);
        precomputeBoxes(mc, tickDelta, eye);
    }

    public int getPrecomputedCount() {
        return precomputedBoxes.size();
    }

    public Entity getPrecomputedEntity(int i) {
        return precomputedEntities.get(i);
    }

    public AABB getPrecomputedBox(int i) {
        return precomputedBoxes.get(i);
    }
}

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.visuals;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import combatant.client.config.common.CommonSettingSchemas;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.RGBAColorValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.WorldPhase;
import combatant.client.features.relations.CategoryService;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.render.engine.animation.AnimatedRenderColors;
import combatant.client.render.engine.pipeline.CombatantRenderPipelines;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.render.engine.uniform.MeshBuilder;
import combatant.client.render.helpers.HeadAnchor;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;

//todo Description
@ModuleInfo(
        id = "tazikhat",
        displayName = "TazikHat",
        category = ModuleCategory.VISUALS
)
public final class TazikHat extends Module {

    private static final int SEGMENTS = 56;
    private static final long DEATH_LIFE_MS = 3600L;
    private static final double RAINBOW_SPATIAL_SCALE = 12.0;

    private static final float BOWL_RIM_RADIUS = 0.42f;
    private static final float BOWL_TOP_RADIUS = 0.245f;
    private static final float BOWL_HEIGHT = 0.18f;
    private static final float BOWL_RIM_WIDTH = 0.052f;
    private final Minecraft mc = Minecraft.getInstance();
    private final Random random = new Random();
    private final EnumValue<RenderTarget> renderTarget =
            enumMode(
                    "render_target",
                    RenderTarget.BOTH,
                    RenderTarget.values()
            );
    private final BooleanValue useRelations =
            boolCommon(
                    "use_relations",
                    "use_relations",
                    CommonSettingSchemas.PLAYER_USE_RELATIONS,
                    true
            );
    private final RGBAColorValue primaryColor =
            visibleWhen(common(
                    color(
                            "primary_color",
                            "#CC17C765"
                    ),
                    CommonSettingSchemas.RENDER_PRIMARY_COLOR.commonI18nKey()
            ), () -> !useRelations.get());
    private final EnumValue<ColorMode> colorMode =
            enumCommon(
                    "color_mode",
                    "color_mode",
                    CommonSettingSchemas.RENDER_COLOR_MODE,
                    ColorMode.STATIC,
                    ColorMode.values()
            );
    private final RGBAColorValue secondaryColor =
            visibleWhen(common(
                    color(
                            "secondary_color",
                            "#AA079C46"
                    ),
                    CommonSettingSchemas.RENDER_SECONDARY_COLOR.commonI18nKey()
            ), this::usesSecondaryManualColor);
    private final NumberValue<Integer> colorSpeed =
            numCommon(
                    "color_speed",
                    "color_speed",
                    CommonSettingSchemas.RENDER_COLOR_SPEED,
                    18,
                    2,
                    54
            );
    private final NumberValue<Float> scale =
            numCommon(
                    "scale",
                    "scale",
                    CommonSettingSchemas.RENDER_SCALE,
                    1.15f,
                    0.45f,
                    2.0f
            );
    private final BooleanValue depthTest =
            boolCommon(
                    "depth_test",
                    "depth_test",
                    CommonSettingSchemas.RENDER_DEPTH_TEST,
                    true
            );
    private final RGBAColorValue rimColor =
            visibleWhen(color(
                    "rim_color",
                    "#EE52FFA0"
            ), () -> !useRelations.get());
    private final NumberValue<Float> helmetScale =
            num(
                    "helmet_scale",
                    1.16f,
                    1.0f,
                    1.45f
            );
    private final NumberValue<Float> headTopOffset =
            num(
                    "head_top_offset",
                    0.255f,
                    0.05f,
                    0.45f
            );
    private final NumberValue<Float> headForwardOffset =
            num(
                    "head_forward_offset",
                    0.0f,
                    -0.25f,
                    0.25f
            );
    private final NumberValue<Float> headRightOffset =
            num(
                    "head_right_offset",
                    0.0f,
                    -0.25f,
                    0.25f
            );
    private final NumberValue<Float> wobbleStrength =
            num(
                    "wobble_strength",
                    1.0f,
                    0.0f,
                    2.5f
            );
    private final BooleanValue deathFlight =
            bool(
                    "death_flight",
                    true
            );
    private final NumberValue<Float> deathFlightPower =
            visibleWhen(num(
                    "death_flight_power",
                    1.0f,
                    0.25f,
                    3.0f
            ), deathFlight::get);
    private final Map<Integer, Long> handledDeaths = new HashMap<>();
    private final Map<Integer, FlyingTazik> flying = new HashMap<>();

    private static void quadGradient(MeshBuilder mesh,
                                     Vec3 p1, int c1,
                                     Vec3 p2, int c2,
                                     Vec3 p3, int c3,
                                     Vec3 p4, int c4) {
        mesh.ensureQuadCapacity();

        putColorVertex(mesh, p1, c1);
        int i1 = mesh.next();

        putColorVertex(mesh, p2, c2);
        int i2 = mesh.next();

        putColorVertex(mesh, p3, c3);
        int i3 = mesh.next();

        putColorVertex(mesh, p4, c4);
        int i4 = mesh.next();

        mesh.quad(i1, i2, i3, i4);
    }

    private static void triangle(MeshBuilder mesh,
                                 Vec3 p1,
                                 Vec3 p2,
                                 Vec3 p3,
                                 int argb) {
        mesh.ensureTriCapacity();

        putColorVertex(mesh, p1, argb);
        int i1 = mesh.next();

        putColorVertex(mesh, p2, argb);
        int i2 = mesh.next();

        putColorVertex(mesh, p3, argb);
        int i3 = mesh.next();

        mesh.triangle(i1, i2, i3);
    }

    private static void lineGradient(MeshBuilder mesh,
                                     Vec3 p1,
                                     Vec3 p2,
                                     int c1,
                                     int c2) {
        mesh.ensureLineCapacity();

        putColorVertex(mesh, p1, c1);
        int i1 = mesh.next();

        putColorVertex(mesh, p2, c2);
        int i2 = mesh.next();

        mesh.line(i1, i2);
    }

    private static void putColorVertex(MeshBuilder mesh, Vec3 p, int argb) {
        mesh.vec3(p.x, p.y, p.z).color(red(argb), green(argb), blue(argb), alpha(argb));
    }

    private static int mix(int a, int b, float t) {
        float clamped = AnimationUtility.clamp01(t);

        int aa = alpha(a);
        int ar = red(a);
        int ag = green(a);
        int ab = blue(a);

        int ba = alpha(b);
        int br = red(b);
        int bg = green(b);
        int bb = blue(b);

        int ca = Math.round(AnimationUtility.lerp(aa, ba, clamped));
        int cr = Math.round(AnimationUtility.lerp(ar, br, clamped));
        int cg = Math.round(AnimationUtility.lerp(ag, bg, clamped));
        int cb = Math.round(AnimationUtility.lerp(ab, bb, clamped));

        return (ca << 24) | (cr << 16) | (cg << 8) | cb;
    }

    private static int multiplyRgb(int argb, float factor) {
        int a = alpha(argb);
        int r = Mth.clamp(Math.round(red(argb) * factor), 0, 255);
        int g = Mth.clamp(Math.round(green(argb) * factor), 0, 255);
        int b = Mth.clamp(Math.round(blue(argb) * factor), 0, 255);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int brighten(int argb, float factor) {
        return multiplyRgb(argb, factor);
    }

    private static int withAlpha(int argb, int alpha) {
        int a = Mth.clamp(alpha, 0, 255);
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    private static int red(int argb) {
        return (argb >>> 16) & 255;
    }

    private static int green(int argb) {
        return (argb >>> 8) & 255;
    }

    private static int blue(int argb) {
        return argb & 255;
    }

    private static int alpha(int argb) {
        return (argb >>> 24) & 255;
    }

    private static int alphaOr(int argb, int fallback) {
        int alpha = alpha(argb);
        return alpha <= 0 ? Mth.clamp(fallback, 0, 255) : alpha;
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.BEFORE_TRANSLUCENT;
    }

    @Override
    public void onTick() {
        if (!isEnabled() || mc.level == null || mc.player == null) {
            return;
        }

        long now = System.currentTimeMillis();

        for (Player player : mc.level.players()) {
            if (player == null || player.isRemoved()) continue;

            int id = player.getId();

            if (player.isAlive() && player.getHealth() > 0.0f) {
                handledDeaths.remove(id);
                continue;
            }

            if (!deathFlight.get()) continue;
            if (handledDeaths.containsKey(id)) continue;

            handledDeaths.put(id, now);
            spawnFlyingTazik(player);
        }

        handledDeaths.entrySet().removeIf(entry -> now - entry.getValue() > 8000L);

        Iterator<Map.Entry<Integer, FlyingTazik>> iterator = flying.entrySet().iterator();
        while (iterator.hasNext()) {
            FlyingTazik tazik = iterator.next().getValue();
            if (tazik.update()) {
                iterator.remove();
            }
        }
    }

    @Override
    public void onDisable() {
        handledDeaths.clear();
        flying.clear();
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!isEnabled() || mc.level == null || mc.player == null) return;

        MeshBuilder tris = renderer.batch(
                depthTest.get()
                        ? CombatantRenderPipelines.WORLD_COLORED_DEPTH
                        : CombatantRenderPipelines.WORLD_COLORED,
                depthTest.get()
                        ? Renderer3D.DepthMode.PRE_DEPTH
                        : Renderer3D.DepthMode.NONE
        );

        MeshBuilder lines = renderer.batch(
                depthTest.get()
                        ? CombatantRenderPipelines.WORLD_COLORED_LINES_DEPTH
                        : CombatantRenderPipelines.WORLD_COLORED_LINES,
                depthTest.get()
                        ? Renderer3D.DepthMode.PRE_DEPTH
                        : Renderer3D.DepthMode.NONE
        );

        if (tris == null || lines == null) return;

        RenderBuffers buffers = new RenderBuffers(tris, lines);

        if (shouldRenderSelf() && !mc.options.getCameraType().isFirstPerson()) {
            renderAttached(buffers, mc.player, tickDelta);
        }

        if (shouldRenderPlayers()) {
            for (Player player : mc.level.players()) {
                if (player == null || player == mc.player || player.isRemoved()) continue;
                if (!player.isAlive() || player.getHealth() <= 0.0f) continue;

                renderAttached(buffers, player, tickDelta);
            }
        }

        renderFlying(buffers, tickDelta);
    }

    private boolean shouldRenderSelf() {
        return renderTarget.get() == RenderTarget.SELF || renderTarget.get() == RenderTarget.BOTH;
    }

    private boolean shouldRenderPlayers() {
        return renderTarget.get() == RenderTarget.PLAYERS || renderTarget.get() == RenderTarget.BOTH;
    }

    private boolean usesSecondaryManualColor() {
        if (useRelations.get()) return false;
        return AnimatedRenderColors.usesSecondary(animatedColorMode());
    }

    private void renderAttached(RenderBuffers buffers, Player player, float tickDelta) {
        boolean helmet = hasHelmet(player);
        float size = scale.get() * (helmet ? helmetScale.get() : 1.0f);

        HeadAnchor anchor = HeadAnchor.of(
                player,
                tickDelta,
                headTopOffset.get(),
                headForwardOffset.get(),
                headRightOffset.get()
        );

        Palette palette = resolvePalette(player);
        Wobble wobble = resolveWobble(player, tickDelta);

        buildTazik(
                buffers,
                anchor,
                size,
                0.0f,
                wobble.pitchDeg,
                wobble.rollDeg,
                palette,
                1.0f,
                player.getId()
        );
    }

    private void renderFlying(RenderBuffers buffers, float tickDelta) {
        long now = System.currentTimeMillis();

        for (FlyingTazik tazik : flying.values()) {
            float life = (now - tazik.createdAtMs) / (float) DEATH_LIFE_MS;
            float fade = 1.0f - AnimationUtility.smoothstep(Math.max(0.0f, life - 0.65f) / 0.35f);

            Vec3 pos = new Vec3(
                    AnimationUtility.lerp(tazik.prevPos.x, tazik.pos.x, tickDelta),
                    AnimationUtility.lerp(tazik.prevPos.y, tazik.pos.y, tickDelta),
                    AnimationUtility.lerp(tazik.prevPos.z, tazik.pos.z, tickDelta)
            );

            buildFlyingTazik(
                    buffers,
                    pos,
                    tazik.scale,
                    tazik.yaw,
                    tazik.pitch,
                    tazik.roll,
                    tazik.palette,
                    fade,
                    tazik.seed
            );
        }
    }

    private Wobble resolveWobble(Player player, float tickDelta) {
        float hurt = Math.max(0.0f, player.hurtTime - tickDelta);
        float hit = AnimationUtility.easeOutBack(AnimationUtility.clamp01(hurt / 10.0f), 1.25f);

        float strength = wobbleStrength.get();
        float time = (player.tickCount + tickDelta) * 0.35f + player.getId() * 0.17f;

        float hitAmp = hit * 5.0f * strength;
        float idleAmp = 1.3f * strength;

        float pitch = Mth.sin(time * 3.7f) * hitAmp
                + Mth.sin(time * 1.2f) * idleAmp;

        float roll = Mth.cos(time * 3.1f) * hitAmp
                + Mth.cos(time * 0.9f) * idleAmp;

        return new Wobble(pitch, roll);
    }

    private void buildTazik(RenderBuffers buffers,
                            HeadAnchor anchor,
                            float size,
                            float yawDeg,
                            float pitchDeg,
                            float rollDeg,
                            Palette palette,
                            float alphaMul,
                            int seed) {
        float rimRadius = BOWL_RIM_RADIUS * size;
        float topRadius = BOWL_TOP_RADIUS * size;
        float height = BOWL_HEIGHT * size;
        float rimWidth = BOWL_RIM_WIDTH * size;

        double rimOuterY = 0.055 * size;
        double rimInnerY = 0.073 * size;
        double topOuterY = height * 0.82;
        double topInnerY = height;

        Vec3[] rimOuter = new Vec3[SEGMENTS];
        Vec3[] rimInner = new Vec3[SEGMENTS];
        Vec3[] topOuter = new Vec3[SEGMENTS];
        Vec3[] topInner = new Vec3[SEGMENTS];

        for (int i = 0; i < SEGMENTS; i++) {
            float angle = (float) ((Math.PI * 2.0 * i) / SEGMENTS);

            float sx = Mth.sin(angle);
            float sz = Mth.cos(angle);

            float rimWave = 1.0f + 0.010f * Mth.sin(angle * 4.0f + seed * 0.013f);
            float topWave = 1.0f + 0.006f * Mth.cos(angle * 3.0f + seed * 0.011f);

            float outerRim = rimRadius * rimWave;
            float innerRim = Math.max(0.02f, outerRim - rimWidth);

            float outerTop = topRadius * topWave;
            float innerTop = Math.max(0.02f, outerTop - rimWidth * 0.35f);

            rimOuter[i] = anchor.localRotated(sx * outerRim, rimOuterY, sz * outerRim, yawDeg, pitchDeg, rollDeg);
            rimInner[i] = anchor.localRotated(sx * innerRim, rimInnerY, sz * innerRim, yawDeg, pitchDeg, rollDeg);
            topOuter[i] = anchor.localRotated(sx * outerTop, topOuterY, sz * outerTop, yawDeg, pitchDeg, rollDeg);
            topInner[i] = anchor.localRotated(sx * innerTop, topInnerY, sz * innerTop, yawDeg, pitchDeg, rollDeg);
        }

        Vec3 topCenter = anchor.localRotated(
                0.0,
                topInnerY + 0.002 * size,
                0.0,
                yawDeg,
                pitchDeg,
                rollDeg
        );

        drawTazikMesh(buffers, rimOuter, rimInner, topOuter, topInner, topCenter, palette, alphaMul);
    }

    private void buildFlyingTazik(RenderBuffers buffers,
                                  Vec3 center,
                                  float size,
                                  float yawDeg,
                                  float pitchDeg,
                                  float rollDeg,
                                  Palette palette,
                                  float alphaMul,
                                  int seed) {
        buildTazik(
                buffers,
                HeadAnchor.world(center),
                size,
                yawDeg,
                pitchDeg,
                rollDeg,
                palette,
                alphaMul,
                seed
        );
    }

    private void drawTazikMesh(RenderBuffers buffers,
                               Vec3[] rimOuter,
                               Vec3[] rimInner,
                               Vec3[] topOuter,
                               Vec3[] topInner,
                               Vec3 topCenter,
                               Palette palette,
                               float alphaMul) {
        for (int i = 0; i < SEGMENTS; i++) {
            int next = (i + 1) % SEGMENTS;

            int rimLow1 = colorAt(palette, i, 0.76f, alphaMul);
            int rimLow2 = colorAt(palette, next, 0.76f, alphaMul);

            int wallTop1 = colorAt(palette, i, 1.02f, alphaMul);
            int wallTop2 = colorAt(palette, next, 1.02f, alphaMul);

            int innerTop1 = colorAt(palette, i, 0.66f, alphaMul * 0.90f);
            int innerTop2 = colorAt(palette, next, 0.66f, alphaMul * 0.90f);

            int innerLow1 = colorAt(palette, i, 0.50f, alphaMul * 0.82f);
            int innerLow2 = colorAt(palette, next, 0.50f, alphaMul * 0.82f);

            int rim1 = rimColorAt(palette, i, alphaMul);
            int rim2 = rimColorAt(palette, next, alphaMul);

            int topCenterColor = colorAt(palette, i, 1.10f, alphaMul * 0.95f);

            quadGradient(buffers.tris, rimOuter[i], rimLow1, rimOuter[next], rimLow2, topOuter[next], wallTop2, topOuter[i], wallTop1);
            quadGradient(buffers.tris, topInner[i], innerTop1, topInner[next], innerTop2, rimInner[next], innerLow2, rimInner[i], innerLow1);
            quadGradient(buffers.tris, rimInner[i], rim1, rimInner[next], rim2, rimOuter[next], rim2, rimOuter[i], rim1);

            triangle(buffers.tris, topCenter, topInner[i], topInner[next], topCenterColor);

            lineGradient(buffers.lines, rimOuter[i], rimOuter[next], rim1, rim2);
            lineGradient(buffers.lines, topOuter[i], topOuter[next], wallTop1, wallTop2);
        }
    }

    private void spawnFlyingTazik(Player player) {
        if (player == null) return;

        boolean helmet = hasHelmet(player);
        float size = scale.get() * (helmet ? helmetScale.get() : 1.0f);

        HeadAnchor anchor = HeadAnchor.of(
                player,
                1.0f,
                headTopOffset.get(),
                headForwardOffset.get(),
                headRightOffset.get()
        );

        Vec3 base = anchor.origin();

        Vec3 away = mc.player != null
                ? base.subtract(mc.player.getEyePosition())
                : new Vec3(randomRange(-1.0f, 1.0f), 0.0, randomRange(-1.0f, 1.0f));

        if (away.lengthSqr() < 1.0e-5) {
            away = new Vec3(randomRange(-1.0f, 1.0f), 0.0, randomRange(-1.0f, 1.0f));
        }

        away = away.normalize();

        float power = deathFlightPower.get();
        Vec3 velocity = new Vec3(
                away.x * randomRange(0.18f, 0.34f) * power + randomRange(-0.08f, 0.08f),
                randomRange(0.34f, 0.58f) * power,
                away.z * randomRange(0.18f, 0.34f) * power + randomRange(-0.08f, 0.08f)
        );

        flying.put(player.getId(), new FlyingTazik(
                base,
                velocity,
                size,
                resolvePalette(player),
                random.nextInt()
        ));
    }

    private Palette resolvePalette(Player player) {
        if (useRelations.get()) {
            int relation = CategoryService.getColor(player);
            int primary = withAlpha(relation, alphaOr(primaryColor.getArgb(), 255));
            int secondary = withAlpha(AnimatedRenderColors.analogousColor(relation), alphaOr(secondaryColor.getArgb(), 170));
            int rim = withAlpha(brighten(relation, 1.35f), alphaOr(rimColor.getArgb(), 238));

            return new Palette(primary, secondary, rim);
        }

        return new Palette(
                primaryColor.getArgb(),
                secondaryColor.getArgb(),
                rimColor.getArgb()
        );
    }

    private int colorAt(Palette palette, int index, float verticalLight, float alphaMul) {
        int dynamic = getDynamicColor(
                palette.primary,
                palette.secondary,
                animatedColorMode(),
                colorSpeed.get(),
                index
        );

        float circumference = 0.5f + 0.5f * Mth.sin(index * 0.47f + System.currentTimeMillis() * 0.0007f);
        int mixed = multiplyRgb(dynamic, verticalLight + circumference * 0.16f);

        return withAlpha(mixed, Math.round(alpha(mixed) * alphaMul));
    }

    private int rimColorAt(Palette palette, int index, float alphaMul) {
        int dynamic = getDynamicColor(
                palette.rim,
                palette.secondary,
                animatedColorMode(),
                colorSpeed.get(),
                index + 120
        );

        float t = 0.5f + 0.5f * Mth.sin(index * 0.71f + System.currentTimeMillis() * 0.0011f);
        int mixed = mix(dynamic, brighten(palette.primary, 1.35f), t);

        return withAlpha(mixed, Math.round(alpha(mixed) * alphaMul));
    }

    private AnimatedRenderColors.Mode animatedColorMode() {
        return switch (colorMode.get()) {
            case RAINBOW -> AnimatedRenderColors.Mode.RAINBOW;
            case LIGHT_RAINBOW -> AnimatedRenderColors.Mode.LIGHT_RAINBOW;
            case SKY -> AnimatedRenderColors.Mode.SKY;
            case FADE -> AnimatedRenderColors.Mode.FADE;
            case DOUBLE_COLOR -> AnimatedRenderColors.Mode.DOUBLE_COLOR;
            case ANALOGOUS -> AnimatedRenderColors.Mode.ANALOGOUS;
            case THEME -> AnimatedRenderColors.Mode.THEME;
            case STATIC -> AnimatedRenderColors.Mode.STATIC;
        };
    }

    private int getDynamicColor(int primary,
                                int secondary,
                                AnimatedRenderColors.Mode mode,
                                int speed,
                                int index) {
        int spatialIndex = Math.floorMod((int) Math.round(index * RAINBOW_SPATIAL_SCALE), 3600);
        return AnimatedRenderColors.resolve(mode, speed, spatialIndex, primary, secondary, true);
    }

    private boolean hasHelmet(Player player) {
        ItemStack stack = player.getItemBySlot(EquipmentSlot.HEAD);
        return !stack.isEmpty() && (
                stack.is(Items.LEATHER_HELMET)
                        || stack.is(Items.CHAINMAIL_HELMET)
                        || stack.is(Items.IRON_HELMET)
                        || stack.is(Items.GOLDEN_HELMET)
                        || stack.is(Items.DIAMOND_HELMET)
                        || stack.is(Items.NETHERITE_HELMET)
        );
    }

    private float randomRange(float min, float max) {
        return min + random.nextFloat() * (max - min);
    }

    public enum RenderTarget implements EnumValue.IdProvider {
        SELF("self"),
        PLAYERS("players"),
        BOTH("both");

        private final String id;

        RenderTarget(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }
    }

    public enum ColorMode implements EnumValue.IdProvider {
        STATIC("static"),
        RAINBOW("rainbow"),
        LIGHT_RAINBOW("light_rainbow"),
        SKY("sky"),
        FADE("fade"),
        DOUBLE_COLOR("double_color"),
        ANALOGOUS("analogous"),
        THEME("theme");

        private final String id;

        ColorMode(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }
    }

    private record RenderBuffers(MeshBuilder tris, MeshBuilder lines) {
    }

    private record Palette(int primary, int secondary, int rim) {
    }

    private record Wobble(float pitchDeg, float rollDeg) {
    }

    private final class FlyingTazik {
        private final float scale;
        private final Palette palette;
        private final int seed;
        private final long createdAtMs;
        private Vec3 pos;
        private Vec3 prevPos;
        private Vec3 velocity;
        private float yaw;
        private float pitch;
        private float roll;
        private float yawVelocity;
        private float pitchVelocity;
        private float rollVelocity;

        private FlyingTazik(Vec3 pos, Vec3 velocity, float scale, Palette palette, int seed) {
            this.pos = pos;
            this.prevPos = pos;
            this.velocity = velocity;
            this.scale = scale;
            this.palette = palette;
            this.seed = seed;
            this.createdAtMs = System.currentTimeMillis();

            float power = deathFlightPower.get();

            this.yaw = randomRange(0.0f, 360.0f);
            this.pitch = randomRange(-20.0f, 20.0f);
            this.roll = randomRange(-20.0f, 20.0f);

            this.yawVelocity = randomRange(-18.0f, 18.0f) * power;
            this.pitchVelocity = randomRange(-22.0f, 22.0f) * power;
            this.rollVelocity = randomRange(-24.0f, 24.0f) * power;
        }

        private boolean update() {
            long age = System.currentTimeMillis() - createdAtMs;
            if (age > DEATH_LIFE_MS) {
                return true;
            }

            prevPos = pos;

            velocity = velocity.add(0.0, -0.034, 0.0);
            velocity = new Vec3(
                    velocity.x * 0.985,
                    velocity.y * 0.965,
                    velocity.z * 0.985
            );

            pos = pos.add(velocity);

            if (mc.level != null) {
                BlockPos ground = BlockPos.containing(pos.x, pos.y - 0.035, pos.z);
                if (!mc.level.getBlockState(ground).isAir() && velocity.y < 0.0) {
                    pos = new Vec3(pos.x, ground.getY() + 1.035, pos.z);

                    if (Math.abs(velocity.y) > 0.045) {
                        velocity = new Vec3(
                                velocity.x * 0.72,
                                -velocity.y * 0.34,
                                velocity.z * 0.72
                        );

                        pitchVelocity *= 0.72f;
                        rollVelocity *= 0.72f;
                    } else {
                        velocity = new Vec3(
                                velocity.x * 0.62,
                                0.0,
                                velocity.z * 0.62
                        );

                        pitchVelocity *= 0.58f;
                        rollVelocity *= 0.58f;
                    }
                }
            }

            yaw += yawVelocity;
            pitch += pitchVelocity;
            roll += rollVelocity;

            yawVelocity *= 0.985f;
            pitchVelocity *= 0.975f;
            rollVelocity *= 0.975f;

            return false;
        }
    }
}

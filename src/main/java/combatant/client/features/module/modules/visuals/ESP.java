/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.visuals;


import combatant.client.features.theme.Theme;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import combatant.client.config.values.*;
import combatant.client.features.gui.hud.HudRenderSpace;
import combatant.client.features.module.*;
import combatant.client.features.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import combatant.client.config.common.CommonSettingSchemas;
import combatant.client.features.gui.clickgui.settings.TextListSetting;
import combatant.client.features.module.modules.combat.Hitbox;
import combatant.client.features.relations.CategoryService;
import combatant.client.mixins.accessors.EntityRenderManagerAccessor;
import combatant.client.mixins.accessors.EntityRendererAccessor;
import combatant.client.mixins.accessors.LevelRendererAccessor;
import combatant.client.render.ShaderEspRenderContext;
import combatant.client.render.compat.EntityCullingCompat;
import combatant.client.render.engine.RenderState;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.render.engine.core.CombatantWorldMatrices;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.msaa.MsaaFramebuffer;
import combatant.client.render.engine.msaa.MsaaWorldTarget;
import combatant.client.render.engine.pipeline.CombatantRenderPipelines;
import combatant.client.render.engine.postprocess.PostProcessManager;
import combatant.client.render.engine.postprocess.PostProcessPass;
import combatant.client.render.engine.renderer.FullScreenRenderer;
import combatant.client.render.engine.renderer.MeshRenderer;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.render.engine.uniform.MeshBuilder;
import combatant.client.render.engine.uniform.impl.ShaderEspBlurUniforms;
import combatant.client.render.engine.uniform.impl.ShaderEspGradientUniforms;
import combatant.client.render.engine.uniform.impl.ShaderEspSmokeUniforms;
import combatant.client.render.iris.IrisCombatantFrameHooks;
import combatant.client.render.helpers.MatteHudStyle;
import combatant.client.render.helpers.ScreenProjection;
import combatant.client.render.helpers.ScreenSpaceOverlay2D;
import combatant.client.util.player.PlayerHealthResolver;

import java.util.*;

//todo Description
@ModuleInfo(id = "esp", displayName = "ESP", aliases = {"wallhack", "wh", "outline"}, category = ModuleCategory.VISUALS)
public class ESP extends Module {

    private static final String MODE_FULL = "Full";
    private static final String MODE_CORNERS = "Corners";
    private static final String MODE_3D = "3D";
    private static final String MODE_SHADER = "Шейдер";
    private static final String SHADER_FILL_SOLID = "Solid";
    private static final String SHADER_FILL_SMOKE = "Smoke";
    private static final String SHADER_COLOR_ENTITY = "Entity";
    private static final String SHADER_COLOR_THEME = "Theme";
    private static final String SHADER_COLOR_CUSTOM = "Custom";
    private static final int OUTLINE_ALPHA = 145;
    private static final int COLOR_ALPHA = 210;
    private static final int HEALTH_ALPHA = 220;
    private static final int ABSORB_RGB = 0x3AA7FF;
    private static final float OUTLINE_EXPAND = 1.5f;
    private static final float OUTLINE_THICKNESS = 1.5f;
    private static final float COLOR_THICKNESS = 2.0f;
    private static final float GRAD_LIGHTEN = 0.30f;
    private static final float GRAD_DARKEN = 0.42f;
    private static final float HEALTH_BAR_WIDTH = 5.0f;
    private static final float HEALTH_BAR_GAP = 8.0f;
    private static final float HEALTH_BAR_INSET = 1.0f;
    private static final double AABB_TOP_OFFSET = 0.18;
    private static final float CORNER_SEGMENT_FRACTION = 0.27f;
    private static final boolean PIXEL_SNAP = true;
    private static final double SHADER_MAX_DISTANCE = 128.0;
    private static final float BOX_3D_GRADIENT_SPEED = 0.00115f;
    private static ProjectionMatrixBuffer shaderEspProjection;
    private static GpuSampler shaderEspSampler;
    private final Minecraft mc = Minecraft.getInstance();
    private final BooleanValue renderEntities =
            bool("espRenderEntities", "render_entities", false);
    private final RGBColorValue entityColor =
            visibleWhen(colorNoAlpha("espEntityColor", "entity_color", "#78C8FF"), renderEntities::get);
    private final ItemIdSetValue searchEntities =
            visibleWhen(itemList("espSearchEntities", "search_entities", TextListSetting.PickerMode.ENTITIES), renderEntities::get);
    private final BooleanValue renderSelf =
            bool("espRenderSelf", "render_self", false);
    private final ModeValue boxMode =
            modeCommon("espBoxMode", "box_mode", CommonSettingSchemas.ESP_BOX_MODE, MODE_FULL, MODE_FULL, MODE_CORNERS, MODE_3D, MODE_SHADER);
    private final SetValue shaderChamsEntities =
            visibleWhen(textList("espShaderChamsEntities", "shader_chams_entities",
                    TextListSetting.PickerMode.ENTITIES, Set.of("minecraft:end_crystal")), this::isShaderBox);
    private final RGBColorValue shaderChamsEntityColor =
            visibleWhen(colorNoAlpha("espShaderChamsEntityColor", "shader_chams_entity_color", "#B064FF"), this::isShaderBox);
    private final BooleanValue healthBar =
            visibleWhen(boolCommon("espHealthBar", "health_bar_left", CommonSettingSchemas.ESP_HEALTH_BAR, true), () -> !is3DBox() && !isShaderBox());
    private final BooleanValue healthBarGradient =
            visibleWhen(boolCommon("espHealthBarGradient", "health_bar_gradient", CommonSettingSchemas.ESP_HEALTH_BAR_GRADIENT, true), () -> healthBar.get() && !is3DBox() && !isShaderBox());
    private final NumberValue<Integer> healthBarDistance =
            visibleWhen(numCommon("espHealthBarDistance", "health_bar_distance", CommonSettingSchemas.ESP_HEALTH_BAR_DISTANCE, 80, 5, 256), () -> healthBar.get() && !is3DBox() && !isShaderBox());
    private final NumberValue<Float> box3dLineWidth =
            visibleWhen(numCommon("esp3dLineWidth", "line_width", CommonSettingSchemas.ESP_LINE_WIDTH, 1.5f, 0.5f, 6.0f), this::is3DBox);
    private final NumberValue<Float> box3dGradientStrength =
            visibleWhen(num("esp3dGradientStrength", "gradient_strength", 1.35f, 0.0f, 2.5f), this::is3DBox);
    private final NumberValue<Float> box3dFillAlpha =
            visibleWhen(num("esp3dFillAlpha", "fill_alpha", 0.30f, 0.0f, 1.0f), this::is3DBox);
    private final NumberValue<Float> box3dOutlineAlpha =
            visibleWhen(num("esp3dOutlineAlpha", "outline_alpha", 1.0f, 0.0f, 1.0f), this::is3DBox);
    private final BooleanValue shaderGlow =
            visibleWhen(bool("espShaderGlowEnabled", "shader_glow_enabled", true), this::isShaderBox);
    private final NumberValue<Float> shaderGlowStrength =
            visibleWhen(num("espShaderGlowStrength", "shader_glow_strength", 8.0f, 1.0f, 48.0f), () -> isShaderBox() && shaderGlow.get());
    private final NumberValue<Float> shaderGlowAlpha =
            visibleWhen(num("espShaderGlowAlpha", "shader_glow_alpha", 0.72f, 0.0f, 1.0f), () -> isShaderBox() && shaderGlow.get());
    private final NumberValue<Float> shaderGlowIntensity =
            visibleWhen(num("espShaderGlowIntensity", "shader_glow_intensity", 1.25f, 0.10f, 4.0f), () -> isShaderBox() && shaderGlow.get());
    private final ModeValue shaderGlowColorMode =
            visibleWhen(mode("espShaderGlowColorMode", "shader_glow_color_mode", SHADER_COLOR_ENTITY,
                            SHADER_COLOR_ENTITY, SHADER_COLOR_THEME, SHADER_COLOR_CUSTOM),
                    () -> isShaderBox() && shaderGlow.get());
    private final RGBColorValue shaderGlowColor =
            visibleWhen(colorNoAlpha("espShaderGlowColor", "shader_glow_color", "#78C8FF"),
                    () -> isShaderBox() && shaderGlow.get() && isCustomShaderColor(shaderGlowColorMode));
    private final BooleanValue shaderOutline =
            visibleWhen(bool("espShaderOutlineEnabled", "shader_outline_enabled", true), this::isShaderBox);
    private final NumberValue<Float> shaderOutlineWidth =
            visibleWhen(num("espShaderOutlineWidth", "shader_outline_width", 2.0f, 0.5f, 18.0f), () -> isShaderBox() && shaderOutline.get());
    private final NumberValue<Float> shaderOutlineAlpha =
            visibleWhen(num("espShaderOutlineAlpha", "shader_outline_alpha", 0.92f, 0.0f, 1.0f), () -> isShaderBox() && shaderOutline.get());
    private final NumberValue<Float> shaderOutlineIntensity =
            visibleWhen(num("espShaderOutlineIntensity", "shader_outline_intensity", 1.15f, 0.10f, 4.0f), () -> isShaderBox() && shaderOutline.get());
    private final ModeValue shaderShadowColorMode =
            visibleWhen(mode("espShaderShadowColorMode", "shader_shadow_color_mode", SHADER_COLOR_ENTITY,
                            SHADER_COLOR_ENTITY, SHADER_COLOR_THEME, SHADER_COLOR_CUSTOM),
                    () -> isShaderBox() && shaderOutline.get());
    private final RGBColorValue shaderShadowColor =
            visibleWhen(colorNoAlpha("espShaderShadowColor", "shader_shadow_color", "#78C8FF"),
                    () -> isShaderBox() && shaderOutline.get() && isCustomShaderColor(shaderShadowColorMode));
    private final BooleanValue shaderFill =
            visibleWhen(bool("espShaderFillEnabled", "shader_fill_enabled", true), this::isShaderBox);
    private final ModeValue shaderFillStyle =
            visibleWhen(mode("espShaderFillStyle", "shader_fill_style", SHADER_FILL_SOLID, SHADER_FILL_SOLID, SHADER_FILL_SMOKE),
                    () -> isShaderBox() && shaderFill.get());
    private final NumberValue<Float> shaderSmokeScale =
            visibleWhen(num("espShaderSmokeScale", "shader_smoke_scale", 2.60f, 0.35f, 8.0f),
                    () -> isShaderBox() && shaderFill.get() && isShaderSmokeFill());
    private final NumberValue<Float> shaderSmokeSpeed =
            visibleWhen(num("espShaderSmokeSpeed", "shader_smoke_speed", 0.55f, 0.0f, 3.0f),
                    () -> isShaderBox() && shaderFill.get() && isShaderSmokeFill());
    private final NumberValue<Integer> shaderSmokeOctaves =
            visibleWhen(num("espShaderSmokeOctaves", "shader_smoke_octaves", 4, 1, 6),
                    () -> isShaderBox() && shaderFill.get() && isShaderSmokeFill());
    private final NumberValue<Float> shaderSmokeContrast =
            visibleWhen(num("espShaderSmokeContrast", "shader_smoke_contrast", 1.20f, 0.35f, 3.0f),
                    () -> isShaderBox() && shaderFill.get() && isShaderSmokeFill());
    private final NumberValue<Float> shaderFillAlpha =
            visibleWhen(num("espShaderFillAlpha", "shader_fill_alpha", 0.41f, 0.0f, 1.0f),
                    () -> isShaderBox() && shaderFill.get());
    private final ModeValue shaderFillColorMode =
            visibleWhen(mode("espShaderFillColorMode", "shader_fill_color_mode", SHADER_COLOR_ENTITY,
                            SHADER_COLOR_ENTITY, SHADER_COLOR_THEME, SHADER_COLOR_CUSTOM),
                    () -> isShaderBox() && shaderFill.get());
    private final RGBColorValue shaderFillColor =
            visibleWhen(colorNoAlpha("espShaderFillColor", "shader_fill_color", "#78C8FF"),
                    () -> isShaderBox() && shaderFill.get() && isCustomShaderColor(shaderFillColorMode));
    private final NumberValue<Float> shaderDarkMultiplier =
            visibleWhen(num("espShaderDarkMultiplier", "shader_dark_multiplier", 0.55f, 0.0f, 1.5f), this::isShaderBox);
    private final PostProcessPass shaderEspPass = new ShaderEspPass();
    private final SubmitNodeStorage shaderEspCommandQueue = new SubmitNodeStorage();
    private TextureTarget shaderMask;
    private TextureTarget shaderBlurBuffer;
    private TextureTarget shaderEffectBuffer;
    private MsaaFramebuffer shaderMaskMsaa;
    private FeatureRenderDispatcher shaderEspRenderDispatcher;
    private RenderBuffers shaderEspRenderBuffers;
    private int shaderBufferW = -1;
    private int shaderBufferH = -1;
    private int shaderMaskSamples;

    {
        PostProcessManager.register(shaderEspPass);
    }

    private static boolean containsEntityId(Set<String> selected, Identifier id) {
        String full = id.toString();
        String path = id.getPath();
        for (String raw : selected) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String normalized = raw.trim().toLowerCase(Locale.ROOT);
            if (full.equals(normalized) || path.equals(normalized)) {
                return true;
            }
            if (!normalized.contains(":") && full.equals("minecraft:" + normalized)) {
                return true;
            }
        }
        return false;
    }

    private static GpuSampler getShaderEspSampler() {
        if (shaderEspSampler != null) {
            return shaderEspSampler;
        }
        shaderEspSampler = RenderSystem.getDevice().createSampler(
                AddressMode.CLAMP_TO_EDGE,
                AddressMode.CLAMP_TO_EDGE,
                FilterMode.LINEAR,
                FilterMode.LINEAR,
                1,
                OptionalDouble.empty()
        );
        return shaderEspSampler;
    }

    private static void clearFramebuffer(RenderTarget framebuffer) {
        if (framebuffer == null || framebuffer.getColorTexture() == null) {
            return;
        }
        RenderSystem.getDevice().createCommandEncoder().clearColorTexture(framebuffer.getColorTexture(), new org.joml.Vector4f(0.0f, 0.0f, 0.0f, 0.0f));
    }

    private static String formatHealth(float health) {
        if (Math.abs(health - Math.round(health)) < 0.05f) {
            return Math.round(health) + " HP";
        }
        return String.format(Locale.ROOT, "%.1f HP", health);
    }

    private static void addFilledBox(MeshBuilder mesh,
                                     AABB box,
                                     int c000, int c001, int c010, int c011,
                                     int c100, int c101, int c110, int c111) {
        double minX = box.minX;
        double minY = box.minY;
        double minZ = box.minZ;
        double maxX = box.maxX;
        double maxY = box.maxY;
        double maxZ = box.maxZ;

        quad(mesh, minX, minY, minZ, c000, maxX, minY, minZ, c100, maxX, minY, maxZ, c101, minX, minY, maxZ, c001);
        quad(mesh, minX, maxY, minZ, c010, minX, maxY, maxZ, c011, maxX, maxY, maxZ, c111, maxX, maxY, minZ, c110);
        quad(mesh, minX, minY, maxZ, c001, maxX, minY, maxZ, c101, maxX, maxY, maxZ, c111, minX, maxY, maxZ, c011);
        quad(mesh, minX, minY, minZ, c000, minX, maxY, minZ, c010, maxX, maxY, minZ, c110, maxX, minY, minZ, c100);
        quad(mesh, maxX, minY, minZ, c100, maxX, maxY, minZ, c110, maxX, maxY, maxZ, c111, maxX, minY, maxZ, c101);
        quad(mesh, minX, minY, minZ, c000, minX, minY, maxZ, c001, minX, maxY, maxZ, c011, minX, maxY, minZ, c010);
    }

    private static void addOutlineBox(MeshBuilder mesh,
                                      AABB box,
                                      int c000, int c001, int c010, int c011,
                                      int c100, int c101, int c110, int c111) {
        double minX = box.minX;
        double minY = box.minY;
        double minZ = box.minZ;
        double maxX = box.maxX;
        double maxY = box.maxY;
        double maxZ = box.maxZ;

        line(mesh, minX, minY, minZ, c000, maxX, minY, minZ, c100);
        line(mesh, maxX, minY, minZ, c100, maxX, minY, maxZ, c101);
        line(mesh, maxX, minY, maxZ, c101, minX, minY, maxZ, c001);
        line(mesh, minX, minY, maxZ, c001, minX, minY, minZ, c000);

        line(mesh, minX, maxY, minZ, c010, maxX, maxY, minZ, c110);
        line(mesh, maxX, maxY, minZ, c110, maxX, maxY, maxZ, c111);
        line(mesh, maxX, maxY, maxZ, c111, minX, maxY, maxZ, c011);
        line(mesh, minX, maxY, maxZ, c011, minX, maxY, minZ, c010);

        line(mesh, minX, minY, minZ, c000, minX, maxY, minZ, c010);
        line(mesh, maxX, minY, minZ, c100, maxX, maxY, minZ, c110);
        line(mesh, maxX, minY, maxZ, c101, maxX, maxY, maxZ, c111);
        line(mesh, minX, minY, maxZ, c001, minX, maxY, maxZ, c011);
    }

    private static void quad(MeshBuilder mesh,
                             double x1, double y1, double z1, int c1,
                             double x2, double y2, double z2, int c2,
                             double x3, double y3, double z3, int c3,
                             double x4, double y4, double z4, int c4) {
        mesh.ensureQuadCapacity();
        putVertex(mesh, x1, y1, z1, c1);
        int i1 = mesh.next();
        putVertex(mesh, x2, y2, z2, c2);
        int i2 = mesh.next();
        putVertex(mesh, x3, y3, z3, c3);
        int i3 = mesh.next();
        putVertex(mesh, x4, y4, z4, c4);
        int i4 = mesh.next();
        mesh.quad(i1, i2, i3, i4);
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
                             double x1, double y1, double z1, int c1,
                             double x2, double y2, double z2, int c2) {
        mesh.ensureLineCapacity();
        putVertex(mesh, x1, y1, z1, c1);
        int i1 = mesh.next();
        putVertex(mesh, x2, y2, z2, c2);
        int i2 = mesh.next();
        mesh.line(i1, i2);
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

    private static void putVertex(MeshBuilder mesh, double x, double y, double z, int argb) {
        mesh.vec3(x, y, z).color(
                (argb >>> 16) & 0xFF,
                (argb >>> 8) & 0xFF,
                argb & 0xFF,
                (argb >>> 24) & 0xFF
        );
    }

    private static int boxGradientColor(int baseRgb, AABB box, double x, double y, double z, int alpha, float strength) {
        double nx = safeRatio(x - box.minX, box.maxX - box.minX);
        double ny = safeRatio(y - box.minY, box.maxY - box.minY);
        double nz = safeRatio(z - box.minZ, box.maxZ - box.minZ);
        double phase = AnimationUtility.time(BOX_3D_GRADIENT_SPEED)
                + (box.minX + box.minY * 0.7 + box.minZ * 1.3) * 0.18;
        double waveA = 0.5 + 0.5 * Math.sin(phase + nx * 2.65 + ny * 5.30 - nz * 1.75);
        double waveB = 0.5 + 0.5 * Math.sin(phase * 0.72 - nx * 3.10 + ny * 1.35 + nz * 4.40);
        float t = clamp01((float) (ny * 0.44 + nz * 0.18 + (1.0 - nx) * 0.16 + waveA * 0.16 + waveB * 0.12));

        int darkRgb = mixRgb(baseRgb, 0x000000, clamp01(0.18f + 0.36f * strength));
        int lightRgb = mixRgb(baseRgb, 0xFFFFFF, clamp01(0.16f + 0.40f * strength));
        int gradientRgb = mixRgb(darkRgb, lightRgb, t);
        int preservedRgb = mixRgb(baseRgb, gradientRgb, clamp01(strength));
        return withAlpha(preservedRgb, alpha);
    }

    private static double safeRatio(double value, double length) {
        if (length <= 0.0001) return 0.0;
        if (value <= 0.0) return 0.0;
        if (value >= length) return 1.0;
        return value / length;
    }

    private static boolean isCustomShaderColor(ModeValue mode) {
        return mode != null && SHADER_COLOR_CUSTOM.equalsIgnoreCase(mode.get());
    }

    private static boolean usesShaderOverrideColor(ModeValue mode) {
        if (mode == null) return false;
        String value = mode.get();
        return SHADER_COLOR_THEME.equalsIgnoreCase(value) || SHADER_COLOR_CUSTOM.equalsIgnoreCase(value);
    }

    private static float shaderSmokeTime() {
        return (System.currentTimeMillis() % 600_000L) / 1000.0f;
    }

    private static int deriveSmokeColor(int rgb, int layer) {
        int base = rgb & 0x00FFFFFF;
        return switch (layer) {
            case 0 -> mixRgb(base, 0xFFFFFF, 0.18f);
            case 1 -> base;
            default -> mixRgb(base, 0x05070C, 0.48f);
        };
    }

    private static int withAlpha(int rgb, int alpha) {
        int a = clamp(alpha);
        return (a << 24) | (rgb & 0x00FFFFFF);
    }

    private static int alpha255(float alpha) {
        return clamp(Math.round(clamp01(alpha) * 255.0f));
    }

    private static int mixRgb(int rgbA, int rgbB, float t) {
        float clamped = Math.max(0f, Math.min(1f, t));
        int ar = (rgbA >>> 16) & 0xFF;
        int ag = (rgbA >>> 8) & 0xFF;
        int ab = rgbA & 0xFF;
        int br = (rgbB >>> 16) & 0xFF;
        int bg = (rgbB >>> 8) & 0xFF;
        int bb = rgbB & 0xFF;

        int r = Math.round(ar + (br - ar) * clamped);
        int g = Math.round(ag + (bg - ag) * clamped);
        int b = Math.round(ab + (bb - ab) * clamped);
        return (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    private static int scaleRgb(int rgb, float multiplier) {
        float m = Math.max(0.0f, Math.min(2.0f, multiplier));
        int r = Math.round(((rgb >>> 16) & 0xFF) * m);
        int g = Math.round(((rgb >>> 8) & 0xFF) * m);
        int b = Math.round((rgb & 0xFF) * m);
        return (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    private static float clamp01(float v) {
        if (v < 0f) return 0f;
        if (v > 1f) return 1f;
        return v;
    }

    private static int healthRgb(float ratio) {
        float t = clamp01(ratio);
        if (t >= 0.5f) {
            float k = (t - 0.5f) / 0.5f;
            return mixRgb(0xFFFF00, 0x00FF00, k);
        }
        float k = t / 0.5f;
        return mixRgb(0xFF0000, 0xFFFF00, k);
    }

    private static int clamp(int v) {
        if (v < 0) return 0;
        if (v > 255) return 255;
        return v;
    }

    private static int mixArgb(int a, int b, float t) {
        float clamped = clamp01(t);
        int aa = (a >>> 24) & 0xFF;
        int ar = (a >>> 16) & 0xFF;
        int ag = (a >>> 8) & 0xFF;
        int ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF;
        int br = (b >>> 16) & 0xFF;
        int bg = (b >>> 8) & 0xFF;
        int bb = b & 0xFF;

        int oa = Math.round(aa + (ba - aa) * clamped);
        int or = Math.round(ar + (br - ar) * clamped);
        int og = Math.round(ag + (bg - ag) * clamped);
        int ob = Math.round(ab + (bb - ab) * clamped);
        return (clamp(oa) << 24) | (clamp(or) << 16) | (clamp(og) << 8) | clamp(ob);
    }

    @Override
    public HudRenderSpace getHudRenderSpace() {
        return HudRenderSpace.UNSCALED_LOGICAL;
    }

    @Override
    public HudPhase getHudPhase() {
        return HudPhase.BEFORE_MISC_OVERLAYS;
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.END_MAIN;
    }

    @Override
    public void onRenderHudEngine(Renderer2D renderer, TextRenderer textRenderer, float tickDelta) {
        if (!isEnabled() || mc.level == null || mc.player == null) return;

        Vec3 camPos = mc.gameRenderer.mainCamera().position();

        if (!is3DBox() && !isShaderBox()) {
            List<RenderEntry> playerEntries = collectPlayerEntries(camPos);
            playerEntries.sort(Comparator.comparingDouble(RenderEntry::distSq).reversed());

            for (RenderEntry entry : playerEntries) {
                draw2DESP(renderer, entry.entity(), tickDelta, entry.color(), entry.distSq());
            }
        }

        renderSearchEntityOverlay(renderer, textRenderer, tickDelta, camPos);
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!isEnabled() || !is3DBox() || mc.level == null || mc.player == null) return;

        Vec3 camPos = mc.gameRenderer.mainCamera().position();
        List<RenderEntry> entries = collectPlayerEntries(camPos);
        if (entries.isEmpty()) return;

        float previousLineWidth = RenderState.lineWidth;
        MeshBuilder lines;
        MeshBuilder tris;
        try {
            RenderState.lineWidth = box3dLineWidth.get();
            lines = renderer.batch(CombatantRenderPipelines.WORLD_COLORED_LINES, Renderer3D.DepthMode.NONE);
            tris = renderer.batch(CombatantRenderPipelines.WORLD_COLORED, Renderer3D.DepthMode.NONE);
        } finally {
            RenderState.lineWidth = previousLineWidth;
        }

        entries.sort(Comparator.comparingDouble(RenderEntry::distSq).reversed());
        for (RenderEntry entry : entries) {
            Entity entity = entry.entity();
            AABB box = resolveWorldBox(entity, tickDelta, shouldUseHitboxExpansion(entity));
            int color = resolve3DColor(entity, entry.color());
            add3DBox(tris, lines, box, color);
        }
    }

    @Override
    public void onDisable() {
        if (shaderMaskMsaa != null) {
            shaderMaskMsaa.destroyBuffers();
            shaderMaskMsaa = null;
        }
        closeShaderEspRenderDispatcher();
        shaderMaskSamples = 0;
    }

    private void closeShaderEspRenderDispatcher() {
        if (shaderEspRenderDispatcher != null) {
            shaderEspRenderDispatcher.close();
            shaderEspRenderDispatcher = null;
        }
        if (shaderEspRenderBuffers != null) {
            shaderEspRenderBuffers.close();
            shaderEspRenderBuffers = null;
        }
    }

    private List<RenderEntry> collectPlayerEntries(Vec3 camPos) {
        List<RenderEntry> entries = new ArrayList<>();

        for (Player player : mc.level.players()) {
            if (!shouldRenderPlayer(player)) continue;
            int color = resolvePlayerColor(player);
            double distSq = player.distanceToSqr(camPos);
            entries.add(new RenderEntry(player, color, distSq));
        }

        return entries;
    }

    private List<RenderEntry> collectSearchEntityEntries(Vec3 camPos) {
        List<RenderEntry> entries = new ArrayList<>();
        if (!renderEntities.get()) return entries;

        int color = entityColor.getArgb();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!shouldRenderEntity(entity)) continue;
            double distSq = entity.distanceToSqr(camPos);
            entries.add(new RenderEntry(entity, color, distSq));
        }

        return entries;
    }

    private boolean shouldRenderPlayer(Player p) {
        if (p == null) return false;
        if (p == mc.player) {
            if (mc.options.getCameraType().isFirstPerson()) return false;
            return renderSelf.get();
        }
        return true;
    }

    private boolean shouldRenderEntity(Entity e) {
        if (e == null) return false;
        if (e instanceof Player) return false;
        if (e == mc.player) return false;
        Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
        if (id == null) return false;
        return searchEntities.get().contains(id.toString());
    }

    private int resolvePlayerColor(Player p) {
        return CategoryService.getColor(p);
    }

    private int resolve3DColor(Entity entity, int fallbackColor) {
        if (entity instanceof Player player) {
            return CategoryService.getColor(player);
        }
        return fallbackColor;
    }

    private boolean renderShaderEsp(GpuTextureView src, GpuTextureView dst, float tickDelta) {
        if (!isEnabled() || !isShaderBox() || mc.level == null || mc.player == null) {
            return false;
        }
        if (!shaderGlow.get() && !shaderOutline.get() && !shaderFill.get()) {
            return false;
        }

        Vec3 camPos = mc.gameRenderer.mainCamera().position();
        List<RenderEntry> entries = collectShaderEntries(camPos);
        if (entries.isEmpty()) {
            return false;
        }

        ensureShaderBuffers();
        if (shaderMask == null || shaderBlurBuffer == null || shaderEffectBuffer == null) {
            return false;
        }

        if (!renderShaderEntityMask(entries, tickDelta)) {
            return false;
        }

        PostProcessManager.copy(src, dst);

        int width = mc.getWindow().getWidth();
        int height = mc.getWindow().getHeight();
        return renderShaderColorPass(dst, width, height);
    }

    private List<RenderEntry> collectShaderEntries(Vec3 camPos) {
        List<RenderEntry> entries = new ArrayList<>();
        double maxDistSq = SHADER_MAX_DISTANCE * SHADER_MAX_DISTANCE;

        for (RenderEntry entry : collectPlayerEntries(camPos)) {
            if (entry.distSq() > maxDistSq) {
                continue;
            }
            entries.add(entry);
        }

        for (RenderEntry entry : collectShaderChamsEntityEntries(camPos, maxDistSq)) {
            entries.add(entry);
        }

        entries.sort(Comparator.comparingDouble(RenderEntry::distSq).reversed());
        return entries;
    }

    private List<RenderEntry> collectShaderChamsEntityEntries(Vec3 camPos, double maxDistSq) {
        List<RenderEntry> entries = new ArrayList<>();
        Set<String> selected = shaderChamsEntities.get();
        if (selected == null || selected.isEmpty()) {
            return entries;
        }

        int color = shaderChamsEntityColor.getArgb();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!shouldRenderShaderChamsEntity(entity, selected)) {
                continue;
            }
            double distSq = entity.distanceToSqr(camPos);
            if (distSq > maxDistSq) {
                continue;
            }
            entries.add(new RenderEntry(entity, color, distSq));
        }
        return entries;
    }

    private boolean shouldRenderShaderChamsEntity(Entity entity, Set<String> selected) {
        if (entity == null) return false;
        if (entity instanceof Player) return false;
        if (entity == mc.player) return false;

        Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (id == null) return false;
        return containsEntityId(selected, id);
    }

    private boolean renderShaderColorPass(GpuTextureView dst, int width, int height) {
        if (shaderMask.getColorTextureView() == null) {
            return false;
        }

        float darkMultiplier = shaderDarkMultiplier.get();
        boolean rendered = false;

        if (shaderGlow.get()) {
            float glowRadius = Math.max(1.0f, shaderGlowStrength.get());
            blurShaderMask(shaderMask, shaderEffectBuffer, glowRadius, width, height);
            drawShaderGradient(shaderEffectBuffer, dst, width, height,
                    alpha255(shaderGlowAlpha.get()),
                    darkMultiplier,
                    shaderGlowColorMode,
                    shaderGlowColor,
                    shaderGlowIntensity.get());
            rendered = true;
        }

        if (shaderFill.get()) {
            if (isShaderSmokeFill()) {
                drawShaderSmoke(shaderMask, dst, width, height);
            } else {
                drawShaderGradient(shaderMask, dst, width, height,
                        alpha255(shaderFillAlpha.get()),
                        darkMultiplier,
                        shaderFillColorMode,
                        shaderFillColor,
                        1.0f);
            }
            rendered = true;
        }

        if (shaderOutline.get()) {
            float outlineRadius = Math.max(0.5f, shaderOutlineWidth.get());
            blurShaderMask(shaderMask, shaderEffectBuffer, outlineRadius, width, height);
            drawShaderGradient(shaderEffectBuffer, dst, width, height,
                    alpha255(shaderOutlineAlpha.get()),
                    darkMultiplier,
                    shaderShadowColorMode,
                    shaderShadowColor,
                    shaderOutlineIntensity.get());
            rendered = true;
        }

        return rendered;
    }

    private void blurShaderMask(RenderTarget input, RenderTarget output, float radius, int width, int height) {

        clearFramebuffer(shaderBlurBuffer);
        ShaderEspBlurUniforms.update(width, height, Math.min(radius, 63.0f), 1.0f, 0.0f);
        FullScreenRenderer.begin("Combatant Fullscreen Pass")
                .attachment(shaderBlurBuffer.getColorTextureView())
                .pipeline(CombatantRenderPipelines.SHADER_ESP_SHADOW)
                .uniform("ShaderEspBlur", ShaderEspBlurUniforms.get())
                .sampler("u_Texture", input.getColorTextureView(), getShaderEspSampler())
                .sampler("u_Mask", input.getColorTextureView(), getShaderEspSampler())
                .end();

        clearFramebuffer(output);
        ShaderEspBlurUniforms.update(width, height, Math.min(radius, 63.0f), 0.0f, 1.0f);
        FullScreenRenderer.begin("Combatant Fullscreen Pass")
                .attachment(output.getColorTextureView())
                .pipeline(CombatantRenderPipelines.SHADER_ESP_SHADOW)
                .uniform("ShaderEspBlur", ShaderEspBlurUniforms.get())
                .sampler("u_Texture", shaderBlurBuffer.getColorTextureView(), getShaderEspSampler())
                .sampler("u_Mask", input.getColorTextureView(), getShaderEspSampler())
                .end();
    }

    private void drawShaderGradient(RenderTarget input, GpuTextureView dst, int width, int height, int alpha,
                                    float darkMultiplier, ModeValue colorMode, RGBColorValue customColor,
                                    float intensity) {
        int rgb = resolveShaderColor(colorMode, customColor);
        boolean overrideColor = usesShaderOverrideColor(colorMode);
        int passColor = withAlpha(rgb, alpha);
        ShaderEspGradientUniforms.update(
                0.0f,
                0.0f,
                width,
                height,
                passColor,
                darkMultiplier,
                overrideColor ? 1.0f : 0.0f,
                Math.max(0.0f, Math.min(4.0f, intensity))
        );
        FullScreenRenderer.begin("Combatant Fullscreen Pass")
                .attachment(dst)
                .pipeline(CombatantRenderPipelines.SHADER_ESP_GRADIENT)
                .uniform("ShaderEspGradient", ShaderEspGradientUniforms.get())
                .sampler("u_Texture", input.getColorTextureView(), getShaderEspSampler())
                .end();
    }

    private void drawShaderSmoke(RenderTarget input, GpuTextureView dst, int width, int height) {
        int rgb = resolveShaderColor(shaderFillColorMode, shaderFillColor);
        boolean overrideColor = usesShaderOverrideColor(shaderFillColorMode);
        ShaderEspSmokeUniforms.update(
                0.0f,
                0.0f,
                width,
                height,
                shaderSmokeTime(),
                shaderSmokeScale.get(),
                shaderSmokeSpeed.get(),
                clamp01(shaderFillAlpha.get()),
                shaderSmokeOctaves.get(),
                shaderSmokeContrast.get(),
                overrideColor ? 1.0f : 0.0f,
                1.0f,
                deriveSmokeColor(rgb, 0),
                deriveSmokeColor(rgb, 1),
                deriveSmokeColor(rgb, 2)
        );
        FullScreenRenderer.begin("Combatant Fullscreen Pass")
                .attachment(dst)
                .pipeline(CombatantRenderPipelines.SHADER_ESP_SMOKE)
                .uniform("ShaderEspSmoke", ShaderEspSmokeUniforms.get())
                .sampler("u_Texture", input.getColorTextureView(), getShaderEspSampler())
                .end();
    }

    private void ensureShaderBuffers() {
        int width = mc.getWindow().getWidth();
        int height = mc.getWindow().getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }

        boolean sizeChanged = width != shaderBufferW || height != shaderBufferH;
        if (shaderMask == null) {
            shaderMask = new TextureTarget("combatant-shader-esp-mask", width, height, true, GpuFormat.RGBA8_UNORM);
            shaderBlurBuffer = new TextureTarget("combatant-shader-esp-blur", width, height, false, GpuFormat.RGBA8_UNORM);
            shaderEffectBuffer = new TextureTarget("combatant-shader-esp-effect", width, height, false, GpuFormat.RGBA8_UNORM);
            shaderBufferW = width;
            shaderBufferH = height;
        } else if (sizeChanged) {
            shaderMask.resize(width, height);
            shaderBlurBuffer.resize(width, height);
            shaderEffectBuffer.resize(width, height);
            shaderBufferW = width;
            shaderBufferH = height;
        }

        int samples = MsaaWorldTarget.getSamples();
        if (samples > 1) {
            if (shaderMaskMsaa == null || shaderMaskSamples != samples) {
                if (shaderMaskMsaa != null) {
                    shaderMaskMsaa.destroyBuffers();
                }
                shaderMaskMsaa = new MsaaFramebuffer("combatant-shader-esp-mask-msaa", width, height, true, samples);
                shaderMaskSamples = samples;
            } else if (sizeChanged) {
                shaderMaskMsaa.resize(width, height);
            }
        } else if (shaderMaskMsaa != null) {
            shaderMaskMsaa.destroyBuffers();
            shaderMaskMsaa = null;
            shaderMaskSamples = 0;
        }
    }

    private boolean renderShaderEntityMask(List<RenderEntry> entries, float tickDelta) {
        if (entries.isEmpty() || shaderMask == null) {
            return false;
        }

        LevelRenderer worldRenderer = mc.levelRenderer;
        if (worldRenderer == null) {
            return false;
        }

        MsaaFramebuffer worldMsaa = MsaaWorldTarget.getMsaaFramebuffer();
        boolean useMsaaMask = worldMsaa != null
                && worldMsaa.getSamples() > 1
                && shaderMaskMsaa != null;
        RenderTarget target = useMsaaMask ? shaderMaskMsaa : shaderMask;

        var colorTex = target.getColorTexture();
        if (colorTex == null) {
            return false;
        }

        var encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.clearColorTexture(colorTex, new org.joml.Vector4f(0.0f, 0.0f, 0.0f, 0.0f));

        var depthTex = target.getDepthTexture();
        if (depthTex != null) {
            encoder.clearDepthTexture(depthTex, 1.0);
        }

        GpuTextureView prevColor = RenderSystem.outputColorTextureOverride;
        GpuTextureView prevDepth = RenderSystem.outputDepthTextureOverride;
        GpuBufferSlice prevProjection = RenderSystem.getProjectionMatrixBuffer();
        ProjectionType prevProjectionType = RenderSystem.getProjectionType();
        Matrix4f prevMeshProjection = MeshRenderer.projection();
        Matrix4f prevWorldProjection = new Matrix4f(RenderState.worldProjection);
        boolean prevRendering3D = RenderState.rendering3D;

        RenderSystem.outputColorTextureOverride = target.getColorTextureView();
        RenderSystem.outputDepthTextureOverride = target.getDepthTextureView();

        if (shaderEspProjection == null) {
            shaderEspProjection = new ProjectionMatrixBuffer("combatant-shader-esp-projection");
        }
        Matrix4f espProjection = CombatantWorldMatrices.renderProjectionMatrix();
        if (espProjection == null) {
            espProjection = new Matrix4f(RenderState.worldProjection);
        }
        RenderSystem.setProjectionMatrix(shaderEspProjection.getBuffer(espProjection), ProjectionType.PERSPECTIVE);
        MeshRenderer.setProjection(espProjection);
        RenderState.worldProjection.set(espProjection);
        RenderState.rendering3D = true;

        var modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.identity();
        Matrix4f capturedPosition = CombatantWorldMatrices.positionMatrix();
        if (capturedPosition != null) {
            modelView.mul(capturedPosition);
        } else if (mc.gameRenderer != null && mc.gameRenderer.mainCamera() != null) {
            modelView.mul(new Matrix4f().rotation(mc.gameRenderer.mainCamera().rotation().conjugate(new Quaternionf())));
        }

        PoseStack matrices = new PoseStack();
        boolean pushed = false;

        try (ShaderEspRenderContext.Scope ignored = ShaderEspRenderContext.enter()) {
            LevelRendererAccessor accessor = (LevelRendererAccessor) worldRenderer;
            LevelRenderState base = accessor.combatant$getWorldRenderState();
            if (base == null || base.cameraRenderState == null) {
                return false;
            }

            LevelRenderState temp = new LevelRenderState();
            temp.cameraRenderState = base.cameraRenderState;
            temp.shouldShowEntityOutlines = true;

            shaderEspCommandQueue.getSubmitsPerOrder().clear();

            EntityRenderDispatcher entityRenderManager = mc.getEntityRenderDispatcher();
            for (RenderEntry entry : entries) {
                Entity entity = entry.entity();
                EntityCullingCompat.forceVisibleForShaderEsp(entity);
                EntityRenderer<?, ?> entityRenderer =
                        ((EntityRenderManagerAccessor) entityRenderManager).combatant$invokeGetRenderer(entity);
                if (entityRenderer == null) {
                    continue;
                }

                EntityRenderState state = ((EntityRendererAccessor) entityRenderer).combatant$invokeCreateRenderState();
                ((EntityRendererAccessor) entityRenderer).combatant$invokeUpdateRenderState(entity, state, tickDelta);
                if (state == null) {
                    continue;
                }

                state.outlineColor = 0xFF000000 | (entry.color() & 0x00FFFFFF);
                state.shadowRadius = 0.0f;
                state.shadowPieces.clear();
                state.nameTag = null;
                state.nameTagAttachment = null;
                temp.entityRenderStates.add(state);
            }

            if (temp.entityRenderStates.isEmpty()) {
                return false;
            }

            FeatureRenderDispatcher dispatcher = getShaderEspRenderDispatcher();
            if (dispatcher == null) {
                return false;
            }

            boolean standaloneDispatcher = dispatcher == shaderEspRenderDispatcher && shaderEspRenderBuffers != null;
            accessor.combatant$invokeSubmitEntities(matrices, temp, shaderEspCommandQueue);
            pushed = true;
            try {
                try (FeatureRenderDispatcher.PreparedFrame prepared = dispatcher.prepareFrame(shaderEspCommandQueue)) {
                    if (!prepared.hasAnyOutline()) {
                        return false;
                    }
                    prepared.executeOutline();
                }
            } finally {
                if (standaloneDispatcher) {
                    shaderEspRenderBuffers.endFrame();
                }
            }
        } finally {
            modelView.popMatrix();
            MeshRenderer.setProjection(prevMeshProjection);
            RenderState.worldProjection.set(prevWorldProjection);
            RenderState.rendering3D = prevRendering3D;
            if (prevProjection != null && prevProjectionType != null) {
                RenderSystem.setProjectionMatrix(prevProjection, prevProjectionType);
            }
            RenderSystem.outputColorTextureOverride = prevColor;
            RenderSystem.outputDepthTextureOverride = prevDepth;
            shaderEspCommandQueue.getSubmitsPerOrder().clear();
        }

        return !useMsaaMask || CombatantRenderSystem.rhi().msaa().resolve(shaderMaskMsaa, shaderMask, true, false);
    }

    private FeatureRenderDispatcher getShaderEspRenderDispatcher() {
        if (IrisCombatantFrameHooks.isRenderingAfterIrisFinalization()) {
            return getStandaloneShaderEspRenderDispatcher();
        }
        if (mc != null && mc.levelRenderer instanceof LevelRendererAccessor accessor) {
            FeatureRenderDispatcher dispatcher = accessor.combatant$getEntityRenderDispatcher();
            if (dispatcher != null) {
                return dispatcher;
            }
        }
        return getStandaloneShaderEspRenderDispatcher();
    }

    private FeatureRenderDispatcher getStandaloneShaderEspRenderDispatcher() {
        if (shaderEspRenderDispatcher != null) {
            return shaderEspRenderDispatcher;
        }
        if (mc.gameRenderer == null || mc.getModelManager() == null || mc.getAtlasManager() == null || mc.font == null) {
            return null;
        }
        shaderEspRenderBuffers = new RenderBuffers(1);
        shaderEspRenderDispatcher = new FeatureRenderDispatcher(
                shaderEspRenderBuffers,
                mc.getModelManager(),
                mc.getAtlasManager(),
                mc.font,
                mc.gameRenderer.gameRenderState()
        );
        return shaderEspRenderDispatcher;
    }

    private void renderSearchEntityOverlay(Renderer2D renderer, TextRenderer fallbackTextRenderer, float tickDelta, Vec3 camPos) {
        List<RenderEntry> entries = collectSearchEntityEntries(camPos);
        if (entries.isEmpty()) return;

        entries.sort(Comparator.comparingDouble(RenderEntry::distSq).reversed());

        TextRenderer labelRenderer = ScreenSpaceOverlay2D.labelRenderer(fallbackTextRenderer);
        List<SearchLabelEntry> labels = new ArrayList<>();
        boolean measureStarted = false;
        if (!labelRenderer.isBuilding()) {
            labelRenderer.begin(ScreenSpaceOverlay2D.TEXT_SCALE, true, false);
            measureStarted = true;
        }

        for (RenderEntry entry : entries) {
            Entity entity = entry.entity();
            AABB box = resolveWorldBox(entity, tickDelta, false);
            ScreenSpaceOverlay2D.ScreenRect rect = ScreenSpaceOverlay2D.projectBox(box, tickDelta);
            if (rect == null) continue;

            ScreenSpaceOverlay2D.LabelEntry label = createSearchEntityLabel(labelRenderer, entity, entry.color(), rect);
            labels.add(new SearchLabelEntry(entry.distSq(), rect, entry.color(), label));
        }

        if (measureStarted) {
            labelRenderer.end();
        }
        if (labels.isEmpty()) return;

        labels.sort(Comparator.comparingDouble(SearchLabelEntry::distSq).reversed());
        for (SearchLabelEntry entry : labels) {
            renderSearchEntityLabelPass(renderer, labelRenderer, entry);
        }
    }

    private void renderSearchEntityLabelPass(Renderer2D renderer,
                                             TextRenderer labelRenderer,
                                             SearchLabelEntry entry) {
        ScreenSpaceOverlay2D.ScreenRect rect = entry.rect();
        ScreenSpaceOverlay2D.LabelEntry label = entry.label();
        MatteHudStyle.drawFrame(renderer, rect.minX(), rect.minY(), rect.width(), rect.height(), entry.color(), 1.0f);
        MatteHudStyle.drawLabelPlate(renderer, label.x(), label.y(), label.totalWidth(), label.height(), 1.0f);

        boolean renderStarted = false;
        if (!labelRenderer.isBuilding()) {
            labelRenderer.begin(ScreenSpaceOverlay2D.TEXT_SCALE);
            renderStarted = true;
        }
        try {
            ScreenSpaceOverlay2D.renderLabels(labelRenderer, List.of(label), false);
        } finally {
            if (renderStarted) {
                labelRenderer.end();
            }
        }
        Renderer2D.flushBatch(Renderer2D.FlushReason.EXPLICIT);
    }

    private ScreenSpaceOverlay2D.LabelEntry createSearchEntityLabel(TextRenderer labelRenderer, Entity entity,
                                                                    int baseColor,
                                                                    ScreenSpaceOverlay2D.ScreenRect rect) {
        String name = entity.getDisplayName() != null ? entity.getDisplayName().getString() : entity.getName().getString();
        if (entity instanceof LivingEntity living) {
            PlayerHealthResolver.HealthSnapshot health = PlayerHealthResolver.resolve(living);
            float max = health.maxHealth();
            if (max > 0.0f) {
                float ratio = clamp01(health.totalHealth() / max);
                String hpText = formatHealth(health.totalHealth());
                return ScreenSpaceOverlay2D.createCenteredLabel(
                        labelRenderer,
                        name,
                        hpText,
                        baseColor,
                        withAlpha(healthRgb(ratio), 235),
                        rect
                );
            }
        }
        return ScreenSpaceOverlay2D.createCenteredLabel(labelRenderer, name, baseColor, rect);
    }

    private void draw2DESP(Renderer2D renderer, Entity entity, float tickDelta, int baseColor, double distSq) {
        AABB box = resolveWorldBox(entity, tickDelta, false);
        ScreenBox screen = project(box, tickDelta);
        if (screen == null) return;

        drawBoxESP(renderer, screen.minX(), screen.minY(), screen.maxX(), screen.maxY(), baseColor);
        if (healthBar.get()) {
            double maxDist = healthBarDistance.get();
            if (distSq <= maxDist * maxDist) {
                drawHealthBar(renderer, screen.minX(), screen.minY(), screen.maxX(), screen.maxY(), entity);
            }
        }
    }

    private AABB resolveWorldBox(Entity entity, float tickDelta, boolean hitboxExpanded) {
        Vec3 pos = entity.getPosition(tickDelta);
        AABB box = entity.getBoundingBox().move(
                pos.x - entity.getX(),
                pos.y - entity.getY(),
                pos.z - entity.getZ()
        );

        Hitbox hitbox = Modules.get(Hitbox.class);
        if (hitboxExpanded && hitbox != null) {
            box = box.inflate(hitbox.getPadding());
        }

        return new AABB(box.minX, box.minY, box.minZ, box.maxX, box.maxY + AABB_TOP_OFFSET, box.maxZ);
    }

    private ScreenBox project(AABB box, float tickDelta) {
        Vec3[] pts = new Vec3[]{
                new Vec3(box.minX, box.minY, box.minZ),
                new Vec3(box.minX, box.minY, box.maxZ),
                new Vec3(box.minX, box.maxY, box.minZ),
                new Vec3(box.minX, box.maxY, box.maxZ),
                new Vec3(box.maxX, box.minY, box.minZ),
                new Vec3(box.maxX, box.minY, box.maxZ),
                new Vec3(box.maxX, box.maxY, box.minZ),
                new Vec3(box.maxX, box.maxY, box.maxZ)
        };

        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;

        for (Vec3 p : pts) {
            Vec3 screen = ScreenProjection.worldToScreen(p, tickDelta);
            if (screen == null) continue;
            minX = Math.min(minX, screen.x);
            minY = Math.min(minY, screen.y);
            maxX = Math.max(maxX, screen.x);
            maxY = Math.max(maxY, screen.y);
        }

        if (minX == Double.POSITIVE_INFINITY) return null;
        if (maxX <= minX || maxY <= minY) return null;

        if (PIXEL_SNAP) {
            minX = Math.floor(minX);
            minY = Math.floor(minY);
            maxX = Math.ceil(maxX);
            maxY = Math.ceil(maxY);
        }

        return new ScreenBox(minX, minY, maxX, maxY);
    }

    private void drawBoxESP(Renderer2D renderer, double minX, double minY, double maxX, double maxY, int baseColor) {
        int baseRgb = baseColor & 0x00FFFFFF;
        int darkRgb = mixRgb(baseRgb, 0x000000, GRAD_DARKEN);
        int lightRgb = mixRgb(baseRgb, 0xFFFFFF, GRAD_LIGHTEN);
        Gradient gradient = new Gradient(
                withAlpha(lightRgb, COLOR_ALPHA),
                withAlpha(baseRgb, COLOR_ALPHA),
                withAlpha(lightRgb, COLOR_ALPHA),
                withAlpha(darkRgb, COLOR_ALPHA)
        );
        int outline = withAlpha(0x000000, OUTLINE_ALPHA);
        drawOutline(renderer,
                minX - OUTLINE_EXPAND, minY - OUTLINE_EXPAND,
                maxX + OUTLINE_EXPAND, maxY + OUTLINE_EXPAND,
                OUTLINE_THICKNESS, SolidColor.of(outline));

        drawOutline(renderer, minX, minY, maxX, maxY, COLOR_THICKNESS, gradient);
        double innerInset = COLOR_THICKNESS;
        drawOutline(renderer,
                minX + innerInset, minY + innerInset,
                maxX - innerInset, maxY - innerInset,
                OUTLINE_THICKNESS, SolidColor.of(outline));
    }

    private void drawHealthBar(Renderer2D renderer, double x1, double y1, double x2, double y2, Entity entity) {
        if (!(entity instanceof LivingEntity living)) return;
        double height = y2 - y1;
        if (height <= 0.0) return;

        PlayerHealthResolver.HealthSnapshot health = PlayerHealthResolver.resolve(living);
        float max = health.maxHealth();
        if (max <= 0f) return;
        float current = health.totalHealth();
        float ratio = clamp01(current / max);
        float absorption = Math.max(0f, health.absorption());
        float absorptionRatio = clamp01(absorption / max);

        double barX = x1 - HEALTH_BAR_GAP - HEALTH_BAR_WIDTH;
        double barY = y1;

        renderer.quad(barX, barY, HEALTH_BAR_WIDTH, height, 0xFF000000);

        double inset = Math.min(HEALTH_BAR_INSET, Math.min(HEALTH_BAR_WIDTH * 0.45, height * 0.1));
        double innerX = barX + inset;
        double innerY = barY + inset;
        double innerW = Math.max(0.0, HEALTH_BAR_WIDTH - inset * 2.0);
        double innerH = Math.max(0.0, height - inset * 2.0);

        double fillH = innerH * ratio;
        if (fillH <= 0.0) return;
        double fillY = innerY + (innerH - fillH);
        if (healthBarGradient.get()) {
            float topT = clamp01((float) (fillH / Math.max(0.0001, innerH)));
            int topRgb = mixRgb(0xFF0000, 0x00FF00, topT);
            int bottomRgb = 0xFF0000;
            renderer.quad(
                    innerX, fillY, innerW, fillH,
                    withAlpha(topRgb, HEALTH_ALPHA),
                    withAlpha(topRgb, HEALTH_ALPHA),
                    withAlpha(bottomRgb, HEALTH_ALPHA),
                    withAlpha(bottomRgb, HEALTH_ALPHA)
            );
        } else {
            int rgb = healthRgb(ratio);
            renderer.quad(innerX, fillY, innerW, fillH, withAlpha(rgb, HEALTH_ALPHA));
        }

        if (absorptionRatio > 0f) {
            float totalRatio = clamp01(current / max);
            double absorbH = innerH * Math.min(absorptionRatio, totalRatio);
            if (absorbH > 0.0) {
                double absorbY = innerY + (innerH - absorbH);
                renderer.quad(innerX, absorbY, innerW, absorbH, withAlpha(ABSORB_RGB, HEALTH_ALPHA));
            }
        }
    }

    private void drawOutline(Renderer2D renderer, double x1, double y1, double x2, double y2,
                             double thickness, ColorResolver colors) {
        double width = x2 - x1;
        double height = y2 - y1;
        if (width <= 0 || height <= 0 || thickness <= 0.0) return;

        if (isCornerBox()) {
            drawOutlineCorners(renderer, x1, y1, x2, y2, thickness, colors);
            return;
        }

        drawGradientQuad(renderer, x1, y1, x2, y1 + thickness, x1, y1, x2, y2, colors);
        drawGradientQuad(renderer, x1, y2 - thickness, x2, y2, x1, y1, x2, y2, colors);
        drawGradientQuad(renderer, x1, y1, x1 + thickness, y2, x1, y1, x2, y2, colors);
        drawGradientQuad(renderer, x2 - thickness, y1, x2, y2, x1, y1, x2, y2, colors);
    }

    private void drawOutlineCorners(Renderer2D renderer, double x1, double y1, double x2, double y2,
                                    double thickness, ColorResolver colors) {
        double width = x2 - x1;
        double height = y2 - y1;
        if (width <= 0 || height <= 0) return;

        double seg = Math.min(width, height) * CORNER_SEGMENT_FRACTION;
        double minSeg = thickness * 2.0;
        double segX = Math.min(width * 0.5, Math.max(seg, minSeg));
        double segY = Math.min(height * 0.5, Math.max(seg, minSeg));

        drawCornerSegment(renderer, x1, y1, x1 + segX, y1 + thickness, x1, y1, x2, y2, colors);
        drawCornerSegment(renderer, x1, y1, x1 + thickness, y1 + segY, x1, y1, x2, y2, colors);

        drawCornerSegment(renderer, x2 - segX, y1, x2, y1 + thickness, x1, y1, x2, y2, colors);
        drawCornerSegment(renderer, x2 - thickness, y1, x2, y1 + segY, x1, y1, x2, y2, colors);

        drawCornerSegment(renderer, x1, y2 - thickness, x1 + segX, y2, x1, y1, x2, y2, colors);
        drawCornerSegment(renderer, x1, y2 - segY, x1 + thickness, y2, x1, y1, x2, y2, colors);

        drawCornerSegment(renderer, x2 - segX, y2 - thickness, x2, y2, x1, y1, x2, y2, colors);
        drawCornerSegment(renderer, x2 - thickness, y2 - segY, x2, y2, x1, y1, x2, y2, colors);
    }

    private void drawCornerSegment(Renderer2D renderer,
                                   double sx1, double sy1, double sx2, double sy2,
                                   double boxX1, double boxY1, double boxX2, double boxY2,
                                   ColorResolver colors) {
        drawGradientQuad(renderer, sx1, sy1, sx2, sy2, boxX1, boxY1, boxX2, boxY2, colors);
    }

    private void drawGradientQuad(Renderer2D renderer,
                                  double sx1, double sy1, double sx2, double sy2,
                                  double boxX1, double boxY1, double boxX2, double boxY2,
                                  ColorResolver colors) {
        renderer.quad(
                sx1, sy1, sx2 - sx1, sy2 - sy1,
                colors.colorAt(sx1, sy1, boxX1, boxY1, boxX2, boxY2),
                colors.colorAt(sx2, sy1, boxX1, boxY1, boxX2, boxY2),
                colors.colorAt(sx2, sy2, boxX1, boxY1, boxX2, boxY2),
                colors.colorAt(sx1, sy2, boxX1, boxY1, boxX2, boxY2)
        );
    }

    private void add3DBox(MeshBuilder tris, MeshBuilder lines, AABB box, int argb) {
        int rgb = argb & 0x00FFFFFF;
        int fillAlpha = alpha255(box3dFillAlpha.get());
        int outlineAlpha = alpha255(box3dOutlineAlpha.get());
        float gradientStrength = Math.max(0.0f, Math.min(2.5f, box3dGradientStrength.get()));

        int c000 = boxGradientColor(rgb, box, box.minX, box.minY, box.minZ, fillAlpha, gradientStrength);
        int c001 = boxGradientColor(rgb, box, box.minX, box.minY, box.maxZ, fillAlpha, gradientStrength);
        int c010 = boxGradientColor(rgb, box, box.minX, box.maxY, box.minZ, fillAlpha, gradientStrength);
        int c011 = boxGradientColor(rgb, box, box.minX, box.maxY, box.maxZ, fillAlpha, gradientStrength);
        int c100 = boxGradientColor(rgb, box, box.maxX, box.minY, box.minZ, fillAlpha, gradientStrength);
        int c101 = boxGradientColor(rgb, box, box.maxX, box.minY, box.maxZ, fillAlpha, gradientStrength);
        int c110 = boxGradientColor(rgb, box, box.maxX, box.maxY, box.minZ, fillAlpha, gradientStrength);
        int c111 = boxGradientColor(rgb, box, box.maxX, box.maxY, box.maxZ, fillAlpha, gradientStrength);

        addFilledBox(tris, box, c000, c001, c010, c011, c100, c101, c110, c111);
        addOutlineBox(lines, box,
                withAlpha(c000, outlineAlpha),
                withAlpha(c001, outlineAlpha),
                withAlpha(c010, outlineAlpha),
                withAlpha(c011, outlineAlpha),
                withAlpha(c100, outlineAlpha),
                withAlpha(c101, outlineAlpha),
                withAlpha(c110, outlineAlpha),
                withAlpha(c111, outlineAlpha));
    }

    public boolean shouldRenderHitboxReplacement(Entity entity) {
        if (!isEnabled() || !is3DBox()) return false;
        if (entity == null) return false;
        if (entity instanceof Player player) {
            return shouldRenderPlayer(player) && shouldUseHitboxExpansion(entity);
        }
        return false;
    }

    private boolean shouldUseHitboxExpansion(Entity entity) {
        Hitbox hitbox = Modules.get(Hitbox.class);
        return hitbox != null && hitbox.isReplacementCandidate(entity);
    }

    public boolean shouldLiftNameTagsSection(Player player) {
        return isEnabled() && isNameTagsLiftBoxMode() && shouldRenderPlayer(player);
    }

    private boolean isNameTagsLiftBoxMode() {
        return MODE_FULL.equalsIgnoreCase(boxMode.get())
                || MODE_CORNERS.equalsIgnoreCase(boxMode.get());
    }

    private boolean isShaderSmokeFill() {
        return SHADER_FILL_SMOKE.equalsIgnoreCase(shaderFillStyle.get());
    }

    private int resolveShaderColor(ModeValue mode, RGBColorValue customColor) {
        if (mode != null) {
            String value = mode.get();
            if (SHADER_COLOR_THEME.equalsIgnoreCase(value)) {
                return Theme.theme().accent() & 0x00FFFFFF;
            }
            if (SHADER_COLOR_CUSTOM.equalsIgnoreCase(value) && customColor != null) {
                return customColor.getArgb() & 0x00FFFFFF;
            }
        }
        return 0xFFFFFF;
    }

    private boolean isCornerBox() {
        return MODE_CORNERS.equalsIgnoreCase(boxMode.get());
    }

    private boolean is3DBox() {
        return MODE_3D.equalsIgnoreCase(boxMode.get());
    }

    private boolean isShaderBox() {
        return MODE_SHADER.equalsIgnoreCase(boxMode.get());
    }

    @FunctionalInterface
    private interface ColorResolver {
        int colorAt(double x, double y, double boxX1, double boxY1, double boxX2, double boxY2);
    }

    private record RenderEntry(Entity entity, int color, double distSq) {
    }

    private record SearchLabelEntry(double distSq, ScreenSpaceOverlay2D.ScreenRect rect, int color,
                                    ScreenSpaceOverlay2D.LabelEntry label) {
    }

    private record ScreenBox(double minX, double minY, double maxX, double maxY) {
    }

    private record SolidColor(int color) implements ColorResolver {
        static SolidColor of(int color) {
            return new SolidColor(color);
        }

        @Override
        public int colorAt(double x, double y, double boxX1, double boxY1, double boxX2, double boxY2) {
            return color;
        }
    }

    private record Gradient(int topLeft, int topRight, int bottomRight, int bottomLeft) implements ColorResolver {
        private static double safeRatio(double value, double length) {
            if (length <= 0.0001) return 0.0;
            if (value <= 0.0) return 0.0;
            if (value >= length) return 1.0;
            return value / length;
        }

        @Override
        public int colorAt(double x, double y, double boxX1, double boxY1, double boxX2, double boxY2) {
            double tx = safeRatio(x - boxX1, boxX2 - boxX1);
            double ty = safeRatio(y - boxY1, boxY2 - boxY1);

            int top = mixArgb(topLeft, topRight, (float) tx);
            int bottom = mixArgb(bottomLeft, bottomRight, (float) tx);
            return mixArgb(top, bottom, (float) ty);
        }
    }

    private final class ShaderEspPass implements PostProcessPass {
        @Override
        public boolean isActive() {
            return ESP.this.isEnabled()
                    && ESP.this.isShaderBox()
                    && mc.player != null
                    && mc.level != null;
        }

        @Override
        public Phase getPhase() {
            return Phase.PRE_HAND;
        }

        @Override
        public boolean render(GpuTextureView src, GpuTextureView dst, float tickDelta) {
            return ESP.this.renderShaderEsp(src, dst, tickDelta);
        }
    }
}

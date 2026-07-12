/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.visuals;


import combatant.client.features.theme.Theme;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import combatant.client.config.values.*;
import net.irisshaders.iris.mixinterface.ItemInHandInterface;
import net.irisshaders.iris.pathways.HandRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Util;
import org.joml.Matrix4fc;
import combatant.client.features.theme.Themes;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.Modules;
import combatant.client.mixins.accessors.GameRendererAccessor;
import combatant.client.mixins.iris.IrisHandRendererAccessor;
import combatant.client.render.engine.pipeline.CombatantRenderPipelines;
import combatant.client.render.engine.postprocess.PostProcessManager;
import combatant.client.render.engine.postprocess.PostProcessPass;
import combatant.client.render.engine.renderer.FullScreenRenderer;
import combatant.client.render.engine.uniform.impl.HandGlassUniforms;
import combatant.client.render.engine.uniform.impl.HandMetallicUniforms;
import combatant.client.render.engine.uniform.impl.HandSmokeUniforms;
import combatant.client.render.iris.IrisHandMaskState;
import combatant.client.render.iris.IrisRuntime;
import combatant.client.render.engine.RenderState;

//todo Description
@ModuleInfo(id = "chams", displayName = "Chams", category = ModuleCategory.VISUALS)
public class Chams extends Module {

    private static final String SETTING_HANDS = "hands";
    private static final String SETTING_MODE = "mode";
    private static final String SETTING_FILL = "fill";
    private static final String SETTING_VISUAL_COLOR_SOURCE = "visual_color_source";
    private static final String SETTING_EFFECT_COLOR_SOURCE = "effect_color_source";
    private static final String SETTING_GLOW = "glow";
    private static final String SETTING_GLOW_COLOR = "glow_color";
    private static final String SETTING_GLOW_ALPHA = "glow_alpha";
    private static final String SETTING_GLOW_STRENGTH = "glow_strength";
    private static final String SETTING_SHADOW = "shadow";
    private static final String SETTING_SHADOW_COLOR = "shadow_color";
    private static final String SETTING_SHADOW_ALPHA = "shadow_alpha";
    private static final String SETTING_SHADOW_STRENGTH = "shadow_strength";
    private static final String SETTING_EDGE_WIDTH = "edge_width";
    private static final String SETTING_QUALITY = "quality";
    private static final String SETTING_SMOKE_OCTAVES = "smoke_octaves";
    private static final String SETTING_SMOKE_SPEED = "smoke_speed";
    private static final String SETTING_SMOKE_SCALE = "smoke_scale";
    private static final String SETTING_SMOKE_CONTRAST = "smoke_contrast";
    private static final String SETTING_SMOKE_SWIRL = "smoke_swirl";
    private static final String SETTING_SMOKE_DENSITY = "smoke_density";
    private static final String SETTING_METALLIC_BASE = "metallic_base";
    private static final String SETTING_METALLIC_HIGHLIGHT = "metallic_highlight";
    private static final String SETTING_METALLIC_INTENSITY = "metallic_intensity";
    private static final String SETTING_METALLIC_SHARPNESS = "metallic_sharpness";
    private static final String SETTING_METALLIC_EDGE = "metallic_edge";
    private static final String SETTING_METALLIC_SWEEP_SPEED = "metallic_sweep_speed";
    private static final String SETTING_METALLIC_SWEEP_SCALE = "metallic_sweep_scale";
    private static final String SETTING_METALLIC_BRUSHED_LINES = "metallic_brushed_lines";
    private static final String SETTING_METALLIC_FLAKES = "metallic_flakes";
    private static final String SETTING_METALLIC_PRISM = "metallic_prism";
    private final Minecraft mc = Minecraft.getInstance();
    private final ModeValue mode =
            modeSetting("handChamsMode", SETTING_MODE, "Smoke", "Smoke", "Metallic", "Glass");

    private final BooleanValue hands =
            bool("chamsHands", SETTING_HANDS, true);

    private final RGBAColorValue fillColor =
            color("handChamsFillColor", SETTING_FILL, "#66FFFFFF");

    private final ModeValue visualColorSource =
            visibleWhen(modeSetting("handChamsVisualColorSource", SETTING_VISUAL_COLOR_SOURCE, "Custom", "Custom", "Theme"), this::isHandEffectMode);
    private final RGBColorValue metallicBase =
            visibleWhen(colorNoAlpha("handChamsMetallicBase", SETTING_METALLIC_BASE, "#B0B4BA"), this::isCustomMetallicVisualColor);
    private final RGBColorValue metallicHighlight =
            visibleWhen(colorNoAlpha("handChamsMetallicHighlight", SETTING_METALLIC_HIGHLIGHT, "#FFFFFF"), this::isCustomMetallicVisualColor);
    private final ModeValue effectColorSource =
            visibleWhen(modeSetting("handChamsEffectColorSource", SETTING_EFFECT_COLOR_SOURCE, "Theme", "Material", "Theme", "Custom"), this::isHandEffectMode);
    private final RGBColorValue glowColor =
            visibleWhen(colorNoAlpha("handChamsGlowColor", SETTING_GLOW_COLOR, "#87DFFF"), this::isCustomEffectColor);
    private final RGBColorValue shadowColor =
            visibleWhen(colorNoAlpha("handChamsShadowColor", SETTING_SHADOW_COLOR, "#10131A"), this::isCustomEffectColor);
    private final BooleanValue glow =
            visibleWhen(bool("handChamsGlow", SETTING_GLOW, true), this::isHandEffectMode);
    private final NumberValue<Float> glowAlpha =
            visibleWhen(num("handChamsGlowAlpha", SETTING_GLOW_ALPHA, 0.72f, 0.0f, 1.0f), this::isGlowVisible);
    private final NumberValue<Float> glowStrength =
            visibleWhen(num("handChamsGlowStrength", SETTING_GLOW_STRENGTH, 1.15f, 0.0f, 6.0f), this::isGlowVisible);
    private final BooleanValue shadow =
            visibleWhen(bool("handChamsShadow", SETTING_SHADOW, true), this::isHandEffectMode);
    private final NumberValue<Float> shadowAlpha =
            visibleWhen(num("handChamsShadowAlpha", SETTING_SHADOW_ALPHA, 0.48f, 0.0f, 1.0f), this::isShadowVisible);
    private final NumberValue<Float> shadowStrength =
            visibleWhen(num("handChamsShadowStrength", SETTING_SHADOW_STRENGTH, 1.0f, 0.0f, 4.0f), this::isShadowVisible);
    private final NumberValue<Float> edgeWidth =
            visibleWhen(num("handChamsEdgeWidth", SETTING_EDGE_WIDTH, 10.0f, 0.0f, 36.0f), this::isHandEffectMode);
    private final NumberValue<Integer> quality =
            visibleWhen(num("handChamsQuality", SETTING_QUALITY, 2, 1, 4), this::isHandEffectMode);
    private final NumberValue<Integer> smokeOctaves =
            visibleWhen(num("handChamsSmokeOctaves", SETTING_SMOKE_OCTAVES, 4, 1, 6), this::isSmokeMode);
    private final NumberValue<Float> smokeSpeed =
            visibleWhen(num("chamsSmokeSpeed", SETTING_SMOKE_SPEED, 0.72f, 0.0f, 3.0f), this::isSmokeMode);
    private final NumberValue<Float> smokeScale =
            visibleWhen(num("handChamsSmokeScale", SETTING_SMOKE_SCALE, 4.4f, 1.0f, 12.0f), this::isSmokeMode);
    private final NumberValue<Float> smokeContrast =
            visibleWhen(num("handChamsSmokeContrast", SETTING_SMOKE_CONTRAST, 1.12f, 0.35f, 2.4f), this::isSmokeMode);
    private final NumberValue<Float> smokeSwirl =
            visibleWhen(num("handChamsSmokeSwirl", SETTING_SMOKE_SWIRL, 0.85f, 0.0f, 2.5f), this::isSmokeMode);
    private final NumberValue<Float> smokeDensity =
            visibleWhen(num("handChamsSmokeDensity", SETTING_SMOKE_DENSITY, 1.0f, 0.2f, 2.5f), this::isSmokeMode);
    private final NumberValue<Float> metallicIntensity =
            visibleWhen(num("handChamsMetallicIntensity", SETTING_METALLIC_INTENSITY, 1.35f, 0.0f, 5.0f), this::isMetallicMode);
    private final NumberValue<Float> metallicSharpness =
            visibleWhen(num("handChamsMetallicSharpness", SETTING_METALLIC_SHARPNESS, 2.2f, 0.35f, 10.0f), this::isMetallicMode);
    private final NumberValue<Float> metallicEdge =
            visibleWhen(num("handChamsMetallicEdge", SETTING_METALLIC_EDGE, 1.25f, 0.0f, 4.0f), this::isMetallicMode);
    private final NumberValue<Float> metallicSweepSpeed =
            visibleWhen(num("handChamsMetallicSweepSpeed", SETTING_METALLIC_SWEEP_SPEED, 1.15f, 0.0f, 5.0f), this::isMetallicMode);
    private final NumberValue<Float> metallicSweepScale =
            visibleWhen(num("handChamsMetallicSweepScale", SETTING_METALLIC_SWEEP_SCALE, 10.0f, 1.0f, 36.0f), this::isMetallicMode);
    private final NumberValue<Float> metallicBrushedLines =
            visibleWhen(num("handChamsMetallicBrushedLines", SETTING_METALLIC_BRUSHED_LINES, 0.55f, 0.0f, 1.0f), this::isMetallicMode);
    private final NumberValue<Float> metallicFlakes =
            visibleWhen(num("handChamsMetallicFlakes", SETTING_METALLIC_FLAKES, 0.25f, 0.0f, 1.0f), this::isMetallicMode);
    private final NumberValue<Float> metallicPrism =
            visibleWhen(num("handChamsMetallicPrism", SETTING_METALLIC_PRISM, 0.18f, 0.0f, 1.0f), this::isMetallicMode);
    private final PostProcessPass handsPass = new HandsPass();
    private TextureTarget handMask;
    private GpuSampler handMaskSampler;
    private int bufferW = -1;
    private int bufferH = -1;
    private boolean maskReady;
    private RenderBuffers irisHandRenderBuffers;
    private FeatureRenderDispatcher irisHandFeatureDispatcher;

    {
        PostProcessManager.register(handsPass);
    }

    private static float channel(int argb, int shift) {
        return ((argb >> shift) & 0xFF) / 255.0f;
    }

    private static float clamp01(float v) {
        if (v < 0.0f) return 0.0f;
        if (v > 1.0f) return 1.0f;
        return v;
    }

    private static int forceOpaque(int argb) {
        return 0xFF000000 | (argb & 0x00FFFFFF);
    }

    private static int mixRgb(int a, int b, float t) {
        t = clamp01(t);
        int ar = (a >>> 16) & 0xFF;
        int ag = (a >>> 8) & 0xFF;
        int ab = a & 0xFF;
        int br = (b >>> 16) & 0xFF;
        int bg = (b >>> 8) & 0xFF;
        int bb = b & 0xFF;
        int rr = Math.round(ar + (br - ar) * t);
        int rg = Math.round(ag + (bg - ag) * t);
        int rb = Math.round(ab + (bb - ab) * t);
        return 0xFF000000 | (rr << 16) | (rg << 8) | rb;
    }

    public boolean isActive() {
        return isEnabled() && mc.player != null && mc.level != null;
    }

    @Override
    public void onDisable() {
        maskReady = false;
        closeStandaloneHandRenderer();
    }

    private FeatureRenderDispatcher getStandaloneHandFeatureDispatcher() {
        if (irisHandFeatureDispatcher != null) {
            return irisHandFeatureDispatcher;
        }
        if (mc.gameRenderer == null || mc.getModelManager() == null || mc.getAtlasManager() == null || mc.font == null) {
            return null;
        }
        irisHandRenderBuffers = new RenderBuffers(1);
        irisHandFeatureDispatcher = new FeatureRenderDispatcher(
                irisHandRenderBuffers,
                mc.getModelManager(),
                mc.getAtlasManager(),
                mc.font,
                mc.gameRenderer.gameRenderState()
        );
        return irisHandFeatureDispatcher;
    }

    private void endStandaloneHandFrame() {
        if (irisHandRenderBuffers != null) {
            irisHandRenderBuffers.endFrame();
        }
    }

    private void closeStandaloneHandRenderer() {
        if (irisHandFeatureDispatcher != null) {
            irisHandFeatureDispatcher.close();
            irisHandFeatureDispatcher = null;
        }
        if (irisHandRenderBuffers != null) {
            irisHandRenderBuffers.close();
            irisHandRenderBuffers = null;
        }
    }

    public boolean renderHandMask(GameRenderer renderer, CameraRenderState cameraRenderState, float tickDelta, Matrix4fc positionMatrix) {
        if (!isEnabled() || mc.player == null || mc.level == null) return false;
        if (mc.gameMode == null) return false;
        if (!hands.get() || !shouldRenderHand()) return false;
        if (!mc.options.getCameraType().isFirstPerson()) return false;

        maskReady = false;
        ensureBuffers();
        if (handMask == null) return false;

        if (mc.gameRenderer.mainRenderTarget() == null) return false;

        var colorTex = handMask.getColorTexture();
        if (colorTex == null) return false;

        var encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.clearColorTexture(colorTex, new org.joml.Vector4f(0.0f, 0.0f, 0.0f, 0.0f));

        var depthTex = handMask.getDepthTexture();
        if (depthTex != null) {
            encoder.clearDepthTexture(depthTex, 0.0);
        }

        GpuTextureView prevColor = RenderSystem.outputColorTextureOverride;
        GpuTextureView prevDepth = RenderSystem.outputDepthTextureOverride;
        boolean prevRendering3D = RenderState.rendering3D;

        RenderSystem.outputColorTextureOverride = handMask.getColorTextureView();
        RenderSystem.outputDepthTextureOverride = handMask.getDepthTextureView();
        RenderState.rendering3D = true;

        try {
            ((GameRendererAccessor) renderer).invokeRenderHand(cameraRenderState, tickDelta, positionMatrix);
        } finally {
            RenderState.rendering3D = prevRendering3D;
            RenderSystem.outputColorTextureOverride = prevColor;
            RenderSystem.outputDepthTextureOverride = prevDepth;
        }

        maskReady = true;
        return true;
    }

    public boolean renderPreparedHandScene(FeatureRenderDispatcher dispatcher, SubmitNodeStorage storage) {
        if (!isEnabled() || mc.player == null || mc.level == null) return false;
        if (dispatcher == null || storage == null) return false;
        if (!hands.get() || !shouldRenderHand()) return false;
        if (!mc.options.getCameraType().isFirstPerson()) return false;

        maskReady = false;
        ensureBuffers();
        if (handMask == null || handMask.getColorTexture() == null) return false;

        var encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.clearColorTexture(handMask.getColorTexture(), new org.joml.Vector4f(0.0f, 0.0f, 0.0f, 0.0f));
        if (handMask.getDepthTexture() != null) {
            encoder.clearDepthTexture(handMask.getDepthTexture(), 0.0);
        }

        try (FeatureRenderDispatcher.PreparedFrame prepared = dispatcher.prepareFrame(storage)) {
            GpuTextureView prevColor = RenderSystem.outputColorTextureOverride;
            GpuTextureView prevDepth = RenderSystem.outputDepthTextureOverride;
            boolean prevRendering3D = RenderState.rendering3D;
            RenderSystem.outputColorTextureOverride = handMask.getColorTextureView();
            RenderSystem.outputDepthTextureOverride = handMask.getDepthTextureView();
            RenderState.rendering3D = true;
            try {
                executePreparedHandFrame(prepared);
            } finally {
                RenderState.rendering3D = prevRendering3D;
                RenderSystem.outputColorTextureOverride = prevColor;
                RenderSystem.outputDepthTextureOverride = prevDepth;
            }

            maskReady = true;
            executePreparedHandFrame(prepared);
        }

        return true;
    }

    private static void executePreparedHandFrame(FeatureRenderDispatcher.PreparedFrame prepared) {
        prepared.executeSolid();
        prepared.executeTranslucent();
        prepared.executeTranslucentAfterTerrain();
        prepared.executeAlwaysOnTop();
    }

    public boolean renderIrisHandMask(GameRenderer renderer,
                                      CameraRenderState cameraRenderState,
                                      Matrix4fc positionMatrix,
                                      float tickDelta) {
        if (!IrisRuntime.isShaderpackRendererActive() || !isEnabled() || mc.player == null || mc.level == null) {
            return false;
        }
        if (!hands.get() || !shouldRenderHand() || !mc.options.getCameraType().isFirstPerson()) {
            return false;
        }
        if (renderer == null || cameraRenderState == null || positionMatrix == null) {
            return false;
        }

        FeatureRenderDispatcher dispatcher = getStandaloneHandFeatureDispatcher();
        if (dispatcher == null) {
            return false;
        }

        maskReady = false;
        ensureBuffers();
        if (handMask == null || handMask.getColorTexture() == null) {
            return false;
        }

        var encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.clearColorTexture(handMask.getColorTexture(), new org.joml.Vector4f(0.0f, 0.0f, 0.0f, 0.0f));
        if (handMask.getDepthTexture() != null) {
            encoder.clearDepthTexture(handMask.getDepthTexture(), 0.0);
        }

        GpuTextureView prevColor = RenderSystem.outputColorTextureOverride;
        GpuTextureView prevDepth = RenderSystem.outputDepthTextureOverride;
        boolean prevRendering3D = RenderState.rendering3D;
        RenderSystem.outputColorTextureOverride = handMask.getColorTextureView();
        RenderSystem.outputDepthTextureOverride = handMask.getDepthTextureView();
        RenderState.rendering3D = true;
        RenderSystem.backupProjectionMatrix();
        try {
            renderIrisHandMaskPhase(renderer, cameraRenderState, positionMatrix, dispatcher, tickDelta, true);
            renderIrisHandMaskPhase(renderer, cameraRenderState, positionMatrix, dispatcher, tickDelta, false);
        } finally {
            ((IrisHandRendererAccessor) HandRenderer.INSTANCE).combatant$setRenderingSolid(false);
            RenderSystem.restoreProjectionMatrix();
            RenderState.rendering3D = prevRendering3D;
            RenderSystem.outputColorTextureOverride = prevColor;
            RenderSystem.outputDepthTextureOverride = prevDepth;
        }

        maskReady = true;
        return true;
    }

    private void renderIrisHandMaskPhase(GameRenderer renderer,
                                          CameraRenderState cameraRenderState,
                                          Matrix4fc positionMatrix,
                                          FeatureRenderDispatcher dispatcher,
                                          float tickDelta,
                                          boolean solidPhase) {
        IrisHandRendererAccessor irisHand = (IrisHandRendererAccessor) HandRenderer.INSTANCE;
        irisHand.combatant$setRenderingSolid(solidPhase);

        PoseStack handMatrices = irisHand.combatant$invokeSetupGlState(renderer, cameraRenderState, positionMatrix, tickDelta);
        handMatrices.pushPose();
        var modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.set(handMatrices.last().pose());

        SubmitNodeStorage handSubmits = new SubmitNodeStorage();
        IrisHandMaskState.renderBothPhases(() ->
                ((ItemInHandInterface) renderer.itemInHandRenderer).iris$renderHandsWithCustomRenderer(
                        HandRenderer.INSTANCE,
                        tickDelta,
                        new PoseStack(),
                        handSubmits,
                        mc.player,
                        mc.getEntityRenderDispatcher().getPackedLightCoords(mc.player, tickDelta)
                )
        );
        try {
            dispatcher.renderAllFeatures(handSubmits);
        } finally {
            modelView.popMatrix();
            handMatrices.popPose();
            endStandaloneHandFrame();
        }
    }

    private boolean shouldRenderHand() {
        Freecam fc = Modules.get(Freecam.class);
        return fc == null || !fc.isEnabled() || fc.renderHand();
    }

    private void ensureBuffers() {
        int w = mc.getWindow().getWidth();
        int h = mc.getWindow().getHeight();
        if (w <= 0 || h <= 0) return;

        if (handMask == null) {
            handMask = new TextureTarget("combatant-hand-mask", w, h, true, GpuFormat.RGBA8_UNORM);
            bufferW = w;
            bufferH = h;
        } else if (w != bufferW || h != bufferH) {
            handMask.resize(w, h);
            bufferW = w;
            bufferH = h;
        }
    }

    private GpuSampler getHandMaskSampler() {
        if (handMaskSampler == null) {
            handMaskSampler = RenderSystem.getSamplerCache().getSampler(
                    AddressMode.CLAMP_TO_EDGE,
                    AddressMode.CLAMP_TO_EDGE,
                    FilterMode.NEAREST,
                    FilterMode.NEAREST,
                    false
            );
        }
        return handMaskSampler;
    }

    private boolean isSmokeMode() {
        return "Smoke".equals(mode.get());
    }

    private boolean isMetallicMode() {
        return "Metallic".equals(mode.get());
    }

    private boolean isHandEffectMode() {
        return isSmokeMode() || isMetallicMode();
    }

    private boolean isGlowVisible() {
        return isHandEffectMode() && glow.get();
    }

    private boolean isShadowVisible() {
        return isHandEffectMode() && shadow.get();
    }

    private boolean isCustomEffectColor() {
        return isHandEffectMode() && "Custom".equals(effectColorSource.get());
    }

    private boolean isCustomMetallicVisualColor() {
        return isMetallicMode() && "Custom".equals(visualColorSource.get());
    }

    public boolean isGlassMode() {
        return "Glass".equals(mode.get());
    }

    private int materialFillRgb() {
        int fill = fillColor.getArgb();
        if ("Theme".equals(visualColorSource.get())) {
            Themes.Theme theme = Theme.theme();
            return forceOpaque(mixRgb(theme.accent(), theme.textPrimary(), 0.18f));
        }
        return forceOpaque(fill);
    }

    private int metallicBaseRgb() {
        if ("Theme".equals(visualColorSource.get())) {
            Themes.Theme theme = Theme.theme();
            return forceOpaque(mixRgb(theme.surfaceHover(), theme.accent(), 0.42f));
        }
        return forceOpaque(metallicBase.getArgb());
    }

    private int metallicHighlightRgb() {
        if ("Theme".equals(visualColorSource.get())) {
            Themes.Theme theme = Theme.theme();
            return forceOpaque(mixRgb(theme.textPrimary(), theme.accentSoft(), 0.28f));
        }
        return forceOpaque(metallicHighlight.getArgb());
    }

    private int glowRgb(int materialRgb) {
        String source = effectColorSource.get();
        if ("Custom".equals(source)) return forceOpaque(glowColor.getArgb());
        if ("Theme".equals(source)) return forceOpaque(Theme.theme().accentSoft());
        return forceOpaque(mixRgb(materialRgb, 0xFFFFFFFF, 0.25f));
    }

    private int shadowRgb(int materialRgb) {
        String source = effectColorSource.get();
        if ("Custom".equals(source)) return forceOpaque(shadowColor.getArgb());
        if ("Theme".equals(source))
            return forceOpaque(mixRgb(Theme.theme().windowBg(), Theme.theme().accent(), 0.08f));
        return forceOpaque(mixRgb(materialRgb, 0xFF05070C, 0.72f));
    }

    private boolean renderHands(GpuTextureView src, GpuTextureView dst, float tickDelta) {
        if (!maskReady) return false;
        if (handMask == null) {
            maskReady = false;
            return false;
        }

        var maskView = handMask.getColorTextureView();
        if (maskView == null) {
            maskReady = false;
            return false;
        }

        float rawTime = getRawTime();

        if ("Glass".equals(mode.get())) {
            int fillArgb = fillColor.getArgb();

            float fillR = channel(fillArgb, 16);
            float fillG = channel(fillArgb, 8);
            float fillB = channel(fillArgb, 0);
            float fillA = channel(fillArgb, 24);

            int w = mc.getWindow().getWidth();
            int h = mc.getWindow().getHeight();
            float glassAlpha = clamp01(fillA);
            float glassStrength = Math.max(0.0f, fillA * 4.0f);
            float glassLevel = Math.min(glassStrength, 4.0f);

            // Hand glass is intentionally blur-first, like Renderer2D liquid glass.
            // The item is already rendered in src; this pass only adds frosted refraction on top.
            float edgeWidth = 12.0f + glassLevel * 2.25f;
            float refractionPx = 5.5f + glassLevel * 2.35f;
            float hazeStrength = 0.72f + Math.min(glassStrength, 3.0f) * 0.085f;
            float frostBlurPx = 5.0f + glassLevel * 1.65f;
            float bodyFrost = 0.48f + Math.min(glassStrength, 3.0f) * 0.105f;
            float chromaticPx = 1.65f + glassLevel * 0.42f;
            float edgeRefractionMul = 1.15f + glassLevel * 0.11f;
            float clarity = 0.08f;

            HandGlassUniforms.update(
                    w, h, rawTime, edgeWidth,
                    fillR, fillG, fillB, glassAlpha,
                    glassStrength, refractionPx, hazeStrength, frostBlurPx,
                    bodyFrost, chromaticPx, edgeRefractionMul, clarity
            );

            FullScreenRenderer.begin("Combatant Fullscreen Pass")
                    .attachment(dst)
                    .pipeline(CombatantRenderPipelines.HAND_GLASS)
                    .uniform("HandGlass", HandGlassUniforms.get())
                    .sampler("u_Src", src, PostProcessManager.getSampler())
                    .sampler("u_Mask", maskView, getHandMaskSampler())
                    .end();
        } else if ("Metallic".equals(mode.get())) {
            int fillArgb = fillColor.getArgb();
            int baseRgb = metallicBaseRgb();
            int hiRgb = metallicHighlightRgb();
            int glowRgb = glowRgb(baseRgb);
            int shadowRgb = shadowRgb(baseRgb);

            float fillA = channel(fillArgb, 24);
            float glowA = glow.get() ? glowAlpha.get() : 0.0f;
            float shadowA = shadow.get() ? shadowAlpha.get() : 0.0f;

            HandMetallicUniforms.update(
                    channel(baseRgb, 16), channel(baseRgb, 8), channel(baseRgb, 0), fillA,
                    channel(hiRgb, 16), channel(hiRgb, 8), channel(hiRgb, 0), 1.0f,
                    channel(glowRgb, 16), channel(glowRgb, 8), channel(glowRgb, 0), glowA,
                    channel(shadowRgb, 16), channel(shadowRgb, 8), channel(shadowRgb, 0), shadowA,
                    metallicIntensity.get(), metallicSharpness.get(), metallicEdge.get(), rawTime,
                    metallicSweepSpeed.get(), metallicSweepScale.get(), metallicBrushedLines.get(), metallicFlakes.get(),
                    glowStrength.get(), shadowStrength.get(), edgeWidth.get(), metallicPrism.get()
            );

            FullScreenRenderer.begin("Combatant Fullscreen Pass")
                    .attachment(dst)
                    .pipeline(CombatantRenderPipelines.HAND_METALLIC)
                    .uniform("HandMetallic", HandMetallicUniforms.get())
                    .sampler("u_Src", src, PostProcessManager.getSampler())
                    .sampler("u_Mask", maskView, getHandMaskSampler())
                    .end();
        } else {
            int fillArgb = fillColor.getArgb();
            int fillRgb = materialFillRgb();
            int glowRgb = glowRgb(fillRgb);
            int shadowRgb = shadowRgb(fillRgb);

            float fillA = channel(fillArgb, 24);
            float glowA = glow.get() ? glowAlpha.get() : 0.0f;
            float shadowA = shadow.get() ? shadowAlpha.get() : 0.0f;

            int w = mc.getWindow().getWidth();
            int h = mc.getWindow().getHeight();

            HandSmokeUniforms.update(
                    channel(fillRgb, 16), channel(fillRgb, 8), channel(fillRgb, 0), fillA,
                    channel(glowRgb, 16), channel(glowRgb, 8), channel(glowRgb, 0), glowA,
                    channel(shadowRgb, 16), channel(shadowRgb, 8), channel(shadowRgb, 0), shadowA,
                    edgeWidth.get(), quality.get(), smokeOctaves.get(), rawTime * smokeSpeed.get(),
                    w, h, smokeScale.get(), smokeContrast.get(),
                    smokeSwirl.get(), glowStrength.get(), shadowStrength.get(), smokeDensity.get()
            );

            FullScreenRenderer.begin("Combatant Fullscreen Pass")
                    .attachment(dst)
                    .pipeline(CombatantRenderPipelines.HAND_SMOKE)
                    .uniform("HandSmoke", HandSmokeUniforms.get())
                    .sampler("u_Src", src, PostProcessManager.getSampler())
                    .sampler("u_Mask", maskView, getHandMaskSampler())
                    .end();
        }

        maskReady = false;
        return true;
    }

    private float getRawTime() {
        return (float) (Util.getMillis() / 1000.0);
    }

    private final class HandsPass implements PostProcessPass {
        @Override
        public boolean isActive() {
            return Chams.this.isEnabled()
                    && mc.player != null
                    && mc.level != null
                    && hands.get()
                    && mc.options.getCameraType().isFirstPerson();
        }

        @Override
        public Phase getPhase() {
            return Phase.POST_HAND;
        }

        @Override
        public boolean render(GpuTextureView src, GpuTextureView dst, float tickDelta) {
            if (!Chams.this.isEnabled() || mc.player == null || mc.level == null) return false;
            if (!hands.get() || !mc.options.getCameraType().isFirstPerson()) return false;
            return Chams.this.renderHands(src, dst, tickDelta);
        }
    }

}

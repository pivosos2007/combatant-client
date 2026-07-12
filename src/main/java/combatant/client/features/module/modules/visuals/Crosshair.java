/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.visuals;

import combatant.client.features.module.*;
import combatant.client.features.module.Module;
import combatant.client.render.engine.core.ViewportContext;
import combatant.client.util.item.FoodUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.RGBAColorValue;
import combatant.client.config.common.CommonSettingSchemas;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.AttackEntityEvent;
import combatant.client.events.impl.PacketEvent;
import combatant.client.features.gui.hud.HudRenderUtil;
import combatant.client.features.gui.hud.draggable.impl.Itemizer;
import combatant.client.features.module.*;
import combatant.client.features.module.modules.combat.KillAura;
import combatant.client.features.module.modules.combat.Reach;
import combatant.client.features.relations.CategoryService;
import combatant.client.features.relations.EntityFilters;
import combatant.client.features.relations.PlayerRelations;
import combatant.client.mixininterface.IPlayerAttackCooldown;
import combatant.client.render.engine.animation.AnimatedRenderColors;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.math.ColorMath;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.text.FontInfo;
import combatant.client.render.engine.text.Fonts;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.util.combat.VulcanReachController;

import java.util.List;
import java.util.Locale;

//todo Description
@ModuleInfo(
        id = "crosshair",
        displayName = "Crosshair",
        category = ModuleCategory.VISUALS
)
public class Crosshair extends Module {

    private static final int DEFAULT_COLOR = 0xFFFFFFFF;
    private static final int OUTLINE_COLOR = 0xFF000000;
    private static final int RIPTIDE_BLOCK_COLOR = 0xFFFF3B3B;
    private static final float OUTLINE_EXPAND = 0.5f;
    private static final float GAP_SMOOTHING = 0.15f;
    private static final float COLOR_SMOOTH_SPEED = 14.0f;
    private static final float ORBIZ_PROGRESS_SMOOTH_SPEED = 22.0f;
    private static final float COOLDOWN_LEAD = 0.06f;
    private static final float RIPTIDE_CROSS_LENGTH_FACTOR = 0.65f;
    private static final float VULCAN_REACH_FONT_SIZE = 14.0f;
    private static final int VULCAN_REACH_PRIMARY = 0xF2FFFFFF;
    private static final int VULCAN_REACH_SECONDARY = 0xD8D7DEE8;
    private final Minecraft mc = Minecraft.getInstance();
    private final EnumValue<CrosshairType> crosshairType =
            enumSetting("crosshairType", "type", CrosshairType.CLASSIC);
    private final EnumValue<OrbizMotionMode> orbizMotion =
            visibleWhen(enumSetting("crosshairOrbizMotion", "orbiz_motion", OrbizMotionMode.DYNAMIC), this::isOrbiz);
    private final BooleanValue showOutline = visibleWhen(
            bool("crosshairOutline", "outline", true),
            this::isClassicCrosshair
    );
    private final BooleanValue dynamicGap = visibleWhen(
            bool("crosshairDynamicGap", "dynamic_gap", true),
            this::isClassicCrosshair
    );
    private final BooleanValue highlightTarget = bool("crosshairHighlightTarget", "highlight_target", true);
    private final NumberValue<Double> thickness = visibleWhen(
            num("crosshairThickness", "thickness", 0.5, 0.5, 1.0),
            this::isClassicCrosshair
    );
    private final NumberValue<Double> length = visibleWhen(
            num("crosshairLength", "length", 3.0, 1.0, 10.0),
            this::isClassicCrosshair
    );
    private final NumberValue<Double> baseGap = visibleWhen(
            num("crosshairBaseGap", "base_gap", 4.0, 0.0, 10.0),
            this::isClassicCrosshair
    );
    private final NumberValue<Double> maxGapIncrease = visibleWhen(
            num("crosshairMaxGapIncrease", "max_gap_increase", 7.0, 0.0, 15.0),
            () -> isClassicCrosshair() && dynamicGap.get()
    );
    private final NumberValue<Double> orbizRadius = visibleWhen(
            num("crosshairOrbizRadius", "orbiz_radius", 5.0, 2.0, 5.0),
            this::isOrbiz
    );
    private final NumberValue<Double> orbizThickness = visibleWhen(
            num("crosshairOrbizThickness", "orbiz_thickness", 1.35, 0.5, 5.0),
            this::isOrbiz
    );
    private final NumberValue<Double> orbizSoftness = visibleWhen(
            num("crosshairOrbizSoftness", "orbiz_softness", 0.35, 0.0, 2.0),
            this::isOrbiz
    );
    private final NumberValue<Double> orbizGlow = visibleWhen(
            num("crosshairOrbizGlow", "orbiz_glow", 5.5, 0.0, 20.0),
            this::isOrbiz
    );
    private final NumberValue<Double> orbizGlowStrength = visibleWhen(
            num("crosshairOrbizGlowStrength", "orbiz_glow_strength", 0.72, 0.0, 2.0),
            this::isOrbiz
    );
    private final NumberValue<Double> orbizCooldownMultiplier = visibleWhen(
            num("crosshairOrbizCooldownMultiplier", "orbiz_cooldown_multiplier", 1.0, 0.1, 1.5),
            this::isOrbiz
    );
    private final NumberValue<Double> orbizTrackAlpha = visibleWhen(
            num("crosshairOrbizTrackAlpha", "orbiz_track_alpha", 0.24, 0.0, 1.0),
            this::isOrbiz
    );
    private final NumberValue<Double> orbizBodyAlpha = visibleWhen(
            num("crosshairOrbizBodyAlpha", "orbiz_body_alpha", 0.92, 0.0, 1.0),
            this::isOrbiz
    );
    private final NumberValue<Double> orbizHeadBoost = visibleWhen(
            num("crosshairOrbizHeadBoost", "orbiz_head_boost", 0.30, 0.0, 1.0),
            this::isOrbiz
    );
    private final NumberValue<Double> orbizAngularCycles = visibleWhen(
            num("crosshairOrbizAngularCycles", "orbiz_angular_cycles", 1.0, 0.25, 4.0),
            this::isOrbiz
    );
    private final EnumValue<AnimatedRenderColors.Mode> orbizColorMode = visibleWhen(
            common(enumSetting(
                    "crosshairOrbizColorMode",
                    "orbiz_color_mode",
                    AnimatedRenderColors.Mode.RAINBOW,
                    AnimatedRenderColors.Mode.STATIC,
                    AnimatedRenderColors.Mode.RAINBOW,
                    AnimatedRenderColors.Mode.LIGHT_RAINBOW,
                    AnimatedRenderColors.Mode.SKY,
                    AnimatedRenderColors.Mode.FADE,
                    AnimatedRenderColors.Mode.DOUBLE_COLOR,
                    AnimatedRenderColors.Mode.ANALOGOUS,
                    AnimatedRenderColors.Mode.THEME
            ), CommonSettingSchemas.RENDER_COLOR_MODE.commonI18nKey()),
            this::isOrbiz
    );
    private final NumberValue<Integer> orbizColorSpeed = visibleWhen(
            common(num("crosshairOrbizColorSpeed", "orbiz_color_speed", 18, 2, 54),
                    CommonSettingSchemas.RENDER_COLOR_SPEED.commonI18nKey()),
            this::isOrbiz
    );
    private final RGBAColorValue orbizColor = visibleWhen(
            common(color("crosshairOrbizColor", "orbiz_color", "#EEFFFFFF"),
                    CommonSettingSchemas.RENDER_PRIMARY_COLOR.commonI18nKey()),
            this::isOrbiz
    );
    private final RGBAColorValue orbizColor2 = visibleWhen(
            common(color("crosshairOrbizColor2", "orbiz_color2", "#EE55FFFF"),
                    CommonSettingSchemas.RENDER_SECONDARY_COLOR.commonI18nKey()),
            () -> isOrbiz() && AnimatedRenderColors.usesSecondary(orbizColorMode.get())
    );
    private final NumberValue<Double> orbizLookMultiplier = visibleWhen(
            num("crosshairOrbizLookMultiplier", "orbiz_look_multiplier", 0.45, 0.0, 2.5),
            () -> isOrbiz() && isOrbizMotionDynamic()
    );
    private final NumberValue<Double> orbizMoveMultiplier = visibleWhen(
            num("crosshairOrbizMoveMultiplier", "orbiz_move_multiplier", 2.0, 0.0, 10.0),
            () -> isOrbiz() && isOrbizMotionDynamic()
    );
    private final NumberValue<Double> orbizMotionLimit = visibleWhen(
            num("crosshairOrbizMotionLimit", "orbiz_motion_limit", 8.0, 0.0, 24.0),
            () -> isOrbiz() && isOrbizMotionDynamic()
    );
    private final NumberValue<Double> orbizMotionSmoothing = visibleWhen(
            num("crosshairOrbizMotionSmoothing", "orbiz_motion_smoothing", 12.0, 1.0, 30.0),
            () -> isOrbiz() && isOrbizMotionDynamic()
    );
    private final EnumValue<VulcanReachDisplay> vulcanReachDisplay =
            enumSetting("crosshairVulcanReachDisplay", "vulcan_reach_display",
                    VulcanReachDisplay.TIMED, VulcanReachDisplay.values());
    private final NumberValue<Integer> vulcanReachLookMs =
            visibleWhen(num("crosshairVulcanReachLookMs", "vulcan_reach_look_ms", 1200, 0, 3000),
                    () -> vulcanReachDisplay.get() == VulcanReachDisplay.TIMED);
    private final NumberValue<Integer> vulcanReachAttackMs =
            visibleWhen(num("crosshairVulcanReachAttackMs", "vulcan_reach_attack_ms", 2500, 0, 6000),
                    () -> vulcanReachDisplay.get() == VulcanReachDisplay.TIMED);
    private float animatedGap;
    private int animatedColor = DEFAULT_COLOR;
    private float animatedOrbizProgress = 1.0f;
    private float orbizOffsetX;
    private float orbizOffsetY;
    private float lastOrbizYaw;
    private float lastOrbizPitch;
    private boolean orbizMotionInitialized;
    private boolean colorInitialized;
    private boolean chestInit;
    private boolean lastSwapCandidate;
    private boolean lastChestWasElytra;
    private ItemStack lastChestStack = ItemStack.EMPTY;
    private long lastVulcanReachLookMs;
    private long lastVulcanReachAttackMs;
    private Player lastVulcanReachTarget;

    private static int colorDistance(int a, int b) {
        int dr = Math.abs(((a >>> 16) & 0xFF) - ((b >>> 16) & 0xFF));
        int dg = Math.abs(((a >>> 8) & 0xFF) - ((b >>> 8) & 0xFF));
        int db = Math.abs((a & 0xFF) - (b & 0xFF));
        int da = Math.abs(((a >>> 24) & 0xFF) - ((b >>> 24) & 0xFF));
        return dr + dg + db + da;
    }

    public static boolean isRiptideBlocked(Player player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) return false;
        if (!stack.is(Items.TRIDENT)) return false;
        if (!hasRiptide(stack)) return false;
        return !isTouchingWaterOrServerRain(player);
    }

    private static boolean hasRiptide(ItemStack stack) {
        ItemEnchantments ench = stack.getOrDefault(
                DataComponents.ENCHANTMENTS,
                ItemEnchantments.EMPTY
        );
        return ench.keySet().stream().anyMatch(entry -> {
            var keyOpt = entry.unwrapKey();
            return keyOpt.isPresent() && keyOpt.get().identifier().equals(Enchantments.RIPTIDE.identifier());
        });
    }

    private static boolean isTouchingWaterOrServerRain(Player player) {
        if (player == null) return false;
        if (player.isInWater()) return true;
        Level world = player.level();
        BlockPos pos = player.blockPosition();
        if (WorldTweaks.hasServerRainAt(world, pos)) return true;
        BlockPos top = BlockPos.containing(pos.getX(), player.getBoundingBox().maxY, pos.getZ());
        return WorldTweaks.hasServerRainAt(world, top);
    }

    @Override
    public HudPhase getHudPhase() {
        return HudPhase.LAST;
    }

    public boolean shouldHideVanilla() {
        return isEnabled()
                && mc.player != null
                && mc.level != null
                && mc.options.getCameraType().isFirstPerson();
    }

    @Override
    public void onEnable() {
        resetSwapDetector();
        resetOrbizState();
        colorInitialized = false;
    }

    @Override
    public void onDisable() {
        resetSwapDetector();
        resetOrbizState();
        Itemizer.hideElytraSwap();
    }

    @Override
    public void onRenderHudEngine(Renderer2D renderer, TextRenderer textRenderer, GuiGraphicsExtractor ctx, float tickDelta) {
        if (!isEnabled() || mc.player == null || mc.level == null) return;
        if (!mc.options.getCameraType().isFirstPerson()) return;

        float scale = ViewportContext.getScaleFactor();
        float centerX = mc.getWindow().getWidth() * 0.5f;
        float centerY = mc.getWindow().getHeight() * 0.5f;

        float thicknessPx = (float) (thickness.get() * scale);
        float lengthPx = (float) (length.get() * scale);
        float outlinePx = OUTLINE_EXPAND * scale;

        float cooldown = 0.0f;
        if (dynamicGap.get()) {
            float charge = getChargeProgress(mc.player);
            if (charge >= 0.0f) {
                cooldown = 1.0f - charge;
            } else {
                float progress = getAttackCooldownProgress();
                progress = Mth.clamp(progress + COOLDOWN_LEAD, 0.0f, 1.0f);
                cooldown = 1.0f - progress;
            }
        }

        float targetGap = (float) (baseGap.get() + maxGapIncrease.get() * cooldown);
        animatedGap += (targetGap - animatedGap) * GAP_SMOOTHING;
        float gapPx = animatedGap * scale;

        updateSwapDetector(mc.player);

        if (crosshairType.get() == CrosshairType.ORBIZ) {
            renderOrbiz(renderer, centerX, centerY, scale);
            float infoGapPx = (float) (orbizRadius.get() * scale);
            float infoLengthPx = (float) ((orbizGlow.get() + orbizThickness.get()) * scale);
            renderVulcanReachInfo(textRenderer, centerX, centerY, infoGapPx, infoLengthPx);
            return;
        }

        boolean outline = showOutline.get();
        if (shouldShowRiptideBlocked(mc.player) || shouldShowCooldownCross(mc.player)) {
            int dangerColor = smoothCrosshairColor(RIPTIDE_BLOCK_COLOR);
            renderRiptideBlockedCross(renderer, centerX, centerY, thicknessPx, lengthPx, outline, outlinePx, dangerColor);
            return;
        }

        int color = smoothCrosshairColor(resolveCrosshairTargetColor());
        boolean tClassic = crosshairType.get() == CrosshairType.T_CLASSIC;

        if (tClassic) {
            double barX = centerX - lengthPx;
            double barY = centerY - thicknessPx * 0.5f;
            double barW = lengthPx * 2.0f;
            double stemX = centerX - thicknessPx * 0.5f;
            double stemH = lengthPx + gapPx;
            drawSegment(renderer, barX, barY, barW, thicknessPx, color, outline, outlinePx);
            drawSegment(renderer, stemX, centerY, thicknessPx, stemH, color, outline, outlinePx);
            renderVulcanReachInfo(textRenderer, centerX, centerY, gapPx, lengthPx);
            return;
        }

        double topX = centerX - thicknessPx * 0.5f;
        double topY = centerY - gapPx - lengthPx;
        double bottomX = centerX - thicknessPx * 0.5f;
        double bottomY = centerY + gapPx;
        double leftX = centerX - gapPx - lengthPx;
        double leftY = centerY - thicknessPx * 0.5f;
        double rightX = centerX + gapPx;
        double rightY = centerY - thicknessPx * 0.5f;

        drawSegment(renderer, topX, topY, thicknessPx, lengthPx, color, outline, outlinePx);
        drawSegment(renderer, bottomX, bottomY, thicknessPx, lengthPx, color, outline, outlinePx);
        drawSegment(renderer, leftX, leftY, lengthPx, thicknessPx, color, outline, outlinePx);
        drawSegment(renderer, rightX, rightY, lengthPx, thicknessPx, color, outline, outlinePx);
        renderVulcanReachInfo(textRenderer, centerX, centerY, gapPx, lengthPx);
    }

    @EventHandler
    private void onAttackEntity(AttackEntityEvent event) {
        if (!isVulcanReachActive()) {
            return;
        }
        if (event.getPlayer() != mc.player || !(event.getTarget() instanceof Player player)) {
            return;
        }
        lastVulcanReachAttackMs = System.currentTimeMillis();
        lastVulcanReachTarget = player;
    }

    @EventHandler
    private void onPacketSendPost(PacketEvent.SendPost event) {
        if (!isVulcanReachActive() || mc.level == null) {
            return;
        }
        if (!(event.getPacket() instanceof ServerboundInteractPacket packet) || !isAttackPacket(packet)) {
            return;
        }

        Entity target = mc.level.getEntity(packet.entityId());
        if (target instanceof Player player) {
            lastVulcanReachAttackMs = System.currentTimeMillis();
            lastVulcanReachTarget = player;
        }
    }

    private float getAttackCooldownProgress() {
        if (mc.player instanceof IPlayerAttackCooldown cooldown) {
            return cooldown.combatant$getAttackCooldownProgress(0.0f);
        }
        return mc.player.getAttackStrengthScale(0.0f);
    }

    private float getChargeProgress(Player player) {
        if (player == null || !player.isUsingItem()) return -1.0f;
        ItemStack stack = player.getUseItem();
        if (stack == null || stack.isEmpty()) return -1.0f;

        int useTicks = player.getTicksUsingItem();
        Item item = stack.getItem();

        if (item instanceof BowItem) {
            return Mth.clamp(BowItem.getPowerForTime(useTicks), 0.0f, 1.0f);
        }
        if (item instanceof CrossbowItem) {
            int pullTime = CrossbowItem.getChargeDuration(stack, player);
            if (pullTime <= 0) return 1.0f;
            return Mth.clamp(useTicks / (float) pullTime, 0.0f, 1.0f);
        }
        if (stack.is(Items.TRIDENT)) {
            return Mth.clamp(useTicks / 10.0f, 0.0f, 1.0f);
        }

        return -1.0f;
    }

    private void renderOrbiz(Renderer2D renderer, float centerX, float centerY, float scale) {
        boolean danger = shouldShowRiptideBlocked(mc.player) || shouldShowCooldownCross(mc.player);
        float progress = danger ? 1.0f : resolveOrbizProgress();
        float targetProgress = Mth.clamp(progress * orbizCooldownMultiplier.get().floatValue(), 0.0f, 1.0f);
        if (targetProgress < animatedOrbizProgress) {
            animatedOrbizProgress = targetProgress;
        } else {
            float t = AnimationUtility.clamp01(AnimationUtility.deltaTime() * ORBIZ_PROGRESS_SMOOTH_SPEED);
            animatedOrbizProgress = AnimationUtility.lerp(animatedOrbizProgress, targetProgress, t);
        }

        updateOrbizMotion();

        AnimatedRenderColors.Mode mode = danger ? AnimatedRenderColors.Mode.STATIC : orbizColorMode.get();
        int primary = danger ? colorWithConfigAlpha(RIPTIDE_BLOCK_COLOR, orbizColor.getArgb()) : resolveOrbizPrimary(mode);
        int secondary = danger ? primary : AnimatedRenderColors.angularSecondaryColor(mode, primary, orbizColor2.getArgb());
        primary = AnimatedRenderColors.angularPrimaryColor(mode, primary);

        float radiusPx = (float) (orbizRadius.get() * scale);
        float thicknessPx = (float) (orbizThickness.get() * scale);
        float softnessPx = (float) (orbizSoftness.get() * scale);
        float glowPx = (float) (orbizGlow.get() * scale);
        float sweep = animatedOrbizProgress * 360.0f;
        float trackGlow = glowPx > 0.0f ? 1.0f : 0.0f;

        renderer.orbizRing(
                centerX + orbizOffsetX * scale,
                centerY + orbizOffsetY * scale,
                radiusPx,
                thicknessPx,
                0.0f,
                sweep,
                softnessPx,
                glowPx,
                orbizGlowStrength.get().floatValue(),
                primary,
                secondary,
                AnimatedRenderColors.shaderMode(mode),
                AnimatedRenderColors.angularOffset01(mode, orbizColorSpeed.get()),
                orbizAngularCycles.get().floatValue(),
                orbizTrackAlpha.get().floatValue(),
                orbizBodyAlpha.get().floatValue(),
                orbizHeadBoost.get().floatValue(),
                trackGlow,
                true
        );
    }

    private float resolveOrbizProgress() {
        float charge = getChargeProgress(mc.player);
        if (charge >= 0.0f) {
            return Mth.clamp(charge, 0.0f, 1.0f);
        }
        return Mth.clamp(getAttackCooldownProgress() + COOLDOWN_LEAD, 0.0f, 1.0f);
    }

    private void updateOrbizMotion() {
        if (mc.player == null) {
            orbizOffsetX = AnimationUtility.fast(orbizOffsetX, 0.0f, 10.0f);
            orbizOffsetY = AnimationUtility.fast(orbizOffsetY, 0.0f, 10.0f);
            orbizMotionInitialized = false;
            return;
        }

        float yaw = mc.player.getYRot();
        float pitch = mc.player.getXRot();
        if (!orbizMotionInitialized) {
            lastOrbizYaw = yaw;
            lastOrbizPitch = pitch;
            orbizMotionInitialized = true;
        }

        float targetX = 0.0f;
        float targetY = 0.0f;
        if (isOrbizMotionDynamic()) {
            float look = orbizLookMultiplier.get().floatValue();
            float move = orbizMoveMultiplier.get().floatValue();
            targetX = Mth.wrapDegrees(lastOrbizYaw - yaw) * look + mc.player.xxa * move;
            targetY = (lastOrbizPitch - pitch) * look + mc.player.zza * move;
            float limit = orbizMotionLimit.get().floatValue();
            targetX = Mth.clamp(targetX, -limit, limit);
            targetY = Mth.clamp(targetY, -limit, limit);
        }

        float t = AnimationUtility.clamp01(AnimationUtility.deltaTime() * orbizMotionSmoothing.get().floatValue());
        orbizOffsetX = AnimationUtility.lerp(orbizOffsetX, targetX, t);
        orbizOffsetY = AnimationUtility.lerp(orbizOffsetY, targetY, t);
        lastOrbizYaw = yaw;
        lastOrbizPitch = pitch;
    }

    private int resolveOrbizPrimary(AnimatedRenderColors.Mode mode) {
        int configured = orbizColor.getArgb();
        if (mode == AnimatedRenderColors.Mode.STATIC && highlightTarget.get()) {
            return colorWithConfigAlpha(smoothCrosshairColor(resolveCrosshairTargetColor()), configured);
        }
        return configured;
    }

    private int colorWithConfigAlpha(int rgbSource, int alphaSource) {
        return ColorMath.colorWithAlpha(rgbSource, (alphaSource >>> 24) & 0xFF);
    }

    private boolean isOrbiz() {
        return crosshairType.get() == CrosshairType.ORBIZ;
    }

    private boolean isClassicCrosshair() {
        return crosshairType.get() != CrosshairType.ORBIZ;
    }

    private boolean isOrbizMotionDynamic() {
        return orbizMotion.get() == OrbizMotionMode.DYNAMIC;
    }

    private void resetOrbizState() {
        animatedOrbizProgress = 1.0f;
        orbizOffsetX = 0.0f;
        orbizOffsetY = 0.0f;
        lastOrbizYaw = 0.0f;
        lastOrbizPitch = 0.0f;
        orbizMotionInitialized = false;
    }

    private int resolveCrosshairTargetColor() {
        if (!highlightTarget.get()) return DEFAULT_COLOR;
        HitResult hit = mc.hitResult;
        if (!(hit instanceof EntityHitResult ehr)) return DEFAULT_COLOR;
        Entity entity = ehr.getEntity();
        if (entity == null) return DEFAULT_COLOR;
        return resolveEntityColor(entity);
    }

    private void renderVulcanReachInfo(TextRenderer fallback, float centerX, float centerY, float gapPx, float lengthPx) {
        if (!isVulcanReachActive()) {
            return;
        }

        Player target = resolveVulcanReachTarget();
        double requestedReach = resolveVulcanRequestedReach(target);
        VulcanReachController.HudSnapshot snapshot = target != null && target.isAlive()
                ? VulcanReachController.INSTANCE.hudSnapshot(target, requestedReach)
                : vulcanReachDisplay.get() == VulcanReachDisplay.ALWAYS
                ? VulcanReachController.INSTANCE.hudSnapshot(requestedReach)
                : null;
        if (snapshot == null) {
            return;
        }

        String reachText = String.format(Locale.ROOT, "%.1f", snapshot.displayReach());
        String hitsText = Integer.toString(snapshot.longHitsRemaining());

        TextRenderer font = Fonts.renderer("Montserrat", FontInfo.Type.Regular, fallback);
        float textScale = VULCAN_REACH_FONT_SIZE / 18.0f;
        float x = centerX + gapPx + lengthPx + 7.0f;
        float y = centerY - VULCAN_REACH_FONT_SIZE * 0.95f;
        float lineHeight = VULCAN_REACH_FONT_SIZE * 0.92f;

        font.begin(textScale, false, false);
        font.render(reachText, x, y, new RenderColor(VULCAN_REACH_PRIMARY), true);
        font.render(hitsText, x, y + lineHeight, new RenderColor(VULCAN_REACH_SECONDARY), true);
        font.end();
    }

    private boolean isVulcanReachActive() {
        Reach reach = Modules.get(Reach.class);
        if (reach != null && reach.isEnabled() && reach.isVulcan297Mode()) {
            return true;
        }

        KillAura killAura = Modules.get(KillAura.class);
        return killAura != null && killAura.isEnabled() && killAura.usesDynamicReach();
    }

    private double resolveVulcanRequestedReach(Player target) {
        Reach reach = Modules.get(Reach.class);
        if (reach != null && reach.isEnabled() && reach.isVulcan297Mode()) {
            return reach.getConfiguredDistance();
        }

        KillAura killAura = Modules.get(KillAura.class);
        if (killAura != null && killAura.isEnabled() && killAura.usesDynamicReach()) {
            return killAura.getRange();
        }

        return 3.0;
    }

    private Player resolveVulcanReachTarget() {
        long now = System.currentTimeMillis();
        HitResult hit = mc.hitResult;
        if (hit instanceof EntityHitResult entityHit
                && entityHit.getEntity() instanceof Player player
                && player.isAlive()) {
            lastVulcanReachLookMs = now;
            lastVulcanReachTarget = player;
            return player;
        }

        KillAura killAura = Modules.get(KillAura.class);
        if (killAura != null
                && killAura.isEnabled()
                && killAura.usesDynamicReach()
                && killAura.getCurrentTarget() instanceof Player auraTarget
                && auraTarget.isAlive()) {
            lastVulcanReachTarget = auraTarget;
            return auraTarget;
        }

        if (lastVulcanReachTarget != null && !lastVulcanReachTarget.isAlive()) {
            lastVulcanReachTarget = null;
        }

        if (vulcanReachDisplay.get() == VulcanReachDisplay.ALWAYS) {
            return lastVulcanReachTarget;
        }

        if (lastVulcanReachTarget == null) {
            return null;
        }

        long lookWindow = Math.max(0, vulcanReachLookMs.get());
        long attackWindow = Math.max(0, vulcanReachAttackMs.get());
        boolean recentLook = lookWindow > 0 && now - lastVulcanReachLookMs <= lookWindow;
        boolean recentAttack = attackWindow > 0 && now - lastVulcanReachAttackMs <= attackWindow;
        return recentLook || recentAttack ? lastVulcanReachTarget : null;
    }

    private boolean isAttackPacket(ServerboundInteractPacket packet) {
        return packet.hand() == null && packet.location() == null;
    }

    private int smoothCrosshairColor(int targetColor) {
        if (!colorInitialized) {
            animatedColor = targetColor;
            colorInitialized = true;
            return animatedColor;
        }
        float t = AnimationUtility.clamp01(AnimationUtility.deltaTime() * COLOR_SMOOTH_SPEED);
        animatedColor = HudRenderUtil.mixColor(animatedColor, targetColor, t);
        if (colorDistance(animatedColor, targetColor) <= 2) {
            animatedColor = targetColor;
        }
        return animatedColor;
    }

    private int resolveEntityColor(Entity entity) {
        if (entity instanceof Player player) {
            return CategoryService.getColor(player);
        }

        PlayerRelations rel = PlayerRelations.get();
        boolean friendly = isFriendlyEntity(entity);
        return friendly ? rel.colorFriend() : rel.colorEnemy();
    }

    private boolean isFriendlyEntity(Entity entity) {
        var id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (id != null && EntityFilters.get().isIgnoredEntity(id.toString())) {
            return true;
        }
        return entity.getType().getCategory() != MobCategory.MONSTER;
    }

    private boolean shouldShowRiptideBlocked(Player player) {
        if (player == null) return false;
        ItemStack main = player.getMainHandItem();
        if (isRiptideBlocked(player, main)) return true;
        if (!isHandLikeItem(main)) return false;
        return isRiptideBlocked(player, player.getOffhandItem());
    }

    private boolean shouldShowCooldownCross(Player player) {
        if (player == null) return false;
        ItemStack main = player.getMainHandItem();
        if (isCooldownCrossItem(main) && player.getCooldowns().isOnCooldown(main)) return true;
        if (shouldSuppressOffhandCooldown(main)) return false;
        ItemStack off = player.getOffhandItem();
        return isCooldownCrossItem(off) && player.getCooldowns().isOnCooldown(off);
    }

    private boolean isCooldownCrossItem(ItemStack stack) {
        return stack != null && !stack.isEmpty();
    }

    private boolean shouldSuppressOffhandCooldown(ItemStack main) {
        return !isHandLikeItem(main);
    }

    private boolean isHandLikeItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return true;
        Item item = stack.getItem();
        if (item instanceof BowItem || item instanceof CrossbowItem) return false;
        if (stack.is(Items.TRIDENT)) return false;
        if (FoodUtil.isFood(stack)) return false;

        final boolean[] hasAttackMod = {false};
        stack.forEachModifier(net.minecraft.world.entity.EquipmentSlotGroup.MAINHAND, (attribute, modifier, display) -> {
            if (attribute.equals(Attributes.ATTACK_DAMAGE) || attribute.equals(Attributes.ATTACK_SPEED)) {
                hasAttackMod[0] = true;
            }
        });
        return !hasAttackMod[0];
    }

    private void renderRiptideBlockedCross(Renderer2D renderer,
                                           float centerX,
                                           float centerY,
                                           float thicknessPx,
                                           float lengthPx,
                                           boolean outline,
                                           float outlinePx,
                                           int color) {
        float crossLength = lengthPx * RIPTIDE_CROSS_LENGTH_FACTOR;
        double hX = centerX - crossLength;
        double hY = centerY - thicknessPx * 0.5f;
        double vX = centerX - thicknessPx * 0.5f;
        double vY = centerY - crossLength;

        drawSegment(renderer, hX, hY, crossLength * 2.0f, thicknessPx, color, outline, outlinePx);
        drawSegment(renderer, vX, vY, thicknessPx, crossLength * 2.0f, color, outline, outlinePx);
    }

    private void updateSwapDetector(Player player) {
        ItemStack current = player.getItemBySlot(EquipmentSlot.CHEST);
        boolean isElytra = isElytra(current);
        boolean isChestplate = isChestplate(current);
        boolean candidate = isElytra || isChestplate;

        if (!chestInit) {
            chestInit = true;
            lastSwapCandidate = candidate;
            lastChestWasElytra = isElytra;
            lastChestStack = current.copy();
            return;
        }

        if (candidate && lastSwapCandidate && isElytra != lastChestWasElytra
                && !ItemStack.isSameItem(current, lastChestStack)) {
            Itemizer.showElytraSwap(current.copy());
        }

        lastSwapCandidate = candidate;
        lastChestWasElytra = isElytra;
        lastChestStack = current.copy();
    }

    private boolean isElytra(ItemStack stack) {
        return !stack.isEmpty() && stack.is(Items.ELYTRA);
    }

    private boolean isChestplate(ItemStack stack) {
        if (stack.isEmpty() || stack.is(Items.ELYTRA)) return false;
        Equippable eq = stack.get(DataComponents.EQUIPPABLE);
        return eq != null && eq.slot() == EquipmentSlot.CHEST;
    }

    private void resetSwapDetector() {
        chestInit = false;
        lastSwapCandidate = false;
        lastChestWasElytra = false;
        lastChestStack = ItemStack.EMPTY;
    }

    private void drawSegment(Renderer2D renderer,
                             double x,
                             double y,
                             double w,
                             double h,
                             int color,
                             boolean outline,
                             float outlinePx) {
        if (outline) {
            renderer.quad(x - outlinePx, y - outlinePx, w + outlinePx * 2.0f, h + outlinePx * 2.0f, OUTLINE_COLOR);
        }
        renderer.quad(x, y, w, h, color);
    }

    public enum VulcanReachDisplay implements EnumValue.IdProvider {
        TIMED("timed"),
        ALWAYS("always");

        private final String id;

        VulcanReachDisplay(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }
    }

    private enum CrosshairType implements EnumValue.AliasProvider {
        CLASSIC,
        T_CLASSIC,
        ORBIZ;

        @Override
        public List<String> aliases() {
            return switch (this) {
                case CLASSIC -> List.of("Classic");
                case T_CLASSIC -> List.of("T-Classic");
                case ORBIZ -> List.of("Orbiz");
            };
        }
    }

    private enum OrbizMotionMode implements EnumValue.AliasProvider {
        STATIC,
        DYNAMIC;

        @Override
        public List<String> aliases() {
            return switch (this) {
                case STATIC -> List.of("Static", "Off", "Disabled", "false");
                case DYNAMIC -> List.of("Dynamic", "On", "Enabled", "true");
            };
        }
    }
}

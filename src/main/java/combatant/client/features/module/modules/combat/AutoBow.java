/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat;

import combatant.client.render.engine.text.BuiltinFontCatalog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import combatant.client.config.common.CommonSettingSchemas;
import combatant.client.config.common.impl.TargetFilters;
import combatant.client.config.values.BooleanMapValue;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.EventSync;
import combatant.client.events.impl.KeybindIsPressedEvent;
import combatant.client.events.impl.RotationUpdateEvent;
import combatant.client.features.module.HudPhase;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.core.ViewportContext;
import combatant.client.render.engine.math.HudScale;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.render.helpers.ScreenProjection;
import combatant.client.render.helpers.TickDelta;
import combatant.client.util.aiming.RotationManager;
import combatant.client.util.aiming.RotationTarget;
import combatant.client.util.aiming.data.Rotation;
import combatant.client.util.aiming.features.MovementCorrection;
import combatant.client.util.aiming.raytrace.RotationRaytrace;
import combatant.client.util.projectile.ProjectileAimSolver;
import combatant.client.util.logging.DebugLog;
import combatant.client.util.target.ScreenRingSelection;
import combatant.client.util.target.TargetManager;
import combatant.client.util.target.TargetingUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@ModuleInfo(
        id = "autobow",
        displayName = "AutoBow",
        category = ModuleCategory.COMBAT, subcategory = ModuleSubcategory.ATTACK,
        description = "module.autobow.description")
public class AutoBow extends Module {

    private static final int COLOR_IDLE = 0x66FFFFFF;
    private static final int COLOR_WAIT = 0xFFFFD45A;
    private static final int COLOR_LOCK = 0xFF59C9FF;
    private static final int COLOR_SHOOT = 0xFF69FF9C;
    private static final int TEXT_SHADOW = 0xAA000000;
    private static final float RING_SOFTNESS = 1.15f;
    private static final float TEXT_SCALE = 1.0f;
    private static final float TEXT_OFFSET = 20.0f;
    private static final int ROTATION_PRIORITY = 50;
    private static final int TRIDENT_THROW_THRESHOLD_TICKS = 10;
    private static final int BOW_FULL_PULL_RELEASE_TICKS = 21;
    private static final int CROSSBOW_RELEASE_EXTRA_TICKS = 1;
    private static final int TRIDENT_RELEASE_EXTRA_TICKS = 1;
    private static final float LOCK_RETAIN_MARGIN = 6.0f;
    private static final double CLOSE_DIRECT_AIM_RANGE = 6.0;
    private static final double TARGET_BOX_INFLATE = 0.08;
    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Double> range = num("autobowRange", 48.0, 4.0, 160.0);
    private final EnumValue<TargetingUtil.TargetPriority> priority =
            enumCommon(
                    "autobowPriority",
                    "priority",
                    CommonSettingSchemas.COMBAT_PRIORITY,
                    TargetingUtil.TargetPriority.DISTANCE,
                    TargetingUtil.TargetPriority.values()
            );
    private final EnumValue<AutomationMode> mode =
            enumMode("autobowMode", AutomationMode.FULL_AUTO, AutomationMode.values());
    private final EnumValue<MovementCorrection> movementCorrection =
            enumCommon(
                    "autobowMovementCorrection",
                    "movement_correction",
                    CommonSettingSchemas.ROTATION_MOVEMENT_CORRECTION,
                    MovementCorrection.SILENT,
                    MovementCorrection.values()
            );
    private final NumberValue<Integer> rotationResetTicks =
            numCommon(
                    "autobowRotationResetTicks",
                    "rotation_reset_ticks",
                    CommonSettingSchemas.ROTATION_RESET_TICKS,
                    1,
                    1,
                    20
            );
    private final NumberValue<Float> rotationResetThreshold =
            numCommon(
                    "autobowRotationResetThreshold",
                    "rotation_reset_threshold",
                    CommonSettingSchemas.ROTATION_RESET_THRESHOLD,
                    2.0f,
                    0.1f,
                    15.0f
            );
    private final NumberValue<Double> hudRadius = num("autobowHudRadius", 64.0, 12.0, 240.0);
    private final NumberValue<Double> hudThickness = num("autobowHudThickness", 2.5, 1.0, 8.0);
    private final NumberValue<Float> aimThreshold = num("autobowAimThreshold", 1.5f, 0.25f, 6.0f);
    private final BooleanValue bowRapidFire = bool("autobowBowRapidFire", false);
    private final NumberValue<Integer> chargedTicks = num("autobowChargedTicks", 15, 3, 20);
    private final NumberValue<Integer> chargedRandom = num("autobowChargedRandom", 0, 0, 10);
    private final NumberValue<Double> delayBetweenShots =
            num("autobowDelayBetweenShots", 0.0, 0.0, 5.0);
    private final BooleanMapValue toggles = groupCommon(
            "autobow_toggles",
            "toggles",
            CommonSettingSchemas.TARGET_FILTERS
    );

    private Snapshot lastSnapshot = Snapshot.hidden();
    private long lastShotAtMs;
    private boolean forceReleaseUse;
    private boolean forceHoldUse;
    private Integer currentChargeRandom;
    private int snapshotTick = Integer.MIN_VALUE;
    private LivingEntity preferredTarget;
    private PendingUse pendingUse;
    private boolean rotationRequested;

    @Override
    public HudPhase getHudPhase() {
        return HudPhase.LAST;
    }

    @Override
    public void onEnable() {
        DebugLog.info("[AutoBow] enabled, ringRadius={}px mode={} range={}",
                hudRadius.get(), mode.get(), range.get());
        lastShotAtMs = 0L;
        lastSnapshot = Snapshot.hidden();
        forceReleaseUse = false;
        forceHoldUse = false;
        currentChargeRandom = null;
        snapshotTick = Integer.MIN_VALUE;
        preferredTarget = null;
        pendingUse = null;
        rotationRequested = false;
    }

    @Override
    public void onDisable() {
        DebugLog.info("[AutoBow] disabled");
        lastSnapshot = Snapshot.hidden();
        forceReleaseUse = false;
        forceHoldUse = false;
        currentChargeRandom = null;
        snapshotTick = Integer.MIN_VALUE;
        preferredTarget = null;
        pendingUse = null;
        releaseRotationIfRequested();
        TargetManager.clear(this);
    }

    @EventHandler(priority = 100)
    private void onKeybindIsPressed(KeybindIsPressedEvent event) {
        if (!isEnabled() || mc.options == null) {
            return;
        }
        if (event.getKeyBinding() != mc.options.keyUse) {
            return;
        }
        if (!forceReleaseUse) {
            if (forceHoldUse) {
                event.setPressed(true);
            }
            return;
        }

        event.setPressed(false);
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;

        Snapshot snapshot = getSnapshot(TickDelta.tickProgress(false));
        boolean inside = snapshot.insideRadius();
        forceHoldUse = shouldHoldUseForFullAuto(snapshot);
        TargetManager.publish(this, inside ? snapshot.target() : null, TargetManager.Source.MODULE, 500);

        if (mode.get() == AutomationMode.FULL_AUTO && inside) {
            tryAutoDraw(snapshot);
        }

        if (mode.get() != AutomationMode.FULL_AUTO) {
            return;
        }

        if (!inside || snapshot.state() != HudState.SHOOT) {
            return;
        }

        tryAutoShoot(snapshot);
    }

    public void onInputCycleHandled() {
        forceReleaseUse = false;
    }

    public boolean shouldCancelVanillaDoItemUse() {
        if (!isEnabled() || mc.player == null) {
            return false;
        }

        if (pendingUse != null) {
            return true;
        }

        Snapshot snapshot = getSnapshot(TickDelta.tickProgress(false));
        ActiveWeapon weapon = snapshot.weapon();
        return weapon != null
                && weapon.type() == WeaponType.CROSSBOW
                && snapshot.target() != null
                && snapshot.insideRadius();
    }

    @EventHandler(priority = -1000)
    private void onSync(EventSync event) {
        if (!isEnabled()) {
            return;
        }

        LocalPlayer player = mc.player;
        MultiPlayerGameMode manager = mc.gameMode;
        PendingUse use = pendingUse;
        if (player == null || manager == null || use == null) {
            return;
        }

        if (player.tickCount > use.expireAge()) {
            pendingUse = null;
            return;
        }

        ItemStack stack = player.getItemInHand(use.hand());
        if (!canUseStackForAction(player, stack, use.action())) {
            pendingUse = null;
            return;
        }

        Rotation rotation = use.rotation();
        if (rotation == null) {
            pendingUse = null;
            return;
        }

        pendingUse = null;
        event.setRotation(rotation.yaw(), rotation.pitch(), true);

        float prevYaw = player.getYRot();
        float prevPitch = player.getXRot();
        player.setYRot(rotation.yaw());
        player.setXRot(rotation.pitch());
        event.addPostAction(() -> {
            player.setYRot(prevYaw);
            player.setXRot(prevPitch);
        });

        InteractionResult result = manager.useItem(player, use.hand());
        if (result.consumesAction()) {
            if (use.action() == UseAction.FIRE_CROSSBOW) {
                lastShotAtMs = System.currentTimeMillis();
            }
        }
    }

    @Override
    public void onRenderHudEngine(Renderer2D renderer, TextRenderer textRenderer, GuiGraphicsExtractor ctx, float tickDelta) {
        if (!isEnabled() || ctx == null) return;
        if (mc.player == null || mc.level == null) return;
        if (!mc.options.getCameraType().isFirstPerson()) return;

        Snapshot snapshot = getSnapshot(tickDelta);
        if (!snapshot.showHud()) {
            return;
        }

        int fbw = mc.getWindow().getWidth();
        int fbh = mc.getWindow().getHeight();
        float logicalWidth = HudScale.virtualWidth(fbw, fbh);
        float logicalHeight = HudScale.virtualHeight(fbw, fbh);
        float centerX = logicalWidth * 0.5f;
        float centerY = logicalHeight * 0.5f;
        float radiusValue = hudRadius.get().floatValue();
        float thicknessValue = hudThickness.get().floatValue();
        int color = snapshot.state().color;

        ViewportContext.beginUnscaledLogical(ctx);
        try {
            renderer.circleStroke(centerX, centerY, radiusValue, thicknessValue, RING_SOFTNESS, color);
            if (snapshot.state().cross) {
                float halfLength = radiusValue * 0.64f;
                drawCross(renderer, centerX, centerY, halfLength, Math.max(1.0f, thicknessValue * 0.8f), color);
            }
            renderStateText(textRenderer, centerX, centerY - radiusValue - TEXT_OFFSET, snapshot.state());
        } finally {
            ViewportContext.beginUnscaled(ctx);
        }
    }

    @EventHandler(priority = 15)
    private void onRotationUpdate(RotationUpdateEvent event) {
        if (event.getType() != RotationUpdateEvent.Type.PRE) {
            return;
        }

        if (!isEnabled()) return;

        Snapshot snapshot = getSnapshot(TickDelta.tickProgress(false));
        boolean canRotate = canRotateForWeaponState(mc.player, snapshot.weapon());
        boolean inside = snapshot.insideRadius();
        TargetManager.publish(this, inside ? snapshot.target() : null, TargetManager.Source.MODULE, 500);

        if (!snapshot.shouldRotate() || !canRotate) {
            releaseRotationIfRequested();
            return;
        }

        RotationTarget target = new RotationTarget(
                snapshot.solution(),
                snapshot.target(),
                List.of(),
                rotationResetTicks.get(),
                rotationResetThreshold.get(),
                true,
                movementCorrection.get(),
                null
        );
        RotationManager.INSTANCE.setRotationTarget(target, ROTATION_PRIORITY, this);
        rotationRequested = true;
    }

    private void releaseRotationIfRequested() {
        if (!rotationRequested) {
            return;
        }

        RotationManager.INSTANCE.release(this);
        rotationRequested = false;
    }

    private Snapshot evaluateSnapshot(float tickDelta) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return Snapshot.hidden();
        }

        ActiveWeapon weapon = findActiveWeapon(player);
        if (weapon == null) {
            DebugLog.infoOnChange("autobow.context", "no-weapon", "[AutoBow] no supported weapon in hands");
            return Snapshot.hidden();
        }

        int fbw = mc.getWindow().getWidth();
        int fbh = mc.getWindow().getHeight();
        float logicalWidth = HudScale.virtualWidth(fbw, fbh);
        float logicalHeight = HudScale.virtualHeight(fbw, fbh);
        float baseRadius = hudRadius.get().floatValue();
        TargetingUtil.TargetingSettings settings = buildTargetingSettings();
        List<ScreenCandidate> eligible = new ArrayList<>();
        List<LivingEntity> available = getPrioritizedCandidates(settings);
        for (LivingEntity entity : available) {
            Vec2 point = projectTargetToLogical(entity, logicalWidth, logicalHeight, tickDelta);
            if (point == null || !ScreenRingSelection.contains(point.x, point.y,
                    logicalWidth, logicalHeight, baseRadius)) continue;
            double dx = point.x - logicalWidth * 0.5;
            double dy = point.y - logicalHeight * 0.5;
            eligible.add(new ScreenCandidate(entity, point, ScreenRingSelection.score(
                    dx * dx + dy * dy, entity == preferredTarget ? LOCK_RETAIN_MARGIN : 0.0)));
        }
        eligible.sort(java.util.Comparator.comparingDouble(ScreenCandidate::score));
        int trajectoriesRejected = 0;
        for (ScreenCandidate inside : eligible) {
            LivingEntity target = inside.target();
            Snapshot candidate = evaluateTargetCandidate(player, weapon, target, inside.point());
            if (candidate == null) {
                trajectoriesRejected++;
            } else {
                preferredTarget = candidate.target();
                DebugLog.infoOnChange("autobow.target",
                        target.getId() + ":" + candidate.state() + ":" + candidate.chargeReady()
                                + ":" + candidate.aimReady(),
                        "[AutoBow] lock target={} weapon={} circle={}px aim={} charge={} ready={} "
                                + "size={}x{} shooterMotion={} targetMotion={}",
                        target.getId(), weapon.type(), baseRadius, candidate.solution(),
                        candidate.chargeReady(), candidate.aimReady(),
                        target.getBbWidth(), target.getBbHeight(), player.getKnownMovement(),
                        target.getDeltaMovement());
                return candidate;
            }
        }

        DebugLog.infoOnChange("autobow.target",
                "none:" + available.size() + ":" + eligible.size() + ":" + trajectoriesRejected,
                "[AutoBow] no target: eligible={} insideRing={} rejectedByVisibilityOrTrajectory={} radius={}px",
                available.size(), eligible.size(), trajectoriesRejected, baseRadius);
        preferredTarget = null;

        if (mode.get() == AutomationMode.FULL_AUTO && player.isUsingItem()) {
            return Snapshot.idle(weapon);
        }

        return Snapshot.idle(weapon);
    }

    private List<LivingEntity> getPrioritizedCandidates(TargetingUtil.TargetingSettings settings) {
        // TargetManager/TargetingUtil supplies eligible targets in priority order.
        // Never pre-filter by proximity to an off-screen target: the ring is
        // an independent screen-space selection constraint.
        return TargetingUtil.findTargets(mc, settings);
    }

    private Snapshot refreshSnapshot(float tickDelta) {
        if (mc.player == null) {
            snapshotTick = Integer.MIN_VALUE;
            return lastSnapshot = Snapshot.hidden();
        }

        snapshotTick = mc.player.tickCount;
        lastSnapshot = evaluateSnapshot(tickDelta);
        return lastSnapshot;
    }

    private Snapshot getSnapshot(float tickDelta) {
        if (mc.player == null) {
            snapshotTick = Integer.MIN_VALUE;
            return Snapshot.hidden();
        }

        if (snapshotTick != mc.player.tickCount) {
            return refreshSnapshot(tickDelta);
        }
        return lastSnapshot;
    }

    private Snapshot evaluateTargetCandidate(LocalPlayer player,
                                             ActiveWeapon weapon,
                                             LivingEntity target,
                                             Vec2 point) {
        if (target == null || !target.isAlive() || target.isRemoved() || point == null) return null;

        boolean directlyVisible = hasDirectSightToTarget(player, target);
        if (toggles.get(TargetFilters.VISIBLE_ONLY) && !directlyVisible) {
            DebugLog.infoOnChange("autobow.reject." + target.getId(), "not-visible",
                    "[AutoBow] reject target={} reason=visible_only", target.getId());
            return null;
        }

        int plannedCharge = weapon.type() == WeaponType.BOW && bowRapidFire.get()
                ? Math.min(20, Math.max(1, getBowChargeThreshold())) : 20;
        ProjectileAimSolver.Solution computed = ProjectileAimSolver.forEntity(
                player, weapon.stack(), target, shouldUseFullPowerAim(weapon), plannedCharge);
        // Selection and shooting must use the same collision-checked result.
        // Do not lock a mob behind walls or hold a bow against an impossible shot.
        if (computed == null) {
            DebugLog.infoOnChange("autobow.reject." + target.getId(), "no-ballistic-hit",
                    "[AutoBow] reject target={} reason=no_verified_projectile_path visible={} distance={}",
                    target.getId(), directlyVisible, Math.sqrt(target.distanceToSqr(player)));
            return null;
        }
        Rotation normalized = computed.rotation().normalize();
        boolean trajectoryReady = true;
        boolean chargeReady = isChargeReady(player, weapon);
        boolean aimReady = normalized != null && RotationManager.INSTANCE.getServerRotation().approximatelyEquals(normalized, aimThreshold.get());
        boolean shootReady = mode.get() == AutomationMode.FULL_AUTO
                && chargeReady
                && aimReady
                && trajectoryReady
                && canAutoShoot(player, weapon, target);
        HudState state;
        if (shootReady) {
            state = HudState.SHOOT;
        } else if (!chargeReady) {
            state = HudState.WAIT;
        } else if (mode.get() == AutomationMode.AIM_ONLY) {
            state = HudState.SHOOT;
        } else {
            state = HudState.LOCK;
        }
        return Snapshot.withState(weapon, target, normalized, point, true, chargeReady, aimReady, state);
    }

    private TargetingUtil.TargetingSettings buildTargetingSettings() {
        return new TargetingUtil.TargetingSettings(
                range.get(),
                180.0f,
                toggles.get(TargetFilters.PLAYERS_ONLY),
                toggles.get(TargetFilters.IGNORE_FRIENDS),
                toggles.get(TargetFilters.IGNORE_STAFF),
                toggles.get(TargetFilters.IGNORE_ENEMIES),
                toggles.get(TargetFilters.IGNORE_NAKED),
                toggles.get(TargetFilters.IGNORE_ENTITIES),
                false,
                priority.get()
        );
    }

    private ActiveWeapon findActiveWeapon(LocalPlayer player) {
        if (player.isUsingItem()) {
            InteractionHand activeHand = player.getUsedItemHand();
            ItemStack activeStack = player.getUseItem();
            if (activeStack.is(Items.BOW)) {
                return new ActiveWeapon(activeHand, activeStack, WeaponType.BOW);
            }
            if (activeStack.is(Items.CROSSBOW)) {
                return new ActiveWeapon(activeHand, activeStack, WeaponType.CROSSBOW);
            }
            if (activeStack.is(Items.TRIDENT)) {
                return new ActiveWeapon(activeHand, activeStack, WeaponType.TRIDENT);
            }
        }

        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.is(Items.CROSSBOW) && CrossbowItem.isCharged(stack)) {
                return new ActiveWeapon(hand, stack, WeaponType.CROSSBOW);
            }
            if (stack.is(Items.BOW)) {
                return new ActiveWeapon(hand, stack, WeaponType.BOW);
            }
            if (stack.is(Items.CROSSBOW)) {
                return new ActiveWeapon(hand, stack, WeaponType.CROSSBOW);
            }
            if (stack.is(Items.TRIDENT)) {
                return new ActiveWeapon(hand, stack, WeaponType.TRIDENT);
            }
        }

        return null;
    }

    private boolean shouldAlwaysShowBow(LocalPlayer player, ActiveWeapon weapon) {
        return weapon.type() == WeaponType.BOW
                && (!player.isUsingItem() || player.getUsedItemHand() != weapon.hand());
    }

    private boolean canRotateForWeaponState(LocalPlayer player, ActiveWeapon weapon) {
        if (player == null || weapon == null) {
            return false;
        }

        return switch (weapon.type()) {
            case BOW, TRIDENT -> player.isUsingItem() && player.getUsedItemHand() == weapon.hand();
            case CROSSBOW -> isCrossbowReadyForRotation(player, weapon);
        };
    }

    private boolean isCrossbowReadyForRotation(LocalPlayer player, ActiveWeapon weapon) {
        if (CrossbowItem.isCharged(weapon.stack())) {
            return true;
        }

        // Crossbow should keep tracking throughout the whole charge window,
        // otherwise it feels inconsistent compared to bow and may never settle in time.
        return player.isUsingItem() && player.getUsedItemHand() == weapon.hand();
    }

    private boolean shouldHoldUseForFullAuto(Snapshot snapshot) {
        if (mode.get() != AutomationMode.FULL_AUTO || snapshot == null) {
            return false;
        }

        if (!snapshot.insideRadius() || snapshot.target() == null) {
            return false;
        }

        ActiveWeapon weapon = snapshot.weapon();
        if (weapon == null) {
            return false;
        }

        return switch (weapon.type()) {
            case BOW, TRIDENT -> false;
            case CROSSBOW -> !CrossbowItem.isCharged(weapon.stack());
        };
    }

    private boolean shouldUseFullPowerAim(ActiveWeapon weapon) {
        return weapon != null && weapon.type() == WeaponType.BOW;
    }

    private boolean isChargeReady(LocalPlayer player, ActiveWeapon weapon) {
        return switch (weapon.type()) {
            case BOW -> isBowChargeReady(player, weapon);
            case CROSSBOW -> CrossbowItem.isCharged(weapon.stack())
                    || (player.isUsingItem()
                    && player.getUsedItemHand() == weapon.hand()
                    && player.getTicksUsingItem() >= CrossbowItem.getChargeDuration(weapon.stack(), player) + CROSSBOW_RELEASE_EXTRA_TICKS);
            case TRIDENT -> player.isUsingItem()
                    && player.getUsedItemHand() == weapon.hand()
                    && player.getTicksUsingItem() > TRIDENT_THROW_THRESHOLD_TICKS + TRIDENT_RELEASE_EXTRA_TICKS;
        };
    }

    private int getBowChargeThreshold() {
        // A negative random offset must not permit a nearly zero-power shot.
        return Math.max(3, chargedTicks.get() + getChargedRandom());
    }

    private boolean isBowChargeReady(LocalPlayer player, ActiveWeapon weapon) {
        if (!player.isUsingItem() || player.getUsedItemHand() != weapon.hand()) {
            return false;
        }

        if (bowRapidFire.get()) {
            return player.getTicksUsingItem() > getBowChargeThreshold();
        }

        return player.getTicksUsingItem() >= BOW_FULL_PULL_RELEASE_TICKS;
    }

    private int getChargedRandom() {
        if (currentChargeRandom == null) {
            updateChargeRandom();
        }
        return currentChargeRandom;
    }

    private void updateChargeRandom() {
        int random = chargedRandom.get();
        if (random <= 0) {
            currentChargeRandom = 0;
            return;
        }

        int offset = (int) Math.round(ThreadLocalRandom.current().nextGaussian() * (random / 2.0));
        currentChargeRandom = Mth.clamp(offset, -random, random);
    }

    private Vec2 projectTargetToLogical(LivingEntity target, float logicalWidth, float logicalHeight, float tickDelta) {
        if (target == null || mc.gameRenderer == null || mc.player == null) {
            return null;
        }

        AABB box = target.getBoundingBox().inflate(TARGET_BOX_INFLATE);
        float centerX = logicalWidth * 0.5f;
        float centerY = logicalHeight * 0.5f;

        Vec3 eye = mc.gameRenderer.mainCamera().position();
        Vec3 look = mc.player.getViewVector(tickDelta).normalize();
        if (box.contains(eye)
                || box.clip(eye, eye.add(look.scale(range.get()))).isPresent()) {
            return new Vec2(centerX, centerY);
        }

        double[] xs = {box.minX, box.maxX};
        double[] ys = {box.minY, box.maxY};
        double[] zs = {box.minZ, box.maxZ};

        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        int projectedCount = 0;

        for (double x : xs) {
            for (double y : ys) {
                for (double z : zs) {
                    Vec2 projected = projectWorldToLogical(
                            new Vec3(x, y, z),
                            logicalWidth,
                            logicalHeight,
                            tickDelta
                    );
                    if (projected == null) {
                        continue;
                    }

                    minX = Math.min(minX, projected.x);
                    minY = Math.min(minY, projected.y);
                    maxX = Math.max(maxX, projected.x);
                    maxY = Math.max(maxY, projected.y);
                    projectedCount++;
                }
            }
        }

        Vec2 projectedCenter = projectWorldToLogical(box.getCenter(), logicalWidth, logicalHeight, tickDelta);
        if (projectedCenter != null) {
            minX = Math.min(minX, projectedCenter.x);
            minY = Math.min(minY, projectedCenter.y);
            maxX = Math.max(maxX, projectedCenter.x);
            maxY = Math.max(maxY, projectedCenter.y);
            projectedCount++;
        }

        if (projectedCount == 0) {
            return null;
        }

        return new Vec2(
                Mth.clamp(centerX, minX, maxX),
                Mth.clamp(centerY, minY, maxY)
        );
    }

    private Vec2 projectWorldToLogical(Vec3 worldPos, float logicalWidth, float logicalHeight, float tickDelta) {
        int framebufferWidth = mc.getWindow().getWidth();
        int framebufferHeight = mc.getWindow().getHeight();
        if (framebufferWidth <= 0 || framebufferHeight <= 0) {
            return null;
        }

        Vec3 screen = ScreenProjection.worldToScreen(worldPos, tickDelta);
        if (screen == null) {
            return null;
        }

        ViewportContext viewport = ViewportContext.current();
        double sourceWidth = viewport == null ? framebufferWidth : viewport.width();
        double sourceHeight = viewport == null ? framebufferHeight : viewport.height();
        float x = (float) ScreenRingSelection.toLogical(screen.x, sourceWidth, logicalWidth);
        float y = (float) ScreenRingSelection.toLogical(screen.y, sourceHeight, logicalHeight);
        return new Vec2(x, y);
    }

    private void renderStateText(TextRenderer textRenderer, float centerX, float y, HudState state) {
        if (state.label.isEmpty()) {
            return;
        }

        TextRenderer renderer = BuiltinFontCatalog.ONEST_MEDIUM.renderer(textRenderer);
        float width = (float) renderer.getWidth(state.label, false) * TEXT_SCALE;
        float height = (float) renderer.getHeight(false) * TEXT_SCALE;
        float x = centerX - width * 0.5f;
        float baseY = y - height * 0.5f;

        renderer.begin(TEXT_SCALE);
        renderer.render(state.label, x + 1.0f, baseY + 1.0f, new RenderColor(TEXT_SHADOW), false);
        renderer.render(state.label, x, baseY, new RenderColor(state.color), false);
        renderer.end();
    }

    private void drawCross(Renderer2D renderer, float centerX, float centerY, float halfLength, float thickness, int color) {
        drawThickLine(renderer, centerX - halfLength, centerY - halfLength, centerX + halfLength, centerY + halfLength, thickness, color);
        drawThickLine(renderer, centerX - halfLength, centerY + halfLength, centerX + halfLength, centerY - halfLength, thickness, color);
    }

    private void drawThickLine(Renderer2D renderer, float x1, float y1, float x2, float y2, float thickness, int color) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = Mth.sqrt(dx * dx + dy * dy);
        if (length <= 0.0f) {
            return;
        }

        float nx = -dy / length;
        float ny = dx / length;
        int layers = Math.max(1, Math.round(thickness));
        float start = -(layers - 1) * 0.5f;
        for (int i = 0; i < layers; i++) {
            float offset = start + i;
            float ox = nx * offset;
            float oy = ny * offset;
            renderer.line(x1 + ox, y1 + oy, x2 + ox, y2 + oy, color);
        }
    }

    private void tryAutoShoot(Snapshot snapshot) {
        LocalPlayer player = mc.player;
        MultiPlayerGameMode manager = mc.gameMode;
        if (player == null || manager == null) {
            return;
        }

        ActiveWeapon weapon = snapshot.weapon();
        if (weapon == null) {
            return;
        }

        long now = System.currentTimeMillis();
        long delayMs = (long) (delayBetweenShots.get() * 1000.0);
        if (!canAutoShoot(player, weapon, snapshot.target())) {
            return;
        }

        switch (weapon.type()) {
            case BOW, TRIDENT -> {
                if (now - lastShotAtMs < delayMs) {
                    return;
                }
                if (player.isUsingItem() && player.getUsedItemHand() == weapon.hand()) {
                    forceReleaseUse = true;
                    if (weapon.type() == WeaponType.BOW) {
                        updateChargeRandom();
                    }
                    lastShotAtMs = now;
                }
            }
            case CROSSBOW -> {
                if (player.isUsingItem() && player.getUsedItemHand() == weapon.hand()) {
                    if (player.getTicksUsingItem() >= CrossbowItem.getChargeDuration(weapon.stack(), player)) {
                        forceReleaseUse = true;
                    }
                    return;
                }
                if (now - lastShotAtMs < delayMs) {
                    return;
                }
                if (CrossbowItem.isCharged(weapon.stack())) {
                    Rotation shotRotation = snapshot.solution();
                    if (shotRotation != null && pendingUse == null) {
                        pendingUse = new PendingUse(
                                weapon.hand(),
                                shotRotation,
                                player.tickCount + 1,
                                UseAction.FIRE_CROSSBOW
                        );
                    }
                }
            }
        }
    }

    private void tryAutoDraw(Snapshot snapshot) {
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null) {
            return;
        }

        ActiveWeapon weapon = snapshot.weapon();
        if (weapon == null) {
            return;
        }

        if (player.isUsingItem()) {
            return;
        }

        if (snapshot.target() == null || !snapshot.insideRadius()) {
            return;
        }

        switch (weapon.type()) {
            case BOW, TRIDENT -> {
            }
            case CROSSBOW -> {
                if (!CrossbowItem.isCharged(weapon.stack()) && pendingUse == null) {
                    pendingUse = new PendingUse(
                            weapon.hand(),
                            snapshot.solution(),
                            player.tickCount + 1,
                            UseAction.CHARGE_CROSSBOW
                    );
                }
            }
        }
    }

    private boolean canUseStackForAction(LocalPlayer player, ItemStack stack, UseAction action) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        return switch (action) {
            case CHARGE_CROSSBOW -> !player.isUsingItem()
                    && stack.is(Items.CROSSBOW)
                    && !CrossbowItem.isCharged(stack);
            case FIRE_CROSSBOW -> stack.is(Items.CROSSBOW) && CrossbowItem.isCharged(stack);
        };
    }

    private boolean canAutoShoot(LocalPlayer player, ActiveWeapon weapon, LivingEntity target) {
        if (player == null || weapon == null || target == null) {
            return false;
        }

        long now = System.currentTimeMillis();
        long delayMs = (long) (delayBetweenShots.get() * 1000.0);
        if (weapon.type() != WeaponType.CROSSBOW && now - lastShotAtMs < delayMs) {
            return false;
        }

        Rotation serverRotation = RotationManager.INSTANCE.getServerRotation();
        if (serverRotation == null) {
            return false;
        }

        if (!ProjectileAimSolver.hitsEntity(player, weapon.stack(), serverRotation, target,
                shouldUseFullPowerAim(weapon))) return false;

        return weapon.type() != WeaponType.CROSSBOW || player.isUsingItem() || now - lastShotAtMs >= delayMs;
    }

    private boolean hasDirectSightToTarget(LocalPlayer player, LivingEntity target) {
        if (player == null || target == null) {
            return false;
        }

        return RotationRaytrace.canSeeBox(
                player.getEyePosition(),
                target.getBoundingBox(),
                range.get(),
                0.0
        );
    }

    private enum WeaponType {
        BOW,
        CROSSBOW,
        TRIDENT
    }

    private enum AutomationMode implements EnumValue.IdProvider {
        FULL_AUTO("full_auto"),
        AIM_ONLY("aim_only");

        private final String id;

        AutomationMode(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }
    }

    private enum HudState {
        IDLE("", COLOR_IDLE, false),
        WAIT("WAIT", COLOR_WAIT, false),
        LOCK("LOCK", COLOR_LOCK, false),
        SHOOT("SHOOT", COLOR_SHOOT, false);

        private final String label;
        private final int color;
        private final boolean cross;

        HudState(String label, int color, boolean cross) {
            this.label = label;
            this.color = color;
            this.cross = cross;
        }
    }

    private enum UseAction {
        CHARGE_CROSSBOW,
        FIRE_CROSSBOW
    }

    private record ScreenCandidate(LivingEntity target, Vec2 point, double score) { }

    private record ActiveWeapon(InteractionHand hand, ItemStack stack, WeaponType type) {
    }

    private record PendingUse(InteractionHand hand, Rotation rotation, int expireAge, UseAction action) {
    }

    private record Snapshot(
            boolean showHud,
            ActiveWeapon weapon,
            LivingEntity target,
            Rotation solution,
            Vec2 logicalPoint,
            boolean insideRadius,
            boolean chargeReady,
            boolean aimReady,
            HudState state
    ) {
        private static Snapshot hidden() {
            return new Snapshot(false, null, null, null, null, false, false, false, HudState.IDLE);
        }

        private static Snapshot idle(ActiveWeapon weapon) {
            return new Snapshot(true, weapon, null, null, null, false, false, false, HudState.IDLE);
        }

        private static Snapshot waiting(ActiveWeapon weapon) {
            return new Snapshot(true, weapon, null, null, null, false, false, false, HudState.WAIT);
        }

        private static Snapshot withState(ActiveWeapon weapon,
                                          LivingEntity target,
                                          Rotation solution,
                                          Vec2 logicalPoint,
                                          boolean insideRadius,
                                          boolean chargeReady,
                                          boolean aimReady,
                                          HudState state) {
            return new Snapshot(true, weapon, target, solution, logicalPoint, insideRadius, chargeReady, aimReady, state);
        }

        private boolean shouldRotate() {
            return weapon != null && target != null && solution != null && insideRadius;
        }
    }
}

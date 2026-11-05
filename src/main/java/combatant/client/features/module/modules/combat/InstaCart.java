/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.module.modules.combat;

import combatant.client.config.common.CommonSettingSchemas;
import combatant.client.config.common.impl.TargetFilters;
import combatant.client.config.values.BindMode;
import combatant.client.config.values.BooleanMapValue;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.KeyBindValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.RotationUpdateEvent;
import combatant.client.events.impl.EventPostSync;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.util.logging.DebugLog;
import combatant.client.events.impl.KeybindIsPressedEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.gui.clickgui.settings.FunctionBindSetting;
import combatant.client.util.target.hud.TargetHudStatus;
import combatant.client.features.module.Notifier;
import combatant.client.features.module.modules.combat.instacart.InstaCartPlanner;
import combatant.client.features.module.modules.combat.instacart.InstaCartPlanner.Shot;
import combatant.client.features.module.modules.combat.instacart.InstaCartPlanner.Site;
import combatant.client.util.aiming.RestrictedSingleUseAction;
import combatant.client.features.module.modules.combat.instacart.InstaCartCycleRules;
import combatant.client.util.aiming.RotationManager;
import combatant.client.util.aiming.RotationTarget;
import combatant.client.util.aiming.data.Rotation;
import combatant.client.util.aiming.features.MovementCorrection;
import combatant.client.util.item.EnchantUtil;
import combatant.client.util.player.inventory.InventoryActionKind;
import combatant.client.util.player.inventory.InventorySearchScope;
import combatant.client.util.player.inventory.InventorySwap;
import combatant.client.util.player.inventory.PacketWindow;
import combatant.client.util.player.inventory.InventorySwapRequest;
import combatant.client.util.player.inventory.InventorySwapVisibility;
import combatant.client.util.player.inventory.SlotResult;
import combatant.client.util.target.TargetManager;
import combatant.client.util.target.TargetingUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.vehicle.minecart.MinecartTNT;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.function.Predicate;

@ModuleInfo(id = "instacart", displayName = "InstaCart", aliases = {"CartBomb"},
        category = ModuleCategory.COMBAT, subcategory = ModuleSubcategory.ATTACK,
        description = "module.instacart.description")
public final class InstaCart extends Module {
    private static final int ROTATION_PRIORITY = 65;
    private static final int TARGET_PRIORITY = 550;
    private static final int SITE_RECHECK_TICKS = 4;
    private static final int DRAW_TIMEOUT_TICKS = 32;
    private static final int BOW_SELECT_TIMEOUT_TICKS = 20;
    private static final int PLACEMENT_TIMEOUT_TICKS = 12;

    private final Minecraft mc = Minecraft.getInstance();
    private final BooleanValue automatic = bool("instaCartAutomatic", "automatic", true);
    private final FunctionBindSetting triggerKey = action("trigger_key", new KeyBindValue("instaCartTriggerKey", "NONE"), BindMode.PRESS);
    private final NumberValue<Double> targetRange = num("instaCartTargetRange", "target_range", 9.0, 3.0, 16.0);
    private final NumberValue<Double> range = numCommon("instaCartRange", "place_range", CommonSettingSchemas.PLACEMENT_RANGE, 5.5, 3.0, 8.0);
    private final NumberValue<Integer> bowChargeTicks = num("instaCartBowCharge", "bow_charge_ticks", 18, 6, 20);
    private final NumberValue<Integer> fastExtraTicks = num("instaCartFastMargin", "fast_margin_ticks", 0, 0, 5);
    // Match AutoCrystal's common damage and prediction controls and defaults.
    private final NumberValue<Float> minDamage = numCommon("instaCartMinDamage", CommonSettingSchemas.MIN_DAMAGE, 4.0f, 0.0f, 36.0f);
    private final NumberValue<Float> maxSelfDamage = numCommon("instaCartMaxSelfDamage", CommonSettingSchemas.MAX_SELF_DAMAGE, 8.0f, 0.0f, 36.0f);
    private final NumberValue<Float> faceplaceHealth = numCommon("instaCartFaceplaceHealth", CommonSettingSchemas.FACEPLACE_HEALTH, 10.0f, 0.0f, 36.0f);
    private final NumberValue<Integer> predictTicks = numCommon("instaCartPredictTicks", CommonSettingSchemas.PLACEMENT_PREDICTION, 0, 0, 20);
    private final NumberValue<Integer> selfPredictTicks = numCommon("instaCartSelfPredictTicks", CommonSettingSchemas.SELF_PREDICT_TICKS, 0, 0, 20);
    private final EnumValue<MovementCorrection> movementCorrection = enumCommon(
            "instaCartMovementCorrection", "movement_correction",
            CommonSettingSchemas.PLACEMENT_MOVEMENT_CORRECTION,
            MovementCorrection.SILENT, MovementCorrection.values());
    private final BooleanMapValue targetToggles = groupCommon(
            "instaCartTargets", "targets", CommonSettingSchemas.TARGET_FILTERS);
    private final EnumValue<TargetingUtil.TargetPriority> priority = enumCommon(
            "instaCartPriority", "priority", CommonSettingSchemas.COMBAT_PRIORITY,
            TargetingUtil.TargetPriority.DISTANCE, TargetingUtil.TargetPriority.values());

    private enum Phase { IDLE, WAIT_SITE, PRE_RAIL, PRE_CART, WAIT_SHOT, WAIT_BOW, DRAWING, POST_RAIL, POST_CART, WAIT_IMPACT }
    private enum Ready { READY, NO_BOW, NO_RAIL, NO_CART }
    private enum PlacementAction { RAIL, CART }
    private record Selection(LivingEntity target, Site site, int existingCartId) { }

    private Phase phase = Phase.IDLE;
    private Site site;
    private Shot pendingShot;
    private Shot queuedRelease;
    private PlacementAction queuedPlacement;
    private int lockedBowSlot = -1;
    private boolean cartSeen;
    private int existingCartId = -1;
    private int observedCartId = -1;
    private int shotAttempts;
    private int shotInvalidTicks;
    private int targetEntityId = -1;
    private int previousSlot = -1;
    private int phaseTicks;
    private int flightTicks;
    private int cooldown;
    private int nextPlacementAttemptTick;
    private int placementAttempts;
    private boolean fired;
    private int siteRecheck;
    private Selection cachedSelection;
    // Retain failed positions per target long enough to explore alternatives.
    private final Map<Integer, Map<BlockPos, Integer>> rejectedSites = new HashMap<>();
    private double closestArrowDistanceSq = Double.POSITIVE_INFINITY;
    private boolean arrowObserved;
    private int lastTargetRecheckTick = -1;
    private String planningStatus = "Waiting for target";
    private String lastAbortReason = "";
    private String shotWaitReason = "";

    @Override
    public void onEnable() {
        DebugLog.info("[InstaCart] enabled");
        reset();
        invalidateSiteCache();
        rejectedSites.clear();
        // Function binds remain registered for UI/config; discard presses queued while disabled.
        while (triggerKey.isPressed()) { }
    }

    @Override
    public void onDisable() {
        DebugLog.info("[InstaCart] disabled, phase={} target={}", phase, targetEntityId);
        reset();
        invalidateSiteCache();
        rejectedSites.clear();
    }

    /** Vanilla handleKeybinds() releases the held bow if keyUse.isDown() is false.
     * A synthetic useItem() call alone does NOT hold the bow across ticks.
     * Run after AutoBow's key handler so another module cannot release our shot.
     */
    @EventHandler(priority = -500)
    private void onUseKey(KeybindIsPressedEvent event) {
        if (isEnabled() && mc.options != null && event.getKeyBinding() == mc.options.keyUse
                && InstaCartCycleRules.shouldHoldBow(phase == Phase.DRAWING,
                        lockedBowSlot >= 0 && InventorySwap.INSTANCE.clientSelectedSlot() == lockedBowSlot))
            event.setPressed(true);
    }

    /** Placement needs a permitted hotbar change. EventPostSync is too late:
     * PacketWindow.slotChangeAllowed() is false after the movement packet.
     * The last tick has already transmitted the requested placement rotation. */
    @EventHandler(priority = 1000)
    private void onEarlyGameTick(GameTickEvent event) {
        if (isEnabled()) performQueuedPlacement();
    }

    /** The rotation callback runs before sendPosition(). Release only after its movement packet. */
    @EventHandler(priority = -50)
    private void onPostSync(EventPostSync event) {
        if (!isEnabled()) return;
        if (phase != Phase.DRAWING || queuedRelease == null) return;
        LocalPlayer player = mc.player;
        Shot shot = queuedRelease;
        if (player == null || mc.gameMode == null || lockedBowSlot < 0) {
            queuedRelease = null;
            traceBlocker("release", "Release dropped: no player, game mode or bow slot");
            return;
        }
        // Validate the rotation the server actually received, not the ideal
        // rotation before sensitivity quantization/another rotation consumer.
        Rotation serverRotation = RotationManager.INSTANCE.getServerRotation();
        boolean rotationSent = serverRotation != null
                && RotationManager.INSTANCE.ownsActiveRotation(this)
                && serverRotation.approximatelyEquals(shot.rotation(), 0.5f);
        if (!InstaCartCycleRules.canRelease(phase == Phase.DRAWING,
                player.isUsingItem() && player.getUseItem().is(Items.BOW),
                InventorySwap.INSTANCE.clientSelectedSlot() == lockedBowSlot, rotationSent)) {
            traceBlocker("release", "Release waiting: rotation=" + rotationSent
                    + " usingBow=" + (player.isUsingItem() && player.getUseItem().is(Items.BOW))
                    + " slotMatches=" + (InventorySwap.INSTANCE.clientSelectedSlot() == lockedBowSlot));
            return;
        }
        LivingEntity target = resolveTarget();
        if (target == null || site == null || TargetingUtil.distanceToEntityBoxSq(
                player.getEyePosition(), target) > targetRange.get() * targetRange.get()) {
            abort("Lost target while aiming"); return;
        }
        MinecartTNT cart = existingCartId >= 0 ? findCart() : null;
        if (existingCartId >= 0 && cart == null) { abort("Existing cart disappeared before shot"); return; }
        int actualCharge = player.getTicksUsingItem();
        Shot verified = validateCurrentShot(serverRotation, actualCharge);
        if (verified == null) {
            // Do not compare against a NEW optimizer result. The exact angle
            // already sent to the server must still hit the minecart in time.
            queuedRelease = null;
            shotWaitReason = "Synchronized shot no longer intersects cart";
            traceBlocker("release", shotWaitReason + " desired=" + shot.rotation()
                    + " sent=" + serverRotation + " plannedCharge=" + shot.chargeTicks()
                    + " actualCharge=" + actualCharge);
            // A one-packet-old angle can miss after a small change in shooter
            // position. Do not abandon an already confirmed cart or discard
            // charge because of this transient mismatch. drawAndShoot() will
            // compute another angle on the next tick with the new origin.
            if (existingCartId < 0) prepareBeforeShot("Shot invalidated by actual position");
            return;
        }
        Vec3 explosion = cart == null ? site.explosionPoint() : cart.position().add(0.0, 0.35, 0.0);
        if (!InstaCartPlanner.isDamageAcceptable(player, target, explosion, verified, settings())) {
            shotWaitReason = "Waiting for safe explosion damage";
            traceBlocker("release", shotWaitReason);
            queuedRelease = null;
            if (existingCartId < 0) prepareBeforeShot("Unsafe explosion at release");
            // When the minecart already exists, the target/self damage estimate
            // may change next tick; keep the draw and recheck the same cart.
            return;
        }
        // The client's held slot must still be the bow when the release packet is sent.
        queuedRelease = null;
        mc.gameMode.releaseUsingItem(player);
        DebugLog.info("[InstaCart] release target={} rail={} travel={}t charge={}t existingCart={}",
                targetEntityId, site.rail(), verified.impactTicks(), actualCharge, existingCartId);
        shotAttempts++;
        closestArrowDistanceSq = Double.POSITIVE_INFINITY;
        arrowObserved = false;
        pendingShot = verified;
        fired = true;
        flightTicks = 0;
        lockedBowSlot = -1;
        shotWaitReason = "";
        enter(switch (InstaCartCycleRules.afterRelease(existingCartId >= 0, site.existingRail())) {
            case SHOOT_EXISTING -> Phase.WAIT_IMPACT;
            case PLACE_CART -> Phase.POST_CART;
            case PLACE_RAIL -> Phase.POST_RAIL;
        });
    }

    /** Block interactions are issued before this tick's movement packet, using
     * the rotation synchronized during the previous tick. A silent slot change
     * is forbidden after movement, so do not move this back to EventPostSync. */
    private void performQueuedPlacement() {
        PlacementAction requested = queuedPlacement;
        queuedPlacement = null;
        if (requested == null) return;
        if (mc.player == null || mc.level == null || site == null) {
            traceBlocker("placement", "Queued " + requested + " dropped: world, player or site unavailable");
            return;
        }
        boolean correctPhase = requested == PlacementAction.RAIL
                ? phase == Phase.PRE_RAIL || phase == Phase.POST_RAIL
                : phase == Phase.PRE_CART || phase == Phase.POST_CART;
        if (!correctPhase || phaseTicks < nextPlacementAttemptTick) {
            traceBlocker("placement", "Queued " + requested + " dropped: phase=" + phase
                    + " expected=" + requested + " delayNotElapsed="
                    + (phaseTicks < nextPlacementAttemptTick));
            return;
        }
        boolean railExists = mc.level.getBlockState(site.rail()).is(BlockTags.RAILS);
        if (requested == PlacementAction.RAIL && railExists) {
            traceBlocker("placement", "Skipped RAIL: rail already exists at " + site.rail());
            return;
        }
        if (requested == PlacementAction.CART && (!railExists || findCart() != null)) {
            traceBlocker("placement", "Skipped CART: railExists=" + railExists
                    + " cartExists=" + (findCart() != null));
            return;
        }
        BlockHitResult hit = InstaCartPlanner.interaction(mc.level, mc.player, site.rail(),
                requested == PlacementAction.CART, range.get());
        if (hit == null) {
            traceBlocker("placement-raycast", "No interaction hit for " + requested + " at " + site.rail());
            return;
        }
        Rotation requiredRotation = Rotation.lookingAt(hit.getLocation(), mc.player.getEyePosition()).normalize();
        if (!RotationManager.INSTANCE.ownsActiveRotation(this)
                || !RotationManager.INSTANCE.getServerRotation().approximatelyEquals(requiredRotation, 4.0f)) {
            traceBlocker("placement-rotation", "Rotation not synchronized for " + requested + " at " + site.rail());
            return;
        }
        if (!PacketWindow.INSTANCE.slotChangeAllowed()
                || !PacketWindow.INSTANCE.actionAllowed(InventoryActionKind.BLOCK_INTERACT)) {
            traceBlocker("placement", "Interaction window blocked for " + requested
                    + " postMovement=" + PacketWindow.INSTANCE.isPostMovement()
                    + " slotAllowed=" + PacketWindow.INSTANCE.slotChangeAllowed()
                    + " blockAllowed=" + PacketWindow.INSTANCE.actionAllowed(InventoryActionKind.BLOCK_INTERACT));
            return;
        }
        boolean sent = useOnBlock(requested == PlacementAction.RAIL
                ? InstaCart::isRail : stack -> stack.is(Items.TNT_MINECART), hit);
        placementAttempts++;
        if (!sent) traceBlocker("placement", "InventorySwap or local useItemOn rejected " + requested
                + " at " + site.rail());
        nextPlacementAttemptTick = phaseTicks + (sent ? (requested == PlacementAction.RAIL ? 3 : 4) : 1);
        DebugLog.info(
                "[InstaCart] {} attempt={} sent={} rail={} elapsed={} selectedSlot={} postMovement={} slotAllowed={} blockAllowed={}",
                requested, placementAttempts, sent, site.rail(), phaseTicks,
                InventorySwap.INSTANCE.clientSelectedSlot(), PacketWindow.INSTANCE.isPostMovement(),
                PacketWindow.INSTANCE.slotChangeAllowed(),
                PacketWindow.INSTANCE.actionAllowed(InventoryActionKind.BLOCK_INTERACT));
    }

    @EventHandler(priority = 22)
    private void onRotationUpdate(RotationUpdateEvent event) {
        if (event.getType() != RotationUpdateEvent.Type.PRE || !isEnabled()) return;
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gameMode == null || !player.isAlive()) {
            reset();
            invalidateSiteCache();
            return;
        }
        boolean manualPress = triggerKey.isPressed();
        if (cooldown > 0) cooldown--;

        if (phase == Phase.IDLE) {
            Selection selected = acquireTarget(player, manualPress);
            if (selected.target() == null) {
                TargetManager.clear(this);
                planningStatus = "Waiting for target";
                traceBlocker("target", "No eligible combat targets");
                if (manualPress) Notifier.warning(I18n.get("notification.instacart.no_target"));
                return;
            }
            Ready ready = checkItems(selected.site(), selected.existingCartId());
            boolean usable = selected.site() != null && ready == Ready.READY;
            String status = selected.site() == null ? "waiting_site"
                    : ready == Ready.READY ? "ready"
                    : ready.name().toLowerCase(Locale.ROOT);
            planningStatus = selected.site() == null ? planningStatus
                    : ready != Ready.READY ? switch (ready) {
                        case NO_BOW -> "No Flame bow / arrows";
                        case NO_RAIL -> "No rails";
                        case NO_CART -> "No TNT minecart";
                        default -> "Ready";
                    } : selected.site().fastShot() == null
                    ? "Preparing rail/cart before shot"
                    : (automatic.get() ? "Ready " : "Press trigger ") + "Fast";
            TargetManager.publish(this, selected.target(), TargetManager.Source.MODULE,
                    TARGET_PRIORITY, status, usable, usable ? 1 : 0);
            DebugLog.infoOnChange("instacart.availability", selected.target().getId() + ":" + status + ":" + planningStatus,
                    "[InstaCart] target={} plan={} status={} ready={} cooldown={} automatic={}",
                    selected.target().getId(), planningStatus, status, ready, cooldown, automatic.get());
            if (cooldown > 0) return;
            if (player.isUsingItem()) {
                planningStatus = "Player is already using an item";
                return;
            }
            if (!automatic.get() && !manualPress) return;
            if (selected.site() == null) {
                if (manualPress) Notifier.warning(I18n.get("notification.instacart.no_position")
                        + ": " + planningStatus);
                return;
            }
            if (ready != Ready.READY) {
                if (manualPress) warnMissing(ready);
                return;
            }
            begin(selected.target(), selected.site(), selected.existingCartId());
            return;
        }

        LivingEntity target = resolveTarget();
        if (target == null || TargetingUtil.distanceToEntityBoxSq(player.getEyePosition(), target)
                > targetRange.get() * targetRange.get()) {
            abort("Target lost / outside range"); return;
        }
        // While charging, do not keep the bow drawn at a stale target or a failed
        // rail site. We can start another candidate without launching a blind shot.
        if (!fired && (lastTargetRecheckTick < 0
                || player.tickCount - lastTargetRecheckTick >= SITE_RECHECK_TICKS)) {
            lastTargetRecheckTick = player.tickCount;
            List<LivingEntity> allowed = TargetingUtil.findTargets(mc, targetingSettings());
            if (!allowed.contains(target)) {
                abort("Target changed / no longer eligible");
                return;
            }
            if (existingCartId < 0 && (phase == Phase.DRAWING || phase == Phase.WAIT_BOW)
                    && !InstaCartPlanner.stillPlaceable(mc.level, player, site, range.get())) {
                prepareBeforeShot("Support not reachable while aiming");
                return;
            }
        }
        phaseTicks++;
        if (fired) flightTicks++;
        TargetManager.publish(this, target, TargetManager.Source.MODULE, TARGET_PRIORITY,
                phase.name().toLowerCase(Locale.ROOT), true, site == null ? 0 : 1);
        switch (phase) {
            case WAIT_SITE -> {
                if (InstaCartPlanner.stillPlaceable(mc.level, player, site, range.get()))
                    enter(mc.level.getBlockState(site.rail()).is(BlockTags.RAILS)
                            ? Phase.PRE_CART : Phase.PRE_RAIL);
                else if (phaseTicks > 16) abort("Support not reachable after waiting for placement");
            }
            case PRE_RAIL, POST_RAIL -> placeRail();
            case PRE_CART, POST_CART -> placeCart();
            case WAIT_SHOT -> waitForShot(player, target);
            case WAIT_BOW -> {
                if (existingCartId >= 0 && findCart() == null) { abort("Existing minecart disappeared"); break; }
                if (!InstaCartCycleRules.shouldRetryBow(false, phaseTicks, BOW_SELECT_TIMEOUT_TICKS)) {
                    abort("Cannot select/use Flame bow"); break;
                }
                startBow(); // Retry if the slot transaction is forbidden in this packet window.
            }
            case DRAWING -> drawAndShoot(player, target);
            case WAIT_IMPACT -> {
                // A locally sent interaction does not prove that the server placed a cart.
                MinecartTNT current = findCart();
                if (current != null) {
                    cartSeen = true;
                    observedCartId = current.getId();
                    // Observe real arrow entities after a server-confirmed
                    // release. No arrow nearby indicates a packet/use problem;
                    // a closest pass outside the cart indicates ballistic miss.
                    AABB flightArea = new AABB(player.getEyePosition(), current.position()).inflate(2.5);
                    for (AbstractArrow arrow : mc.level.getEntitiesOfClass(AbstractArrow.class, flightArea)) {
                        arrowObserved = true;
                        closestArrowDistanceSq = Math.min(closestArrowDistanceSq,
                                arrow.position().distanceToSqr(current.getBoundingBox().getCenter()));
                    }
                }
                if (cartSeen && current == null) { finish(); break; }
                if (phaseTicks > Math.max(16,
                        pendingShot == null ? 16 : pendingShot.impactTicks() + 10)) {
                    MinecartTNT surviving = findCart();
                    if (surviving == null && cartSeen) finish();
                    else if (surviving != null) {
                        // Surviving an arrow is evidence that THIS SHOT failed,
                        // not that the cart or its rail is unusable. Keep the
                        // confirmed entity and recompute a fresh charge/aim
                        // against its actual hitbox before attempting a retry.
                        existingCartId = surviving.getId();
                        observedCartId = existingCartId;
                        pendingShot = null;
                        DebugLog.info("[InstaCart] minecart id={} still alive after shot {}; "
                                        + "arrowObserved={} closestArrowCenter={} blocks; re-aiming same cart",
                                existingCartId, shotAttempts, arrowObserved,
                                arrowObserved ? String.format(java.util.Locale.ROOT, "%.2f",
                                        Math.sqrt(closestArrowDistanceSq)) : "unknown");
                        enter(Phase.WAIT_SHOT);
                    } else {
                        abort("No minecart was confirmed after release");
                    }
                }
            }
            default -> { }
        }
    }

    private InstaCartPlanner.Settings settings() {
        return new InstaCartPlanner.Settings(range.get(), minDamage.get(), maxSelfDamage.get(),
                faceplaceHealth.get(), predictTicks.get(), selfPredictTicks.get(),
                bowChargeTicks.get(), fastExtraTicks.get());
    }

    private TargetingUtil.TargetingSettings targetingSettings() {
        return new TargetingUtil.TargetingSettings(
                targetRange.get(), 180.0f,
                targetToggles.get(TargetFilters.PLAYERS_ONLY), targetToggles.get(TargetFilters.IGNORE_FRIENDS),
                targetToggles.get(TargetFilters.IGNORE_STAFF), targetToggles.get(TargetFilters.IGNORE_ENEMIES),
                targetToggles.get(TargetFilters.IGNORE_NAKED), targetToggles.get(TargetFilters.IGNORE_ENTITIES),
                targetToggles.get(TargetFilters.VISIBLE_ONLY), priority.get());
    }

    private Set<BlockPos> exclusions(int targetId, int tick) {
        Map<BlockPos, Integer> sites = rejectedSites.get(targetId);
        if (sites == null) return Set.of();
        sites.entrySet().removeIf(e -> e.getValue() <= tick);
        return new HashSet<>(sites.keySet());
    }

    private void rejectSite(int targetId, BlockPos pos) {
        rejectSite(targetId, pos, 50);
    }

    private void rejectSite(int targetId, BlockPos pos, int cooldownTicks) {
        // Entity ids can be negative (e.g. dummy entities in singleplayer).
        // -1 is our sentinel, not a general validity check for entity ids.
        if (mc.player == null || pos == null || !InstaCartCycleRules.isKnownTargetId(targetId)) return;
        rejectedSites.computeIfAbsent(targetId, unused -> new HashMap<>())
                .put(pos.immutable(), mc.player.tickCount + cooldownTicks);
        DebugLog.info("[InstaCart] reject site target={} pos={} untilTick={} reason=failed-plan",
                targetId, pos, mc.player.tickCount + cooldownTicks);
        invalidateSiteCache();
    }

    private Selection acquireTarget(LocalPlayer player, boolean refresh) {
        List<LivingEntity> candidates = TargetingUtil.findTargets(mc, targetingSettings());
        if (candidates.isEmpty()) return new Selection(null, null, -1);
        LivingEntity managed = TargetManager.getTarget(false);
        if (managed != null && candidates.contains(managed)) {
            candidates = new ArrayList<>(candidates);
            candidates.remove(managed);
            candidates.add(0, managed);
        }
        if (!refresh && siteRecheck-- > 0 && cachedSelection != null
                && candidates.contains(cachedSelection.target())) {
            Selection previous = cachedSelection;
            // Cache negative plans too. Otherwise an impossible target causes
            // a full 48-site ballistic search on EVERY tick, stalling the client.
            if (previous.site() == null) return previous;
            if ((previous.existingCartId() >= 0
                    && mc.level.getEntity(previous.existingCartId()) instanceof MinecartTNT cart
                    && cart.isAlive())
                    || (previous.existingCartId() < 0 && !exclusions(previous.target().getId(), player.tickCount)
                        .contains(previous.site().rail())
                        && InstaCartPlanner.stillPlaceable(mc.level, player, previous.site(), range.get()))) {
                return previous;
            }
        }
        siteRecheck = SITE_RECHECK_TICKS;
        Selection best = null;
        String blocked = "No viable position";
        // If earlier targets are obstructed, continue through the entire list.
        // Once a workable plan exists, compare two additional targets without
        // evaluating dozens of expensive independent trajectory searches.
        int evaluatedWithPlan = 0;
        for (LivingEntity candidate : candidates) {
            if (best != null && evaluatedWithPlan++ >= 2) break;
            InstaCartPlanner.ExistingCart existing = InstaCartPlanner.findExistingCart(
                    mc.level, player, candidate, settings(), targetRange.get());
            if (existing != null) {
                Site planned = existing.site();
                if (best == null || InstaCartPlanner.isBetter(planned, best.site()))
                    best = new Selection(candidate, planned, existing.cart().getId());
                continue;
            }
            InstaCartPlanner.SearchResult result = InstaCartPlanner.findBestDetailed(mc.level, player,
                    candidate, settings(), exclusions(candidate.getId(), player.tickCount));
            Site planned = result.site();
            if (best == null) blocked = result.reason();
            if (planned != null && (best == null || InstaCartPlanner.isBetter(planned, best.site())))
                best = new Selection(candidate, planned, -1);
        }
        cachedSelection = best != null ? best : new Selection(candidates.get(0), null, -1);
        planningStatus = best != null ? "Ready" : blocked;
        String decision = cachedSelection.target().getId() + ":" + (cachedSelection.site() == null
                ? "none:" + planningStatus : cachedSelection.site().rail().toShortString());
        DebugLog.infoOnChange("instacart.plan", decision,
                "[InstaCart] plan target={} rail={} existingCart={} reason={}",
                cachedSelection.target().getId(), cachedSelection.site() == null ? "-" : cachedSelection.site().rail(),
                cachedSelection.existingCartId(), planningStatus);
        return cachedSelection;
    }

    private void begin(LivingEntity target, Site planned, int existingCartEntityId) {
        targetEntityId = target.getId();
        lastTargetRecheckTick = mc.player == null ? -1 : mc.player.tickCount;
        site = planned;
        lastAbortReason = "";
        shotWaitReason = "";
        existingCartId = existingCartEntityId;
        pendingShot = planned.fastShot();
        fired = false;
        flightTicks = 0;
        cartSeen = existingCartId >= 0;
        observedCartId = existingCartId;
        shotAttempts = 0;
        shotInvalidTicks = 0;
        queuedRelease = null;
        // One automatic cycle with an opportunistic setup route: if the rail
        // cannot be populated before arrow impact, place rail/cart FIRST, then
        // shoot the confirmed entity. This is not a user-selectable slow mode.
        enter(InstaCartCycleRules.shouldPrepareBeforeShot(existingCartId >= 0, pendingShot != null)
                ? planned.existingRail() ? Phase.PRE_CART : Phase.PRE_RAIL
                : Phase.WAIT_BOW);
        DebugLog.info("[InstaCart] begin target={} rail={} existingCart={} shot={}",
                targetEntityId, site.rail(), existingCartId, pendingShot);
    }

    /** A previously selected rail can be established before drawing/releasing.
     * Never send a blind arrow when the ballistic window collapses. */
    private void prepareBeforeShot(String cause) {
        if (site == null || mc.level == null || mc.player == null || existingCartId >= 0) {
            abort(cause); return;
        }
        if (!InstaCartPlanner.stillPlaceable(mc.level, mc.player, site, range.get())) {
            cancelLocalBow();
            queuedRelease = null;
            pendingShot = null;
            enter(Phase.WAIT_SITE);
            DebugLog.info("[InstaCart] waiting to reach support {} reason={}", site.rail(), cause);
            return;
        }
        // Cancel the local draw before changing item slots. Do not release a
        // Flame arrow into the world just to switch to rail/cart placement.
        cancelLocalBow();
        queuedRelease = null;
        pendingShot = null;
        shotInvalidTicks = 0;
        enter(mc.level.getBlockState(site.rail()).is(BlockTags.RAILS)
                ? Phase.PRE_CART : Phase.PRE_RAIL);
        DebugLog.info("[InstaCart] switched to prepare-before-shot target={} rail={} reason={}",
                targetEntityId, site.rail(), cause);
    }

    private void cancelLocalBow() {
        if (mc.player != null && mc.player.isUsingItem()
                && mc.player.getUseItem().is(Items.BOW)) mc.player.stopUsingItem();
        lockedBowSlot = -1;
        if (previousSlot >= 0) InventorySwap.INSTANCE.restoreHotbar(previousSlot);
        previousSlot = -1;
    }

    private void parkBowUntilShot(String cause) {
        cancelLocalBow();
        queuedRelease = null;
        pendingShot = null;
        shotInvalidTicks = 0;
        enter(Phase.WAIT_SHOT);
        DebugLog.info("[InstaCart] waiting for a valid shot at existing cart id={} reason={}",
                existingCartId, cause);
    }

    /** No bow is held while a blocked/unsafe shot is pending. A new opportunity
     * is checked on later ticks using the actual confirmed cart hitbox. */
    private void waitForShot(LocalPlayer player, LivingEntity target) {
        MinecartTNT cart = findCart();
        if (cart == null) { abort("Prepared TNT cart disappeared"); return; }
        if (player.isUsingItem()) return;
        // The old 4-tick polling missed short shot windows while the player
        // strafed around the cart. Evaluate every simulation tick; only one
        // nearby cart and a bounded series of charges are considered.
        Shot opportunity = InstaCartPlanner.findSafePreparedShot(
                mc.level, player, target, cart, settings(),
                pendingShot == null ? null : pendingShot.rotation());
        if (opportunity == null) {
            // Waiting for a line of fire must not invalidate an already
            // deployed TNT minecart. It can become hittable as the player
            // strafes, the target moves, or the obstruction clears.
            traceBlocker("wait-shot", "Prepared TNT cart " + cart.getId()
                    + ": no valid, damage-safe trajectory; waiting on same cart");
            return;
        }
        pendingShot = opportunity;
        enter(Phase.WAIT_BOW);
        DebugLog.info("[InstaCart] prepared cart shot ready id={} charge={} impact={}t",
                cart.getId(), opportunity.chargeTicks(), opportunity.impactTicks());
    }

    private boolean startBow() {
        LocalPlayer player = mc.player;
        if (player == null) return false;
        SlotResult bow = InventorySwap.INSTANCE.findHotbar(this::isFlameBow);
        if (!bow.found() || player.getProjectile(bow.stack()).isEmpty()) {
            abort("Missing Flame bow or arrows"); return false;
        }
        int oldSlot = InventorySwap.INSTANCE.clientSelectedSlot();
        if (!InventorySwap.INSTANCE.selectHotbar(bow.slot())) {
            traceBlocker("bow-slot", "Flame bow slot selection blocked by inventory packet window");
            return false;
        }
        if (previousSlot < 0) previousSlot = oldSlot;
        lockedBowSlot = bow.slot();
        if (!InventorySwap.INSTANCE.useItem(InteractionHand.MAIN_HAND).consumesAction()
                || !player.isUsingItem() || !player.getUseItem().is(Items.BOW)) {
            traceBlocker("bow-start", "Bow use was rejected or not started locally");
            if (previousSlot >= 0) InventorySwap.INSTANCE.restoreHotbar(previousSlot);
            previousSlot = -1;
            lockedBowSlot = -1;
            return false;
        }
        enter(Phase.DRAWING);
        return true;
    }

    private void drawAndShoot(LocalPlayer player, LivingEntity target) {
        if (phaseTicks > (existingCartId >= 0 ? 64 : DRAW_TIMEOUT_TICKS)) {
            // A timeout is only a draw reset, never a failed cart/site.
            if (existingCartId >= 0) parkBowUntilShot("Draw reset; retaining existing cart");
            else prepareBeforeShot("Draw window expired before placement");
            return;
        }
        if (!player.isUsingItem() || !player.getUseItem().is(Items.BOW)
                || InventorySwap.INSTANCE.clientSelectedSlot() != lockedBowSlot) {
            abort("Bow use interrupted (slot/key changed)");
            return;
        }
        int charge = player.getTicksUsingItem();
        int required = pendingShot == null ? bowChargeTicks.get() : pendingShot.chargeTicks();
        if (site == null) { abort("No selected site"); return; }
        // Rotation is requested before the release packet. In the observed
        // Minecraft tick order getTicksUsingItem() advances between PRE and
        // EventPostSync. Prepare for the actual charge at release, not today's
        // charge used while finding a direction.
        int releaseCharge = InstaCartCycleRules.expectedReleaseCharge(charge);
        if (releaseCharge < required) {
            Shot early = pendingShot;
            if (early != null) aim(early.rotation(), () -> { });
            shotWaitReason = "Charging and pre-aiming";
            DebugLog.infoOnChange("instacart.draw", "charging:" + targetEntityId + ":" + required,
                    "[InstaCart] drawing bow target={} requiredCharge={} currentCharge={} preAimValid={}",
                    targetEntityId, required, charge, early != null);
            return;
        }
        if (existingCartId < 0 && !InstaCartPlanner.stillPlaceable(mc.level, player, site, range.get())) {
            // Do not fire toward an unreachable support: the rail must be placed
            // before the arrow, or the site must be abandoned as truly invalid.
            prepareBeforeShot("Rail support temporarily out of reach"); return;
        }
        MinecartTNT liveCart = existingCartId >= 0 ? findCart() : null;
        if (existingCartId >= 0 && liveCart == null) {
            abort("Existing cart disappeared"); return;
        }
        int minFlight = currentMinimumFlight();
        Shot predicted = InstaCartPlanner.predict(mc.level, player,
                liveCart == null ? InstaCartPlanner.cartBox(mc.level, site.rail()) : liveCart.getBoundingBox(),
                releaseCharge, liveCart, minFlight, pendingShot == null ? null : pendingShot.rotation());
        if (predicted == null) {
            shotWaitReason = "No viable trajectory for current charge / placement window";
            traceBlocker("shot-trajectory", shotWaitReason + " currentCharge=" + charge
                    + " releaseCharge=" + releaseCharge + " minimumFlight=" + minFlight);
            shotInvalidTicks++;
            if (shotInvalidTicks >= 12 && releaseCharge >= 20) {
                if (existingCartId >= 0) parkBowUntilShot("No trajectory even at full configured charge");
                else prepareBeforeShot("Arrow timing became unreliable");
            }
            return;
        }
        shotInvalidTicks = 0;
        shotWaitReason = "Waiting for sent aim rotation";
        aim(predicted.rotation(), () -> {
            if (phase != Phase.DRAWING || mc.player == null || !mc.player.isUsingItem()) return;
            // Revalidate when the rotation callback actually fires; aim requests can be delayed.
            if (resolveTarget() == null) { abort("Target lost before release"); return; }
            Shot latest = validateCurrentShot(predicted.rotation(),
                    InstaCartCycleRules.expectedReleaseCharge(mc.player.getTicksUsingItem()));
            if (latest == null) {
                traceBlocker("rotation-callback", "Planned angle no longer intersects cart at current charge");
                return;
            }
            pendingShot = latest;
            MinecartTNT currentCart = existingCartId >= 0 ? findCart() : null;
            Vec3 explosion = currentCart != null ? currentCart.position().add(0.0, 0.35, 0.0)
                    : site.explosionPoint();
            if (!InstaCartPlanner.isDamageAcceptable(mc.player, target,
                    explosion, latest, settings())) {
                shotWaitReason = "Waiting for safe explosion damage";
                traceBlocker("rotation-callback", shotWaitReason);
                return;
            }
            // RotationManager calls this before the movement packet is sent. Shooting
            // here would use the previous server yaw/pitch. PostSync performs the release.
            queuedRelease = latest;
            DebugLog.infoOnChange("instacart.release-queued", targetEntityId + ":" + site.rail()
                            + ":" + latest.chargeTicks(),
                    "[InstaCart] release queued target={} charge={} flight={} rail={}",
                    targetEntityId, latest.chargeTicks(), latest.impactTicks(), site.rail());
        });
    }

    private int currentMinimumFlight() {
        return existingCartId >= 0 ? 1
                : InstaCartCycleRules.minimumFastFlightTicks(site != null && site.existingRail(),
                        fastExtraTicks.get());
    }

    private Shot validateCurrentShot(Rotation sentRotation, int chargeTicks) {
        if (mc.player == null || mc.level == null || site == null || sentRotation == null) return null;
        MinecartTNT cart = existingCartId >= 0 ? findCart() : null;
        if (existingCartId >= 0 && cart == null) return null;
        return InstaCartPlanner.validateShot(mc.level, mc.player,
                cart != null ? cart.getBoundingBox() : InstaCartPlanner.cartBox(mc.level, site.rail()),
                chargeTicks, cart, currentMinimumFlight(), sentRotation);
    }

    private void placeRail() {
        if (site == null) { abort(); return; }
        if (mc.level.getBlockState(site.rail()).is(BlockTags.RAILS)) {
            boolean preparing = phase == Phase.PRE_RAIL;
            enter(preparing ? Phase.PRE_CART : Phase.POST_CART);
            // Already confirmed support: prepare the cart's rotation now.
            placeCart();
            return;
        }
        if (phaseTicks > PLACEMENT_TIMEOUT_TICKS) {
            abort("Rail did not appear / became unreachable"); return;
        }
        // A temporary failed raycast or out-of-reach position is not proof of
        // a permanently invalid plan after the arrow has already been shot.
        if (!InstaCartPlanner.stillPlaceable(mc.level, mc.player, site, range.get())) {
            traceBlocker("rail-window", "Rail interaction not currently reachable at " + site.rail());
            return;
        }
        if (!InstaCartCycleRules.canRetryPlacement(phaseTicks, nextPlacementAttemptTick,
                placementAttempts, 5)) return;
        BlockHitResult hit = InstaCartPlanner.interaction(mc.level, mc.player, site.rail(), false, range.get());
        if (hit == null) {
            traceBlocker("rail-hit", "Rail support temporarily not hittable: " + site.rail());
            return;
        }
        aimAt(hit.getLocation(), () -> queuedPlacement = PlacementAction.RAIL);
    }

    private void placeCart() {
        if (site == null) { abort(); return; }
        if (!mc.level.getBlockState(site.rail()).is(BlockTags.RAILS)) {
            if (phaseTicks > PLACEMENT_TIMEOUT_TICKS) abort("Rail lost before cart placement");
            return;
        }
        MinecartTNT cart = findCart();
        if (cart != null) {
            DebugLog.info("[InstaCart] cart confirmed id={} at {}",
                    cart.getId(), site.rail());
            cartSeen = true;
            observedCartId = cart.getId();
            if (phase == Phase.PRE_CART) {
                existingCartId = cart.getId();
                pendingShot = null;
                enter(Phase.WAIT_SHOT);
                DebugLog.info("[InstaCart] prepared TNT minecart id={}; waiting for a verified shot", existingCartId);
            } else enter(Phase.WAIT_IMPACT);
            return;
        }
        if (phaseTicks > PLACEMENT_TIMEOUT_TICKS) {
            abort("Cart not confirmed after " + placementAttempts + " attempts"); return;
        }
        if (!InstaCartCycleRules.canRetryPlacement(phaseTicks, nextPlacementAttemptTick,
                placementAttempts, 4)) return;
        BlockHitResult hit = InstaCartPlanner.interaction(mc.level, mc.player, site.rail(), true, range.get());
        if (hit == null) {
            traceBlocker("cart-hit", "Rail temporarily not hittable for minecart: " + site.rail());
            return;
        }
        aimAt(hit.getLocation(), () -> queuedPlacement = PlacementAction.CART);
    }

    private MinecartTNT findCart() {
        if (mc.level == null) return null;
        int trackedId = existingCartId >= 0 ? existingCartId : observedCartId;
        if (trackedId >= 0) {
            return mc.level.getEntity(trackedId) instanceof MinecartTNT cart && cart.isAlive() ? cart : null;
        }
        if (site == null) return null;
        List<MinecartTNT> carts = mc.level.getEntitiesOfClass(MinecartTNT.class,
                new AABB(site.rail()).inflate(0.25, 0.7, 0.25));
        MinecartTNT closest = null;
        double distance = Double.POSITIVE_INFINITY;
        for (MinecartTNT cart : carts) {
            if (!cart.isAlive()) continue;
            // An adjacent cart is not evidence that OUR interaction succeeded.
            if (Math.abs(cart.getX() - (site.rail().getX() + 0.5)) > 0.48
                    || Math.abs(cart.getZ() - (site.rail().getZ() + 0.5)) > 0.48) continue;
            double current = cart.position().distanceToSqr(site.explosionPoint());
            if (current < distance) { closest = cart; distance = current; }
        }
        return closest;
    }

    private void aimAt(Vec3 point, Runnable action) {
        aim(Rotation.lookingAt(point, mc.player.getEyePosition()).normalize(), action);
    }

    private void aim(Rotation rotation, Runnable action) {
        RotationTarget target = new RotationTarget(rotation, mc.player, List.of(),
                2, 4.0f, // survives update()'s request tick until EventPostSync ownership check
                false, movementCorrection.get(), new RestrictedSingleUseAction(action));
        RotationManager.INSTANCE.setRotationTarget(target, ROTATION_PRIORITY, this);
    }

    private boolean useOnBlock(Predicate<ItemStack> item, BlockHitResult hit) {
        boolean[] used = {false};
        InventorySwapRequest request = InventorySwapRequest.builder(item, () -> {
                    if (mc.player == null || mc.gameMode == null) return;
                    if (mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit).consumesAction()) {
                        used[0] = true;
                        mc.player.swing(InteractionHand.MAIN_HAND,
                                mc.player.getMainHandItem().getInteractAnimation(), false);
                    }
                })
                .scope(InventorySearchScope.HOTBAR)
                .visibility(InventorySwapVisibility.SILENT)
                .restore(true)
                .actionKind(InventoryActionKind.BLOCK_INTERACT)
                .build();
        return InventorySwap.INSTANCE.execute(request) && used[0];
    }

    private LivingEntity resolveTarget() {
        return mc.level != null && mc.level.getEntity(targetEntityId) instanceof LivingEntity living
                && living.isAlive() && TargetingUtil.isValidCombatTarget(living) ? living : null;
    }

    private Ready checkItems(Site candidate, int selectedExistingCartId) {
        SlotResult bow = InventorySwap.INSTANCE.findHotbar(this::isFlameBow);
        if (!bow.found() || mc.player == null || mc.player.getProjectile(bow.stack()).isEmpty()) return Ready.NO_BOW;
        if (candidate != null && selectedExistingCartId < 0 && !candidate.existingRail()
                && !InventorySwap.INSTANCE.findHotbar(InstaCart::isRail).found()) return Ready.NO_RAIL;
        if (selectedExistingCartId < 0 && !InventorySwap.INSTANCE.findHotbar(stack -> stack.is(Items.TNT_MINECART)).found()) return Ready.NO_CART;
        return Ready.READY;
    }

    private void warnMissing(Ready reason) {
        String key = switch (reason) {
            case NO_BOW -> "notification.instacart.no_flame_bow";
            case NO_RAIL -> "notification.instacart.no_rail";
            case NO_CART -> "notification.instacart.no_cart";
            case READY -> null;
        };
        if (key != null) Notifier.warning(I18n.get(key));
    }

    private boolean isFlameBow(ItemStack stack) {
        return stack.is(Items.BOW) && EnchantUtil.level(mc.level, Enchantments.FLAME, stack) > 0;
    }

    private static boolean isRail(ItemStack stack) { return stack.is(ItemTags.RAILS); }

    private void enter(Phase next) {
        if (phase != next) DebugLog.info(
                "[InstaCart] {} -> {} target={} rail={} fired={} cartId={}",
                phase, next, targetEntityId, site == null ? "-" : site.rail(), fired, existingCartId);
        phase = next;
        phaseTicks = 0;
        queuedPlacement = null;
        placementAttempts = 0;
        nextPlacementAttemptTick = 0;
    }

    private void finish() {
        cooldown = 16;
        reset();
    }

    private void abort() {
        String reason = switch (phase) {
            case PRE_RAIL, POST_RAIL -> "Rail interaction failed or timed out";
            case PRE_CART, POST_CART -> "Cart interaction failed or timed out";
            case WAIT_SITE -> "Rail support remained unreachable";
            case WAIT_SHOT -> "Prepared minecart no longer viable";
            case WAIT_BOW -> "Bow slot unavailable";
            case DRAWING -> "Flame bow / arrow trajectory invalid";
            case WAIT_IMPACT -> "Impact not confirmed";
            default -> "Execution aborted";
        };
        abort(reason);
    }

    private void abort(String reason) {
        lastAbortReason = reason;
        // Never blacklist a valid site because of a bow-slot conflict, changed
        // target, or an interrupted draw. Only actual placement failures do that.
        if (site != null && existingCartId < 0 && (phase == Phase.POST_RAIL
                || phase == Phase.POST_CART || phase == Phase.PRE_RAIL || phase == Phase.PRE_CART)
                && placementAttempts > 0)
            rejectSite(targetEntityId, site.rail());
        DebugLog.info(
                "[InstaCart] abort phase={} target={} rail={} fired={} attempts={} reason={}",
                phase, targetEntityId, site == null ? "-" : site.rail(), fired, placementAttempts, reason);
        // No long idle after a failed interaction: the next opportunity may
        // be a different reachable rail or a cart already present in the world.
        cooldown = Math.max(cooldown, fired ? 2 : 1);
        reset();
        invalidateSiteCache();
    }

    private void reset() {
        TargetManager.clear(this);
        if (phase == Phase.DRAWING && mc.player != null && mc.player.isUsingItem()) {
            mc.player.stopUsingItem();
        }
        if (previousSlot >= 0) InventorySwap.INSTANCE.restoreHotbar(previousSlot);
        previousSlot = -1;
        targetEntityId = -1;
        site = null;
        pendingShot = null;
        queuedRelease = null;
        lockedBowSlot = -1;
        cartSeen = false;
        observedCartId = -1;
        shotAttempts = 0;
        shotInvalidTicks = 0;
        closestArrowDistanceSq = Double.POSITIVE_INFINITY;
        arrowObserved = false;
        shotWaitReason = "";
        existingCartId = -1;
        fired = false;
        flightTicks = 0;
        enter(Phase.IDLE);
        lastTargetRecheckTick = -1;
        RotationManager.INSTANCE.release(this);
    }

    private void traceBlocker(String key, String message) {
        DebugLog.infoOnChange("instacart." + key, message,
                "[InstaCart] {}", message);
    }

    private void invalidateSiteCache() {
        cachedSelection = null;
        siteRecheck = 0;
    }

    @TargetHudStatus
    public String getTargetHudStatus(LivingEntity hudTarget) {
        if (!isEnabled() || hudTarget == null) return null;
        LivingEntity active = phase == Phase.IDLE
                ? (cachedSelection == null ? null : cachedSelection.target()) : resolveTarget();
        if (active != hudTarget) return null;
        if (phase != Phase.IDLE) {
            return switch (phase) {
                case WAIT_SITE -> "Waiting for reachable rail support";
                case PRE_RAIL -> "Preparing rail before shot";
                case PRE_CART -> "Preparing TNT cart before shot";
                case WAIT_SHOT -> "Waiting for a clear shot at TNT cart";
                case POST_RAIL -> "Placing rail";
                case POST_CART -> "Placing TNT cart";
                case WAIT_BOW -> "Waiting for bow slot";
                case DRAWING -> "Drawing Flame bow " + (mc.player == null ? 0 : mc.player.getTicksUsingItem())
                        + "/" + (pendingShot != null ? pendingShot.chargeTicks() : bowChargeTicks.get()) + "t"
                        + (shotWaitReason.isEmpty() ? "" : " · " + shotWaitReason);
                case WAIT_IMPACT -> "Waiting for arrow";
                default -> "Planning";
            };
        }
        if (cooldown > 0) return lastAbortReason.isEmpty()
                ? "Cooldown " + cooldown + "t"
                : lastAbortReason + " (" + cooldown + "t)";
        return planningStatus;
    }
}

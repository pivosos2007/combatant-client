/*
 * This file is part of the Combatant Client distribution.
 * Combatant modifications copyright (c) 2026 pivosos2007.
 *
 * Portions of this file are based on LiquidBounce
 * (https://github.com/CCBlueX/LiquidBounce).
 * Copyright (c) 2015-2026 CCBlueX.
 *
 * LiquidBounce portions are licensed under GPLv3-or-later.
 * Combatant modifications are licensed under GPLv3.
 * See THIRD_PARTY_NOTICES.md for details.
 */

package combatant.client.features.module.modules.combat;

/*
 * Inspired by LiquidBounce (https://github.com/CCBlueX/LiquidBounce).
 */

import combatant.client.config.values.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import combatant.client.config.common.CommonSettingSchemas;
import combatant.client.config.common.impl.TargetFilters;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.EventSync;
import combatant.client.events.impl.PacketEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.Modules;
import combatant.client.features.relations.CategoryRules;
import combatant.client.features.relations.CategoryType;
import combatant.client.features.relations.EntityFilters;
import combatant.client.util.click.AttackPressing;
import combatant.client.util.click.ClickHumanizer;
import combatant.client.util.click.ClickScheduler;
import combatant.client.util.combat.CombatStrikeController;
import combatant.client.util.item.FoodUtil;
import combatant.client.util.player.ResetAttackCooldown;
import combatant.client.util.combat.protocol.CombatProtocolHeuristics;
import combatant.client.util.combat.protocol.ProtocolUtil;

import java.util.LinkedHashMap;


//todo Description
@ModuleInfo(
        id = "triggerbot",
        displayName = "TriggerBot",
        category = ModuleCategory.COMBAT
)
public class AutoAttack extends Module {

    private static final int MAX_CPS = 100;
    private static final String TOGGLE_HUMANIZE_MISS = "miss";
    private static final String TOGGLE_HUMANIZE_SWING = "miss_swing";
    private static final String TOGGLE_HUMANIZE_PAUSE = "pause";
    private final Minecraft mc = Minecraft.getInstance();
    private final ModeValue mode =
            mode(
                    "autoattack_mode",
                    "mode",
                    "hold",
                    "hold",
                    "auto"
            );
    private final EnumValue<ObjectiveType> objective =
            enumCommon(
                    "autoattack_objective",
                    "objective",
                    CommonSettingSchemas.COMBAT_OBJECTIVE,
                    ObjectiveType.ANY,
                    ObjectiveType.values()
            );
    private final NumberValue<Integer> cpsMin =
            visibleWhen(numCommon(
                    "autoattack_cps_min",
                    "cps_min",
                    CommonSettingSchemas.COMBAT_CPS_MIN,
                    8,
                    1,
                    MAX_CPS
            ), () -> objective.get() != ObjectiveType.BLOCK && !isAutoMode());
    private final NumberValue<Integer> cpsMax =
            visibleWhen(numCommon(
                    "autoattack_cps_max",
                    "cps_max",
                    CommonSettingSchemas.COMBAT_CPS_MAX,
                    12,
                    1,
                    MAX_CPS
            ), () -> objective.get() != ObjectiveType.BLOCK && !isAutoMode());
    private final ClickScheduler clicker = new ClickScheduler(cpsMin.get(), cpsMax.get());
    private final BooleanValue delayOnBroken =
            visibleWhen(bool(
                    "autoattack_delay_on_broken",
                    "delay_on_broken",
                    true
            ), () -> objective.get() != ObjectiveType.BLOCK);
    private final EnumValue<WeaponType> weapon =
            visibleWhen(enumCommon(
                    "autoattack_weapon",
                    "weapon",
                    CommonSettingSchemas.COMBAT_WEAPON,
                    WeaponType.ANY,
                    WeaponType.values()
            ), () -> objective.get() != ObjectiveType.BLOCK);
    private final EnumValue<UseMode> onItemUse =
            visibleWhen(enumCommon(
                    "autoattack_on_item_use",
                    "on_item_use",
                    CommonSettingSchemas.COMBAT_ON_ITEM_USE,
                    UseMode.WAIT,
                    UseMode.values()
            ), () -> objective.get() != ObjectiveType.BLOCK);
    private final NumberValue<Integer> delayPostStopUse =
            visibleWhen(num(
                    "autoattack_delay_post_stop_use",
                    "delay_post_stop_use",
                    0,
                    0,
                    20
            ), () -> objective.get() != ObjectiveType.BLOCK && onItemUse.get() != UseMode.IGNORE);
    private final BooleanMapValue targetToggles =
            visibleWhen(common(
                    group(
                            "autoattack_toggles",
                            "targets",
                            targetFilterDefaults()
                    ),
                    CommonSettingSchemas.TARGET_FILTERS.commonI18nKey()
            ), () -> objective.get() == ObjectiveType.ENEMY);
    private final BooleanMapValue humanizeToggles =
            visibleWhen(group(
                    "autoattack_humanize_toggles",
                    "humanize_toggles",
                    humanizeDefaults()
            ), () -> objective.get() != ObjectiveType.BLOCK && !isAutoMode());
    private final NumberValue<Integer> humanizeMissRate =
            visibleWhen(num(
                    "autoattack_humanize_miss_rate",
                    "humanize_miss_rate",
                    8,
                    1,
                    100
            ), () -> objective.get() != ObjectiveType.BLOCK
                    && !isAutoMode()
                    && humanizeToggles.get(TOGGLE_HUMANIZE_MISS));
    private final NumberValue<Integer> humanizeSwingRate =
            visibleWhen(num(
                    "autoattack_humanize_swing_rate",
                    "humanize_swing_rate",
                    5,
                    1,
                    100
            ), () -> objective.get() != ObjectiveType.BLOCK
                    && !isAutoMode()
                    && humanizeToggles.get(TOGGLE_HUMANIZE_SWING));
    private final NumberValue<Integer> humanizePauseRate =
            visibleWhen(num(
                    "autoattack_humanize_pause_rate",
                    "humanize_pause_rate",
                    5,
                    1,
                    100
            ), () -> objective.get() != ObjectiveType.BLOCK
                    && !isAutoMode()
                    && humanizeToggles.get(TOGGLE_HUMANIZE_PAUSE));
    private final NumberValue<Integer> humanizePauseMin =
            visibleWhen(num(
                    "autoattack_humanize_pause_min",
                    "humanize_pause_min",
                    1,
                    1,
                    10
            ), () -> objective.get() != ObjectiveType.BLOCK
                    && !isAutoMode()
                    && humanizeToggles.get(TOGGLE_HUMANIZE_PAUSE));
    private final NumberValue<Integer> humanizePauseMax =
            visibleWhen(num(
                    "autoattack_humanize_pause_max",
                    "humanize_pause_max",
                    2,
                    1,
                    20
            ), () -> objective.get() != ObjectiveType.BLOCK
                    && !isAutoMode()
                    && humanizeToggles.get(TOGGLE_HUMANIZE_PAUSE));
    private final ClickHumanizer humanizer = new ClickHumanizer(
            humanizeToggles,
            humanizeMissRate,
            humanizeSwingRate,
            humanizePauseRate,
            humanizePauseMin,
            humanizePauseMax,
            TOGGLE_HUMANIZE_MISS,
            TOGGLE_HUMANIZE_SWING,
            TOGGLE_HUMANIZE_PAUSE
    );
    private final EnumValue<ProtocolMode> protocolMode =
            visibleWhen(enumCommon(
                    "autoattack_protocol_mode",
                    "protocol_mode",
                    CommonSettingSchemas.COMBAT_PROTOCOL,
                    ProtocolMode.AUTO,
                    ProtocolMode.values()
            ), () -> objective.get() != ObjectiveType.BLOCK);
    private final BooleanMapValue protocolHeuristicSources =
            visibleWhen(protocolHeuristics(
                    "autoattack_protocol_heuristics",
                    "protocol_heuristics",
                    CommonSettingSchemas.COMBAT_PROTOCOL_HEURISTICS
            ), () -> objective.get() != ObjectiveType.BLOCK && protocolMode.get() == ProtocolMode.AUTO);
    private long lastFinishBreak = 0L;
    private int postUseDelayTicks = 0;
    private boolean wasUsingItem = false;
    private int lastSyncHandledAge = -1;

    private static LinkedHashMap<String, Boolean> targetFilterDefaults() {
        LinkedHashMap<String, Boolean> defaults = new LinkedHashMap<>();
        defaults.put(TargetFilters.PLAYERS_ONLY, false);
        defaults.put(TargetFilters.IGNORE_FRIENDS, true);
        defaults.put(TargetFilters.IGNORE_STAFF, true);
        defaults.put(TargetFilters.IGNORE_ENEMIES, false);
        defaults.put(TargetFilters.IGNORE_ENTITIES, true);
        defaults.put(TargetFilters.VISIBLE_ONLY, false);
        return defaults;
    }

    private static LinkedHashMap<String, Boolean> humanizeDefaults() {
        LinkedHashMap<String, Boolean> defaults = new LinkedHashMap<>();
        defaults.put(TOGGLE_HUMANIZE_MISS, false);
        defaults.put(TOGGLE_HUMANIZE_SWING, false);
        defaults.put(TOGGLE_HUMANIZE_PAUSE, false);
        return defaults;
    }

    @Override
    public void onEnable() {
        clicker.reset();
        lastFinishBreak = 0L;
        postUseDelayTicks = 0;
        wasUsingItem = false;
        lastSyncHandledAge = -1;
        humanizer.reset();
    }

    @Override
    public void onDisable() {
        clicker.reset();
        postUseDelayTicks = 0;
        wasUsingItem = false;
        lastSyncHandledAge = -1;
        humanizer.reset();
    }

    @Override
    public void onTick() {
    }

    @EventHandler(priority = -1000)
    private void onSync(EventSync event) {
        if (!isEnabled()) return;
        if (mc.player == null || mc.options == null) return;
        if (mc.player.tickCount == lastSyncHandledAge) return;
        lastSyncHandledAge = mc.player.tickCount;

        clicker.setCps(cpsMin.get(), cpsMax.get());
        clicker.tick();

        boolean attackInput = mc.options.keyAttack.isDown();
        HitResult hit = resolveHitResult(mc.player);
        if (objective.get() == ObjectiveType.BLOCK) {
            // Let vanilla handle block breaking; no CPS/humanize in this mode.
            wasUsingItem = mc.player.isUsingItem();
            return;
        }
        boolean autoMode = isAutoMode();

        if (!attackInput && !autoMode) {
            wasUsingItem = mc.player.isUsingItem();
            return;
        }

        // In auto mode, only process entity clicks (avoid block/air spam).
        if (autoMode && !(hit instanceof EntityHitResult)) {
            wasUsingItem = mc.player.isUsingItem();
            return;
        }

        if (!isWeaponSelected(mc.player)) return;

        if (mc.gameMode != null && mc.gameMode.isDestroying()) {
            return;
        }

        if (delayOnBroken.get() && System.currentTimeMillis() - lastFinishBreak < 300L) {
            return;
        }

        boolean usingItem = mc.player.isUsingItem();
        boolean usingShield = usingItem
                && (mc.player.getUseItem().is(Items.SHIELD)
                || mc.player.getOffhandItem().is(Items.SHIELD));
        boolean usingFood = usingItem && FoodUtil.isFood(mc.player.getUseItem());

        RubberHand rh = Modules.get(RubberHand.class);
        boolean rhEnabled = rh != null && rh.isEnabled();
        boolean bypassItemUseLogic = false;

        if (usingItem && rhEnabled) {
            if (usingShield) {
                if (!rh.canAttackWhileShield()) {
                    wasUsingItem = true;
                    return;
                }
                bypassItemUseLogic = true;
            } else if (usingFood) {
                if (!rh.canAttackWhileEating()) {
                    wasUsingItem = true;
                    return;
                }
                bypassItemUseLogic = true;
            }
        }

        if (!usingItem && wasUsingItem && onItemUse.get() != UseMode.IGNORE) {
            int delay = delayPostStopUse.get();
            if (delay > 0) {
                postUseDelayTicks = delay;
            }
        }

        if (postUseDelayTicks > 0) {
            postUseDelayTicks--;
            wasUsingItem = usingItem;
            return;
        }

        if (usingItem && !bypassItemUseLogic) {
            switch (onItemUse.get()) {
                case WAIT -> {
                    wasUsingItem = true;
                    return;
                }
                case STOP -> {
                    mc.player.releaseUsingItem();
                    postUseDelayTicks = delayPostStopUse.get();
                    wasUsingItem = false;
                    return;
                }
                case IGNORE -> {
                    // continue
                }
            }
        }

        wasUsingItem = usingItem;

        if (!isOnObjective(hit)) return;

        int clickCount = 1;
        if (!autoMode) {
            clickCount = resolveClickCountForCurrentProtocol();
            if (clickCount <= 0) return;
        }

        if (!autoMode) {
            ClickHumanizer.Decision decision = humanizer.decide();
            switch (decision) {
                case PAUSE -> {
                    return;
                }
                case MISS_SWING -> {
                    mc.player.swing(InteractionHand.MAIN_HAND);
                    return;
                }
                case MISS -> {
                    return;
                }
                case CLICK -> {
                    // proceed
                }
            }
        }

        if (hit instanceof EntityHitResult entityHit) {
            if (!(entityHit.getEntity() instanceof LivingEntity living) || !canAttackNow(mc.player, living)) {
                return;
            }
        }
        performClick(mc.player, hit, clickCount);
    }

    @EventHandler
    public void onPacketSend(PacketEvent.Send event) {
        if (!isEnabled()) return;
        if (!delayOnBroken.get()) return;
        if (event.getPacket() instanceof ServerboundPlayerActionPacket pkt
                && pkt.getAction() == ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK) {
            lastFinishBreak = System.currentTimeMillis();
        }
    }

    private HitResult resolveHitResult(LocalPlayer player) {
        HitResult hit = mc.hitResult;
        Reach reach = Modules.get(Reach.class);
        if ((hit == null || hit.getType() != HitResult.Type.ENTITY)
                && reach != null && reach.isEnabled()) {
            EntityHitResult reachHit = reach.raycastEntities(player);
            if (reachHit != null) {
                hit = reachHit;
            }
        }
        return hit;
    }

    private boolean isOnObjective(HitResult hit) {
        return switch (objective.get()) {
            case ENEMY -> hit instanceof EntityHitResult eHit && isValidTarget(eHit.getEntity());
            case ENTITY -> hit instanceof EntityHitResult;
            case BLOCK -> hit instanceof BlockHitResult;
            case ANY -> true;
        };
    }

    private boolean isValidTarget(Entity entity) {
        if (!(entity instanceof LivingEntity living) || !living.isAlive()) return false;
        if (mc.player == null) return false;

        if (targetToggles.get(TargetFilters.PLAYERS_ONLY) && !(living instanceof Player)) {
            return false;
        }

        if (targetToggles.get(TargetFilters.VISIBLE_ONLY) && !mc.player.hasLineOfSight(living)) {
            return false;
        }

        if (targetToggles.get(TargetFilters.IGNORE_ENTITIES)) {
            var id = BuiltInRegistries.ENTITY_TYPE.getKey(living.getType());
            if (id != null && EntityFilters.get().isIgnoredEntity(id.toString())) {
                return false;
            }
        }

        if (living instanceof Player p) {
            CategoryType type = CategoryRules.determine(p.getGameProfile().name());
            if (type == CategoryType.BEDWARS_SELF) return false;
            if (targetToggles.get(TargetFilters.IGNORE_FRIENDS) && type == CategoryType.FRIEND) return false;
            if (targetToggles.get(TargetFilters.IGNORE_STAFF) && type == CategoryType.STAFF) return false;
            return !targetToggles.get(TargetFilters.IGNORE_ENEMIES)
                    || (type != CategoryType.ENEMY && type != CategoryType.BEDWARS_ENEMY);
        }

        return true;
    }

    private boolean isWeaponSelected(LocalPlayer player) {
        ItemStack stack = player.getMainHandItem();
        return switch (weapon.get()) {
            case SWORD -> stack.is(ItemTags.SWORDS);
            case AXE -> stack.is(ItemTags.AXES);
            case BOTH -> stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES);
            case ANY -> true;
        };
    }

    private void performClick(LocalPlayer player, HitResult hit, int clickCount) {
        boolean legacyProtocol = isLegacyAttackProtocol();
        if (hit instanceof EntityHitResult eHit) {
            Entity target = eHit.getEntity();
            if (objective.get() == ObjectiveType.ENEMY && !isValidTarget(target)) {
                return;
            }

            Reach reach = Modules.get(Reach.class);
            if (reach != null && reach.isEnabled()) {
                mc.hitResult = eHit;
            }

            if (target instanceof LivingEntity living) {
                CombatStrikeController.INSTANCE.tryAttack(
                        mc,
                        living,
                        legacyProtocol,
                        clickCount,
                        CombatStrikeController.SprintResetMode.LEGIT,
                        false,
                        null
                );
            }
            return;
        }

        if (objective.get() == ObjectiveType.BLOCK) {
            for (int i = 0; i < clickCount; i++) {
                player.swing(InteractionHand.MAIN_HAND);
                if (legacyProtocol) {
                    ResetAttackCooldown.resetAttackCooldown(player);
                }
            }
        }
    }

    public boolean isAutoMode() {
        return "auto".equalsIgnoreCase(mode.get());
    }

    public boolean shouldBlockBreaking() {
        return objective.get() != ObjectiveType.BLOCK;
    }

    private int resolveClickCountForCurrentProtocol() {
        if (!isLegacyAttackProtocol()) {
            return 1;
        }

        int clickCount = clicker.getClicksAt(0);
        if (clickCount <= 0) {
            return 0;
        }

        return clickCount;
    }

    private boolean canAttackNow(LocalPlayer player, LivingEntity target) {
        if (player == null) return false;
        return AttackPressing.INSTANCE.isCooldownComplete(player, target, isLegacyAttackProtocol(), 0);
    }

    private boolean isLegacyAttackProtocol() {
        return switch (protocolMode.get()) {
            case AUTO -> ProtocolUtil.isLegacyAttackProtocol(protocolHeuristicSources.getAll());
            case FORCE_1_8 -> true;
            case FORCE_1_9 -> false;
        };
    }

    public boolean usesLegacyProtocol() {
        return isLegacyAttackProtocol();
    }

    public enum ObjectiveType {
        ENEMY,
        ENTITY,
        BLOCK,
        ANY
    }

    public enum WeaponType {
        SWORD,
        AXE,
        BOTH,
        ANY
    }

    public enum UseMode {
        WAIT,
        STOP,
        IGNORE
    }

    @Getter
    @RequiredArgsConstructor
    public enum ProtocolMode implements EnumValue.IdProvider {
        AUTO("auto"),
        FORCE_1_8("1.8"),
        FORCE_1_9("1.9");

        private final String id;
    }
}

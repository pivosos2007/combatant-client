/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat.anchormacro;

import combatant.client.automation.Action;
import combatant.client.automation.ActionResult;
import combatant.client.automation.Actions;
import combatant.client.automation.FailureReason;
import combatant.client.automation.actions.InteractBlockAction;
import combatant.client.automation.actions.PlaceBlockAction;
import combatant.client.automation.placement.PlacementConstraints;
import combatant.client.automation.placement.PlacementPlanner;
import combatant.client.automation.placement.PlacementSlot;
import combatant.client.automation.placement.PlacementTarget;
import combatant.client.features.module.modules.combat.anchormacro.AnchorMacroPlanner.AbortReason;
import combatant.client.features.module.modules.combat.anchormacro.AnchorMacroPlanner.Decision;
import combatant.client.features.module.modules.combat.anchormacro.AnchorMacroPlanner.State;
import combatant.client.features.module.modules.combat.anchormacro.AnchorMacroPlanner.Step;
import combatant.client.features.module.modules.combat.anchormacro.AnchorSlotPicker.DetonatorPreference;
import combatant.client.features.module.modules.combat.anchormacro.AnchorSlotPicker.ItemKind;
import combatant.client.features.module.modules.combat.autoanchor.AutoAnchorInteractionUtil;
import combatant.client.features.module.modules.combat.autoanchor.AutoAnchorPlanner;
import combatant.client.util.combat.ExplosionDamageRules;
import combatant.client.util.player.inventory.InventorySwap;
import combatant.client.util.world.ExplosionDamageUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Builds the steps (place, charge, detonate) of one anchor out of runtime actions. Each step is deferred: it looks at
 * the anchor and the hotbar when it starts and, through {@link AnchorMacroPlanner}, either runs, skips because the
 * anchor is already further along, or fails because the world went another way than the sequence assumed.
 */
public final class AnchorSteps {
    /** Failure details the module reports to the player (the other details are {@link AbortReason} names). */
    public static final String UNSAFE = "UNSAFE";
    public static final String DRIFT = "DRIFT";
    public static final String UNREACHABLE = "UNREACHABLE";

    /** Gate verdict meaning "nothing to do here". */
    private static final Action SKIP = Actions.waitTicks(0);

    /**
     * @param owner         module owning the tasks and the hotbar lease
     * @param targetCharges charges loaded before the anchor is detonated
     * @param maxSelfDamage detonation is refused above this predicted damage to the player
     * @param allowTotemPop a lethal explosion is accepted while a totem is held
     * @param placement     constraints for placing anchors
     * @param interact      constraints for charging and detonating
     */
    public record Config(Object owner, int targetCharges, DetonatorPreference detonator, float maxSelfDamage,
                         int selfPredictTicks, boolean allowTotemPop, PlacementConstraints placement,
                         InteractBlockAction.Settings interact) {
    }

    private final Minecraft mc = Minecraft.getInstance();
    private final Config config;

    public AnchorSteps(Config config) {
        this.config = config;
    }

    /** Places an anchor at {@code pos} unless one stands there. */
    public Action place(BlockPos pos, int delayTicks) {
        BlockPos fixed = pos.immutable();
        return Actions.defer("PlaceAnchor", () -> {
            Action early = gate(Step.PLACE, fixed);
            if (early == SKIP) return null;
            if (early != null) return early;
            LocalPlayer player = mc.player;
            PlacementSlot slot = slotOf(player, ItemKind.ANCHOR);
            if (slot == null) return fail(FailureReason.MISSING_ITEM, AbortReason.NO_ANCHOR_ITEM.name());
            PlacementTarget target = PlacementPlanner.planOne(player, fixed, slot, config.placement(), null);
            if (target == null) return fail(FailureReason.UNREACHABLE, UNREACHABLE);
            return Actions.sequence("PlaceAnchorSteps", Actions.waitTicks(delayTicks),
                    new PlaceBlockAction(config.owner(), List.of(target), config.placement(), 1));
        });
    }

    /** Loads one more charge into the anchor at {@code pos} unless it holds the target already. */
    public Action charge(BlockPos pos, int delayTicks) {
        BlockPos fixed = pos.immutable();
        return Actions.defer("ChargeAnchor", () -> {
            Action early = gate(Step.CHARGE, fixed);
            if (early == SKIP) return null;
            if (early != null) return early;
            int before = chargeAt(fixed);
            return Actions.sequence("ChargeAnchorSteps", Actions.waitTicks(delayTicks),
                    new InteractBlockAction(config.owner(), "ChargeAnchor", fixed,
                            player -> slotOf(player, ItemKind.GLOWSTONE), config.interact(),
                            () -> chargeAt(fixed) > before));
        });
    }

    /** Detonates the anchor at {@code pos}, refusing while the explosion would hurt the player too much. */
    public Action detonate(BlockPos pos, int delayTicks) {
        BlockPos fixed = pos.immutable();
        return Actions.defer("DetonateAnchor", () -> {
            Action early = gate(Step.DETONATE, fixed);
            if (early == SKIP) return null;
            if (early != null) return early;
            Action click = new InteractBlockAction(config.owner(), "DetonateAnchor", fixed, this::detonatorSlot,
                    config.interact(), () -> !isAnchor(fixed));
            return Actions.sequence("DetonateAnchorSteps", Actions.waitTicks(delayTicks),
                    Actions.guard(UNSAFE, () -> isSafeToDetonate(fixed), click));
        });
    }

    /** Whole sequence for one anchor: place, charge up to the target, detonate. */
    public Action fullSequence(BlockPos pos, int delayTicks) {
        int charges = AnchorMacroPlanner.clampTarget(config.targetCharges());
        Action[] steps = new Action[charges + 2];
        steps[0] = place(pos, 0);
        for (int i = 0; i < charges; i++) steps[i + 1] = charge(pos, delayTicks);
        steps[charges + 1] = detonate(pos, delayTicks);
        return Actions.sequence("AnchorMacro", steps);
    }

    /** null when the step has work to do, {@link #SKIP} when it is already done, else a failing action. */
    private Action gate(Step expected, BlockPos pos) {
        Decision decision = AnchorMacroPlanner.next(stateAt(pos));
        return switch (AnchorMacroPlanner.disposition(expected, decision)) {
            case RUN -> null;
            case SKIP -> SKIP;
            case DRIFT -> fail(FailureReason.PRECONDITION, DRIFT);
            case ABORT -> fail(decision.reason() == AbortReason.OFFHAND_FUEL
                    ? FailureReason.PRECONDITION : FailureReason.MISSING_ITEM, decision.reason().name());
        };
    }

    private static Action fail(FailureReason reason, String detail) {
        return Actions.step("AnchorStop", () -> ActionResult.failure(reason, detail));
    }

    /** What the anchor at {@code pos} and the player's inventory look like right now. */
    public State stateAt(BlockPos pos) {
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        boolean present = false;
        int charge = 0;
        if (level != null) {
            BlockState state = level.getBlockState(pos);
            present = state.is(Blocks.RESPAWN_ANCHOR);
            if (present) charge = state.getValue(RespawnAnchorBlock.CHARGE);
        }
        boolean offhandFuel = player != null && AutoAnchorInteractionUtil.isGlowstone(player.getOffhandItem());
        return new State(present, charge, config.targetCharges(), slotOf(player, ItemKind.ANCHOR) != null,
                slotOf(player, ItemKind.GLOWSTONE) != null, detonatorSlot(player) != null, offhandFuel);
    }

    private boolean isAnchor(BlockPos pos) {
        return mc.level != null && mc.level.getBlockState(pos).is(Blocks.RESPAWN_ANCHOR);
    }

    private int chargeAt(BlockPos pos) {
        if (mc.level == null) return 0;
        BlockState state = mc.level.getBlockState(pos);
        return state.is(Blocks.RESPAWN_ANCHOR) ? state.getValue(RespawnAnchorBlock.CHARGE) : 0;
    }

    /** Predicted damage of detonating the anchor against the configured limits. */
    public boolean isSafeToDetonate(BlockPos pos) {
        LocalPlayer player = mc.player;
        if (player == null) return false;
        float selfDamage = ExplosionDamageUtil.getExplosionDamageThroughClearedCell(
                player, Vec3.atCenterOf(pos), AutoAnchorPlanner.ANCHOR_POWER, config.selfPredictTicks(), pos);
        return AnchorSafety.detonationAllowed(selfDamage, config.maxSelfDamage(),
                ExplosionDamageRules.health(player), ExplosionDamageRules.holdsTotem(player), config.allowTotemPop());
    }

    /** Offhand first, then the hotbar with the selected slot winning. */
    private PlacementSlot slotOf(LocalPlayer player, ItemKind wanted) {
        if (player == null) return null;
        ItemStack offhand = player.getOffhandItem();
        if (classify(offhand) == wanted) return new PlacementSlot(-1, InteractionHand.OFF_HAND, offhand);
        int slot = AnchorSlotPicker.pick(hotbarKinds(player), InventorySwap.INSTANCE.effectiveSelectedSlot(), wanted);
        return slot < 0 ? null : mainHand(player, slot);
    }

    private PlacementSlot detonatorSlot(LocalPlayer player) {
        if (player == null) return null;
        int slot = AnchorSlotPicker.pickDetonator(
                hotbarKinds(player), InventorySwap.INSTANCE.effectiveSelectedSlot(), config.detonator());
        return slot < 0 ? null : mainHand(player, slot);
    }

    private static PlacementSlot mainHand(LocalPlayer player, int slot) {
        return new PlacementSlot(slot, InteractionHand.MAIN_HAND, player.getInventory().getItem(slot));
    }

    private static ItemKind[] hotbarKinds(LocalPlayer player) {
        ItemKind[] kinds = new ItemKind[9];
        for (int i = 0; i < 9; i++) kinds[i] = classify(player.getInventory().getItem(i));
        return kinds;
    }

    private static ItemKind classify(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return ItemKind.EMPTY;
        if (AutoAnchorInteractionUtil.isAnchor(stack)) return ItemKind.ANCHOR;
        if (AutoAnchorInteractionUtil.isGlowstone(stack)) return ItemKind.GLOWSTONE;
        if (stack.is(Items.TOTEM_OF_UNDYING)) return ItemKind.TOTEM;
        if (stack.is(ItemTags.SWORDS)) return ItemKind.SWORD;
        return ItemKind.OTHER;
    }

    /** Anchors carried in the hotbar and offhand. */
    public static int anchorItemCount(LocalPlayer player) {
        if (player == null) return 0;
        int count = AutoAnchorInteractionUtil.isAnchor(player.getOffhandItem()) ? player.getOffhandItem().getCount() : 0;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (AutoAnchorInteractionUtil.isAnchor(stack)) count += stack.getCount();
        }
        return count;
    }
}

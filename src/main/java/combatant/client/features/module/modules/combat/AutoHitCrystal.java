/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.module.modules.combat;

import combatant.client.config.values.BindMode;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.KeyBindValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.RotationUpdateEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.input.CombatantInput;
import combatant.client.input.InputScope;
import combatant.client.mixins.accessors.MultiPlayerGameModeAccessor;
import combatant.client.mixins.accessors.PlayerInventoryAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import combatant.client.util.combat.AttackUtil;

/**
 * Hit-crystal macro driven by the player's own aim: obsidian on the looked-at block, crystal on top of it, then the
 * crystal is detonated. Everything runs in the pre-rotation phase so it never interleaves with post-movement packets.
 */
@ModuleInfo(
        id = "autohitcrystal",
        displayName = "AutoHitCrystal",
        aliases = {"HitCrystal", "CrystalMacro", "ObiCrystal"},
        category = ModuleCategory.COMBAT,
        subcategory = ModuleSubcategory.ATTACK,
        description = "module.autohitcrystal.description")
public final class AutoHitCrystal extends Module {

    private static final int TIMEOUT_TICKS = 20;

    private final Minecraft mc = Minecraft.getInstance();

    private final KeyBindValue activationKey = bind("autoHitCrystalKey", "activation_key", "NONE", BindMode.HOLD);
    private final EnumValue<Mode> mode = enumSetting("autoHitCrystalMode", "mode", Mode.SINGLE, Mode.values());
    private final NumberValue<Integer> obsidianDelay = num("autoHitCrystalObsidianDelay", "obsidian_delay", 0, 0, 10);
    private final NumberValue<Integer> placeDelay = num("autoHitCrystalPlaceDelay", "place_delay", 0, 0, 10);
    private final NumberValue<Integer> breakDelay = num("autoHitCrystalBreakDelay", "break_delay", 0, 0, 10);
    private final BooleanValue switchBack = bool("autoHitCrystalSwitchBack", "switch_back", true);

    private State state = State.IDLE;
    private String boundCombo = "NONE";
    private boolean keyWasHeld;
    private BlockPos base;
    private int waitTicks;
    private int stateTicks;
    private int originalSlot = -1;

    @Override
    public void onEnable() {
        ensureBind();
        keyWasHeld = keyHeld();
        reset();
    }

    @Override
    public void onDisable() {
        reset();
        CombatantInput.bindings().unbind(bindingName());
        boundCombo = "NONE";
    }

    @Override
    public void onTick() {
        if (isEnabled()) ensureBind();
    }

    @EventHandler
    private void onRotationUpdate(RotationUpdateEvent event) {
        if (!isEnabled() || event.getType() != RotationUpdateEvent.Type.PRE) return;
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gameMode == null) {
            reset();
            return;
        }

        boolean held = keyHeld();
        boolean pressed = held && !keyWasHeld;
        keyWasHeld = held;

        if (state == State.IDLE) {
            boolean start = mode.get() == Mode.SPAM ? held : pressed;
            if (start && noScreen()) begin(player);
            return;
        }

        if (++stateTicks > TIMEOUT_TICKS || !noScreen()) {
            reset();
            return;
        }
        if (waitTicks > 0) {
            waitTicks--;
            return;
        }

        switch (state) {
            case OBSIDIAN -> placeObsidian(player);
            case CRYSTAL -> placeCrystal(player);
            case BREAK -> breakCrystal(player);
            default -> {
            }
        }
    }

    private boolean noScreen() {
        return combatant.client.util.screen.ClientScreen.current(mc) == null;
    }

    private void begin(LocalPlayer player) {
        base = null;
        originalSlot = ((PlayerInventoryAccessor) player.getInventory()).combatant$getSelectedSlot();
        stateTicks = 0;
        waitTicks = 0;

        if (mc.hitResult instanceof EntityHitResult entityHit
                && entityHit.getEntity() instanceof EndCrystal crystal && crystal.isAlive()) {
            base = crystal.blockPosition().below();
            state = State.BREAK;
            return;
        }
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            reset();
            return;
        }
        BlockPos looked = hit.getBlockPos();
        if (isBase(looked)) {
            base = looked;
            state = State.CRYSTAL;
            return;
        }
        state = State.OBSIDIAN;
        waitTicks = obsidianDelay.get();
    }

    private void placeObsidian(LocalPlayer player) {
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            reset();
            return;
        }
        int slot = findHotbar(player, Items.OBSIDIAN);
        if (slot < 0) {
            reset();
            return;
        }
        BlockPos target = hit.getBlockPos().relative(hit.getDirection());
        if (!mc.level.getBlockState(target).canBeReplaced() || !spaceAbove(target)) {
            reset();
            return;
        }
        select(player, slot);
        if (!interact(player, InteractionHand.MAIN_HAND, hit)) {
            reset();
            return;
        }
        base = target;
        state = State.CRYSTAL;
        waitTicks = placeDelay.get();
    }

    private void placeCrystal(LocalPlayer player) {
        if (base == null || !isBase(base)) {
            // The obsidian may still be in flight; the timeout resets the macro if it never lands.
            return;
        }
        if (!spaceAbove(base)) {
            reset();
            return;
        }
        InteractionHand hand;
        if (player.getOffhandItem().is(Items.END_CRYSTAL)) {
            hand = InteractionHand.OFF_HAND;
        } else {
            int slot = findHotbar(player, Items.END_CRYSTAL);
            if (slot < 0) {
                reset();
                return;
            }
            select(player, slot);
            hand = InteractionHand.MAIN_HAND;
        }
        BlockHitResult hit = mc.hitResult instanceof BlockHitResult looking
                && looking.getType() == HitResult.Type.BLOCK && looking.getBlockPos().equals(base)
                ? looking
                : new BlockHitResult(new Vec3(base.getX() + 0.5, base.getY() + 1.0, base.getZ() + 0.5),
                Direction.UP, base, false);
        if (!interact(player, hand, hit)) {
            reset();
            return;
        }
        state = State.BREAK;
        waitTicks = breakDelay.get();
    }

    private void breakCrystal(LocalPlayer player) {
        if (base == null) {
            reset();
            return;
        }
        List<EndCrystal> crystals = mc.level.getEntitiesOfClass(EndCrystal.class, new AABB(base.above()), Entity::isAlive);
        if (crystals.isEmpty()) return; // Wait for the server to spawn it; timeout covers a rejected placement.

        if (switchBack.get()) restoreSlot(player);
        mc.gameMode.attack(player, crystals.get(0));
        AttackUtil.swingMainHand(player);
        finishCycle(player);
    }

    private void finishCycle(LocalPlayer player) {
        if (mode.get() == Mode.SPAM && keyHeld()) {
            // Keep spamming: the next cycle starts on the next tick from the current aim.
            base = null;
            state = State.IDLE;
            return;
        }
        reset();
    }

    private boolean interact(LocalPlayer player, InteractionHand hand, BlockHitResult hit) {
        InteractionResult result = mc.gameMode.useItemOn(player, hand, hit);
        if (result == null || !result.consumesAction()) return false;
        player.swing(hand, player.getItemInHand(hand).getInteractAnimation(), false);
        return true;
    }

    private boolean isBase(BlockPos pos) {
        var block = mc.level.getBlockState(pos).getBlock();
        return block == Blocks.OBSIDIAN || block == Blocks.BEDROCK;
    }

    private boolean spaceAbove(BlockPos pos) {
        BlockPos above = pos.above();
        if (!mc.level.getBlockState(above).isAir()) return false;
        AABB box = new AABB(above.getX(), above.getY(), above.getZ(), above.getX() + 1.0, above.getY() + 2.0, above.getZ() + 1.0);
        return mc.level.getEntities((Entity) null, box, entity -> !(entity instanceof EndCrystal) || !entity.isAlive()).isEmpty();
    }

    private static int findHotbar(LocalPlayer player, Item item) {
        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getItem(i).is(item)) return i;
        }
        return -1;
    }

    private void select(LocalPlayer player, int slot) {
        ((PlayerInventoryAccessor) player.getInventory()).combatant$setSelectedSlot(slot);
        if (mc.gameMode instanceof MultiPlayerGameModeAccessor accessor) {
            accessor.combatant$syncSelectedSlot();
        }
    }

    private void restoreSlot(LocalPlayer player) {
        if (originalSlot >= 0 && originalSlot < 9) select(player, originalSlot);
    }

    private void reset() {
        LocalPlayer player = mc.player;
        if (state != State.IDLE && switchBack.get() && player != null && mc.gameMode != null) {
            restoreSlot(player);
        }
        state = State.IDLE;
        base = null;
        waitTicks = 0;
        stateTicks = 0;
        originalSlot = -1;
    }

    private String bindingName() {
        return name() + ":" + activationKey.getName();
    }

    private void ensureBind() {
        String combo = activationKey.get();
        if (combo == null) combo = "NONE";
        if (combo.equalsIgnoreCase(boundCombo)) return;
        CombatantInput.bindings().unbind(bindingName());
        if (!activationKey.isNone()) CombatantInput.bindings().bind(bindingName(), combo);
        boundCombo = combo;
    }

    private boolean keyHeld() {
        return !activationKey.isNone() && CombatantInput.bindings().isHeld(bindingName(), InputScope.GAME);
    }

    private enum State {
        IDLE, OBSIDIAN, CRYSTAL, BREAK
    }

    public enum Mode {
        SINGLE, SPAM
    }
}

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import combatant.client.config.values.BindMode;
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
import combatant.client.util.player.inventory.InventorySwap;
import combatant.client.util.player.inventory.SlotResult;
import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.HitResult;

/**
 * Keybind lava trick: pressing the key places a lava bucket on the block under the crosshair, picks it back
 * up on the next tick, and hands the previous hotbar slot back after a configurable delay.
 */
@ModuleInfo(
        id = "keylava",
        displayName = "KeyLava",
        aliases = {"LavaKey"},
        category = ModuleCategory.PLAYER,
        subcategory = ModuleSubcategory.AUTOMATION,
        description = "module.keylava.description")
public final class KeyLava extends Module {

    private static final int PHASE_TIMEOUT_TICKS = 6;

    private final Minecraft mc = Minecraft.getInstance();

    private final KeyBindValue activationKey = bind("keyLavaActivationKey", "activation_key", "NONE", BindMode.PRESS);
    private final NumberValue<Integer> restoreDelayMs = num("keyLavaRestoreDelay", "restore_delay_ms", 100, 0, 1000);

    private Phase phase = Phase.IDLE;
    private int previousSlot = -1;
    private int lavaSlot = -1;
    private int phaseTicks;
    private long restoreAt;
    private String lastCombo = "NONE";

    @Override
    public void onEnable() {
        reset();
        ensureBind();
    }

    @Override
    public void onDisable() {
        restoreSlot();
        reset();
        CombatantInput.bindings().unbind(bindingName());
        lastCombo = "NONE";
    }

    @EventHandler
    private void onRotationUpdate(RotationUpdateEvent event) {
        if (event.getType() != RotationUpdateEvent.Type.PRE || !isEnabled()) return;
        ensureBind();

        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gameMode == null) {
            reset();
            return;
        }
        if (ClientScreen.current(mc) != null) {
            if (phase != Phase.IDLE) {
                restoreSlot();
                reset();
            }
            return;
        }

        switch (phase) {
            case IDLE -> {
                if (CombatantInput.bindings().consumePress(bindingName(), InputScope.GAME)) place();
            }
            case PICKUP -> pickup(player);
            case RESTORE -> {
                if (System.currentTimeMillis() >= restoreAt) {
                    restoreSlot();
                    reset();
                }
            }
        }
    }

    private void place() {
        if (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.BLOCK) return;
        SlotResult lava = InventorySwap.INSTANCE.findHotbar(stack -> stack.is(Items.LAVA_BUCKET));
        if (!lava.found()) return;

        previousSlot = InventorySwap.INSTANCE.clientSelectedSlot();
        lavaSlot = lava.slot();
        if (!InventorySwap.INSTANCE.selectHotbar(lavaSlot)) {
            reset();
            return;
        }
        if (!use()) {
            restoreSlot();
            reset();
            return;
        }
        phase = Phase.PICKUP;
        phaseTicks = 0;
    }

    private void pickup(LocalPlayer player) {
        if (++phaseTicks > PHASE_TIMEOUT_TICKS) {
            beginRestore();
            return;
        }
        if (!player.getMainHandItem().is(Items.BUCKET)) return;
        if (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.BLOCK) return;
        use();
        beginRestore();
    }

    private boolean use() {
        InteractionResult result = InventorySwap.INSTANCE.useItem(InteractionHand.MAIN_HAND);
        if (!result.consumesAction()) return false;
        mc.player.swing(InteractionHand.MAIN_HAND, mc.player.getItemInHand(InteractionHand.MAIN_HAND).getInteractAnimation(), false);
        return true;
    }

    private void beginRestore() {
        phase = Phase.RESTORE;
        restoreAt = System.currentTimeMillis() + restoreDelayMs.get();
    }

    private void restoreSlot() {
        if (previousSlot >= 0 && mc.player != null && InventorySwap.INSTANCE.clientSelectedSlot() == lavaSlot) {
            InventorySwap.INSTANCE.selectHotbar(previousSlot);
        }
        previousSlot = -1;
        lavaSlot = -1;
    }

    private void reset() {
        phase = Phase.IDLE;
        phaseTicks = 0;
        previousSlot = -1;
        lavaSlot = -1;
        restoreAt = 0L;
    }

    private String bindingName() {
        return name() + ":" + activationKey.getName();
    }

    private void ensureBind() {
        String combo = activationKey.get();
        if (combo == null) combo = "NONE";
        if (combo.equalsIgnoreCase(lastCombo)) return;
        String name = bindingName();
        CombatantInput.bindings().unbind(name);
        if (!activationKey.isNone()) CombatantInput.bindings().bind(name, combo);
        lastCombo = combo;
    }

    private enum Phase {
        IDLE,
        PICKUP,
        RESTORE
    }
}

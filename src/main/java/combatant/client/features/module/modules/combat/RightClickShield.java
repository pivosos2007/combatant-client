/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat;

import combatant.client.config.values.EnumValue;
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
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Holding right click with a weapon raises a hotbar shield: the shield slot is selected and the use key is
 * held for as long as the physical button is down, then the weapon slot is restored.
 */
@ModuleInfo(
        id = "rightclickshield",
        displayName = "RightClickShield",
        aliases = {"AutoShield"},
        category = ModuleCategory.COMBAT,
        subcategory = ModuleSubcategory.LEGIT,
        description = "module.rightclickshield.description")
public final class RightClickShield extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final EnumValue<WeaponType> weaponType = enumSetting("rightClickShieldWeapon", "weapon_type", WeaponType.BOTH, WeaponType.values());
    private final NumberValue<Integer> delayMs = num("rightClickShieldDelay", "delay_ms", 0, 0, 200);

    private boolean active;
    private int previousSlot = -1;
    private int shieldSlot = -1;
    private long pressedSince;

    @Override
    public void onEnable() {
        resetState();
    }

    @Override
    public void onDisable() {
        deactivate();
        pressedSince = 0L;
    }

    @EventHandler
    private void onRotationUpdate(RotationUpdateEvent event) {
        if (event.getType() != RotationUpdateEvent.Type.PRE || !isEnabled()) return;

        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.options == null || ClientScreen.current(mc) != null) {
            deactivate();
            pressedSince = 0L;
            return;
        }

        boolean physical = CombatantInput.bindings().isChordHeld("MOUSE_RIGHT", InputScope.GAME);
        if (!physical || player.getOffhandItem().is(Items.SHIELD)) {
            deactivate();
            pressedSince = 0L;
            return;
        }

        if (active) {
            if (!player.getMainHandItem().is(Items.SHIELD)) {
                // Slot changed under us (scroll, hotkey): let go instead of fighting the player.
                deactivate();
                return;
            }
            mc.options.keyUse.setDown(true);
            return;
        }

        if (!matchesWeapon(player.getMainHandItem())) {
            pressedSince = 0L;
            return;
        }
        SlotResult shield = InventorySwap.INSTANCE.findHotbar(stack -> stack.is(Items.SHIELD));
        if (!shield.found()) {
            pressedSince = 0L;
            return;
        }

        long now = System.currentTimeMillis();
        if (delayMs.get() > 0) {
            if (pressedSince == 0L) {
                pressedSince = now;
                return;
            }
            if (now - pressedSince < delayMs.get()) return;
        }

        previousSlot = InventorySwap.INSTANCE.clientSelectedSlot();
        shieldSlot = shield.slot();
        if (!InventorySwap.INSTANCE.selectHotbar(shieldSlot)) {
            resetState();
            return;
        }
        active = true;
        pressedSince = 0L;
        mc.options.keyUse.setDown(true);
    }

    private boolean matchesWeapon(ItemStack stack) {
        if (stack.isEmpty()) return false;
        boolean sword = stack.is(ItemTags.SWORDS);
        boolean axe = stack.is(ItemTags.AXES);
        return switch (weaponType.get()) {
            case SWORDS -> sword;
            case AXES -> axe;
            case BOTH -> sword || axe;
            case ALL_WEAPONS -> sword || axe || stack.is(Items.MACE) || stack.is(Items.TRIDENT);
        };
    }

    private void deactivate() {
        if (!active) {
            resetState();
            return;
        }
        if (mc.options != null) mc.options.keyUse.setDown(false);
        if (mc.player != null && previousSlot >= 0
                && InventorySwap.INSTANCE.clientSelectedSlot() == shieldSlot) {
            InventorySwap.INSTANCE.selectHotbar(previousSlot);
        }
        resetState();
    }

    private void resetState() {
        active = false;
        previousSlot = -1;
        shieldSlot = -1;
    }

    public enum WeaponType {
        BOTH,
        SWORDS,
        AXES,
        ALL_WEAPONS
    }
}

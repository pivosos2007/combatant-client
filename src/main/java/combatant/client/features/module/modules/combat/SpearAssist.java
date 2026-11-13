/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat;

import combatant.client.config.values.BooleanMapValue;
import combatant.client.config.values.EnumValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.util.combat.AttackUtil;
import combatant.client.util.combat.SpearKinetics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.KineticWeapon;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.phys.EntityHitResult;

import java.util.Map;
import java.util.Optional;

@ModuleInfo(
        id = "spearassist",
        displayName = "SpearAssist",
        category = ModuleCategory.COMBAT, subcategory = ModuleSubcategory.LEGIT,
        description = "module.spearassist.description")
public class SpearAssist extends Module {

    public enum DualAttackMode {
        OFF, LEGIT, KEEP_USE
    }

    private final Minecraft mc = Minecraft.getInstance();

    // Keep the original group/config key so existing auto-recharge settings remain intact.
    private final BooleanMapValue toggles = group(
            "spearassist_toggles", "toggles", Map.of("auto_recharge", false));
    private final EnumValue<DualAttackMode> dualAttack = enumSetting(
            "spearassist_dual_attack", "dual_attack", DualAttackMode.OFF, DualAttackMode.values());

    public static boolean isSpear(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && (stack.get(DataComponents.KINETIC_WEAPON) != null
                || stack.get(DataComponents.PIERCING_WEAPON) != null);
    }

    private static int duration(Optional<KineticWeapon.Condition> condition) {
        return condition.map(KineticWeapon.Condition::maxDurationTicks).orElse(-1);
    }

    /** Release only after all kinetic-weapon effect windows have expired. */
    @EventHandler
    private void onGameTick(GameTickEvent event) {
        if (!isEnabled() || !toggles.get("auto_recharge") || mc.player == null
                || mc.gameMode == null || mc.options == null) return;
        LocalPlayer player = mc.player;
        if (!player.isUsingItem()) return;
        KineticWeapon weapon = player.getUseItem().get(DataComponents.KINETIC_WEAPON);
        if (weapon == null) return;
        if (SpearKinetics.shouldRecharge(mc.options.keyUse.isDown(), player.getTicksUsingItem(),
                weapon.delayTicks(), duration(weapon.dismountConditions()),
                duration(weapon.knockbackConditions()), duration(weapon.damageConditions()))) {
            mc.gameMode.releaseUsingItem(player);
        }
    }

    /**
     * Invoked from Minecraft.handleKeybinds() when vanilla drains attack clicks while
     * an item is in use. Minecraft.startAttack() is never reached in that branch.
     *
     * KEEP_USE sends a normal main-hand attack without a RELEASE_USE_ITEM packet.
     * The vanilla server's handleAttack() validates attack range and weapon charge,
     * and does not require item use to end. LEGIT sends the release before attacking.
     */
    public boolean handleAttackClickDuringItemUse() {
        if (!isEnabled() || dualAttack.get() == DualAttackMode.OFF || mc.player == null
                || mc.gameMode == null || mc.level == null || mc.hitResult == null) return false;

        LocalPlayer player = mc.player;
        if (!player.isUsingItem() || player.getUsedItemHand() != InteractionHand.OFF_HAND
                || player.isSpectator() || mc.gameMode.isSpectator()
                || !isSpear(player.getUseItem())) return false;

        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.isEmpty() || isSpear(mainHand)
                || !mainHand.isItemEnabled(mc.level.enabledFeatures())
                || player.cannotAttackWithItem(mainHand, 0)
                || !(mc.hitResult instanceof EntityHitResult hit)) return false;

        Entity target = hit.getEntity();
        if (target == null || target == player || target.isRemoved() || !target.isPickable()
                || target instanceof ItemEntity || target instanceof ExperienceOrb) return false;

        // Match the vanilla main-hand range check before sending an attack packet.
        AttackRange range = mainHand.get(DataComponents.ATTACK_RANGE);
        if (range != null && !range.isInRange(player, hit.getLocation())) return false;

        if (dualAttack.get() == DualAttackMode.LEGIT) {
            mc.gameMode.releaseUsingItem(player);
        }

        // attack() sends ServerboundAttackPacket, updates client attack state and
        // resets the main-hand cooldown. swingMainHand() sends ServerboundPunchPacket.
        mc.gameMode.attack(player, target);
        AttackUtil.swingMainHand(player);
        return true;
    }
}

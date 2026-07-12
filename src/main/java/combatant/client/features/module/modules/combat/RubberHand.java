/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;
import combatant.client.config.values.BooleanMapValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.EventSync;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.util.combat.AttackUtil;
import combatant.client.util.item.FoodUtil;

import java.util.LinkedHashMap;

@ModuleInfo(
        id = "rubberhand",
        displayName = "RubberHand",
        aliases = "MultiActions",
        category = ModuleCategory.COMBAT,
        description = "module.rubberhand.description"
)
public class RubberHand extends Module {

    private static final String SETTING_ACTIONS = "actions";
    private static final String ACTION_ATTACK_WHILE_SHIELD = "attack_while_shield";
    private static final String ACTION_ATTACK_WHILE_EATING = "attack_while_eating";

    private final Minecraft mc = Minecraft.getInstance();
    private final BooleanMapValue actions = group(
            "rubberhand_actions",
            SETTING_ACTIONS,
            new LinkedHashMap<>() {{
                put(ACTION_ATTACK_WHILE_SHIELD, true);
                put(ACTION_ATTACK_WHILE_EATING, false);
            }}
    );
    private boolean didAttackThisHold = false;

    public boolean canAttackWhileShield() {
        return isEnabled() && actions.get(ACTION_ATTACK_WHILE_SHIELD);
    }

    public boolean canAttackWhileEating() {
        return isEnabled() && actions.get(ACTION_ATTACK_WHILE_EATING);
    }

    public boolean canBypassShieldUse() {
        return canAttackWhileShield();
    }

    public boolean canBypassFoodUse() {
        return canAttackWhileEating();
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;
        if (mc.player == null) return;

        boolean attackPressed = mc.options.keyAttack.isDown();

        if (!attackPressed) {
            didAttackThisHold = false;
        }
    }

    @EventHandler
    private void onSync(EventSync event) {
        if (!isEnabled() || mc.player == null || mc.gameMode == null) return;
        if (didAttackThisHold) return;
        if (!mc.options.keyAttack.isDown() || !mc.options.keyUse.isDown()) return;
        if (!(mc.hitResult instanceof EntityHitResult ehr)) return;

        LocalPlayer player = mc.player;
        Entity target = ehr.getEntity();

        boolean usingShield = player.isUsingItem()
                && (player.getUseItem().is(Items.SHIELD) || player.getOffhandItem().is(Items.SHIELD));
        boolean usingFood = player.isUsingItem() && FoodUtil.isFood(player.getUseItem());

        if (usingFood) {
            if (!canAttackWhileEating()) {
                return;
            }

            if (target instanceof net.minecraft.world.entity.LivingEntity living) {
                AttackUtil.attackCurrentItem(mc, living, false);
                didAttackThisHold = true;
            }
            return;
        }

        if (!usingShield || !canAttackWhileShield()) {
            return;
        }

        boolean restoreUseKey = mc.options.keyUse.isDown();

        player.releaseUsingItem();
        mc.options.keyUse.setDown(false);

        performAttack(player, target);

        if (restoreUseKey) {
            event.addPostAction(() -> mc.options.keyUse.setDown(true));
        }
    }

    private void performAttack(LocalPlayer player, Entity target) {
        mc.gameMode.attack(player, target);
        player.swing(InteractionHand.MAIN_HAND);
        didAttackThisHold = true;
    }
}

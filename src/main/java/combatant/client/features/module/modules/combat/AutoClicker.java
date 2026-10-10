/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.modules.combat.autoclicker.ClickGate;
import combatant.client.features.relations.CategoryRules;
import combatant.client.features.relations.CategoryType;
import combatant.client.util.click.ClickScheduler;
import combatant.client.util.combat.AttackUtil;
import combatant.client.util.screen.ClientScreen;

/**
 * Left-click macro for servers where holding the button should keep hitting (1.8-style PvP, or
 * ViaFabricPlus into an older version). It never clicks while you look at a block, so mining stays
 * vanilla, and by default it waits for the attack cooldown so it is also usable on current servers.
 */
@ModuleInfo(
        id = "autoclicker",
        displayName = "AutoClicker",
        aliases = {"Clicker", "HoldClick"},
        category = ModuleCategory.COMBAT,
        subcategory = ModuleSubcategory.LEGIT,
        description = "module.autoclicker.description")
public final class AutoClicker extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Integer> minCps = num("autoClickerMinCps", "min_cps", 8, 1, 20);
    private final NumberValue<Integer> maxCps = num("autoClickerMaxCps", "max_cps", 12, 1, 20);
    private final BooleanValue holdToClick = bool("autoClickerHoldToClick", "hold_to_click", true);
    private final BooleanValue respectCooldown = bool("autoClickerRespectCooldown", "respect_cooldown", true);
    private final BooleanValue entitiesOnly = bool("autoClickerEntitiesOnly", "entities_only", false);
    private final BooleanValue weaponOnly = bool("autoClickerWeaponOnly", "weapon_only", false);
    private final BooleanValue ignoreFriends = bool("autoClickerIgnoreFriends", "ignore_friends", true);

    private final ClickScheduler clicker = new ClickScheduler(minCps.get(), maxCps.get());
    private int appliedMin = -1;
    private int appliedMax = -1;

    @Override
    public void onEnable() {
        appliedMin = -1;
        appliedMax = -1;
    }

    /**
     * Clicks from the pre-tick hook. {@code onTick} runs after the player's movement packet, which put every
     * attack behind the flying packet of its tick; a vanilla client sends its click first, and Grim's Post check
     * flags the other order.
     */
    @EventHandler
    private void onGameTick(GameTickEvent event) {
        if (!isEnabled()) return;

        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gameMode == null) return;
        if (ClientScreen.current() != null || player.isUsingItem() || player.isSpectator()) return;

        // setCps refills the click pattern, so only call it when a setting actually changed.
        int min = minCps.get();
        int max = Math.max(min, maxCps.get());
        if (min != appliedMin || max != appliedMax) {
            clicker.setCps(min, max);
            appliedMin = min;
            appliedMax = max;
        }

        clicker.tick();
        if (!clicker.shouldClick()) return;

        HitResult hit = mc.hitResult;
        Entity entity = hit instanceof EntityHitResult entityHit ? entityHit.getEntity() : null;
        boolean attackable = entity instanceof LivingEntity;

        ClickGate.Verdict verdict = ClickGate.decide(
                holdToClick.get(),
                mc.options.keyAttack.isDown(),
                hit != null && hit.getType() == HitResult.Type.BLOCK,
                attackable,
                attackable && ignoreFriends.get() && isFriend(entity),
                entitiesOnly.get(),
                weaponOnly.get(),
                isWeapon(player.getMainHandItem()),
                respectCooldown.get(),
                player.getAttackStrengthScale(0.5f));

        switch (verdict) {
            case ATTACK -> AttackUtil.attack(mc, (LivingEntity) entity);
            case SWING -> {
                player.swing(InteractionHand.MAIN_HAND);
                player.resetAttackStrengthTicker();
            }
            case SKIP -> {
            }
        }
    }

    private static boolean isFriend(Entity entity) {
        if (!(entity instanceof Player other)) return false;
        CategoryType type = CategoryRules.determine(other.getGameProfile().name());
        return type == CategoryType.FRIEND || type == CategoryType.BEDWARS_SELF;
    }

    private static boolean isWeapon(ItemStack stack) {
        return stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES) || stack.is(ItemTags.SPEARS)
                || stack.is(Items.MACE) || stack.is(Items.TRIDENT);
    }
}

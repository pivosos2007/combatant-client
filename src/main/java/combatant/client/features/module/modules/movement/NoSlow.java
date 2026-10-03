/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec2;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.util.item.FoodUtil;
import combatant.client.util.player.InteractionUtil;

@ModuleInfo(
        id = "noslow",
        displayName = "NoSlow",
        category = ModuleCategory.MOVEMENT,
        subcategory = ModuleSubcategory.BASIC,
        description = "module.noslow.description"
)
public final class NoSlow extends Module {

    public enum Mode {
        VANILLA,
        GRIM,
        GRIM_TICK,
        CUSTOM
    }

    private final EnumValue<Mode> mode = enumMode("mode", Mode.VANILLA, Mode.values());

    private final BooleanValue food = bool("food", true);
    private final BooleanValue bow = bool("bow", true);
    private final BooleanValue crossbow = bool("crossbow", true);
    private final BooleanValue shield = bool("shield", true);
    private final BooleanValue trident = bool("trident", true);
    private final BooleanValue other = bool("other", true);

    private final BooleanValue onlyOnGround = bool("only_on_ground", false);
    private final BooleanValue allowSprint = bool("sprint", true);

    private final NumberValue<Float> forwardMultiplier = visibleWhen(
            num("forward_multiplier", 1.0f, 0.2f, 1.0f),
            () -> mode.get() == Mode.CUSTOM
    );
    private final NumberValue<Float> strafeMultiplier = visibleWhen(
            num("strafe_multiplier", 1.0f, 0.2f, 1.0f),
            () -> mode.get() == Mode.CUSTOM
    );

    private int grimTicks;

    @EventHandler
    private void onGameTick(GameTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            grimTicks = 0;
            return;
        }
        if (mc.player.isUsingItem()) {
            grimTicks++;
        } else {
            grimTicks = 0;
        }
    }

    @Override
    public void onDisable() {
        grimTicks = 0;
    }

    public boolean shouldModifyInput(LocalPlayer player) {
        if (!isEnabled()) return false;
        if (player == null || !player.isUsingItem() || player.isPassenger()) return false;
        if (onlyOnGround.get() && !player.onGround()) return false;
        return appliesToItem(player.getUseItem());
    }

    public boolean appliesToItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (food.get() && (FoodUtil.isFood(stack) || isDrinkOrConsumable(stack))) return true;
        if (bow.get() && stack.is(Items.BOW)) return true;
        if (crossbow.get() && stack.is(Items.CROSSBOW)) return true;
        if (shield.get() && stack.is(Items.SHIELD)) return true;
        if (trident.get() && stack.is(Items.TRIDENT)) return true;
        if (other.get()) {
            ItemUseAnimation anim = stack.getUseAnimation();
            return anim != ItemUseAnimation.NONE;
        }
        return false;
    }

    private boolean isDrinkOrConsumable(ItemStack stack) {
        if (stack.get(DataComponents.CONSUMABLE) != null) return true;
        ItemUseAnimation anim = stack.getUseAnimation();
        return anim == ItemUseAnimation.DRINK || anim == ItemUseAnimation.EAT;
    }

    public boolean shouldAllowSprint(LocalPlayer player) {
        return isEnabled() && allowSprint.get() && shouldModifyInput(player);
    }

    public Vec2 getModifiedInput(Vec2 input, LocalPlayer player) {
        Mode currentMode = mode.get();
        if (currentMode == Mode.GRIM) {
            if (input.x != 0.0f || input.y != 0.0f) {
                handleGrimBypass(player);
            }
            return input;
        } else if (currentMode == Mode.GRIM_TICK) {
            if (grimTicks >= 2) {
                grimTicks = 0;
                return input;
            }
            return input.scale(0.2f);
        } else if (currentMode == Mode.CUSTOM) {
            return new Vec2(input.x * strafeMultiplier.get(), input.y * forwardMultiplier.get());
        } else {
            // VANILLA
            return input;
        }
    }

    private void handleGrimBypass(LocalPlayer player) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null || player == null) return;
        InteractionHand activeHand = player.getUsedItemHand();
        InteractionHand otherHand = (activeHand == InteractionHand.MAIN_HAND) ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        InteractionUtil.sendSequencedPacket(id ->
                new ServerboundUseItemPacket(otherHand, id, player.getYRot(), player.getXRot())
        );
    }
}

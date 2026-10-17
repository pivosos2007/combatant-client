/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.PacketEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.Notifier;
import combatant.client.util.player.inventory.InventoryActionKind;
import combatant.client.util.player.inventory.InventorySwap;
import combatant.client.util.player.inventory.InventorySwapPolicy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

@ModuleInfo(
        id = "autofish",
        displayName = "AutoFish",
        aliases = {"FishBot", "FishingHelper"},
        category = ModuleCategory.PLAYER,
        subcategory = ModuleSubcategory.AUTOMATION,
        description = "module.autofish.description")
public final class AutoFish extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Integer> recastDelay =
            num("autoFishRecastDelay", "recast_delay", 6, 2, 40);
    private final BooleanValue autoCast =
            bool("autoFishAutoCast", "auto_cast", true);
    private final BooleanValue saveRod =
            bool("autoFishSaveRod", "save_rod", true);
    private final NumberValue<Integer> minimumDurability = visibleWhen(
            num("autoFishMinimumDurability", "minimum_durability", 10, 1, 100),
            saveRod::get
    );

    private volatile boolean bitePending;
    private int recastTicks;
    private int initialCastTicks;
    private boolean rodSwapPending;
    private boolean warnedNoRod;

    @Override
    public void onEnable() {
        resetState();
    }

    @Override
    public void onDisable() {
        resetState();
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;

        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null || mc.level == null) {
            resetState();
            return;
        }

        InteractionHand rodHand = rodHand(player);
        if (rodHand == null) {
            bitePending = false;
            recastTicks = 0;
            initialCastTicks = 0;
            return;
        }

        if (saveRod.get() && isLowDurability(player.getItemInHand(rodHand))) {
            if (!rodSwapPending && !replaceRod(player, rodHand)) {
                if (!warnedNoRod) {
                    warnedNoRod = true;
                    Notifier.warning(I18n.get("notification.autofish.no_replacement"));
                }
            }
            return;
        }
        warnedNoRod = false;

        if (rodSwapPending) return;

        if (bitePending && player.fishing != null) {
            bitePending = false;
            useRod(player, rodHand);
            recastTicks = recastDelay.get();
            return;
        }

        if (recastTicks > 0) {
            recastTicks--;
            if (recastTicks == 0 && player.fishing == null) {
                InteractionHand currentHand = rodHand(player);
                if (currentHand != null) useRod(player, currentHand);
            }
            return;
        }

        if (player.fishing == null && autoCast.get()) {
            initialCastTicks++;
            if (initialCastTicks >= recastDelay.get()) {
                initialCastTicks = 0;
                useRod(player, rodHand);
            }
        } else {
            initialCastTicks = 0;
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!isEnabled() || !(event.getPacket() instanceof ClientboundSoundPacket sound)) return;
        if (sound.getSound().value() != SoundEvents.FISHING_BOBBER_SPLASH) return;

        LocalPlayer player = mc.player;
        if (player == null) return;
        FishingHook hook = player.fishing;
        if (hook == null) return;

        Vec3 hookPos = hook.position();
        double dx = sound.getX() - hookPos.x;
        double dy = sound.getY() - hookPos.y;
        double dz = sound.getZ() - hookPos.z;
        if (dx * dx + dy * dy + dz * dz <= 4.0D) bitePending = true;
    }

    private boolean replaceRod(LocalPlayer player, InteractionHand currentHand) {
        int bestSlot = bestReplacementSlot(player);
        if (bestSlot < 0) return false;

        rodSwapPending = true;
        boolean accepted = InventorySwap.INSTANCE.command(
                InventoryActionKind.INVENTORY_CLICK,
                InventorySwapPolicy.LEGIT,
                () -> {
                    try {
                        if (mc.player == null) return;
                        if (currentHand == InteractionHand.OFF_HAND) {
                            InventorySwap.INSTANCE.swapInventoryToOffhand(bestSlot);
                        } else {
                            int selected = InventorySwap.INSTANCE.clientSelectedSlot();
                            if (bestSlot >= 0 && bestSlot < 9) {
                                InventorySwap.INSTANCE.selectHotbar(bestSlot);
                            } else {
                                InventorySwap.INSTANCE.swapInventoryToHotbar(bestSlot, selected);
                            }
                        }

                        InteractionHand replacementHand = rodHand(mc.player);
                        if (mc.player.fishing != null && replacementHand != null) {
                            useRod(mc.player, replacementHand);
                            recastTicks = recastDelay.get();
                            bitePending = false;
                        }
                    } finally {
                        rodSwapPending = false;
                    }
                }
        );
        if (!accepted) rodSwapPending = false;
        return accepted;
    }

    private int bestReplacementSlot(LocalPlayer player) {
        int bestSlot = -1;
        int bestDurability = minimumDurability.get();
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.is(Items.FISHING_ROD)) continue;
            int durability = remainingDurability(stack);
            if (durability <= bestDurability) continue;
            bestDurability = durability;
            bestSlot = slot;
        }
        return bestSlot;
    }

    private boolean isLowDurability(ItemStack stack) {
        return stack.is(Items.FISHING_ROD) && remainingDurability(stack) <= minimumDurability.get();
    }

    private int remainingDurability(ItemStack stack) {
        return stack.getMaxDamage() - stack.getDamageValue();
    }

    private InteractionHand rodHand(LocalPlayer player) {
        if (player.getMainHandItem().is(Items.FISHING_ROD)) return InteractionHand.MAIN_HAND;
        if (player.getOffhandItem().is(Items.FISHING_ROD)) return InteractionHand.OFF_HAND;
        return null;
    }

    private void useRod(LocalPlayer player, InteractionHand hand) {
        InteractionResult result = InventorySwap.INSTANCE.useItem(hand);
        if (result != null && result.consumesAction()) player.swing(hand, player.getItemInHand(hand).getInteractAnimation(), false);
    }

    private void resetState() {
        bitePending = false;
        recastTicks = 0;
        initialCastTicks = 0;
        rodSwapPending = false;
        warnedNoRod = false;
    }
}

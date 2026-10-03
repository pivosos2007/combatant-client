/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.EntityHitResult;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.mixins.accessors.MultiPlayerGameModeAccessor;
import combatant.client.mixins.accessors.PlayerInventoryAccessor;
import combatant.client.util.player.BreakSpeedUtil;
import combatant.client.util.player.ToolSelection;
import combatant.client.util.player.inventory.InventorySwap;

import java.util.function.BiConsumer;

@ModuleInfo(
        id = "autotool",
        displayName = "AutoTool",
        category = ModuleCategory.PLAYER, subcategory = ModuleSubcategory.AUTOMATION,
        description = "module.autotool.description")
public class AutoTool extends Module {

    private static final String SETTING_RESTORE_DELAY = "restore_delay_ms";

    /** A preferred enchantment outranks a plain tool that is up to this much faster. */
    private static final float PREFERRED_BONUS = 0.5f;

    private final BooleanValue weaponOnAttack =
            bool("autoToolWeaponOnAttack", "weapon_on_attack", false);
    private final EnumValue<EnchantPreference> enchantPreference =
            enumSetting("autoToolEnchantPreference", "enchant_preference", EnchantPreference.NONE, EnchantPreference.values());
    private final NumberValue<Integer> durabilityGuard =
            num("autoToolDurabilityGuard", "durability_guard", 0, 0, 90);
    private final BooleanValue sneakPauses =
            bool("autoToolSneakPauses", "sneak_pauses", false);
    private final BooleanValue restore =
            bool("autoToolRestore", "restore", true);
    private final NumberValue<Integer> restoreDelayMs =
            visibleWhen(num("autoToolRestoreDelayMs", SETTING_RESTORE_DELAY, 300, 0, 1000), restore::get);

    private final Minecraft mc = Minecraft.getInstance();
    private final ToolSelection picker = new ToolSelection();

    // Scratch for weaponScore: forEachModifier takes a callback, and a fresh lambda per slot per tick adds up.
    private double scratchDamage;
    private double scratchAttackSpeed;
    private final BiConsumer<Holder<Attribute>, AttributeModifier> weaponCollector = (attribute, modifier) -> {
        if (modifier.operation() != AttributeModifier.Operation.ADD_VALUE) return;
        if (attribute.is(Attributes.ATTACK_DAMAGE)) {
            scratchDamage += modifier.amount();
        } else if (attribute.is(Attributes.ATTACK_SPEED)) {
            scratchAttackSpeed += modifier.amount();
        }
    };

    private int originalSlot = -1;
    private long lastBreakMs = 0L;

    @Override
    public void onDisable() {
        if (mc != null && mc.player != null && originalSlot >= 0) {
            InventorySwap.INSTANCE.selectHotbar(originalSlot);
        }
        originalSlot = -1;
        lastBreakMs = 0L;
    }

    @EventHandler
    private void onGameTick(GameTickEvent event) {
        if (!isEnabled() || mc.player == null || mc.level == null || mc.options == null || mc.gameMode == null) {
            resetState();
            return;
        }

        boolean paused = sneakPauses.get() && mc.player.isShiftKeyDown();
        if (!paused && mc.options.keyAttack.isDown()) {
            if (mc.gameMode.isDestroying()) {
                if (mc.gameMode instanceof MultiPlayerGameModeAccessor accessor) {
                    BlockPos pos = accessor.combatant$getCurrentBreakingPos();
                    if (pos != null) {
                        equip(findBestHotbarTool(pos));
                        return;
                    }
                }
            } else if (weaponOnAttack.get()
                    && mc.hitResult instanceof EntityHitResult hit
                    && hit.getEntity() instanceof LivingEntity) {
                equip(findBestWeapon());
                return;
            }
        }

        restoreIfIdle();
    }

    private void equip(int bestSlot) {
        int selected = selectedSlot();
        if (bestSlot >= 0 && bestSlot != selected) {
            if (originalSlot < 0) {
                originalSlot = selected;
            }
            InventorySwap.INSTANCE.selectHotbar(bestSlot);
        }
        lastBreakMs = System.currentTimeMillis();
    }

    private void restoreIfIdle() {
        if (originalSlot < 0) return;
        if (!restore.get()) {
            originalSlot = -1;
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastBreakMs >= restoreDelayMs.get()) {
            InventorySwap.INSTANCE.selectHotbar(originalSlot);
            originalSlot = -1;
        }
    }

    private int findBestHotbarTool(BlockPos pos) {
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) return -1;
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return -1;

        // Progress per tick, not raw destroy speed: it carries Efficiency and the correct-tool-for-drops penalty.
        float baseline = BreakSpeedUtil.progressPerTick(player, level, pos, state, ItemStack.EMPTY);
        Holder<Enchantment> preferred = preferredEnchantment(level);
        picker.reset(baseline, selectedSlot(), guardFraction(), preferred == null ? 0.0f : PREFERRED_BONUS);
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.isEmpty()) continue;
            float progress = BreakSpeedUtil.progressPerTick(player, level, pos, state, stack);
            boolean wanted = preferred != null && EnchantmentHelper.getItemEnchantmentLevel(preferred, stack) > 0;
            picker.offer(slot, progress, durabilityLeft(stack), wanted);
        }
        return picker.result();
    }

    private int findBestWeapon() {
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) return -1;

        Holder<Enchantment> sharpness = enchantmentHolder(level, Enchantments.SHARPNESS);
        picker.reset(ToolSelection.weaponScore(0.0, 0.0), selectedSlot(), guardFraction(), 0.0f);
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.isEmpty()) continue;
            picker.offer(slot, weaponScore(stack, sharpness), durabilityLeft(stack), false);
        }
        return picker.result();
    }

    private float weaponScore(ItemStack stack, Holder<Enchantment> sharpness) {
        scratchDamage = 0.0;
        scratchAttackSpeed = 0.0;
        stack.forEachModifier(EquipmentSlot.MAINHAND, weaponCollector);
        if (sharpness != null) {
            int level = EnchantmentHelper.getItemEnchantmentLevel(sharpness, stack);
            if (level > 0) scratchDamage += 0.5 * level + 0.5;
        }
        return ToolSelection.weaponScore(scratchDamage, scratchAttackSpeed);
    }

    private Holder<Enchantment> preferredEnchantment(ClientLevel level) {
        return switch (enchantPreference.get()) {
            case NONE -> null;
            case SILK_TOUCH -> enchantmentHolder(level, Enchantments.SILK_TOUCH);
            case FORTUNE -> enchantmentHolder(level, Enchantments.FORTUNE);
        };
    }

    private static Holder<Enchantment> enchantmentHolder(ClientLevel level, ResourceKey<Enchantment> key) {
        Registry<Enchantment> registry = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        Enchantment enchantment = registry.getValue(key);
        return enchantment == null ? null : registry.wrapAsHolder(enchantment);
    }

    private static float durabilityLeft(ItemStack stack) {
        if (!stack.isDamageableItem()) return 1.0f;
        return ToolSelection.durabilityLeft(stack.getDamageValue(), stack.getMaxDamage());
    }

    private float guardFraction() {
        return durabilityGuard.get() / 100.0f;
    }

    private int selectedSlot() {
        return ((PlayerInventoryAccessor) mc.player.getInventory()).combatant$getSelectedSlot();
    }

    private void resetState() {
        originalSlot = -1;
        lastBreakMs = 0L;
    }

    public enum EnchantPreference {
        NONE,
        SILK_TOUCH,
        FORTUNE
    }
}

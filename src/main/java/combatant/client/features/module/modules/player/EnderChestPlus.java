/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.Slot;
import net.minecraft.ChatFormatting;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Remembers the contents of the last ender chest you opened (for this session) and shows them without opening it:
 * as a tooltip on any ender chest item and through the {@code @echest} command. The cache is refreshed every tick
 * while the ender chest screen is open, so it always matches what the server last showed.
 */
@ModuleInfo(
        id = "enderchestplus",
        displayName = "EnderChestPlus",
        aliases = {"EChestPlus", "EChestCache"},
        category = ModuleCategory.PLAYER, subcategory = ModuleSubcategory.AUTOMATION,
        description = "module.enderchestplus.description")
public final class EnderChestPlus extends Module {

    private static final int ROWS_SLOTS = 27;
    private static boolean tooltipRegistered;

    private final Minecraft mc = Minecraft.getInstance();
    private final BooleanValue tooltip = bool("enderChestPlusTooltip", "tooltip", true);
    private final NumberValue<Integer> tooltipLines = visibleWhen(
            num("enderChestPlusTooltipLines", "tooltip_lines", 8, 1, 27), tooltip::get);

    private final List<ItemStack> cache = new ArrayList<>();
    private boolean hasCache;

    public EnderChestPlus() {
        if (!tooltipRegistered) {
            tooltipRegistered = true;
            ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> appendTooltip(stack, lines));
        }
    }

    private void appendTooltip(ItemStack stack, List<Component> lines) {
        if (!isEnabled() || !tooltip.get() || !stack.is(Items.ENDER_CHEST)) return;
        if (!hasCache) {
            lines.add(Component.literal("Ender chest: not opened yet").withStyle(ChatFormatting.GRAY));
            return;
        }
        int shown = 0;
        Map<String, Integer> totals = totals();
        lines.add(Component.literal("Ender chest: " + totals.size() + " kinds").withStyle(ChatFormatting.LIGHT_PURPLE));
        for (Map.Entry<String, Integer> entry : totals.entrySet()) {
            if (shown++ >= tooltipLines.get()) {
                lines.add(Component.literal("...").withStyle(ChatFormatting.GRAY));
                break;
            }
            lines.add(Component.literal(entry.getValue() + "x " + entry.getKey()).withStyle(ChatFormatting.GRAY));
        }
    }

    @Override
    public void onDisable() {
        // The cache is kept: re-enabling shows the last known contents.
    }

    @Override
    public void onTick() {
        if (!isEnabled() || mc.player == null) return;
        if (!(combatant.client.util.screen.ClientScreen.current() instanceof ContainerScreen screen)) return;
        if (!screen.getTitle().getString().equalsIgnoreCase(I18n.get("container.enderchest"))) return;
        if (!(screen.getMenu() instanceof ChestMenu menu) || menu.slots.size() < ROWS_SLOTS) return;
        cache.clear();
        for (int i = 0; i < ROWS_SLOTS; i++) {
            Slot slot = menu.slots.get(i);
            cache.add(slot.getItem().copy());
        }
        hasCache = true;
    }

    public boolean hasCache() {
        return hasCache;
    }

    /** Item name to total count, in slot order of first appearance. */
    public Map<String, Integer> totals() {
        Map<String, Integer> totals = new LinkedHashMap<>();
        for (ItemStack stack : cache) {
            if (stack.isEmpty()) continue;
            totals.merge(stack.getHoverName().getString(), stack.getCount(), Integer::sum);
        }
        return totals;
    }
}

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.render;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.renderer.ui.runtime.core.UiBounds;
import combatant.client.render.engine.renderer.ui.runtime.core.UiNode;

public final class UiItemRendererBridge {
    public void render(UiNode node, UiRenderContext context) {
        if (node == null || context == null || context.renderer() == null) return;
        ItemStack stack = stack(node);
        if (stack.isEmpty()) return;
        UiBounds rawBounds = node.bounds();
        UiBounds bounds = new UiBounds(
                node.props().number("renderX", rawBounds.x()),
                node.props().number("renderY", rawBounds.y()),
                Math.max(0.0f, node.props().number("renderWidth", rawBounds.width())),
                Math.max(0.0f, node.props().number("renderHeight", rawBounds.height()))
        );
        float scale = Math.max(0.01f, Math.min(bounds.width(), bounds.height()) / 16.0f);
        boolean overlay = node.props().bool("overlay", true);
        String overlayMode = node.props().string("overlayMode", "all");
        int flags = overlay ? overlayFlags(overlayMode) : Renderer2D.ITEM_OVERLAY_NONE;
        int durabilityThreshold = Math.max(0, Math.min(100, Math.round(node.props().number("durabilityThreshold", 100.0f))));
        int durabilityColorThreshold = Math.max(0, Math.min(100, Math.round(node.props().number("durabilityColorThreshold", 70.0f))));
        int seed = Math.round(node.props().number("seed", 0.0f));
        Renderer2D renderer = context.renderer();
        // Keep the ordinary ui.item path identical to the pre-fade implementation.
        // Only nodes that explicitly opt into alpha (timed-list disappearing rows)
        // temporarily compose renderer alpha; Inventory/item-count overlays never do.
        if (!node.props().values().containsKey("alpha")) {
            renderer.item(
                    stack,
                    bounds.x(),
                    bounds.y(),
                    scale,
                    seed,
                    flags,
                    node.props().string("countText", null),
                    durabilityThreshold,
                    durabilityColorThreshold
            );
            return;
        }

        double previousAlpha = renderer.getAlpha();
        double itemAlpha = Math.max(0.0, Math.min(1.0, node.props().number("alpha", 1.0f)));
        renderer.setAlpha(previousAlpha * itemAlpha);
        try {
            renderer.item(
                    stack,
                    bounds.x(),
                    bounds.y(),
                    scale,
                    seed,
                    flags,
                    node.props().string("countText", null),
                    durabilityThreshold,
                    durabilityColorThreshold
            );
        } finally {
            renderer.setAlpha(previousAlpha);
        }
    }

    private static int overlayFlags(String mode) {
        if (mode == null || mode.isBlank()) return Renderer2D.ITEM_OVERLAY_ALL;
        return switch (mode.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "none" -> Renderer2D.ITEM_OVERLAY_NONE;
            case "count" -> Renderer2D.ITEM_OVERLAY_COUNT;
            case "durability" -> Renderer2D.ITEM_OVERLAY_DURABILITY;
            case "durability-text", "durability_text", "text" -> Renderer2D.ITEM_OVERLAY_DURABILITY_TEXT;
            case "durability+count", "durability-count" -> Renderer2D.ITEM_OVERLAY_DURABILITY | Renderer2D.ITEM_OVERLAY_COUNT;
            case "durability-text+count", "durability-text-count" -> Renderer2D.ITEM_OVERLAY_DURABILITY_TEXT | Renderer2D.ITEM_OVERLAY_COUNT;
            default -> Renderer2D.ITEM_OVERLAY_ALL;
        };
    }

    private ItemStack stack(UiNode node) {
        Object direct = node.props().get("stack");
        if (direct instanceof ItemStack itemStack) return itemStack;
        String id = node.props().string("item", node.props().string("id", ""));
        if (id.isBlank()) return ItemStack.EMPTY;
        try {
            Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
            int count = Math.max(1, Math.round(node.props().number("count", 1.0f)));
            ItemStack stack = new ItemStack(item, count);
            int damage = Math.max(0, Math.round(node.props().number("damage", 0.0f)));
            int maxDamage = Math.max(0, Math.round(node.props().number("maxDamage", 0.0f)));
            if (damage > 0 && maxDamage > 0 && stack.isDamageableItem()) {
                stack.setDamageValue(Math.min(damage, stack.getMaxDamage()));
            }
            return stack;
        } catch (Exception ignored) {
            return ItemStack.EMPTY;
        }
    }
}

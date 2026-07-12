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
        int flags = node.props().bool("overlay", true)
                ? Renderer2D.ITEM_OVERLAY_ALL
                : Renderer2D.ITEM_OVERLAY_NONE;
        context.renderer().item(
                stack,
                bounds.x(),
                bounds.y(),
                scale,
                0,
                flags,
                node.props().string("countText", null)
        );
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

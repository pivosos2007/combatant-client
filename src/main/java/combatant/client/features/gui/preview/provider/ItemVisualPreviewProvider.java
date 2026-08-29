/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.preview.provider;

import combatant.client.features.gui.chat.ChatHoverUtil;
import combatant.client.features.gui.clickgui.ClickGuiRenderer;
import combatant.client.features.gui.preview.VisualPreviewControlMode;
import combatant.client.features.gui.preview.VisualPreviewProvider;
import combatant.client.features.gui.preview.VisualPreviewSceneContext;
import combatant.client.features.gui.preview.render.VisualPreviewItemRenderer;
import combatant.client.render.engine.renderer.Renderer2D;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

public final class ItemVisualPreviewProvider implements VisualPreviewProvider {
    private final ItemStack stack;

    public ItemVisualPreviewProvider(ItemStack stack) {
        this.stack = stack == null ? ItemStack.EMPTY : stack.copy();
    }

    @Override
    public String id() {
        return "item:" + BuiltInRegistries.ITEM.getKey(stack.getItem());
    }

    @Override
    public String title() {
        return stack.isEmpty() ? "Item Preview" : stack.getHoverName().getString();
    }

    @Override
    public VisualPreviewControlMode controlMode() {
        return VisualPreviewControlMode.OBJECT_ROTATE;
    }

    @Override
    public void renderSubject(VisualPreviewSceneContext context) {
        VisualPreviewItemRenderer.render(stack, context);
    }

    @Override
    public void renderOverlay(VisualPreviewSceneContext context, Renderer2D renderer) {
        if (stack.isEmpty()) return;
        ChatHoverUtil.HoverTip tip = ChatHoverUtil.buildItemTip(stack, context.minecraft(), false);
        if (tip == null || tip.lines().isEmpty()) return;

        float scale = Math.max(0.85f, Math.min(1.35f, context.height() / 720.0f));
        float fontSize = 8.0f * scale;
        float x = 20.0f * scale;
        float y = 49.0f * scale;
        float lineHeight = 11.0f * scale;
        int limit = Math.min(14, tip.lines().size());
        for (int i = 0; i < limit; i++) {
            ChatHoverUtil.ColoredLine line = tip.lines().get(i);
            int color = line.color() == 0 ? 0xFFDDE3EA : line.color();
            ClickGuiRenderer.drawText(
                    ClickGuiRenderer.getIosevkaRegular(),
                    line.text(),
                    x,
                    y + i * lineHeight,
                    fontSize,
                    color,
                    false
            );
        }
    }

    public ItemStack stack() {
        return stack.copy();
    }
}

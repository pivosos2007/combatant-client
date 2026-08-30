/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.preview.provider;

import combatant.client.features.gui.chat.ChatHoverUtil;
import combatant.client.features.gui.hud.script.ScriptedTooltipPanel;
import combatant.client.features.gui.preview.VisualPreviewControlMode;
import combatant.client.features.gui.preview.VisualPreviewInteractionProfile;
import combatant.client.features.gui.preview.VisualPreviewProvider;
import combatant.client.features.gui.preview.VisualPreviewSceneContext;
import combatant.client.features.gui.preview.render.VisualPreviewItemRenderer;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.renderer.ui.runtime.render.UiProjectionMode;
import combatant.client.render.engine.text.TextRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class ItemVisualPreviewProvider implements VisualPreviewProvider {
    private final ItemStack stack;
    private final ScriptedTooltipPanel tooltipPanel = new ScriptedTooltipPanel("item_preview");

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
    public VisualPreviewInteractionProfile interactionProfile() {
        return VisualPreviewInteractionProfile.OBJECT_INSPECTION;
    }

    @Override
    public float initialZoom() {
        return 0.72f;
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
        float maxContentWidth = Math.max(160.0f * scale,
                Math.min(360.0f * scale, context.width() * 0.31f));
        List<ScriptedTooltipPanel.Line> lines = new ArrayList<>(Math.min(18, tip.lines().size()));
        int sourceLimit = Math.min(18, tip.lines().size());
        for (int i = 0; i < sourceLimit; i++) {
            ChatHoverUtil.ColoredLine source = tip.lines().get(i);
            lines.add(new ScriptedTooltipPanel.Line(
                    source.text() != null ? source.text() : "",
                    source.color()
            ));
        }

        TextRenderer fallback = TextRenderer.get();
        ScriptedTooltipPanel.Prepared prepared = tooltipPanel.prepare(
                context.minecraft(),
                fallback,
                lines,
                scale,
                maxContentWidth,
                0.0f,
                0.0f,
                1.0f,
                ScriptedTooltipPanel.Style.DEFAULT,
                ScriptedTooltipPanel.Context.ITEM
        );
        if (prepared == null) return;

        tooltipPanel.render(
                context.minecraft(),
                prepared,
                renderer,
                fallback,
                null,
                context.tickDelta(),
                18.0f * scale,
                47.0f * scale,
                UiProjectionMode.CURRENT
        );
    }

    public ItemStack stack() {
        return stack.copy();
    }
}

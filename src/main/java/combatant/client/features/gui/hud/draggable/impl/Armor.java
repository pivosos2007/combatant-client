/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.hud.draggable.impl;

import combatant.client.config.values.*;
import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import combatant.client.features.gui.hud.HudElementInfo;
import combatant.client.features.gui.hud.HudGlobalConfig;
import combatant.client.features.gui.hud.HudRenderUtil;
import combatant.client.features.gui.hud.draggable.DraggableHudElement;
import combatant.client.features.gui.hud.draggable.DraggableHudElementRegistry;
import combatant.client.render.engine.math.HudScale;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.text.TextRenderer;

//todo Description
@HudElementInfo(
        id = "armor",
        displayName = "Armor",
        enabledByDefault = false,
        order = 210
)
public final class Armor extends DraggableHudElement {

    {
        defaultLayout(1616.315f, 1003.6977f);
    }


    private static final int SLOTS = 4;
    private static final float DEFAULT_SLOT_SIZE = 18f;
    private static final float DEFAULT_SLOT_GAP = 2f;
    private static final float PANEL_RADIUS = 4.0f;
    private static final float GLASS_BLUR_STRENGTH = 1.0f;
    private static final float GLASS_ALPHA = 1.0f;
    private static final float DEFAULT_MARGIN = 16f;
    private static final float DIVIDER_THICKNESS = 0.5f;

    private final Minecraft mc = Minecraft.getInstance();
    private final HudGlobalConfig hud = HudGlobalConfig.get();
    private final NumberValue<Double> scaleValue = num("armor_scale", 3.68, 0.5, 5.0);
    private final EnumValue<LayoutMode> layoutMode =
            enumSetting("armor_layout", LayoutMode.HORIZONTAL, LayoutMode.VERTICAL, LayoutMode.HORIZONTAL);
    private final BooleanValue reverse = bool("armor_reverse", false);
    private final EnumValue<StateMode> stateMode =
            enumSetting("armor_state_mode", StateMode.TEXT, StateMode.BAR, StateMode.TEXT);
    private final NumberValue<Integer> stateBarThreshold =
            visibleWhen(num("armor_state_bar_threshold", 70, 0, 100), () -> stateMode.get() == StateMode.BAR);
    private final NumberValue<Integer> stateThreshold =
            visibleWhen(num("armor_state_threshold", 70, 0, 100), () -> stateMode.get() == StateMode.TEXT);
    private final NumberValue<Integer> stateColorThreshold =
            visibleWhen(num("armor_state_color_threshold", 70, 0, 100), () -> stateMode.get() == StateMode.TEXT);
    private float baseX;
    private float baseY;
    private float slotSize;
    private float slotGap;
    private float slotRadius;
    private float itemScale;
    private boolean layoutReady;
    private ItemStack[] cachedStacks;
    private float renderX;
    private float renderY;

    private static boolean hasAnyStacks(ItemStack[] stacks) {
        if (stacks == null) return false;
        for (ItemStack stack : stacks) {
            if (stack != null && !stack.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void applyDefaultPosition(int screenW, int screenH) {
        float scale = HudScale.scale(screenW, screenH) * scaleValue.get().floatValue();
        float size = DEFAULT_SLOT_SIZE * scale;
        float gap = DEFAULT_SLOT_GAP * scale;
        boolean horizontal = isHorizontal();
        float w = horizontal ? SLOTS * size + (SLOTS - 1) * gap : size;
        float h = horizontal ? size : SLOTS * size + (SLOTS - 1) * gap;
        if (horizontal) {
            this.x = screenW * 0.5f - w * 0.5f;
            this.y = screenH - h - DEFAULT_MARGIN * scale;
        } else {
            this.x = screenW - w - DEFAULT_MARGIN * scale;
            this.y = screenH * 0.5f - h * 0.5f;
        }
    }

    @Override
    public boolean usesEngineRenderer() {
        return true;
    }

    @Override
    public void renderEngine(Renderer2D renderer,
                             TextRenderer textRenderer,
                             GuiGraphicsExtractor ctx,
                             float tickDelta,
                             int screenW,
                             int screenH) {
        layoutReady = false;
        boolean preview = DraggableHudElementRegistry.isForceVisible();
        float scale = HudScale.scale(screenW, screenH) * scaleValue.get().floatValue();
        boolean horizontal = isHorizontal();

        slotSize = DEFAULT_SLOT_SIZE * scale;
        slotGap = DEFAULT_SLOT_GAP * scale;
        slotRadius = PANEL_RADIUS * scale;
        itemScale = scale;

        width = horizontal ? SLOTS * slotSize + (SLOTS - 1) * slotGap : slotSize;
        height = horizontal ? slotSize : SLOTS * slotSize + (SLOTS - 1) * slotGap;

        if (mc == null || (mc.player == null && !preview)) {
            width = 0f;
            height = 0f;
            return;
        }
        if (!preview && !isEnabled()) {
            width = 0f;
            height = 0f;
            return;
        }

        ItemStack[] stacks = (mc != null && mc.player != null) ? getArmorStacks() : new ItemStack[0];
        if (preview && !hasAnyStacks(stacks)) {
            stacks = previewStacks();
        }
        cachedStacks = stacks;
        if (!hasAnyStacks(cachedStacks) && !shouldShowWhenEmpty() && !preview) {
            width = 0f;
            height = 0f;
            return;
        }

        boolean reverse = x > screenW * 0.5f;
        renderX = reverse ? (x - width) : x;
        renderY = y;
        baseX = renderX;
        baseY = renderY;

        float glassScale = PANEL_RADIUS <= 0.0f ? 1.0f : slotRadius / PANEL_RADIUS;
        HudRenderUtil.drawLiquidGlass(
                baseX,
                baseY,
                width,
                height,
                slotRadius,
                glassScale,
                true,
                GLASS_BLUR_STRENGTH,
                GLASS_ALPHA
        );
        renderer.roundedRectCorners(
                baseX,
                baseY,
                width,
                height,
                slotRadius,
                slotRadius,
                slotRadius,
                slotRadius,
                1.0f,
                HudRenderUtil.glassSmallBackground(GLASS_ALPHA)
        );
        drawSlotDividers(renderer, horizontal, scale);

        layoutReady = true;
    }

    @Override
    public void renderEngineForeground(Renderer2D renderer,
                                       TextRenderer textRenderer,
                                       GuiGraphicsExtractor ctx,
                                       float tickDelta,
                                       int screenW,
                                       int screenH) {
        if (!layoutReady || mc == null || mc.player == null || ctx == null) return;

        ItemStack[] stacks = cachedStacks != null ? cachedStacks : getArmorStacks();
        float iconSize = 16f * itemScale;
        float offset = (slotSize - iconSize) * 0.5f;
        boolean horizontal = isHorizontal();
        boolean reverseOrder = isReverseOrder();
        StateMode mode = stateMode.get();
        int overlayFlags = mode == StateMode.TEXT
                ? Renderer2D.ITEM_OVERLAY_DURABILITY_TEXT
                : Renderer2D.ITEM_OVERLAY_DURABILITY;
        int durabilityThreshold = mode == StateMode.TEXT ? stateThreshold.get() : stateBarThreshold.get();
        int durabilityColorThreshold = mode == StateMode.TEXT ? stateColorThreshold.get() : stateBarThreshold.get();

        int seed = 0;
        for (int i = 0; i < stacks.length; i++) {
            int idx = reverseOrder ? (SLOTS - 1 - i) : i;
            ItemStack stack = stacks[idx];
            if (stack == null || stack.isEmpty()) continue;
            float sx = baseX + (horizontal ? i * (slotSize + slotGap) : 0f) + offset;
            float sy = baseY + (horizontal ? 0f : i * (slotSize + slotGap)) + offset;

            // Armor widget is laid out in UNSCALED_LOGICAL coordinates together with its slots.
            renderer.item(stack, sx, sy, itemScale, seed++, overlayFlags, null,
                    durabilityThreshold, durabilityColorThreshold);
        }
    }

    private ItemStack[] getArmorStacks() {
        return new ItemStack[]{
                mc.player.getItemBySlot(EquipmentSlot.HEAD),
                mc.player.getItemBySlot(EquipmentSlot.CHEST),
                mc.player.getItemBySlot(EquipmentSlot.LEGS),
                mc.player.getItemBySlot(EquipmentSlot.FEET)
        };
    }

    private ItemStack[] previewStacks() {
        ItemStack head = new ItemStack(Items.DIAMOND_HELMET);
        ItemStack chest = new ItemStack(Items.DIAMOND_CHESTPLATE);
        ItemStack legs = new ItemStack(Items.DIAMOND_LEGGINGS);
        ItemStack feet = new ItemStack(Items.DIAMOND_BOOTS);
        head.setDamageValue(Math.max(1, head.getMaxDamage() / 3));
        chest.setDamageValue(Math.max(1, chest.getMaxDamage() / 2));
        legs.setDamageValue(Math.max(1, legs.getMaxDamage() / 4));
        feet.setDamageValue(Math.max(1, feet.getMaxDamage() / 5));
        return new ItemStack[]{head, chest, legs, feet};
    }

    private void drawSlotDividers(Renderer2D renderer, boolean horizontal, float scale) {
        if (renderer == null) return;
        float dividerThickness = Math.max(0.5f, DIVIDER_THICKNESS * scale);
        float dividerLength = Math.max(6.0f * scale, slotSize * 0.52f);
        float dividerInset = Math.max(0.0f, (slotSize - dividerLength) * 0.5f);
        int divider = HudRenderUtil.scaleAlpha(0xFFFFFFFF, 0.12f);
        int dividerShadow = HudRenderUtil.scaleAlpha(0xFF000000, 0.10f);
        for (int i = 0; i < SLOTS - 1; i++) {
            if (horizontal) {
                float lineX = baseX + slotSize + i * (slotSize + slotGap)
                        + slotGap * 0.5f - dividerThickness * 0.5f;
                float lineY = baseY + dividerInset;
                renderer.quad(lineX + dividerThickness, lineY, dividerThickness, dividerLength, dividerShadow);
                renderer.quad(lineX, lineY, dividerThickness, dividerLength, divider);
            } else {
                float lineX = baseX + dividerInset;
                float lineY = baseY + slotSize + i * (slotSize + slotGap)
                        + slotGap * 0.5f - dividerThickness * 0.5f;
                renderer.quad(lineX, lineY + dividerThickness, dividerLength, dividerThickness, dividerShadow);
                renderer.quad(lineX, lineY, dividerLength, dividerThickness, divider);
            }
        }
    }

    private boolean shouldShowWhenEmpty() {
        if (hud == null) return false;
        HudGlobalConfig.HeaderMode mode = hud.getHeaderMode();
        if (mode == HudGlobalConfig.HeaderMode.ALWAYS) return true;
        if (mode == HudGlobalConfig.HeaderMode.CHAT) {
            return mc != null && ClientScreen.current() instanceof ChatScreen;
        }
        return false;
    }

    private boolean isHorizontal() {
        return layoutMode.get() == LayoutMode.HORIZONTAL;
    }

    private boolean isReverseOrder() {
        return reverse.get();
    }

    @Override
    public boolean contains(float mx, float my) {
        return mx >= renderX && mx <= renderX + width && my >= renderY && my <= renderY + height;
    }

    private enum LayoutMode implements EnumValue.IdProvider {
        VERTICAL("Vertical"),
        HORIZONTAL("Horizontal");

        private final String id;

        LayoutMode(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }
    }

    private enum StateMode implements EnumValue.IdProvider {
        BAR("Bar"),
        TEXT("Text");

        private final String id;

        StateMode(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }
    }

}


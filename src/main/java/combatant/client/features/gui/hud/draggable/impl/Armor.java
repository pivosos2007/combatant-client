/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.hud.draggable.impl;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.features.gui.hud.HudElementInfo;
import combatant.client.features.gui.hud.HudGlobalConfig;
import combatant.client.features.gui.hud.HudRenderUtil;
import combatant.client.features.gui.hud.draggable.DraggableHudElement;
import combatant.client.features.gui.hud.draggable.DraggableHudElementRegistry;
import combatant.client.features.theme.Themes;
import combatant.client.render.engine.math.HudScale;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.LinkedHashMap;
import java.util.List;

import static combatant.client.features.theme.Theme.theme;

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
    private static final float DEFAULT_MARGIN = 16f;

    private final Minecraft mc = Minecraft.getInstance();
    private final HudGlobalConfig hud = HudGlobalConfig.get();
    private final ScriptedArmorHudPanel scriptedPanel = new ScriptedArmorHudPanel();

    private final NumberValue<Double> scaleValue = num("armor_scale", 3.68, 0.5, 5.0);
    private final NumberValue<Integer> bgAlpha = num("armor_bg_alpha", "bg_alpha", 156, 0, 255);
    private final NumberValue<Integer> themeGradientStrength =
            num("armor_theme_gradient_strength", "theme_gradient_strength", 52, 0, 100);
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

    private float renderX;
    private float renderY;

    private static boolean hasAnyStacks(ItemStack[] stacks) {
        if (stacks == null) return false;
        for (ItemStack stack : stacks) {
            if (stack != null && !stack.isEmpty()) return true;
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
        boolean preview = DraggableHudElementRegistry.isForceVisible();
        float scale = HudScale.scale(screenW, screenH) * scaleValue.get().floatValue();
        boolean horizontal = isHorizontal();
        float slotSize = DEFAULT_SLOT_SIZE * scale;
        float slotGap = DEFAULT_SLOT_GAP * scale;

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

        ItemStack[] stacks = mc.player != null ? getArmorStacks() : new ItemStack[0];
        if (preview && !hasAnyStacks(stacks)) {
            stacks = previewStacks();
        }
        if (!hasAnyStacks(stacks) && !shouldShowWhenEmpty() && !preview) {
            width = 0f;
            height = 0f;
            return;
        }

        boolean rightAnchored = x > screenW * 0.5f;
        renderX = rightAnchored ? (x - width) : x;
        renderY = y;

        StateMode currentStateMode = stateMode.get();
        int durabilityThreshold = currentStateMode == StateMode.TEXT ? stateThreshold.get() : stateBarThreshold.get();
        int durabilityColorThreshold = currentStateMode == StateMode.TEXT ? stateColorThreshold.get() : stateBarThreshold.get();

        Themes.Theme currentTheme = theme();
        int themeWindow = currentTheme != null ? currentTheme.windowBg() : 0xFF3A3C41;
        int themeSurface = currentTheme != null ? currentTheme.surface() : 0xFF4A4D53;
        int themeStrokeSoft = currentTheme != null ? currentTheme.strokeSoft() : 0xFF6A6E75;
        HudRenderUtil.ThemeGradient panelGradient = HudRenderUtil.themePanelGradient(255);
        HudRenderUtil.ThemeGradient accentGradient = HudRenderUtil.themeAccentGradient(255);

        List<LinkedHashMap<String, Object>> items = ScriptedArmorHudPanel.items(stacks, isReverseOrder());
        scriptedPanel.render(
                renderer,
                textRenderer,
                ctx,
                tickDelta,
                new ScriptedArmorHudPanel.Panel(
                        renderX,
                        renderY,
                        width,
                        height,
                        scale,
                        slotSize,
                        slotGap,
                        horizontal,
                        currentStateMode.name(),
                        durabilityThreshold,
                        durabilityColorThreshold,
                        bgAlpha.get(),
                        themeGradientStrength.get(),
                        themeWindow,
                        themeSurface,
                        themeStrokeSoft,
                        panelGradient.start(),
                        panelGradient.end(),
                        panelGradient.angleDeg(),
                        accentGradient.start(),
                        accentGradient.end(),
                        accentGradient.angleDeg(),
                        items
                )
        );
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

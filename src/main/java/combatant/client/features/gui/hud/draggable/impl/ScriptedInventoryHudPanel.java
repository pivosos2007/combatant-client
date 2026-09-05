/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.hud.draggable.impl;

import combatant.client.util.resources.asset.UiScriptAsset;
import java.util.LinkedHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import combatant.client.features.gui.hud.script.HudScriptLayouts;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.renderer.ui.runtime.core.UiRuntime;
import combatant.client.render.engine.renderer.ui.runtime.render.UiProjectionMode;
import combatant.client.render.engine.renderer.ui.runtime.render.UiRenderContext;
import combatant.client.render.engine.renderer.ui.runtime.script.CachedUiScriptRuntime;
import combatant.client.render.engine.renderer.ui.runtime.script.UiScriptModule;
import combatant.client.render.engine.renderer.ui.runtime.script.UiScriptModuleHandle;
import combatant.client.render.engine.text.TextRenderer;

import java.util.ArrayList;
import java.util.List;

@UiScriptAsset("combatant:modules/hud/draggable/inventory_panel")
final class ScriptedInventoryHudPanel {
    private final UiScriptModuleHandle moduleHandle = HudScriptLayouts.handle(ScriptedInventoryHudPanel.class);
    private final CachedUiScriptRuntime runtime = new CachedUiScriptRuntime(HudScriptLayouts.runtimeReporter());

    static List<LinkedHashMap<String, Object>> cells(List<ItemStack> stacks) {
        List<LinkedHashMap<String, Object>> out = new ArrayList<>();
        if (stacks == null) return out;
        for (int i = 0; i < stacks.size(); i++) {
            ItemStack stack = stacks.get(i);
            if (stack == null || stack.isEmpty()) continue;
            LinkedHashMap<String, Object> cell = new LinkedHashMap<>();
            cell.put("slot", i);
            cell.put("stack", stack.copy());
            cell.put("item", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            cell.put("count", stack.getCount());
            cell.put("damage", stack.getDamageValue());
            cell.put("maxDamage", stack.getMaxDamage());
            cell.put("componentsHash", stack.getComponents().hashCode());
            out.add(cell);
        }
        return out;
    }

    private static String string(Object value) {
        return value instanceof String s ? s : "";
    }

    private static int intValue(Object value) {
        return value instanceof Number n ? n.intValue() : 0;
    }

    private static int stackHash(Object value) {
        if (!(value instanceof ItemStack stack) || stack.isEmpty()) {
            return 0;
        }
        int hash = ItemStack.hashItemAndComponents(stack);
        hash = 31 * hash + stack.getCount();
        hash = 31 * hash + stack.getDamageValue();
        hash = 31 * hash + stack.getComponents().hashCode();
        return hash;
    }

    private static void putPatch(LinkedHashMap<String, LinkedHashMap<String, Object>> patches,
                                 String key,
                                 String prop,
                                 Object value) {
        LinkedHashMap<String, Object> patch = patches.get(key);
        if (patch == null) {
            patch = new LinkedHashMap<>();
            patches.put(key, patch);
        }
        patch.put(prop, value);
    }

    boolean render(Renderer2D renderer,
                   TextRenderer textRenderer,
                   GuiGraphicsExtractor ctx,
                   float tickDelta,
                   Panel panel) {
        if (renderer == null || panel == null || panel.width <= 0.0f || panel.height <= 0.0f) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getResourceManager() == null) return false;

        HudScriptLayouts.pollReloadCombo(mc);
        if (moduleHandle.consumeChanged()) {
            runtime.reset();
        }

        UiScriptModule module = ensureModule(mc);
        if (module == null) return false;

        TextRenderer fallback = textRenderer != null ? textRenderer : TextRenderer.get();
        LinkedHashMap<String, Object> props = panel.toProps();
        UiRuntime baked = runtime.bake(
                moduleHandle,
                module,
                "inventory_panel",
                panel.treeSignature(),
                panel.dataSignature(),
                panel.layoutSignature(),
                panel.width,
                panel.height,
                fallback,
                panel.x,
                panel.y,
                panel.width,
                panel.height,
                () -> props,
                panel::patches
        );
        if (baked == null) return false;
        baked.render(new UiRenderContext(renderer, fallback, ctx, tickDelta, UiProjectionMode.CURRENT));
        return true;
    }

    private UiScriptModule ensureModule(Minecraft mc) {
        if (!moduleHandle.ensureLoaded(mc.getResourceManager())) {
            HudScriptLayouts.reportLoadError(moduleHandle);
            return null;
        }
        moduleHandle.consumeChanged();
        return moduleHandle.module();
    }

    record Panel(float x,
                 float y,
                 float width,
                 float height,
                 float drawScale,
                 float baseScale,
                 float fontScale,
                 float headerIconHeight,
                 float headerTextHeight,
                 float rowTextHeight,
                 float countLabelWidth,
                 float countValueWidth,
                 long itemCount,
                 boolean blur,
                 float blurAlpha,
                 boolean strokeEnabled,
                 float strokeAlpha,
                 boolean strokeGradient,
                 int strokeStartColor,
                 int strokeEndColor,
                 int headerIconColor,
                 boolean headerIconGradient,
                 int headerIconGradientStart,
                 int headerIconGradientEnd,
                 float headerIconGradientAngle,
                 int gridDivider,
                 String layout,
                 ScriptedListHudPanel.Palette palette,
                 List<LinkedHashMap<String, Object>> items) {
        LinkedHashMap<String, Object> toProps() {
            LinkedHashMap<String, Object> out = new LinkedHashMap<>();
            out.put("id", "inventory");
            out.put("title", "Inventory");
            out.put("headerIcon", "A");
            out.put("width", width);
            out.put("height", height);
            out.put("drawScale", drawScale);
            out.put("baseScale", baseScale);
            out.put("fontScale", fontScale);
            out.put("headerIconHeight", headerIconHeight);
            out.put("headerTextHeight", headerTextHeight);
            out.put("rowTextHeight", rowTextHeight);
            out.put("countLabelWidth", countLabelWidth);
            out.put("countValueWidth", countValueWidth);
            out.put("activeCount", itemCount);
            out.put("blur", blur);
            out.put("blurAlpha", blurAlpha);
            out.put("shadowControlled", true);
            out.put("strokeEnabled", strokeEnabled);
            out.put("strokeAlpha", strokeAlpha);
            out.put("strokeGradient", strokeGradient);
            out.put("strokeStartColor", ScriptedListHudPanel.hex(strokeStartColor));
            out.put("strokeEndColor", ScriptedListHudPanel.hex(strokeEndColor));
            out.put("headerIconColor", ScriptedListHudPanel.hex(headerIconColor));
            out.put("headerIconGradient", headerIconGradient);
            out.put("headerIconGradientStart", ScriptedListHudPanel.hex(headerIconGradientStart));
            out.put("headerIconGradientEnd", ScriptedListHudPanel.hex(headerIconGradientEnd));
            out.put("headerIconGradientAngle", headerIconGradientAngle);
            out.put("gridDivider", ScriptedListHudPanel.hex(gridDivider));
            out.put("layout", layout != null ? layout : HudPanelLayoutModes.SPLIT_HEADER);
            out.put("cols", 9);
            out.put("rowsCount", 3);
            // Horizontal grid inset is owned by panel_base.js so header/body alignment
            // cannot drift independently. Only the vertical grid geometry stays local.
            out.put("gridStartY", 22.0f);
            out.put("gridStep", 13.0f);
            out.put("gridLineOffsetX", 10.25f);
            out.put("gridLineOffsetY", 10.0f);
            out.put("gridLineLength", 9.0f);
            out.put("gridLineThickness", 0.5f);
            out.put("itemRenderScale", 0.5f);
            out.put("palette", palette.toProps());
            out.put("items", items != null ? items.toArray() : new Object[0]);
            out.put("variant", variantProps());
            return out;
        }

        private LinkedHashMap<String, Object> variantProps() {
            LinkedHashMap<String, Object> out = new LinkedHashMap<>();
            out.put("bodyY", 18.4f);
            // Header icon slot, divider, title and content inset are one shared
            // horizontal contract in panel_base.js. Inventory only supplies
            // feature-specific values here.
            out.put("countLabelOffset", 21.0f);
            out.put("countValueOffset", 2.0f);
            return out;
        }

        long treeSignature() {
            long h = 0xcbf29ce484222325L;
            h = CachedUiScriptRuntime.mix(h, "inventory");
            h = CachedUiScriptRuntime.mix(h, width);
            h = CachedUiScriptRuntime.mix(h, height);
            h = CachedUiScriptRuntime.mix(h, drawScale);
            h = CachedUiScriptRuntime.mix(h, baseScale);
            h = CachedUiScriptRuntime.mix(h, fontScale);
            h = CachedUiScriptRuntime.mix(h, headerIconHeight);
            h = CachedUiScriptRuntime.mix(h, headerTextHeight);
            h = CachedUiScriptRuntime.mix(h, rowTextHeight);
            h = CachedUiScriptRuntime.mix(h, countLabelWidth);
            h = CachedUiScriptRuntime.mix(h, countValueWidth);
            h = CachedUiScriptRuntime.mix(h, blur);
            h = CachedUiScriptRuntime.mix(h, blurAlpha);
            h = CachedUiScriptRuntime.mix(h, strokeEnabled);
            h = CachedUiScriptRuntime.mix(h, strokeAlpha);
            h = CachedUiScriptRuntime.mix(h, strokeGradient);
            h = CachedUiScriptRuntime.mix(h, strokeStartColor);
            h = CachedUiScriptRuntime.mix(h, strokeEndColor);
            h = CachedUiScriptRuntime.mix(h, headerIconGradient);
            h = CachedUiScriptRuntime.mix(h, headerIconGradientAngle);
            h = CachedUiScriptRuntime.mix(h, gridDivider);
            h = CachedUiScriptRuntime.mix(h, layout);
            h = mixPalette(h);
            h = CachedUiScriptRuntime.mix(h, String.valueOf(Math.max(0L, itemCount)).length());
            h = CachedUiScriptRuntime.mix(h, (int) Math.min(Integer.MAX_VALUE, Math.max(0L, itemCount)));
            h = CachedUiScriptRuntime.mix(h, items != null ? items.size() : 0);
            if (items != null) {
                for (LinkedHashMap<String, Object> item : items) {
                    h = CachedUiScriptRuntime.mix(h, intValue(item.get("slot")));
                }
            }
            return h;
        }

        long dataSignature() {
            long h = 0xcbf29ce484222325L;
            h = CachedUiScriptRuntime.mix(h, (int) Math.min(Integer.MAX_VALUE, Math.max(0L, itemCount)));
            h = CachedUiScriptRuntime.mix(h, headerIconColor);
            h = CachedUiScriptRuntime.mix(h, headerIconGradientStart);
            h = CachedUiScriptRuntime.mix(h, headerIconGradientEnd);
            h = CachedUiScriptRuntime.mix(h, strokeEnabled);
            h = CachedUiScriptRuntime.mix(h, strokeAlpha);
            h = CachedUiScriptRuntime.mix(h, strokeGradient);
            h = CachedUiScriptRuntime.mix(h, strokeStartColor);
            h = CachedUiScriptRuntime.mix(h, strokeEndColor);
            if (items != null) {
                for (LinkedHashMap<String, Object> item : items) {
                    h = CachedUiScriptRuntime.mix(h, intValue(item.get("slot")));
                    h = CachedUiScriptRuntime.mix(h, string(item.get("item")));
                    h = CachedUiScriptRuntime.mix(h, intValue(item.get("count")));
                    h = CachedUiScriptRuntime.mix(h, intValue(item.get("damage")));
                    h = CachedUiScriptRuntime.mix(h, intValue(item.get("maxDamage")));
                    h = CachedUiScriptRuntime.mix(h, intValue(item.get("componentsHash")));
                    h = CachedUiScriptRuntime.mix(h, stackHash(item.get("stack")));
                }
            }
            return h;
        }

        long layoutSignature() {
            long h = 0xcbf29ce484222325L;
            h = CachedUiScriptRuntime.mix(h, x);
            h = CachedUiScriptRuntime.mix(h, y);
            h = CachedUiScriptRuntime.mix(h, width);
            h = CachedUiScriptRuntime.mix(h, height);
            return h;
        }

        LinkedHashMap<String, LinkedHashMap<String, Object>> patches() {
            LinkedHashMap<String, LinkedHashMap<String, Object>> patches = new LinkedHashMap<>();
            putPatch(patches, "header:count-value", "text", String.valueOf(Math.max(0L, itemCount)));
            putPatch(patches, "header:icon", "color", ScriptedListHudPanel.hex(headerIconColor));
            putPatch(patches, "header:icon", "gradientStartColor", ScriptedListHudPanel.hex(headerIconGradientStart));
            putPatch(patches, "header:icon", "gradientEndColor", ScriptedListHudPanel.hex(headerIconGradientEnd));
            if (items != null) {
                for (LinkedHashMap<String, Object> item : items) {
                    String key = "item:" + intValue(item.get("slot"));
                    putPatch(patches, key, "stack", item.get("stack"));
                    putPatch(patches, key, "item", string(item.get("item")));
                    putPatch(patches, key, "count", intValue(item.get("count")));
                    putPatch(patches, key, "damage", intValue(item.get("damage")));
                    putPatch(patches, key, "maxDamage", intValue(item.get("maxDamage")));
                    putPatch(patches, key, "componentsHash", intValue(item.get("componentsHash")));
                }
            }
            return patches;
        }

        private long mixPalette(long h) {
            h = CachedUiScriptRuntime.mix(h, palette.headerLeft());
            h = CachedUiScriptRuntime.mix(h, palette.headerRight());
            h = CachedUiScriptRuntime.mix(h, palette.bodyLeft());
            h = CachedUiScriptRuntime.mix(h, palette.bodyRight());
            h = CachedUiScriptRuntime.mix(h, palette.outline());
            h = CachedUiScriptRuntime.mix(h, palette.text());
            h = CachedUiScriptRuntime.mix(h, palette.muted());
            h = CachedUiScriptRuntime.mix(h, palette.counter());
            h = CachedUiScriptRuntime.mix(h, palette.titleText());
            h = CachedUiScriptRuntime.mix(h, palette.divider());
            h = CachedUiScriptRuntime.mix(h, palette.blurTint());
            return h;
        }
    }
}

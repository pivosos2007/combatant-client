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
import combatant.client.render.engine.renderer.ui.runtime.core.UiDynamicPatchBuffer;
import combatant.client.render.engine.renderer.ui.runtime.render.UiProjectionMode;
import combatant.client.render.engine.renderer.ui.runtime.render.UiRenderContext;
import combatant.client.render.engine.renderer.ui.runtime.script.CachedUiScriptRuntime;
import combatant.client.render.engine.renderer.ui.runtime.script.UiScriptModule;
import combatant.client.render.engine.renderer.ui.runtime.script.UiScriptModuleHandle;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.render.engine.text.TextSizing;

import java.util.ArrayList;
import java.util.List;

@UiScriptAsset("combatant:modules/hud/draggable/inventory_panel")
final class ScriptedInventoryHudPanel {
    private final UiScriptModuleHandle moduleHandle = HudScriptLayouts.handle(ScriptedInventoryHudPanel.class);
    private final CachedUiScriptRuntime runtime = new CachedUiScriptRuntime(HudScriptLayouts.runtimeReporter());
    // The JS snapshot detaches cell maps before sending work to the V8 worker.
    // The render-thread cell metadata can therefore be overwritten each frame.
    @SuppressWarnings("unchecked")
    private final LinkedHashMap<String, Object>[] reusableCells = new LinkedHashMap[27];
    private final ArrayList<LinkedHashMap<String, Object>> currentCells = new ArrayList<>(27);
    private final Object[] cachedItemTypes = new Object[27];
    private final String[] cachedItemIds = new String[27];

    // Reuse node key strings in the per-frame instruction stream.
    private static final String[] ITEM_KEYS = itemKeys();

    private static String[] itemKeys() {
        String[] keys = new String[27];
        for (int i = 0; i < keys.length; i++) keys[i] = "item:" + i;
        return keys;
    }

    List<LinkedHashMap<String, Object>> cells(List<ItemStack> stacks) {
        currentCells.clear();
        if (stacks == null) return currentCells;
        for (int i = 0, n = Math.min(stacks.size(), reusableCells.length); i < n; i++) {
            ItemStack stack = stacks.get(i);
            if (stack == null || stack.isEmpty()) {
                // Drop the previous item reference when a slot becomes empty.
                LinkedHashMap<String, Object> old = reusableCells[i];
                if (old != null) old.remove("stack");
                continue;
            }
            LinkedHashMap<String, Object> cell = reusableCells[i];
            if (cell == null) {
                cell = new LinkedHashMap<>(8);
                cell.put("slot", i);
                reusableCells[i] = cell;
            }
            cell.put("stack", stack.copy());
            Object itemType = stack.getItem();
            if (cachedItemTypes[i] != itemType) {
                cachedItemTypes[i] = itemType;
                cachedItemIds[i] = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            }
            cell.put("item", cachedItemIds[i]);
            cell.put("count", stack.getCount());
            cell.put("damage", stack.getDamageValue());
            cell.put("maxDamage", stack.getMaxDamage());
            cell.put("componentsHash", stack.getComponents().hashCode());
            currentCells.add(cell);
        }
        return currentCells;
    }

    private static String string(Object value) {
        return value instanceof String s ? s : "";
    }

    private static int intValue(Object value) {
        return value instanceof Number n ? n.intValue() : 0;
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
        float logicalWidth = panel.logical(panel.width);
        float logicalHeight = panel.logical(panel.height);
        UiRuntime baked = runtime.bakeDynamic(
                moduleHandle,
                module,
                "inventory_panel",
                panel.treeSignature(),
                logicalWidth,
                logicalHeight,
                fallback,
                0.0f,
                0.0f,
                logicalWidth,
                logicalHeight,
                panel::toProps,
                panel::writeDynamicPatches
        );
        if (baked == null) return false;
        baked.render(new UiRenderContext(renderer, fallback, ctx, tickDelta, UiProjectionMode.CURRENT)
                .at(panel.x, panel.y, panel.renderScale()));
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
                 float renderScale,
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
                 List<LinkedHashMap<String, Object>> items, String materialMode) {
        Panel {
            renderScale = Math.max(0.0001f, renderScale);
            items = items != null ? items : List.of();
            materialMode = materialMode != null ? materialMode : "";
        }

        float logical(float renderValue) {
            return Math.round(renderValue / renderScale * 1024.0f) / 1024.0f;
        }

        LinkedHashMap<String, Object> toProps() {
            LinkedHashMap<String, Object> out = new LinkedHashMap<>();
            out.put("id", "inventory");
            out.put("title", "Inventory");
            out.put("headerIcon", "A");
            out.put("width", logical(width));
            out.put("height", logical(height));
            out.put("fontSize", TextSizing.sizeForScale(logical(fontScale)));
            out.put("headerIconHeight", logical(headerIconHeight));
            out.put("headerTextHeight", logical(headerTextHeight));
            out.put("rowTextHeight", logical(rowTextHeight));
            out.put("countLabelWidth", logical(countLabelWidth));
            out.put("countValueWidth", logical(countValueWidth));
            out.put("activeCount", itemCount);
            out.put("materialMode", materialMode);
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
            h = CachedUiScriptRuntime.mix(h, materialMode);
            h = CachedUiScriptRuntime.mix(h, "inventory");
            h = CachedUiScriptRuntime.mix(h, logical(width));
            h = CachedUiScriptRuntime.mix(h, logical(height));
            h = CachedUiScriptRuntime.mix(h, TextSizing.sizeForScale(logical(fontScale)));
            h = CachedUiScriptRuntime.mix(h, logical(headerIconHeight));
            h = CachedUiScriptRuntime.mix(h, logical(headerTextHeight));
            h = CachedUiScriptRuntime.mix(h, logical(rowTextHeight));
            h = CachedUiScriptRuntime.mix(h, logical(countLabelWidth));
            h = CachedUiScriptRuntime.mix(h, logical(countValueWidth));
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
            h = CachedUiScriptRuntime.mix(h, items != null ? items.size() : 0);
            if (items != null) {
                for (LinkedHashMap<String, Object> item : items) {
                    h = CachedUiScriptRuntime.mix(h, intValue(item.get("slot")));
                }
            }
            return h;
        }

        /** Direct typed instructions; never allocate per-item patch maps. */
        void writeDynamicPatches(UiDynamicPatchBuffer patches) {
            patches.put("header:count-value", "text", String.valueOf(Math.max(0L, itemCount)));
            patches.put("header:icon", "color", ScriptedListHudPanel.hex(headerIconColor));
            patches.put("header:icon", "gradientStartColor", ScriptedListHudPanel.hex(headerIconGradientStart));
            patches.put("header:icon", "gradientEndColor", ScriptedListHudPanel.hex(headerIconGradientEnd));
            if (items == null) return;
            for (LinkedHashMap<String, Object> item : items) {
                int slot = intValue(item.get("slot"));
                if (slot < 0 || slot >= ITEM_KEYS.length) continue;
                String key = ITEM_KEYS[slot];
                // Keep ItemStack render-thread-owned; never forward it to V8.
                patches.put(key, "stack", item.get("stack"));
                patches.put(key, "item", string(item.get("item")));
                patches.putInt(key, "count", intValue(item.get("count")));
                patches.putInt(key, "damage", intValue(item.get("damage")));
                patches.putInt(key, "maxDamage", intValue(item.get("maxDamage")));
                patches.putInt(key, "componentsHash", intValue(item.get("componentsHash")));
            }
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

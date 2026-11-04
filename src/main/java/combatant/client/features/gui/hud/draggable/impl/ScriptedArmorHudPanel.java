/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.hud.draggable.impl;

import combatant.client.util.resources.asset.UiScriptAsset;
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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

@UiScriptAsset("combatant:modules/hud/draggable/armor")
final class ScriptedArmorHudPanel {
    private final UiScriptModuleHandle moduleHandle = HudScriptLayouts.handle(ScriptedArmorHudPanel.class);
    private final CachedUiScriptRuntime runtime = new CachedUiScriptRuntime(HudScriptLayouts.runtimeReporter());
    @SuppressWarnings("unchecked")
    private final LinkedHashMap<String, Object>[] reusableItems = new LinkedHashMap[4];
    private final ArrayList<LinkedHashMap<String, Object>> currentItems = new ArrayList<>(4);
    private final Object[] cachedItemTypes = new Object[4];
    private final String[] cachedItemIds = new String[4];

    private static final String[] ITEM_KEYS = {"armor:item:0", "armor:item:1", "armor:item:2", "armor:item:3"};

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
        UiRuntime baked = runtime.bakeDynamic(
                moduleHandle,
                module,
                "armor",
                panel.treeSignature(),
                panel.width,
                panel.height,
                fallback,
                panel.x,
                panel.y,
                panel.width,
                panel.height,
                panel::toProps,
                panel::writeDynamicPatches
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

    List<LinkedHashMap<String, Object>> items(ItemStack[] stacks, boolean reverse) {
        currentItems.clear();
        if (stacks == null) return currentItems;
        for (int visualIndex = 0, n = Math.min(stacks.length, reusableItems.length); visualIndex < n; visualIndex++) {
            int sourceIndex = reverse ? (stacks.length - 1 - visualIndex) : visualIndex;
            ItemStack stack = stacks[sourceIndex];
            if (stack == null || stack.isEmpty()) {
                LinkedHashMap<String, Object> old = reusableItems[visualIndex];
                if (old != null) old.remove("stack");
                continue;
            }
            LinkedHashMap<String, Object> item = reusableItems[visualIndex];
            if (item == null) {
                item = new LinkedHashMap<>(7);
                item.put("visualIndex", visualIndex);
                reusableItems[visualIndex] = item;
            }
            item.put("stack", stack.copy());
            Object itemType = stack.getItem();
            if (cachedItemTypes[visualIndex] != itemType) {
                cachedItemTypes[visualIndex] = itemType;
                cachedItemIds[visualIndex] = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            }
            item.put("item", cachedItemIds[visualIndex]);
            item.put("count", stack.getCount());
            item.put("damage", stack.getDamageValue());
            item.put("maxDamage", stack.getMaxDamage());
            item.put("componentsHash", stack.getComponents().hashCode());
            currentItems.add(item);
        }
        return currentItems;
    }

    record Panel(float x,
                 float y,
                 float width,
                 float height,
                 float scale,
                 float slotSize,
                 float slotGap,
                 boolean horizontal,
                 String stateMode,
                 int durabilityThreshold,
                 int durabilityColorThreshold,
                 int bgAlpha,
                 int themeGradientStrength,
                 int themeWindow,
                 int themeSurface,
                 int themeStrokeSoft,
                 int themePanelStart,
                 int themePanelEnd,
                 float themePanelAngle,
                 int themeAccentStart,
                 int themeAccentEnd,
                 float themeAccentAngle,
                 List<LinkedHashMap<String, Object>> items, String materialMode) {

        LinkedHashMap<String, Object> toProps() {
            LinkedHashMap<String, Object> out = new LinkedHashMap<>();
            out.put("materialMode", materialMode);
            out.put("width", width);
            out.put("height", height);
            out.put("scale", scale);
            out.put("slotSize", slotSize);
            out.put("slotGap", slotGap);
            out.put("horizontal", horizontal);
            out.put("stateMode", stateMode != null ? stateMode : "TEXT");
            out.put("durabilityThreshold", durabilityThreshold);
            out.put("durabilityColorThreshold", durabilityColorThreshold);
            out.put("bgAlpha", bgAlpha);
            out.put("themeGradientStrength", themeGradientStrength);
            out.put("themeWindow", ScriptedListHudPanel.hex(themeWindow));
            out.put("themeSurface", ScriptedListHudPanel.hex(themeSurface));
            out.put("themeStrokeSoft", ScriptedListHudPanel.hex(themeStrokeSoft));
            out.put("themePanelStart", ScriptedListHudPanel.hex(themePanelStart));
            out.put("themePanelEnd", ScriptedListHudPanel.hex(themePanelEnd));
            out.put("themePanelAngle", themePanelAngle);
            out.put("themeAccentStart", ScriptedListHudPanel.hex(themeAccentStart));
            out.put("themeAccentEnd", ScriptedListHudPanel.hex(themeAccentEnd));
            out.put("themeAccentAngle", themeAccentAngle);
            out.put("items", items != null ? items.toArray() : new Object[0]);
            return out;
        }

        long treeSignature() {
            long h = 0xcbf29ce484222325L;
            h = CachedUiScriptRuntime.mix(h, materialMode);
            h = CachedUiScriptRuntime.mix(h, width);
            h = CachedUiScriptRuntime.mix(h, height);
            h = CachedUiScriptRuntime.mix(h, scale);
            h = CachedUiScriptRuntime.mix(h, slotSize);
            h = CachedUiScriptRuntime.mix(h, slotGap);
            h = CachedUiScriptRuntime.mix(h, horizontal);
            h = CachedUiScriptRuntime.mix(h, stateMode);
            h = CachedUiScriptRuntime.mix(h, durabilityThreshold);
            h = CachedUiScriptRuntime.mix(h, durabilityColorThreshold);
            h = CachedUiScriptRuntime.mix(h, bgAlpha);
            h = CachedUiScriptRuntime.mix(h, themeGradientStrength);
            h = CachedUiScriptRuntime.mix(h, themeWindow);
            h = CachedUiScriptRuntime.mix(h, themeSurface);
            h = CachedUiScriptRuntime.mix(h, themeStrokeSoft);
            h = CachedUiScriptRuntime.mix(h, themePanelStart);
            h = CachedUiScriptRuntime.mix(h, themePanelEnd);
            h = CachedUiScriptRuntime.mix(h, themePanelAngle);
            h = CachedUiScriptRuntime.mix(h, themeAccentStart);
            h = CachedUiScriptRuntime.mix(h, themeAccentEnd);
            h = CachedUiScriptRuntime.mix(h, themeAccentAngle);
            h = CachedUiScriptRuntime.mix(h, items != null ? items.size() : 0);
            return h;
        }

        /** Direct typed instructions; actual stacks remain on the render thread. */
        void writeDynamicPatches(UiDynamicPatchBuffer patches) {
            if (items == null) return;
            for (LinkedHashMap<String, Object> item : items) {
                int visualIndex = intValue(item.get("visualIndex"));
                if (visualIndex < 0 || visualIndex >= ITEM_KEYS.length) continue;
                String key = ITEM_KEYS[visualIndex];
                patches.put(key, "stack", item.get("stack"));
                patches.put(key, "item", string(item.get("item")));
                patches.putInt(key, "count", intValue(item.get("count")));
                patches.putInt(key, "damage", intValue(item.get("damage")));
                patches.putInt(key, "maxDamage", intValue(item.get("maxDamage")));
            }
        }
    }

    private static String string(Object value) {
        return value instanceof String s ? s : "";
    }

    private static int intValue(Object value) {
        return value instanceof Number n ? n.intValue() : 0;
    }

}

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.hud.draggable.impl;

import combatant.client.features.gui.hud.script.HudScriptLayouts;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.renderer.ui.runtime.core.UiRuntime;
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

final class ScriptedArmorHudPanel {
    private static final String LAYOUT_ID = "combatant:api/hud/draggable/armor";

    private final UiScriptModuleHandle moduleHandle = HudScriptLayouts.handle(LAYOUT_ID);
    private final CachedUiScriptRuntime runtime = new CachedUiScriptRuntime(HudScriptLayouts.runtimeReporter());

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
                "armor",
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

    static List<LinkedHashMap<String, Object>> items(ItemStack[] stacks, boolean reverse) {
        List<LinkedHashMap<String, Object>> out = new ArrayList<>(4);
        if (stacks == null) return out;
        for (int visualIndex = 0; visualIndex < stacks.length; visualIndex++) {
            int sourceIndex = reverse ? (stacks.length - 1 - visualIndex) : visualIndex;
            ItemStack stack = stacks[sourceIndex];
            if (stack == null || stack.isEmpty()) continue;
            LinkedHashMap<String, Object> item = new LinkedHashMap<>();
            item.put("visualIndex", visualIndex);
            item.put("stack", stack.copy());
            item.put("item", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            item.put("count", stack.getCount());
            item.put("damage", stack.getDamageValue());
            item.put("maxDamage", stack.getMaxDamage());
            item.put("componentsHash", stack.getComponents().hashCode());
            out.add(item);
        }
        return out;
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
                 List<LinkedHashMap<String, Object>> items) {

        LinkedHashMap<String, Object> toProps() {
            LinkedHashMap<String, Object> out = new LinkedHashMap<>();
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

        long dataSignature() {
            long h = 0xcbf29ce484222325L;
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
            h = CachedUiScriptRuntime.mix(h, themeAccentStart);
            h = CachedUiScriptRuntime.mix(h, themeAccentEnd);
            if (items != null) {
                for (LinkedHashMap<String, Object> item : items) {
                    h = CachedUiScriptRuntime.mix(h, intValue(item.get("visualIndex")));
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
            if (items != null) {
                for (LinkedHashMap<String, Object> item : items) {
                    int visualIndex = intValue(item.get("visualIndex"));
                    LinkedHashMap<String, Object> patch = new LinkedHashMap<>();
                    patch.put("stack", item.get("stack"));
                    patch.put("item", string(item.get("item")));
                    patch.put("count", intValue(item.get("count")));
                    patch.put("damage", intValue(item.get("damage")));
                    patch.put("maxDamage", intValue(item.get("maxDamage")));
                    patches.put("armor:item:" + visualIndex, patch);
                }
            }
            return patches;
        }
    }

    private static String string(Object value) {
        return value instanceof String s ? s : "";
    }

    private static int intValue(Object value) {
        return value instanceof Number n ? n.intValue() : 0;
    }

    private static int stackHash(Object value) {
        if (!(value instanceof ItemStack stack) || stack.isEmpty()) return 0;
        int hash = ItemStack.hashItemAndComponents(stack);
        hash = 31 * hash + stack.getCount();
        hash = 31 * hash + stack.getDamageValue();
        hash = 31 * hash + stack.getComponents().hashCode();
        return hash;
    }
}

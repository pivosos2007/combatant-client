/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.layout.screen.modules;

import combatant.client.features.gui.clickgui.ClickGuiRenderer;
import combatant.client.features.gui.clickgui.settings.Setting;
import combatant.client.features.gui.clickgui.settings.SettingErrorView;
import combatant.client.runtime.error.ErrorHandler;
import combatant.client.features.gui.clickgui.settings.SettingRenderContext;
import combatant.client.features.gui.clickgui.settings.SettingRenderSurface;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.features.module.ModuleSubcategoryDefinition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class ModulesMenuPanel {
    final ModulesMenuCategory category;
    final int index;
    final List<ModuleHit> hits = new ArrayList<>();
    final List<SettingHit> settingHits = new ArrayList<>();
    final List<SubcategoryHit> subcategoryHits = new ArrayList<>();
    final Map<String, Float> enabledAnim = new HashMap<>();
    final Map<String, Float> subcategoryHoverAnim = new HashMap<>();
    final Map<String, Float> hoverAnim = new HashMap<>();
    final List<Setting> selectedSettings = new ArrayList<>();

    float x;
    float y;
    float w;
    float h;

    float modulesScroll;
    float modulesSmoothScroll;
    float maxModulesScroll;
    float modulesScrollbarX, modulesScrollbarY, modulesScrollbarW, modulesScrollbarH;
    float modulesScrollbarHandleY, modulesScrollbarHandleH;
    boolean modulesScrollbarDragging;
    float modulesScrollbarDragOffset;

    float settingsScroll;
    float settingsSmoothScroll;
    float maxSettingsScroll;
    float settingsScrollbarX, settingsScrollbarY, settingsScrollbarW, settingsScrollbarH;
    float settingsScrollbarHandleY, settingsScrollbarHandleH;
    boolean settingsScrollbarDragging;
    float settingsScrollbarDragOffset;
    float previewX, previewY, previewW, previewH;

    float swap;
    float anim;

    ModuleSubcategoryDefinition selectedSubcategory;
    float subcategoryScroll;
    float subcategorySmoothScroll;
    float maxSubcategoryScroll;
    float subcategoryViewportX, subcategoryViewportY, subcategoryViewportW, subcategoryViewportH;
    float subcategoryIndicatorX;
    float subcategoryIndicatorW;
    float subcategoryIndicatorStartX;
    float subcategoryIndicatorStartW;
    float subcategoryIndicatorTargetX;
    float subcategoryIndicatorTargetW;
    float subcategoryIndicatorProgress = 1.0f;
    boolean subcategoryIndicatorReady;
    float subcategoryLeftHintAnim;
    float subcategoryRightHintAnim;
    float subcategoryContentAnim = 1.0f;
    int subcategoryDirection = 1;

    String selected;
    String selectedTitle;
    String bindingId;

    ModulesMenuPanel(ModulesMenuCategory category, int index) {
        this.category = category;
        this.index = index;
        this.selectedSubcategory = defaultSubcategory();
    }

    void reset() {
        selected = null;
        selectedTitle = null;
        selectedSettings.clear();
        settingsScroll = 0.0f;
        settingsSmoothScroll = 0.0f;
        modulesScroll = 0.0f;
        modulesSmoothScroll = 0.0f;
        subcategoryViewportX = subcategoryViewportY = subcategoryViewportW = subcategoryViewportH = 0.0f;
        swap = 0.0f;
        hits.clear();
        settingHits.clear();
        subcategoryHits.clear();
        subcategoryIndicatorReady = false;
        subcategoryIndicatorProgress = 1.0f;
        subcategoryLeftHintAnim = 0.0f;
        subcategoryRightHintAnim = 0.0f;
        subcategoryContentAnim = 1.0f;
        subcategoryDirection = 1;
        previewX = previewY = previewW = previewH = 0.0f;
    }

    void update() {
        float dt = AnimationUtility.deltaTime();
        float target = selected == null ? 0.0f : 1.0f;

        swap = AnimationUtility.approach(swap, target, dt, 7.2f);
        swap = AnimationUtility.snap(swap, target, 0.0015f);

        subcategoryContentAnim = AnimationUtility.approach(subcategoryContentAnim, 1.0f, dt, 8.4f);
        subcategoryContentAnim = AnimationUtility.snap(subcategoryContentAnim, 1.0f, 0.0008f);

        subcategoryScroll = AnimationUtility.clamp(subcategoryScroll, 0.0f, maxSubcategoryScroll);
        subcategorySmoothScroll = AnimationUtility.approach(
                subcategorySmoothScroll, subcategoryScroll, dt, 14.0f);
        subcategorySmoothScroll = AnimationUtility.snap(subcategorySmoothScroll, subcategoryScroll, 0.02f);
    }

    ModuleSubcategoryDefinition activeSubcategory() {
        List<ModuleSubcategoryDefinition> available = category.subcategories();
        if (available.isEmpty()) {
            selectedSubcategory = null;
            return null;
        }
        if (selectedSubcategory != null) {
            for (ModuleSubcategoryDefinition candidate : available) {
                if (candidate.key().equals(selectedSubcategory.key())) {
                    selectedSubcategory = candidate;
                    return candidate;
                }
            }
        }
        selectedSubcategory = available.get(0);
        return selectedSubcategory;
    }

    void animateSubcategoryIndicator(float targetX, float targetW) {
        if (!Float.isFinite(targetX) || !Float.isFinite(targetW) || targetW <= 0.0f) return;
        if (!subcategoryIndicatorReady) {
            subcategoryIndicatorX = targetX;
            subcategoryIndicatorW = targetW;
            subcategoryIndicatorStartX = targetX;
            subcategoryIndicatorStartW = targetW;
            subcategoryIndicatorTargetX = targetX;
            subcategoryIndicatorTargetW = targetW;
            subcategoryIndicatorProgress = 1.0f;
            subcategoryIndicatorReady = true;
            return;
        }

        boolean targetChanged = Math.abs(targetX - subcategoryIndicatorTargetX) > 0.05f
                || Math.abs(targetW - subcategoryIndicatorTargetW) > 0.05f;
        if (targetChanged) {
            subcategoryIndicatorStartX = subcategoryIndicatorX;
            subcategoryIndicatorStartW = subcategoryIndicatorW;
            subcategoryIndicatorTargetX = targetX;
            subcategoryIndicatorTargetW = targetW;
            subcategoryIndicatorProgress = 0.0f;
        }

        if (subcategoryIndicatorProgress < 1.0f) {
            float dt = AnimationUtility.deltaTime();
            subcategoryIndicatorProgress = Math.min(1.0f, subcategoryIndicatorProgress + dt / 0.15f);
            float eased = AnimationUtility.easeOutCubic(subcategoryIndicatorProgress);
            subcategoryIndicatorX = AnimationUtility.lerp(subcategoryIndicatorStartX, subcategoryIndicatorTargetX, eased);
            subcategoryIndicatorW = AnimationUtility.lerp(subcategoryIndicatorStartW, subcategoryIndicatorTargetW, eased);
        } else {
            subcategoryIndicatorX = subcategoryIndicatorTargetX;
            subcategoryIndicatorW = subcategoryIndicatorTargetW;
        }
    }

    void animateSubcategoryScrollHints(float leftTarget, float rightTarget) {
        float dt = AnimationUtility.deltaTime();
        subcategoryLeftHintAnim = AnimationUtility.approach(
                subcategoryLeftHintAnim, AnimationUtility.clamp01(leftTarget), dt, 12.5f);
        subcategoryRightHintAnim = AnimationUtility.approach(
                subcategoryRightHintAnim, AnimationUtility.clamp01(rightTarget), dt, 12.5f);
        subcategoryLeftHintAnim = AnimationUtility.snap(
                subcategoryLeftHintAnim, AnimationUtility.clamp01(leftTarget), 0.002f);
        subcategoryRightHintAnim = AnimationUtility.snap(
                subcategoryRightHintAnim, AnimationUtility.clamp01(rightTarget), 0.002f);
    }

    float subcategoryHoverAnim(ModuleSubcategoryDefinition subcategory, boolean hover) {
        if (subcategory == null) return 0.0f;
        String key = subcategory.key();
        Float prev = subcategoryHoverAnim.get(key);
        float next = AnimationUtility.approach(prev == null ? 0.0f : prev, hover ? 1.0f : 0.0f, 0.20f);
        subcategoryHoverAnim.put(key, next);
        return next;
    }

    private ModuleSubcategoryDefinition defaultSubcategory() {
        List<ModuleSubcategoryDefinition> subcategories = category.subcategories();
        return subcategories.isEmpty() ? null : subcategories.get(0);
    }

    private void selectSubcategory(ModuleSubcategoryDefinition subcategory) {
        if (subcategory == null || subcategory.category() != category.moduleCategory()) return;
        List<ModuleSubcategoryDefinition> available = category.subcategories();
        int nextIndex = -1;
        for (int i = 0; i < available.size(); i++) {
            if (available.get(i).key().equals(subcategory.key())) {
                nextIndex = i;
                subcategory = available.get(i);
                break;
            }
        }
        if (nextIndex < 0) return;

        ModuleSubcategoryDefinition previous = activeSubcategory();
        if (previous != null && previous.key().equals(subcategory.key())) return;

        int previousIndex = previous == null ? 0 : available.indexOf(previous);
        subcategoryDirection = nextIndex >= previousIndex ? 1 : -1;
        selectedSubcategory = subcategory;
        subcategoryContentAnim = 0.08f;
        modulesScroll = 0.0f;
        modulesSmoothScroll = 0.0f;
        maxModulesScroll = 0.0f;
        modulesScrollbarX = modulesScrollbarY = modulesScrollbarW = modulesScrollbarH = 0.0f;
    }

    float enabledAnim(String id, boolean enabled) {
        Float prev = enabledAnim.get(id);
        float next = AnimationUtility.approach(prev == null ? (enabled ? 1.0f : 0.0f) : prev, enabled ? 1.0f : 0.0f, 0.18f);
        enabledAnim.put(id, next);
        return next;
    }

    float enabledAnimValue(String id) {
        Float value = enabledAnim.get(id);
        return value != null ? value : 0.0f;
    }

    float hoverAnim(String id, boolean hover) {
        Float prev = hoverAnim.get(id);
        float next = AnimationUtility.approach(prev == null ? 0.0f : prev, hover ? 1.0f : 0.0f, 0.18f);
        hoverAnim.put(id, next);
        return next;
    }

    float hoverAnimValue(String id) {
        Float value = hoverAnim.get(id);
        return value != null ? value : 0.0f;
    }

    boolean mousePressed(float mx, float my, int button) {
        if (selected != null && swap > 0.2f) {
            float backY = y + 28.0f * ModulesMenuScreen.computePortScale();
            float backH = 20.0f * ModulesMenuScreen.computePortScale();

            if (button == combatant.client.input.InputKeys.MOUSE_RIGHT && ModulesMenuScreen.inside(mx, my, x, backY, w, backH)) {
                closeSettings();
                return true;
            }

            if (button == combatant.client.input.InputKeys.MOUSE_LEFT
                    && previewW > 0.0f
                    && ModulesMenuScreen.inside(mx, my, previewX, previewY, previewW, previewH)) {
                return ModulesMenuResolver.openPreview(selected);
            }

            try (SettingRenderContext.Scope ignored = SettingRenderContext.push(SettingRenderSurface.MODULES, ModulesMenuScreen.computePortScale())) {
                SettingHit target = null;
                for (SettingHit hit : settingHits) {
                    if (ModulesMenuScreen.inside(mx, my, hit.x, hit.y, hit.w, hit.h)) {
                        target = hit;
                        break;
                    }
                }
                for (Setting setting : selectedSettings) {
                    if (target != null && target.setting == setting) continue;
                    setting.mouseClickedOutsideSafely(mx, my, button);
                }
                if (target != null) {
                    target.setting.mouseClickedSafely(mx, my, button, target.x, target.y, target.w);
                    return true;
                }
            }

            if (button == combatant.client.input.InputKeys.MOUSE_LEFT && settingsScrollbarHit(mx, my)) {
                settingsScrollbarDragging = true;
                settingsScrollbarDragOffset = my - settingsScrollbarHandleY;
                updateSettingsScrollbarDrag(my);
                return true;
            }

            return true;
        }

        if (button == combatant.client.input.InputKeys.MOUSE_LEFT
                && ModulesMenuScreen.inside(mx, my, subcategoryViewportX, subcategoryViewportY,
                subcategoryViewportW, subcategoryViewportH)) {
            for (SubcategoryHit hit : subcategoryHits) {
                if (!ModulesMenuScreen.inside(mx, my, hit.x, hit.y, hit.w, hit.h)) continue;
                selectSubcategory(hit.subcategory);
                return true;
            }
        }

        if (button == combatant.client.input.InputKeys.MOUSE_LEFT && modulesScrollbarHit(mx, my)) {
            modulesScrollbarDragging = true;
            modulesScrollbarDragOffset = my - modulesScrollbarHandleY;
            updateModulesScrollbarDrag(my);
            return true;
        }

        for (ModuleHit hit : hits) {
            if (!ModulesMenuScreen.inside(mx, my, hit.x, hit.y, hit.w, hit.h)) continue;

            if (ModulesMenuScreen.isModuleListEditHeld()
                    && hit.toggleable
                    && (button == combatant.client.input.InputKeys.MOUSE_LEFT || button == combatant.client.input.InputKeys.MOUSE_RIGHT)) {
                ModulesMenuResolver.toggleModuleListVisibility(hit.id);
                return true;
            }

            if (button == combatant.client.input.InputKeys.MOUSE_LEFT && hit.toggleable) {
                ModulesMenuResolver.toggleEntry(hit.id);
                return true;
            }

            if (button == combatant.client.input.InputKeys.MOUSE_MIDDLE) {
                bindingId = hit.id;
                ModulesMenuResolver.beginBind(hit.id);
                return true;
            }

            if (button == combatant.client.input.InputKeys.MOUSE_RIGHT) {
                ModulesMenuResolver.ResolvedSettings resolved = ModulesMenuResolver.resolveSettings(hit.id);
                if (resolved == null) return true;

                selected = resolved.getId();
                selectedTitle = resolved.title();
                selectedSettings.clear();
                selectedSettings.addAll(resolved.settings());
                settingsScroll = 0.0f;
                settingsSmoothScroll = 0.0f;
                return true;
            }

            return true;
        }

        return true;
    }

    void mouseReleased(float mx, float my, int button) {
        if (button == combatant.client.input.InputKeys.MOUSE_LEFT) {
            modulesScrollbarDragging = false;
            settingsScrollbarDragging = false;
        }
        try (SettingRenderContext.Scope ignored = SettingRenderContext.push(SettingRenderSurface.MODULES, ModulesMenuScreen.computePortScale())) {
            for (Setting setting : selectedSettings) {
                setting.mouseReleasedSafely(mx, my, button);
            }
        }
    }

    void scroll(float mx, float my, double amount) {
        float delta = (float) (-amount * 20.0f * ModulesMenuScreen.computePortScale());

        if (selected == null && maxSubcategoryScroll > 0.5f
                && ModulesMenuScreen.inside(mx, my, subcategoryViewportX, subcategoryViewportY,
                subcategoryViewportW, subcategoryViewportH)) {
            subcategoryScroll = AnimationUtility.clamp(
                    subcategoryScroll + delta, 0.0f, maxSubcategoryScroll);
            return;
        }

        if (selected != null) {
            try (SettingRenderContext.Scope ignored = SettingRenderContext.push(SettingRenderSurface.MODULES, ModulesMenuScreen.computePortScale())) {
                for (SettingHit hit : settingHits) {
                    if (!ModulesMenuScreen.inside(mx, my, hit.x, hit.y, hit.w, hit.h)) continue;
                    if (hit.setting.mouseScrolledSafely(mx, my, amount)) return;
                    break;
                }
            }
            settingsScroll = AnimationUtility.clamp(settingsScroll + delta, 0.0f, maxSettingsScroll);
        } else {
            modulesScroll = AnimationUtility.clamp(modulesScroll + delta, 0.0f, maxModulesScroll);
        }
    }

    boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (selected == null) {
            if (keyCode == combatant.client.input.InputKeys.UP) {
                modulesScroll = AnimationUtility.clamp(modulesScroll - 20.0f * ModulesMenuScreen.computePortScale(), 0.0f, maxModulesScroll);
                return true;
            }

            if (keyCode == combatant.client.input.InputKeys.DOWN) {
                modulesScroll = AnimationUtility.clamp(modulesScroll + 20.0f * ModulesMenuScreen.computePortScale(), 0.0f, maxModulesScroll);
                return true;
            }

            if (bindingId != null && !ClickGuiRenderer.waitingForKey) {
                bindingId = null;
            }

            return false;
        }

        try (SettingRenderContext.Scope ignored = SettingRenderContext.push(SettingRenderSurface.MODULES, ModulesMenuScreen.computePortScale())) {
            for (Setting setting : selectedSettings) {
                if (setting.keyPressedSafely(keyCode, scanCode, modifiers)) return true;
            }
        }

        if (keyCode == combatant.client.input.InputKeys.ESCAPE) {
            closeSettings();
            return true;
        }

        if (keyCode == combatant.client.input.InputKeys.UP) {
            settingsScroll = AnimationUtility.clamp(settingsScroll - 20.0f * ModulesMenuScreen.computePortScale(), 0.0f, maxSettingsScroll);
            return true;
        }

        if (keyCode == combatant.client.input.InputKeys.DOWN) {
            settingsScroll = AnimationUtility.clamp(settingsScroll + 20.0f * ModulesMenuScreen.computePortScale(), 0.0f, maxSettingsScroll);
            return true;
        }

        return false;
    }

    boolean charTyped(char chr, int modifiers) {
        if (selected == null) return false;
        try (SettingRenderContext.Scope ignored = SettingRenderContext.push(SettingRenderSurface.MODULES, ModulesMenuScreen.computePortScale())) {
            for (Setting setting : selectedSettings) {
                if (setting.charTypedSafely(chr, modifiers)) return true;
            }
        }
        return false;
    }


    void updateScrollbarDrag(float mx, float my) {
        if (modulesScrollbarDragging) updateModulesScrollbarDrag(my);
        if (settingsScrollbarDragging) updateSettingsScrollbarDrag(my);
    }

    boolean isDraggingScrollbar() {
        return modulesScrollbarDragging || settingsScrollbarDragging;
    }

    private boolean modulesScrollbarHit(float mx, float my) {
        return maxModulesScroll > 0.5f && ModulesMenuScreen.inside(mx, my, modulesScrollbarX - 3f * ModulesMenuScreen.computePortScale(), modulesScrollbarY, modulesScrollbarW + 6f * ModulesMenuScreen.computePortScale(), modulesScrollbarH);
    }

    private boolean settingsScrollbarHit(float mx, float my) {
        return maxSettingsScroll > 0.5f && ModulesMenuScreen.inside(mx, my, settingsScrollbarX - 3f * ModulesMenuScreen.computePortScale(), settingsScrollbarY, settingsScrollbarW + 6f * ModulesMenuScreen.computePortScale(), settingsScrollbarH);
    }

    private void updateModulesScrollbarDrag(float my) {
        float track = Math.max(1f, modulesScrollbarH - modulesScrollbarHandleH);
        float handleY = AnimationUtility.clamp(my - modulesScrollbarDragOffset, modulesScrollbarY, modulesScrollbarY + track);
        float t = (handleY - modulesScrollbarY) / track;
        modulesScroll = AnimationUtility.clamp(t * maxModulesScroll, 0.0f, maxModulesScroll);
    }

    private void updateSettingsScrollbarDrag(float my) {
        float track = Math.max(1f, settingsScrollbarH - settingsScrollbarHandleH);
        float handleY = AnimationUtility.clamp(my - settingsScrollbarDragOffset, settingsScrollbarY, settingsScrollbarY + track);
        float t = (handleY - settingsScrollbarY) / track;
        settingsScroll = AnimationUtility.clamp(t * maxSettingsScroll, 0.0f, maxSettingsScroll);
    }

    private void closeSettings() {
        selected = null;
        selectedSettings.clear();
        settingsScroll = 0.0f;
        settingsSmoothScroll = 0.0f;
    }

    record SubcategoryHit(ModuleSubcategoryDefinition subcategory, float x, float y, float w, float h) {
    }

    record ModuleHit(String id, float x, float y, float w, float h, boolean hasSettings, boolean toggleable) {
    }

    record SettingHit(Setting setting, float x, float y, float w, float h) {
    }
}

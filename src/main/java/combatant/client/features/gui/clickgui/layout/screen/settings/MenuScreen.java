/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.layout.screen.settings;


import combatant.client.features.theme.Theme;
import combatant.client.config.MainConfig;
import combatant.client.render.engine.renderer.RenderWarpStack;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.svg.SvgRenderOptions;
import org.lwjgl.glfw.GLFW;
import combatant.client.features.gui.clickgui.ClickGuiRenderer;
import combatant.client.features.gui.clickgui.ClickGuiSearch;
import combatant.client.features.gui.clickgui.layout.screen.settings.implement.config.ConfigProfilesComponent;
import combatant.client.features.gui.clickgui.layout.screen.settings.implement.main.MainSettingsComponent;
import combatant.client.features.gui.clickgui.layout.screen.settings.implement.module.ModuleComponent;
import combatant.client.features.gui.clickgui.layout.screen.settings.implement.module.SettingsPanelComponent;
import combatant.client.features.gui.clickgui.layout.screen.settings.implement.other.BackgroundComponent;
import combatant.client.features.gui.clickgui.layout.screen.settings.implement.other.CategoryContainerComponent;
import combatant.client.features.gui.clickgui.layout.screen.settings.implement.other.SearchComponent;
import combatant.client.features.gui.clickgui.layout.screen.settings.implement.relations.RelationsComponent;
import combatant.client.features.gui.clickgui.layout.screen.settings.implement.theme.ThemeComponent;
import combatant.client.features.gui.clickgui.layout.screen.settings.implement.theme.ThemeEditorPreviewComponent;
import combatant.client.features.gui.clickgui.layout.screen.settings.render.LayoutRender2D;
import combatant.client.features.gui.clickgui.layout.screen.settings.render.SettingsCardTransition;
import combatant.client.features.gui.clickgui.util.ClickGuiHintOverlay;
import combatant.client.features.gui.clickgui.util.ClickGuiI18n;
import combatant.client.features.theme.EditableClickGuiTheme;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.render.engine.text.FontInfo;
import combatant.client.render.engine.text.Fonts;
import combatant.client.render.engine.text.TextRenderer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class MenuScreen {
    private static final float INPUT_READY_PROGRESS = 0.72f;
    private static final float S = 2f;
    private static final float MENU_W = 400f;
    private static final float MENU_H = 250f;
    private static final float CATEGORY_TRANSITION_DURATION_MS = 900f;
    private final BackgroundComponent backgroundComponent = new BackgroundComponent();
    private final CategoryContainerComponent categoryContainer = new CategoryContainerComponent();
    private final SearchComponent searchComponent = new SearchComponent();
    private final ModuleComponent moduleComponent = new ModuleComponent();
    private final ThemeComponent themeComponent = new ThemeComponent();
    private final ThemeEditorPreviewComponent themeEditorPreview = new ThemeEditorPreviewComponent();
    private final MainSettingsComponent mainSettingsComponent = new MainSettingsComponent();
    private final RelationsComponent relationsComponent = new RelationsComponent();
    private final ConfigProfilesComponent configProfilesComponent = new ConfigProfilesComponent();
    private final SettingsPanelComponent settingsPanel = new SettingsPanelComponent();
    private Category category = Category.HUD;
    private float menuX;
    private float menuY;
    private float menuW;
    private float menuH;
    private float shellX;
    private float shellY;
    private float shellW;
    private float shellH;
    private float categoryX;
    private float categoryY;
    private float searchX;
    private float searchY;
    private float menuMaskAnim = 0f;
    private float screenAnim = 0f;
    private float prismProgress = 1f;
    private float categoryTransition = 1f;
    private boolean openTarget = false;
    private EditableClickGuiTheme activeThemeEditor;

    private static float easeOutCubic(float t) {
        t = AnimationUtility.clamp(t, 0.0f, 1.0f);
        float inv = 1.0f - t;
        return 1.0f - inv * inv * inv;
    }

    private static float easeOutQuint(float t) {
        t = AnimationUtility.clamp(t, 0.0f, 1.0f);
        float inv = 1.0f - t;
        return 1.0f - inv * inv * inv * inv * inv;
    }

    private static float smootherStep(float t) {
        t = AnimationUtility.clamp(t, 0.0f, 1.0f);
        return t * t * t * (t * (t * 6.0f - 15.0f) + 10.0f);
    }

    private static List<ModuleComponent.CardEntry> visibleEntries(List<ModuleComponent.CardEntry> src) {
        if (!ClickGuiSearch.isActive() || ClickGuiSearch.getText().isEmpty()) return src;
        List<ModuleComponent.CardEntry> out = new ArrayList<>();
        for (ModuleComponent.CardEntry e : src) {
            if (ClickGuiSearch.matches(e.title(), e.searchAliases())) out.add(e);
        }
        return out;
    }

    public void clearScreens() {
    }

    public void markDirty() {
    }

    public void open() {
        if (!openTarget) {
            prismProgress = 0f;
            categoryTransition = 0f;
        }
        openTarget = true;
    }

    public void close() {
        openTarget = false;
        settingsPanel.mouseReleased(0f, 0f, 0);
        relationsComponent.resetScroll();
        activeThemeEditor = null;
        themeEditorPreview.reset();
        if (!ClickGuiRenderer.isClosingForExit()) {
            screenAnim = 0.0f;
        }
    }

    public boolean isVisible() {
        return openTarget || screenAnim > 0.001f;
    }

    public void render(float areaX, float areaY, float areaW, float areaH, float mx, float my) {
        SettingsGuiPalette palette = SettingsGuiPalette.current();
        float dt = AnimationUtility.deltaTime();
        if (openTarget && prismProgress < 1f) {
            prismProgress = Math.min(1f, prismProgress + dt / 0.85f);
        }
        screenAnim = AnimationUtility.approach(screenAnim, openTarget ? 1.0f : 0.0f, dt, openTarget ? 8.5f : 7.2f);
        screenAnim = AnimationUtility.snap(screenAnim, openTarget ? 1.0f : 0.0f, 0.002f);
        if (screenAnim <= 0.001f && !openTarget) return;

        layout(areaX, areaY, areaW, areaH);
        boolean hudPanelActive = settingsPanel.shouldMaskMenu();
        float maskTarget = hudPanelActive ? 1f : 0f;
        menuMaskAnim = AnimationUtility.approach(menuMaskAnim, maskTarget, dt, 10f);
        menuMaskAnim = AnimationUtility.snap(menuMaskAnim, maskTarget, 0.01f);

        boolean renderMain = !hudPanelActive;
        if (activeThemeEditor != null && !settingsPanel.isActive()) {
            activeThemeEditor = null;
            themeEditorPreview.reset();
        }
        float distanceProgress = easeOutQuint(screenAnim);
        float fadeProgress = smootherStep(screenAnim);
        float previousAlpha = ClickGuiRenderer.setRenderAlphaMultiplier(fadeProgress);
        Renderer2D.COLOR.setAlpha(fadeProgress);
        try (RenderWarpStack.Scope ignored = pushLifecycleWarp(distanceProgress)) {
            if (renderMain) {
                renderPanelShadow(palette);

                backgroundComponent.render(shellX, shellY, shellW, shellH, S, prismProgress);
                renderShellChrome(palette);

                categoryContainer.render(categoryX, categoryY, mx, my, category, S);
                searchComponent.render(searchX, searchY, 80f * S, 15f * S);

                if (categoryTransition < 1f) {
                    float millisDt = AnimationUtility.deltaTime(AnimationUtility.Mode.MILLIS);
                    categoryTransition = Math.min(1f, categoryTransition + (millisDt * 1000f) / CATEGORY_TRANSITION_DURATION_MS);
                }

                try (SettingsCardTransition.SectionScope ignoredTransition = SettingsCardTransition.push(categoryTransition)) {
                    if (category == Category.THEMES) {
                        themeComponent.render(menuX, menuY, menuW, menuH, mx, my, S);
                    } else if (category == Category.MAIN_SETTINGS) {
                        mainSettingsComponent.render(menuX, menuY, menuW, menuH, mx, my, S);
                    } else if (category == Category.RELATIONS) {
                        relationsComponent.render(menuX, menuY, menuW, menuH, mx, my, S);
                    } else if (category == Category.CONFIGS) {
                        configProfilesComponent.render(menuX, menuY, menuW, menuH, mx, my, S);
                    } else {
                        List<ModuleComponent.CardEntry> entries = visibleEntries(MenuSettingsResolver.buildCards(category));
                        moduleComponent.render(menuX, menuY, menuW, menuH, entries, S);
                    }
                }
            }

            if (renderMain && menuMaskAnim > 0.01f) {
                int c0 = LayoutRender2D.alpha(palette.menuMaskLeft(), menuMaskAnim * fadeProgress);
                int c1 = LayoutRender2D.alpha(palette.menuMaskRight(), menuMaskAnim * fadeProgress);
                LayoutRender2D.roundedQuad(shellX, shellY, shellW, shellH, 8f * S, c0, c1, c1, c0);
            }

            if (activeThemeEditor != null && settingsPanel.isActive()) {
                themeEditorPreview.render(activeThemeEditor, menuX, menuY, menuW, menuH, mx, my, S);
            }

            settingsPanel.render(menuX, menuY, menuW, menuH, mx, my, S);
        } finally {
            Renderer2D.COLOR.setAlpha(1.0f);
            ClickGuiRenderer.restoreRenderAlphaMultiplier(previousAlpha);
        }

        if (renderMain && !settingsPanel.isActive()) {
            renderContextHints(areaX, areaY, areaW, areaH, fadeProgress);
        }
    }


    private void renderContextHints(float areaX, float areaY, float areaW, float areaH, float alpha) {
        MainConfig config = MainConfig.get();
        if (!config.isClickGuiHintsEnabled()) return;

        String primary;
        String secondary;
        String tertiary;
        switch (category) {
            case HUD -> {
                primary = ClickGuiI18n.tr("clickgui.hints.settings.hud.toggle", "LMB - toggle HUD element");
                secondary = ClickGuiI18n.tr("clickgui.hints.settings.hud.settings", "RMB - open element settings");
                tertiary = ClickGuiI18n.tr("clickgui.hints.settings.hud.drag", "Open settings - edit widget position");
            }
            case UI -> {
                primary = ClickGuiI18n.tr("clickgui.hints.settings.ui.toggle", "LMB - toggle UI element");
                secondary = ClickGuiI18n.tr("clickgui.hints.settings.ui.settings", "RMB - open element settings");
                tertiary = ClickGuiI18n.tr("clickgui.hints.settings.ui.search", "Ctrl+F - search cards");
            }
            case THEMES -> {
                primary = ClickGuiI18n.tr("clickgui.hints.settings.themes.select", "LMB - select theme");
                secondary = ClickGuiI18n.tr("clickgui.hints.settings.themes.edit", "Edit - open theme editor");
                tertiary = ClickGuiI18n.tr("clickgui.hints.settings.themes.scroll", "Wheel - scroll themes");
            }
            case MAIN_SETTINGS -> {
                primary = ClickGuiI18n.tr("clickgui.hints.settings.main.select", "LMB - change setting");
                secondary = ClickGuiI18n.tr("clickgui.hints.settings.main.category", "Left list - switch setting group");
                tertiary = ClickGuiI18n.tr("clickgui.hints.settings.main.scroll", "Wheel - scroll settings");
            }
            case RELATIONS -> {
                primary = ClickGuiI18n.tr("clickgui.hints.settings.relations.mode", "Top pills - choose relation list");
                secondary = ClickGuiI18n.tr("clickgui.hints.settings.relations.add", "User-plus icon - pick online player");
                tertiary = ClickGuiI18n.tr("clickgui.hints.settings.relations.search", "Search - filter names");
            }
            case CONFIGS -> {
                primary = ClickGuiI18n.tr("clickgui.hints.settings.configs.select", "LMB - select config");
                secondary = ClickGuiI18n.tr("clickgui.hints.settings.configs.rename", "Click name - rename config");
                tertiary = ClickGuiI18n.tr("clickgui.hints.settings.configs.enter", "Enter/Esc - confirm or cancel text edit");
            }
            default -> {
                primary = ClickGuiI18n.tr("clickgui.hints.settings.generic.select", "LMB - select");
                secondary = ClickGuiI18n.tr("clickgui.hints.settings.generic.scroll", "Wheel - scroll");
                tertiary = ClickGuiI18n.tr("clickgui.hints.settings.generic.search", "Ctrl+F - search");
            }
        }

        ClickGuiHintOverlay.renderBottomLeft(
                areaX,
                areaY,
                areaW,
                areaH,
                S,
                alpha,
                primary,
                secondary,
                tertiary,
                ClickGuiI18n.tr("clickgui.hints.settings.close", "Esc - close ClickGUI"),
                ClickGuiI18n.tr("clickgui.hints.settings.hide", "Alt+H - hide hints")
        );
    }

    private static boolean isHintToggle(int keyCode, int modifiers) {
        return keyCode == GLFW.GLFW_KEY_H && (modifiers & GLFW.GLFW_MOD_ALT) != 0;
    }

    public boolean mouseClicked(float mx, float my, int button) {
        if (!isInteractive()) return false;
        if (button != 0 && button != 1) return false;

        if (activeThemeEditor != null && themeEditorPreview.mouseClicked(activeThemeEditor, mx, my, button)) {
            settingsPanel.close();
            activeThemeEditor = null;
            themeEditorPreview.reset();
            return true;
        }

        if (settingsPanel.mouseClicked(mx, my, button, S)) return true;
        if (settingsPanel.blocksMenuInput()) return true;

        if (searchComponent.click(searchX, searchY, 80f * S, 15f * S, mx, my, button)) {
            return true;
        }

        Category hitCategory = categoryContainer.click(categoryX, categoryY, mx, my, S);
        if (hitCategory != null) {
            if (hitCategory != category) {
                category = hitCategory;
                categoryTransition = 0f;
                moduleComponent.resetScroll();
                themeComponent.resetScroll();
                mainSettingsComponent.resetScroll();
                relationsComponent.resetScroll();
                configProfilesComponent.resetScroll();
            }
            return true;
        }

        if (category == Category.THEMES) {
            if (themeComponent.mousePressedScrollbar(mx, my, button)) return true;
            ThemeComponent.ThemeAction action = themeComponent.click(mx, my, button);
            if (action == null) return false;
            if (action.type() == ThemeComponent.ThemeActionType.SELECT && action.themeId() != null) {
                Theme.setTheme(action.themeId());
            } else if (action.type() == ThemeComponent.ThemeActionType.ADD) {
                openThemeEditor(EditableClickGuiTheme.createFromCurrent());
            } else if (action.type() == ThemeComponent.ThemeActionType.EDIT && action.themeId() != null) {
                openThemeEditor(EditableClickGuiTheme.existing(action.themeId()));
            } else if (action.type() == ThemeComponent.ThemeActionType.DELETE && action.themeId() != null) {
                Theme.deleteCustomTheme(action.themeId());
            }
            return true;
        }

        if (category == Category.CONFIGS) {
            if (configProfilesComponent.mousePressedScrollbar(mx, my, button)) return true;
            return configProfilesComponent.click(mx, my, button);
        }

        if (category == Category.MAIN_SETTINGS) {
            if (mainSettingsComponent.mousePressedScrollbar(mx, my, button)) return true;
            return mainSettingsComponent.mouseClicked(mx, my, button);
        }

        if (category == Category.RELATIONS) {
            if (relationsComponent.mousePressedScrollbar(mx, my, button)) return true;
            return relationsComponent.click(mx, my, button);
        }

        if (moduleComponent.mousePressedScrollbar(mx, my, button)) return true;
        ModuleComponent.CardAction action = moduleComponent.click(mx, my, button);
        if (action == null) return false;
        if (action.toggleable() && (action.statusClick() || action.leftClick())) {
            toggleEntry(action.getId());
        }
        if (action.rightClick()) {
            openSettings(action.getId());
        }
        return true;
    }

    public void mouseReleased(float mx, float my, int button) {
        if (!isInteractive()) return;
        settingsPanel.mouseReleased(mx, my, button);
        themeComponent.mouseReleased(button);
        mainSettingsComponent.mouseReleased(mx, my, button);
        relationsComponent.mouseReleased(button);
        configProfilesComponent.mouseReleased(button);
        moduleComponent.mouseReleased(button);
    }

    public void mouseScrolled(float mx, float my, double delta) {
        if (!isInteractive()) return;
        if (settingsPanel.mouseScrolled(mx, my, delta)) return;
        if (settingsPanel.blocksMenuInput()) return;
        if (category == Category.THEMES) {
            themeComponent.scroll(mx, my, menuX, menuY, menuW, menuH, delta, S);
        } else if (category == Category.MAIN_SETTINGS) {
            mainSettingsComponent.scroll(mx, my, delta);
        } else if (category == Category.RELATIONS) {
            relationsComponent.scroll(mx, my, delta);
        } else if (category == Category.CONFIGS) {
            configProfilesComponent.scroll(mx, my, menuX, menuY, menuW, menuH, delta, S);
        } else {
            moduleComponent.scroll(mx, my, menuX, menuY, menuW, menuH, delta, S);
        }
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!isInteractive()) return false;

        if (settingsPanel.isActive() && settingsPanel.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }

        if (isHintToggle(keyCode, modifiers)) {
            MainConfig config = MainConfig.get();
            config.setClickGuiHintsEnabled(!config.isClickGuiHintsEnabled());
            return true;
        }

        if (category == Category.MAIN_SETTINGS && mainSettingsComponent.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }

        if (category == Category.CONFIGS && configProfilesComponent.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }

        if (category == Category.RELATIONS && relationsComponent.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }

        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;

        if (ctrl && keyCode == GLFW.GLFW_KEY_F) {
            ClickGuiSearch.setActive(true);
            return true;
        }

        if (ClickGuiSearch.isActive()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                ClickGuiSearch.deactivate();
                return true;
            }

            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                ClickGuiSearch.backspace();
                return true;
            }

            return true;
        }

        return false;
    }

    public boolean charTyped(char chr, int modifiers) {
        if (!isInteractive()) return false;
        if (category == Category.MAIN_SETTINGS && mainSettingsComponent.charTyped(chr, modifiers)) {
            return true;
        }

        if (category == Category.CONFIGS && configProfilesComponent.charTyped(chr, modifiers)) {
            return true;
        }

        if (category == Category.RELATIONS && relationsComponent.charTyped(chr, modifiers)) {
            return true;
        }

        if (ClickGuiSearch.isActive()) {
            ClickGuiSearch.append(chr);
            return true;
        }

        return settingsPanel.charTyped(chr, modifiers);
    }

    public boolean onFilesDrop(List<Path> paths) {
        if (!isInteractive() || category != Category.CONFIGS) return false;
        return configProfilesComponent.importDroppedFiles(paths);
    }

    private void layout(float areaX, float areaY, float areaW, float areaH) {
        menuW = MENU_W * S;
        menuH = MENU_H * S;
        menuX = areaX + (areaW - menuW) * 0.5f;
        menuY = areaY + (areaH - menuH) * 0.5f;

        shellX = menuX - 20f * S;
        shellY = menuY;
        shellW = menuW + 40f * S;
        shellH = menuH;

        categoryX = menuX - 20f * S;
        categoryY = menuY + 40f * S;
        searchX = menuX + 330f * S;
        searchY = menuY + 7.5f * S;
    }

    private boolean isInteractive() {
        return openTarget && screenAnim >= INPUT_READY_PROGRESS;
    }

    private RenderWarpStack.Scope pushLifecycleWarp(float progress) {
        if (progress >= 0.999f) return Renderer2D.pushWarp(null);
        return Renderer2D.pushPerspectiveWarp(
                shellX,
                shellY,
                shellW,
                shellH,
                0.0f,
                0.0f,
                0.0f,
                3.6f,
                0.80f,
                0.90f + 0.10f * progress
        );
    }

    private void renderShellChrome(SettingsGuiPalette palette) {
        float hLineY = shellY + 28f * S;
        float lineThickness = 0.5f * S;
        float hLineW = shellW - 43f * S;

        LayoutRender2D.rectQuad(
                shellX + 43f * S, hLineY, hLineW, lineThickness,
                palette.menuLineStrong(),
                palette.menuLineLow(),
                palette.menuLineStrong(),
                palette.menuLineLow()
        );

        if (category.iconToken()) {
            TextRenderer icons = Fonts.renderer("Icons", FontInfo.Type.Regular, ClickGuiRenderer.getInterRegular());
            float iconSize = 11.5f * S;
            float textSize = 11.5f * S;
            float baseX = shellX + 50f * S;
            float baseY = shellY + 13.5f * S;
            ClickGuiRenderer.drawText(
                    icons,
                    category.token(),
                    baseX,
                    baseY,
                    iconSize,
                    palette.menuHeaderText(),
                    false
            );
            float iconW = ClickGuiRenderer.textWidth(icons, category.token(), iconSize);
            ClickGuiRenderer.drawText(
                    ClickGuiRenderer.getInterRegular(),
                    " " + category.title(),
                    baseX + iconW + 1.5f * S,
                    baseY,
                    textSize,
                    palette.menuHeaderText(),
                    false
            );
        } else if (category.svgIcon()) {
            float iconSize = 11.5f * S;
            float textSize = 11.5f * S;
            float baseX = shellX + 50f * S;
            float baseY = shellY + 13.5f * S;
            Renderer2D.COLOR.svg(
                    category.token(),
                    baseX,
                    baseY + 0.5f * S,
                    iconSize,
                    iconSize,
                    SvgRenderOptions.overrideColor(palette.menuHeaderText())
            );
            ClickGuiRenderer.drawText(
                    ClickGuiRenderer.getInterRegular(),
                    " " + category.title(),
                    baseX + iconSize + 1.5f * S,
                    baseY,
                    textSize,
                    palette.menuHeaderText(),
                    false
            );
        } else {
            ClickGuiRenderer.drawText(
                    ClickGuiRenderer.getInterRegular(),
                    "| " + category.title(),
                    shellX + 50f * S,
                    shellY + 13.5f * S,
                    11.5f * S,
                    palette.menuHeaderText(),
                    false
            );
        }
    }

    private void renderPanelShadow(SettingsGuiPalette palette) {
        LayoutRender2D.roundedSoftShadow(
                shellX,
                shellY,
                shellW,
                shellH,
                8f * S,
                8f * S,
                0.018f,
                palette.menuShadow()
        );
    }

    private void toggleEntry(String id) {
        MenuSettingsResolver.toggleEntry(id);
    }

    private void openSettings(String id) {
        MenuSettingsResolver.ResolvedSettings resolved = MenuSettingsResolver.resolveSettings(id);
        if (resolved == null || resolved.settings().isEmpty()) return;
        settingsPanel.open(resolved.getId(), resolved.title(), resolved.settings(), resolved.hudContext());
    }

    private void openThemeEditor(EditableClickGuiTheme editor) {
        if (editor == null) return;
        activeThemeEditor = editor;
        themeEditorPreview.reset();
        settingsPanel.openEditor("theme:" + editor.id(), editor.title(), editor.buildSettings());
    }

    public enum Category {
        HUD("HUD", "clickgui.settings.tab.hud", "HUD", IconKind.TEXT),
        UI("UI", "clickgui.settings.tab.ui", "UI", IconKind.TEXT),
        THEMES("G", "clickgui.settings.tab.themes", "Themes", IconKind.FONT),
        MAIN_SETTINGS("columns-3-cog", "clickgui.settings.tab.main_settings", "Main Settings", IconKind.SVG),
        RELATIONS("user-pen", "clickgui.settings.tab.relations", "Relations", IconKind.SVG),
        CONFIGS("folder-cog", "clickgui.settings.tab.configs", "Configs", IconKind.SVG);

        private final String token;
        private final String titleKey;
        private final String fallbackTitle;
        private final IconKind iconKind;

        Category(String token, String titleKey, String fallbackTitle, IconKind iconKind) {
            this.token = token;
            this.titleKey = titleKey;
            this.fallbackTitle = fallbackTitle;
            this.iconKind = iconKind;
        }

        public String token() {
            return token;
        }

        public String title() {
            return ClickGuiI18n.tr(titleKey, fallbackTitle);
        }

        public boolean iconToken() {
            return iconKind == IconKind.FONT;
        }

        public boolean svgIcon() {
            return iconKind == IconKind.SVG;
        }

        public enum IconKind {
            TEXT,
            FONT,
            SVG
        }
    }
}

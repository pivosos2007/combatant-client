/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.sections;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.ModeValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.StringValue;
import combatant.client.config.values.SetValue;
import combatant.client.config.subsystem.DuplexLocalConfig;
import combatant.client.config.subsystem.MapUiConfig;
import combatant.client.config.subsystem.MapHeuristicConfig;
import combatant.client.config.subsystem.MapLinkConfig;
import combatant.client.config.subsystem.MapTriangulationConfig;
import combatant.client.features.account.SkinManager;
import combatant.client.features.gui.clickgui.ClickGuiRenderer;
import combatant.client.features.gui.clickgui.util.ClickGuiRichTextRenderer;
import combatant.client.features.gui.clickgui.layout.screen.settings.SettingsGuiPalette;
import combatant.client.features.gui.clickgui.layout.screen.settings.implement.other.SearchComponent;
import combatant.client.features.gui.clickgui.layout.screen.settings.render.LayoutRender2D;
import combatant.client.features.gui.clickgui.settings.BooleanSetting;
import combatant.client.features.gui.clickgui.settings.ColorSetting;
import combatant.client.features.gui.clickgui.settings.ModeSetting;
import combatant.client.features.gui.clickgui.settings.Setting;
import combatant.client.features.gui.clickgui.settings.SettingFactory;
import combatant.client.features.gui.clickgui.settings.TextListSetting;
import combatant.client.features.gui.clickgui.settings.SettingRenderContext;
import combatant.client.features.gui.clickgui.settings.SettingRenderSurface;
import combatant.client.features.gui.clickgui.settings.SliderSetting;
import combatant.client.features.gui.clickgui.settings.TextSetting;
import combatant.client.features.gui.hud.script.HudScriptLayouts;
import combatant.client.features.map.duplex.DuplexRuntime;
import combatant.client.features.map.duplex.DuplexState;
import combatant.client.features.map.location.PlayerLocationEvent;
import combatant.client.features.map.location.PlayerLocationEventType;
import combatant.client.features.map.location.PlayerLocationService;
import combatant.client.features.map.location.PlayerLocationSnapshot;
import combatant.client.features.map.location.PlayerLocationSource;
import combatant.client.features.map.heuristic.HeuristicEstimate;
import combatant.client.features.map.heuristic.HeuristicRuntime;
import combatant.client.features.map.heuristic.HeuristicRuntimeStats;
import combatant.client.features.map.heuristic.HeuristicObservation;
import combatant.client.features.map.heuristic.HeuristicTargetMetrics;
import combatant.client.features.map.heuristic.MapTriangulationMode;
import combatant.client.features.map.runtime.MapLocationRuntime;
import combatant.client.features.map.runtime.PlayerTrackingRangeRuntime;
import combatant.client.features.map.runtime.PlayerTrackingRangeSnapshot;
import combatant.client.features.maplink.model.MapLinkProfile;
import combatant.client.features.maplink.model.MapLinkProviderType;
import combatant.client.features.maplink.model.MapLinkProfileState;
import combatant.client.features.maplink.model.MapLinkProfileStatus;
import combatant.client.features.maplink.model.MapLinkSnapshot;
import combatant.client.features.maplink.runtime.MapLinkRuntime;
import combatant.client.features.relations.CategoryService;
import combatant.client.features.relations.CategoryType;
import combatant.client.features.relations.PlayerRelations;
import combatant.client.features.theme.Theme;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.core.ViewportContext;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.renderer.ui.draw.UiBackdropRequest;
import combatant.client.render.engine.renderer.ui.runtime.core.UiRuntime;
import combatant.client.render.engine.renderer.ui.runtime.render.UiProjectionMode;
import combatant.client.render.engine.renderer.ui.runtime.render.UiRenderContext;
import combatant.client.render.engine.renderer.ui.runtime.script.CachedUiScriptRuntime;
import combatant.client.render.engine.renderer.ui.runtime.script.UiScriptModule;
import combatant.client.render.engine.renderer.ui.runtime.script.UiScriptModuleHandle;
import combatant.client.render.engine.svg.SvgRenderOptions;
import combatant.client.render.helpers.PlayerHeadRenderer;
import combatant.client.render.helpers.ScissorFunction;
import combatant.client.render.helpers.SystemCursor;
import combatant.client.util.logging.DebugLog;
import combatant.client.util.player.PlayerSkinResolver;
import combatant.client.util.resources.asset.UiScriptAsset;
import combatant.client.util.text.ChatNameUtil;
import combatant.client.util.text.ClipboardUtil;
import combatant.client.util.text.LegacyTextUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import xaero.lib.client.config.ClientConfigManager;
import xaero.lib.common.config.Config;
import xaero.lib.common.config.option.BooleanConfigOption;
import xaero.lib.common.config.option.ConfigOption;
import xaero.lib.common.config.option.IndexedConfigOption;
import xaero.lib.common.config.option.RangeConfigOption;
import xaero.lib.common.config.option.SteppedConfigOption;
import xaero.map.MapProcessor;
import xaero.map.WorldMap;
import xaero.map.common.config.option.WorldMapProfiledConfigOptions;
import xaero.map.config.primary.option.WorldMapPrimaryClientConfigOptions;
import xaero.map.config.util.WorldMapClientConfigUtils;
import xaero.map.world.MapDimension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

@UiScriptAsset("combatant:modules/clickgui/map_settings")
final class XaeroMapSettingsPanel {
    private static final float DESIGN_WIDTH = 1120.0f;
    private static final float DESIGN_HEIGHT = 720.0f;
    private static final float MIN_WIDTH = 820.0f;
    private static final float MIN_HEIGHT = 540.0f;
    private static final float SCREEN_INSET = 24.0f;
    private static final float BASE_INSET = 16.0f;
    private static final float HEADER_HEIGHT = 62.0f;
    private static final float SEARCH_WIDTH = 210.0f;
    private static final float SEARCH_HEIGHT = 34.0f;
    private static final float CLOSE_SIZE = 32.0f;

    private final UiScriptModuleHandle moduleHandle = HudScriptLayouts.handle(XaeroMapSettingsPanel.class);
    private final CachedUiScriptRuntime scriptRuntime = new CachedUiScriptRuntime(HudScriptLayouts.runtimeReporter());
    private final Supplier<MapProcessor> processorSupplier;
    private final Supplier<MapDimension> dimensionSupplier;
    private final List<Entry> entries = new ArrayList<>();
    private final List<Hit> hits = new ArrayList<>();
    private final List<CategoryHit> categoryHits = new ArrayList<>();
    private final Set<ConfigOption<?>> boundXaeroOptions = Collections.newSetFromMap(new IdentityHashMap<>());
    private final SearchComponent searchComponent = new SearchComponent();

    private Category selectedCategory = Category.DISPLAY;
    private boolean open;
    private float openAnim;
    private float contentAnim = 1.0f;
    private boolean searchFocused;
    private String search = "";
    private float x;
    private float y;
    private float width;
    private float height;
    private float scroll;
    private float scrollTarget;
    private float scrollVelocity;
    private float maxScroll;
    private float scrollbarX;
    private float scrollbarY;
    private float scrollbarW;
    private float scrollbarH;
    private float scrollbarThumbY;
    private float scrollbarThumbH;
    private float scrollbarDragOffset;
    private float scrollbarHoverAnim;
    private boolean scrollbarVisible;
    private boolean scrollbarDragging;
    private float searchX;
    private float searchY;
    private float searchW;
    private float searchH;
    private float closeX;
    private float closeY;
    private float closeW;
    private float closeH;
    private float contentViewportY;
    private float contentViewportBottom;
    private long renderFrameId;
    private Setting pendingRevealSetting;
    private Setting mapLinkEditorAnchor;
    private boolean profiledSavePending;
    private String selectedMapLinkProfileId = "";
    private boolean primarySavePending;
    private long saveDeadlineNs;
    private final SearchComponent.Model searchModel = new SearchComponent.Model() {
        @Override public boolean focused() { return searchFocused; }
        @Override public String text() { return search; }
        @Override public String placeholder() { return tr("gui.combatant.map.browser.search", "Search"); }
        @Override public void setFocused(boolean focused) { searchFocused = focused; }
    };

    XaeroMapSettingsPanel(Supplier<MapProcessor> processorSupplier, Supplier<MapDimension> dimensionSupplier) {
        this.processorSupplier = processorSupplier != null ? processorSupplier : () -> null;
        this.dimensionSupplier = dimensionSupplier != null ? dimensionSupplier : () -> null;

        ClientConfigManager manager = WorldMap.INSTANCE.getConfigs().getClientConfigManager();
        Config primary = manager.getPrimaryConfigManager().getConfig();

        addCombatantArrowSettings();
        addCombatantHudSettings();
        addCombatantPlayerSettings();

        addProfiled(manager, WorldMapProfiledConfigOptions.COORDINATES, Category.DISPLAY);
        addProfiled(manager, WorldMapProfiledConfigOptions.FOOTSTEPS, Category.DISPLAY);
        addProfiled(manager, WorldMapProfiledConfigOptions.ARROW, Category.DISPLAY);
        addProfiled(manager, WorldMapProfiledConfigOptions.ARROW_COLOR, Category.DISPLAY);
        addProfiled(manager, WorldMapProfiledConfigOptions.DISPLAY_ZOOM, Category.DISPLAY);
        addProfiled(manager, WorldMapProfiledConfigOptions.DISPLAY_HOVERED_BIOME, Category.DISPLAY);
        addProfiled(manager, WorldMapProfiledConfigOptions.ZOOM_BUTTONS, Category.DISPLAY);
        addProfiled(manager, WorldMapProfiledConfigOptions.OPENING_ANIMATION, Category.DISPLAY);
        addProfiled(manager, WorldMapProfiledConfigOptions.MAP_ITEM, Category.DISPLAY);

        addProfiled(manager, WorldMapProfiledConfigOptions.LIGHTING, Category.TERRAIN);
        addProfiled(manager, WorldMapProfiledConfigOptions.BLOCK_COLORS, Category.TERRAIN);
        addProfiled(manager, WorldMapProfiledConfigOptions.LOAD_NEW_CHUNKS, Category.TERRAIN);
        addProfiled(manager, WorldMapProfiledConfigOptions.UPDATE_CHUNKS, Category.TERRAIN);
        addProfiled(manager, WorldMapProfiledConfigOptions.TERRAIN_DEPTH, Category.TERRAIN);
        addProfiled(manager, WorldMapProfiledConfigOptions.TERRAIN_SLOPES, Category.TERRAIN);
        addProfiled(manager, WorldMapProfiledConfigOptions.BIOME_BLENDING, Category.TERRAIN);
        addProfiled(manager, WorldMapProfiledConfigOptions.BIOME_COLORS_IN_VANILLA, Category.TERRAIN);
        addProfiled(manager, WorldMapProfiledConfigOptions.ADJUST_HEIGHT_FOR_SHORT_BLOCKS, Category.TERRAIN);
        addProfiled(manager, WorldMapProfiledConfigOptions.FLOWERS, Category.TERRAIN);
        addProfiled(manager, WorldMapProfiledConfigOptions.STAINED_GLASS, Category.TERRAIN);

        addProfiled(manager, WorldMapProfiledConfigOptions.WAYPOINTS, Category.WAYPOINTS);
        addProfiled(manager, WorldMapProfiledConfigOptions.RENDER_WAYPOINTS, Category.WAYPOINTS);
        addProfiled(manager, WorldMapProfiledConfigOptions.WAYPOINT_BACKGROUNDS, Category.WAYPOINTS);
        addProfiled(manager, WorldMapProfiledConfigOptions.WAYPOINT_SCALE, Category.WAYPOINTS);
        addProfiled(manager, WorldMapProfiledConfigOptions.MIN_ZOOM_LOCAL_WAYPOINTS, Category.WAYPOINTS);
        addPrimary(primary, WorldMapPrimaryClientConfigOptions.DISPLAY_DISABLED_WAYPOINTS, Category.WAYPOINTS);
        addPrimary(primary, WorldMapPrimaryClientConfigOptions.CLOSE_WAYPOINTS_AFTER_HOP, Category.WAYPOINTS);
        addPrimary(primary, WorldMapPrimaryClientConfigOptions.ONLY_CURRENT_MAP_WAYPOINTS, Category.WAYPOINTS);

        addProfiled(manager, WorldMapProfiledConfigOptions.CAVE_MODE_ALLOWED, Category.CAVE);
        addProfiledSet(manager, WorldMapProfiledConfigOptions.CAVE_MODE_ALLOWED_DIMENSIONS, Category.CAVE);
        addCurrentCaveMode();
        addProfiled(manager, WorldMapProfiledConfigOptions.CAVE_MODE_DEPTH, Category.CAVE);
        addProfiled(manager, WorldMapProfiledConfigOptions.LEGIBLE_CAVE_MAPS, Category.CAVE);
        addProfiled(manager, WorldMapProfiledConfigOptions.AUTO_CAVE_MODE, Category.CAVE);
        addProfiled(manager, WorldMapProfiledConfigOptions.CAVE_MODE_TOGGLE_TIMER, Category.CAVE);
        addProfiled(manager, WorldMapProfiledConfigOptions.DEFAULT_CAVE_MODE_TYPE, Category.CAVE);
        addProfiled(manager, WorldMapProfiledConfigOptions.DISPLAY_CAVE_MODE_START, Category.CAVE);
        addCaveStart(primary);

        addProfiled(manager, WorldMapProfiledConfigOptions.MINIMAP_RADAR, Category.PLAYERS);
        addProfiled(manager, WorldMapProfiledConfigOptions.DISPLAY_TRACKED_PLAYERS, Category.PLAYERS);
        addProfiled(manager, WorldMapProfiledConfigOptions.OPAC_CLAIMS, Category.PLAYERS);
        addProfiled(manager, WorldMapProfiledConfigOptions.OPAC_CLAIMS_BORDER_OPACITY, Category.PLAYERS);
        addProfiled(manager, WorldMapProfiledConfigOptions.OPAC_CLAIMS_FILL_OPACITY, Category.PLAYERS);

        addProfiled(manager, WorldMapProfiledConfigOptions.MAP_TELEPORT_ALLOWED, Category.NAVIGATION);
        addProfiled(manager, WorldMapProfiledConfigOptions.PARTIAL_Y_TELEPORT, Category.NAVIGATION);
        addProfiled(manager, WorldMapProfiledConfigOptions.DEFAULT_MAP_TELEPORT_FORMAT, Category.NAVIGATION);
        addProfiled(manager, WorldMapProfiledConfigOptions.DEFAULT_MAP_TELEPORT_DIMENSION_FORMAT, Category.NAVIGATION);
        addProfiled(manager, WorldMapProfiledConfigOptions.DEFAULT_PLAYER_TELEPORT_FORMAT, Category.NAVIGATION);

        addProfiled(manager, WorldMapProfiledConfigOptions.WRITING_DISTANCE, Category.ADVANCED);
        addProfiled(manager, WorldMapProfiledConfigOptions.DETECT_AMBIGUOUS_Y, Category.ADVANCED);
        addPrimary(primary, WorldMapPrimaryClientConfigOptions.RELOAD_VIEWED, Category.ADVANCED);
        addPrimary(primary, WorldMapPrimaryClientConfigOptions.MAX_LOADED_REGIONS, Category.ADVANCED);
        addPrimary(primary, WorldMapPrimaryClientConfigOptions.UPDATE_NOTIFICATIONS, Category.ADVANCED);
        addPrimary(primary, WorldMapPrimaryClientConfigOptions.DEBUG, Category.ADVANCED);
        addPrimary(primary, WorldMapPrimaryClientConfigOptions.DIFFERENTIATE_BY_SERVER_ADDRESS, Category.ADVANCED);

        addTargetingFrontend();
        addTriangulationFrontend();
        addMapLinkFrontend();
        auditXaeroCoverage();
    }

    boolean isOpen() {
        return open;
    }

    boolean isVisible() {
        return open || openAnim > 0.002f;
    }

    void toggle() {
        open = !open;
        if (open) contentAnim = 0.0f;
        else searchFocused = false;
    }

    void openCave() {
        selectedCategory = Category.CAVE;
        resetScroll();
        open = true;
        contentAnim = 0.0f;
        searchFocused = false;
    }

    void close() {
        open = false;
        searchFocused = false;
        flushXaeroSaves();
    }

    void render(float viewportX, float viewportY, float viewportWidth, float viewportHeight,
                float mouseX, float mouseY) {
        float dt = AnimationUtility.deltaTime();
        float target = open ? 1.0f : 0.0f;
        openAnim = animateToward(openAnim, target, dt, open ? 10.5f : 12.0f);
        contentAnim = animateToward(contentAnim, 1.0f, dt, 8.5f);
        if (scrollbarDragging) scrollToMouse(mouseY);
        updateSmoothScroll(dt);
        flushXaeroSavesIfDue();
        if (!open && openAnim <= 0.001f) {
            openAnim = 0.0f;
            hits.clear();
            categoryHits.clear();
            return;
        }

        width = Math.min(DESIGN_WIDTH, Math.max(MIN_WIDTH, viewportWidth - SCREEN_INSET));
        height = Math.min(DESIGN_HEIGHT, Math.max(MIN_HEIGHT, viewportHeight - SCREEN_INSET));
        float eased = easeOutCubic(openAnim);
        x = viewportX + (viewportWidth - width) * 0.5f;
        y = viewportY + (viewportHeight - height) * 0.5f + (1.0f - eased) * 16.0f;

        SolidBrowserLayout layout = SolidBrowserLayout.of(width, height);
        updateInteractiveGeometry(layout);

        float lifecycleAlpha = smootherStep(openAnim);

        // The scripted Browser owns its lifecycle alpha through UiRenderContext so text, images,
        // primitives and glass all decay together. Do not multiply Renderer2D alpha around it as
        // well, otherwise the optical surface fades twice while runtime text only fades once.
        renderBrowserSurface(layout, lifecycleAlpha);

        double previousRendererAlpha = Renderer2D.COLOR.getAlpha();
        float previousGuiAlpha = ClickGuiRenderer.getRenderAlphaMultiplier();
        Renderer2D.COLOR.setAlpha(previousRendererAlpha * lifecycleAlpha);
        ClickGuiRenderer.setRenderAlphaMultiplier(previousGuiAlpha * lifecycleAlpha);
        try {
            searchComponent.render(searchX, searchY, searchW, searchH, searchModel);
            updateSystemCursor(mouseX, mouseY);
            renderSettingsContent(layout, mouseX, mouseY, contentAnim);
        } finally {
            ClickGuiRenderer.restoreRenderAlphaMultiplier(previousGuiAlpha);
            Renderer2D.COLOR.setAlpha(previousRendererAlpha);
        }
    }

    private void updateSystemCursor(float mouseX, float mouseY) {
        if (inside(mouseX, mouseY, searchX, searchY, searchW, searchH)) {
            SystemCursor.set(SystemCursor.CursorType.TEXT);
            return;
        }
        if (inside(mouseX, mouseY, closeX, closeY, closeW, closeH)) {
            SystemCursor.set(SystemCursor.CursorType.HAND);
            return;
        }
        for (CategoryHit hit : categoryHits) {
            if (hit.contains(mouseX, mouseY)) {
                SystemCursor.set(SystemCursor.CursorType.HAND);
                return;
            }
        }
        for (Hit hit : hits) {
            if (!inside(mouseX, mouseY, hit.x, hit.y, hit.width, hit.height)) continue;
            if (hit.setting instanceof TargetPlayersSetting target
                    && target.inputContains(mouseX, mouseY)) {
                SystemCursor.set(SystemCursor.CursorType.TEXT);
            } else {
                SystemCursor.set(SystemCursor.CursorType.HAND);
            }
            return;
        }
    }

    private void renderBrowserSurface(SolidBrowserLayout layout, float lifecycleAlpha) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getResourceManager() == null) return;
        HudScriptLayouts.pollReloadCombo(mc);
        if (moduleHandle.consumeChanged()) scriptRuntime.reset();
        if (!moduleHandle.ensureLoaded(mc.getResourceManager())) {
            HudScriptLayouts.reportLoadError(moduleHandle);
            return;
        }
        UiScriptModule module = moduleHandle.module();
        if (module == null) return;

        LinkedHashMap<String, Object> props = new LinkedHashMap<>();
        props.put("width", width);
        props.put("height", height);
        props.put("title", tr("gui.combatant.map.browser.title", "World Map"));
        props.put("selectedCategory", selectedCategory.id);
        props.put("accent", hex(Theme.theme().accent()));
        props.put("layout", layout.toProps());
        List<LinkedHashMap<String, Object>> categories = new ArrayList<>();
        for (Category category : Category.values()) {
            LinkedHashMap<String, Object> item = new LinkedHashMap<>();
            item.put("id", category.id);
            item.put("label", category.label());
            item.put("description", category.description());
            item.put("icon", category.icon);
            categories.add(item);
        }
        props.put("categories", categories);

        long signature = CachedUiScriptRuntime.signature(props);
        UiRuntime runtime = scriptRuntime.bake(
                moduleHandle,
                module,
                "map-settings",
                signature,
                width,
                height,
                ClickGuiRenderer.getOnestMedium(),
                x,
                y,
                width,
                height,
                () -> props,
                null
        );
        if (runtime != null) {
            // Use the same dedicated Map UI-underlay source as the chrome controls. The map
            // background and both ordinary/direct-textured Xaero tile paths are explicitly mirrored
            // into this target before any Map glass is emitted, so it is the authoritative optical
            // source for UI-native glass. CURRENT_TARGET is intentionally not used here: during the
            // deferred/clip pipeline the visible Map can still live in an intermediate attachment at
            // the point the Browser batch is replayed, which made the snapshot resolve as black.
            Renderer2D.COLOR.withLiquidGlassSceneSource(
                    UiBackdropRequest.SceneSource.UI_UNDERLAY,
                    () -> runtime.render(new UiRenderContext(
                            Renderer2D.COLOR,
                            ClickGuiRenderer.getOnestMedium(),
                            null,
                            0.0f,
                            UiProjectionMode.CURRENT,
                            lifecycleAlpha
                    ))
            );
        }
    }

    private void renderSettingsContent(SolidBrowserLayout layout, float mouseX, float mouseY, float transition) {
        SettingsGuiPalette palette = SettingsGuiPalette.current();
        hits.clear();

        float contentEase = easeOutCubic(transition);
        float contentAlpha = 0.62f + 0.38f * smootherStep(transition);
        float contentX = x + layout.detailX + layout.contentX;
        float contentY = y + layout.contentY + (1.0f - contentEase) * 8.0f;
        float contentW = layout.contentWidth;
        float contentH = layout.contentHeight;
        contentViewportY = contentY;
        contentViewportBottom = contentY + contentH;
        renderFrameId++;
        float cursorY = contentY + scroll;
        float total = 0.0f;
        float columnGap = 12.0f;
        float columnWidth = Math.max(1.0f, (contentW - columnGap) * 0.5f);
        float rowGap = 10.0f;
        float revealOffset = Float.NaN;
        float revealHeight = 0.0f;

        boolean clipped = ScissorFunction.pushRaw(contentX, contentY, contentW, contentH);
        double previousRendererAlpha = Renderer2D.COLOR.getAlpha();
        float previousGuiAlpha = ClickGuiRenderer.getRenderAlphaMultiplier();
        Renderer2D.COLOR.setAlpha(previousRendererAlpha * contentAlpha);
        ClickGuiRenderer.setRenderAlphaMultiplier(previousGuiAlpha * contentAlpha);
        try (SettingRenderContext.Scope ignored = SettingRenderContext.push(SettingRenderSurface.SETTINGS, 1.12f)) {
            // Keep matching entries in their original slots for the whole visibility transition.
            // Removing a compact entry at the tail of its fade changes the two-column pairing and
            // makes its neighbour jump from the right column to the left in a single frame.
            List<Entry> visible = entries.stream().filter(this::matches).toList();
            for (int index = 0; index < visible.size();) {
                Entry left = visible.get(index);
                float leftVis = clamp(left.setting.updateVisibilitySafely(), 0.0f, 1.0f);
                Entry right = isCompact(left.setting)
                        && index + 1 < visible.size()
                        && isCompact(visible.get(index + 1).setting)
                        ? visible.get(index + 1)
                        : null;
                float rightVis = right == null ? 0.0f
                        : clamp(right.setting.updateVisibilitySafely(), 0.0f, 1.0f);
                float leftBaseHeight = left.setting.getHeightSafely();
                float rightBaseHeight = right == null ? 0.0f : right.setting.getHeightSafely();
                float leftHeight = leftBaseHeight * leftVis;
                float rightHeight = rightBaseHeight * rightVis;
                float rowHeight = Math.max(leftHeight, rightHeight);
                float rowVisibility = Math.max(leftVis, rightVis);
                float animatedGap = rowGap * smootherStep(rowVisibility);
                if (pendingRevealSetting != null && rowHeight > 1.0f
                        && (left.setting == pendingRevealSetting || (right != null && right.setting == pendingRevealSetting))) {
                    revealOffset = total;
                    revealHeight = rowHeight;
                }

                if (rowHeight > 0.2f && cursorY + rowHeight >= contentY && cursorY <= contentY + contentH) {
                    float leftWidth = right == null ? preferredWidth(left.setting, contentW) : columnWidth;
                    renderAnimatedSetting(left.setting, contentX, cursorY, leftWidth, leftBaseHeight, leftHeight, leftVis, mouseX, mouseY);
                    if (left.setting.isVisibilityTargetVisibleSafely() && leftVis > 0.45f) {
                        hits.add(new Hit(left.setting, contentX, cursorY, leftWidth, leftHeight));
                    }
                    if (right != null) {
                        float rightX = contentX + columnWidth + columnGap;
                        renderAnimatedSetting(right.setting, rightX, cursorY, columnWidth, rightBaseHeight, rightHeight, rightVis, mouseX, mouseY);
                        if (right.setting.isVisibilityTargetVisibleSafely() && rightVis > 0.45f) {
                            hits.add(new Hit(right.setting, rightX, cursorY, columnWidth, rightHeight));
                        }
                    }
                }
                cursorY += rowHeight + animatedGap;
                total += rowHeight + animatedGap;
                index += right == null ? 1 : 2;
            }
        } finally {
            ClickGuiRenderer.restoreRenderAlphaMultiplier(previousGuiAlpha);
            Renderer2D.COLOR.setAlpha(previousRendererAlpha);
            if (clipped) ScissorFunction.pop();
        }

        if (total <= 0.0f) {
            ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), tr("gui.combatant.map.browser.no_results", "No matching settings"),
                    contentX + 6.0f, contentY + 16.0f, 18.0f,
                    SettingsGuiPalette.withAlpha(palette.panelMuted(), Math.round(255.0f * contentAlpha)), false);
        }
        maxScroll = Math.max(0.0f, total - contentH);
        scrollTarget = clamp(scrollTarget, -maxScroll, 0.0f);
        scroll = clamp(scroll, -maxScroll, 0.0f);
        if (pendingRevealSetting != null && Float.isFinite(revealOffset)) {
            float top = contentY + scroll + revealOffset;
            float bottom = top + revealHeight;
            if (top < contentY + 8.0f) {
                scroll = scrollTarget = clamp(8.0f - revealOffset, -maxScroll, 0.0f);
            } else if (bottom > contentY + contentH - 8.0f) {
                scroll = scrollTarget = clamp(contentH - 8.0f - revealOffset - revealHeight, -maxScroll, 0.0f);
            }
            pendingRevealSetting = null;
        }
        renderScrollbar(contentX, contentY, contentW, contentH, mouseX, mouseY);
    }

    private void renderAnimatedSetting(Setting setting, float sx, float sy, float sw,
                                       float baseHeight, float animatedHeight, float visibility,
                                       float mouseX, float mouseY) {
        if (visibility <= 0.001f || animatedHeight <= 0.001f) return;
        float alpha = smootherStep(visibility);
        float offsetY = (1.0f - easeOutCubic(visibility)) * 7.0f;
        double previousRendererAlpha = Renderer2D.COLOR.getAlpha();
        float previousGuiAlpha = ClickGuiRenderer.getRenderAlphaMultiplier();
        Renderer2D.COLOR.setAlpha(previousRendererAlpha * alpha);
        ClickGuiRenderer.setRenderAlphaMultiplier(previousGuiAlpha * alpha);
        boolean clip = ScissorFunction.pushRaw(sx, sy, sw, Math.max(0.5f, animatedHeight));
        try {
            setting.renderSafely(sx, sy + offsetY, sw, mouseX, mouseY);
        } finally {
            if (clip) ScissorFunction.pop();
            ClickGuiRenderer.restoreRenderAlphaMultiplier(previousGuiAlpha);
            Renderer2D.COLOR.setAlpha(previousRendererAlpha);
        }
    }

    private void updateInteractiveGeometry(SolidBrowserLayout layout) {
        categoryHits.clear();
        float navX = x + layout.navX;
        float navY = y + layout.navStartY;
        Category[] categories = Category.values();
        for (int i = 0; i < categories.length; i++) {
            categoryHits.add(new CategoryHit(categories[i], navX,
                    navY + i * (layout.navRowHeight + layout.navRowGap),
                    layout.navRowWidth, layout.navRowHeight));
        }
        searchX = x + layout.detailX + layout.toolbarLeftX;
        searchY = y + layout.toolbarY;
        searchW = layout.searchWidth;
        searchH = layout.searchHeight;
        closeX = x + layout.detailX + layout.closeX;
        closeY = y + layout.closeY;
        closeW = layout.closeWidth;
        closeH = layout.closeHeight;
    }

    boolean mousePressed(float mouseX, float mouseY, int button) {
        if (!open) return isVisible();
        if (!inside(mouseX, mouseY, x, y, width, height)) {
            close();
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && scrollbarVisible
                && inside(mouseX, mouseY, scrollbarX - 4.0f, scrollbarY,
                scrollbarW + 8.0f, scrollbarH)) {
            scrollbarDragging = true;
            scrollbarDragOffset = inside(mouseX, mouseY, scrollbarX - 4.0f, scrollbarThumbY,
                    scrollbarW + 8.0f, scrollbarThumbH)
                    ? mouseY - scrollbarThumbY : scrollbarThumbH * 0.5f;
            scrollToMouse(mouseY);
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && inside(mouseX, mouseY, closeX, closeY, closeW, closeH)) {
            close();
            return true;
        }
        if (searchComponent.click(searchX, searchY, searchW, searchH, mouseX, mouseY, button, searchModel)) {
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            for (CategoryHit hit : categoryHits) {
                if (!hit.contains(mouseX, mouseY)) continue;
                if (selectedCategory != hit.category) {
                    selectedCategory = hit.category;
                    contentAnim = 0.12f;
                }
                resetScroll();
                return true;
            }
        }
        try (SettingRenderContext.Scope ignored = SettingRenderContext.push(SettingRenderSurface.SETTINGS, 1.12f)) {
            for (Hit hit : hits) {
                if (inside(mouseX, mouseY, hit.x, hit.y, hit.width, hit.height)) {
                    hit.setting.mouseClickedSafely(mouseX, mouseY, button, hit.x, hit.y, hit.width);
                } else {
                    hit.setting.mouseClickedOutsideSafely(mouseX, mouseY, button);
                }
            }
        }
        return true;
    }

    void mouseReleased(float mouseX, float mouseY, int button) {
        if (!open) return;
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) scrollbarDragging = false;
        try (SettingRenderContext.Scope ignored = SettingRenderContext.push(SettingRenderSurface.SETTINGS, 1.12f)) {
            for (Entry entry : entries) entry.setting.mouseReleasedSafely(mouseX, mouseY, button);
        }
    }

    boolean mouseScrolled(float mouseX, float mouseY, double amount) {
        if (!isVisible()) return false;
        if (!open || !inside(mouseX, mouseY, x, y, width, height)) return true;
        try (SettingRenderContext.Scope ignored = SettingRenderContext.push(SettingRenderSurface.SETTINGS, 1.12f)) {
            for (Hit hit : hits) {
                if (inside(mouseX, mouseY, hit.x, hit.y, hit.width, hit.height)
                        && hit.setting.mouseScrolledSafely(mouseX, mouseY, amount)) return true;
            }
        }
        scrollTarget = clamp(scrollTarget + (float) amount * 44.0f, -maxScroll, 0.0f);
        return true;
    }

    boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!open) return false;
        if (searchFocused) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                if (!search.isEmpty()) {
                    search = "";
                } else searchFocused = false;
                resetScroll();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !search.isEmpty()) {
                search = search.substring(0, search.length() - 1);
                resetScroll();
                return true;
            }
        } else if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        try (SettingRenderContext.Scope ignored = SettingRenderContext.push(SettingRenderSurface.SETTINGS, 1.12f)) {
            for (Entry entry : entries) {
                if (matches(entry) && entry.setting.keyPressedSafely(keyCode, scanCode, modifiers)) return true;
            }
        }
        return searchFocused;
    }

    boolean charTyped(char chr, int modifiers) {
        if (!open) return false;
        if (searchFocused && !Character.isISOControl(chr) && search.length() < 64) {
            search += chr;
            resetScroll();
            return true;
        }
        try (SettingRenderContext.Scope ignored = SettingRenderContext.push(SettingRenderSurface.SETTINGS, 1.12f)) {
            for (Entry entry : entries) {
                if (matches(entry) && entry.setting.charTypedSafely(chr, modifiers)) return true;
            }
        }
        return searchFocused;
    }

    private boolean matches(Entry entry) {
        if (search.isBlank()) return entry.category == selectedCategory;
        String needle = search.toLowerCase(Locale.ROOT).trim();
        if (entry.searchText.contains(needle)) return true;
        return entry.setting instanceof DynamicSearchEntry dynamic && dynamic.dynamicSearchText().contains(needle);
    }

    private static boolean isCompact(Setting setting) {
        return setting instanceof BooleanSetting || setting instanceof ModeSetting || setting instanceof SliderSetting<?>;
    }

    private static float preferredWidth(Setting setting, float available) {
        if (setting instanceof ColorSetting) return Math.min(available, 340.0f);
        if (setting instanceof SliderSetting<?>) return Math.min(available, 420.0f);
        if (setting instanceof TextSetting || setting instanceof TextListSetting) return Math.min(available, 520.0f);
        return available;
    }

    private void addCombatantArrowSettings() {
        MapUiConfig config = MapUiConfig.get();
        ModeSetting mode = new ModeSetting(tr("gui.combatant.map.display.arrow_color", "Player arrow color"), config.arrowColorModeValue());
        mode.setI18nEnabled(false, false);
        entries.add(new Entry(mode, Category.DISPLAY, "player arrow color theme custom"));

        ColorSetting color = new ColorSetting(tr("gui.combatant.map.display.arrow_custom", "Custom arrow color"), config.arrowCustomColorValue());
        color.setI18nEnabled(false, false);
        color.visibleWhen(config::isCustomArrowColor);
        entries.add(new Entry(color, Category.DISPLAY, "custom player arrow color tint"));
    }

    private void addCombatantHudSettings() {
        MapUiConfig config = MapUiConfig.get();

        BooleanSetting enabled = new BooleanSetting(
                tr("gui.combatant.map.hud.enabled", "World HUD"),
                config.hudEnabledValue());
        enabled.setI18nEnabled(false, false);
        entries.add(new Entry(enabled, Category.DISPLAY,
                "world hud markers overlay enable disable"));

        BooleanSetting waypoints = new BooleanSetting(
                tr("gui.combatant.map.hud.waypoints", "HUD waypoint markers"),
                config.hudWaypointMarkersValue());
        waypoints.setI18nEnabled(false, false);
        waypoints.visibleWhen(config::hudEnabled);
        entries.add(new Entry(waypoints, Category.WAYPOINTS,
                "hud waypoint markers world labels enable disable"));

        BooleanSetting players = new BooleanSetting(
                tr("gui.combatant.map.hud.players", "HUD distant players"),
                config.hudPlayerMarkersValue());
        players.setI18nEnabled(false, false);
        players.visibleWhen(config::hudEnabled);
        entries.add(new Entry(players, Category.PLAYERS,
                "hud distant remote players heads maplink relations"));

        SliderSetting<Integer> distance = new SliderSetting<>(
                tr("gui.combatant.map.hud.player_distance", "Player HUD distance"),
                config.hudPlayerMaxDistanceValue());
        distance.setI18nEnabled(false, false);
        distance.visibleWhen(() -> config.hudEnabled() && config.hudPlayerMarkers());
        entries.add(new Entry(distance, Category.PLAYERS,
                "player hud distance max range meters"));
    }

    private void addCombatantPlayerSettings() {
        MapUiConfig config = MapUiConfig.get();
        BooleanSetting advanced = new BooleanSetting(
                tr("gui.combatant.map.players.advanced_markers", "Advanced player markers"),
                config.advancedPlayerMarkersValue());
        advanced.setI18nEnabled(false, false);
        entries.add(new Entry(advanced, Category.PLAYERS,
                "advanced player markers heads names relations skins expanded display"));
    }

    private void addCurrentCaveMode() {
        List<Integer> modes = List.of(0, 1, 2);
        List<String> labels = List.of(
                tr("gui.xaero_off", "Off"),
                tr("gui.xaero_wm_cave_mode_type_layered", "Layered"),
                tr("gui.xaero_wm_cave_mode_type_full", "Full")
        );
        ModeSetting setting = new ModeSetting(tr("gui.combatant.map.cave.current_mode", "Current cave mode"), new ExternalModeValue<>(
                "currentCaveMode",
                () -> {
                    MapDimension dimension = dimensionSupplier.get();
                    return dimension == null ? 0 : Math.floorMod(dimension.getCaveModeType(), 3);
                },
                this::setCaveMode,
                modes,
                labels
        ));
        setting.setI18nEnabled(false, false);
        entries.add(new Entry(setting, Category.CAVE, "current cave mode off layered full"));
    }

    private void setCaveMode(int target) {
        MapDimension dimension = dimensionSupplier.get();
        if (dimension == null || !WorldMapClientConfigUtils.getEffectiveCaveModeAllowed()) return;
        target = Math.floorMod(target, 3);
        for (int i = 0; i < 3 && Math.floorMod(dimension.getCaveModeType(), 3) != target; i++) {
            dimension.toggleCaveModeType(true);
        }
        MapProcessor processor = processorSupplier.get();
        if (processor != null) {
            synchronized (processor.uiSync) {
                dimension.saveConfigUnsynced();
            }
            processor.updateCaveStart();
        }
    }

    private void addCaveStart(Config primary) {
        ConfigOption<Integer> option = WorldMapPrimaryClientConfigOptions.CAVE_MODE_START;
        String name = resolveDisplayName(option);
        SliderSetting<Integer> setting = new SliderSetting<>(name, new ExternalCaveStartValue(
                option.getId(),
                () -> {
                    Integer value = primary.get(option);
                    return value == null || value == Integer.MAX_VALUE ? 320 : Math.max(-64, Math.min(319, value));
                },
                value -> {
                    int clamped = Math.max(-64, Math.min(320, value));
                    primary.set(option, clamped >= 320 ? Integer.MAX_VALUE : clamped);
                    markPrimaryDirty();
                    MapProcessor processor = processorSupplier.get();
                    if (processor != null) processor.updateCaveStart();
                }));
        setting.setI18nEnabled(false, false);
        entries.add(new Entry(setting, Category.CAVE, searchText(option, name) + " auto y layer height"));
        boundXaeroOptions.add(option);
    }

    private void addProfiled(ClientConfigManager manager, ConfigOption<?> option, Category category) {
        if (add(option, category,
                () -> profiledDisplayValue(manager, raw(option)),
                value -> {
                    manager.getCurrentProfile().set(raw(option), value);
                    markProfiledDirty();
                },
                () -> profiledUnavailableReason(manager, option))) {
            boundXaeroOptions.add(option);
        }
    }

    private void addProfiledSet(ClientConfigManager manager, ConfigOption<Set<Identifier>> option, Category category) {
        if (option == null) return;
        String name = resolveDisplayName(option);
        ExternalIdentifierSetValue value = new ExternalIdentifierSetValue(option.getId(),
                () -> profiledDisplayValue(manager, option),
                next -> {
                    manager.getCurrentProfile().set(option, next);
                    markProfiledDirty();
                });
        TextListSetting setting = new TextListSetting(name, value, TextListSetting.PickerMode.TEXT);
        setting.setI18nEnabled(false, false);
        setting.unavailableReason(() -> profiledUnavailableReason(manager, option));
        entries.add(new Entry(setting, category, searchText(option, name) + " dimensions worlds"));
        boundXaeroOptions.add(option);
    }

    private void addPrimary(Config config, ConfigOption<?> option, Category category) {
        if (add(option, category, () -> config.get(raw(option)), value -> {
            config.set(raw(option), value);
            markPrimaryDirty();
        }, null)) {
            boundXaeroOptions.add(option);
        }
    }

    private <T> boolean add(ConfigOption<T> option, Category category, Supplier<T> getter, Consumer<T> setter, Supplier<String> unavailableReason) {
        if (option == null) return false;
        try {
            Setting setting;
            String name = resolveDisplayName(option);
            if (option instanceof BooleanConfigOption) {
                setting = new BooleanSetting(name, new ExternalBooleanValue(option.getId(),
                        () -> (Boolean) getter.get(), value -> setter.accept(cast(value))));
            } else if (isExplicitModeOption(option) && option instanceof IndexedConfigOption<?> indexed) {
                setting = modeSetting(name, option, indexed, getter, setter);
            } else if (option instanceof RangeConfigOption && option instanceof IndexedConfigOption<?> indexed
                    && !indexed.getValidValues().isEmpty()) {
                List<Integer> values = indexed.getValidValues().stream().map(value -> (Integer) value).toList();
                setting = new SliderSetting<>(name, new ExternalIntegerValue(option.getId(),
                        () -> (Integer) getter.get(), value -> setter.accept(cast(value)), values,
                        raw(option)));
            } else if (option instanceof SteppedConfigOption && option instanceof IndexedConfigOption<?> indexed
                    && !indexed.getValidValues().isEmpty()) {
                List<Double> values = indexed.getValidValues().stream().map(value -> (Double) value).toList();
                setting = new SliderSetting<>(name, new ExternalDoubleValue(option.getId(),
                        () -> (Double) getter.get(), value -> setter.accept(cast(value)), values,
                        raw(option)));
            } else if (option instanceof IndexedConfigOption<?> indexed && !indexed.getValidValues().isEmpty()) {
                setting = modeSetting(name, option, indexed, getter, setter);
            } else if (option.getDefaultValue() instanceof String) {
                setting = new TextSetting(name, new ExternalStringValue(option.getId(),
                        () -> (String) getter.get(), value -> setter.accept(cast(value))));
            } else {
                return false;
            }
            setting.setI18nEnabled(false, false);
            if (unavailableReason != null) setting.unavailableReason(unavailableReason);
            entries.add(new Entry(setting, category, searchText(option, name)));
            return true;
        } catch (RuntimeException | LinkageError error) {
            String id = option.getId() == null ? "unknown" : option.getId();
            DebugLog.warnOnce("clickgui-map-setting-" + id,
                    "Skipping unavailable Xaero World Map setting: " + id, error);
            return false;
        }
    }

    private void markProfiledDirty() {
        profiledSavePending = true;
        saveDeadlineNs = System.nanoTime() + 250_000_000L;
    }

    private static <T> T profiledDisplayValue(ClientConfigManager manager, ConfigOption<T> option) {
        if (manager == null || option == null) return null;
        try {
            if (isProfiledControlled(manager, option)) return manager.getEffective(option);
        } catch (RuntimeException | LinkageError error) {
            DebugLog.warnOnce("clickgui-map-xaero-effective-" + option.getId(),
                    "Failed to resolve effective Xaero value for " + option.getId(), error);
        }
        return manager.getRaw(option);
    }

    private static boolean isProfiledControlled(ClientConfigManager manager, ConfigOption<?> option) {
        if (manager.getRedirectorManager().shouldDeactivateWidget(option)) return true;
        return !manager.shouldIgnoreServerEnforcement(raw(option))
                && manager.getServerSynced().getEffective(raw(option)) != null;
    }

    private static String profiledUnavailableReason(ClientConfigManager manager, ConfigOption<?> option) {
        if (manager == null || option == null) return null;
        try {
            if (manager.getRedirectorManager().shouldDeactivateWidget(option)) {
                var tooltip = manager.getRedirectorManager().getTooltip(option);
                String detail = tooltip == null ? "" : LegacyTextUtil.stripLegacy(tooltip.getString()).trim();
                return detail.isBlank() ? "Controlled by Xaero compatibility override" : detail;
            }
            if (isProfiledControlled(manager, option)) return "Enforced by server";
        } catch (RuntimeException | LinkageError error) {
            DebugLog.warnOnce("clickgui-map-xaero-override-" + option.getId(),
                    "Failed to resolve Xaero override state for " + option.getId(), error);
        }
        return null;
    }

    private void markPrimaryDirty() {
        primarySavePending = true;
        saveDeadlineNs = System.nanoTime() + 250_000_000L;
    }

    private void flushXaeroSavesIfDue() {
        if ((!profiledSavePending && !primarySavePending) || System.nanoTime() < saveDeadlineNs) return;
        flushXaeroSaves();
    }

    private void flushXaeroSaves() {
        try {
            var channel = WorldMap.INSTANCE.getConfigs();
            if (profiledSavePending) {
                channel.getClientConfigProfileIO().save(channel.getClientConfigManager().getCurrentProfile());
                profiledSavePending = false;
            }
            if (primarySavePending) {
                channel.getPrimaryClientConfigManagerIO().save();
                primarySavePending = false;
            }
        } catch (RuntimeException | LinkageError error) {
            DebugLog.warnOnce("clickgui-map-xaero-save", "Failed to persist Xaero map config", error);
        }
    }

    private void addSubsystemSettings(combatant.client.config.subsystem.SubsystemConfig config, Category category, String... skipIds) {
        Set<String> skip = skipIds == null ? Set.of() : Set.of(skipIds);
        for (var def : config.getSettingDefs()) {
            if (def == null || skip.contains(def.getId())) continue;
            Setting setting = SettingFactory.fromDef(def);
            if (setting == null) continue;
            setting.setParent(config);
            entries.add(new Entry(setting, category, (def.getId() + " " + setting.getName()).toLowerCase(Locale.ROOT)));
        }
    }

    private void addTargetingFrontend() {
        TargetPlayersSetting targets = new TargetPlayersSetting();
        targets.setI18nEnabled(false, false);
        entries.add(new Entry(targets, Category.TARGETS, "target players targeted locator triangulation tracking player list"));
    }

    private void addTriangulationFrontend() {
        entries.add(new Entry(new TriangulationModeSetting(), Category.TRIANGULATION,
                "triangulation mode off data mining targeted collection workload"));

        TriangulationStatusSetting status = new TriangulationStatusSetting();
        status.setI18nEnabled(false, false);
        entries.add(new Entry(status, Category.TRIANGULATION,
                "triangulation resolver status telemetry observations bearings confidence uncertainty estimates data mining targeted"));

        entries.add(new Entry(new SectionHeaderSetting(
                tr("gui.combatant.map.duplex.section", "Duplex courier"),
                tr("gui.combatant.map.duplex.section.description",
                        "Pairs this client's locator ray with a minimal courier running in another game session.")),
                Category.TRIANGULATION, "duplex courier secondary peer local session"));
        BooleanSetting duplexEnabled = new BooleanSetting(
                tr("gui.combatant.map.duplex.enabled", "Enable Duplex listener"),
                DuplexLocalConfig.get().enabledValue());
        duplexEnabled.setI18nEnabled(false, false);
        duplexEnabled.setParent(DuplexLocalConfig.get());
        entries.add(new Entry(duplexEnabled, Category.TRIANGULATION,
                "duplex enabled listener courier local ipc"));
        DuplexStatusSetting duplexStatus = new DuplexStatusSetting();
        duplexStatus.setI18nEnabled(false, false);
        entries.add(new Entry(duplexStatus, Category.TRIANGULATION,
                "duplex status courier peer connected session endpoint bearings estimates error"));

        entries.add(new Entry(new SectionHeaderSetting(
                tr("gui.combatant.map.triangulation.section.workload", "Collection workload"),
                tr("gui.combatant.map.triangulation.section.workload.description", "How often observations are resolved and how many players Data Mining may process.")),
                Category.TRIANGULATION, "triangulation workload solve interval targets"));
        addSubsystemSetting(MapTriangulationConfig.get(), Category.TRIANGULATION, "dataMiningMinSolveIntervalMs");
        addSubsystemSetting(MapTriangulationConfig.get(), Category.TRIANGULATION, "targetedMinSolveIntervalMs");
        addSubsystemSetting(MapTriangulationConfig.get(), Category.TRIANGULATION, "dataMiningMaxActiveTargets");

        entries.add(new Entry(new SectionHeaderSetting(
                tr("gui.combatant.map.triangulation.section.quality", "Resolve quality"),
                tr("gui.combatant.map.triangulation.section.quality.description", "Observation retention, required movement and bearing separation.")),
                Category.TRIANGULATION, "resolver quality samples baseline bearing noise"));
        addSubsystemSetting(MapHeuristicConfig.get(), Category.TRIANGULATION, "enabled");
        addSubsystemSetting(MapHeuristicConfig.get(), Category.TRIANGULATION, "maxSamplesPerTarget");
        addSubsystemSetting(MapHeuristicConfig.get(), Category.TRIANGULATION, "maxSampleAgeMs");
        addSubsystemSetting(MapHeuristicConfig.get(), Category.TRIANGULATION, "minBaseline");
        addSubsystemSetting(MapHeuristicConfig.get(), Category.TRIANGULATION, "minBearingDeltaDegrees");
        addSubsystemSetting(MapHeuristicConfig.get(), Category.TRIANGULATION, "bearingNoiseDegrees");
        addSubsystemSetting(MapHeuristicConfig.get(), Category.TRIANGULATION, "liveSourceGraceMs");
        addSubsystemSetting(MapHeuristicConfig.get(), Category.TRIANGULATION, "staleEstimateDisplayMs");

        entries.add(new Entry(new SectionHeaderSetting(
                tr("gui.combatant.map.triangulation.section.reset", "Movement & rejection"),
                tr("gui.combatant.map.triangulation.section.reset.description", "Advanced gates used to reject backward rays and start a new estimate segment when the target moves.")),
                Category.TRIANGULATION, "movement reset reject segment advanced"));
        addSubsystemSetting(MapHeuristicConfig.get(), Category.TRIANGULATION, "forwardRejectTolerance");
        addSubsystemSetting(MapHeuristicConfig.get(), Category.TRIANGULATION, "segmentResetDistance");
        addSubsystemSetting(MapHeuristicConfig.get(), Category.TRIANGULATION, "segmentResetSigma");
        addSubsystemSetting(MapHeuristicConfig.get(), Category.TRIANGULATION, "segmentResetMinConfidence");
    }

    private void addMapLinkFrontend() {
        addSubsystemSetting(MapLinkConfig.get(), Category.MAPLINK, "enabled");

        MapLinkStatusSetting status = new MapLinkStatusSetting();
        status.setI18nEnabled(false, false);
        entries.add(new Entry(status, Category.MAPLINK, "maplink providers profiles status live stale players worlds server"));

        entries.add(new Entry(new SectionHeaderSetting(
                tr("gui.combatant.map.maplink.section.selected", "Profile settings"),
                tr("gui.combatant.map.maplink.section.selected.description",
                        "Match a Minecraft server to its web map. Changes are applied automatically."),
                this::hasSelectedMapLinkProfile), Category.MAPLINK, "maplink selected profile editor"));

        TextSetting displayName = new TextSetting(tr("gui.combatant.map.maplink.profile.name", "Profile name"),
                new ExternalStringValue("mapLinkProfileName", this::selectedMapLinkDisplayName,
                        value -> updateSelectedMapLinkProfile(profile -> copyMapLinkProfile(profile, value, profile.enabled(), profile.serverMatcher(), profile.baseUrl(), profile.providerType(), profile.refreshIntervalMs(), profile.defaultY(), profile.sourcePriority(), profile.dimensionMappings(), profile.requestHeaders()))));
        displayName.setI18nEnabled(false, false);
        displayName.visibleWhen(this::hasSelectedMapLinkProfile);
        mapLinkEditorAnchor = displayName;
        entries.add(new Entry(displayName, Category.MAPLINK, "maplink profile name label"));

        TextSetting serverMatcher = new TextSetting(tr("gui.combatant.map.maplink.profile.server", "Minecraft server"),
                new ExternalStringValue("mapLinkServerMatcher", this::selectedMapLinkServerMatcher,
                        value -> updateSelectedMapLinkProfile(profile -> copyMapLinkProfile(profile, profile.displayName(), mapLinkProfileConfigured(value, profile.baseUrl()), value, profile.baseUrl(), profile.providerType(), profile.refreshIntervalMs(), profile.defaultY(), profile.sourcePriority(), profile.dimensionMappings(), profile.requestHeaders()))));
        serverMatcher.setI18nEnabled(false, false);
        serverMatcher.visibleWhen(this::hasSelectedMapLinkProfile);
        entries.add(new Entry(serverMatcher, Category.MAPLINK, "maplink profile server matcher address host ip"));

        TextSetting baseUrl = new TextSetting(tr("gui.combatant.map.maplink.profile.url", "Web map URL"),
                new ExternalStringValue("mapLinkBaseUrl", this::selectedMapLinkBaseUrl,
                        value -> updateSelectedMapLinkProfile(profile -> copyMapLinkProfile(profile, profile.displayName(), mapLinkProfileConfigured(profile.serverMatcher(), value), profile.serverMatcher(), value, profile.providerType(), profile.refreshIntervalMs(), profile.defaultY(), profile.sourcePriority(), profile.dimensionMappings(), profile.requestHeaders()))));
        baseUrl.setI18nEnabled(false, false);
        baseUrl.visibleWhen(this::hasSelectedMapLinkProfile);
        entries.add(new Entry(baseUrl, Category.MAPLINK, "maplink profile url base endpoint web map"));

        List<MapLinkProviderType> providers = List.of(MapLinkProviderType.values());
        List<String> providerLabels = providers.stream().map(XaeroMapSettingsPanel::mapLinkProviderLabel).toList();
        ModeSetting provider = new ModeSetting(tr("gui.combatant.map.maplink.profile.provider", "Provider"),
                new ExternalModeValue<>("mapLinkProvider", this::selectedMapLinkProvider,
                        value -> updateSelectedMapLinkProfile(profile -> copyMapLinkProfile(profile, profile.displayName(), profile.enabled(), profile.serverMatcher(), profile.baseUrl(), value, profile.refreshIntervalMs(), profile.defaultY(), profile.sourcePriority(), profile.dimensionMappings(), profile.requestHeaders())),
                        providers, providerLabels));
        provider.setI18nEnabled(false, false);
        provider.visibleWhen(this::hasSelectedMapLinkProfile);
        entries.add(new Entry(provider, Category.MAPLINK, "maplink provider bluemap dynmap liveatlas pl3x squaremap players json"));

        SliderSetting<Long> refresh = new SliderSetting<>(tr("gui.combatant.map.maplink.profile.refresh", "Refresh interval (ms)"),
                new ExternalLongValue("mapLinkRefresh", this::selectedMapLinkRefresh,
                        value -> updateSelectedMapLinkProfile(profile -> copyMapLinkProfile(profile, profile.displayName(), profile.enabled(), profile.serverMatcher(), profile.baseUrl(), profile.providerType(), value, profile.defaultY(), profile.sourcePriority(), profile.dimensionMappings(), profile.requestHeaders())),
                        500L, 120000L));
        refresh.setI18nEnabled(false, false);
        refresh.visibleWhen(this::hasSelectedMapLinkProfile);
        entries.add(new Entry(refresh, Category.MAPLINK, "maplink refresh interval polling"));

        SliderSetting<Integer> defaultY = new SliderSetting<>(tr("gui.combatant.map.maplink.profile.default_y", "Fallback Y"),
                new ExternalIntegerRangeValue("mapLinkDefaultY", this::selectedMapLinkDefaultY,
                        value -> updateSelectedMapLinkProfile(profile -> copyMapLinkProfile(profile, profile.displayName(), profile.enabled(), profile.serverMatcher(), profile.baseUrl(), profile.providerType(), profile.refreshIntervalMs(), value, profile.sourcePriority(), profile.dimensionMappings(), profile.requestHeaders())),
                        -64, 320));
        defaultY.setI18nEnabled(false, false);
        defaultY.visibleWhen(this::hasSelectedMapLinkProfile);
        entries.add(new Entry(defaultY, Category.MAPLINK, "maplink fallback default y height"));

        SliderSetting<Integer> priority = new SliderSetting<>(tr("gui.combatant.map.maplink.profile.priority", "Source priority"),
                new ExternalIntegerRangeValue("mapLinkPriority", this::selectedMapLinkPriority,
                        value -> updateSelectedMapLinkProfile(profile -> copyMapLinkProfile(profile, profile.displayName(), profile.enabled(), profile.serverMatcher(), profile.baseUrl(), profile.providerType(), profile.refreshIntervalMs(), profile.defaultY(), value, profile.dimensionMappings(), profile.requestHeaders())),
                        -100, 100));
        priority.setI18nEnabled(false, false);
        priority.visibleWhen(this::hasSelectedMapLinkProfile);
        entries.add(new Entry(priority, Category.MAPLINK, "maplink source priority location source"));

        TextListSetting mappings = new TextListSetting(tr("gui.combatant.map.maplink.profile.mappings", "World mappings"),
                new ExternalMapSetValue("mapLinkMappings", this::selectedMapLinkMappings,
                        value -> updateSelectedMapLinkProfile(profile -> copyMapLinkProfile(profile, profile.displayName(), profile.enabled(), profile.serverMatcher(), profile.baseUrl(), profile.providerType(), profile.refreshIntervalMs(), profile.defaultY(), profile.sourcePriority(), value, profile.requestHeaders()))),
                TextListSetting.PickerMode.TEXT);
        mappings.setI18nEnabled(false, false);
        mappings.visibleWhen(this::hasSelectedMapLinkProfile);
        entries.add(new Entry(mappings, Category.MAPLINK, "maplink world mappings dimension provider world"));

        entries.add(new Entry(new SectionHeaderSetting(
                tr("gui.combatant.map.maplink.section.network", "Network"),
                tr("gui.combatant.map.maplink.section.network.description", "Timeouts and retry policy.")),
                Category.MAPLINK, "maplink networking timeout stale backoff"));
        addSubsystemSettings(MapLinkConfig.get(), Category.MAPLINK, "enabled");
    }

    private void addSubsystemSetting(combatant.client.config.subsystem.SubsystemConfig config, Category category, String id) {
        if (config == null || id == null) return;
        for (var def : config.getSettingDefs()) {
            if (def == null || !id.equals(def.getId())) continue;
            Setting setting = SettingFactory.fromDef(def);
            if (setting == null) return;
            setting.setParent(config);
            entries.add(new Entry(setting, category, (def.getId() + " " + setting.getName()).toLowerCase(Locale.ROOT)));
            return;
        }
    }

    private boolean hasSelectedMapLinkProfile() {
        return selectedMapLinkProfile() != null;
    }

    private MapLinkProfile selectedMapLinkProfile() {
        if (selectedMapLinkProfileId == null || selectedMapLinkProfileId.isBlank()) return null;
        for (MapLinkProfile profile : MapLinkConfig.get().allProfiles()) {
            if (profile.id().equals(selectedMapLinkProfileId)) return profile;
        }
        return null;
    }

    private void selectMapLinkProfile(String id) {
        selectedMapLinkProfileId = id == null ? "" : id;
    }

    private void revealMapLinkProfile(String id) {
        selectMapLinkProfile(id);
        // "Edit" is a navigation action, not merely row selection. A search query can keep the
        // editor controls filtered out, which previously made the pencil look completely dead.
        // Always leave search mode and reveal the selected profile's editor in the MapLink section.
        selectedCategory = Category.MAPLINK;
        search = "";
        searchFocused = false;
        pendingRevealSetting = mapLinkEditorAnchor;
    }

    private void updateSelectedMapLinkProfile(UnaryOperator<MapLinkProfile> update) {
        MapLinkProfile current = selectedMapLinkProfile();
        if (current == null || update == null) return;
        List<MapLinkProfile> next = new ArrayList<>(MapLinkConfig.get().allProfiles().size());
        boolean changed = false;
        for (MapLinkProfile profile : MapLinkConfig.get().allProfiles()) {
            if (!profile.id().equals(current.id())) {
                next.add(profile);
                continue;
            }
            MapLinkProfile replacement = update.apply(profile);
            next.add(replacement == null ? profile : replacement);
            changed = replacement != null && !replacement.equals(profile);
        }
        if (changed) MapLinkConfig.get().setProfiles(next);
    }

    private void createMapLinkProfile() {
        String server = currentServerAddress();
        String baseId = sanitizeProfileId(server.isBlank() ? "maplink" : server);
        Set<String> used = new LinkedHashSet<>();
        for (MapLinkProfile profile : MapLinkConfig.get().allProfiles()) used.add(profile.id());
        String id = baseId;
        int suffix = 2;
        while (used.contains(id)) id = baseId + '-' + suffix++;
        String label = server.isBlank()
                ? tr("gui.combatant.map.maplink.profiles.new_profile", "New MapLink profile")
                : server;
        MapLinkProfile created = new MapLinkProfile(id, label, false, server, "",
                MapLinkProviderType.BLUEMAP, 10_000L, 64, 0, Map.of(), Map.of());
        List<MapLinkProfile> next = new ArrayList<>(MapLinkConfig.get().allProfiles());
        next.add(created);
        MapLinkConfig.get().setProfiles(next);
        revealMapLinkProfile(created.id());
    }

    private void deleteSelectedMapLinkProfile() {
        MapLinkProfile selected = selectedMapLinkProfile();
        if (selected == null) return;
        List<MapLinkProfile> next = MapLinkConfig.get().allProfiles().stream()
                .filter(profile -> !profile.id().equals(selected.id())).toList();
        MapLinkConfig.get().setProfiles(next);
        selectedMapLinkProfileId = next.isEmpty() ? "" : next.getFirst().id();
    }

    private static String sanitizeProfileId(String raw) {
        String source = raw == null ? "server" : raw.trim().toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(source.length());
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-' || c == '_' || c == '.') out.append(c);
            else out.append('-');
        }
        while (!out.isEmpty() && out.charAt(out.length() - 1) == '-') out.setLength(out.length() - 1);
        return out.isEmpty() ? "server" : out.toString();
    }

    private static boolean mapLinkProfileConfigured(String serverMatcher, String baseUrl) {
        return serverMatcher != null && !serverMatcher.isBlank() && baseUrl != null && !baseUrl.isBlank();
    }

    private static MapLinkProfile copyMapLinkProfile(MapLinkProfile source, String displayName, boolean enabled,
                                                     String serverMatcher, String baseUrl, MapLinkProviderType providerType,
                                                     long refreshIntervalMs, int defaultY, int sourcePriority,
                                                     Map<String, String> mappings, Map<String, String> headers) {
        return new MapLinkProfile(source.id(), displayName, enabled, serverMatcher, baseUrl, providerType,
                refreshIntervalMs, defaultY, sourcePriority, mappings, headers);
    }

    private String selectedMapLinkDisplayName() { MapLinkProfile p = selectedMapLinkProfile(); return p == null ? "" : p.displayName(); }
    private String selectedMapLinkServerMatcher() { MapLinkProfile p = selectedMapLinkProfile(); return p == null ? "" : p.serverMatcher(); }
    private String selectedMapLinkBaseUrl() { MapLinkProfile p = selectedMapLinkProfile(); return p == null ? "" : p.baseUrl(); }
    private MapLinkProviderType selectedMapLinkProvider() { MapLinkProfile p = selectedMapLinkProfile(); return p == null ? MapLinkProviderType.BLUEMAP : p.providerType(); }
    private long selectedMapLinkRefresh() { MapLinkProfile p = selectedMapLinkProfile(); return p == null ? 10_000L : p.refreshIntervalMs(); }
    private int selectedMapLinkDefaultY() { MapLinkProfile p = selectedMapLinkProfile(); return p == null ? 64 : p.defaultY(); }
    private int selectedMapLinkPriority() { MapLinkProfile p = selectedMapLinkProfile(); return p == null ? 0 : p.sourcePriority(); }
    private Map<String, String> selectedMapLinkMappings() { MapLinkProfile p = selectedMapLinkProfile(); return p == null ? Map.of() : p.dimensionMappings(); }

    private static String relationLabel(CategoryType type) {
        if (type == null) type = CategoryType.DEFAULT;
        return switch (type) {
            case STAFF -> tr("gui.combatant.map.targets.relation.staff", "Staff");
            case BEDWARS_SELF -> tr("gui.combatant.map.targets.relation.ally", "Team");
            case FRIEND -> tr("gui.combatant.map.targets.relation.friend", "Friend");
            case BEDWARS_ENEMY -> tr("gui.combatant.map.targets.relation.opponent", "Opponent");
            case ENEMY -> tr("gui.combatant.map.targets.relation.enemy", "Enemy");
            // The absence of a configured relation is not useful metadata in a player list.
            case DEFAULT -> "";
        };
    }

    private static int relationColor(String name) {
        CategoryType type = CategoryService.get(name);
        return type == CategoryType.DEFAULT ? PlayerRelations.get().colorDefault() : CategoryService.getColor(name);
    }

    private static String playerLocationSourceLabel(PlayerLocationSource source) {
        if (source == null) return tr("gui.combatant.map.source.unknown", "unknown");
        return switch (source) {
            case LOCAL_ENTITY_EXACT -> tr("gui.combatant.map.source.local", "local");
            case MAPLINK_EXACT -> tr("gui.combatant.map.source.maplink", "MapLink");
            case LOCATOR_EXACT -> tr("gui.combatant.map.source.locator_exact", "locator exact");
            case DUPLEX_TRIANGULATED -> tr("gui.combatant.map.source.duplex", "duplex");
            case TRIANGULATED -> tr("gui.combatant.map.source.triangulated", "triangulated");
            case LOCATOR_BEARING -> tr("gui.combatant.map.source.bearing", "locator bearing");
            case LOCATOR_APPROXIMATE -> tr("gui.combatant.map.source.locator_approx", "locator approximate");
            case HISTORICAL -> tr("gui.combatant.map.source.history", "history");
        };
    }

    private static String mapLinkStatusLabel(MapLinkProfileStatus status) {
        if (status == null) return tr("gui.combatant.map.maplink.status.idle", "Idle");
        return switch (status) {
            case DISABLED -> tr("gui.combatant.map.maplink.status.disabled", "Disabled");
            case IDLE -> tr("gui.combatant.map.maplink.status.idle", "Idle");
            case CONNECTING -> tr("gui.combatant.map.maplink.status.connecting", "Connecting");
            case LIVE -> tr("gui.combatant.map.maplink.status.live", "Live");
            case STALE -> tr("gui.combatant.map.maplink.status.stale", "Stale");
            case AUTH_ERROR -> tr("gui.combatant.map.maplink.status.auth_error", "Auth error");
            case HTTP_ERROR -> tr("gui.combatant.map.maplink.status.http_error", "HTTP error");
            case PARSE_ERROR -> tr("gui.combatant.map.maplink.status.parse_error", "Parse error");
            case WORLD_UNMAPPED -> tr("gui.combatant.map.maplink.status.world_unmapped", "World unmapped");
        };
    }

    private static String mapLinkProviderLabel(MapLinkProviderType type) {
        if (type == null) return "Players JSON";
        return switch (type) {
            case BLUEMAP -> "BlueMap";
            case DYNMAP -> "Dynmap";
            case LIVEATLAS -> "LiveAtlas";
            case PL3XMAP -> "Pl3xMap";
            case PLAYERS_JSON -> "Players JSON";
            case SQUAREMAP -> "SquareMap";
        };
    }

    private void auditXaeroCoverage() {
        auditXaeroOptionClass(WorldMapProfiledConfigOptions.class, Set.of());
        auditXaeroOptionClass(WorldMapPrimaryClientConfigOptions.class, Set.of(
                WorldMapPrimaryClientConfigOptions.IGNORED_UPDATE,
                WorldMapPrimaryClientConfigOptions.RELOAD_VIEWED_VERSION,
                WorldMapPrimaryClientConfigOptions.GLOBAL_VERSION
        ));
    }

    private void auditXaeroOptionClass(Class<?> owner, Set<ConfigOption<?>> ignored) {
        try {
            for (var field : owner.getFields()) {
                if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
                Object value = field.get(null);
                if (!(value instanceof ConfigOption<?> option) || ignored.contains(option)) continue;
                if (boundXaeroOptions.contains(option)) continue;
                DebugLog.warnOnce("clickgui-map-xaero-unbound-" + owner.getSimpleName() + '-' + field.getName(),
                        "Xaero World Map option is not exposed in Combatant Browser: "
                                + owner.getSimpleName() + '.' + field.getName());
            }
        } catch (ReflectiveOperationException | RuntimeException error) {
            DebugLog.warnOnce("clickgui-map-xaero-coverage-" + owner.getSimpleName(),
                    "Failed to audit Xaero World Map option coverage for " + owner.getSimpleName(), error);
        }
    }

    private static boolean isExplicitModeOption(ConfigOption<?> option) {
        return option == WorldMapProfiledConfigOptions.BLOCK_COLORS
                || option == WorldMapProfiledConfigOptions.TERRAIN_SLOPES
                || option == WorldMapProfiledConfigOptions.AUTO_CAVE_MODE
                || option == WorldMapProfiledConfigOptions.ARROW_COLOR
                || option == WorldMapProfiledConfigOptions.DEFAULT_CAVE_MODE_TYPE;
    }

    private static <T> Setting modeSetting(String name, ConfigOption<T> option,
                                           IndexedConfigOption<?> indexed,
                                           Supplier<T> getter, Consumer<T> setter) {
        List<T> values = indexed.getValidValues().stream().map(XaeroMapSettingsPanel::<T>cast).toList();
        List<String> labels = new ArrayList<>(values.size());
        for (T value : values) {
            var display = option.getDisplayGetter().apply(option, value);
            String label = display == null ? String.valueOf(value) : LegacyTextUtil.stripLegacy(display.getString());
            label = label == null || label.isBlank() ? String.valueOf(value) : label.trim();
            if (labels.contains(label)) label = label + " (" + value + ')';
            labels.add(label);
        }
        return new ModeSetting(name, new ExternalModeValue<>(option.getId(), getter, setter, values, labels));
    }

    private static String searchText(ConfigOption<?> option, String name) {
        return ((name == null ? "" : name) + ' ' + (option.getId() == null ? "" : option.getId()))
                .toLowerCase(Locale.ROOT);
    }

    private static String resolveDisplayName(ConfigOption<?> option) {
        var component = option.getDisplayName();
        if (component != null) {
            String displayName = LegacyTextUtil.stripLegacy(component.getString());
            if (displayName != null && !displayName.isBlank()) return displayName.trim();
        }
        return humanizeOptionId(option.getId());
    }

    private static String humanizeOptionId(String id) {
        if (id == null || id.isBlank()) return tr("gui.combatant.map.common.xaero_setting", "Xaero setting");
        String[] words = id.trim().replace('.', '_').replace('-', '_').split("_+");
        StringBuilder out = new StringBuilder(id.length() + 4);
        for (String word : words) {
            if (word.isBlank()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) out.append(word.substring(1).toLowerCase(Locale.ROOT));
        }
        return out.isEmpty() ? id : out.toString();
    }

    private static String hex(int argb) {
        return String.format(Locale.ROOT, "#%08X", argb);
    }

    @SuppressWarnings("unchecked")
    private static <T> ConfigOption<T> raw(ConfigOption<?> option) { return (ConfigOption<T>) option; }
    @SuppressWarnings("unchecked")
    private static <T> T cast(Object value) { return (T) value; }
    private static boolean inside(float mx, float my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
    private static String tr(String key, String fallback) {
        String translated;
        try {
            translated = I18n.get(key, "");
        } catch (RuntimeException ignored) {
            translated = null;
        }
        if (translated == null || translated.equals(key) || translated.startsWith("Format error:")) {
            translated = fallback;
        }
        return LegacyTextUtil.stripLegacy(translated).replace('\n', ' ').replace('\r', ' ').trim();
    }

    private static float easeOutCubic(float value) {
        float t = clamp(value, 0.0f, 1.0f);
        float inv = 1.0f - t;
        return 1.0f - inv * inv * inv;
    }

    private static float smootherStep(float value) {
        float t = clamp(value, 0.0f, 1.0f);
        return t * t * t * (t * (t * 6.0f - 15.0f) + 10.0f);
    }

    /** Exponential damping is stable across frame rates and never overshoots at the tail. */
    private static float animateToward(float value, float target, float dt, float speed) {
        float safeDt = clamp(Float.isFinite(dt) ? dt : 1.0f / 60.0f, 0.0f, 1.0f / 20.0f);
        float blend = 1.0f - (float) Math.exp(-Math.max(0.0f, speed) * safeDt);
        float next = value + (target - value) * blend;
        return Math.abs(target - next) < 0.0001f ? target : next;
    }

    /** Critically damped scrolling keeps wheel input responsive without the abrupt exponential tail. */
    private void updateSmoothScroll(float dt) {
        float safeDt = clamp(Float.isFinite(dt) ? dt : 1.0f / 60.0f, 0.0f, 1.0f / 20.0f);
        float omega = 2.0f / 0.115f;
        float x = omega * safeDt;
        float decay = 1.0f / (1.0f + x + 0.48f * x * x + 0.235f * x * x * x);
        float change = scroll - scrollTarget;
        float temp = (scrollVelocity + omega * change) * safeDt;
        scrollVelocity = (scrollVelocity - omega * temp) * decay;
        scroll = scrollTarget + (change + temp) * decay;
        if (Math.abs(scrollTarget - scroll) < 0.01f && Math.abs(scrollVelocity) < 0.05f) {
            scroll = scrollTarget;
            scrollVelocity = 0.0f;
        }
    }

    private void renderScrollbar(float contentX, float contentY, float contentW, float contentH,
                                 float mouseX, float mouseY) {
        scrollbarVisible = maxScroll > 0.5f;
        if (!scrollbarVisible) {
            scrollbarDragging = false;
            scrollbarX = scrollbarY = scrollbarW = scrollbarH = 0.0f;
            return;
        }

        scrollbarW = 2.5f;
        scrollbarX = contentX + contentW - 4.0f;
        scrollbarY = contentY + 5.0f;
        scrollbarH = Math.max(1.0f, contentH - 10.0f);
        scrollbarThumbH = Math.max(18.0f, scrollbarH * (scrollbarH / (scrollbarH + maxScroll)));
        float ratio = maxScroll <= 0.0f ? 0.0f : clamp(-scroll / maxScroll, 0.0f, 1.0f);
        scrollbarThumbY = scrollbarY + (scrollbarH - scrollbarThumbH) * ratio;

        boolean hovered = inside(mouseX, mouseY, scrollbarX - 4.0f, scrollbarY,
                scrollbarW + 8.0f, scrollbarH);
        scrollbarHoverAnim = animateToward(scrollbarHoverAnim,
                hovered || scrollbarDragging ? 1.0f : 0.0f,
                AnimationUtility.deltaTime(), hovered || scrollbarDragging ? 14.0f : 8.0f);
        if (hovered || scrollbarDragging) SystemCursor.set(SystemCursor.CursorType.SCROLL);

        SettingsGuiPalette palette = SettingsGuiPalette.current();
        float reveal = smootherStep(scrollbarHoverAnim);
        int trackA = SettingsGuiPalette.withAlpha(palette.moduleScrollTrackA(), Math.round(125.0f + reveal * 45.0f));
        int trackB = SettingsGuiPalette.withAlpha(palette.moduleScrollTrackB(), Math.round(90.0f + reveal * 35.0f));
        int handleA = SettingsGuiPalette.withAlpha(palette.moduleScrollHandleA(), Math.round(205.0f + reveal * 45.0f));
        int handleB = SettingsGuiPalette.withAlpha(palette.moduleScrollHandleB(), Math.round(175.0f + reveal * 60.0f));
        LayoutRender2D.roundedQuad(scrollbarX, scrollbarY, scrollbarW, scrollbarH,
                scrollbarW * 0.5f, trackA, trackB, trackB, trackA);
        LayoutRender2D.roundedQuad(scrollbarX, scrollbarThumbY, scrollbarW, scrollbarThumbH,
                scrollbarW * 0.5f, handleA, handleB, handleB, handleA);
    }

    private void scrollToMouse(float mouseY) {
        if (!scrollbarVisible || maxScroll <= 0.0f) return;
        float span = scrollbarH - scrollbarThumbH;
        if (span <= 0.0f) return;
        float thumbTop = clamp(mouseY - scrollbarDragOffset, scrollbarY, scrollbarY + span);
        float ratio = (thumbTop - scrollbarY) / span;
        scrollTarget = -maxScroll * ratio;
        scroll = scrollTarget;
        scrollVelocity = 0.0f;
    }

    private void resetScroll() {
        scroll = 0.0f;
        scrollTarget = 0.0f;
        scrollVelocity = 0.0f;
        scrollbarDragging = false;
    }

    /** Draws the accent through the card's own rounded silhouette instead of an exposed bar. */
    private static void drawClippedLeadingAccent(float x, float y, float width, float height,
                                                 float radius, int color, float reveal) {
        float amount = clamp(reveal, 0.0f, 1.0f);
        if (amount <= 0.001f) return;
        boolean clipped = ScissorFunction.pushRaw(x, y, 2.0f + 2.0f * amount, height);
        try {
            ClickGuiRenderer.drawRoundedRect(x, y, width, height, radius, color);
        } finally {
            if (clipped) ScissorFunction.pop();
        }
    }

    private static float relationTagWidth(CategoryType type, float size) {
        if (type == null || type == CategoryType.DEFAULT) return 0.0f;
        return ClickGuiRenderer.textWidth(ClickGuiRenderer.getOnestMedium(), relationLabel(type), size) + 13.0f;
    }

    /** Relations are metadata, so use a light dot-label rather than a competing pill. */
    private static void drawRelationTag(CategoryType type, float x, float y, float size, int color) {
        if (type == null || type == CategoryType.DEFAULT) return;
        Renderer2D.COLOR.circle(x + 2.4f, y + size * 0.54f, 2.2f, color);
        ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), relationLabel(type),
                x + 8.0f, y, size, color, false);
    }

    private static float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }

    private int firstVisibleVirtualRow(float rowY, float rowHeight, int size) {
        if (size <= 0 || rowHeight <= 0.0f) return 0;
        return Math.max(0, Math.min(size, (int) Math.floor((contentViewportY - rowY) / rowHeight) - 1));
    }

    private int lastVisibleVirtualRow(float rowY, float rowHeight, int size) {
        if (size <= 0 || rowHeight <= 0.0f) return 0;
        return Math.max(0, Math.min(size, (int) Math.ceil((contentViewportBottom - rowY) / rowHeight) + 1));
    }
    private enum Category {
        DISPLAY("display", "Display", "Map chrome, coordinates, arrow and footprints", "map"),
        TERRAIN("terrain", "Terrain", "Terrain colors, lighting and chunk updates", "layers"),
        WAYPOINTS("waypoints", "Waypoints", "Waypoint rendering and visibility", "map-pinned"),
        CAVE("cave", "Cave", "Xaero cave mode, depth and start layer", "land-plot"),
        PLAYERS("players", "Players & Radar", "Xaero tracked players, radar entities and claims", "users-round"),
        TARGETS("targets", "Targets", "Target players and live location sources", "crosshair"),
        TRIANGULATION("triangulation", "Triangulation", "Collection mode, resolve quality and live telemetry", "radar"),
        MAPLINK("maplink", "MapLink", "Per-server web-map profiles and network status", "map"),
        NAVIGATION("navigation", "Navigation", "Teleport and navigation behaviour", "route"),
        ADVANCED("advanced", "Advanced", "Loading budget and technical options", "settings-2");

        final String id;
        final String fallbackLabel;
        final String fallbackDescription;
        final String icon;
        Category(String id, String fallbackLabel, String fallbackDescription, String icon) {
            this.id = id;
            this.fallbackLabel = fallbackLabel;
            this.fallbackDescription = fallbackDescription;
            this.icon = icon;
        }
        String label() { return tr("gui.combatant.map.category." + id, fallbackLabel); }
        String description() { return tr("gui.combatant.map.category." + id + ".description", fallbackDescription); }
    }

    private record SolidBrowserLayout(float width, float height,
                                      float navWidth, float collectionWidth,
                                      float detailWidth, float detailX,
                                      float headerHeight, float bodyHeight,
                                      float detailViewportWidth, float detailViewportHeight,
                                      float navX, float navStartY, float navRowWidth,
                                      float navRowHeight, float navRowGap,
                                      float toolbarLeftX, float toolbarY,
                                      float searchWidth, float searchHeight,
                                      float closeX, float closeY, float closeWidth, float closeHeight,
                                      float detailHeaderX, float detailHeaderY,
                                      float detailHeaderWidth, float detailHeaderHeight,
                                      float contentX, float contentY, float contentWidth, float contentHeight,
                                      float navSeparatorX, float navSeparatorY, float navSeparatorHeight,
                                      float headerSeparatorX, float headerSeparatorY, float headerSeparatorWidth) {
        static SolidBrowserLayout of(float width, float height) {
            float nav = width * 268.0f / DESIGN_WIDTH;
            float collection = 0.0f;
            float header = HEADER_HEIGHT;
            float detail = Math.max(0.0f, width - nav - collection);
            float body = Math.max(0.0f, height - header);
            float navGap = 6.0f;
            float navRowHeight = Math.max(32.0f, Math.min(46.0f,
                    (body - BASE_INSET * 2.0f - navGap * (Category.values().length - 1))
                            / Category.values().length));
            float navStackHeight = Category.values().length * navRowHeight
                    + (Category.values().length - 1) * navGap;
            float navStartY = header + Math.max(BASE_INSET, (body - navStackHeight) * 0.5f);
            float detailViewportWidth = Math.max(240.0f, detail - BASE_INSET * 2.0f);
            float detailHeaderY = header + BASE_INSET;
            float detailHeaderHeight = 44.0f;
            float contentY = detailHeaderY + detailHeaderHeight + 11.0f;
            return new SolidBrowserLayout(
                    width, height, nav, collection, detail, nav + collection, header, body,
                    detailViewportWidth,
                    Math.max(160.0f, body - BASE_INSET * 2.0f),
                    BASE_INSET, navStartY, Math.max(1.0f, nav - BASE_INSET * 2.0f),
                    navRowHeight, navGap,
                    BASE_INSET, (header - SEARCH_HEIGHT) * 0.5f,
                    SEARCH_WIDTH, SEARCH_HEIGHT,
                    Math.max(0.0f, detail - BASE_INSET - CLOSE_SIZE),
                    (header - CLOSE_SIZE) * 0.5f, CLOSE_SIZE, CLOSE_SIZE,
                    BASE_INSET, detailHeaderY, detailViewportWidth, detailHeaderHeight,
                    BASE_INSET, contentY, detailViewportWidth,
                    Math.max(120.0f, height - contentY - BASE_INSET),
                    nav - 1.0f, BASE_INSET, Math.max(0.0f, height - BASE_INSET * 2.0f),
                    nav + collection + BASE_INSET, header - 1.0f,
                    Math.max(0.0f, detail - BASE_INSET * 2.0f)
            );
        }

        LinkedHashMap<String, Object> toProps() {
            LinkedHashMap<String, Object> props = new LinkedHashMap<>();
            props.put("width", width);
            props.put("height", height);
            props.put("navWidth", navWidth);
            props.put("collectionWidth", collectionWidth);
            props.put("detailWidth", detailWidth);
            props.put("headerHeight", headerHeight);
            props.put("collectionX", navWidth);
            props.put("detailX", detailX);
            props.put("bodyHeight", bodyHeight);
            props.put("detailViewportWidth", detailViewportWidth);
            props.put("detailViewportHeight", detailViewportHeight);
            props.put("navStartY", navStartY);
            props.put("navX", navX);
            props.put("navRowWidth", navRowWidth);
            props.put("navRowHeight", navRowHeight);
            props.put("navRowGap", navRowGap);
            props.put("toolbarLeftX", toolbarLeftX);
            props.put("toolbarY", toolbarY);
            props.put("searchWidth", searchWidth);
            props.put("searchHeight", searchHeight);
            props.put("closeX", closeX);
            props.put("closeY", closeY);
            props.put("closeWidth", closeWidth);
            props.put("closeHeight", closeHeight);
            props.put("detailHeaderX", detailHeaderX);
            props.put("detailHeaderY", detailHeaderY);
            props.put("detailHeaderWidth", detailHeaderWidth);
            props.put("detailHeaderHeight", detailHeaderHeight);
            props.put("contentX", contentX);
            props.put("contentY", contentY);
            props.put("contentWidth", contentWidth);
            props.put("contentHeight", contentHeight);
            props.put("navSeparatorX", navSeparatorX);
            props.put("navSeparatorY", navSeparatorY);
            props.put("navSeparatorHeight", navSeparatorHeight);
            props.put("headerSeparatorX", headerSeparatorX);
            props.put("headerSeparatorY", headerSeparatorY);
            props.put("headerSeparatorWidth", headerSeparatorWidth);
            return props;
        }
    }
    private record Entry(Setting setting, Category category, String searchText) {}
    private record Hit(Setting setting, float x, float y, float width, float height) {}
    private record CategoryHit(Category category, float x, float y, float width, float height) {
        boolean contains(float mx, float my) { return inside(mx, my, x, y, width, height); }
    }
    private static final class ExternalIdentifierSetValue extends SetValue {
        private final Supplier<Set<Identifier>> getter;
        private final Consumer<Set<Identifier>> setter;
        private ExternalIdentifierSetValue(String name, Supplier<Set<Identifier>> getter, Consumer<Set<Identifier>> setter) {
            super(name);
            this.getter = getter;
            this.setter = setter;
        }
        @Override public Set<String> get() {
            Set<Identifier> current = getter.get();
            LinkedHashSet<String> out = new LinkedHashSet<>();
            if (current != null) for (Identifier id : current) if (id != null) out.add(id.toString());
            return out;
        }
        @Override public void set(Set<String> values) {
            LinkedHashSet<Identifier> parsed = new LinkedHashSet<>();
            if (values != null) for (String raw : values) {
                Identifier id = raw == null ? null : Identifier.tryParse(raw.trim());
                if (id != null) parsed.add(id);
            }
            setter.accept(parsed);
        }
        @Override public Object toJson() { return new ArrayList<>(get()); }
        @Override public void fromJson(Object json) {
            if (!(json instanceof List<?> list)) return;
            LinkedHashSet<String> values = new LinkedHashSet<>();
            for (Object value : list) if (value instanceof String text) values.add(text);
            set(values);
        }
    }

    private static final class ExternalCaveStartValue extends NumberValue<Integer> {
        private final Supplier<Integer> getter;
        private final Consumer<Integer> setter;
        private ExternalCaveStartValue(String name, Supplier<Integer> getter, Consumer<Integer> setter) {
            super(name, getter.get(), -64, 320);
            this.getter = getter;
            this.setter = setter;
        }
        @Override public Integer get() { return getter.get(); }
        @Override public void set(Integer value) { setter.accept(value == null ? 320 : value); }
        @Override public Object toJson() { return get(); }
        @Override public void fromJson(Object json) { if (json instanceof Number n) set(n.intValue()); }
        @Override public String toDisplay() {
            return get() >= 320 ? tr("gui.combatant.map.common.auto", "Auto") : Integer.toString(get());
        }
    }

    private interface DynamicSearchEntry {
        String dynamicSearchText();
    }

    private static final class SectionHeaderSetting extends Setting {
        private final String title;
        private final String description;

        private SectionHeaderSetting(String title, String description) {
            this(title, description, null);
        }

        private SectionHeaderSetting(String title, String description, Supplier<Boolean> visible) {
            super(title == null ? "" : title);
            this.title = title == null ? "" : title;
            this.description = description == null ? "" : description;
            setI18nEnabled(false, false);
            if (visible != null) visibleWhen(visible);
        }

        @Override
        public void render(float x, float y, float width, float mouseX, float mouseY) {
            SettingsGuiPalette palette = SettingsGuiPalette.current();
            ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), title,
                    x + 4.0f, y + 2.0f, 19.5f, palette.panelText(), false);
            if (!description.isBlank()) {
                ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), description,
                        x + 4.0f, y + 25.0f, 13.5f, palette.panelMuted(), false);
            }
            ClickGuiRenderer.drawRect(x + 4.0f, y + 48.0f, Math.max(1.0f, width - 8.0f), 1.0f,
                    SettingsGuiPalette.withAlpha(palette.panelText(), 26));
        }

        @Override public void mouseClicked(double mx, double my, int button) {}
        @Override public float getHeight() { return description.isBlank() ? 34.0f : 52.0f; }
    }

    private static final class DuplexStatusSetting extends Setting implements DynamicSearchEntry {
        private DuplexStatusSetting() {
            super(tr("gui.combatant.map.duplex.status.title", "Duplex status"));
        }

        @Override
        public void render(float x, float y, float width, float mouseX, float mouseY) {
            SettingsGuiPalette palette = SettingsGuiPalette.current();
            DuplexLocalConfig config = DuplexLocalConfig.get();
            DuplexRuntime runtime = MapLocationRuntime.get().duplex();
            DuplexState state = runtime.state();
            boolean enabled = config.enabled();

            int statusColor = duplexStatusColor(enabled, state, runtime.transportConnected());
            String status = duplexStatusLabel(enabled, state, runtime.transportConnected());
            String description = duplexStatusDescription(enabled, state);

            ClickGuiRenderer.drawRoundedRect(x + 4.0f, y + 2.0f, Math.max(1.0f, width - 8.0f), 98.0f, 10.0f,
                    SettingsGuiPalette.withAlpha(palette.panelText(), 8));
            ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(),
                    tr("gui.combatant.map.duplex.status.title", "Duplex status"),
                    x + 17.0f, y + 13.0f, 17.0f, palette.panelText(), false);

            Renderer2D.COLOR.circle(x + 19.5f, y + 43.0f, 3.0f,
                    SettingsGuiPalette.withAlpha(statusColor, enabled ? 245 : 145));
            ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), status,
                    x + 30.0f, y + 34.0f, 14.2f, statusColor, false);
            ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(),
                    ClickGuiRenderer.fitText(ClickGuiRenderer.getOnestMedium(), description, 12.1f,
                            Math.max(1.0f, width - 42.0f)),
                    x + 17.0f, y + 57.0f, 12.1f, palette.panelMuted(), false);

            String meta = runtime.transportEndpoint()
                    + "  ·  " + runtime.localBearingCount() + " "
                    + tr("gui.combatant.map.duplex.status.local", "local rays")
                    + "  ·  " + runtime.remoteBearingCount() + " "
                    + tr("gui.combatant.map.duplex.status.remote", "courier rays")
                    + "  ·  " + runtime.estimateCount() + " "
                    + tr("gui.combatant.map.duplex.status.estimates", "solutions");
            if (runtime.peerVerified() && runtime.peerAgeMs() >= 0L) {
                meta += "  ·  " + Math.max(0L, runtime.peerAgeMs() / 1000L) + "s";
            }
            ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(),
                    ClickGuiRenderer.fitText(ClickGuiRenderer.getOnestMedium(), meta, 11.2f,
                            Math.max(1.0f, width - 42.0f)),
                    x + 17.0f, y + 78.0f, 11.2f, palette.panelMuted(), false);

            String error = runtime.transportLastError();
            if (enabled && state == DuplexState.DEGRADED && error != null && !error.isBlank()) {
                ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(),
                        ClickGuiRenderer.fitText(ClickGuiRenderer.getOnestMedium(), error, 10.8f,
                                Math.max(1.0f, width * 0.38f)),
                        x + width - Math.min(width * 0.38f, 260.0f) - 16.0f, y + 14.0f,
                        10.8f, SettingsGuiPalette.withAlpha(statusColor, 190), false);
            }
        }

        @Override public void mouseClicked(double mx, double my, int button) {}
        @Override public float getHeight() { return 104.0f; }

        @Override
        public String dynamicSearchText() {
            DuplexRuntime runtime = MapLocationRuntime.get().duplex();
            return (runtime.state().name() + " " + runtime.transportEndpoint() + " "
                    + runtime.transportLastError()).toLowerCase(Locale.ROOT);
        }

        private static int duplexStatusColor(boolean enabled, DuplexState state, boolean connected) {
            if (!enabled) return 0xFF858A94;
            if (state == DuplexState.READY) return 0xFF72E69A;
            if (state == DuplexState.HANDSHAKING || state == DuplexState.SESSION_VERIFY || connected) return 0xFFFFD36A;
            if (state == DuplexState.DEGRADED) return 0xFFFF8B6A;
            return 0xFF9AA6B6;
        }

        private static String duplexStatusLabel(boolean enabled, DuplexState state, boolean connected) {
            if (!enabled) return tr("gui.combatant.map.duplex.status.disabled", "Disabled");
            if (state == DuplexState.READY) return tr("gui.combatant.map.duplex.status.ready", "Courier connected");
            if (state == DuplexState.HANDSHAKING || state == DuplexState.SESSION_VERIFY || connected) {
                return tr("gui.combatant.map.duplex.status.verifying", "Verifying courier session");
            }
            if (state == DuplexState.DEGRADED) return tr("gui.combatant.map.duplex.status.degraded", "Courier unavailable");
            return tr("gui.combatant.map.duplex.status.waiting", "Waiting for courier");
        }

        private static String duplexStatusDescription(boolean enabled, DuplexState state) {
            if (!enabled) return tr("gui.combatant.map.duplex.status.disabled.description",
                    "Enable the listener when a courier client is running on the same computer.");
            if (state == DuplexState.READY) return tr("gui.combatant.map.duplex.status.ready.description",
                    "Session and world match. Locator bearings are being paired.");
            if (state == DuplexState.HANDSHAKING || state == DuplexState.SESSION_VERIFY) {
                return tr("gui.combatant.map.duplex.status.verifying.description",
                        "Local transport is connected; checking the server and dimension.");
            }
            if (state == DuplexState.DEGRADED) return tr("gui.combatant.map.duplex.status.degraded.description",
                    "Open the same server and dimension in the courier client.");
            return tr("gui.combatant.map.duplex.status.waiting.description",
                    "The listener is ready; start the courier build in another client.");
        }
    }

    private final class TriangulationModeSetting extends Setting {
        private static final float CARD_H = 86.0f;
        private final List<ModeHit> modeHits = new ArrayList<>();
        private final Map<MapTriangulationMode, Float> hoverAnims = new LinkedHashMap<>();
        private final Map<MapTriangulationMode, Float> selectionAnims = new LinkedHashMap<>();
        private final Map<MapTriangulationMode, Float> pressAnims = new LinkedHashMap<>();

        private TriangulationModeSetting() {
            super("Triangulation mode");
            setI18nEnabled(false, false);
        }

        @Override
        public void render(float x, float y, float width, float mouseX, float mouseY) {
            modeHits.clear();
            SettingsGuiPalette palette = SettingsGuiPalette.current();
            MapTriangulationMode selected = MapTriangulationConfig.get().mode();
            ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(),
                    tr("gui.combatant.map.triangulation.mode.title", "Tracking mode"),
                    x + 4.0f, y + 2.0f, 21.0f, palette.panelText(), false);
            ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(),
                    tr("gui.combatant.map.triangulation.mode.description", "Choose what the resolver is allowed to collect. This does not change MapLink exact positions."),
                    x + 4.0f, y + 27.0f, 13.5f, palette.panelMuted(), false);

            float gap = 10.0f;
            float cardW = Math.max(120.0f, (width - gap * 2.0f) / 3.0f);
            float cardY = y + 52.0f;
            MapTriangulationMode[] modes = {MapTriangulationMode.OFF, MapTriangulationMode.DATA_MINING, MapTriangulationMode.TARGETED};
            for (int i = 0; i < modes.length; i++) {
                MapTriangulationMode mode = modes[i];
                float cx = x + i * (cardW + gap);
                boolean hover = inside(mouseX, mouseY, cx, cardY, cardW, CARD_H);
                boolean active = selected == mode;
                float dt = AnimationUtility.deltaTime();
                float hoverAnim = animateToward(hoverAnims.getOrDefault(mode, 0.0f), hover ? 1.0f : 0.0f,
                        dt, hover ? 14.0f : 10.0f);
                float activeAnim = animateToward(selectionAnims.getOrDefault(mode, active ? 1.0f : 0.0f),
                        active ? 1.0f : 0.0f, dt, 12.0f);
                float pressAnim = animateToward(pressAnims.getOrDefault(mode, 0.0f), 0.0f, dt, 18.0f);
                hoverAnims.put(mode, hoverAnim);
                selectionAnims.put(mode, activeAnim);
                pressAnims.put(mode, pressAnim);

                float hoverEase = easeOutCubic(hoverAnim);
                float visualY = cardY - hoverEase * 1.15f + pressAnim * 1.6f;
                int neutral = SettingsGuiPalette.withAlpha(palette.panelText(),
                        Math.round(8.0f + hoverEase * 15.0f));
                int selectedBg = SettingsGuiPalette.withAlpha(Theme.theme().accent(),
                        Math.round(22.0f + hoverEase * 15.0f));
                int bg = SettingsGuiPalette.mix(neutral, selectedBg, activeAnim);
                ClickGuiRenderer.drawRoundedRect(cx, visualY, cardW, CARD_H, 9.0f, bg);
                drawClippedLeadingAccent(cx, visualY, cardW, CARD_H, 9.0f,
                        SettingsGuiPalette.withAlpha(Theme.theme().accent(), Math.round(225.0f * activeAnim)),
                        activeAnim);
                String title = triangulationModeTitle(mode);
                String desc = triangulationModeDescription(mode);
                ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), title,
                        cx + 13.0f, visualY + 10.0f, 17.0f,
                        SettingsGuiPalette.mix(palette.panelText(), Theme.theme().accent(), activeAnim), false);
                drawWrappedText(desc, cx + 13.0f, visualY + 33.0f, cardW - 26.0f, 13.0f, palette.panelMuted(), 2);
                modeHits.add(new ModeHit(mode, cx, cardY, cardW, CARD_H));
            }
        }

        @Override
        public void mouseClicked(double mx, double my, int button) {
            if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return;
            for (ModeHit hit : modeHits) {
                if (!inside((float) mx, (float) my, hit.x, hit.y, hit.w, hit.h)) continue;
                MapTriangulationConfig.get().setMode(hit.mode);
                pressAnims.put(hit.mode, 1.0f);
                return;
            }
        }

        @Override public float getHeight() { return 148.0f; }

        private record ModeHit(MapTriangulationMode mode, float x, float y, float w, float h) {}
    }

    private static String triangulationModeTitle(MapTriangulationMode mode) {
        return switch (mode) {
            case OFF -> tr("gui.combatant.map.triangulation.mode.off", "Off");
            case DATA_MINING -> tr("gui.combatant.map.triangulation.mode.data_mining", "Data Mining");
            case TARGETED -> tr("gui.combatant.map.triangulation.mode.targeted", "Targeted");
        };
    }

    private static String triangulationModeDescription(MapTriangulationMode mode) {
        return switch (mode) {
            case OFF -> tr("gui.combatant.map.triangulation.mode.off.description", "Do not collect locator bearings or run the resolver.");
            case DATA_MINING -> tr("gui.combatant.map.triangulation.mode.data_mining.description", "Track all eligible players at a bounded background rate.");
            case TARGETED -> tr("gui.combatant.map.triangulation.mode.targeted.description", "Spend resolver budget only on players selected in Targets.");
        };
    }

    private static void drawWrappedText(String text, float x, float y, float maxWidth, float size, int color, int maxLines) {
        if (text == null || text.isBlank()) return;
        String[] words = text.trim().split("\\s+");
        StringBuilder line = new StringBuilder();
        int lines = 0;
        for (String word : words) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            float width = ClickGuiRenderer.textWidth(ClickGuiRenderer.getOnestMedium(), candidate, size);
            if (width > maxWidth && !line.isEmpty()) {
                ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), line.toString(), x, y + lines * (size + 3.0f), size, color, false);
                lines++;
                if (lines >= maxLines) return;
                line.setLength(0);
                line.append(word);
            } else {
                if (!line.isEmpty()) line.append(' ');
                line.append(word);
            }
        }
        if (!line.isEmpty() && lines < maxLines) {
            ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), line.toString(), x, y + lines * (size + 3.0f), size, color, false);
        }
    }

    private final class TargetPlayersSetting extends Setting implements DynamicSearchEntry {
        private static final float ROW_H = 54.0f;
        private final List<TargetHit> rowHits = new ArrayList<>();
        private final Map<UUID, Float> hoverAnims = new LinkedHashMap<>();
        private final Map<UUID, Float> selectionAnims = new LinkedHashMap<>();
        private final Map<UUID, Float> pressAnims = new LinkedHashMap<>();
        private long cachedFrame = Long.MIN_VALUE;
        private List<TargetRow> cachedRows = List.of();
        private String cachedFilter = null;
        private List<TargetRow> cachedFilteredRows = List.of();
        private boolean inputFocused;
        private long inputFocusedAtMs;
        private String playerInput = "";
        private String inputMessage = "";
        private long inputMessageUntil;

        private boolean inputContains(float mouseX, float mouseY) {
            return inside(mouseX, mouseY, inputX, inputY, inputW, inputH);
        }
        private float inputX, inputY, inputW, inputH;
        private float addX, addY, addW, addH;
        private float inputHoverAnim;
        private float inputFocusAnim;
        private float addHoverAnim;
        private float addPressAnim;

        private TargetPlayersSetting() { super(tr("gui.combatant.map.targets.title", "Targets")); }

        @Override
        public void render(float x, float y, float width, float mouseX, float mouseY) {
            rowHits.clear();
            var palette = SettingsGuiPalette.current();
            List<TargetRow> players = displayTargetRows();
            List<TargetRow> allPlayers = targetRowsForFrame();

            ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), tr("gui.combatant.map.targets.title", "Targets"),
                    x + 6.0f, y + 4.0f, 20.0f, palette.panelText(), false);
            String summary = targetedCount(allPlayers) + " " + tr("gui.combatant.map.targets.targeted", "targeted")
                    + "  ·  " + onlineCount() + " " + tr("gui.combatant.map.targets.online", "online")
                    + "  ·  " + triangulationModeTitle(MapTriangulationConfig.get().mode());
            ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), summary,
                    x + 6.0f, y + 29.0f, 13.2f, palette.panelMuted(), false);

            inputX = x + 4.0f;
            inputY = y + 52.0f;
            inputH = 34.0f;
            addW = 34.0f;
            addH = 34.0f;
            addX = x + width - addW - 4.0f;
            addY = inputY;
            inputW = Math.max(80.0f, addX - inputX - 8.0f);
            boolean inputHover = inside(mouseX, mouseY, inputX, inputY, inputW, inputH);
            boolean addHover = inside(mouseX, mouseY, addX, addY, addW, addH);
            float inputDt = AnimationUtility.deltaTime();
            inputHoverAnim = animateToward(inputHoverAnim, inputHover ? 1.0f : 0.0f,
                    inputDt, inputHover ? 14.0f : 10.0f);
            inputFocusAnim = animateToward(inputFocusAnim, inputFocused ? 1.0f : 0.0f,
                    inputDt, 13.0f);
            addHoverAnim = animateToward(addHoverAnim, addHover ? 1.0f : 0.0f,
                    inputDt, addHover ? 14.0f : 10.0f);
            addPressAnim = animateToward(addPressAnim, 0.0f, inputDt, 18.0f);
            float addVisualY = addY - easeOutCubic(addHoverAnim) * 0.7f + addPressAnim * 1.2f;
            ClickGuiRenderer.drawRoundedRect(inputX, inputY, inputW, inputH, 8.0f,
                    SettingsGuiPalette.withAlpha(palette.panelText(),
                            Math.round(7.0f + inputHoverAnim * 4.0f + inputFocusAnim * 7.0f)));
            Renderer2D.COLOR.roundedRectStroke(inputX, inputY, inputW, inputH, 8.0f, 1.0f, 0.7f,
                    SettingsGuiPalette.withAlpha(
                            SettingsGuiPalette.mix(palette.panelStroke(), Theme.theme().accent(), inputFocusAnim),
                            Math.round(68.0f + inputFocusAnim * 82.0f)));
            String shown = playerInput.isEmpty()
                    ? (inputFocused ? "" : tr("gui.combatant.map.targets.input.placeholder", "Player nickname"))
                    : playerInput;
            int inputColor = playerInput.isEmpty() ? palette.panelMuted() : palette.panelText();
            boolean clipped = ScissorFunction.pushRaw(inputX + 9.0f, inputY + 2.0f, inputW - 18.0f, inputH - 4.0f);
            ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), shown,
                    inputX + 10.0f, inputY + 9.0f, 13.5f, inputColor, false);
            long inputNow = System.currentTimeMillis();
            boolean cursorVisible = inputFocused
                    && (inputNow - inputFocusedAtMs < 650L || ((inputNow / 500L) & 1L) == 0L);
            if (cursorVisible) {
                float cursorX = inputX + 10.0f + ClickGuiRenderer.textWidth(ClickGuiRenderer.getOnestMedium(), playerInput, 13.5f) + 1.0f;
                ClickGuiRenderer.drawRect(cursorX, inputY + 8.0f, 1.0f, 17.0f, Theme.theme().accent());
            }
            if (clipped) ScissorFunction.pop();

            ClickGuiRenderer.drawRoundedRect(addX, addVisualY, addW, addH, 8.0f,
                    SettingsGuiPalette.withAlpha(Theme.theme().accent(), Math.round(28.0f + addHoverAnim * 20.0f)));
            Renderer2D.COLOR.svg("user-plus", addX + 8.0f, addVisualY + 8.0f, 18.0f, 18.0f,
                    SvgRenderOptions.overrideColor(Theme.theme().accent()));

            float rowY = y + 100.0f;
            if (!inputMessage.isBlank() && System.currentTimeMillis() < inputMessageUntil) {
                ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), inputMessage,
                        x + 6.0f, y + 89.0f, 11.5f, 0xFFFF818A, false);
                rowY += 13.0f;
            }
            if (players.isEmpty()) {
                ClickGuiRenderer.drawRoundedRect(x + 4.0f, rowY, Math.max(1.0f, width - 8.0f), 56.0f, 9.0f,
                        SettingsGuiPalette.withAlpha(palette.panelText(), 6));
                ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(),
                        tr("gui.combatant.map.targets.empty", "No players"), x + 16.0f, rowY + 11.0f,
                        15.0f, palette.panelText(), false);
                drawWrappedText(tr("gui.combatant.map.targets.empty.description",
                                "Add a nickname above."),
                        x + 16.0f, rowY + 31.0f, width - 32.0f, 11.7f, palette.panelMuted(), 2);
                return;
            }

            int first = firstVisibleVirtualRow(rowY, ROW_H, players.size());
            int last = lastVisibleVirtualRow(rowY, ROW_H, players.size());
            long now = System.currentTimeMillis();
            for (int i = first; i < last; i++) {
                TargetRow row = players.get(i);
                boolean targeted = MapTriangulationConfig.get().isTargeted(row.id(), row.name());
                float ry = rowY + i * ROW_H;
                float rw = Math.max(1.0f, width - 8.0f);
                boolean hover = inside(mouseX, mouseY, x + 4.0f, ry, rw, ROW_H - 4.0f);
                float dt = AnimationUtility.deltaTime();
                float hoverAnim = animateToward(hoverAnims.getOrDefault(row.id(), 0.0f), hover ? 1.0f : 0.0f,
                        dt, hover ? 14.0f : 10.0f);
                float activeAnim = animateToward(selectionAnims.getOrDefault(row.id(), targeted ? 1.0f : 0.0f),
                        targeted ? 1.0f : 0.0f, dt, 13.0f);
                float pressAnim = animateToward(pressAnims.getOrDefault(row.id(), 0.0f), 0.0f, dt, 18.0f);
                hoverAnims.put(row.id(), hoverAnim);
                selectionAnims.put(row.id(), activeAnim);
                pressAnims.put(row.id(), pressAnim);

                float hoverEase = easeOutCubic(hoverAnim);
                float visualY = ry - hoverEase * 0.8f + pressAnim * 1.25f;

                int relationColor = relationColor(row.name());
                int idleBase = SettingsGuiPalette.withAlpha(palette.controlSurface(), Math.round(78.0f + hoverEase * 32.0f));
                int targetBase = SettingsGuiPalette.withAlpha(
                        SettingsGuiPalette.mix(palette.controlSurface(), Theme.theme().accent(), 0.18f),
                        Math.round(145.0f + hoverEase * 24.0f));
                int base = SettingsGuiPalette.mix(idleBase, targetBase, activeAnim);
                ClickGuiRenderer.drawRoundedRect(x + 4.0f, visualY, rw, ROW_H - 4.0f, 9.0f, base);
                drawClippedLeadingAccent(x + 4.0f, visualY, rw, ROW_H - 4.0f, 9.0f,
                        SettingsGuiPalette.withAlpha(Theme.theme().accent(), Math.round(235.0f * activeAnim)),
                        activeAnim);

                float head = 32.0f;
                float headX = x + 13.0f;
                float headY = visualY + 9.0f;
                renderTargetHead(row, headX, headY, head, palette);
                if (row.online()) {
                    ClickGuiRenderer.drawCircle(headX + head - 2.5f, headY + head - 2.5f, 3.0f, 0xFF65E48B);
                    Renderer2D.COLOR.circleStroke(headX + head - 2.5f, headY + head - 2.5f, 3.9f, 0.7f, 0.8f,
                            SettingsGuiPalette.withAlpha(palette.panelBgRight(), 220));
                }

                float textX = headX + head + 10.0f;
                float actionSize = 28.0f;
                float actionX = x + width - actionSize - 12.0f;
                float actionY = visualY + 11.0f;
                float metaRight = actionX - 12.0f;
                float metaWidth = Math.min(260.0f, Math.max(130.0f, width * 0.35f));
                float metaX = metaRight - metaWidth;
                float nameWidth = Math.max(88.0f, metaX - textX - 12.0f);
                CategoryType relation = CategoryService.get(row.name());
                float tagW = Math.min(92.0f, relationTagWidth(relation, 10.8f));
                float tagX = textX + Math.max(0.0f, nameWidth - tagW);
                float displayNameW = tagW > 0.0f ? Math.max(42.0f, tagX - textX - 9.0f) : nameWidth;
                ClickGuiRichTextRenderer.draw(row.displayName(), textX, visualY + 16.0f, displayNameW, 15.2f,
                        palette.panelText(), 1.0f, false);
                drawRelationTag(relation, tagX, visualY + 19.0f, 10.8f, relationColor);

                String meta = targetMeta(row, now);
                String fitted = ClickGuiRenderer.fitText(ClickGuiRenderer.getOnestMedium(), meta, 11.8f, metaWidth);
                float metaTextW = ClickGuiRenderer.textWidth(ClickGuiRenderer.getOnestMedium(), fitted, 11.8f);
                ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), fitted,
                        metaRight - metaTextW, visualY + 18.5f, 11.8f, palette.panelMuted(), false);

                int actionColor = SettingsGuiPalette.mix(palette.panelText(), Theme.theme().accent(), activeAnim);
                ClickGuiRenderer.drawRoundedRect(actionX, actionY, actionSize, actionSize, 8.0f,
                        SettingsGuiPalette.withAlpha(actionColor, Math.round(10.0f + activeAnim * 30.0f + hoverEase * 9.0f)));
                Renderer2D.COLOR.svg(activeAnim > 0.5f ? "check" : "user-plus", actionX + 6.0f, actionY + 6.0f, 16.0f, 16.0f,
                        SvgRenderOptions.overrideColor(actionColor));
                rowHits.add(new TargetHit(row.id(), row.name(), x + 4.0f, ry, rw, ROW_H - 4.0f));
            }
        }

        @Override
        public void mouseClicked(double mx, double my, int button) {
            if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return;
            if (inside((float) mx, (float) my, inputX, inputY, inputW, inputH)) {
                searchFocused = false;
                inputFocused = true;
                inputFocusedAtMs = System.currentTimeMillis();
                return;
            }
            if (inside((float) mx, (float) my, addX, addY, addW, addH)) {
                searchFocused = false;
                inputFocused = true;
                inputFocusedAtMs = System.currentTimeMillis();
                addPressAnim = 1.0f;
                commitInput();
                return;
            }
            inputFocused = false;
            for (TargetHit hit : rowHits) {
                if (!inside((float) mx, (float) my, hit.x, hit.y, hit.w, hit.h)) continue;
                MapTriangulationConfig config = MapTriangulationConfig.get();
                if (config.isTargeted(hit.id, hit.name)) config.removeTargetedPlayer(hit.id, hit.name);
                else config.addTargetedPlayer(hit.id, hit.name);
                pressAnims.put(hit.id, 1.0f);
                cachedFrame = Long.MIN_VALUE;
                return;
            }
        }

        @Override
        public void mouseClickedOutside(double mx, double my, int button) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) inputFocused = false;
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (!inputFocused) return false;
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                inputFocused = false;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                commitInput();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !playerInput.isEmpty()) {
                playerInput = playerInput.substring(0, playerInput.length() - 1);
                inputFocusedAtMs = System.currentTimeMillis();
                return true;
            }
            if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && keyCode == GLFW.GLFW_KEY_V) {
                String clip = ClipboardUtil.get();
                if (clip != null && !clip.isBlank()) {
                    playerInput = ChatNameUtil.normalizeNickCandidate(clip);
                    if (playerInput.length() > 32) playerInput = playerInput.substring(0, 32);
                    inputFocusedAtMs = System.currentTimeMillis();
                }
                return true;
            }
            return true;
        }

        @Override
        public boolean charTyped(char chr, int modifiers) {
            if (!inputFocused) return false;
            if (!Character.isISOControl(chr) && playerInput.length() < 32 && (Character.isLetterOrDigit(chr) || chr == '_')) {
                playerInput += chr;
                inputFocusedAtMs = System.currentTimeMillis();
            }
            return true;
        }

        @Override
        public float getHeight() {
            List<TargetRow> rows = displayTargetRows();
            float status = !inputMessage.isBlank() && System.currentTimeMillis() < inputMessageUntil ? 13.0f : 0.0f;
            float body = rows.isEmpty() ? 56.0f : rows.size() * ROW_H;
            return 102.0f + status + body;
        }

        @Override
        public String dynamicSearchText() {
            StringBuilder out = new StringBuilder();
            for (TargetRow row : targetRowsForFrame()) out.append(' ').append(targetSearchText(row));
            return out.toString().toLowerCase(Locale.ROOT);
        }

        private void commitInput() {
            String clean = ChatNameUtil.normalizeNickCandidate(playerInput);
            if (!ChatNameUtil.isNickLike(clean)) {
                showInputMessage(tr("gui.combatant.map.targets.input.invalid", "Enter a valid player nickname"));
                return;
            }
            for (TargetRow row : targetRowsForFrame()) {
                if (!row.name().equalsIgnoreCase(clean)) continue;
                MapTriangulationConfig config = MapTriangulationConfig.get();
                if (!config.isTargeted(row.id(), row.name())) config.addTargetedPlayer(row.id(), row.name());
                playerInput = "";
                inputMessage = "";
                cachedFrame = Long.MIN_VALUE;
                return;
            }
            MapTriangulationConfig config = MapTriangulationConfig.get();
            if (!config.addTargetedName(clean) && config.isTargetedName(clean)) {
                showInputMessage(tr("gui.combatant.map.targets.input.exists", "This player is already targeted"));
                return;
            }
            playerInput = "";
            inputMessage = "";
            cachedFrame = Long.MIN_VALUE;
        }

        private void showInputMessage(String message) {
            inputMessage = message == null ? "" : message;
            inputMessageUntil = System.currentTimeMillis() + 2600L;
        }

        private List<TargetRow> displayTargetRows() {
            List<TargetRow> rows = targetRowsForFrame();
            String needle = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
            if (Objects.equals(cachedFilter, needle)) return cachedFilteredRows;
            cachedFilter = needle;
            if (needle.isEmpty() || "target targets players targeted locator triangulation tracking player list relations staff friend enemy цели игроки стафф друг враг".contains(needle)) {
                cachedFilteredRows = rows;
            } else {
                cachedFilteredRows = rows.stream().filter(row -> targetSearchText(row).contains(needle)).toList();
            }
            return cachedFilteredRows;
        }

        private List<TargetRow> targetRowsForFrame() {
            if (cachedFrame == renderFrameId) return cachedRows;
            cachedFrame = renderFrameId;
            cachedFilter = null;
            cachedFilteredRows = List.of();
            cachedRows = collectTargetRows();
            Set<UUID> alive = new LinkedHashSet<>();
            for (TargetRow row : cachedRows) alive.add(row.id());
            hoverAnims.keySet().retainAll(alive);
            selectionAnims.keySet().retainAll(alive);
            pressAnims.keySet().retainAll(alive);
            return cachedRows;
        }

        private String targetSearchText(TargetRow row) {
            StringBuilder out = new StringBuilder().append(row.id()).append(' ').append(row.name()).append(' ')
                    .append(row.displayName() == null ? "" : row.displayName().getString()).append(' ')
                    .append(relationLabel(CategoryService.get(row.name())));
            if (row.snapshot() != null) out.append(' ').append(row.snapshot().source().name());
            return LegacyTextUtil.stripLegacy(out.toString()).toLowerCase(Locale.ROOT);
        }

        private List<TargetRow> collectTargetRows() {
            LinkedHashMap<String, TargetRow> rows = new LinkedHashMap<>();
            var locations = PlayerLocationService.get().snapshot();
            for (Map.Entry<UUID, PlayerLocationSnapshot> entry : locations.bestByPlayer().entrySet()) {
                PlayerLocationSnapshot snapshot = entry.getValue();
                String name = snapshot == null || snapshot.playerName() == null ? "" : snapshot.playerName();
                String key = targetKey(entry.getKey(), name);
                rows.put(key, new TargetRow(entry.getKey(), name, targetDisplayName(null, name), false, snapshot, null));
            }

            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.getConnection() != null) {
                for (PlayerInfo info : mc.getConnection().getListedOnlinePlayers()) {
                    if (info == null || info.getProfile() == null || info.getProfile().id() == null) continue;
                    UUID id = info.getProfile().id();
                    String name = info.getProfile().name() == null ? "" : info.getProfile().name();
                    String key = targetKey(id, name);
                    TargetRow previous = rows.remove(targetKey(MapTriangulationConfig.offlineTargetUuid(name), name));
                    if (previous == null) previous = rows.get(key);
                    PlayerLocationSnapshot snapshot = previous == null ? locations.bestByPlayer().get(id) : previous.snapshot();
                    rows.put(key, new TargetRow(id, name, targetDisplayName(info, name), true, snapshot, info));
                }
            }

            Set<String> savedNames = MapTriangulationConfig.get().targetedPlayerNamesValue().get();
            if (savedNames != null) {
                for (String raw : savedNames) {
                    String name = ChatNameUtil.normalizeNickCandidate(raw);
                    if (!ChatNameUtil.isNickLike(name)) continue;
                    UUID id = MapTriangulationConfig.offlineTargetUuid(name);
                    String key = targetKey(id, name);
                    rows.putIfAbsent(key, new TargetRow(id, name, targetDisplayName(null, name), false, null, null));
                }
            }

            Set<String> saved = MapTriangulationConfig.get().targetedPlayersValue().get();
            if (saved != null) {
                for (String raw : saved) {
                    if (raw == null || raw.isBlank()) continue;
                    try {
                        UUID id = UUID.fromString(raw.trim());
                        String name = MapTriangulationConfig.get().nameForTarget(id);
                        String key = targetKey(id, name);
                        rows.putIfAbsent(key, new TargetRow(id, name, targetDisplayName(null, name), false, null, null));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }

            List<TargetRow> out = new ArrayList<>(rows.values());
            out.sort(Comparator
                    .comparing((TargetRow row) -> !MapTriangulationConfig.get().isTargeted(row.id(), row.name()))
                    .thenComparing((TargetRow row) -> !row.online())
                    .thenComparing((TargetRow row) -> row.snapshot() == null ? 1 : 0)
                    .thenComparing(row -> row.name().toLowerCase(Locale.ROOT)));
            return List.copyOf(out);
        }

        private String targetMeta(TargetRow row, long now) {
            var service = PlayerLocationService.get();
            var view = service.snapshot();
            PlayerLocationSnapshot snapshot = row.snapshot();
            PlayerLocationSnapshot locator = view.locatorSource(row.id());
            String locatorState;
            if (locator != null) {
                locatorState = switch (locator.source()) {
                    case LOCATOR_EXACT -> tr("gui.combatant.map.locator.present_exact", "Locator exact");
                    case LOCATOR_APPROXIMATE -> tr("gui.combatant.map.locator.present_chunk", "Locator chunk");
                    case LOCATOR_BEARING -> tr("gui.combatant.map.locator.present_bearing", "Locator bearing");
                    default -> tr("gui.combatant.map.locator.present", "Locator visible");
                };
            } else {
                PlayerLocationEvent lost = service.latestLocatorEvent(row.id(), PlayerLocationEventType.SOURCE_LOST);
                if (lost != null) {
                    long lostAge = Math.max(0L, now - lost.timestampMs());
                    locatorState = tr("gui.combatant.map.locator.lost", "Locator lost") + " " + (lostAge / 1000L) + "s";
                } else {
                    locatorState = tr("gui.combatant.map.locator.absent", "Locator not visible");
                }
            }
            if (snapshot == null) return locatorState + " · " + (row.online()
                    ? tr("gui.combatant.map.targets.meta.waiting", "waiting for coordinates")
                    : tr("gui.combatant.map.targets.meta.unseen", "saved"));
            long age = snapshot.ageMs(now);
            String ageText = age < 1000L ? tr("gui.combatant.map.common.now", "now") : (age / 1000L) + "s";
            String source = playerLocationSourceLabel(snapshot.source());
            if (!snapshot.hasPosition()) return locatorState + " · " + ageText;
            return locatorState + " · " + source + " · " + Math.round(snapshot.x()) + ", " + Math.round(snapshot.z())
                    + " · " + Math.round(snapshot.confidence() * 100.0) + "% · " + ageText;
        }

        private void renderTargetHead(TargetRow row, float x, float y, float size, SettingsGuiPalette palette) {
            float opacity = AnimationUtility.clamp01(ClickGuiRenderer.getRenderAlphaMultiplier());
            if (opacity <= 0.01f) return;
            Identifier skin = null;
            if (row.info() != null && row.info().getProfile() != null) skin = PlayerSkinResolver.resolveProfileSkin(row.info().getProfile());
            if (skin == null && !row.name().isBlank()) skin = SkinManager.getSkin(row.name());
            GuiGraphicsExtractor ctx = ViewportContext.getCurrentContext();
            if (skin != null && ctx != null) {
                PlayerHeadRenderer.drawRounded(ctx, x, y, size, 7.0f, skin,
                        new RenderColor(255, 255, 255, Math.round(255.0f * opacity)), true,
                        new RenderColor(255, 255, 255, Math.round(48.0f * opacity)), 0.9f, false);
            } else {
                Renderer2D.COLOR.roundedRect(x, y, size, size, 7.0f, 1.0f, SettingsGuiPalette.withAlpha(palette.panelMuted(), 32));
            }
        }

        private Component targetDisplayName(PlayerInfo info, String fallback) {
            Component display = null;
            Minecraft mc = Minecraft.getInstance();
            if (info != null && mc != null && mc.gui != null && mc.gui.hud != null && mc.gui.hud.getTabList() != null) {
                display = mc.gui.hud.getTabList().getNameForDisplay(info);
            }
            if (display == null && info != null) display = info.getTabListDisplayName();
            if (display == null || display.getString().isBlank()) display = Component.literal(
                    fallback == null || fallback.isBlank()
                            ? tr("gui.combatant.map.source.unknown", "unknown") : fallback);
            return LegacyTextUtil.convertLegacyCodesRobust(display);
        }

        private int targetedCount(List<TargetRow> rows) {
            int count = 0;
            for (TargetRow row : rows) if (MapTriangulationConfig.get().isTargeted(row.id(), row.name())) count++;
            return count;
        }

        private int onlineCount() {
            Minecraft mc = Minecraft.getInstance();
            return mc != null && mc.getConnection() != null ? mc.getConnection().getListedOnlinePlayers().size() : 0;
        }

        private String targetKey(UUID id, String name) {
            String clean = ChatNameUtil.normalizeNickCandidate(name);
            return !clean.isBlank() ? "name:" + clean.toLowerCase(Locale.ROOT) : "uuid:" + id;
        }

        private record TargetRow(UUID id, String name, Component displayName, boolean online,
                                 PlayerLocationSnapshot snapshot, PlayerInfo info) {}
        private record TargetHit(UUID id, String name, float x, float y, float w, float h) {}
    }
    private final class TriangulationStatusSetting extends Setting implements DynamicSearchEntry {
        private static final float ROW_H = 84.0f;
        private final Map<UUID, Float> hoverAnims = new LinkedHashMap<>();
        private final Map<UUID, Float> progressAnims = new LinkedHashMap<>();
        private long cachedFrame = Long.MIN_VALUE;
        private List<HeuristicTargetMetrics> cachedRows = List.of();
        private String cachedFilter = null;
        private List<HeuristicTargetMetrics> cachedFilteredRows = List.of();

        private TriangulationStatusSetting() { super(tr("gui.combatant.map.triangulation.runtime.title", "Resolver status")); }

        @Override
        public void render(float x, float y, float width, float mouseX, float mouseY) {
            var palette = SettingsGuiPalette.current();
            HeuristicRuntimeStats stats = HeuristicRuntime.get().stats();
            List<HeuristicTargetMetrics> targets = displayTelemetry();

            ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), tr("gui.combatant.map.triangulation.runtime.title", "Resolver status"),
                    x + 6.0f, y + 4.0f, 20.0f, palette.panelText(), false);
            String summary = triangulationModeTitle(stats.mode())
                    + "  ·  " + stats.activeTargets() + " " + tr("gui.combatant.map.triangulation.runtime.active", "active")
                    + "  ·  " + stats.queuedTargets() + " " + tr("gui.combatant.map.triangulation.runtime.queued", "queued")
                    + "  ·  " + stats.solved() + " " + tr("gui.combatant.map.triangulation.runtime.solved", "resolved");
            ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), summary,
                    x + 6.0f, y + 29.0f, 13.2f, palette.panelMuted(), false);
            PlayerTrackingRangeSnapshot tracking = PlayerTrackingRangeRuntime.get().snapshot();
            ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), trackingRangeSummary(tracking),
                    x + 6.0f, y + 47.0f, 11.7f, palette.panelMuted(), false);

            float rowY = y + 70.0f;
            if (targets.isEmpty()) {
                ClickGuiRenderer.drawRoundedRect(x + 4.0f, rowY, Math.max(1.0f, width - 8.0f), 62.0f, 9.0f,
                        SettingsGuiPalette.withAlpha(palette.panelText(), 6));
                ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(),
                        tr("gui.combatant.map.triangulation.runtime.empty", "No observations yet"),
                        x + 16.0f, rowY + 13.0f, 15.0f, palette.panelText(), false);
                ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(),
                        tr("gui.combatant.map.triangulation.runtime.empty.description", "Waiting for locator bearings."),
                        x + 16.0f, rowY + 35.0f, 12.0f, palette.panelMuted(), false);
                return;
            }

            int first = firstVisibleVirtualRow(rowY, ROW_H, targets.size());
            int last = lastVisibleVirtualRow(rowY, ROW_H, targets.size());
            long now = System.currentTimeMillis();
            for (int i = first; i < last; i++) {
                HeuristicTargetMetrics metrics = targets.get(i);
                float ry = rowY + i * ROW_H;
                float rw = Math.max(1.0f, width - 8.0f);
                boolean hover = inside(mouseX, mouseY, x + 4.0f, ry, rw, ROW_H - 5.0f);
                float dt = AnimationUtility.deltaTime();
                float hoverAnim = animateToward(hoverAnims.getOrDefault(metrics.targetUuid(), 0.0f),
                        hover ? 1.0f : 0.0f, dt, hover ? 14.0f : 10.0f);
                hoverAnims.put(metrics.targetUuid(), hoverAnim);
                float visualY = ry - easeOutCubic(hoverAnim) * 0.65f;
                int relationColor = relationColor(targetMetricName(metrics));
                ClickGuiRenderer.drawRoundedRect(x + 4.0f, visualY, rw, ROW_H - 5.0f, 9.0f,
                        SettingsGuiPalette.withAlpha(palette.controlSurface(), Math.round(78.0f + hoverAnim * 34.0f)));

                float leftX = x + 16.0f;
                String name = targetMetricName(metrics);
                Component display = targetMetricDisplayName(metrics, name);
                CategoryType relation = CategoryService.get(name);
                float tagW = Math.min(92.0f, relationTagWidth(relation, 10.6f));
                float tagX = x + width - tagW - 16.0f;
                float nameW = tagW > 0.0f ? Math.max(90.0f, tagX - leftX - 10.0f) : width - 32.0f;
                ClickGuiRichTextRenderer.draw(display, leftX, visualY + 10.0f, nameW, 15.3f,
                        palette.panelText(), 1.0f, false);
                drawRelationTag(relation, tagX, visualY + 12.0f, 10.6f, relationColor);

                HeuristicEstimate estimate = metrics.estimate();
                double baseline = observationBaseline(metrics.observations());
                double spread = observationBearingSpread(metrics.observations());
                String status = estimate == null
                        ? collectingStatus(metrics, baseline, spread)
                        : estimateStatus(estimate);
                int statusColor = estimate == null ? palette.panelText()
                        : SettingsGuiPalette.mix(palette.panelText(), Theme.theme().accent(), (float) Math.max(0.0, Math.min(1.0, estimate.confidence())));
                ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(),
                        ClickGuiRenderer.fitText(ClickGuiRenderer.getOnestMedium(), status, 12.6f, width - 32.0f),
                        leftX, visualY + 35.0f, 12.6f, statusColor, false);

                String detail = estimate == null
                        ? collectionDetail(metrics, baseline, spread)
                        : estimateDetail(metrics, estimate, baseline, spread, now);
                ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(),
                        ClickGuiRenderer.fitText(ClickGuiRenderer.getOnestMedium(), detail, 11.4f, width - 32.0f),
                        leftX, visualY + 55.0f, 11.4f, palette.panelMuted(), false);

                float targetProgress = estimate == null
                        ? collectionPercent(metrics, baseline, spread) / 100.0f
                        : (float) estimate.confidence();
                float progress = animateToward(progressAnims.getOrDefault(metrics.targetUuid(), targetProgress),
                        targetProgress, dt, 9.0f);
                progressAnims.put(metrics.targetUuid(), progress);
                float barW = Math.max(1.0f, width - 32.0f);
                ClickGuiRenderer.drawRoundedRect(leftX, visualY + 72.0f, barW, 2.0f, 1.0f,
                        SettingsGuiPalette.withAlpha(palette.panelText(), 16));
                if (progress > 0.002f) {
                    ClickGuiRenderer.drawRoundedRect(leftX, visualY + 72.0f, barW * clamp(progress, 0.0f, 1.0f), 2.0f, 1.0f,
                            SettingsGuiPalette.withAlpha(statusColor, 190));
                }
            }
        }

        private String trackingRangeSummary(PlayerTrackingRangeSnapshot tracking) {
            if (tracking == null || tracking.observedAtMs() <= 0L) {
                return tr("gui.combatant.map.triangulation.tracking.waiting", "Server ranges: waiting for connection state");
            }
            String playerRange;
            if (tracking.hasEmpiricalBoundary()) {
                playerRange = "~" + Math.round(tracking.playerTrackingBoundaryBlocks()) + "m"
                        + " (" + tracking.boundarySampleCount() + " samples)";
            } else if (tracking.confirmedPlayerTrackingLowerBoundBlocks() > 0.0) {
                playerRange = "≥" + Math.round(tracking.confirmedPlayerTrackingLowerBoundBlocks()) + "m";
            } else {
                playerRange = tr("gui.combatant.map.triangulation.tracking.unknown", "learning");
            }
            return tr("gui.combatant.map.triangulation.tracking.simulation", "Simulation") + " "
                    + Math.round(tracking.simulationDistanceBlocks()) + "m"
                    + "  ·  " + tr("gui.combatant.map.triangulation.tracking.view", "chunk view") + " "
                    + Math.round(tracking.viewDistanceBlocks()) + "m"
                    + "  ·  " + tr("gui.combatant.map.triangulation.tracking.players", "player tracking") + " " + playerRange;
        }

        @Override public void mouseClicked(double mx, double my, int button) {}

        @Override
        public float getHeight() {
            int rows = displayTelemetry().size();
            return 72.0f + (rows == 0 ? 62.0f : rows * ROW_H);
        }

        @Override
        public String dynamicSearchText() {
            StringBuilder out = new StringBuilder();
            for (HeuristicTargetMetrics metrics : telemetryRowsForFrame()) {
                out.append(' ').append(metrics.targetUuid()).append(' ').append(metrics.targetName());
            }
            return out.toString().toLowerCase(Locale.ROOT);
        }

        private List<HeuristicTargetMetrics> displayTelemetry() {
            List<HeuristicTargetMetrics> rows = telemetryRowsForFrame();
            String needle = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
            if (Objects.equals(cachedFilter, needle)) return cachedFilteredRows;
            cachedFilter = needle;
            if (needle.isEmpty() || "triangulation resolver status telemetry observations bearings confidence uncertainty estimate data mining targeted триангуляция резолвер состояние наблюдения".contains(needle)) {
                cachedFilteredRows = rows;
            } else {
                cachedFilteredRows = rows.stream().filter(value -> (value.targetUuid() + " " + targetMetricName(value) + " "
                        + relationLabel(CategoryService.get(targetMetricName(value)))).toLowerCase(Locale.ROOT).contains(needle)).toList();
            }
            return cachedFilteredRows;
        }

        private List<HeuristicTargetMetrics> telemetryRowsForFrame() {
            if (cachedFrame == renderFrameId) return cachedRows;
            cachedFrame = renderFrameId;
            cachedFilter = null;
            cachedFilteredRows = List.of();
            List<HeuristicTargetMetrics> rows = new ArrayList<>(HeuristicRuntime.get().telemetry().values());
            rows.sort(Comparator
                    .comparing((HeuristicTargetMetrics value) -> !MapTriangulationConfig.get().isTargeted(value.targetUuid(), value.targetName()))
                    .thenComparing((HeuristicTargetMetrics value) -> value.estimate() == null)
                    .thenComparing(Comparator.comparingInt(HeuristicTargetMetrics::sampleCount).reversed())
                    .thenComparingLong(value -> value.lastSolvedAtMs() > 0L ? -value.lastSolvedAtMs() : Long.MAX_VALUE));
            cachedRows = List.copyOf(rows);
            Set<UUID> alive = new LinkedHashSet<>();
            for (HeuristicTargetMetrics row : cachedRows) alive.add(row.targetUuid());
            hoverAnims.keySet().retainAll(alive);
            progressAnims.keySet().retainAll(alive);
            return cachedRows;
        }

        private String targetMetricName(HeuristicTargetMetrics metrics) {
            if (metrics != null && metrics.targetName() != null && !metrics.targetName().isBlank()) return metrics.targetName();
            if (metrics != null) {
                String configured = MapTriangulationConfig.get().nameForTarget(metrics.targetUuid());
                if (!configured.isBlank()) return configured;
                PlayerLocationSnapshot snapshot = PlayerLocationService.get().snapshot().bestByPlayer().get(metrics.targetUuid());
                if (snapshot != null && snapshot.playerName() != null && !snapshot.playerName().isBlank()) return snapshot.playerName();
                return metrics.targetUuid().toString().substring(0, 8);
            }
            return tr("gui.combatant.map.source.unknown", "unknown");
        }

        private Component targetMetricDisplayName(HeuristicTargetMetrics metrics, String fallback) {
            Minecraft mc = Minecraft.getInstance();
            PlayerInfo info = mc != null && mc.getConnection() != null && metrics != null
                    ? mc.getConnection().getPlayerInfo(metrics.targetUuid()) : null;
            Component display = null;
            if (info != null && mc.gui != null && mc.gui.hud != null && mc.gui.hud.getTabList() != null) {
                display = mc.gui.hud.getTabList().getNameForDisplay(info);
            }
            if (display == null && info != null) display = info.getTabListDisplayName();
            if (display == null || display.getString().isBlank()) display = Component.literal(fallback);
            return LegacyTextUtil.convertLegacyCodesRobust(display);
        }

        private String collectingStatus(HeuristicTargetMetrics metrics, double baseline, double spread) {
            MapHeuristicConfig cfg = MapHeuristicConfig.get();
            int percent = collectionPercent(metrics, baseline, spread);
            String phase = metrics.queued()
                    ? tr("gui.combatant.map.triangulation.runtime.queued_now", "queued for resolve")
                    : tr("gui.combatant.map.triangulation.runtime.collecting", "collecting");
            return percent + "%  ·  " + phase
                    + "  ·  " + metrics.sampleCount() + " " + tr("gui.combatant.map.triangulation.runtime.samples_short", "obs.")
                    + "  ·  " + tr("gui.combatant.map.triangulation.runtime.baseline", "baseline") + " " + Math.round(baseline) + "/" + Math.round(cfg.minBaseline())
                    + "  ·  " + tr("gui.combatant.map.triangulation.runtime.spread", "angle") + " " + oneDecimal(spread) + "/" + oneDecimal(cfg.minBearingDeltaDegrees()) + "°";
        }

        private String collectionDetail(HeuristicTargetMetrics metrics, double baseline, double spread) {
            String missing = collectionMissing(metrics, baseline, spread);
            if (missing.isBlank()) return tr("gui.combatant.map.triangulation.runtime.ready", "enough data");
            return tr("gui.combatant.map.triangulation.runtime.missing", "Need") + ": " + missing;
        }

        private int collectionPercent(HeuristicTargetMetrics metrics, double baseline, double spread) {
            MapHeuristicConfig cfg = MapHeuristicConfig.get();
            double firstRay = metrics.sampleCount() > 0 ? 0.40 : 0.0;
            double baselineP = cfg.minBaseline() <= 0.0 ? 1.0 : Math.min(1.0, baseline / cfg.minBaseline());
            double angleP = cfg.minBearingDeltaDegrees() <= 0.0 ? 1.0 : Math.min(1.0, spread / cfg.minBearingDeltaDegrees());
            double geometry = metrics.sampleCount() >= 2 ? Math.max(baselineP, angleP) * 0.55 : 0.0;
            return (int) Math.round(Math.max(0.0, Math.min(0.95, firstRay + geometry)) * 100.0);
        }

        private String collectionMissing(HeuristicTargetMetrics metrics, double baseline, double spread) {
            MapHeuristicConfig cfg = MapHeuristicConfig.get();
            if (metrics.sampleCount() < 2) return tr("gui.combatant.map.triangulation.runtime.need_bearing", "one more bearing");
            if (metrics.queued()) return "";
            if (baseline < cfg.minBaseline() && spread < cfg.minBearingDeltaDegrees()) {
                long blocks = Math.max(1L, Math.round(cfg.minBaseline() - baseline));
                double degrees = Math.max(0.1, cfg.minBearingDeltaDegrees() - spread);
                return "+" + blocks + " " + tr("gui.combatant.map.triangulation.runtime.blocks_baseline", "blocks movement")
                        + " " + tr("gui.combatant.map.triangulation.runtime.or", "or") + " +" + oneDecimal(degrees) + "°";
            }
            return tr("gui.combatant.map.triangulation.runtime.need_crossing", "a different bearing angle");
        }

        private String estimateStatus(HeuristicEstimate estimate) {
            return Math.round(estimate.confidence() * 100.0) + "% "
                    + tr("gui.combatant.map.triangulation.runtime.confidence", "confidence")
                    + "  ·  ±" + Math.round(estimate.uncertaintyMajor()) + " "
                    + tr("gui.combatant.map.triangulation.runtime.blocks", "blocks")
                    + "  ·  " + estimate.inlierCount() + "/" + estimate.sampleCount() + " "
                    + tr("gui.combatant.map.triangulation.runtime.inliers", "inliers");
        }

        private String estimateDetail(HeuristicTargetMetrics metrics, HeuristicEstimate estimate,
                                      double baseline, double spread, long now) {
            String missing = estimateMissing(metrics, estimate, baseline, spread);
            long age = Math.max(0L, now - estimate.updatedAtMs());
            String ageText = age < 1000L ? tr("gui.combatant.map.common.now", "now") : (age / 1000L) + "s";
            return missing + "  ·  " + tr("gui.combatant.map.triangulation.runtime.updated", "updated") + " " + ageText;
        }

        private String estimateMissing(HeuristicTargetMetrics metrics, HeuristicEstimate estimate,
                                       double baseline, double spread) {
            if (estimate.confidence() >= 0.75 && estimate.inlierCount() >= Math.max(2, estimate.sampleCount() - 1)) {
                return tr("gui.combatant.map.triangulation.runtime.enough", "geometry is good");
            }
            List<String> reasons = new ArrayList<>(2);
            if (metrics.sampleCount() < 5) {
                reasons.add(tr("gui.combatant.map.triangulation.runtime.more_samples", "more observations"));
            }
            if (spread < Math.max(8.0, MapHeuristicConfig.get().minBearingDeltaDegrees() * 2.0)) {
                reasons.add(tr("gui.combatant.map.triangulation.runtime.wider_angle", "wider bearing angle"));
            } else if (baseline < MapHeuristicConfig.get().minBaseline() * 2.0) {
                reasons.add(tr("gui.combatant.map.triangulation.runtime.more_baseline", "more observer movement"));
            }
            if (estimate.inlierCount() < Math.max(2, (int) Math.ceil(estimate.sampleCount() * 0.75))) {
                reasons.add(tr("gui.combatant.map.triangulation.runtime.fewer_outliers", "fewer conflicting observations"));
            }
            if (reasons.isEmpty()) reasons.add(tr("gui.combatant.map.triangulation.runtime.better_geometry", "better geometry"));
            String label = tr("gui.combatant.map.triangulation.runtime.missing", "Need");
            return label + ": " + String.join(", ", reasons.subList(0, Math.min(2, reasons.size())));
        }

        private double observationBaseline(List<HeuristicObservation> observations) {
            double max = 0.0;
            for (int i = 0; i < observations.size(); i++) {
                HeuristicObservation a = observations.get(i);
                for (int j = i + 1; j < observations.size(); j++) {
                    HeuristicObservation b = observations.get(j);
                    max = Math.max(max, Math.hypot(a.observerX() - b.observerX(), a.observerZ() - b.observerZ()));
                }
            }
            return max;
        }

        private double observationBearingSpread(List<HeuristicObservation> observations) {
            double max = 0.0;
            for (int i = 0; i < observations.size(); i++) {
                for (int j = i + 1; j < observations.size(); j++) {
                    double d = Math.abs(observations.get(i).bearingRadians() - observations.get(j).bearingRadians()) % (Math.PI * 2.0);
                    if (d > Math.PI) d = Math.PI * 2.0 - d;
                    d = Math.min(d, Math.PI - d);
                    max = Math.max(max, d);
                }
            }
            return Math.toDegrees(max);
        }

        private String oneDecimal(double value) {
            return String.format(Locale.ROOT, "%.1f", value);
        }
    }
    private final class MapLinkStatusSetting extends Setting implements DynamicSearchEntry {
        private static final float ROW_H = 54.0f;
        private final List<MapLinkHit> rowHits = new ArrayList<>();
        private final Map<String, Float> hoverAnims = new LinkedHashMap<>();
        private final Map<String, Float> selectionAnims = new LinkedHashMap<>();
        private final Map<String, Float> pressAnims = new LinkedHashMap<>();
        private float createHoverAnim;
        private float createPressAnim;
        private float deleteHoverAnim;
        private long cachedFrame = Long.MIN_VALUE;
        private List<MapLinkProfile> cachedRows = List.of();
        private String cachedFilter = null;
        private List<MapLinkProfile> cachedFilteredRows = List.of();
        private float createX, createY, createW, createH;
        private float deleteX, deleteY, deleteW, deleteH;

        private MapLinkStatusSetting() { super(tr("gui.combatant.map.maplink.profiles.title", "MapLink profiles")); }

        @Override
        public void render(float x, float y, float width, float mouseX, float mouseY) {
            rowHits.clear();
            var palette = SettingsGuiPalette.current();
            MapLinkSnapshot snapshot = MapLinkRuntime.get().snapshot();
            Map<String, MapLinkProfileState> states = snapshot.profileStates();
            List<MapLinkProfile> profiles = displayMapLinkProfiles();
            List<MapLinkProfile> allProfiles = mapLinkProfilesForFrame();
            long configured = allProfiles.stream().filter(MapLinkConfig::isConfiguredProfile).count();

            ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), tr("gui.combatant.map.maplink.profiles.title", "MapLink profiles"),
                    x + 6.0f, y + 4.0f, 20.0f, palette.panelText(), false);
            String summary = allProfiles.size() + " " + tr("gui.combatant.map.maplink.profiles.total", "profiles")
                    + "  ·  " + configured + " " + tr("gui.combatant.map.maplink.profiles.ready", "ready")
                    + "  ·  " + snapshot.observations().size() + " " + tr("gui.combatant.map.maplink.profiles.observations", "observations");
            ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), summary,
                    x + 6.0f, y + 29.0f, 13.2f, palette.panelMuted(), false);

            String createLabel = tr("gui.combatant.map.maplink.profiles.create", "New profile");
            createW = Math.max(124.0f, ClickGuiRenderer.textWidth(ClickGuiRenderer.getOnestMedium(), createLabel, 12.8f) + 43.0f);
            createH = 30.0f;
            createX = x + width - createW - 6.0f;
            createY = y + 8.0f;
            boolean createHover = inside(mouseX, mouseY, createX, createY, createW, createH);
            float dt = AnimationUtility.deltaTime();
            createHoverAnim = animateToward(createHoverAnim, createHover ? 1.0f : 0.0f,
                    dt, createHover ? 14.0f : 10.0f);
            createPressAnim = animateToward(createPressAnim, 0.0f, dt, 18.0f);
            float createEase = easeOutCubic(createHoverAnim);
            float createVisualY = createY - createEase * 0.7f + createPressAnim * 1.2f;
            ClickGuiRenderer.drawRoundedRect(createX, createVisualY, createW, createH, 8.0f,
                    SettingsGuiPalette.withAlpha(Theme.theme().accent(), Math.round(30.0f + createEase * 18.0f)));
            Renderer2D.COLOR.svg("map-plus", createX + 8.0f, createVisualY + 7.0f, 16.0f, 16.0f,
                    SvgRenderOptions.overrideColor(Theme.theme().accent()));
            ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), createLabel,
                    createX + 31.0f, createVisualY + 8.0f, 12.8f, Theme.theme().accent(), false);

            float rowY = y + 58.0f;
            if (profiles.isEmpty()) {
                ClickGuiRenderer.drawRoundedRect(x + 4.0f, rowY, Math.max(1.0f, width - 8.0f), 62.0f, 9.0f,
                        SettingsGuiPalette.withAlpha(palette.panelText(), 7));
                ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), tr("gui.combatant.map.maplink.profiles.empty", "No profiles"),
                        x + 16.0f, rowY + 13.0f, 16.0f, palette.panelText(), false);
                ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(),
                        tr("gui.combatant.map.maplink.profiles.empty.description", "Create one and set the server and map URL."),
                        x + 16.0f, rowY + 36.0f, 12.2f, palette.panelMuted(), false);
                return;
            }

            int first = firstVisibleVirtualRow(rowY, ROW_H, profiles.size());
            int last = lastVisibleVirtualRow(rowY, ROW_H, profiles.size());
            for (int i = first; i < last; i++) {
                MapLinkProfile profile = profiles.get(i);
                MapLinkProfileState state = states.get(profile.id());
                float ry = rowY + i * ROW_H;
                float rw = Math.max(1.0f, width - 8.0f);
                boolean hover = inside(mouseX, mouseY, x + 4.0f, ry, rw, ROW_H - 4.0f);
                boolean selected = profile.id().equals(selectedMapLinkProfileId);
                float hoverAnim = animateToward(hoverAnims.getOrDefault(profile.id(), 0.0f), hover ? 1.0f : 0.0f,
                        dt, hover ? 14.0f : 10.0f);
                float selectedAnim = animateToward(selectionAnims.getOrDefault(profile.id(), selected ? 1.0f : 0.0f),
                        selected ? 1.0f : 0.0f, dt, 13.0f);
                float pressAnim = animateToward(pressAnims.getOrDefault(profile.id(), 0.0f), 0.0f, dt, 18.0f);
                hoverAnims.put(profile.id(), hoverAnim);
                selectionAnims.put(profile.id(), selectedAnim);
                pressAnims.put(profile.id(), pressAnim);
                boolean configuredProfile = MapLinkConfig.isConfiguredProfile(profile);
                float hoverEase = easeOutCubic(hoverAnim);
                float visualY = ry - hoverEase * 0.8f + pressAnim * 1.25f;
                int idleBg = SettingsGuiPalette.withAlpha(palette.panelText(), Math.round(7.0f + hoverEase * 14.0f));
                int activeBg = SettingsGuiPalette.withAlpha(Theme.theme().accent(), Math.round(25.0f + hoverEase * 16.0f));
                int bg = SettingsGuiPalette.mix(idleBg, activeBg, selectedAnim);
                ClickGuiRenderer.drawRoundedRect(x + 4.0f, visualY, rw, ROW_H - 4.0f, 9.0f, bg);
                drawClippedLeadingAccent(x + 4.0f, visualY, rw, ROW_H - 4.0f, 9.0f,
                        SettingsGuiPalette.withAlpha(Theme.theme().accent(), Math.round(225.0f * selectedAnim)),
                        selectedAnim);

                int statusColor = mapLinkStatusColor(state, configuredProfile && MapLinkConfig.get().enabled());
                String name = profile.displayName().isBlank() ? profile.id() : profile.displayName();
                float actionSize = 28.0f;
                float actionX = x + width - actionSize - 12.0f;
                float actionY = visualY + 11.0f;
                float textW = Math.max(80.0f, actionX - (x + 16.0f) - 12.0f);
                ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(),
                        ClickGuiRenderer.fitText(ClickGuiRenderer.getOnestMedium(), name, 15.5f, textW),
                        x + 16.0f, visualY + 8.0f, 15.5f, palette.panelText(), false);

                String serverLabel = profile.serverMatcher().isBlank()
                        ? tr("gui.combatant.map.maplink.profiles.server_unset", "server not set") : profile.serverMatcher();
                String status;
                if (!configuredProfile) {
                    status = tr("gui.combatant.map.maplink.profiles.incomplete", "needs setup");
                } else if (!MapLinkConfig.get().enabled()) {
                    status = tr("gui.combatant.map.maplink.profiles.paused", "MapLink is off");
                } else {
                    status = mapLinkStatusLabel(state == null ? MapLinkProfileStatus.IDLE : state.status());
                }
                String meta = serverLabel + "  ·  " + mapLinkProviderLabel(profile.providerType()) + "  ·  " + status;
                if (state != null && configuredProfile && state.playerCount() > 0) {
                    meta += "  ·  " + state.playerCount() + " " + tr("gui.combatant.map.maplink.profiles.players", "players");
                }
                Renderer2D.COLOR.circle(x + 18.5f, visualY + 35.0f, 2.25f,
                        SettingsGuiPalette.withAlpha(statusColor, configuredProfile ? 235 : 130));
                ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(),
                        ClickGuiRenderer.fitText(ClickGuiRenderer.getOnestMedium(), meta, 11.9f, Math.max(1.0f, textW - 10.0f)),
                        x + 26.0f, visualY + 30.0f, 11.9f,
                        configuredProfile ? statusColor : palette.panelMuted(), false);

                boolean editHover = inside(mouseX, mouseY, actionX, actionY, actionSize, actionSize);
                int actionColor = SettingsGuiPalette.mix(palette.panelText(), Theme.theme().accent(), selectedAnim);
                ClickGuiRenderer.drawRoundedRect(actionX, actionY, actionSize, actionSize, 8.0f,
                        SettingsGuiPalette.withAlpha(actionColor, Math.round(9.0f + selectedAnim * 10.0f + (editHover ? 20.0f : 0.0f))));
                Renderer2D.COLOR.svg("pencil", actionX + 6.0f, actionY + 6.0f, 16.0f, 16.0f,
                        SvgRenderOptions.overrideColor(actionColor));
                rowHits.add(new MapLinkHit(profile.id(), x + 4.0f, ry, rw, ROW_H - 4.0f));
            }

            MapLinkProfile selected = selectedMapLinkProfile();
            deleteW = 108.0f;
            deleteH = 28.0f;
            deleteX = x + width - deleteW - 6.0f;
            deleteY = rowY + profiles.size() * ROW_H + 2.0f;
            if (selected != null) {
                boolean hoverDelete = inside(mouseX, mouseY, deleteX, deleteY, deleteW, deleteH);
                deleteHoverAnim = animateToward(deleteHoverAnim, hoverDelete ? 1.0f : 0.0f,
                        dt, hoverDelete ? 14.0f : 10.0f);
                float deleteEase = easeOutCubic(deleteHoverAnim);
                ClickGuiRenderer.drawRoundedRect(deleteX, deleteY, deleteW, deleteH, 7.0f,
                        SettingsGuiPalette.withAlpha(0xFFFF6D78, Math.round(18.0f + deleteEase * 18.0f)));
                Renderer2D.COLOR.svg("trash-2", deleteX + 9.0f, deleteY + 6.0f, 16.0f, 16.0f,
                        SvgRenderOptions.overrideColor(0xFFFF818A));
                ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), tr("gui.combatant.map.maplink.profiles.delete", "Delete"),
                        deleteX + 31.0f, deleteY + 7.0f, 12.4f, 0xFFFF818A, false);
            }
        }

        @Override
        public void mouseClicked(double mx, double my, int button) {
            if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return;
            if (inside((float) mx, (float) my, createX, createY, createW, createH)) {
                createPressAnim = 1.0f;
                createMapLinkProfile();
                cachedFrame = Long.MIN_VALUE;
                return;
            }
            if (hasSelectedMapLinkProfile() && inside((float) mx, (float) my, deleteX, deleteY, deleteW, deleteH)) {
                deleteSelectedMapLinkProfile();
                cachedFrame = Long.MIN_VALUE;
                return;
            }
            for (MapLinkHit hit : rowHits) {
                if (!inside((float) mx, (float) my, hit.x, hit.y, hit.w, hit.h)) continue;
                pressAnims.put(hit.profileId, 1.0f);
                revealMapLinkProfile(hit.profileId);
                return;
            }
        }

        @Override
        public float getHeight() {
            List<MapLinkProfile> profiles = displayMapLinkProfiles();
            if (profiles.isEmpty()) return 124.0f;
            float footer = hasSelectedMapLinkProfile() ? 34.0f : 4.0f;
            return 60.0f + profiles.size() * ROW_H + footer;
        }

        @Override
        public String dynamicSearchText() {
            StringBuilder out = new StringBuilder();
            for (MapLinkProfile profile : mapLinkProfilesForFrame()) out.append(' ').append(mapLinkSearchText(profile));
            return out.toString().toLowerCase(Locale.ROOT);
        }

        private List<MapLinkProfile> displayMapLinkProfiles() {
            List<MapLinkProfile> profiles = mapLinkProfilesForFrame();
            String needle = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
            if (Objects.equals(cachedFilter, needle)) return cachedFilteredRows;
            cachedFilter = needle;
            if (needle.isEmpty() || "maplink providers profiles status live stale players worlds server карта профили сервер".contains(needle)) {
                cachedFilteredRows = profiles;
            } else {
                cachedFilteredRows = profiles.stream().filter(profile -> mapLinkSearchText(profile).contains(needle)).toList();
            }
            return cachedFilteredRows;
        }

        private List<MapLinkProfile> mapLinkProfilesForFrame() {
            if (cachedFrame == renderFrameId) return cachedRows;
            cachedFrame = renderFrameId;
            cachedFilter = null;
            cachedFilteredRows = List.of();
            List<MapLinkProfile> profiles = new ArrayList<>(MapLinkConfig.get().allProfiles());
            if (!selectedMapLinkProfileId.isBlank() && profiles.stream().noneMatch(p -> p.id().equals(selectedMapLinkProfileId))) {
                selectedMapLinkProfileId = "";
            }
            if (selectedMapLinkProfileId.isBlank() && !profiles.isEmpty()) selectedMapLinkProfileId = profiles.getFirst().id();
            cachedRows = List.copyOf(profiles);
            Set<String> alive = new LinkedHashSet<>();
            for (MapLinkProfile profile : cachedRows) alive.add(profile.id());
            hoverAnims.keySet().retainAll(alive);
            selectionAnims.keySet().retainAll(alive);
            pressAnims.keySet().retainAll(alive);
            return cachedRows;
        }

        private static String mapLinkSearchText(MapLinkProfile profile) {
            return (profile.id() + " " + profile.displayName() + " " + profile.providerType() + " "
                    + profile.serverMatcher() + " " + profile.baseUrl()).toLowerCase(Locale.ROOT);
        }

        private static int mapLinkStatusColor(MapLinkProfileState state, boolean active) {
            if (!active) return 0xFF858A94;
            if (state == null) return 0xFF9AA6B6;
            return switch (state.status()) {
                case LIVE -> 0xFF72E69A;
                case CONNECTING -> 0xFFFFD36A;
                case STALE, WORLD_UNMAPPED -> 0xFFFFA35C;
                default -> 0xFFFF6D78;
            };
        }

        private record MapLinkHit(String profileId, float x, float y, float w, float h) {}
    }
    private static String currentServerAddress() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getCurrentServer() == null || mc.getCurrentServer().ip == null) return "";
        return mc.getCurrentServer().ip.trim();
    }

    private static final class ExternalLongValue extends NumberValue<Long> {
        private final Supplier<Long> getter;
        private final Consumer<Long> setter;
        private ExternalLongValue(String name, Supplier<Long> getter, Consumer<Long> setter, long min, long max) {
            super(name, getter.get(), min, max);
            this.getter = getter;
            this.setter = setter;
        }
        @Override public Long get() { return getter.get(); }
        @Override public void set(Long value) { setter.accept(Math.max(getMin(), Math.min(getMax(), value == null ? getMin() : value))); }
        @Override public Object toJson() { return get(); }
        @Override public void fromJson(Object json) { if (json instanceof Number n) set(n.longValue()); }
    }

    private static final class ExternalIntegerRangeValue extends NumberValue<Integer> {
        private final Supplier<Integer> getter;
        private final Consumer<Integer> setter;
        private ExternalIntegerRangeValue(String name, Supplier<Integer> getter, Consumer<Integer> setter, int min, int max) {
            super(name, getter.get(), min, max);
            this.getter = getter;
            this.setter = setter;
        }
        @Override public Integer get() { return getter.get(); }
        @Override public void set(Integer value) { setter.accept(Math.max(getMin(), Math.min(getMax(), value == null ? getMin() : value))); }
        @Override public Object toJson() { return get(); }
        @Override public void fromJson(Object json) { if (json instanceof Number n) set(n.intValue()); }
    }

    private static final class ExternalMapSetValue extends SetValue {
        private final Supplier<Map<String, String>> getter;
        private final Consumer<Map<String, String>> setter;
        private ExternalMapSetValue(String name, Supplier<Map<String, String>> getter, Consumer<Map<String, String>> setter) {
            super(name);
            this.getter = getter;
            this.setter = setter;
        }
        @Override public Set<String> get() {
            LinkedHashSet<String> out = new LinkedHashSet<>();
            Map<String, String> map = getter.get();
            if (map != null) map.forEach((key, value) -> out.add(key + "=" + value));
            return out;
        }
        @Override public void set(Set<String> rows) {
            LinkedHashMap<String, String> out = new LinkedHashMap<>();
            if (rows != null) {
                for (String row : rows) {
                    if (row == null) continue;
                    int split = row.indexOf('=');
                    if (split <= 0 || split >= row.length() - 1) continue;
                    String key = row.substring(0, split).trim();
                    String value = row.substring(split + 1).trim();
                    if (!key.isBlank() && !value.isBlank()) out.put(key, value);
                }
            }
            setter.accept(out);
        }
        @Override public Object toJson() { return new ArrayList<>(get()); }
        @Override public void fromJson(Object json) {
            if (!(json instanceof List<?> list)) return;
            LinkedHashSet<String> rows = new LinkedHashSet<>();
            for (Object item : list) if (item instanceof String text) rows.add(text);
            set(rows);
        }
    }

    private static final class ExternalBooleanValue extends BooleanValue {
        private final Supplier<Boolean> getter; private final Consumer<Boolean> setter;
        private ExternalBooleanValue(String name, Supplier<Boolean> getter, Consumer<Boolean> setter) { super(name, getter.get()); this.getter = getter; this.setter = setter; }
        @Override public Boolean get() { return getter.get(); }
        @Override public void set(Boolean value) { setter.accept(value); }
        @Override public Object toJson() { return get(); }
        @Override public void fromJson(Object json) { if (json instanceof Boolean value) set(value); }
    }
    private static final class ExternalIntegerValue extends NumberValue<Integer> {
        private final Supplier<Integer> getter; private final Consumer<Integer> setter; private final List<Integer> values; private final ConfigOption<Integer> option;
        private ExternalIntegerValue(String name, Supplier<Integer> getter, Consumer<Integer> setter, List<Integer> values, ConfigOption<Integer> option) { super(name, getter.get(), values.getFirst(), values.getLast()); this.getter = getter; this.setter = setter; this.values = values; this.option = option; }
        @Override public Integer get() { return getter.get(); }
        @Override public void set(Integer value) { setter.accept(nearest(value)); }
        @Override public Object toJson() { return get(); }
        @Override public void fromJson(Object json) { if (json instanceof Number value) set(value.intValue()); }
        @Override public String toDisplay() { var display = option.getDisplayGetter().apply(option, get()); return display == null ? super.toDisplay() : LegacyTextUtil.stripLegacy(display.getString()); }
        private int nearest(int value) { int result = values.getFirst(); int distance = Math.abs(result - value); for (int candidate : values) { int d = Math.abs(candidate - value); if (d < distance) { result = candidate; distance = d; } } return result; }
    }
    private static final class ExternalDoubleValue extends NumberValue<Double> {
        private final Supplier<Double> getter; private final Consumer<Double> setter; private final List<Double> values; private final ConfigOption<Double> option;
        private ExternalDoubleValue(String name, Supplier<Double> getter, Consumer<Double> setter, List<Double> values, ConfigOption<Double> option) { super(name, getter.get(), values.getFirst(), values.getLast()); this.getter = getter; this.setter = setter; this.values = values; this.option = option; }
        @Override public Double get() { return getter.get(); }
        @Override public void set(Double value) { setter.accept(nearest(value)); }
        @Override public Object toJson() { return get(); }
        @Override public void fromJson(Object json) { if (json instanceof Number value) set(value.doubleValue()); }
        @Override public String toDisplay() { var display = option.getDisplayGetter().apply(option, get()); return display == null ? super.toDisplay() : LegacyTextUtil.stripLegacy(display.getString()); }
        private double nearest(double value) { double result = values.getFirst(); double distance = Math.abs(result - value); for (double candidate : values) { double d = Math.abs(candidate - value); if (d < distance) { result = candidate; distance = d; } } return result; }
    }
    private static final class ExternalStringValue extends StringValue {
        private final Supplier<String> getter; private final Consumer<String> setter;
        private ExternalStringValue(String name, Supplier<String> getter, Consumer<String> setter) { super(name, getter.get()); this.getter = getter; this.setter = setter; }
        @Override public String get() { return getter.get(); }
        @Override public void set(String value) { setter.accept(value); }
        @Override public Object toJson() { return get(); }
        @Override public void fromJson(Object json) { if (json instanceof String value) set(value); }
    }
    private static final class ExternalModeValue<T> extends ModeValue {
        private final Supplier<T> getter;
        private final Consumer<T> setter;
        private final List<T> values;
        private final List<String> labels;

        private ExternalModeValue(String name, Supplier<T> getter, Consumer<T> setter,
                                  List<T> values, List<String> labels) {
            super(name, currentLabel(getter, values, labels), labels.toArray(String[]::new));
            this.getter = getter;
            this.setter = setter;
            this.values = List.copyOf(values);
            this.labels = List.copyOf(labels);
        }

        @Override public String get() {
            T current = getter.get();
            for (int i = 0; i < values.size(); i++) {
                if (Objects.equals(values.get(i), current)) return labels.get(i);
            }
            return labels.getFirst();
        }

        @Override public void set(String label) {
            int index = labels.indexOf(label);
            if (index >= 0) setter.accept(values.get(index));
        }

        @Override public Object toJson() { return get(); }
        @Override public void fromJson(Object json) { if (json instanceof String label) set(label); }

        private static <T> String currentLabel(Supplier<T> getter, List<T> values, List<String> labels) {
            T current = getter.get();
            for (int i = 0; i < values.size(); i++) {
                if (Objects.equals(values.get(i), current)) return labels.get(i);
            }
            return labels.getFirst();
        }
    }
}

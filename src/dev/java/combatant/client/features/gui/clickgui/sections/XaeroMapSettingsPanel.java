/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.sections;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.StringValue;
import combatant.client.features.gui.clickgui.ClickGuiRenderer;
import combatant.client.features.gui.clickgui.layout.screen.settings.SettingsGuiPalette;
import combatant.client.features.gui.clickgui.settings.BooleanSetting;
import combatant.client.features.gui.clickgui.settings.Setting;
import combatant.client.features.gui.clickgui.settings.SettingRenderContext;
import combatant.client.features.gui.clickgui.settings.SettingRenderSurface;
import combatant.client.features.gui.clickgui.settings.SliderSetting;
import combatant.client.features.gui.clickgui.settings.TextSetting;
import combatant.client.features.gui.hud.script.HudScriptLayouts;
import combatant.client.features.theme.Theme;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.renderer.ui.runtime.core.UiRuntime;
import combatant.client.render.engine.renderer.ui.runtime.render.UiProjectionMode;
import combatant.client.render.engine.renderer.ui.runtime.render.UiRenderContext;
import combatant.client.render.engine.renderer.ui.runtime.script.CachedUiScriptRuntime;
import combatant.client.render.engine.renderer.ui.runtime.script.UiScriptModule;
import combatant.client.render.engine.renderer.ui.runtime.script.UiScriptModuleHandle;
import combatant.client.render.helpers.ScissorFunction;
import combatant.client.util.logging.DebugLog;
import combatant.client.util.resources.asset.UiScriptAsset;
import combatant.client.util.text.LegacyTextUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import org.lwjgl.glfw.GLFW;
import xaero.lib.client.config.ClientConfigManager;
import xaero.lib.common.config.Config;
import xaero.lib.common.config.option.BooleanConfigOption;
import xaero.lib.common.config.option.ConfigOption;
import xaero.lib.common.config.option.IndexedConfigOption;
import xaero.map.MapProcessor;
import xaero.map.WorldMap;
import xaero.map.common.config.option.WorldMapProfiledConfigOptions;
import xaero.map.config.primary.option.WorldMapPrimaryClientConfigOptions;
import xaero.map.config.util.WorldMapClientConfigUtils;
import xaero.map.world.MapDimension;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

@UiScriptAsset("combatant:modules/clickgui/map_settings")
final class XaeroMapSettingsPanel {
    private static final float DESIGN_WIDTH = 976.0f;
    private static final float DESIGN_HEIGHT = 636.0f;
    private static final float MIN_WIDTH = 720.0f;
    private static final float MIN_HEIGHT = 470.0f;
    private static final float SCREEN_INSET = 24.0f;
    private static final float SETTING_SCALE = 2.65f;

    private final UiScriptModuleHandle moduleHandle = HudScriptLayouts.handle(XaeroMapSettingsPanel.class);
    private final CachedUiScriptRuntime scriptRuntime = new CachedUiScriptRuntime(HudScriptLayouts.runtimeReporter());
    private final Supplier<MapProcessor> processorSupplier;
    private final Supplier<MapDimension> dimensionSupplier;
    private final List<Entry> entries = new ArrayList<>();
    private final List<Hit> hits = new ArrayList<>();
    private final List<CategoryHit> categoryHits = new ArrayList<>();
    private final List<CaveModeHit> caveModeHits = new ArrayList<>();

    private Category selectedCategory = Category.DISPLAY;
    private boolean open;
    private boolean searchFocused;
    private String search = "";
    private float x;
    private float y;
    private float width;
    private float height;
    private float scroll;
    private float maxScroll;
    private float searchX;
    private float searchY;
    private float searchW;
    private float searchH;
    private float closeX;
    private float closeY;
    private float closeW;
    private float closeH;

    XaeroMapSettingsPanel(Supplier<MapProcessor> processorSupplier, Supplier<MapDimension> dimensionSupplier) {
        this.processorSupplier = processorSupplier != null ? processorSupplier : () -> null;
        this.dimensionSupplier = dimensionSupplier != null ? dimensionSupplier : () -> null;

        ClientConfigManager manager = WorldMap.INSTANCE.getConfigs().getClientConfigManager();
        Config primary = manager.getPrimaryConfigManager().getConfig();

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

        addPrimary(primary, WorldMapPrimaryClientConfigOptions.EXPORT_MULTIPLE_IMAGES, Category.EXPORT);
        addPrimary(primary, WorldMapPrimaryClientConfigOptions.NIGHT_EXPORT, Category.EXPORT);
        addPrimary(primary, WorldMapPrimaryClientConfigOptions.EXPORT_SCALE_DOWN_SQUARE, Category.EXPORT);
        addPrimary(primary, WorldMapPrimaryClientConfigOptions.EXPORT_HIGHLIGHTS, Category.EXPORT);

        addProfiled(manager, WorldMapProfiledConfigOptions.WRITING_DISTANCE, Category.ADVANCED);
        addProfiled(manager, WorldMapProfiledConfigOptions.DETECT_AMBIGUOUS_Y, Category.ADVANCED);
        addPrimary(primary, WorldMapPrimaryClientConfigOptions.RELOAD_VIEWED, Category.ADVANCED);
        addPrimary(primary, WorldMapPrimaryClientConfigOptions.MAX_LOADED_REGIONS, Category.ADVANCED);
        addPrimary(primary, WorldMapPrimaryClientConfigOptions.UPDATE_NOTIFICATIONS, Category.ADVANCED);
        addPrimary(primary, WorldMapPrimaryClientConfigOptions.DEBUG, Category.ADVANCED);
    }

    boolean isOpen() {
        return open;
    }

    void toggle() {
        open = !open;
        if (!open) searchFocused = false;
    }

    void close() {
        open = false;
        searchFocused = false;
    }

    void render(float viewportX, float viewportY, float viewportWidth, float viewportHeight,
                float mouseX, float mouseY) {
        if (!open) return;

        width = Math.min(DESIGN_WIDTH, Math.max(MIN_WIDTH, viewportWidth - SCREEN_INSET));
        height = Math.min(DESIGN_HEIGHT, Math.max(MIN_HEIGHT, viewportHeight - SCREEN_INSET));
        x = viewportX + (viewportWidth - width) * 0.5f;
        y = viewportY + (viewportHeight - height) * 0.5f;

        Layout layout = Layout.of(width, height);
        updateInteractiveGeometry(layout);
        renderBrowserSurface(layout);
        renderSettingsContent(layout, mouseX, mouseY);
    }

    private void renderBrowserSurface(Layout layout) {
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
        props.put("title", "World Map");
        props.put("selectedCategory", selectedCategory.id);
        props.put("search", search);
        props.put("accent", hex(Theme.theme().accent()));
        List<LinkedHashMap<String, Object>> categories = new ArrayList<>();
        for (Category category : Category.values()) {
            LinkedHashMap<String, Object> item = new LinkedHashMap<>();
            item.put("id", category.id);
            item.put("label", category.label);
            item.put("description", category.description);
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
                signature,
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
            runtime.render(new UiRenderContext(Renderer2D.COLOR, ClickGuiRenderer.getOnestMedium(), null, 0.0f,
                    UiProjectionMode.CURRENT));
        }
    }

    private void renderSettingsContent(Layout layout, float mouseX, float mouseY) {
        SettingsGuiPalette palette = SettingsGuiPalette.current();
        hits.clear();
        caveModeHits.clear();

        float contentX = x + layout.detailX + 22.0f;
        float contentY = y + layout.headerHeight + 76.0f;
        float contentW = Math.max(240.0f, layout.detailWidth - 54.0f);
        float contentH = Math.max(120.0f, height - (contentY - y) - 24.0f);
        float cursorY = contentY + scroll;
        float total = 0.0f;
        float columnGap = 10.0f;
        float columnWidth = Math.max(1.0f, (contentW - columnGap) * 0.5f);

        boolean clipped = ScissorFunction.pushRaw(contentX, contentY, contentW, contentH);
        try (SettingRenderContext.Scope ignored = SettingRenderContext.push(SettingRenderSurface.MODULES, SETTING_SCALE)) {
            if (search.isBlank() && selectedCategory == Category.CAVE) {
                float caveHeight = 84.0f;
                if (cursorY + caveHeight >= contentY && cursorY <= contentY + contentH) {
                    drawCaveModeControl(contentX, cursorY, contentW, palette);
                }
                cursorY += caveHeight + 14.0f;
                total += caveHeight + 14.0f;
            }

            List<Entry> visible = entries.stream().filter(this::matches).toList();
            for (int index = 0; index < visible.size(); index += 2) {
                Entry left = visible.get(index);
                Entry right = index + 1 < visible.size() ? visible.get(index + 1) : null;
                float leftHeight = left.setting.getHeightSafely();
                float rightHeight = right == null ? 0.0f : right.setting.getHeightSafely();
                float rowHeight = Math.max(leftHeight, rightHeight);
                if (cursorY + rowHeight >= contentY && cursorY <= contentY + contentH) {
                    left.setting.renderSafely(contentX, cursorY, columnWidth, mouseX, mouseY);
                    hits.add(new Hit(left.setting, contentX, cursorY, columnWidth, leftHeight));
                    if (right != null) {
                        float rightX = contentX + columnWidth + columnGap;
                        right.setting.renderSafely(rightX, cursorY, columnWidth, mouseX, mouseY);
                        hits.add(new Hit(right.setting, rightX, cursorY, columnWidth, rightHeight));
                    }
                }
                cursorY += rowHeight + 14.0f;
                total += rowHeight + 14.0f;
            }
        } finally {
            if (clipped) ScissorFunction.pop();
        }

        if (total <= 0.0f) {
            ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), "No matching settings",
                    contentX + 6.0f, contentY + 16.0f, 18.0f, palette.panelMuted(), false);
        }
        maxScroll = Math.max(0.0f, total - contentH);
        scroll = clamp(scroll, -maxScroll, 0.0f);
    }

    private void drawCaveModeControl(float x, float y, float width, SettingsGuiPalette palette) {
        ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), "Cave mode type",
                x + 4.0f, y + 4.0f, 19.0f, palette.panelText(), false);
        MapDimension dimension = dimensionSupplier.get();
        int current = dimension == null ? 0 : Math.floorMod(dimension.getCaveModeType(), 3);
        String[] labels = {
                tr("gui.xaero_off", "Off"),
                tr("gui.xaero_wm_cave_mode_type_layered", "Layered"),
                tr("gui.xaero_wm_cave_mode_type_full", "Full")
        };
        float gap = 8.0f;
        float top = y + 34.0f;
        float h = 36.0f;
        float w = (width - gap * 2.0f) / 3.0f;
        for (int i = 0; i < labels.length; i++) {
            float bx = x + i * (w + gap);
            boolean selected = current == i;
            Renderer2D.COLOR.roundedRect(bx, top, w, h, 10.0f,
                    selected ? palette.controlSurfaceHover() : palette.controlSurface());
            if (selected) {
                Renderer2D.COLOR.roundedRectStroke(bx, top, w, h, 10.0f, 1.6f, Theme.theme().accent());
            }
            float tw = ClickGuiRenderer.textWidth(ClickGuiRenderer.getOnestMedium(), labels[i], 17.0f);
            ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), labels[i],
                    bx + (w - tw) * 0.5f, top + 9.0f, 17.0f,
                    selected ? palette.panelText() : palette.panelMuted(), false);
            caveModeHits.add(new CaveModeHit(i, bx, top, w, h));
        }
    }

    private void updateInteractiveGeometry(Layout layout) {
        categoryHits.clear();
        float rowHeight = 46.0f;
        float navX = x + 8.0f;
        float navY = y + layout.headerHeight + 18.0f;
        float rowWidth = Math.max(1.0f, layout.navWidth - 16.0f);
        Category[] categories = Category.values();
        for (int i = 0; i < categories.length; i++) {
            categoryHits.add(new CategoryHit(categories[i], navX, navY + i * 52.0f, rowWidth, rowHeight));
        }
        searchX = x + layout.detailX + 10.0f;
        searchY = y + (layout.headerHeight - 24.0f) * 0.5f;
        searchW = 162.0f;
        searchH = 24.0f;
        closeX = x + width - 54.0f;
        closeY = y + layout.headerHeight + 21.0f;
        closeW = 32.0f;
        closeH = 32.0f;
    }

    boolean mousePressed(float mouseX, float mouseY, int button) {
        if (!open) return false;
        if (!inside(mouseX, mouseY, x, y, width, height)) {
            close();
            return false;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && inside(mouseX, mouseY, closeX, closeY, closeW, closeH)) {
            close();
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && inside(mouseX, mouseY, searchX, searchY, searchW, searchH)) {
            searchFocused = true;
            return true;
        }
        searchFocused = false;
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            for (CategoryHit hit : categoryHits) {
                if (!hit.contains(mouseX, mouseY)) continue;
                selectedCategory = hit.category;
                scroll = 0.0f;
                return true;
            }
            for (CaveModeHit hit : caveModeHits) {
                if (!hit.contains(mouseX, mouseY)) continue;
                setCaveMode(hit.type);
                return true;
            }
        }
        try (SettingRenderContext.Scope ignored = SettingRenderContext.push(SettingRenderSurface.MODULES, SETTING_SCALE)) {
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
        try (SettingRenderContext.Scope ignored = SettingRenderContext.push(SettingRenderSurface.MODULES, SETTING_SCALE)) {
            for (Entry entry : entries) entry.setting.mouseReleasedSafely(mouseX, mouseY, button);
        }
    }

    boolean mouseScrolled(float mouseX, float mouseY, double amount) {
        if (!open || !inside(mouseX, mouseY, x, y, width, height)) return false;
        try (SettingRenderContext.Scope ignored = SettingRenderContext.push(SettingRenderSurface.MODULES, SETTING_SCALE)) {
            for (Hit hit : hits) {
                if (inside(mouseX, mouseY, hit.x, hit.y, hit.width, hit.height)
                        && hit.setting.mouseScrolledSafely(mouseX, mouseY, amount)) return true;
            }
        }
        scroll = clamp(scroll + (float) amount * 44.0f, -maxScroll, 0.0f);
        return true;
    }

    boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!open) return false;
        if (searchFocused) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                if (!search.isEmpty()) search = "";
                else searchFocused = false;
                scroll = 0.0f;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !search.isEmpty()) {
                search = search.substring(0, search.length() - 1);
                scroll = 0.0f;
                return true;
            }
        } else if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        try (SettingRenderContext.Scope ignored = SettingRenderContext.push(SettingRenderSurface.MODULES, SETTING_SCALE)) {
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
            scroll = 0.0f;
            return true;
        }
        try (SettingRenderContext.Scope ignored = SettingRenderContext.push(SettingRenderSurface.MODULES, SETTING_SCALE)) {
            for (Entry entry : entries) {
                if (matches(entry) && entry.setting.charTypedSafely(chr, modifiers)) return true;
            }
        }
        return searchFocused;
    }

    private boolean matches(Entry entry) {
        if (search.isBlank()) return entry.category == selectedCategory;
        String needle = search.toLowerCase(Locale.ROOT).trim();
        return entry.searchText.contains(needle);
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
        TextSetting setting = new TextSetting(name, new ExternalStringValue(option.getId(),
                () -> {
                    Integer value = primary.get(option);
                    return value == null || value == Integer.MAX_VALUE ? "auto" : Integer.toString(value);
                }, value -> {
                    int resolved;
                    if (value == null || value.isBlank() || value.equalsIgnoreCase("auto")) resolved = Integer.MAX_VALUE;
                    else {
                        try { resolved = Math.max(-64, Math.min(319, Integer.parseInt(value.trim()))); }
                        catch (NumberFormatException ignored) { return; }
                    }
                    primary.set(option, resolved);
                    WorldMap.INSTANCE.getConfigs().getPrimaryClientConfigManagerIO().save();
                    MapProcessor processor = processorSupplier.get();
                    if (processor != null) processor.updateCaveStart();
                }));
        setting.setI18nEnabled(false, false);
        entries.add(new Entry(setting, Category.CAVE, searchText(option, name)));
    }

    private void addProfiled(ClientConfigManager manager, ConfigOption<?> option, Category category) {
        add(option, category, () -> manager.getEffective(raw(option)), value -> manager.getCurrentProfile().set(raw(option), value));
    }

    private void addPrimary(Config config, ConfigOption<?> option, Category category) {
        add(option, category, () -> config.get(raw(option)), value -> config.set(raw(option), value));
    }

    private <T> void add(ConfigOption<T> option, Category category, Supplier<T> getter, Consumer<T> setter) {
        if (option == null) return;
        try {
            Setting setting;
            String name = resolveDisplayName(option);
            if (option instanceof BooleanConfigOption) {
                setting = new BooleanSetting(name, new ExternalBooleanValue(option.getId(),
                        () -> (Boolean) getter.get(), value -> setter.accept(cast(value))));
            } else if (option instanceof IndexedConfigOption<?> indexed && !indexed.getValidValues().isEmpty()
                    && indexed.getValidValues().getFirst() instanceof Integer) {
                List<Integer> values = indexed.getValidValues().stream().map(value -> (Integer) value).toList();
                setting = new SliderSetting<>(name, new ExternalIntegerValue(option.getId(),
                        () -> (Integer) getter.get(), value -> setter.accept(cast(value)), values,
                        raw(option)));
            } else if (option instanceof IndexedConfigOption<?> indexed && !indexed.getValidValues().isEmpty()
                    && indexed.getValidValues().getFirst() instanceof Double) {
                List<Double> values = indexed.getValidValues().stream().map(value -> (Double) value).toList();
                setting = new SliderSetting<>(name, new ExternalDoubleValue(option.getId(),
                        () -> (Double) getter.get(), value -> setter.accept(cast(value)), values,
                        raw(option)));
            } else if (option.getDefaultValue() instanceof String) {
                setting = new TextSetting(name, new ExternalStringValue(option.getId(),
                        () -> (String) getter.get(), value -> setter.accept(cast(value))));
            } else {
                return;
            }
            setting.setI18nEnabled(false, false);
            entries.add(new Entry(setting, category, searchText(option, name)));
        } catch (RuntimeException | LinkageError error) {
            String id = option.getId() == null ? "unknown" : option.getId();
            DebugLog.warnOnce("clickgui-map-setting-" + id,
                    "Skipping unavailable Xaero World Map setting: " + id, error);
        }
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
        if (id == null || id.isBlank()) return "Xaero setting";
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

    private static float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }
    private enum Category {
        DISPLAY("display", "Display", "Map chrome, coordinates, arrow and footprints", "map"),
        TERRAIN("terrain", "Terrain", "Terrain colors, lighting and chunk updates", "layers"),
        WAYPOINTS("waypoints", "Waypoints", "Waypoint rendering and visibility", "map-pinned"),
        CAVE("cave", "Cave", "Xaero cave mode, depth and start layer", "land-plot"),
        PLAYERS("players", "Players & Radar", "Tracked players, radar entities and claims", "users-round"),
        NAVIGATION("navigation", "Navigation", "Teleport and navigation behaviour", "route"),
        EXPORT("export", "Export", "World Map export behaviour", "map"),
        ADVANCED("advanced", "Advanced", "Loading budget and technical options", "settings-2");

        final String id;
        final String label;
        final String description;
        final String icon;
        Category(String id, String label, String description, String icon) {
            this.id = id; this.label = label; this.description = description; this.icon = icon;
        }
    }

    private record Layout(float navWidth, float collectionWidth, float detailWidth, float detailX,
                          float headerHeight) {
        static Layout of(float width, float height) {
            float nav = width * 268.0f / DESIGN_WIDTH;
            float collection = 0.0f;
            float header = height * 48.0f / DESIGN_HEIGHT;
            return new Layout(nav, collection, Math.max(0.0f, width - nav - collection), nav + collection, header);
        }
    }
    private record Entry(Setting setting, Category category, String searchText) {}
    private record Hit(Setting setting, float x, float y, float width, float height) {}
    private record CategoryHit(Category category, float x, float y, float width, float height) {
        boolean contains(float mx, float my) { return inside(mx, my, x, y, width, height); }
    }
    private record CaveModeHit(int type, float x, float y, float width, float height) {
        boolean contains(float mx, float my) { return inside(mx, my, x, y, width, height); }
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
}

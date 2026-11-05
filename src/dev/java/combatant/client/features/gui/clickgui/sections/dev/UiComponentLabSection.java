/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.gui.clickgui.sections.dev;

import combatant.client.features.gui.clickgui.ClickGuiRenderer;
import combatant.client.features.gui.clickgui.sections.ClickGuiSection;
import combatant.client.features.gui.clickgui.sections.ClickGuiSectionInfo;
import combatant.client.features.gui.hud.script.HudScriptLayouts;
import combatant.client.input.CombatantInput;
import combatant.client.input.InputDevice;
import combatant.client.input.InputKeys;
import combatant.client.input.InputModifierMask;
import combatant.client.input.minecraft.CombatantTextInputBridge;
import combatant.client.util.text.SingleLineTextInput;
import combatant.client.util.text.TextSelection;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.renderer.ui.runtime.core.UiRuntime;
import combatant.client.render.engine.renderer.ui.runtime.core.UiNode;
import combatant.client.render.engine.renderer.ui.runtime.core.UiBounds;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.render.engine.renderer.ui.runtime.input.UiPointerEvent;
import combatant.client.render.engine.renderer.ui.runtime.input.UiScrollEvent;
import combatant.client.render.engine.renderer.ui.runtime.render.UiProjectionMode;
import combatant.client.render.engine.renderer.ui.runtime.render.UiRenderContext;
import combatant.client.render.engine.renderer.ui.runtime.script.CachedUiScriptRuntime;
import combatant.client.render.engine.renderer.ui.runtime.script.UiScriptModule;
import combatant.client.render.engine.renderer.ui.runtime.script.UiScriptModuleHandle;
import combatant.client.render.engine.text.FontInfo;
import combatant.client.render.engine.text.Fonts;
import combatant.client.render.engine.text.TextRenderer;
import net.minecraft.client.Minecraft;

import java.util.LinkedHashMap;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Locale;

@ClickGuiSectionInfo(id = "combatant:component_lab", label = "UI Kits", order = 50)
public final class UiComponentLabSection implements ClickGuiSection {
    private final UiScriptModuleHandle handle = HudScriptLayouts.handle("combatant:modules/dev/component_gallery");
    private final CachedUiScriptRuntime scripts = new CachedUiScriptRuntime(HudScriptLayouts.runtimeReporter());
    private UiRuntime runtime;
    private UiRuntime actionsRuntime;
    private float x, y, width, height;
    private String kit = "google";
    private boolean adobeChecked, adobeEnabled = true, adobeAction;
    private int adobeActionBarSelected = 3;
    private String adobeActionGroupSingle = "list";
    private String adobePopoverPlacement = "bottom";
    private String adobeStandardDialogSize = "M";
    private String adobeSwatchShape = "square";
    private String adobeSwatchRounding = "default";
    private String adobeSwatchDensity = "regular";
    private String adobeSwatchSelected = "0";
    // Shared HSV color selection. Values update only on real pointer / keyboard input;
    // no color animation or per-frame JS state is involved.
    private int adobeColorHue = 250, adobeColorSaturation = 62;
    private int adobeColorBrightness = 100, adobeColorAlpha = 100;
    private String adobeColorPreset = "0";
    private String adobeColorHexCache = "#7B61FF";
    private final SingleLineTextInput adobeColorInput = new SingleLineTextInput(7,
            cp -> cp == '#' || cp >= '0' && cp <= '9'
                    || cp >= 'a' && cp <= 'f' || cp >= 'A' && cp <= 'F');
    private String adobeColorLastParsed = "";
    private String adobeColorDragMode = "";
    private UiNode adobeColorDragNode;
    private static final String[] ADOBE_COLOR_PRESETS = {
            "#7B61FF", "#4069FD", "#12A594", "#53C94C", "#F9CD46", "#F7894A",
            "#EA5A5A", "#DD72D4", "#8B8B8B", "#FFFFFF", "#181818", "#2685FF"};

    private String adobeColorHex() { return adobeColorHexCache; }

    private String computeAdobeColorHex() {
        float h = adobeColorHue / 60f;
        float saturation = adobeColorSaturation / 100f, brightness = adobeColorBrightness / 100f;
        float chroma = brightness * saturation;
        float middle = chroma * (1f - Math.abs(h % 2f - 1f));
        float base = brightness - chroma;
        float r, g, b;
        if (h < 1f) { r = chroma; g = middle; b = 0f; }
        else if (h < 2f) { r = middle; g = chroma; b = 0f; }
        else if (h < 3f) { r = 0f; g = chroma; b = middle; }
        else if (h < 4f) { r = 0f; g = middle; b = chroma; }
        else if (h < 5f) { r = middle; g = 0f; b = chroma; }
        else { r = chroma; g = 0f; b = middle; }
        return String.format(Locale.ROOT, "#%02X%02X%02X",
                Math.round((r + base) * 255f), Math.round((g + base) * 255f),
                Math.round((b + base) * 255f));
    }

    private void setAdobeColorHex(String value) {
        if (value == null || !value.matches("#[0-9a-fA-F]{6}")) return;
        int rgb = Integer.parseInt(value.substring(1), 16);
        float r = ((rgb >>> 16) & 255) / 255f;
        float g = ((rgb >>> 8) & 255) / 255f;
        float b = (rgb & 255) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        float delta = max - min, hue = adobeColorHue;
        if (delta > 0f) {
            if (max == r) hue = 60f * ((g - b) / delta);
            else if (max == g) hue = 60f * ((b - r) / delta + 2f);
            else hue = 60f * ((r - g) / delta + 4f);
        }
        adobeColorHue = Math.floorMod(Math.round(hue), 360);
        adobeColorSaturation = max == 0f ? 0 : Math.round(delta * 100f / max);
        adobeColorBrightness = Math.round(max * 100f);
        adobeColorHexCache = value.toUpperCase(Locale.ROOT);
        adobeColorLastParsed = adobeColorHexCache;
        if (!"adobe-color".equals(focus)) adobeColorInput.setText(adobeColorLastParsed);
    }

    private void syncAdobeColorField() {
        if (!"adobe-color".equals(focus)) return;
        String entered = adobeColorInput.text();
        if (entered.matches("#[0-9a-fA-F]{6}")
                && !entered.equalsIgnoreCase(adobeColorLastParsed)) {
            setAdobeColorHex(entered);
            adobeColorPreset = "";
        }
    }

    private void commitAdobeColorField() {
        if (adobeColorInput.text().matches("#[0-9a-fA-F]{6}"))
            setAdobeColorHex(adobeColorInput.text());
        else adobeColorInput.setText(adobeColorHex());
    }

    private void changeAdobeColor(int hue, int saturation, int brightness, int alpha) {
        int nextHue = Math.floorMod(hue, 360);
        int nextSat = Math.max(0, Math.min(100, saturation));
        int nextBright = Math.max(0, Math.min(100, brightness));
        int nextAlpha = Math.max(0, Math.min(100, alpha));
        if (adobeColorHue == nextHue && adobeColorSaturation == nextSat
                && adobeColorBrightness == nextBright && adobeColorAlpha == nextAlpha) return;
        adobeColorHue = nextHue;
        adobeColorSaturation = nextSat;
        adobeColorBrightness = nextBright;
        adobeColorAlpha = nextAlpha;
        adobeColorPreset = "";
        adobeColorHexCache = computeAdobeColorHex();
        if (!"adobe-color".equals(focus)) {
            adobeColorInput.setText(adobeColorHex());
            adobeColorLastParsed = adobeColorInput.text();
        }
    }

    private void adjustAdobeColor(float mouseX, float mouseY) {
        if (adobeColorDragNode == null || adobeColorDragMode.isEmpty()) return;
        UiBounds bounds = adobeColorDragNode.bounds();
        float bx = bounds.x(), by = bounds.y();
        float bw = Math.max(1f, bounds.width()), bh = Math.max(1f, bounds.height());
        switch (adobeColorDragMode) {
            case "s2:color:area", "s2:color:wheel-area" -> changeAdobeColor(adobeColorHue,
                    Math.round(Math.max(0f, Math.min(1f, (mouseX - bx) / bw)) * 100f),
                    Math.round(Math.max(0f, Math.min(1f, 1f - (mouseY - by) / bh)) * 100f),
                    adobeColorAlpha);
            case "s2:color:hue", "s2:color:vertical-hue" -> {
                float frac = "s2:color:vertical-hue".equals(adobeColorDragMode)
                        ? 1f - (mouseY - by) / bh : (mouseX - bx) / bw;
                // Use a closed range for keyboard endpoints, wrapped back to 0°.
                changeAdobeColor(Math.round(Math.max(0f, Math.min(1f, frac)) * 360f),
                        adobeColorSaturation, adobeColorBrightness, adobeColorAlpha);
            }
            case "s2:color:alpha" -> changeAdobeColor(adobeColorHue,
                    adobeColorSaturation, adobeColorBrightness,
                    Math.round(Math.max(0f, Math.min(1f, (mouseX - bx) / bw)) * 100f));
            case "s2:color:wheel" -> {
                double x = mouseX - (bx + bw * .5f), y = mouseY - (by + bh * .5f);
                if (x * x + y * y >= bw * bw * .32d * .32d) {
                    double angle = Math.toDegrees(Math.atan2(y, x)) + 90d;
                    changeAdobeColor((int) Math.round(angle), adobeColorSaturation,
                            adobeColorBrightness, adobeColorAlpha);
                }
            }
            default -> { }
        }
    }

    private final Set<String> adobeActionGroupMulti = new LinkedHashSet<>(List.of("pin"));
    private final Set<String> adobeCheckboxGroup = new LinkedHashSet<>(List.of("design"));
    private String adobeSegmentedView = "list";
    private int adobeRangeStart = 25, adobeRangeEnd = 75;
    // -1 = no pointer drag; 0 = lower thumb; 1 = upper thumb.
    private int adobeRangeDragging = -1, adobeRangeActiveThumb;
    private String adobeToggleGroupSingle = "left", adobeSelectBoxSingle = "aws";
    private final Set<String> adobeToggleGroupMulti = new LinkedHashSet<>(List.of("bold"));
    private final Set<String> adobeSelectBoxMulti = new LinkedHashSet<>(List.of("storage"));
    private final Set<String> adobeTagItems = new LinkedHashSet<>(
            List.of("design", "photo", "video", "brand", "export", "review"));
    private final Set<String> adobeTagSelected = new LinkedHashSet<>(List.of("design"));
    private final Set<String> adobeDisclosures = new LinkedHashSet<>(List.of("requirements"));
    // Stateful Spectrum data controls; no JS timer or per-frame model mutation.
    private final Set<String> adobeTableSelected = new LinkedHashSet<>();
    private String adobeTableSort = "name", adobeTableDirection = "asc";
    private String adobeTableDensity = "regular", adobeTableQuiet = "roadmap";
    private final SingleLineTextInput adobeNumberInput = new SingleLineTextInput(32,
            cp -> cp >= '0' && cp <= '9' || cp == '-' || cp == '.' || cp == '+');
    private int adobeNumberValue = 50;
    private static final String[] ADOBE_COMBO_IDS =
            {"photoshop", "illustrator", "acrobat", "premiere", "after-effects", "indesign", "lightroom"};
    private static final String[] ADOBE_COMBO_LABELS =
            {"Photoshop document", "Illustrator artwork", "Acrobat PDF", "Premiere project",
             "After Effects composition", "InDesign document", "Lightroom catalog"};
    private String adobeComboSelected = "";
    private final SingleLineTextInput adobeComboInput = new SingleLineTextInput(120);
    private YearMonth adobeCalendarMonth = YearMonth.of(2026, 10);
    private int adobeCalendarFocusDay = 15;
    private LocalDate adobeDateSingle = LocalDate.of(2026, 10, 15);
    private LocalDate adobeDateRangeStart, adobeDateRangeEnd, adobeDateRangeDraftStart;
    private final Set<String> adobeListViewSelected = new LinkedHashSet<>(List.of("ps"));
    private String adobeListViewSingle = "grid";
    private final Set<String> adobeCardViewSelected = new LinkedHashSet<>(List.of("brand"));
    private String adobeCardViewSingle = "recent";
    private String adobeRadioGroupSelected = "balanced";
    private String adobeRadioGroupOrientation = "landscape";
    private int adobeRadio;
    private String adobeTab = "files", adobeView = "list";
    private String adobeNav = "projects";
    private boolean adobeLibrariesExpanded = true;
    // Spectrum Accordion: host-owned expansion targets and frame-interpolated progress.
    private boolean adobeAccordionMultiple;
    private final boolean[] adobeAccordionExpanded = {true, false, false, false};
    private final float[] adobeAccordionProgress = {1f, 0f, 0f, 0f};
    private UiRuntime adobeAccordionCachedRuntime;
    private long adobeAccordionLastSignature = Long.MIN_VALUE;
    private static final char[] HEX_DIGITS = "0123456789ABCDEF".toCharArray();
    private UiNode adobeAccordionCachedRoot;
    private final Map<String, UiNode> adobeAccordionCachedNodes = new LinkedHashMap<>();
    private final float[] adobeAccordionPatchedProgress = {Float.NaN, Float.NaN, Float.NaN, Float.NaN};
    private static final String[] ADOBE_BREADCRUMB_IDS =
            {"home", "projects", "assets", "campaign", "images"};
    private int adobeBreadcrumbCurrent = 4;
    // Expansion is never inferred from the visible breadcrumb slice: hidden children
    // remain in the tree model and can always be reopened.
    private final Set<String> adobeTreeExpanded = new LinkedHashSet<>(
            List.of("home", "projects", "assets", "campaign"));
    private String adobeTreeSelected = "images";
    private static final Map<String, List<String>> ADOBE_TREE_CHILDREN = Map.of(
            "home", List.of("projects", "shared", "archive"),
            "projects", List.of("assets", "drafts"),
            "assets", List.of("campaign", "logos"),
            "campaign", List.of("images"));

    // Toast notifications have independent lifetimes. Never remove one before its
    // exit animation finishes, including Escape, Retry and capacity eviction.
    private static final int ADOBE_TOAST_LIMIT = 3;
    private static final float ADOBE_TOAST_ENTER_SECONDS = .220f;
    private static final float ADOBE_TOAST_EXIT_SECONDS = .160f;
    private static final class AdobeToastEntry {
        final int id;
        final String variant;
        float progress;
        float stackOffset;
        boolean exiting;
        AdobeToastEntry(int id, String variant) {
            this.id = id;
            this.variant = variant;
        }
    }
    private final List<AdobeToastEntry> adobeToasts = new ArrayList<>();
    private final ArrayDeque<AdobeToastEntry> adobeToastPending = new ArrayDeque<>();
    private int adobeToastSerial;
    private String adobeToastPlacement = "bottom";

    private void showAdobeToast(String variant) {
        AdobeToastEntry toast = new AdobeToastEntry(++adobeToastSerial, variant);
        if (adobeToasts.size() < ADOBE_TOAST_LIMIT && adobeToastPending.isEmpty()) {
            adobeToasts.add(toast);
        } else {
            // Preserve the newest notification; retire the oldest to make space.
            if (adobeToastPending.size() >= 8) adobeToastPending.removeFirst();
            adobeToastPending.addLast(toast);
            if (!adobeToasts.isEmpty()) adobeToasts.get(0).exiting = true;
        }
    }

    private boolean dismissAdobeToast(int id) {
        for (AdobeToastEntry toast : adobeToasts) {
            if (toast.id == id) {
                toast.exiting = true;
                return true;
            }
        }
        return false;
    }

    private boolean dismissNewestAdobeToast() {
        for (int i = adobeToasts.size() - 1; i >= 0; i--) {
            if (!adobeToasts.get(i).exiting) {
                adobeToasts.get(i).exiting = true;
                return true;
            }
        }
        return false;
    }

    private void animateAdobeToasts(float dt) {
        for (Iterator<AdobeToastEntry> iter = adobeToasts.iterator(); iter.hasNext();) {
            AdobeToastEntry toast = iter.next();
            float step = dt / (toast.exiting ? ADOBE_TOAST_EXIT_SECONDS : ADOBE_TOAST_ENTER_SECONDS);
            toast.progress = Math.max(0f, Math.min(1f, toast.progress + (toast.exiting ? -step : step)));
            if (toast.exiting && toast.progress <= 0f) iter.remove();
        }
        while (adobeToasts.size() < ADOBE_TOAST_LIMIT && !adobeToastPending.isEmpty()) {
            adobeToasts.add(adobeToastPending.removeFirst());
        }
        // A burst of four or more notifications must not strand entries in the
        // pending queue after the first exit finishes.
        if (!adobeToastPending.isEmpty() && !adobeToasts.isEmpty()
                && adobeToasts.stream().noneMatch(entry -> entry.exiting))
            adobeToasts.get(0).exiting = true;
        float toastWidth = Math.max(240f, Math.min(480f, width - 32f));
        float stackTarget = 0f;
        float response = 1f - (float) Math.exp(-18f * dt);
        for (int i = adobeToasts.size() - 1; i >= 0; i--) {
            AdobeToastEntry toast = adobeToasts.get(i);
            if (toast.progress <= 0f) toast.stackOffset = stackTarget;
            else toast.stackOffset += (stackTarget - toast.stackOffset) * response;
            stackTarget += ("negative".equals(toast.variant) && toastWidth < 360f ? 112f : 64f) + 10f;
        }
    }

    private List<Map<String, Object>> adobeToastModels() {
        List<Map<String, Object>> result = new ArrayList<>(adobeToasts.size());
        for (AdobeToastEntry toast : adobeToasts) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", toast.id);
            entry.put("variant", toast.variant);
            entry.put("progress", toast.progress);
            entry.put("stackOffset", toast.stackOffset);
            entry.put("exiting", toast.exiting);
            result.add(entry);
        }
        return result;
    }
    // Tooltip hover starts the warmup; the shown content persists until fade-out.
    private String adobeTooltipHover = "";
    private String adobeTooltipVariant = "neutral";
    private float adobeTooltipProgress;
    private float adobeTooltipX, adobeTooltipY, adobeTooltipW, adobeTooltipH;
    private long adobeTooltipHoveredAt;
    private static final long ADOBE_TOOLTIP_WARMUP_NS = 1_500_000_000L;
    private static final float ADOBE_TOOLTIP_SECONDS = .160f;

    private void updateAdobeTooltipHover(UiNode hovered) {
        String key = hovered == null ? "" : hovered.key();
        String prefix = "s2:tooltip:trigger:";
        String next = "adobe".equals(kit) && "tooltip".equals(page) && overlay.isEmpty()
                && key.startsWith(prefix) ? key.substring(prefix.length()) : "";
        if (!next.equals(adobeTooltipHover)) {
            adobeTooltipHover = next;
            adobeTooltipHoveredAt = System.nanoTime()
                    - (hovered != null && hovered.state().focusVisible()
                            ? ADOBE_TOOLTIP_WARMUP_NS : 0L);
        }
        // Do not swap the content of a visible popup while it is fading out.
        // A different trigger starts its own enter transition only after exit.
        if (!next.isEmpty() && hovered != null && runtime != null && runtime.root() != null
                && (next.equals(adobeTooltipVariant) || adobeTooltipProgress <= .001f)) {
            adobeTooltipVariant = next;
            UiBounds bounds = hovered.bounds(), rootBounds = runtime.root().bounds();
            adobeTooltipX = bounds.x() - rootBounds.x();
            adobeTooltipY = bounds.y() - rootBounds.y();
            adobeTooltipW = bounds.width();
            adobeTooltipH = bounds.height();
        }
    }
    private String adobeDialogVariant = "confirmation";
    private float adobeModalProgress;
    private boolean adobeDialogExiting;
    private String adobePicker = "medium";
    private String adobeLastAction = "None";
    private boolean adobeCardSelected;
    // A controlled toggle group is carried by the Java host, not per-frame JS globals.
    private final Set<String> adobeToggles = new LinkedHashSet<>(List.of("pin"));
    // Independently controlled samples in the Spectrum disabled-state gallery.
    private int adobeStateRadio;
    private boolean adobeStateCheckOff, adobeStateCheckOn = true;
    private boolean adobeStateSwitchOff, adobeStateSwitchOn = true;
    private float adobeSlider = 40f;
    private boolean adobeSliderDragging;
    private String page = "buttons";
    private String tab = "overview", chip = "all", segment = "week", list = "profile";
    private String focus = "";
    private String overlay = "";
    private String selectValue = "system", rail = "home", searchSelection = "";
    private int overlayIndex;
    private int submenuIndex = -1;
    private String submenu = "";
    private boolean showHidden;
    private String viewMode = "list", sortMode = "name";
    private String menuTypeahead = "";
    private long menuTypeaheadAt;
    private boolean snackbar;
    private String snackMessage = "Changes saved";
    private long snackbarStarted;
    private String tooltip = "";
    private float tooltipX, tooltipY;

    private record MenuEntry(String id, String label, String kind, String icon,
                             String shortcut, String child, String selection, boolean disabled) {
        Map<String, Object> asJs(boolean checked) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", id);
            result.put("label", label);
            result.put("kind", kind);
            if (!icon.isEmpty()) result.put("icon", icon);
            if (!shortcut.isEmpty()) result.put("shortcut", shortcut);
            if (!child.isEmpty()) result.put("submenu", child);
            if (!selection.isEmpty()) result.put("selection", selection);
            result.put("checked", checked);
            result.put("disabled", disabled);
            return result;
        }
        boolean navigable() { return "item".equals(kind) && !disabled; }
    }
    private static MenuEntry option(String id, String label, String icon, String shortcut) {
        return new MenuEntry(id, label, "item", icon, shortcut, "", "", false);
    }
    private static MenuEntry group(String id, String label) {
        return new MenuEntry(id, label, "header", "", "", "", "", true);
    }
    private static MenuEntry divider(String id) {
        return new MenuEntry(id, "", "divider", "", "", "", "", true);
    }
    private static MenuEntry child(String id, String label, String name) {
        return new MenuEntry(id, label, "item", "", "", name, "", false);
    }
    private static MenuEntry choice(String id, String label) {
        return new MenuEntry(id, label, "item", "", "", "", "radio", false);
    }
    private static final List<MenuEntry> MENU_ROOT = List.of(
        group("actions", "Actions"),
        option("copy", "Copy", "clipboard", "Ctrl+C"),
        option("share", "Share", "share-2", ""),
        divider("divide-1"),
        child("view", "View", "view"),
        child("sort", "Sort by", "sort"),
        new MenuEntry("hidden", "Show hidden files", "item", "", "", "", "checkbox", false),
        divider("divide-2"),
        new MenuEntry("paste", "Paste", "item", "clipboard", "Ctrl+V", "", "", true),
        option("delete", "Delete", "trash-2", "")
    );
    private static final List<MenuEntry> VIEW_MENU = List.of(
        choice("list", "List"), choice("grid", "Grid"), choice("details", "Details")
    );
    private static final List<MenuEntry> SORT_MENU = List.of(
        choice("name", "Name"), choice("date", "Date modified"), choice("size", "Size")
    );
    private List<MenuEntry> activeMenuEntries() {
        return submenu.isEmpty() ? MENU_ROOT : submenu.equals("view") ? VIEW_MENU : SORT_MENU;
    }
    private List<Map<String, Object>> jsMenu(List<MenuEntry> items) {
        List<Map<String, Object>> result = new ArrayList<>(items.size());
        for (MenuEntry item : items) {
            boolean checked = switch (item.id()) {
                case "hidden" -> showHidden;
                default -> ("view".equals(submenu) && item.id().equals(viewMode))
                    || ("sort".equals(submenu) && item.id().equals(sortMode));
            };
            result.add(item.asJs(checked));
        }
        return result;
    }
    private static int nextMenuIndex(List<MenuEntry> entries, int index, int direction) {
        if (entries.isEmpty()) return -1;
        for (int i = 0; i < entries.size(); i++) {
            index = Math.floorMod(index + direction, entries.size());
            if (entries.get(index).navigable()) return index;
        }
        return -1;
    }
    private void openSubmenu(String id) {
        if (!"view".equals(id) && !"sort".equals(id)) return;
        if (!id.equals(submenu)) {
            submenu = id;
            submenuIndex = nextMenuIndex(activeMenuEntries(), -1, 1);
        }
    }
    private void activateRootItem(String id) {
        if ("view".equals(id) || "sort".equals(id)) { openSubmenu(id); return; }
        if ("hidden".equals(id)) { showHidden = !showHidden; return; }
        if ("paste".equals(id)) return; // Disabled at the input boundary as well.
        closeOverlay();
        event("menu:" + id);
    }
    private void activateSubmenuItem(String id) {
        List<MenuEntry> entries = activeMenuEntries();
        if (entries.stream().noneMatch(entry -> entry.id().equals(id) && entry.navigable())) return;
        if ("view".equals(submenu)) viewMode = id;
        else if ("sort".equals(submenu)) sortMode = id;
        closeOverlay();
        event("menu:" + id);
    }

    private String overlayAnchor = "";
    private float anchorX, anchorY, anchorWidth = 240f, anchorHeight = 40f;
    private final SingleLineTextInput searchInput = new SingleLineTextInput(120);
    private final SingleLineTextInput adobeSearchInput = new SingleLineTextInput(120);
    // The catalog filter is scoped to the active kit; preserve both queries on kit changes.
    private final SingleLineTextInput googleCatalogInput = new SingleLineTextInput(100);
    private final SingleLineTextInput adobeCatalogInput = new SingleLineTextInput(100);
    // The editor is authoritative. This selection is its paint-only TextSelection snapshot.
    private final TextSelection catalogPaintSelection = new TextSelection();
    private SingleLineTextInput catalogEditor() {
        return "adobe".equals(kit) ? adobeCatalogInput : googleCatalogInput;
    }
    // Reuse the same editor, selection, clipboard and IME pipeline as the rest of ClickGUI.
    private final SingleLineTextInput textInput = new SingleLineTextInput(120);
    private static String comboLabel(String id) {
        for (int i = 0; i < ADOBE_COMBO_IDS.length; i++)
            if (ADOBE_COMBO_IDS[i].equals(id)) return ADOBE_COMBO_LABELS[i];
        return "";
    }
    private List<String> adobeComboMatches() {
        String needle = adobeComboInput.text().strip().toLowerCase(Locale.ROOT);
        List<String> found = new ArrayList<>();
        for (int i = 0; i < ADOBE_COMBO_IDS.length; i++)
            if (ADOBE_COMBO_LABELS[i].toLowerCase(Locale.ROOT).contains(needle))
                found.add(ADOBE_COMBO_IDS[i]);
        return found;
    }
    private void chooseCombo(String id) {
        if (!adobeComboMatches().contains(id)) return;
        adobeComboSelected = id;
        finishOverlayClose();
        clearTextFocus();
    }
    private int editedNumberValue() {
        try {
            return (int) Math.max(0, Math.min(100, Math.round(Double.parseDouble(adobeNumberInput.text()))));
        } catch (NumberFormatException exception) {
            return adobeNumberValue;
        }
    }
    private void stepAdobeNumber(int step) {
        adobeNumberValue = Math.max(0, Math.min(100, editedNumberValue() + step));
        adobeNumberInput.setText(Integer.toString(adobeNumberValue));
        adobeNumberInput.moveToEnd();
    }
    private void commitAdobeNumber() {
        adobeNumberValue = editedNumberValue();
        adobeNumberInput.setText(Integer.toString(adobeNumberValue));
        adobeNumberInput.moveToEnd();
    }
    private void calendarMove(int months) {
        YearMonth next = adobeCalendarMonth.plusMonths(months);
        if (next.isBefore(YearMonth.of(2026, 1)) || next.isAfter(YearMonth.of(2027, 12))) return;
        adobeCalendarMonth = next;
        adobeCalendarFocusDay = Math.min(adobeCalendarFocusDay, next.lengthOfMonth());
    }
    private void calendarChoose(int day) {
        if (day < 1 || day > adobeCalendarMonth.lengthOfMonth()) return;
        LocalDate chosen = adobeCalendarMonth.atDay(day);
        if (chosen.isBefore(LocalDate.of(2026, 1, 1)) || chosen.isAfter(LocalDate.of(2027, 12, 31))) return;
        adobeCalendarFocusDay = day;
        if ("adobe-date-range".equals(overlay)) {
            if (adobeDateRangeDraftStart == null) {
                // Preserve the last committed range in the field while the first
                // date of a new selection is still provisional (Spectrum behavior).
                adobeDateRangeDraftStart = chosen;
                if (adobeDateRangeEnd == null) adobeDateRangeStart = chosen;
            } else {
                LocalDate first = adobeDateRangeDraftStart;
                adobeDateRangeStart = chosen.isBefore(first) ? chosen : first;
                adobeDateRangeEnd = chosen.isAfter(first) ? chosen : first;
                adobeDateRangeDraftStart = null;
                finishOverlayClose();
            }
        } else {
            adobeDateSingle = chosen;
            if ("adobe-date-single".equals(overlay)) finishOverlayClose();
        }
    }

    private String last = "none";
    private boolean enabled = true, checked, selected;
    private int radio, clicks;
    private float slider = 45f;
    private boolean sliderDragging;
    private float animatedTab = Float.NaN, animatedSwitch = Float.NaN;
    private float animatedAdobeTab = Float.NaN;

    @Override
    public void layout(float x, float y, float width, float height) {
        this.x = x;
        this.y = y;
        this.width = Math.max(1f, width);
        this.height = Math.max(1f, height);
    }

    private Map<String, Object> model() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("width", width);
        m.put("height", height);
        m.put("kit", kit);
        m.put("adobeChecked", adobeChecked);
        m.put("adobeEnabled", adobeEnabled);
        m.put("adobeAction", adobeAction);
        m.put("adobeActionBarSelected", adobeActionBarSelected);
        m.put("adobeActionGroupSingle", adobeActionGroupSingle);
        m.put("adobeSwatchShape", adobeSwatchShape);
        m.put("adobeSwatchRounding", adobeSwatchRounding);
        m.put("adobeSwatchDensity", adobeSwatchDensity);
        m.put("adobeSwatchSelected", adobeSwatchSelected);
        syncAdobeColorField();
        m.put("adobeColorHue", adobeColorHue);
        m.put("adobeColorSaturation", adobeColorSaturation);
        m.put("adobeColorBrightness", adobeColorBrightness);
        m.put("adobeColorAlpha", adobeColorAlpha);
        m.put("adobeColorHex", adobeColorHex());
        m.put("adobeColorPreset", adobeColorPreset);
        if (adobeColorInput.text().isEmpty() && !"adobe-color".equals(focus)) {
            adobeColorInput.setText(adobeColorHex());
            adobeColorLastParsed = adobeColorInput.text();
        }
        m.put("adobeColorInput", adobeColorInput.text());
        m.put("adobePopoverPlacement", adobePopoverPlacement);
        m.put("adobeStandardDialogSize", adobeStandardDialogSize);
        m.put("adobeColorFieldFocused", "adobe-color".equals(focus));
        m.put("adobeColorCaretOffset", "adobe-color".equals(focus)
                ? adobeEditorCaretOffset(adobeColorInput) : 0f);
        m.put("adobeActionGroupMulti", List.copyOf(adobeActionGroupMulti));
        m.put("adobeCheckboxGroup", List.copyOf(adobeCheckboxGroup));
        m.put("adobeSegmentedView", adobeSegmentedView);
        m.put("adobeRangeStart", adobeRangeStart);
        m.put("adobeRangeEnd", adobeRangeEnd);
        m.put("adobeToggleGroupSingle", adobeToggleGroupSingle);
        m.put("adobeToggleGroupMulti", List.copyOf(adobeToggleGroupMulti));
        m.put("adobeSelectBoxSingle", adobeSelectBoxSingle);
        m.put("adobeSelectBoxMulti", List.copyOf(adobeSelectBoxMulti));
        m.put("adobeTagItems", List.copyOf(adobeTagItems));
        m.put("adobeTagSelected", List.copyOf(adobeTagSelected));
        m.put("adobeDisclosures", List.copyOf(adobeDisclosures));
        m.put("adobeTableSelected", List.copyOf(adobeTableSelected));
        m.put("adobeTableSort", adobeTableSort);
        m.put("adobeTableDirection", adobeTableDirection);
        m.put("adobeTableDensity", adobeTableDensity);
        m.put("adobeTableQuiet", adobeTableQuiet);
        m.put("adobeNumberValue", editedNumberValue());
        m.put("adobeNumberText", adobeNumberInput.text());
        m.put("adobeNumberCaretOffset", "adobe-number".equals(focus) ? adobeEditorCaretOffset(adobeNumberInput) : 0f);
        m.put("adobeComboSelectedLabel", comboLabel(adobeComboSelected));
        m.put("adobeComboQuery", adobeComboInput.text());
        m.put("adobeComboCaretOffset", "adobe-combo".equals(focus) ? adobeEditorCaretOffset(adobeComboInput) : 0f);
        m.put("adobeComboMatches", "adobe".equals(kit) && "combo-box".equals(page)
                && "adobe-combo".equals(overlay) ? adobeComboMatches() : List.of());
        m.put("adobeCalendarYear", adobeCalendarMonth.getYear());
        m.put("adobeCalendarMonth", adobeCalendarMonth.getMonthValue());
        m.put("adobeCalendarFocusDay", adobeCalendarFocusDay);
        m.put("adobeDateSingle", adobeDateSingle != null ? adobeDateSingle.toString() : "");
        m.put("adobeDateRangeStart", adobeDateRangeStart != null ? adobeDateRangeStart.toString() : "");
        m.put("adobeDateRangeEnd", adobeDateRangeEnd != null ? adobeDateRangeEnd.toString() : "");
        m.put("adobeDateRangeDraftStart", adobeDateRangeDraftStart != null
                ? adobeDateRangeDraftStart.toString() : "");
        m.put("adobeListViewSelected", List.copyOf(adobeListViewSelected));
        m.put("adobeListViewSingle", adobeListViewSingle);
        m.put("adobeCardViewSelected", List.copyOf(adobeCardViewSelected));
        m.put("adobeCardViewSingle", adobeCardViewSingle);
        m.put("adobeRadioGroupSelected", adobeRadioGroupSelected);
        m.put("adobeRadioGroupOrientation", adobeRadioGroupOrientation);
        m.put("adobeRadio", adobeRadio);
        m.put("adobeTab", adobeTab);
        m.put("adobeView", adobeView);
        m.put("adobeNav", adobeNav);
        m.put("adobeLibrariesExpanded", adobeLibrariesExpanded);
        m.put("adobeAccordionMultiple", adobeAccordionMultiple);
        m.put("adobeBreadcrumbCurrent", adobeBreadcrumbCurrent);
        m.put("adobeTreeExpanded", List.copyOf(adobeTreeExpanded));
        m.put("adobeTreeSelected", adobeTreeSelected);

        m.put("adobeToasts", adobeToastModels());
        m.put("adobeToastPlacement", adobeToastPlacement);
        m.put("adobeTooltipProgress", adobeTooltipProgress);
        m.put("adobeTooltipVariant", adobeTooltipVariant);
        m.put("adobeTooltipX", adobeTooltipX);
        m.put("adobeTooltipY", adobeTooltipY);
        m.put("adobeTooltipW", adobeTooltipW);
        m.put("adobeTooltipH", adobeTooltipH);
        m.put("adobeDialogVariant", adobeDialogVariant);
        m.put("adobeModalProgress", adobeModalProgress);
        m.put("adobePicker", adobePicker);
        m.put("adobeLastAction", adobeLastAction);
        m.put("adobeCardSelected", adobeCardSelected);
        m.put("adobeToggles", List.copyOf(adobeToggles));
        m.put("adobeStateRadio", adobeStateRadio);
        m.put("adobeStateCheckOff", adobeStateCheckOff);
        m.put("adobeStateCheckOn", adobeStateCheckOn);
        m.put("adobeStateSwitchOff", adobeStateSwitchOff);
        m.put("adobeStateSwitchOn", adobeStateSwitchOn);
        m.put("adobeSlider", adobeSlider);
        m.put("adobeSearch", adobeSearchInput.text());
        m.put("adobeSearchCaretOffset", "adobe-search".equals(focus) ? adobeSearchCaretOffset() : 0f);
        putCatalogTextModel(m);
        m.put("page", page);
        m.put("tab", tab);
        m.put("chip", chip);
        m.put("segment", segment);
        m.put("list", list);
        m.put("radio", radio);
        m.put("focus", focus);
        m.put("value", textInput.text());
        m.put("caret", textInput.cursor());
        m.put("caretOffset", "filled".equals(focus) || "outlined".equals(focus)
                || "adobe-field".equals(focus) ? caretOffset() : 0f);
        m.put("slider", slider);
        m.put("enabled", enabled);
        m.put("checked", checked);
        m.put("clicks", clicks);
        m.put("last", last);
        m.put("overlay", overlay);
        m.put("overlayIndex", overlayIndex);
        m.put("selectValue", selectValue);
        m.put("rail", rail);
        m.put("search", searchInput.text());
        m.put("searchSelection", searchSelection);
        m.put("anchorX", anchorX);
        m.put("anchorY", anchorY);
        m.put("anchorWidth", anchorWidth);
        m.put("anchorHeight", anchorHeight);
        m.put("snackbar", snackbar);
        m.put("snackMessage", snackMessage);
        m.put("tooltip", tooltip);
        m.put("tooltipX", tooltipX);
        m.put("tooltipY", tooltipY);
        if ("menu".equals(overlay)) {
            m.put("menuItems", jsMenu(MENU_ROOT));
            m.put("menuSubItems", submenu.isEmpty() ? List.of() : jsMenu(activeMenuEntries()));
            m.put("submenu", submenu);
            m.put("submenuIndex", submenuIndex);
        }
        return m;
    }

    private float caretOffset() {
        TextRenderer renderer = Fonts.renderer("adobe".equals(kit) ? "SourceSans3" : "Roboto",
                FontInfo.Type.Regular, ClickGuiRenderer.getOnestMedium());
        if (renderer == null) return textInput.cursor() * 8f;
        renderer.beginSize("adobe".equals(kit) ? 17f : 18f, true, false);
        try {
            String content = textInput.text();
            return (float) renderer.getWidth(content.substring(0, Math.max(0, Math.min(content.length(), textInput.cursor()))), false);
        } finally {
            renderer.end();
        }
    }

    private float adobeEditorCaretOffset(SingleLineTextInput editor) {
        TextRenderer renderer = Fonts.renderer("SourceSans3", FontInfo.Type.Regular,
                ClickGuiRenderer.getOnestMedium());
        if (renderer == null) return editor.cursor() * 8f;
        renderer.beginSize(17f, true, false);
        try {
            String value = editor.text();
            return (float) renderer.getWidth(value.substring(0,
                    Math.max(0, Math.min(value.length(), editor.cursor()))), false);
        } finally {
            renderer.end();
        }
    }

    private float adobeSearchCaretOffset() {
        TextRenderer renderer = Fonts.renderer("SourceSans3", FontInfo.Type.Regular,
                ClickGuiRenderer.getOnestMedium());
        if (renderer == null) return adobeSearchInput.cursor() * 8f;
        renderer.beginSize(17f, true, false);
        try {
            String value = adobeSearchInput.text();
            return (float) renderer.getWidth(value.substring(0,
                    Math.max(0, Math.min(value.length(), adobeSearchInput.cursor()))), false);
        } finally {
            renderer.end();
        }
    }

    /** Use precisely the same font metrics as SingleLineTextInput.hit/keepCursorVisible. */
    private float catalogTextWidth(TextRenderer font, String text) {
        return font == null ? text.length() * 7f : ClickGuiRenderer.textWidth(font, text, 15f);
    }

    private void putCatalogTextModel(Map<String, Object> model) {
        SingleLineTextInput editor = catalogEditor();
        String value = editor.text();
        boolean focused = "catalog-search".equals(focus);
        if (!focused) {
            model.put("catalogQuery", value);
            model.put("catalogDisplay", value);
            model.put("catalogFocused", false);
            model.put("catalogScroll", 0f);
            model.put("catalogTextWidth", 0f);
            model.put("catalogCaretOffset", 0f);
            model.put("catalogSelectionStart", 0f);
            model.put("catalogSelectionEnd", 0f);
            model.put("catalogHasSelection", false);
            model.put("catalogCompositionStart", 0f);
            model.put("catalogCompositionWidth", 0f);
            return;
        }
        // The kit-neutral catalog uses Onest in JS in both kits.
        TextRenderer font = Fonts.renderer("Onest", FontInfo.Type.Regular,
                ClickGuiRenderer.getOnestMedium());
        // Keep the editor viewport in sync before baking the tree, including after
        // keyboard navigation, selection, pasting and IME commits.
        if (focused && runtime != null) {
            UiNode node = find(runtime.root(), "catalog:search");
            if (node != null) {
                UiBounds bounds = node.bounds();
                editor.layout(bounds.x() + 32f, Math.max(1f, bounds.width() - 68f), font, 15f);
            }
        }
        float scroll = focused ? editor.viewportOffset() : 0f;
        float caret = catalogTextWidth(font, value.substring(0, editor.cursor()));
        String composition = focused ? editor.compositionText() : "";
        float compositionWidth = catalogTextWidth(font, composition);
        float compositionCaret = catalogTextWidth(font, composition.substring(0,
                Math.max(0, Math.min(editor.compositionCaret(), composition.length()))));
        String painted = composition.isEmpty() ? value
                : value.substring(0, editor.cursor()) + composition + value.substring(editor.cursor());
        catalogPaintSelection.clear();
        if (focused && editor.hasSelection()) {
            catalogPaintSelection.begin(0, editor.selectionStart());
            catalogPaintSelection.updateCaret(0, editor.selectionEnd());
        }
        model.put("catalogQuery", value);
        model.put("catalogDisplay", painted);
        model.put("catalogFocused", focused);
        model.put("catalogScroll", scroll);
        model.put("catalogTextWidth", catalogTextWidth(font, painted));
        model.put("catalogCaretOffset", caret + (composition.isEmpty() ? 0f : compositionCaret));
        model.put("catalogSelectionStart", catalogPaintSelection.hasRange()
                ? catalogTextWidth(font, value.substring(0, catalogPaintSelection.start())) : 0f);
        model.put("catalogSelectionEnd", catalogPaintSelection.hasRange()
                ? catalogTextWidth(font, value.substring(0, catalogPaintSelection.end())) : 0f);
        model.put("catalogHasSelection", catalogPaintSelection.hasRange());
        model.put("catalogCompositionStart", caret);
        model.put("catalogCompositionWidth", compositionWidth);
    }

    @Override
    public void render(float mouseX, float mouseY) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getResourceManager() == null || ClickGuiRenderer.currentRenderer() == null) return;
        if (handle.consumeChanged()) {
            runtime = null;
            actionsRuntime = null;
            scripts.reset();
        }
        if (!handle.ensureLoaded(minecraft.getResourceManager())) {
            HudScriptLayouts.reportLoadError(handle);
            return;
        }
        UiScriptModule script = handle.module();
        if (script == null) return;
        if (snackbar && System.nanoTime() - snackbarStarted > 5_000_000_000L)
            snackbar = false;
        float dt = Math.max(0f, Math.min(.1f, AnimationUtility.deltaTime()));
        animateAdobeToasts(dt);
        boolean tooltipReady = !adobeTooltipHover.isEmpty()
                && "adobe".equals(kit) && "tooltip".equals(page) && overlay.isEmpty()
                && System.nanoTime() - adobeTooltipHoveredAt >= ADOBE_TOOLTIP_WARMUP_NS
                && adobeTooltipHover.equals(adobeTooltipVariant);
        float tooltipTarget = tooltipReady ? 1f : 0f;
        adobeTooltipProgress = Math.max(0f, Math.min(1f,
                adobeTooltipProgress + dt / ADOBE_TOOLTIP_SECONDS
                        * (tooltipTarget > adobeTooltipProgress ? 1f : -1f)));
        // Ease the disclosure content height on the host clock. The JS component
        // receives an interpolated progress and clips content to that height.
        float disclosureResponse = 1f - (float) Math.exp(-20f * dt);
        for (int i = 0; i < adobeAccordionProgress.length; i++) {
            float target = adobeAccordionExpanded[i] ? 1f : 0f;
            float value = adobeAccordionProgress[i];
            value += (target - value) * disclosureResponse;
            adobeAccordionProgress[i] = Math.abs(target - value) < .002f ? target : value;
        }

        // Modal lifecycle: keep the overlay mounted while its exit animation finishes.
        if ("adobe-dialog".equals(overlay)) {
            float target = adobeDialogExiting ? 0f : 1f;
            float step = dt / (adobeDialogExiting ? .145f : .215f);
            adobeModalProgress = target > adobeModalProgress
                    ? Math.min(target, adobeModalProgress + step)
                    : Math.max(target, adobeModalProgress - step);
            if (adobeDialogExiting && adobeModalProgress <= 0.0001f) finishOverlayClose();
        }
        float targetTab = switch (tab) { case "activity" -> 1f; case "settings" -> 2f; default -> 0f; };
        if (!Float.isFinite(animatedTab)) animatedTab = targetTab;
        if (!Float.isFinite(animatedSwitch)) animatedSwitch = enabled ? 1f : 0f;
        float response = 1f - (float) Math.exp(-14f * dt);
        animatedTab += (targetTab - animatedTab) * response;
        animatedSwitch += ((enabled ? 1f : 0f) - animatedSwitch) * response;
        float adobeTarget = switch (adobeTab) {case "activity" -> 1f; case "sharing" -> 2f; default -> 0f;};
        if (!Float.isFinite(animatedAdobeTab)) animatedAdobeTab = adobeTarget;
        animatedAdobeTab += (adobeTarget - animatedAdobeTab) * response;
        Map<String, Object> m = model();
        TextRenderer font = Fonts.renderer("adobe".equals(kit) ? "SourceSans3" : "Roboto",
                FontInfo.Type.Regular, ClickGuiRenderer.getOnestMedium());
        long signature = CachedUiScriptRuntime.signature(m);
        if ("adobe".equals(kit) && "accordion".equals(page)
                && signature != adobeAccordionLastSignature) {
            Arrays.fill(adobeAccordionPatchedProgress, Float.NaN);
        }
        adobeAccordionLastSignature = signature;
        UiRuntime next = scripts.bake(handle, script, "component-lab", signature,
                width, height, font, x, y, width, height, () -> m, null, this::animatedBounds);
        if (next == null) return;
        runtime = next;
        // The runtime's smooth-scroll tick performs a full retained layout at render time.
        // Patch the Accordion *after* that layout; otherwise scrolling temporarily restores
        // the JS template's initial row positions until the scroll animation settles.
        runtime.render(new UiRenderContext(Renderer2D.COLOR, font, null, 0f, UiProjectionMode.CURRENT, 1f), () -> {
            patchAdobeProgressCircles();
            patchAdobeAccordion();
            if (runtime != actionsRuntime) {
                registerActions(runtime);
                actionsRuntime = runtime;
            }
            runtime.input().updateHover(runtime.root(), mouseX, mouseY);
            UiNode keyboardTooltip = runtime.input().state().focusedNode();
            updateAdobeTooltipHover(keyboardTooltip != null && keyboardTooltip.state().focusVisible()
                    ? keyboardTooltip : runtime.input().state().hoveredNode());
            syncOverlayFocus();
            if (sliderDragging) adjustSlider(mouseX);
            if (adobeSliderDragging) adjustAdobeSlider(mouseX);
            updateTextInputArea();
        });
    }

    private static UiNode find(UiNode root, String key) {
        if (root == null) return null;
        if (key.equals(root.key())) return root;
        for (UiNode child : root.children()) {
            UiNode found = find(child, key);
            if (found != null) return found;
        }
        return null;
    }

    /** Reuse the arc nodes allocated by the initial Spectrum template. Angle props are
     * consumed directly by UiShapeRenderer and do not affect layout or input bounds. */
    private void patchAdobeProgressCircles() {
        if (runtime == null || !"adobe".equals(kit) || !"progress-circle".equals(page)) return;
        double phase = (System.nanoTime() % 1_600_000_000L) / 1_600_000_000.0;
        float start = (float) (phase * 360.0 - 90.0);
        float sweep = (float) (78.0 + 42.0 * (0.5 - 0.5 * Math.cos(phase * 4.0 * Math.PI)));
        Map<String, Map<String, ?>> patches = new LinkedHashMap<>(3);
        for (String size : new String[]{"S", "M", "L"}) {
            patches.put("s2:progress-circle:spin:" + size + ":value",
                    Map.of("startAngle", start, "endAngle", start + sweep));
        }
        runtime.patchPropsByKey(patches);
    }

    /**
     * Accordion disclosure is paint/layout-geometry-only while animating. All four
     * clipped panels stay in the retained tree (including closed panels), so their
     * bounds can be updated without baking the entire gallery every frame.
     * The header/button and its descendants must translate together for hit-testing.
     */
    private static void moveSubtreeToY(UiNode node, float y) {
        if (node == null) return;
        float delta = y - node.bounds().y();
        if (Math.abs(delta) <= .0001f) return;
        translateSubtree(node, delta);
    }

    private static void translateSubtree(UiNode node, float deltaY) {
        UiBounds old = node.bounds();
        node.setBounds(new UiBounds(old.x(), old.y() + deltaY, old.width(), old.height()));
        for (UiNode child : node.children()) translateSubtree(child, deltaY);
    }

    private UiNode accordionNode(String key) {
        return adobeAccordionCachedNodes.get(key);
    }

    private void patchAdobeAccordion() {
        if (runtime == null || !"adobe".equals(kit) || !"accordion".equals(page)) {
            adobeAccordionCachedRoot = null;
            adobeAccordionCachedRuntime = null;
            adobeAccordionCachedNodes.clear();
            return;
        }
        if (adobeAccordionCachedRoot == null || adobeAccordionCachedRuntime != runtime) {
            adobeAccordionCachedNodes.clear();
            UiNode rootNode = runtime.root();
            for (String id : new String[]{"s2:accordion", "s2:accordion:bottom-divider",
                    "s2g:accordion-divider", "s2g:accordion-multiple-title",
                    "s2:accordion:multiple", "s2g:accordion-caption",
                    "s2g:accordion:quiet-separator", "s2g:accordion:quiet-heading",
                    "s2:accordion:quiet"}) {
                adobeAccordionCachedNodes.put(id, find(rootNode, id));
            }
            for (int i = 0; i < adobeAccordionProgress.length; i++) {
                String id = "s2:accordion:item:" + i;
                for (String suffix : new String[]{"", ":divider", ":panel", ":chevron:right", ":chevron:down", ":content"})
                    adobeAccordionCachedNodes.put(id+suffix, find(rootNode, id+suffix));
                adobeAccordionPatchedProgress[i] = Float.NaN;
            }
            adobeAccordionCachedRoot = accordionNode("s2:accordion");
            adobeAccordionCachedRuntime = runtime;
        }
        UiNode root = adobeAccordionCachedRoot;
        if (root == null) return;
        float y = root.bounds().y();
        Map<String, Map<String, ?>> colors = null;
        for (int i = 0; i < adobeAccordionProgress.length; i++) {
            String prefix = "s2:accordion:item:" + i;
            float progress = adobeAccordionProgress[i];
            moveSubtreeToY(accordionNode(prefix + ":divider"), y);
            UiNode row = accordionNode(prefix);
            moveSubtreeToY(row, y);
            y += row != null ? row.bounds().height() : 48f;
            UiNode contentNode = accordionNode(prefix + ":content");
            float contentHeight = contentNode != null ? contentNode.bounds().height() + 16f : 86f;
            UiNode panel = accordionNode(prefix + ":panel");
            if (panel != null) {
                moveSubtreeToY(panel, y);
                UiBounds b = panel.bounds();
                float clippedHeight = contentHeight * progress;
                if (Math.abs(b.height() - clippedHeight) > .0001f)
                    panel.setBounds(new UiBounds(b.x(), b.y(), b.width(), clippedHeight));
            }
            if (Float.isNaN(adobeAccordionPatchedProgress[i])
                    || Math.abs(progress - adobeAccordionPatchedProgress[i]) > .00001f) {
                if (colors == null) colors = new LinkedHashMap<>();
                colors.put(prefix + ":chevron:right", Map.of("tint", alphaColor("#FFB9B9B9", 1f-progress)));
                colors.put(prefix + ":chevron:down", Map.of("tint", alphaColor("#FFB9B9B9", progress)));
                colors.put(prefix + ":content", Map.of("color", alphaColor(i == 2 ? "#FF818181" : "#FFB9B9B9", Math.min(1f, progress*2.25f))));
                adobeAccordionPatchedProgress[i] = progress;
            }
            y += contentHeight * progress;
        }
        moveSubtreeToY(accordionNode("s2:accordion:bottom-divider"), y);
        float height = y - root.bounds().y() + 1f;
        UiBounds old = root.bounds();
        if (Math.abs(old.height()-height) > .0001f)
            root.setBounds(new UiBounds(old.x(),old.y(),old.width(),height));
        // Absolute-positioned siblings must follow the animated disclosure.
        moveSubtreeToY(accordionNode("s2g:accordion-divider"), y + 35f);
        moveSubtreeToY(accordionNode("s2g:accordion-multiple-title"), y + 52f);
        moveSubtreeToY(accordionNode("s2:accordion:multiple"), y + 112f);
        moveSubtreeToY(accordionNode("s2g:accordion-caption"), y + 177f);
        moveSubtreeToY(accordionNode("s2g:accordion:quiet-separator"), y + 227f);
        moveSubtreeToY(accordionNode("s2g:accordion:quiet-heading"), y + 242f);
        moveSubtreeToY(accordionNode("s2:accordion:quiet"), y + 307f);
        if (colors != null) runtime.patchPropsByKey(colors);
    }

    private static String alphaColor(String argb, float progress) {
        int alpha = Math.max(0, Math.min(255, Math.round(255f * progress)));
        return "#" + HEX_DIGITS[alpha >>> 4] + HEX_DIGITS[alpha & 15] + argb.substring(3);
    }

    /** Patch drawn geometry using the live node's own computed layout, never hard-coded screen coordinates. */
    private Map<String, UiBounds> animatedBounds() {
        if (runtime == null || runtime.root() == null) return Map.of();
        Map<String, UiBounds> patches = new LinkedHashMap<>();
        UiNode tabNode = "google".equals(kit) && "navigation".equals(page)
                ? find(runtime.root(), "lab:tabs") : null;
        if (tabNode != null) {
            UiBounds b = tabNode.bounds();
            float itemWidth = b.width() / 3f;
            patches.put("lab:tabs:indicator", new UiBounds(
                    b.x() + animatedTab * itemWidth + 12f, b.y() + 45f,
                    Math.max(1f, itemWidth - 24f), 3f));
        }
        UiNode toggle = "google".equals(kit) && "selection".equals(page)
                ? find(runtime.root(), "lab:switch") : null;
        if (toggle != null) {
            UiBounds b = toggle.bounds();
            float handleSize = 16f + 8f * animatedSwitch;
            float left = 8f + 16f * animatedSwitch;
            patches.put("lab:switch:handle", new UiBounds(
                    b.x() + left, b.y() + (32f - handleSize) * .5f,
                    handleSize, handleSize));
        }
        if ("adobe".equals(kit) && "tabs".equals(page)) {
            UiNode spectrumTabs = find(runtime.root(), "s2:tabs");
            if (spectrumTabs != null) {
                UiBounds b = spectrumTabs.bounds();
                float item = b.width() / 3f;
                patches.put("s2:tabs:indicator", new UiBounds(
                        b.x() + animatedAdobeTab * item + 8f,
                        b.y() + 45f, Math.max(1f, item - 16f), 3f));
            }
        }
        return patches;
    }

    /** The popup keyboard cursor is an actual focused UiNode, not only a
     * painted outline. Kept in sync after the retained tree is reconciled. */
    private void syncOverlayFocus() {
        if (runtime == null || runtime.root() == null || overlay.isEmpty()) return;
        String key = null;
        if ("menu".equals(overlay)) {
            List<MenuEntry> entries = activeMenuEntries();
            int index = submenu.isEmpty() ? overlayIndex : submenuIndex;
            if (index >= 0 && index < entries.size() && entries.get(index).navigable()) {
                key = (submenu.isEmpty() ? "m3:popup:" : "m3:submenu:") + entries.get(index).id();
            }
        } else if ("adobe-menu".equals(overlay)) {
            String[] ids = {"rename", "duplicate", "share"};
            int[] indices = {1, 2, 4};
            for (int i = 0; i < indices.length; i++) {
                if (overlayIndex == indices[i]) key = "s2:menu:item:" + ids[i];
            }
        } else if ("adobe-breadcrumbs".equals(overlay)) {
            List<Integer> indices = adobeBreadcrumbHiddenIndices();
            if (!indices.isEmpty()) key = "s2:breadcrumbs:option:" +
                    ADOBE_BREADCRUMB_IDS[indices.get(Math.min(indices.size() - 1, overlayIndex))];
        } else if ("adobe-standard-dialog".equals(overlay)) {
            key = "s2:standard-dialog:" + (overlayIndex == 0 ? "cancel" : "confirm");
        } else if ("adobe-dialog".equals(overlay)) {
            String[] ids = adobeDialogActionIds();
            key = "s2:dialog:" + ids[Math.floorMod(overlayIndex, ids.length)];
        } else if ("adobe-picker".equals(overlay)) {
            String[] ids = {"low", "medium", "high"};
            key = "s2:picker:option:" + ids[Math.max(0, Math.min(ids.length - 1, overlayIndex))];
        } else if ("select".equals(overlay)) {
            String[] ids = {"system", "light", "dark", "dynamic"};
            key = "m3:popup:" + ids[Math.max(0, Math.min(ids.length - 1, overlayIndex))];
        } else if ("dialog".equals(overlay)) {
            key = "m3:dialog:action:" + Math.max(0, Math.min(1, overlayIndex));
        }
        if (key == null) return;
        UiNode node = find(runtime.root(), key);
        if (node != null && node != runtime.input().state().focusedNode())
            runtime.input().focus(node);
    }

    private void event(String name) {
        clicks++;
        last = name;
    }

    private void registerActions(UiRuntime ui) {
        for (String id : new String[]{"ps", "ai", "pdf"}) {
            ui.actions().register("lab:adobe:list-view:toggle:" + id, action -> {
                if (!adobeListViewSelected.add(id)) adobeListViewSelected.remove(id);
                return true;
            });
        }
        for (String id : new String[]{"grid", "list", "detail"}) {
            ui.actions().register("lab:adobe:list-view:single:" + id, action -> {
                adobeListViewSingle = id; return true;
            });
        }
        for (String id : new String[]{"brand", "campaign", "logo"}) {
            ui.actions().register("lab:adobe:card-view:toggle:" + id, action -> {
                if (!adobeCardViewSelected.add(id)) adobeCardViewSelected.remove(id);
                return true;
            });
        }
        for (String id : new String[]{"recent", "shared"}) {
            ui.actions().register("lab:adobe:card-view:single:" + id, action -> {
                adobeCardViewSingle = id; return true;
            });
        }
        for (String id : new String[]{"fast", "balanced", "high"}) {
            ui.actions().register("lab:adobe:radio-group:" + id, action -> {
                adobeRadioGroupSelected = id; return true;
            });
        }
        for (String id : new String[]{"portrait", "landscape"}) {
            ui.actions().register("lab:adobe:radio-group:orientation:" + id, action -> {
                adobeRadioGroupOrientation = id; return true;
            });
        }
        for (String variant : new String[]{"help", "info"}) {
            ui.actions().register("lab:adobe:context-help:open:" + variant, action -> {
                openOverlay("adobe-context-help-" + variant, "s2:help:" + variant);
                return true;
            });
        }
        ui.actions().register("lab:adobe:context-help:close", action -> {
            finishOverlayClose(); return true;
        });
        for (String actionId : new String[]{"docs", "details", "learn"}) {
            ui.actions().register("lab:adobe:link-button:" + actionId, action -> {
                adobeLastAction = "Link button: " + actionId; return true;
            });
        }
        for (String id : new String[]{"budget", "roadmap", "proposal", "welcome", "locked"}) {
            ui.actions().register("lab:adobe:table:row:" + id, action -> {
                if ("locked".equals(id)) return true;
                if (!adobeTableSelected.add(id)) adobeTableSelected.remove(id);
                return true;
            });
        }
        ui.actions().register("lab:adobe:table:all", action -> {
            if (adobeTableSelected.size() == 4) adobeTableSelected.clear();
            else adobeTableSelected.addAll(List.of("budget", "roadmap", "proposal", "welcome"));
            return true;
        });
        for (String id : new String[]{"name", "type", "size"}) {
            ui.actions().register("lab:adobe:table:sort:" + id, action -> {
                adobeTableDirection = id.equals(adobeTableSort) && "asc".equals(adobeTableDirection)
                        ? "desc" : "asc";
                adobeTableSort = id;
                return true;
            });
        }
        for (String density : new String[]{"compact", "regular", "spacious"}) {
            ui.actions().register("lab:adobe:table:density:" + density, action -> {
                adobeTableDensity = density; return true;
            });
        }
        for (String id : new String[]{"budget", "roadmap", "proposal"}) {
            ui.actions().register("lab:adobe:table:quiet:" + id, action -> {
                adobeTableQuiet = id; return true;
            });
        }
        adobeNumberInput.setText(Integer.toString(adobeNumberValue));
        ui.actions().register("lab:adobe:number:focus", action -> {
            focusTextField("adobe-number", false, 0f); return true;
        });
        ui.actions().register("lab:adobe:number:inc", action -> {
            stepAdobeNumber(1); return true;
        });
        ui.actions().register("lab:adobe:number:dec", action -> {
            stepAdobeNumber(-1); return true;
        });
        ui.actions().register("lab:adobe:combo:open", action -> {
            if (!"adobe-combo".equals(overlay)) {
                adobeComboInput.clear();
                openOverlay("adobe-combo", "s2:combo:input");
            }
            focusTextField("adobe-combo", false, 0f);
            return true;
        });
        ui.actions().register("lab:adobe:combo:close", action -> {
            finishOverlayClose(); clearTextFocus(); return true;
        });
        for (String id : ADOBE_COMBO_IDS) {
            ui.actions().register("lab:adobe:combo:choose:" + id, action -> {
                chooseCombo(id); return true;
            });
        }
        ui.actions().register("lab:adobe:calendar:previous", action -> { calendarMove(-1); return true; });
        ui.actions().register("lab:adobe:calendar:next", action -> { calendarMove(1); return true; });
        for (int day = 1; day <= 31; day++) {
            final int selectedDay = day;
            ui.actions().register("lab:adobe:calendar:day:" + day, action -> {
                calendarChoose(selectedDay); return true;
            });
        }
        for (String mode : new String[]{"single", "range"}) {
            ui.actions().register("lab:adobe:date:open:" + mode, action -> {
                if ("single".equals(mode) && adobeDateSingle != null)
                    adobeCalendarMonth = YearMonth.from(adobeDateSingle);
                if ("range".equals(mode) && adobeDateRangeStart != null)
                    adobeCalendarMonth = YearMonth.from(adobeDateRangeStart);
                adobeCalendarFocusDay = "single".equals(mode) && adobeDateSingle != null
                        ? adobeDateSingle.getDayOfMonth() : adobeDateRangeStart != null
                        ? adobeDateRangeStart.getDayOfMonth() : 1;
                openOverlay("adobe-date-" + mode, "s2:date:" + mode);
                return true;
            });
        }
        ui.actions().register("lab:adobe:date:close", action -> {
            adobeDateRangeDraftStart = null;
            finishOverlayClose(); return true;
        });
        ui.actions().register("lab:adobe:date:clear", action -> {
            adobeDateRangeStart = null; adobeDateRangeEnd = null;
            adobeDateRangeDraftStart = null; return true;
        });
        for (String placement : new String[]{"bottom", "top", "left", "right"}) {
            ui.actions().register("lab:adobe:popover:open:" + placement, action -> {
                adobePopoverPlacement = placement;
                openOverlay("adobe-popover", "s2:popover:trigger:" + placement);
                return true;
            });
        }
        ui.actions().register("lab:adobe:popover:close", action -> {
            finishOverlayClose(); return true;
        });
        for (String size : new String[]{"S", "M", "L"}) {
            ui.actions().register("lab:adobe:standard-dialog:open:" + size, action -> {
                adobeStandardDialogSize = size;
                openOverlay("adobe-standard-dialog", "s2:standard-dialog:trigger:" + size);
                return true;
            });
        }
        ui.actions().register("lab:adobe:standard-dialog:cancel", action -> {
            adobeLastAction = "Cancelled"; finishOverlayClose(); return true;
        });
        ui.actions().register("lab:adobe:standard-dialog:confirm", action -> {
            adobeLastAction = "Saved project settings"; finishOverlayClose(); return true;
        });
        ui.actions().register("lab:catalog:search:focus", action -> {
            focusTextField("catalog-search", false, 0f);
            return true;
        });
        ui.actions().register("lab:catalog:search:clear", action -> {
            catalogEditor().clear();
            return true;
        });
        ui.actions().register("lab:kit:google", action -> {
            kit = "google";
            page = "buttons";
            finishOverlayClose();
            clearTextFocus();
            tooltip = "";
            snackbar = false;
            return true;
        });
        ui.actions().register("lab:kit:adobe", action -> {
            kit = "adobe";
            page = "buttons";
            clearTextFocus();
            finishOverlayClose();
            tooltip = "";
            snackbar = false;
            return true;
        });
        for (String variant : new String[]{"accent","primary","secondary","negative",
                "small","medium","large"}) {
            ui.actions().register("lab:adobe:button:" + variant,
                    action -> { event("adobe:button:" + variant); return true; });
        }
        for (String id : new String[]{"primary", "secondary", "quiet"}) {
            ui.actions().register("lab:adobe:link:" + id,
                    action -> { event("adobe:link:" + id); return true; });
        }
        for (String id : new String[]{"design", "photography", "video"}) {
            ui.actions().register("lab:adobe:check-group:" + id, action -> {
                if (!adobeCheckboxGroup.add(id)) adobeCheckboxGroup.remove(id);
                return true;
            });
        }
        for (String id : new String[]{"list", "grid", "details"}) {
            ui.actions().register("lab:adobe:segments:" + id, action -> {
                adobeSegmentedView = id;
                return true;
            });
        }
        ui.actions().register("lab:adobe:action:edit", action -> { event("adobe:edit"); return true; });
        ui.actions().register("lab:adobe:action:preview", action -> {
            adobeAction = !adobeAction;
            return true;
        });
        for (String id : new String[]{"list", "grid", "details"}) {
            ui.actions().register("lab:adobe:action-group:single:" + id, action -> {
                adobeActionGroupSingle = id;
                return true;
            });
        }
        for (String id : new String[]{"pin", "favorite", "lock"}) {
            ui.actions().register("lab:adobe:action-group:multi:" + id, action -> {
                if (!adobeActionGroupMulti.add(id)) adobeActionGroupMulti.remove(id);
                return true;
            });
        }
        for (String id : new String[]{"edit", "copy"}) {
            ui.actions().register("lab:adobe:action-group:action:" + id, action -> {
                event("adobe:action-group:" + id);
                return true;
            });
        }
        for (String id : new String[]{"cancel", "preview", "save", "import"}) {
            ui.actions().register("lab:adobe:button-group:" + id, action -> {
                event("adobe:button-group:" + id);
                return true;
            });
        }
        ui.actions().register("lab:adobe:action-bar:clear", action -> {
            adobeActionBarSelected = 0; return true;
        });
        ui.actions().register("lab:adobe:action-bar:select", action -> {
            adobeActionBarSelected = 3; return true;
        });
        for (String id : new String[]{"edit", "copy", "share", "export"}) {
            ui.actions().register("lab:adobe:action-bar:" + id, action -> {
                event("adobe:action-bar:" + id); return true;
            });
        }
        for (String id : new String[]{"import", "upload", "clear"}) {
            ui.actions().register("lab:adobe:illustrated:" + id, action -> {
                event("adobe:illustrated:" + id); return true;
            });
        }
        ui.actions().register("lab:adobe:checkbox", action -> {
            adobeChecked = !adobeChecked; return true;
        });
        ui.actions().register("lab:adobe:switch", action -> {
            adobeEnabled = !adobeEnabled; return true;
        });
        for (int i = 0; i < 2; i++) {
            final int index = i;
            ui.actions().register("lab:adobe:states:radio:" + i, action -> {
                adobeStateRadio = index; return true;
            });
        }
        ui.actions().register("lab:adobe:states:check:off", action -> {
            adobeStateCheckOff = !adobeStateCheckOff; return true;
        });
        ui.actions().register("lab:adobe:states:check:on", action -> {
            adobeStateCheckOn = !adobeStateCheckOn; return true;
        });
        ui.actions().register("lab:adobe:states:switch:off", action -> {
            adobeStateSwitchOff = !adobeStateSwitchOff; return true;
        });
        ui.actions().register("lab:adobe:states:switch:on", action -> {
            adobeStateSwitchOn = !adobeStateSwitchOn; return true;
        });
        for (int i=0;i<3;i++) {
            final int index = i;
            ui.actions().register("lab:adobe:radio:" + i, action -> {
                adobeRadio = index; return true;
            });
        }
        for (String value : new String[]{"files","activity","sharing"}) {
            ui.actions().register("lab:adobe:tab:" + value, action -> {
                adobeTab = value; return true;
            });
        }
        for (String value : new String[]{"projects", "recents", "shared", "assets"}) {
            ui.actions().register("lab:adobe:nav:" + value, action -> {
                adobeNav = value;
                return true;
            });
        }
        for (int i : new int[]{0, 1, 3}) {
            final int index = i;
            ui.actions().register("lab:adobe:accordion:" + i, action -> {
                boolean expand = !adobeAccordionExpanded[index];
                if (expand && !adobeAccordionMultiple) {
                    for (int j = 0; j < adobeAccordionExpanded.length; j++)
                        adobeAccordionExpanded[j] = false;
                }
                adobeAccordionExpanded[index] = expand;
                return true;
            });
        }
        ui.actions().register("lab:adobe:accordion:multiple", action -> {
            adobeAccordionMultiple = !adobeAccordionMultiple;
            if (!adobeAccordionMultiple) {
                int keep = -1;
                for (int i = 0; i < adobeAccordionExpanded.length; i++) {
                    if (adobeAccordionExpanded[i]) {
                        if (keep < 0) keep = i;
                        else adobeAccordionExpanded[i] = false;
                    }
                }
            }
            return true;
        });
        for (int i = 0; i < ADOBE_BREADCRUMB_IDS.length; i++) {
            final int index = i;
            ui.actions().register("lab:adobe:breadcrumbs:choose:" + ADOBE_BREADCRUMB_IDS[i], action -> {
                adobeBreadcrumbCurrent = index;
                adobeTreeSelected = ADOBE_BREADCRUMB_IDS[index];
                finishOverlayClose();
                return true;
            });
        }
        for (String id : new String[]{"home", "projects", "assets", "campaign"}) {
            ui.actions().register("lab:adobe:tree:toggle:" + id, action -> {
                if (!adobeTreeExpanded.add(id)) adobeTreeExpanded.remove(id);
                return true;
            });
        }
        for (String id : new String[]{"home", "projects", "assets", "campaign", "images",
                "logos", "drafts", "shared"}) {
            ui.actions().register("lab:adobe:tree:select:" + id, action -> {
                adobeTreeSelected = id;
                for (int i = 0; i < ADOBE_BREADCRUMB_IDS.length; i++) {
                    if (ADOBE_BREADCRUMB_IDS[i].equals(id)) {
                        adobeBreadcrumbCurrent = i;
                        break;
                    }
                }
                return true;
            });
        }
        ui.actions().register("lab:adobe:tree:expand-all", action -> {
            adobeTreeExpanded.addAll(ADOBE_TREE_CHILDREN.keySet());
            return true;
        });
        ui.actions().register("lab:adobe:tree:collapse-all", action -> {
            adobeTreeExpanded.clear();
            return true;
        });
        ui.actions().register("lab:adobe:breadcrumbs:reset", action -> {
            adobeBreadcrumbCurrent = ADOBE_BREADCRUMB_IDS.length - 1;
            adobeTreeSelected = ADOBE_BREADCRUMB_IDS[adobeBreadcrumbCurrent];
            adobeTreeExpanded.addAll(ADOBE_TREE_CHILDREN.keySet());
            return true;
        });
        ui.actions().register("lab:adobe:breadcrumbs:overflow", action -> {
            openOverlay("adobe-breadcrumbs", "s2:breadcrumbs:overflow");
            return true;
        });
        ui.actions().register("lab:adobe:breadcrumbs:close", action -> {
            closeOverlay(); return true;
        });
        ui.actions().register("lab:adobe:nav:expand", action -> {
            adobeLibrariesExpanded = !adobeLibrariesExpanded;
            return true;
        });
        ui.actions().register("lab:adobe:focus:field", action -> {
            focusTextField("adobe-field", false, 0f); return true;
        });
        ui.actions().register("lab:adobe:focus:search", action -> {
            focusTextField("adobe-search", false, 0f); return true;
        });
        ui.actions().register("lab:adobe:search:clear", action -> {
            adobeSearchInput.clear(); return true;
        });
        ui.actions().register("lab:adobe:picker:open", action -> {
            openOverlay("adobe-picker", "s2:picker"); return true;
        });
        ui.actions().register("lab:adobe:picker:close", action -> {
            closeOverlay(); return true;
        });
        for (String value : new String[]{"low", "medium", "high"}) {
            ui.actions().register("lab:adobe:picker:choose:" + value, action -> {
                adobePicker = value;
                closeOverlay();
                return true;
            });
        }
        for (String id : new String[]{"pin", "favorite", "filter"}) {
            ui.actions().register("lab:adobe:toggle:" + id, action -> {
                if (!adobeToggles.add(id)) adobeToggles.remove(id);
                return true;
            });
        }
        ui.actions().register("lab:adobe:card:toggle", action -> {
            adobeCardSelected = !adobeCardSelected;
            adobeLastAction = adobeCardSelected ? "Selected card" : "Unselected card";
            return true;
        });
        ui.actions().register("lab:adobe:card:review", action -> {
            adobeLastAction = "Review card";
            return true;
        });
        ui.actions().register("lab:adobe:menu:open", action -> {
            openOverlay("adobe-menu", "s2:action-menu");
            return true;
        });
        for (String variant : new String[]{"neutral", "informative", "positive", "negative"}) {
            ui.actions().register("lab:adobe:toast:show:" + variant, action -> {
                showAdobeToast(variant);
                return true;
            });
        }
        // Parameterized actions are registered once; each toast has a stable ID.
        for (String placement : new String[]{"top", "top-end", "bottom", "bottom-end"}) {
            ui.actions().register("lab:adobe:toast:placement:" + placement, action -> {
                adobeToastPlacement = placement;
                return true;
            });
        }
        for (String variant : new String[]{"neutral", "informative", "negative"})
            ui.actions().register("lab:adobe:tooltip:noop:" + variant, action -> true);
        ui.actions().register("spectrum.toast.close", action -> {
            try { return dismissAdobeToast(Integer.parseInt(action.ref().argument())); }
            catch (NumberFormatException ignored) { return false; }
        });
        ui.actions().register("spectrum.toast.retry", action -> {
            try {
                int id = Integer.parseInt(action.ref().argument());
                if (!dismissAdobeToast(id)) return false;
                adobeLastAction = "Retry requested";
                return true;
            } catch (NumberFormatException ignored) { return false; }
        });
        for (String variant : new String[]{"confirmation", "information", "warning",
                "destructive", "error"}) {
            ui.actions().register("lab:adobe:dialog:open:" + variant, action -> {
                adobeDialogVariant = variant;
                openOverlay("adobe-dialog", "s2:dialog-trigger:" + variant);
                return true;
            });
        }
        ui.actions().register("lab:adobe:overlay:close", action -> {
            closeOverlay(); return true;
        });
        for (String command : new String[]{"rename", "duplicate", "share"}) {
            ui.actions().register("lab:adobe:menu:choose:" + command, action -> {
                adobeLastAction = command;
                closeOverlay();
                return true;
            });
        }
        ui.actions().register("lab:adobe:dialog:cancel", action -> {
            adobeLastAction = "Cancelled";
            closeOverlay();
            return true;
        });
        ui.actions().register("lab:adobe:dialog:secondary", action -> {
            adobeLastAction = "Remind me later";
            closeOverlay();
            return true;
        });
        ui.actions().register("lab:adobe:dialog:confirm", action -> {
            adobeLastAction = switch (adobeDialogVariant) {
                case "destructive" -> "Deleted";
                case "warning" -> "Continued despite warning";
                case "information" -> "Continued";
                case "error" -> "Acknowledged";
                default -> "Enabled";
            };
            closeOverlay();
            return true;
        });
        for (String shape : new String[]{"square", "rectangle"}) {
            ui.actions().register("lab:adobe:swatch:shape:" + shape, action -> {
                adobeSwatchShape = shape; return true;
            });
        }
        for (String rounding : new String[]{"none", "default", "full"}) {
            ui.actions().register("lab:adobe:swatch:rounding:" + rounding, action -> {
                adobeSwatchRounding = rounding; return true;
            });
        }
        for (String density : new String[]{"compact", "regular", "spacious"}) {
            ui.actions().register("lab:adobe:swatch:density:" + density, action -> {
                adobeSwatchDensity = density; return true;
            });
        }
        for (int i = 0; i < 12; i++) {
            String id = Integer.toString(i);
            ui.actions().register("lab:adobe:swatch:select:" + id, action -> {
                adobeSwatchSelected = id; return true;
            });
        }
        ui.actions().register("lab:adobe:inline-alert:retry", action -> {
            event("adobe:inline-alert:retry"); return true;
        });

        ui.actions().register("lab:adobe:color:noop", action -> true);
        ui.actions().register("lab:adobe:color:field:focus", action -> {
            if (adobeColorInput.text().isBlank()) adobeColorInput.setText(adobeColorHex());
            focusTextField("adobe-color", false, 0f); return true;
        });
        for (int i = 0; i < ADOBE_COLOR_PRESETS.length; i++) {
            final int selectedIndex = i;
            ui.actions().register("lab:adobe:color:preset:" + i, action -> {
                adobeColorPreset = Integer.toString(selectedIndex);
                setAdobeColorHex(ADOBE_COLOR_PRESETS[selectedIndex]);
                adobeColorInput.setText(ADOBE_COLOR_PRESETS[selectedIndex]);
                return true;
            });
        }
        ui.actions().register("lab:adobe:range:focus", action -> true);
        for (String id : new String[]{"bold", "italic", "underline"}) {
            ui.actions().register("lab:adobe:toggle-group:multi:" + id, action -> {
                if (!adobeToggleGroupMulti.add(id)) adobeToggleGroupMulti.remove(id);
                return true;
            });
        }
        for (String id : new String[]{"left", "center", "right"}) {
            ui.actions().register("lab:adobe:toggle-group:single:" + id, action -> {
                adobeToggleGroupSingle = id; return true;
            });
        }
        for (String id : new String[]{"aws", "azure", "gcp"}) {
            ui.actions().register("lab:adobe:select-box:single:" + id, action -> {
                adobeSelectBoxSingle = id; return true;
            });
        }
        for (String id : new String[]{"storage", "cdn"}) {
            ui.actions().register("lab:adobe:select-box:multi:" + id, action -> {
                if (!adobeSelectBoxMulti.add(id)) adobeSelectBoxMulti.remove(id);
                return true;
            });
        }
        for (String id : new String[]{"design", "photo", "video", "brand", "export", "review"}) {
            ui.actions().register("lab:adobe:tag-group:select:" + id, action -> {
                if (adobeTagItems.contains(id)) {
                    if (!adobeTagSelected.add(id)) adobeTagSelected.remove(id);
                }
                return true;
            });
            ui.actions().register("lab:adobe:tag-group:remove:" + id, action -> {
                adobeTagItems.remove(id); adobeTagSelected.remove(id); return true;
            });
        }
        ui.actions().register("lab:adobe:tag-group:reset", action -> {
            adobeTagItems.clear(); adobeTagItems.addAll(List.of(
                    "design", "photo", "video", "brand", "export", "review"));
            adobeTagSelected.clear(); adobeTagSelected.add("design"); return true;
        });
        for (String id : new String[]{"requirements", "setup"}) {
            ui.actions().register("lab:adobe:disclosure:" + id, action -> {
                if (!adobeDisclosures.add(id)) adobeDisclosures.remove(id);
                return true;
            });
        }
        ui.actions().register("lab:adobe:slider:focus", action -> {
            event("adobe:slider"); return true;
        });
        // Register every page exposed by the two kit navigations. The original
        // Adobe-only list silently broke five Google Material 3 pages.
        for (String p : new String[]{"buttons","selection","inputs","navigation",
                "surfaces","overlays","states", "action-buttons","action-group","action-bar","button-group","illustrated-message","checkbox","checkbox-group","segmented-control","switch","radio",
                "tabs","side-navigation","text-field","search-field","tags","picker",
                "slider","progress-bar","meter","cards","status-light","menu",
                "alert-dialog","links","accordion","breadcrumbs","tree-view","toast","tooltip","inline-alert","skeleton","labeled-value","color-swatch",
                "badges","avatars","avatar-group","dividers","progress-circle","toggle-button","toggle-button-group","tag-group","select-box-group","disclosure","range-slider","table","number-field","combo-box","calendar","date-picker","color-area","color-slider","color-wheel","color-field","color-swatch-picker","list-view","card-view","radio-group","contextual-help","link-button"}) {
            ui.actions().register("lab:nav:" + p, action -> {
                page = p;
                clearTextFocus();
                tooltip = "";
                finishOverlayClose();
                return true;
            });
        }
        for (String p : new String[]{"filled","tonal","elevated","outlined","text","icon",
                "fab","extended","enabled","save","cancel"}) {
            final String id = p;
            ui.actions().register("lab:clicked:" + id, action -> { event(id); return true; });
        }
        ui.actions().register("lab:toggle", action -> { enabled = !enabled; event("switch"); return true; });
        ui.actions().register("lab:check", action -> { checked = !checked; event("checkbox"); return true; });
        for (int i=0; i<3; i++) {
            final int index=i;
            ui.actions().register("lab:radio:"+i, action -> {radio=index; event("radio"); return true;});
        }
        for (String value : new String[]{"all","design","code","assist"}) {
            ui.actions().register("lab:chip:"+value, action -> {chip=value; event("chip"); return true;});
        }
        for (String value : new String[]{"day","week","month"}) {
            ui.actions().register("lab:segment:"+value, action -> {segment=value; event("segment"); return true;});
        }
        for (String value : new String[]{"overview","activity","settings"}) {
            ui.actions().register("lab:tab:"+value, action -> {tab=value; event("tabs"); return true;});
        }
        for (String value : new String[]{"profile","notifications","settings"}) {
            ui.actions().register("lab:list:"+value, action -> {list=value; event("list"); return true;});
        }
        for (String value : new String[]{"filled","outlined","search"}) {
            ui.actions().register("lab:focus:"+value, action -> {
                focusTextField(value, false, 0f);
                return true;
            });
        }
        ui.actions().register("lab:overlay:menu", action -> { openOverlay("menu", "lab:menu-trigger"); return true; });
        ui.actions().register("lab:overlay:select", action -> { openOverlay("select", "lab:select-field"); return true; });
        ui.actions().register("lab:overlay:dialog", action -> { openOverlay("dialog", "lab:dialog-trigger"); return true; });
        ui.actions().register("lab:overlay:close", action -> { closeOverlay(); return true; });
        ui.actions().register("lab:overlay:consume", action -> true);
        ui.actions().register("lab:snackbar:show", action -> {
            snackbar = true;
            snackMessage = "Preferences saved";
            snackbarStarted = System.nanoTime();
            return true;
        });
        ui.actions().register("lab:snackbar:undo", action -> {
            snackbar = false;
            event("snackbar:undo");
            return true;
        });
        ui.actions().register("lab:snackbar:dismiss", action -> { snackbar = false; return true; });
        ui.actions().register("lab:tooltip:noop", action -> true);
        ui.actions().register("lab:dialog:cancel", action -> { closeOverlay(); return true; });
        ui.actions().register("lab:dialog:confirm", action -> { closeOverlay(); event("delete"); return true; });
        for (MenuEntry entry : MENU_ROOT) {
            if (!entry.navigable()) continue;
            ui.actions().register("lab:menu:root:" + entry.id(), action -> {
                activateRootItem(entry.id()); return true;
            });
        }
        for (MenuEntry entry : VIEW_MENU) {
            ui.actions().register("lab:menu:sub:" + entry.id(), action -> {
                activateSubmenuItem(entry.id()); return true;
            });
        }
        for (MenuEntry entry : SORT_MENU) {
            ui.actions().register("lab:menu:sub:" + entry.id(), action -> {
                activateSubmenuItem(entry.id()); return true;
            });
        }
        for (String value : new String[]{"system", "light", "dark", "dynamic"}) {
            ui.actions().register("lab:select:"+value, action -> { selectValue=value; closeOverlay(); return true; });
        }
        for (String value : new String[]{"home", "favorites", "settings"}) {
            ui.actions().register("lab:rail:"+value, action -> { rail=value; return true; });
        }
        for (String value : new String[]{"account", "appearance", "audio", "controls", "language", "notifications", "privacy"}) {
            ui.actions().register("lab:result:"+value, action -> {
                searchSelection=value;
                clearTextFocus();
                event("search:"+value);
                return true;
            });
        }
    }

    private void openOverlay(String type, String anchorKey) {
        clearTextFocus();
        UiNode anchor = runtime != null ? find(runtime.root(), anchorKey) : null;
        overlayAnchor = anchorKey;
        if (anchor != null) {
            UiBounds b = anchor.bounds();
            UiBounds rootBounds = runtime.root().bounds();
            // Runtime UiBounds are screen-space. The script overlay is viewport-local.
            anchorX = b.x() - rootBounds.x();
            anchorY = b.y() - rootBounds.y();
            anchorWidth = b.width();
            anchorHeight = b.height();
        }
        overlay = type;
        if ("adobe-dialog".equals(type)) {
            adobeModalProgress = 0f;
            adobeDialogExiting = false;
        }
        tooltip = "";
        overlayIndex = "adobe-breadcrumbs".equals(type) ? 0 :
                "adobe-menu".equals(type) ? 1 :
                "adobe-dialog".equals(type) ? 0 :
                "adobe-picker".equals(type) ? switch (adobePicker) {
            case "low" -> 0;
            case "high" -> 2;
            default -> 1;
        } : "select".equals(type) ? switch (selectValue) {
            case "light" -> 1;
            case "dark" -> 2;
            case "dynamic" -> 3;
            default -> 0;
        } : "menu".equals(type) ? nextMenuIndex(MENU_ROOT, -1, 1) : 0;
        submenu = "";
        submenuIndex = -1;
        menuTypeahead = "";
    }

    private String[] adobeDialogActionIds() {
        return switch (adobeDialogVariant) {
            case "confirmation" -> new String[]{"cancel", "secondary", "confirm"};
            case "error" -> new String[]{"confirm"};
            default -> new String[]{"cancel", "confirm"};
        };
    }

    private void closeOverlay() {
        if ("adobe-dialog".equals(overlay)) {
            adobeDialogExiting = true;
            return;
        }
        finishOverlayClose();
    }

    private void finishOverlayClose() {
        if (overlay.isEmpty()) return;
        if ("adobe-date-range".equals(overlay)) adobeDateRangeDraftStart = null;
        overlay = "";
        adobeDialogExiting = false;
        adobeModalProgress = 0f;
        submenu = "";
        submenuIndex = -1;
        menuTypeahead = "";
        if (runtime != null) {
            UiNode trigger = find(runtime.root(), overlayAnchor);
            runtime.input().focus(trigger);
        }
        overlayAnchor = "";
    }

    /** Same compact-hierarchy policy as SpectrumBreadcrumbs.layout(), using the
     * actual retained component width rather than a hard-coded viewport size. */
    private List<Integer> adobeBreadcrumbHiddenIndices() {
        String[] labels = {"Creative Cloud", "Projects", "Brand assets", "Campaign", "Hero images"};
        if (adobeBreadcrumbCurrent <= 2) return List.of();
        UiNode breadcrumbs = runtime != null ? find(runtime.root(), "s2:breadcrumbs") : null;
        float available = breadcrumbs != null ? breadcrumbs.bounds().width() : 570f;
        int keep = adobeBreadcrumbCurrent;
        float budget = crumbWidth(labels[0]) + crumbWidth(labels[keep]) + 68f;
        while (keep > 2 && budget + crumbWidth(labels[keep - 1]) + 20f < available) {
            keep--;
            budget += crumbWidth(labels[keep]) + 20f;
        }
        List<Integer> indices = new ArrayList<>();
        for (int i = 1; i < keep; i++) indices.add(i);
        return indices;
    }

    private static int crumbWidth(String label) {
        return Math.max(42, (int) Math.ceil(label.length() * 16f * .54f) + 22);
    }

    private void addVisibleAdobeTreeRows(String id, List<String> rows) {
        if (!"archive".equals(id)) rows.add(id); // disabled nodes are skipped by keyboard navigation
        if (adobeTreeExpanded.contains(id)) {
            for (String child : ADOBE_TREE_CHILDREN.getOrDefault(id, List.of()))
                addVisibleAdobeTreeRows(child, rows);
        }
    }

    private List<String> visibleAdobeTreeRows() {
        List<String> rows = new ArrayList<>();
        addVisibleAdobeTreeRows("home", rows);
        return rows;
    }

    /** Move actual retained keyboard focus; do not change the selection until Enter/Space. */
    private void focusAdobeTreeRow(String id) {
        if (runtime == null) return;
        UiNode node = find(runtime.root(), "s2:tree:item:" + id);
        if (node != null && !node.state().disabled()) runtime.input().focus(node);
    }

    private void chooseOverlayOption() {
        if ("adobe-breadcrumbs".equals(overlay)) {
            List<Integer> indices = adobeBreadcrumbHiddenIndices();
            if (!indices.isEmpty()) {
                adobeBreadcrumbCurrent = indices.get(
                        Math.max(0, Math.min(indices.size() - 1, overlayIndex)));
                adobeTreeSelected = ADOBE_BREADCRUMB_IDS[adobeBreadcrumbCurrent];
            }
            finishOverlayClose();
            return;
        }
        if ("adobe-menu".equals(overlay)) {
            switch (overlayIndex) {
                case 1 -> adobeLastAction = "rename";
                case 2 -> adobeLastAction = "duplicate";
                case 4 -> adobeLastAction = "share";
                default -> { return; }
            }
            closeOverlay();
            return;
        }
        if ("adobe-dialog".equals(overlay)) {
            String[] ids = adobeDialogActionIds();
            String id = ids[Math.floorMod(overlayIndex, ids.length)];
            adobeLastAction = switch (id) {
                case "cancel" -> "Cancelled";
                case "secondary" -> "Remind me later";
                default -> switch (adobeDialogVariant) {
                    case "destructive" -> "Deleted";
                    case "warning" -> "Continued despite warning";
                    case "information" -> "Continued";
                    case "error" -> "Acknowledged";
                    default -> "Enabled";
                };
            };
            closeOverlay();
            return;
        }
        if ("adobe-picker".equals(overlay)) {
            String[] values = {"low", "medium", "high"};
            adobePicker = values[Math.max(0, Math.min(values.length - 1, overlayIndex))];
            closeOverlay();
            return;
        }
        if ("select".equals(overlay)) {
            String[] values = {"system", "light", "dark", "dynamic"};
            selectValue = values[Math.max(0, Math.min(values.length - 1, overlayIndex))];
            closeOverlay();
            return;
        }
        if ("menu".equals(overlay)) {
            List<MenuEntry> items = activeMenuEntries();
            int index = submenu.isEmpty() ? overlayIndex : submenuIndex;
            if (index < 0 || index >= items.size()) return;
            MenuEntry entry = items.get(index);
            if (!entry.navigable()) return;
            if (submenu.isEmpty()) activateRootItem(entry.id());
            else activateSubmenuItem(entry.id());
        }
    }

    private UiNode focusedTextField() {
        if (runtime == null || runtime.root() == null || focus.isEmpty()) return null;
        if ("catalog-search".equals(focus)) return find(runtime.root(), "catalog:search");
        if ("adobe-field".equals(focus) && "adobe".equals(kit) && "text-field".equals(page))
            return find(runtime.root(), "s2:field");
        if ("adobe-search".equals(focus) && "adobe".equals(kit) && "search-field".equals(page))
            return find(runtime.root(), "s2:search");
        if ("adobe-number".equals(focus) && "adobe".equals(kit) && "number-field".equals(page))
            return find(runtime.root(), "s2:number:quantity");
        if ("adobe-color".equals(focus) && "adobe".equals(kit) && "color-field".equals(page))
            return find(runtime.root(), "s2:color:field");
        if ("adobe-combo".equals(focus) && "adobe".equals(kit) && "combo-box".equals(page))
            return find(runtime.root(), "s2:combo:input");
        if ("search".equals(focus) && "overlays".equals(page))
            return find(runtime.root(), "lab:search:field");
        if (!"inputs".equals(page)) return null;
        return find(runtime.root(), "lab:field:" + focus);
    }

    private void clearTextFocus() {
        if ("adobe-color".equals(focus)) commitAdobeColorField();
        focus = "";
        textInput.unfocus();
        searchInput.unfocus();
        adobeSearchInput.unfocus();
        adobeNumberInput.unfocus();
        adobeColorInput.unfocus();
        adobeComboInput.unfocus();
        googleCatalogInput.unfocus();
        adobeCatalogInput.unfocus();
    }

    /** Keep platform text focus and IME area in sync with the actual UiNode layout. */
    private void updateTextInputArea() {
        UiNode field = focusedTextField();
        if (field == null) {
            if (!focus.isEmpty()) clearTextFocus();
            return;
        }
        UiBounds bounds = field.bounds();
        TextRenderer font = "catalog-search".equals(focus)
                ? Fonts.renderer("Onest", FontInfo.Type.Regular, ClickGuiRenderer.getOnestMedium())
                : Fonts.renderer("adobe".equals(kit) ? "SourceSans3" : "Roboto",
                    FontInfo.Type.Regular, ClickGuiRenderer.getOnestMedium());
        SingleLineTextInput editor = focusedEditor();
        float left = "catalog-search".equals(focus) ? 32f :
                "search".equals(focus) ? 56f : "adobe-search".equals(focus) ? 43f :
                "adobe-field".equals(focus) ? 13f : "adobe-number".equals(focus)
                || "adobe-combo".equals(focus) ? 14f : "adobe-color".equals(focus) ? 42f : 16f;
        float size = "catalog-search".equals(focus) ? 15f :
                "search".equals(focus) ? 16f : "adobe-field".equals(focus)
                || "adobe-search".equals(focus) || "adobe-number".equals(focus)
                || "adobe-combo".equals(focus) || "adobe-color".equals(focus) ? 17f : 18f;
        editor.layout(bounds.x() + left, Math.max(1f, bounds.width() - left - 16f), font, size);
        CombatantTextInputBridge.focus(editor,
                Math.round(bounds.x()), Math.round(bounds.y()),
                Math.round(bounds.x() + bounds.width()), Math.round(bounds.y() + bounds.height()));
    }

    private SingleLineTextInput focusedEditor() {
        return "catalog-search".equals(focus) ? catalogEditor() :
                "search".equals(focus) ? searchInput :
                "adobe-search".equals(focus) ? adobeSearchInput :
                "adobe-number".equals(focus) ? adobeNumberInput :
                "adobe-combo".equals(focus) ? adobeComboInput :
                "adobe-color".equals(focus) ? adobeColorInput : textInput;
    }

    private void focusTextField(String field, boolean positionCaret, float mouseX) {
        if (!"filled".equals(field) && !"outlined".equals(field)
                && !"search".equals(field) && !"adobe-field".equals(field)
                && !"adobe-search".equals(field)
                && !"catalog-search".equals(field) && !"adobe-number".equals(field)
                && !"adobe-combo".equals(field) && !"adobe-color".equals(field)) return;
        focus = field;
        updateTextInputArea();
        if (positionCaret) {
            boolean shift = CombatantInput.state().isPressed(InputDevice.KEYBOARD, InputKeys.LEFT_SHIFT)
                    || CombatantInput.state().isPressed(InputDevice.KEYBOARD, InputKeys.RIGHT_SHIFT);
            focusedEditor().beginDrag(mouseX, shift);
        }
    }

    private static int runtimePointerButton(int button) {
        return switch (button) {
            case InputKeys.MOUSE_LEFT -> 0;
            case InputKeys.MOUSE_RIGHT -> 1;
            case InputKeys.MOUSE_MIDDLE -> 2;
            default -> button;
        };
    }

    private void adjustSlider(float pointerX) {
        UiNode node = runtime == null ? null : find(runtime.root(), "lab:slider");
        if (node == null) return;
        UiBounds b = node.bounds();
        float fraction = Math.max(0f, Math.min(1f, (pointerX - b.x()) / Math.max(1f, b.width())));
        slider = Math.round(fraction * 100f);
    }

    private void adjustAdobeSlider(float pointerX) {
        UiNode node = runtime == null ? null : find(runtime.root(), "s2:slider");
        if (node == null) return;
        UiBounds b = node.bounds();
        float fraction = Math.max(0f, Math.min(1f, (pointerX - b.x() - 12f)
                / Math.max(1f, b.width() - 24f)));
        adobeSlider = Math.round(fraction * 100f);
    }

    private void adjustAdobeRange(float pointerX) {
        UiNode node = runtime == null ? null : find(runtime.root(), "s2:range-slider");
        if (node == null || adobeRangeDragging < 0) return;
        UiBounds b = node.bounds();
        float fraction = Math.max(0f, Math.min(1f,
                (pointerX - b.x() - 12f) / Math.max(1f, b.width() - 24f)));
        int value = Math.round(fraction * 100f);
        if (adobeRangeDragging == 0) adobeRangeStart = Math.min(value, adobeRangeEnd);
        else adobeRangeEnd = Math.max(adobeRangeStart, value);
    }

    @Override
    public boolean mousePressed(float mouseX, float mouseY, int button) {
        if (runtime == null) return false;
        boolean handled = runtime.input().pointerDown(runtime.root(),
                new UiPointerEvent(mouseX, mouseY, runtimePointerButton(button)));
        UiNode pressed = runtime.input().state().pressedNode();
        if (button == InputKeys.MOUSE_LEFT) {
            String pressedKey = pressed == null ? "" : pressed.key();
            if ("catalog:search".equals(pressedKey)) focusTextField("catalog-search", true, mouseX);
            else if ("lab:field:filled".equals(pressedKey)) focusTextField("filled", true, mouseX);
            else if ("lab:field:outlined".equals(pressedKey)) focusTextField("outlined", true, mouseX);
            else if ("lab:search:field".equals(pressedKey)) focusTextField("search", true, mouseX);
            else if ("s2:field".equals(pressedKey)) focusTextField("adobe-field", true, mouseX);
            else if ("s2:search".equals(pressedKey)) focusTextField("adobe-search", true, mouseX);
            else if ("s2:number:quantity".equals(pressedKey))
                focusTextField("adobe-number", true, mouseX);
            else if ("s2:combo:input".equals(pressedKey))
                focusTextField("adobe-combo", true, mouseX);
            else if ("s2:color:field".equals(pressedKey))
                focusTextField("adobe-color", true, mouseX);
            else if (!"s2:search:clear".equals(pressedKey)
                    && !"catalog:search:clear".equals(pressedKey)
                    && !focus.isEmpty()) clearTextFocus();
        }
        sliderDragging = button == InputKeys.MOUSE_LEFT && pressed != null && "lab:slider".equals(pressed.key());
        if (sliderDragging) adjustSlider(mouseX);
        adobeSliderDragging = button == InputKeys.MOUSE_LEFT && pressed != null
                && "s2:slider".equals(pressed.key());
        if (adobeSliderDragging) adjustAdobeSlider(mouseX);
        adobeColorDragMode = "";
        adobeColorDragNode = null;
        if (button == InputKeys.MOUSE_LEFT && pressed != null && "adobe".equals(kit)) {
            String id = pressed.key();
            if (Set.of("s2:color:area", "s2:color:wheel-area", "s2:color:hue",
                    "s2:color:alpha", "s2:color:vertical-hue", "s2:color:wheel").contains(id)) {
                adobeColorDragMode = id;
                adobeColorDragNode = pressed;
                adjustAdobeColor(mouseX, mouseY);
            }
        }
        adobeRangeDragging = -1;
        if (button == InputKeys.MOUSE_LEFT && pressed != null
                && "adobe".equals(kit) && "range-slider".equals(page)
                && "s2:range-slider".equals(pressed.key())) {
            UiBounds b = pressed.bounds();
            float value = Math.max(0f, Math.min(100f,
                    (mouseX - b.x() - 12f) * 100f / Math.max(1f, b.width() - 24f)));
            adobeRangeDragging = Math.abs(value - adobeRangeStart)
                    <= Math.abs(value - adobeRangeEnd) ? 0 : 1;
            adobeRangeActiveThumb = adobeRangeDragging;
            adjustAdobeRange(mouseX);
        }
        return handled;
    }

    @Override
    public void mouseReleased(float mouseX, float mouseY, int button) {
        if (sliderDragging) {
            adjustSlider(mouseX);
            event("slider");
            sliderDragging = false;
        }
        if (adobeSliderDragging) {
            adjustAdobeSlider(mouseX);
            adobeSliderDragging = false;
        }
        if (adobeRangeDragging >= 0) {
            adjustAdobeRange(mouseX);
            adobeRangeDragging = -1;
        }
        if (adobeColorDragNode != null) {
            adjustAdobeColor(mouseX, mouseY);
            adobeColorDragNode = null;
            adobeColorDragMode = "";
        }
        if (runtime != null) runtime.input().pointerUp(runtime.root(),
                new UiPointerEvent(mouseX, mouseY, runtimePointerButton(button)));
    }

    @Override
    public boolean mouseScrolled(float mouseX, float mouseY, double amount) {
        tooltip = "";
        return runtime != null && runtime.input().scroll(runtime.root(),
                new UiScrollEvent(mouseX, mouseY, 0, -amount));
    }

    private static int matchMenuPrefix(List<MenuEntry> items, int index, String prefix) {
        if (items.isEmpty() || prefix.isEmpty()) return -1;
        for (int i = 1; i <= items.size(); i++) {
            int cursor = Math.floorMod(index + i, items.size());
            MenuEntry entry = items.get(cursor);
            if (entry.navigable() && entry.label().toLowerCase(java.util.Locale.ROOT).startsWith(prefix))
                return cursor;
        }
        return -1;
    }

    /** Pointer hover drives submenu disclosure without a click, using the same
     * computed UiNode hit geometry as the rest of Combatant's input pipeline. */
    @Override
    public void mouseMoved(float mouseX, float mouseY) {
        if (runtime == null) return;
        if (sliderDragging) adjustSlider(mouseX);
        if (adobeSliderDragging) adjustAdobeSlider(mouseX);
        if (adobeRangeDragging >= 0) adjustAdobeRange(mouseX);
        if (adobeColorDragNode != null) adjustAdobeColor(mouseX, mouseY);
        runtime.input().updateHover(runtime.root(), mouseX, mouseY);
        UiNode hovered = runtime.input().state().hoveredNode();
        updateAdobeTooltipHover(hovered);
        if (overlay.isEmpty()) {
            boolean showing = hovered != null && "lab:tooltip:trigger".equals(hovered.key());
            tooltip = showing ? "More information" : "";
            if (showing) {
                UiBounds b = hovered.bounds();
                UiBounds rootBounds = runtime.root().bounds();
                tooltipX = b.x() - rootBounds.x();
                tooltipY = b.y() - rootBounds.y();
            }
        }
        if (!"menu".equals(overlay) || hovered == null) return;
        String key = hovered.key();
        String prefix = "m3:popup:";
        String subPrefix = "m3:submenu:";
        if (key.startsWith(prefix) && !key.equals("m3:popup:hit")) {
            String id = key.substring(prefix.length());
            for (int i = 0; i < MENU_ROOT.size(); i++) {
                MenuEntry entry = MENU_ROOT.get(i);
                if (!entry.id().equals(id) || !entry.navigable()) continue;
                overlayIndex = i;
                if (!entry.child().isEmpty()) openSubmenu(entry.child());
                else { submenu = ""; submenuIndex = -1; }
                return;
            }
        } else if (key.startsWith(subPrefix) && !key.equals("m3:submenu:hit")) {
            String id = key.substring(subPrefix.length());
            List<MenuEntry> items = activeMenuEntries();
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i).id().equals(id) && items.get(i).navigable()) {
                    submenuIndex = i;
                    return;
                }
            }
        }
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (!overlay.isEmpty()) {
            if ("adobe-combo".equals(overlay)) {
                if (!Character.isISOControl(chr) && !CombatantTextInputBridge.owns(adobeComboInput))
                    adobeComboInput.type(chr);
                overlayIndex = 0;
                return true;
            }
            if ("dialog".equals(overlay) || "adobe-dialog".equals(overlay)
                    || "adobe-standard-dialog".equals(overlay)) return true;
            if ("adobe-breadcrumbs".equals(overlay)) {
                String[] titles = {"", "projects", "brand assets", "campaign", "hero images"};
                List<Integer> indices = adobeBreadcrumbHiddenIndices();
                for (int i = 0; i < indices.size(); i++) {
                    if (Character.toLowerCase(chr) == titles[indices.get(i)].charAt(0)) {
                        overlayIndex = i;
                        break;
                    }
                }
                return true;
            }
            if ("adobe-menu".equals(overlay)) {
                char letter = Character.toLowerCase(chr);
                if (letter == 'r') overlayIndex = 1;
                if (letter == 'd') overlayIndex = 2;
                if (letter == 's') overlayIndex = 4;
                return true;
            }
            if (Character.isISOControl(chr)) return true;
            if ("adobe-picker".equals(overlay)) {
                String[] labels = {"low quality", "balanced", "high quality"};
                char needle = Character.toLowerCase(chr);
                for (int offset = 1; offset <= labels.length; offset++) {
                    int index = (overlayIndex + offset) % labels.length;
                    if (labels[index].charAt(0) == needle) {
                        overlayIndex = index;
                        break;
                    }
                }
                return true;
            }
            if ("menu".equals(overlay)) {
                List<MenuEntry> items = activeMenuEntries();
                long now = System.nanoTime();
                menuTypeahead = now - menuTypeaheadAt > 250_000_000L
                        ? Character.toString(chr).toLowerCase(java.util.Locale.ROOT)
                        : (menuTypeahead + chr).toLowerCase(java.util.Locale.ROOT);
                menuTypeaheadAt = now;
                int start = submenu.isEmpty() ? overlayIndex : submenuIndex;
                int target = matchMenuPrefix(items, start, menuTypeahead);
                if (target < 0 && menuTypeahead.length() > 1) {
                    menuTypeahead = Character.toString(chr).toLowerCase(java.util.Locale.ROOT);
                    target = matchMenuPrefix(items, start, menuTypeahead);
                }
                if (target >= 0) {
                    if (submenu.isEmpty()) overlayIndex = target;
                    else submenuIndex = target;
                }
                return true;
            }
            String[] labels = {"System default", "Light", "Dark", "Dynamic colors"};
            char needle = Character.toLowerCase(chr);
            for (int offset = 1; offset <= labels.length; offset++) {
                int index = (overlayIndex + offset) % labels.length;
                if (Character.toLowerCase(labels[index].charAt(0)) == needle) {
                    overlayIndex = index;
                    break;
                }
            }
            return true;
        }
        if (focus.isEmpty()) return false;
        // Committed Unicode normally arrives via MinecraftInputAdapter and
        // CombatantTextInputBridge. Only use this for legacy char callbacks.
        SingleLineTextInput editor = focusedEditor();
        if (!CombatantTextInputBridge.owns(editor)) editor.type(chr);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int shortcutKey, int modifiers) {
        if (InputModifierMask.has(modifiers, InputModifierMask.SHORTCUT) && shortcutKey == 'f'
                && !"adobe-dialog".equals(overlay) && !"adobe-standard-dialog".equals(overlay)) {
            if (!overlay.isEmpty()) finishOverlayClose();
            focusTextField("catalog-search", false, 0f);
            catalogEditor().selectAll();
            updateTextInputArea();
            return true;
        }
        if (overlay.isEmpty() && keyCode == InputKeys.ESCAPE && dismissNewestAdobeToast())
            return true;
        if (!overlay.isEmpty()) {
            if ("adobe-combo".equals(overlay)) {
                if (keyCode == InputKeys.ESCAPE || keyCode == InputKeys.TAB) {
                    finishOverlayClose(); clearTextFocus(); return true;
                }
                List<String> matches = adobeComboMatches();
                if (keyCode == InputKeys.DOWN || keyCode == InputKeys.UP) {
                    overlayIndex = matches.isEmpty() ? 0 : Math.floorMod(overlayIndex
                            + (keyCode == InputKeys.DOWN ? 1 : -1), matches.size());
                    return true;
                }
                if (keyCode == InputKeys.ENTER || keyCode == InputKeys.KP_ENTER) {
                    if (!matches.isEmpty()) chooseCombo(matches.get(Math.min(overlayIndex,matches.size()-1)));
                    return true;
                }
                if ("adobe-combo".equals(focus)) {
                    adobeComboInput.keyPressed(keyCode, shortcutKey, modifiers);
                    overlayIndex = 0;
                }
                return true;
            }
            if ("adobe-date-single".equals(overlay) || "adobe-date-range".equals(overlay)) {
                if (keyCode == InputKeys.ESCAPE || keyCode == InputKeys.TAB) {
                    adobeDateRangeDraftStart = null;
                    finishOverlayClose(); return true;
                }
                if (keyCode == InputKeys.PAGE_DOWN) { calendarMove(1); return true; }
                if (keyCode == InputKeys.PAGE_UP) { calendarMove(-1); return true; }
                if (keyCode == InputKeys.HOME) { adobeCalendarFocusDay = 1; return true; }
                if (keyCode == InputKeys.END) {
                    adobeCalendarFocusDay = adobeCalendarMonth.lengthOfMonth(); return true;
                }
                if (keyCode == InputKeys.ENTER || keyCode == InputKeys.KP_ENTER
                        || keyCode == InputKeys.SPACE) {
                    calendarChoose(adobeCalendarFocusDay); return true;
                }
                int delta = keyCode == InputKeys.LEFT ? -1 : keyCode == InputKeys.RIGHT ? 1 :
                        keyCode == InputKeys.UP ? -7 : keyCode == InputKeys.DOWN ? 7 : 0;
                if (delta != 0) {
                    LocalDate target = adobeCalendarMonth.atDay(adobeCalendarFocusDay).plusDays(delta);
                    if (!target.isBefore(LocalDate.of(2026, 1, 1))
                            && !target.isAfter(LocalDate.of(2027, 12, 31))) {
                        adobeCalendarMonth = YearMonth.from(target);
                        adobeCalendarFocusDay = target.getDayOfMonth();
                    }
                }
                return true;
            }
            if (keyCode == InputKeys.TAB) {
                if ("adobe-standard-dialog".equals(overlay)) {
                    int shift = InputModifierMask.has(modifiers, InputModifierMask.SHIFT) ? -1 : 1;
                    overlayIndex = Math.floorMod(overlayIndex + shift, 2);
                } else if ("adobe-dialog".equals(overlay)) {
                    int shift = InputModifierMask.has(modifiers, InputModifierMask.SHIFT) ? -1 : 1;
                    overlayIndex = Math.floorMod(overlayIndex + shift, adobeDialogActionIds().length);
                } else closeOverlay();
                return true;
            }
            if (keyCode == InputKeys.ESCAPE) {
                if ("menu".equals(overlay) && !submenu.isEmpty()) {
                    submenu = "";
                    submenuIndex = -1;
                } else {
                    if ("adobe-dialog".equals(overlay))
                        adobeLastAction = "error".equals(adobeDialogVariant) ? "Acknowledged" : "Cancelled";
                    closeOverlay();
                }
                return true;
            }
            if ("adobe-standard-dialog".equals(overlay)) {
                if (keyCode == InputKeys.LEFT || keyCode == InputKeys.RIGHT) {
                    overlayIndex = 1 - overlayIndex; return true;
                }
                if (keyCode == InputKeys.ENTER || keyCode == InputKeys.KP_ENTER
                        || keyCode == InputKeys.SPACE) {
                    if (overlayIndex == 0) adobeLastAction = "Cancelled";
                    else adobeLastAction = "Saved project settings";
                    finishOverlayClose(); return true;
                }
                return true;
            }
            if ("adobe-breadcrumbs".equals(overlay)) {
                int count = Math.max(1, adobeBreadcrumbHiddenIndices().size());
                if (keyCode == InputKeys.DOWN) { overlayIndex = (overlayIndex + 1) % count; return true; }
                if (keyCode == InputKeys.UP) { overlayIndex = (overlayIndex + count - 1) % count; return true; }
                if (keyCode == InputKeys.HOME) { overlayIndex = 0; return true; }
                if (keyCode == InputKeys.END) { overlayIndex = count - 1; return true; }
                if (keyCode == InputKeys.ENTER || keyCode == InputKeys.KP_ENTER || keyCode == InputKeys.SPACE) {
                    chooseOverlayOption(); return true;
                }
                return true;
            }
            if ("adobe-menu".equals(overlay)) {
                int[] indices = {1, 2, 4};
                int current = 0;
                for (int i = 0; i < indices.length; i++)
                    if (indices[i] == overlayIndex) current = i;
                if (keyCode == InputKeys.DOWN) {
                    overlayIndex = indices[(current + 1) % indices.length]; return true;
                }
                if (keyCode == InputKeys.UP) {
                    overlayIndex = indices[(current + indices.length - 1) % indices.length]; return true;
                }
                if (keyCode == InputKeys.HOME) { overlayIndex = indices[0]; return true; }
                if (keyCode == InputKeys.END) { overlayIndex = indices[indices.length - 1]; return true; }
                if (keyCode == InputKeys.ENTER || keyCode == InputKeys.KP_ENTER || keyCode == InputKeys.SPACE) {
                    chooseOverlayOption(); return true;
                }
                return true;
            }
            if ("adobe-dialog".equals(overlay)) {
                if (adobeDialogExiting) return true;
                if (keyCode == InputKeys.LEFT || keyCode == InputKeys.RIGHT) {
                    int delta = keyCode == InputKeys.RIGHT ? 1 : -1;
                    overlayIndex = Math.floorMod(overlayIndex + delta, adobeDialogActionIds().length);
                    return true;
                }
                if (keyCode == InputKeys.ENTER || keyCode == InputKeys.KP_ENTER || keyCode == InputKeys.SPACE) {
                    chooseOverlayOption(); return true;
                }
                return true;
            }
            if ("adobe-picker".equals(overlay)) {
                if (keyCode == InputKeys.DOWN) { overlayIndex = (overlayIndex + 1) % 3; return true; }
                if (keyCode == InputKeys.UP) { overlayIndex = (overlayIndex + 2) % 3; return true; }
                if (keyCode == InputKeys.HOME) { overlayIndex = 0; return true; }
                if (keyCode == InputKeys.END) { overlayIndex = 2; return true; }
                if (keyCode == InputKeys.ENTER || keyCode == InputKeys.KP_ENTER || keyCode == InputKeys.SPACE) {
                    chooseOverlayOption(); return true;
                }
                return true;
            }
            if ("dialog".equals(overlay)) {
                if (keyCode == InputKeys.LEFT || keyCode == InputKeys.RIGHT) {
                    overlayIndex = 1 - overlayIndex;
                    return true;
                }
                if (keyCode == InputKeys.ENTER || keyCode == InputKeys.KP_ENTER || keyCode == InputKeys.SPACE) {
                    if (overlayIndex == 0) closeOverlay();
                    else { closeOverlay(); event("delete"); }
                    return true;
                }
                return true;
            }
            if ("menu".equals(overlay)) {
                List<MenuEntry> items = activeMenuEntries();
                int current = submenu.isEmpty() ? overlayIndex : submenuIndex;
                int next = current;
                if (keyCode == InputKeys.DOWN) next = nextMenuIndex(items, current, 1);
                else if (keyCode == InputKeys.UP) next = nextMenuIndex(items, current, -1);
                else if (keyCode == InputKeys.HOME) next = nextMenuIndex(items, -1, 1);
                else if (keyCode == InputKeys.END) next = nextMenuIndex(items, 0, -1);
                else if (keyCode == InputKeys.RIGHT && submenu.isEmpty() && overlayIndex >= 0) {
                    MenuEntry entry = MENU_ROOT.get(overlayIndex);
                    if (!entry.child().isEmpty()) openSubmenu(entry.child());
                    return true;
                } else if (keyCode == InputKeys.LEFT && !submenu.isEmpty()) {
                    submenu = "";
                    submenuIndex = -1;
                    return true;
                } else if (keyCode == InputKeys.ENTER || keyCode == InputKeys.KP_ENTER || keyCode == InputKeys.SPACE) {
                    chooseOverlayOption(); return true;
                }
                if (next >= 0) {
                    if (submenu.isEmpty()) overlayIndex = next;
                    else submenuIndex = next;
                }
                return true;
            }
            if (keyCode == InputKeys.DOWN) { overlayIndex = (overlayIndex + 1) % 4; return true; }
            if (keyCode == InputKeys.UP) { overlayIndex = (overlayIndex + 3) % 4; return true; }
            if (keyCode == InputKeys.HOME) { overlayIndex = 0; return true; }
            if (keyCode == InputKeys.END) { overlayIndex = 3; return true; }
            if (keyCode == InputKeys.ENTER || keyCode == InputKeys.KP_ENTER || keyCode == InputKeys.SPACE) {
                chooseOverlayOption(); return true;
            }
            return true;
        }
        if (!focus.isEmpty()) {
            if ("adobe-color".equals(focus)) {
                if (keyCode == InputKeys.ENTER || keyCode == InputKeys.KP_ENTER
                        || keyCode == InputKeys.ESCAPE) {
                    if (keyCode != InputKeys.ESCAPE) commitAdobeColorField();
                    else adobeColorInput.setText(adobeColorHex());
                    clearTextFocus();
                    return true;
                }
            }
            if ("adobe-number".equals(focus)) {
                if (keyCode == InputKeys.UP || keyCode == InputKeys.DOWN) {
                    stepAdobeNumber(keyCode == InputKeys.UP ? 1 : -1); return true;
                }
                if (keyCode == InputKeys.ENTER || keyCode == InputKeys.KP_ENTER) {
                    commitAdobeNumber(); clearTextFocus(); return true;
                }
            }
            if (keyCode == InputKeys.ESCAPE || keyCode == InputKeys.ENTER || keyCode == InputKeys.KP_ENTER) {
                if ("adobe-search".equals(focus) && keyCode != InputKeys.ESCAPE)
                    event("adobe:search:submit");
                clearTextFocus();
                return true;
            }
            // Both physical key codes and shortcut keys are already normalized
            // by ClickGuiRenderer/CombatantInput; do not reinterpret them as platform key codes.
            focusedEditor().keyPressed(keyCode, shortcutKey, modifiers);
            return true;
        }
        UiNode focused = runtime != null ? runtime.input().state().focusedNode() : null;
        // Native collection keyboard handling. Arrow keys move the retained focus;
        // Space/Enter activate the selected item. Disabled elements are never targets.
        if (focused != null && focused.key() != null && "adobe".equals(kit)
                && !focused.state().disabled()) {
            String id = focused.key();
            boolean activate = keyCode == InputKeys.ENTER || keyCode == InputKeys.KP_ENTER
                    || keyCode == InputKeys.SPACE;
            int move = keyCode == InputKeys.RIGHT || keyCode == InputKeys.DOWN ? 1
                    : keyCode == InputKeys.LEFT || keyCode == InputKeys.UP ? -1 : 0;
            if ("list-view".equals(page) && id.startsWith("s2:lv:")) {
                String[] names = id.startsWith("s2:lv:files:item:")
                        ? new String[]{"ps", "ai", "pdf"}
                        : id.startsWith("s2:lv:single:item:")
                        ? new String[]{"grid", "list", "detail"} : new String[0];
                String prefix = id.startsWith("s2:lv:files:item:")
                        ? "s2:lv:files:item:" : "s2:lv:single:item:";
                String option = id.substring(id.lastIndexOf(':') + 1);
                int current = Arrays.asList(names).indexOf(option);
                if (current >= 0) {
                    if (move != 0) {
                        UiNode next = find(runtime.root(), prefix
                                + names[Math.floorMod(current + move, names.length)]);
                        if (next != null) runtime.input().focus(next);
                        return true;
                    }
                    if (activate) {
                        if (prefix.contains(":files:")) {
                            if (!adobeListViewSelected.add(option)) adobeListViewSelected.remove(option);
                        } else adobeListViewSingle = option;
                        return true;
                    }
                }
            }
            if ("card-view".equals(page) && id.startsWith("s2:cv:")) {
                boolean compact = id.startsWith("s2:cv:compact:card:");
                String prefix = compact ? "s2:cv:compact:card:" : "s2:cv:assets:card:";
                String[] names = compact ? new String[]{"recent", "shared"}
                        : new String[]{"brand", "campaign", "logo"};
                String option = id.substring(id.lastIndexOf(':') + 1);
                int current = Arrays.asList(names).indexOf(option);
                if (current >= 0) {
                    if (move != 0) {
                        UiNode next = find(runtime.root(), prefix
                                + names[Math.floorMod(current + move, names.length)]);
                        if (next != null) runtime.input().focus(next);
                        return true;
                    }
                    if (activate) {
                        if (compact) adobeCardViewSingle = option;
                        else if (!adobeCardViewSelected.add(option)) adobeCardViewSelected.remove(option);
                        return true;
                    }
                }
            }
            if ("radio-group".equals(page) && id.startsWith("s2:rg:")) {
                boolean horizontal = id.startsWith("s2:rg:orientation:item:");
                String prefix = horizontal ? "s2:rg:orientation:item:" : "s2:rg:quality:item:";
                String[] names = horizontal ? new String[]{"portrait", "landscape"}
                        : new String[]{"fast", "balanced", "high"};
                String option = id.substring(id.lastIndexOf(':') + 1);
                int current = Arrays.asList(names).indexOf(option);
                if (current >= 0 && (move != 0 || activate)) {
                    int nextIndex = move == 0 ? current : Math.floorMod(current + move, names.length);
                    UiNode next = find(runtime.root(), prefix + names[nextIndex]);
                    if (next != null && !next.state().disabled()) {
                        runtime.input().focus(next);
                        if (horizontal) adobeRadioGroupOrientation = names[nextIndex];
                        else adobeRadioGroupSelected = names[nextIndex];
                    }
                    return true;
                }
            }
            if ("contextual-help".equals(page) && activate) {
                if ("s2:help:help".equals(id) || "s2:help:info".equals(id)) {
                    openOverlay("adobe-context-help-" + id.substring("s2:help:".length()), id);
                    return true;
                }
            }
            if ("link-button".equals(page) && activate && id.startsWith("s2:lb:")) {
                String actionId = id.substring("s2:lb:".length());
                if (Set.of("primary", "secondary", "quiet").contains(actionId)) {
                    adobeLastAction = "Link button: " + switch (actionId) {
                        case "primary" -> "docs";
                        case "secondary" -> "details";
                        default -> "learn";
                    };
                    return true;
                }
            }
        }
        if (focused != null && "adobe".equals(kit) && focused.key().startsWith("s2:color:")
                && !focused.state().disabled()) {
            String key = focused.key();
            int delta = keyCode == InputKeys.UP || keyCode == InputKeys.RIGHT ? 1 :
                    keyCode == InputKeys.DOWN || keyCode == InputKeys.LEFT ? -1 : 0;
            if ("s2:color:field".equals(key)
                    && (keyCode == InputKeys.ENTER || keyCode == InputKeys.KP_ENTER)) {
                focusTextField("adobe-color", false, 0f); return true;
            }
            if (delta != 0 || keyCode == InputKeys.HOME || keyCode == InputKeys.END) {
                int jump = InputModifierMask.has(modifiers, InputModifierMask.SHIFT) ? 10 : 1;
                if ("s2:color:hue".equals(key) || "s2:color:vertical-hue".equals(key)
                        || "s2:color:wheel".equals(key)) {
                    changeAdobeColor(keyCode == InputKeys.HOME ? 0 :
                            keyCode == InputKeys.END ? 359 : adobeColorHue + delta * jump,
                            adobeColorSaturation, adobeColorBrightness, adobeColorAlpha);
                    return true;
                }
                if ("s2:color:alpha".equals(key)) {
                    changeAdobeColor(adobeColorHue, adobeColorSaturation, adobeColorBrightness,
                            keyCode == InputKeys.HOME ? 0 : keyCode == InputKeys.END ? 100
                                    : adobeColorAlpha + delta * jump);
                    return true;
                }
                if ("s2:color:area".equals(key) || "s2:color:wheel-area".equals(key)) {
                    int saturation = adobeColorSaturation, brightness = adobeColorBrightness;
                    if (keyCode == InputKeys.LEFT || keyCode == InputKeys.RIGHT)
                        saturation += delta * jump;
                    if (keyCode == InputKeys.UP || keyCode == InputKeys.DOWN)
                        brightness += delta * jump;
                    if (keyCode == InputKeys.HOME) { saturation = 0; brightness = 100; }
                    if (keyCode == InputKeys.END) { saturation = 100; brightness = 0; }
                    changeAdobeColor(adobeColorHue, saturation, brightness, adobeColorAlpha);
                    return true;
                }
            }
        }

        if (focused != null && "adobe".equals(kit) && !focused.state().disabled()) {
            String id = focused.key();
            boolean activate = keyCode == InputKeys.ENTER || keyCode == InputKeys.KP_ENTER
                    || keyCode == InputKeys.SPACE;
            if ("number-field".equals(page) && activate) {
                if ("s2:number:quantity:inc".equals(id)) { stepAdobeNumber(1); return true; }
                if ("s2:number:quantity:dec".equals(id)) { stepAdobeNumber(-1); return true; }
                if ("s2:number:quantity".equals(id)) {
                    focusTextField("adobe-number", false, 0f); return true;
                }
            }
            if ("combo-box".equals(page) && activate && "s2:combo:input".equals(id)) {
                adobeComboInput.clear();
                openOverlay("adobe-combo", id);
                focusTextField("adobe-combo", false, 0f);
                return true;
            }
            if ("date-picker".equals(page) && activate && id.startsWith("s2:date:")) {
                String mode = "s2:date:single".equals(id) ? "single" :
                        "s2:date:range".equals(id) ? "range" : "";
                if (!mode.isEmpty()) {
                    if ("single".equals(mode) && adobeDateSingle != null)
                        adobeCalendarMonth = YearMonth.from(adobeDateSingle);
                    if ("range".equals(mode) && adobeDateRangeStart != null)
                        adobeCalendarMonth = YearMonth.from(adobeDateRangeStart);
                    adobeCalendarFocusDay = "single".equals(mode) && adobeDateSingle != null
                            ? adobeDateSingle.getDayOfMonth() : adobeDateRangeStart != null
                            ? adobeDateRangeStart.getDayOfMonth() : 1;
                    openOverlay("adobe-date-" + mode, id);
                    return true;
                }
                if ("s2:date:range:clear".equals(id)) {
                    adobeDateRangeStart = null; adobeDateRangeEnd = null;
            adobeDateRangeDraftStart = null; return true;
                }
            }
            if ("calendar".equals(page) && id.startsWith("s2:calendar:standalone:")) {
                if (activate) {
                    if (id.endsWith(":previous")) { calendarMove(-1); return true; }
                    if (id.endsWith(":next")) { calendarMove(1); return true; }
                    if (id.contains(":day:")) {
                        try { calendarChoose(adobeCalendarFocusDay); }
                        catch (NumberFormatException ignored) { }
                        return true;
                    }
                }
                int delta = keyCode == InputKeys.LEFT ? -1 : keyCode == InputKeys.RIGHT ? 1 :
                        keyCode == InputKeys.UP ? -7 : keyCode == InputKeys.DOWN ? 7 : 0;
                if (delta != 0) {
                    LocalDate next = adobeCalendarMonth.atDay(adobeCalendarFocusDay).plusDays(delta);
                    if (!next.isBefore(LocalDate.of(2026, 1, 1))
                            && !next.isAfter(LocalDate.of(2027, 12, 31))) {
                        adobeCalendarMonth = YearMonth.from(next);
                        adobeCalendarFocusDay = next.getDayOfMonth();
                    }
                    return true;
                }
            }
            if ("table".equals(page) && activate) {
                if (id.startsWith("s2:table:files:row:")) {
                    String row = id.substring("s2:table:files:row:".length());
                    if (!"locked".equals(row)) {
                        if (!adobeTableSelected.add(row)) adobeTableSelected.remove(row);
                    }
                    return true;
                }
                if (id.startsWith("s2:table:files:header:")) {
                    String column = id.substring("s2:table:files:header:".length());
                    if (Set.of("name", "type", "size").contains(column)) {
                        adobeTableDirection = column.equals(adobeTableSort)
                                && "asc".equals(adobeTableDirection) ? "desc" : "asc";
                        adobeTableSort = column;
                    }
                    return true;
                }
                if ("s2:table:files:select-all".equals(id)) {
                    if (adobeTableSelected.size() == 4) adobeTableSelected.clear();
                    else adobeTableSelected.addAll(List.of("budget", "roadmap", "proposal", "welcome"));
                    return true;
                }
                if (id.startsWith("s2:table:quiet:row:")) {
                    adobeTableQuiet = id.substring("s2:table:quiet:row:".length()); return true;
                }
                if (id.startsWith("s2:table:density:")) {
                    String density = id.substring("s2:table:density:".length());
                    if (Set.of("compact", "regular", "spacious").contains(density))
                        adobeTableDensity = density;
                    return true;
                }
            }
        }
        if (focused != null && "adobe".equals(kit) && !focused.state().disabled()) {
            String key = focused.key();
            boolean activate = keyCode == InputKeys.ENTER || keyCode == InputKeys.KP_ENTER
                    || keyCode == InputKeys.SPACE;
            int move = (keyCode == InputKeys.RIGHT || keyCode == InputKeys.DOWN) ? 1 :
                    (keyCode == InputKeys.LEFT || keyCode == InputKeys.UP) ? -1 : 0;
            if ("toggle-button-group".equals(page) && key.startsWith("s2:tbg:")) {
                boolean multiple = key.startsWith("s2:tbg:multi:item:");
                boolean single = key.startsWith("s2:tbg:single:item:");
                if (multiple || single) {
                    String[] options = multiple ? new String[]{"bold", "italic", "underline"}
                            : new String[]{"left", "center", "right"};
                    String prefix = multiple ? "s2:tbg:multi:item:" : "s2:tbg:single:item:";
                    String id = key.substring(prefix.length());
                    int at = Arrays.asList(options).indexOf(id);
                    if (at >= 0) {
                        if (activate) {
                            if (multiple) {
                                if (!adobeToggleGroupMulti.add(id)) adobeToggleGroupMulti.remove(id);
                            } else adobeToggleGroupSingle = id;
                            return true;
                        }
                        if (move != 0 || keyCode == InputKeys.HOME || keyCode == InputKeys.END) {
                            int next = keyCode == InputKeys.HOME ? 0 : keyCode == InputKeys.END
                                    ? options.length-1 : Math.floorMod(at+move,options.length);
                            UiNode target = find(runtime.root(),prefix+options[next]);
                            if (target != null) runtime.input().focus(target);
                            return true;
                        }
                    }
                }
            }
            if ("select-box-group".equals(page) && activate && key.startsWith("s2:sbg:")) {
                if (key.startsWith("s2:sbg:single:item:")) {
                    String id = key.substring("s2:sbg:single:item:".length());
                    if (Set.of("aws","azure","gcp").contains(id)) adobeSelectBoxSingle = id;
                    return true;
                }
                if (key.startsWith("s2:sbg:multi:item:")) {
                    String id = key.substring("s2:sbg:multi:item:".length());
                    if (Set.of("storage","cdn").contains(id)) {
                        if (!adobeSelectBoxMulti.add(id)) adobeSelectBoxMulti.remove(id);
                    }
                    return true;
                }
            }
            if ("tag-group".equals(page) && activate && key.startsWith("s2:tags:edit:")) {
                if (key.startsWith("s2:tags:edit:remove:")) {
                    String id = key.substring("s2:tags:edit:remove:".length());
                    adobeTagItems.remove(id); adobeTagSelected.remove(id); return true;
                }
                if (key.startsWith("s2:tags:edit:item:")) {
                    String id = key.substring("s2:tags:edit:item:".length());
                    if (adobeTagItems.contains(id)) {
                        if (!adobeTagSelected.add(id)) adobeTagSelected.remove(id);
                    }
                    return true;
                }
            }
            if ("disclosure".equals(page) && activate && key.startsWith("s2:disclosure:")
                    && key.endsWith(":trigger")) {
                String id = key.substring("s2:disclosure:".length(),key.length()-":trigger".length());
                if (Set.of("requirements","setup").contains(id)) {
                    if (!adobeDisclosures.add(id)) adobeDisclosures.remove(id);
                }
                return true;
            }
        }
        // Group controls use the normal focused UiNode; do not bind any JavaScript key loop.
        if (focused != null && "adobe".equals(kit) && "checkbox-group".equals(page)
                && focused.key().startsWith("s2:check-group:")
                && !focused.state().disabled()) {
            String id = focused.key().substring(focused.key().lastIndexOf(':') + 1);
            String[] enabled = {"design", "photography", "video"};
            int at = Arrays.asList(enabled).indexOf(id);
            if (at >= 0) {
                if (keyCode == InputKeys.ENTER || keyCode == InputKeys.KP_ENTER
                        || keyCode == InputKeys.SPACE) {
                    if (!adobeCheckboxGroup.add(id)) adobeCheckboxGroup.remove(id);
                    return true;
                }
                int direction = (keyCode == InputKeys.RIGHT || keyCode == InputKeys.DOWN
                        || keyCode == InputKeys.PAGE_DOWN) ? 1 :
                        (keyCode == InputKeys.LEFT || keyCode == InputKeys.UP
                        || keyCode == InputKeys.PAGE_UP) ? -1 : 0;
                if (direction != 0 || keyCode == InputKeys.HOME || keyCode == InputKeys.END) {
                    int dest = keyCode == InputKeys.HOME ? 0 : keyCode == InputKeys.END ? enabled.length - 1 :
                            Math.floorMod(at + direction, enabled.length);
                    String prefix = focused.key().startsWith("s2:check-group:horizontal:")
                            ? "s2:check-group:horizontal:item:" : "s2:check-group:vertical:item:";
                    UiNode target = find(runtime.root(), prefix + enabled[dest]);
                    if (target != null) runtime.input().focus(target);
                    return true;
                }
            }
        }
        if (focused != null && "adobe".equals(kit) && "segmented-control".equals(page)
                && focused.key().startsWith("s2:segments:") && !focused.state().disabled()) {
            String key = focused.key();
            String id = key.substring(key.lastIndexOf(':') + 1);
            String[] ids = {"list", "grid", "details"};
            int at = Arrays.asList(ids).indexOf(id);
            if (at >= 0) {
                if (keyCode == InputKeys.ENTER || keyCode == InputKeys.KP_ENTER
                        || keyCode == InputKeys.SPACE) {
                    adobeSegmentedView = id;
                    return true;
                }
                int direction = (keyCode == InputKeys.RIGHT || keyCode == InputKeys.DOWN) ? 1 :
                        (keyCode == InputKeys.LEFT || keyCode == InputKeys.UP) ? -1 : 0;
                if (direction != 0 || keyCode == InputKeys.HOME || keyCode == InputKeys.END) {
                    int dest = keyCode == InputKeys.HOME ? 0 : keyCode == InputKeys.END ? ids.length-1 :
                            Math.floorMod(at + direction, ids.length);
                    if (key.startsWith("s2:segments:disabled-item:") && dest == 1)
                        dest = direction < 0 ? 0 : 2;
                    String prefix = key.substring(0, key.lastIndexOf(":item:") + 6);
                    UiNode target = find(runtime.root(), prefix + ids[dest]);
                    if (target != null && !target.state().disabled()) {
                        runtime.input().focus(target);
                        adobeSegmentedView = ids[dest];
                    }
                    return true;
                }
            }
        }
        // ToggleButton uses Combatant's existing focused UiNode and normalized key codes.
        // Never dispatch disabled controls; emphasized/quiet mirror the Pin state.
        if (focused != null && "adobe".equals(kit) && "toggle-button".equals(page)
                && (keyCode == InputKeys.ENTER || keyCode == InputKeys.KP_ENTER
                        || keyCode == InputKeys.SPACE)
                && focused.key().startsWith("s2:toggle-button:")) {
            String id = focused.key().substring("s2:toggle-button:".length());
            if ("emphasized".equals(id) || "quiet".equals(id)) id = "pin";
            if (Set.of("pin", "favorite", "filter").contains(id)) {
                if (!adobeToggles.add(id)) adobeToggles.remove(id);
                return true;
            }
            if ("disabled".equals(id)) return true;
        }
        // Action groups use the host's existing focused UiNode, like RadioGroup.
        // The Java host owns selection; no JS globals or extra keyboard listeners.
        if (focused != null && "adobe".equals(kit) && "action-group".equals(page)
                && focused.key().startsWith("s2:ag:") && !focused.state().disabled()) {
            String key = focused.key();
            boolean activate = keyCode == InputKeys.ENTER || keyCode == InputKeys.KP_ENTER
                    || keyCode == InputKeys.SPACE;
            if (key.startsWith("s2:ag:single:item:")) {
                String id = key.substring("s2:ag:single:item:".length());
                String[] ids = {"list", "grid", "details"};
                int index = java.util.Arrays.asList(ids).indexOf(id);
                if (index >= 0) {
                    int direction = (keyCode == InputKeys.RIGHT || keyCode == InputKeys.DOWN) ? 1 :
                            (keyCode == InputKeys.LEFT || keyCode == InputKeys.UP) ? -1 : 0;
                    if (direction != 0) {
                        index = Math.floorMod(index + direction, ids.length);
                        UiNode target = find(runtime.root(), "s2:ag:single:item:" + ids[index]);
                        if (target != null) runtime.input().focus(target);
                        adobeActionGroupSingle = ids[index];
                        return true;
                    }
                    if (activate) { adobeActionGroupSingle = id; return true; }
                }
            } else if (key.startsWith("s2:ag:multi:item:") && activate) {
                String id = key.substring("s2:ag:multi:item:".length());
                if (Set.of("pin", "favorite", "lock").contains(id)) {
                    if (!adobeActionGroupMulti.add(id)) adobeActionGroupMulti.remove(id);
                    return true;
                }
            } else if (key.startsWith("s2:ag:quiet:item:") && activate) {
                String id = key.substring("s2:ag:quiet:item:".length());
                if (Set.of("edit", "copy").contains(id)) {
                    event("adobe:action-group:" + id);
                    return true;
                }
            }
        }
        if (focused != null && "adobe".equals(kit) && "button-group".equals(page)
                && focused.key().startsWith("s2:bg:") && !focused.state().disabled()
                && (keyCode == InputKeys.ENTER || keyCode == InputKeys.KP_ENTER
                        || keyCode == InputKeys.SPACE)) {
            String id = focused.key().substring(focused.key().lastIndexOf(':') + 1);
            if (Set.of("cancel", "preview", "save", "import").contains(id)) {
                event("adobe:button-group:" + id);
                return true;
            }
        }
        if (focused != null && "adobe".equals(kit) && !focused.state().disabled()
                && (keyCode == InputKeys.ENTER || keyCode == InputKeys.KP_ENTER
                    || keyCode == InputKeys.SPACE)) {
            String focusedKey = focused.key();
            if ("action-bar".equals(page)) {
                if (focusedKey.equals("s2:action-bar:select")) {
                    adobeActionBarSelected = 3; return true;
                }
                if (focusedKey.equals("s2:action-bar:clear")
                        || focusedKey.equals("s2:action-bar:emphasized:clear")) {
                    adobeActionBarSelected = 0; return true;
                }
                for (String id : new String[]{"edit", "copy", "share", "export"}) {
                    if (focusedKey.equals("s2:action-bar:action:" + id)
                            || focusedKey.equals("s2:action-bar:emphasized:action:" + id)) {
                        event("adobe:action-bar:" + id); return true;
                    }
                }
            }
            if ("illustrated-message".equals(page)) {
                for (String id : new String[]{"import", "upload", "clear"}) {
                    if (focusedKey.endsWith(":actions:item:" + id)
                            && focusedKey.startsWith("s2:illustration:")) {
                        event("adobe:illustrated:" + id); return true;
                    }
                }
            }
        }
        // Spectrum RadioGroup: arrow keys move selection among enabled radios, wrapping.
        // The actual focus remains in Combatant's UiInputDispatcher; no separate key stack.
        if (focused != null && focused.key() != null && focused.key().startsWith("s2:radio:")
                && "adobe".equals(kit) && "radio".equals(page)) {
            int dir = (keyCode == InputKeys.RIGHT || keyCode == InputKeys.DOWN) ? 1 :
                    (keyCode == InputKeys.LEFT || keyCode == InputKeys.UP) ? -1 : 0;
            if (dir != 0) {
                adobeRadio = Math.floorMod(adobeRadio + dir, 3);
                String groupPrefix = focused.key().startsWith("s2:radio:emphasis:")
                        ? "s2:radio:emphasis:" : "s2:radio:";
                UiNode target = find(runtime.root(), groupPrefix + adobeRadio);
                if (target != null) runtime.input().focus(target);
                return true;
            }
        }
        if (focused != null && "adobe".equals(kit)
                && ("tree-view".equals(page) || "breadcrumbs".equals(page))
                && focused.key().startsWith("s2:tree:item:")) {
            String id = focused.key().substring("s2:tree:item:".length());
            boolean toggleFocus = id.endsWith(":toggle");
            if (toggleFocus) id = id.substring(0, id.length() - ":toggle".length());
            boolean folder = ADOBE_TREE_CHILDREN.containsKey(id);
            if (keyCode == InputKeys.DOWN || keyCode == InputKeys.UP
                    || keyCode == InputKeys.HOME || keyCode == InputKeys.END) {
                List<String> visible = visibleAdobeTreeRows();
                int current = visible.indexOf(id);
                int target = switch (keyCode) {
                    case InputKeys.HOME -> 0;
                    case InputKeys.END -> visible.size() - 1;
                    case InputKeys.DOWN -> Math.min(visible.size() - 1, current + 1);
                    default -> Math.max(0, current - 1);
                };
                if (!visible.isEmpty()) focusAdobeTreeRow(visible.get(target));
                return true;
            }
            if (keyCode == InputKeys.RIGHT && folder) {
                if (!adobeTreeExpanded.add(id)) {
                    List<String> children = ADOBE_TREE_CHILDREN.getOrDefault(id, List.of());
                    if (!children.isEmpty()) focusAdobeTreeRow(children.get(0));
                }
                return true;
            }
            if (keyCode == InputKeys.LEFT) {
                if (folder && adobeTreeExpanded.remove(id)) return true;
                for (Map.Entry<String, List<String>> entry : ADOBE_TREE_CHILDREN.entrySet())
                    if (entry.getValue().contains(id)) {
                        focusAdobeTreeRow(entry.getKey());
                        break;
                    }
                return true;
            }
            if ((keyCode == InputKeys.ENTER || keyCode == InputKeys.KP_ENTER
                    || keyCode == InputKeys.SPACE) && toggleFocus && folder) {
                if (!adobeTreeExpanded.add(id)) adobeTreeExpanded.remove(id);
                return true;
            }
            if ((keyCode == InputKeys.ENTER || keyCode == InputKeys.KP_ENTER
                    || keyCode == InputKeys.SPACE) && !"archive".equals(id)) {
                adobeTreeSelected = id;
                for (int i = 0; i < ADOBE_BREADCRUMB_IDS.length; i++)
                    if (ADOBE_BREADCRUMB_IDS[i].equals(id)) { adobeBreadcrumbCurrent = i; adobeTreeSelected = id; break; }
                return true;
            }
        }
        if (focused != null && "adobe".equals(kit) && "accordion".equals(page)
                && focused.key().startsWith("s2:accordion:item:")
                && (keyCode == InputKeys.ENTER || keyCode == InputKeys.KP_ENTER || keyCode == InputKeys.SPACE)) {
            try {
                int index = Integer.parseInt(focused.key().substring("s2:accordion:item:".length()));
                if (index >= 0 && index < adobeAccordionExpanded.length && index != 2) {
                    boolean expand = !adobeAccordionExpanded[index];
                    if (expand && !adobeAccordionMultiple)
                        for (int i = 0; i < adobeAccordionExpanded.length; i++) adobeAccordionExpanded[i] = false;
                    adobeAccordionExpanded[index] = expand;
                }
            } catch (NumberFormatException ignored) { }
            return true;
        }
        if (focused != null && "adobe".equals(kit) && "breadcrumbs".equals(page)
                && focused.key().startsWith("s2:breadcrumbs:")
                && (keyCode == InputKeys.ENTER || keyCode == InputKeys.KP_ENTER || keyCode == InputKeys.SPACE)) {
            String id = focused.key().substring("s2:breadcrumbs:".length());
            if ("overflow".equals(id)) openOverlay("adobe-breadcrumbs", focused.key());
            else for (int i = 0; i < ADOBE_BREADCRUMB_IDS.length; i++)
                if (ADOBE_BREADCRUMB_IDS[i].equals(id)) { adobeBreadcrumbCurrent = i; adobeTreeSelected = id; break; }
            return true;
        }
        if (focused != null && "adobe".equals(kit) && "range-slider".equals(page)
                && "s2:range-slider".equals(focused.key())) {
            int thumb = InputModifierMask.has(modifiers, InputModifierMask.SHIFT)
                    ? 1 : adobeRangeActiveThumb;
            int delta = (keyCode == InputKeys.LEFT || keyCode == InputKeys.DOWN) ? -1 :
                    (keyCode == InputKeys.RIGHT || keyCode == InputKeys.UP) ? 1 : 0;
            if (keyCode == InputKeys.HOME || keyCode == InputKeys.END) {
                int value = keyCode == InputKeys.HOME ? 0 : 100;
                if (thumb == 0) adobeRangeStart = Math.min(value, adobeRangeEnd);
                else adobeRangeEnd = Math.max(value, adobeRangeStart);
                return true;
            }
            if (delta != 0) {
                if (thumb == 0) adobeRangeStart = Math.max(0,
                        Math.min(adobeRangeEnd, adobeRangeStart + delta));
                else adobeRangeEnd = Math.min(100,
                        Math.max(adobeRangeStart, adobeRangeEnd + delta));
                return true;
            }
        }
        if (focused != null && "s2:slider".equals(focused.key())) {
            float step = InputModifierMask.has(modifiers, InputModifierMask.SHIFT) ? 10f : 1f;
            if (keyCode == InputKeys.LEFT || keyCode == InputKeys.DOWN) {
                adobeSlider = Math.max(0f, adobeSlider - step); return true;
            }
            if (keyCode == InputKeys.RIGHT || keyCode == InputKeys.UP) {
                adobeSlider = Math.min(100f, adobeSlider + step); return true;
            }
            if (keyCode == InputKeys.HOME) { adobeSlider = 0f; return true; }
            if (keyCode == InputKeys.END) { adobeSlider = 100f; return true; }
        }
        if (focused != null && "lab:slider".equals(focused.key())) {
            float step = InputModifierMask.has(modifiers, InputModifierMask.SHIFT) ? 10f : 1f;
            if (keyCode == InputKeys.LEFT || keyCode == InputKeys.DOWN) {
                slider = Math.max(0f, slider - step);
                return true;
            }
            if (keyCode == InputKeys.RIGHT || keyCode == InputKeys.UP) {
                slider = Math.min(100f, slider + step);
                return true;
            }
            if (keyCode == InputKeys.HOME) { slider = 0f; return true; }
            if (keyCode == InputKeys.END) { slider = 100f; return true; }
        }
        return false;
    }

    @Override public void onSelected() { selected = true; }
    @Override public void onDeselected() {
        selected = false;
        clearTextFocus();
        finishOverlayClose();
        sliderDragging = false;
        adobeSliderDragging = false;
        adobeRangeDragging = -1;
        tooltip = "";
        snackbar = false;
        adobeToasts.clear();
        adobeToastPending.clear();
        adobeTooltipHover = "";
        adobeTooltipProgress = 0f;
    }
    @Override public boolean isVisible() { return selected; }
    @Override public boolean usesFullViewport() { return true; }
}

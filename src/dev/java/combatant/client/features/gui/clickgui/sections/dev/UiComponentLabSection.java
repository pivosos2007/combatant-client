/*
 * Combatant Client (GPL-3.0). Development-only component gallery, hosting independent Google and Adobe kits.
 * Rendering is native to Combatant; Google components live in their own JS kit.
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@ClickGuiSectionInfo(id = "combatant:component_lab", label = "UI Kits", order = 50)
public final class UiComponentLabSection implements ClickGuiSection {
    private final UiScriptModuleHandle handle = HudScriptLayouts.handle("combatant:modules/dev/component_gallery");
    private final CachedUiScriptRuntime scripts = new CachedUiScriptRuntime(HudScriptLayouts.runtimeReporter());
    private UiRuntime runtime;
    private UiRuntime actionsRuntime;
    private float x, y, width, height;
    private String kit = "google";
    private boolean adobeChecked, adobeEnabled = true, adobeAction;
    private int adobeRadio;
    private String adobeTab = "files", adobeView = "list";
    private String adobePicker = "medium";
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
    // Reuse the same editor, selection, clipboard and IME pipeline as the rest of ClickGUI.
    private final SingleLineTextInput textInput = new SingleLineTextInput(120);
    private String last = "none";
    private boolean enabled = true, checked, selected;
    private int radio, clicks;
    private float slider = 45f;
    private boolean sliderDragging;
    private float animatedTab = Float.NaN, animatedSwitch = Float.NaN;
    private float animatedAdobeTab = Float.NaN, animatedAdobeSwitch = Float.NaN;

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
        m.put("adobeRadio", adobeRadio);
        m.put("adobeTab", adobeTab);
        m.put("adobeView", adobeView);
        m.put("adobePicker", adobePicker);
        m.put("adobeSlider", adobeSlider);
        m.put("adobeSearch", adobeSearchInput.text());
        m.put("adobeSearchCaretOffset", adobeSearchCaretOffset());
        m.put("page", page);
        m.put("tab", tab);
        m.put("chip", chip);
        m.put("segment", segment);
        m.put("list", list);
        m.put("radio", radio);
        m.put("focus", focus);
        m.put("value", textInput.text());
        m.put("caret", textInput.cursor());
        m.put("caretOffset", caretOffset());
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
        float targetTab = switch (tab) { case "activity" -> 1f; case "settings" -> 2f; default -> 0f; };
        if (!Float.isFinite(animatedTab)) animatedTab = targetTab;
        if (!Float.isFinite(animatedSwitch)) animatedSwitch = enabled ? 1f : 0f;
        float response = 1f - (float) Math.exp(-14f * dt);
        animatedTab += (targetTab - animatedTab) * response;
        animatedSwitch += ((enabled ? 1f : 0f) - animatedSwitch) * response;
        float adobeTarget = switch (adobeTab) {case "activity" -> 1f; case "sharing" -> 2f; default -> 0f;};
        if (!Float.isFinite(animatedAdobeTab)) animatedAdobeTab = adobeTarget;
        if (!Float.isFinite(animatedAdobeSwitch)) animatedAdobeSwitch = adobeEnabled ? 1f : 0f;
        animatedAdobeTab += (adobeTarget - animatedAdobeTab) * response;
        animatedAdobeSwitch += ((adobeEnabled ? 1f : 0f) - animatedAdobeSwitch) * response;
        Map<String, Object> m = model();
        TextRenderer font = Fonts.renderer("adobe".equals(kit) ? "SourceSans3" : "Roboto",
                FontInfo.Type.Regular, ClickGuiRenderer.getOnestMedium());
        UiRuntime next = scripts.bake(handle, script, "component-lab", CachedUiScriptRuntime.signature(m),
                width, height, font, x, y, width, height, () -> m, null, this::animatedBounds);
        if (next == null) return;
        runtime = next;
        if (runtime != actionsRuntime) {
            registerActions(runtime);
            actionsRuntime = runtime;
        }
        runtime.input().updateHover(runtime.root(), mouseX, mouseY);
        syncOverlayFocus();
        if (sliderDragging) adjustSlider(mouseX);
        if (adobeSliderDragging) adjustAdobeSlider(mouseX);
        updateTextInputArea();
        runtime.render(new UiRenderContext(Renderer2D.COLOR, font, null, 0f, UiProjectionMode.CURRENT, 1f));
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

    /** Patch drawn geometry using the live node's own computed layout, never hard-coded screen coordinates. */
    private Map<String, UiBounds> animatedBounds() {
        if (runtime == null || runtime.root() == null) return Map.of();
        Map<String, UiBounds> patches = new LinkedHashMap<>();
        UiNode tabNode = find(runtime.root(), "lab:tabs");
        if (tabNode != null && "navigation".equals(page)) {
            UiBounds b = tabNode.bounds();
            float itemWidth = b.width() / 3f;
            patches.put("lab:tabs:indicator", new UiBounds(
                    b.x() + animatedTab * itemWidth + 12f, b.y() + 45f,
                    Math.max(1f, itemWidth - 24f), 3f));
        }
        UiNode toggle = find(runtime.root(), "lab:switch");
        if (toggle != null && "selection".equals(page)) {
            UiBounds b = toggle.bounds();
            float handleSize = 16f + 8f * animatedSwitch;
            float left = 8f + 16f * animatedSwitch;
            patches.put("lab:switch:handle", new UiBounds(
                    b.x() + left, b.y() + (32f - handleSize) * .5f,
                    handleSize, handleSize));
        }
        if ("adobe".equals(kit)) {
            UiNode spectrumTabs = find(runtime.root(), "s2:tabs");
            if (spectrumTabs != null && "navigation".equals(page)) {
                UiBounds b = spectrumTabs.bounds();
                float item = b.width() / 3f;
                patches.put("s2:tabs:indicator", new UiBounds(
                        b.x() + animatedAdobeTab * item + 8f,
                        b.y() + 45f, Math.max(1f, item - 16f), 3f));
            }
            UiNode spectrumSwitch = find(runtime.root(), "s2:switch");
            if (spectrumSwitch != null && "selection".equals(page)) {
                UiBounds b = spectrumSwitch.bounds();
                patches.put("s2:switch:thumb", new UiBounds(
                        b.x() + 6f + 26f * animatedAdobeSwitch,
                        b.y() + 8f, 16f, 16f));
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
        ui.actions().register("lab:kit:google", action -> {
            kit = "google";
            page = "buttons";
            closeOverlay();
            clearTextFocus();
            tooltip = "";
            snackbar = false;
            return true;
        });
        ui.actions().register("lab:kit:adobe", action -> {
            kit = "adobe";
            page = "buttons";
            clearTextFocus();
            closeOverlay();
            tooltip = "";
            snackbar = false;
            return true;
        });
        for (String variant : new String[]{"accent","primary","secondary","negative",
                "small","medium","large"}) {
            ui.actions().register("lab:adobe:button:" + variant,
                    action -> { event("adobe:button:" + variant); return true; });
        }
        ui.actions().register("lab:adobe:action:edit", action -> { event("adobe:edit"); return true; });
        ui.actions().register("lab:adobe:action:preview", action -> {
            adobeAction = !adobeAction;
            return true;
        });
        ui.actions().register("lab:adobe:checkbox", action -> {
            adobeChecked = !adobeChecked; return true;
        });
        ui.actions().register("lab:adobe:switch", action -> {
            adobeEnabled = !adobeEnabled; return true;
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
        for (String value : new String[]{"grid","list","detail"}) {
            ui.actions().register("lab:adobe:view:" + value, action -> {
                adobeView = value; return true;
            });
        }
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
        ui.actions().register("lab:adobe:slider:focus", action -> {
            event("adobe:slider"); return true;
        });
        for (String p : new String[]{"buttons","selection","inputs","navigation","surfaces","overlays","states"}) {
            ui.actions().register("lab:nav:" + p, action -> {
                page = p;
                clearTextFocus();
                tooltip = "";
                closeOverlay();
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
        tooltip = "";
        overlayIndex = "adobe-picker".equals(type) ? switch (adobePicker) {
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

    private void closeOverlay() {
        if (overlay.isEmpty()) return;
        overlay = "";
        submenu = "";
        submenuIndex = -1;
        menuTypeahead = "";
        if (runtime != null) {
            UiNode trigger = find(runtime.root(), overlayAnchor);
            runtime.input().focus(trigger);
        }
        overlayAnchor = "";
    }

    private void chooseOverlayOption() {
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
        if ("adobe-field".equals(focus) && "adobe".equals(kit) && "inputs".equals(page))
            return find(runtime.root(), "s2:field");
        if ("adobe-search".equals(focus) && "adobe".equals(kit) && "inputs".equals(page))
            return find(runtime.root(), "s2:search");
        if ("search".equals(focus) && "overlays".equals(page))
            return find(runtime.root(), "lab:search:field");
        if (!"inputs".equals(page)) return null;
        return find(runtime.root(), "lab:field:" + focus);
    }

    private void clearTextFocus() {
        focus = "";
        textInput.unfocus();
        searchInput.unfocus();
        adobeSearchInput.unfocus();
    }

    /** Keep platform text focus and IME area in sync with the actual UiNode layout. */
    private void updateTextInputArea() {
        UiNode field = focusedTextField();
        if (field == null) {
            if (!focus.isEmpty()) clearTextFocus();
            return;
        }
        UiBounds bounds = field.bounds();
        TextRenderer font = Fonts.renderer("adobe".equals(kit) ? "SourceSans3" : "Roboto",
                FontInfo.Type.Regular, ClickGuiRenderer.getOnestMedium());
        SingleLineTextInput editor = focusedEditor();
        float left = "search".equals(focus) ? 56f : "adobe-search".equals(focus) ? 43f :
                "adobe-field".equals(focus) ? 13f : 16f;
        float size = "search".equals(focus) ? 16f : "adobe-field".equals(focus)
                || "adobe-search".equals(focus) ? 17f : 18f;
        editor.layout(bounds.x() + left, Math.max(1f, bounds.width() - left - 16f), font, size);
        CombatantTextInputBridge.focus(editor,
                Math.round(bounds.x()), Math.round(bounds.y()),
                Math.round(bounds.x() + bounds.width()), Math.round(bounds.y() + bounds.height()));
    }

    private SingleLineTextInput focusedEditor() {
        return "search".equals(focus) ? searchInput :
                "adobe-search".equals(focus) ? adobeSearchInput : textInput;
    }

    private void focusTextField(String field, boolean positionCaret, float mouseX) {
        if (!"filled".equals(field) && !"outlined".equals(field)
                && !"search".equals(field) && !"adobe-field".equals(field)
                && !"adobe-search".equals(field)) return;
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

    @Override
    public boolean mousePressed(float mouseX, float mouseY, int button) {
        if (runtime == null) return false;
        boolean handled = runtime.input().pointerDown(runtime.root(),
                new UiPointerEvent(mouseX, mouseY, runtimePointerButton(button)));
        UiNode pressed = runtime.input().state().pressedNode();
        if (button == InputKeys.MOUSE_LEFT) {
            String pressedKey = pressed == null ? "" : pressed.key();
            if ("lab:field:filled".equals(pressedKey)) focusTextField("filled", true, mouseX);
            else if ("lab:field:outlined".equals(pressedKey)) focusTextField("outlined", true, mouseX);
            else if ("lab:search:field".equals(pressedKey)) focusTextField("search", true, mouseX);
            else if ("s2:field".equals(pressedKey)) focusTextField("adobe-field", true, mouseX);
            else if ("s2:search".equals(pressedKey)) focusTextField("adobe-search", true, mouseX);
            else if (!"s2:search:clear".equals(pressedKey) && !focus.isEmpty()) clearTextFocus();
        }
        sliderDragging = button == InputKeys.MOUSE_LEFT && pressed != null && "lab:slider".equals(pressed.key());
        if (sliderDragging) adjustSlider(mouseX);
        adobeSliderDragging = button == InputKeys.MOUSE_LEFT && pressed != null
                && "s2:slider".equals(pressed.key());
        if (adobeSliderDragging) adjustAdobeSlider(mouseX);
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
        runtime.input().updateHover(runtime.root(), mouseX, mouseY);
        UiNode hovered = runtime.input().state().hoveredNode();
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
            if ("dialog".equals(overlay)) return true;
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
        if (!overlay.isEmpty()) {
            if (keyCode == InputKeys.TAB) { closeOverlay(); return true; }
            if (keyCode == InputKeys.ESCAPE) {
                if ("menu".equals(overlay) && !submenu.isEmpty()) {
                    submenu = "";
                    submenuIndex = -1;
                } else closeOverlay();
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
        closeOverlay();
        sliderDragging = false;
        adobeSliderDragging = false;
        tooltip = "";
        snackbar = false;
    }
    @Override public boolean isVisible() { return selected; }
    @Override public boolean usesFullViewport() { return true; }
}

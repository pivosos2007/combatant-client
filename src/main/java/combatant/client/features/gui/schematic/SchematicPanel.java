/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.gui.schematic;

import combatant.client.render.engine.text.BuiltinFontCatalog;

import combatant.client.render.engine.renderer.ui.draw.LayoutRender2D;

import combatant.client.automation.compat.litematica.LitematicaCompat;
import combatant.client.automation.compat.litematica.LitematicaWorkspace;
import combatant.client.features.gui.clickgui.layout.screen.settings.implement.module.SettingsPanelComponent;
import combatant.client.features.gui.clickgui.settings.SettingErrorView;
import combatant.client.features.gui.clickgui.settings.SettingRenderSurface;
import combatant.client.features.gui.hud.script.HudScriptLayouts;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleManager;
import combatant.client.features.module.modules.player.Printer;
import combatant.client.features.theme.Theme;
import combatant.client.input.CombatantInput;
import combatant.client.input.InputDevice;
import combatant.client.input.InputKeys;
import combatant.client.input.InputModifierMask;
import combatant.client.input.minecraft.CombatantTextInputBridge;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.render.engine.core.ViewportContext;
import combatant.client.render.engine.math.HudScale;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.helpers.ScissorFunction;
import combatant.client.render.engine.renderer.ui.runtime.core.UiRuntime;
import combatant.client.render.engine.renderer.ui.runtime.core.UiBounds;
import combatant.client.render.engine.renderer.ui.runtime.core.UiNode;
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
import combatant.client.util.resources.asset.UiScriptAsset;
import combatant.client.util.schematic.LayerStats;
import combatant.client.util.schematic.Schematic;
import combatant.client.util.schematic.SchematicAttention;
import combatant.client.util.schematic.SchematicDiff;
import combatant.client.util.schematic.SchematicHeader;
import combatant.client.util.schematic.SchematicFormats;
import combatant.client.util.schematic.SchematicLibrary;
import combatant.client.util.schematic.SchematicMaterials;
import combatant.client.util.schematic.SchematicPlacement;
import combatant.client.util.schematic.SchematicState;
import combatant.client.util.text.SingleLineTextInput;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@UiScriptAsset("combatant:modules/schematic/workspace")
public final class SchematicPanel {
    private static final float SETTINGS_SCALE = 2.0f;
    private static final int[] SPEEDS = {1, 2, 4, 8};
    private static final long INVENTORY_INTERVAL = 500_000_000L;
    private static final long DIFF_INTERVAL = 50_000_000L;
    private final Runnable onClose;
    private final UiScriptModuleHandle moduleHandle = HudScriptLayouts.handle(SchematicPanel.class);
    private final CachedUiScriptRuntime script = new CachedUiScriptRuntime(HudScriptLayouts.runtimeReporter());
    private final SettingsPanelComponent settingsPanel = new SettingsPanelComponent(SettingRenderSurface.MODULES);
    private final SchematicState state = SchematicState.INSTANCE;
    private final SchematicLibrary library = SchematicLibrary.INSTANCE;
    private final SchematicViewport viewport = new SchematicViewport();
    private final SchematicLayerGrid grid = new SchematicLayerGrid();
    private final SchematicUi nativeUi = new SchematicUi();
    private final LitematicaSettingsPanel litematicaSettings = new LitematicaSettingsPanel();
    private boolean litematicaSettingsMode;
    private final SingleLineTextInput searchInput = new SingleLineTextInput(96);
    private final SingleLineTextInput originInput = new SingleLineTextInput(14,
            ch -> ch >= '0' && ch <= '9' || ch == '-');
    private int originEdit = -1;
    private int selectedLiteIndex = -1;
    private int linkedLiteIndex = -1;
    private int previewedLiteIndex = -1;
    private TextRenderer searchFont;
    private long searchFontGeneration = -1L;

    private TextRenderer currentSearchFont() {
        long generation = Fonts.generation();
        if (searchFont == null || searchFontGeneration != generation) {
            searchFont = Fonts.renderer("SourceSans3", FontInfo.Type.Regular,
                    BuiltinFontCatalog.ONEST_MEDIUM.renderer());
            searchFontGeneration = generation;
        }
        return searchFont;
    }
    private UiRuntime runtime;
    private Layout bounds;
    private boolean initialized;
    private float mouseX, mouseY;
    private float openAnim;
    private long lastFrame, lastDiffTick;
    private boolean searchFocused;
    private Path selectedPath;
    private List<SchematicHeader> filtered = List.of();
    private String filterKey = "";
    private int filterGeneration = -1;
    private boolean view3d = true;
    private boolean playing;
    private int speedIndex = 1;
    private float playAccumulator;
    private boolean layerDragging, gapDragging, gridDragging;
    private float gridPanX, gridPanY, gridZoom = 1f, lastGridX, lastGridY;
    private boolean doubleClickFile;
    private boolean materialsTotal, issuesShown, libraryMode = true;
    private boolean cardsMode;
    private String visibleDrawer = "none";
    private float drawerProgress;
    private boolean drawerEnterPending;
    private long drawerEnterStarted;
    private long lastFeedbackSequence;
    private final List<LocalToast> localToasts = new ArrayList<>();
    private static final long TOAST_DURATION = 4_500_000_000L;
    private static final long TOAST_DISMISS_NS = 220_000_000L;
    private static final class LocalToast {
        private final long id;
        private final String message, variant;
        private long started;
        private long dismissedAt;
        private int count = 1;
        private float stack = -1f;
        private LocalToast(long id, String message, String variant, long started) {
            this.id = id; this.message = message; this.variant = variant; this.started = started;
        }
        long id() { return id; }
        String message() { return message; }
        String variant() { return variant; }
        long started() { return started; }
        int count() { return count; }
    }
    private String drawerMode = "none";
    private String settingsId;
    private List<SchematicMaterials.Entry> materials = List.of();
    private List<SchematicAttention.Entry> issues = List.of();
    private int[] materialHave = new int[0];
    private Object materialKey, issueKey;
    private long materialHaveAt;

    public SchematicPanel(Runnable onClose) { this.onClose = onClose; }
    public SchematicPanel() { this(null); }

    private static String tr(String key, Object... args) { return I18n.get(key, args); }
    private static LitematicaWorkspace lite() { return LitematicaCompat.workspace(); }
    private static float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }
    private static String color(int argb) { return String.format("#%08X", argb | 0xFF000000); }
    private static Map<String, Object> map(Object... entries) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i + 1 < entries.length; i += 2) result.put((String) entries[i], entries[i + 1]);
        return result;
    }
    private static boolean inside(float mx, float my, float x, float y, float w, float h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    public void init() {
        nativeUi.init();
        initialized = true;
        library.refresh();
        lastFeedbackSequence = SchematicActions.feedbackSequence();
        localToasts.clear();
        LitematicaWorkspace bridge = lite();
        LitematicaWorkspace.Placement active = null;
        if (bridge != null) for (LitematicaWorkspace.Placement entry : bridge.placements()) {
            if (entry.selected()) { active = entry; selectedLiteIndex = entry.index(); break; }
        }
        if (active == null) state.clear();
        libraryMode = active == null;
        previewedLiteIndex = active == null ? -1 : active.index();
        if (active != null) {
            if (state.schematic() != null && (active.file() == null ||
                    !state.schematic().fileName().equals(active.file().getFileName().toString())))
                state.clear();
            if (active.file() != null && state.schematic() == null
                    && SchematicActions.loadingPath() == null) {
                SchematicActions.preview(active.file());
            }
        }
        if (selectedPath == null && state.schematic() != null)
            selectedPath = library.resolve(state.schematic().fileName());
    }

    public void restartOpenAnimation() { openAnim = 0f; lastFrame = 0L; }
    public void tick() { /* Litematica owns in-world verification and rendering. */ }

    public void renderStandalone(GuiGraphicsExtractor context, int mx, int my) {
        if (!initialized) init();
        Minecraft mc = Minecraft.getInstance();
        float fixedWidth = Math.max(1f, HudScale.virtualWidth(mc.getWindow().getWidth(), mc.getWindow().getHeight()));
        float fixedHeight = Math.max(1f, HudScale.virtualHeight(mc.getWindow().getWidth(), mc.getWindow().getHeight()));
        mouseX = toFixed(mx);
        mouseY = toFixed(my);
        float width = Math.min(fixedWidth - 34f, 1660f);
        float height = Math.min(fixedHeight - 34f, 980f);
        bounds = Layout.compute((fixedWidth - width) * .5f, (fixedHeight - height) * .5f, width, height, true, libraryMode, drawerProgress);
        ViewportContext.beginUnscaledLogical(context);
        Renderer2D.COLOR.begin();
        try {
            Renderer2D.COLOR.quad(0, 0, fixedWidth, fixedHeight, Theme.theme().windowBg() | 0xFF000000);
            renderWorkspace();
        }
        finally {
            Renderer2D.COLOR.render();
            ViewportContext.end(context);
        }
    }

    public void renderEmbedded(float x, float y, float w, float h, float mx, float my) {
        if (!initialized) init();
        mouseX = mx;
        mouseY = my;
        bounds = Layout.compute(x, y, w, h, false, libraryMode, drawerProgress);
        mouseDragged(mx, my);
        renderWorkspace();
    }

    private void renderWorkspace() {
        long now = System.nanoTime();
        float dt = lastFrame == 0L ? .016f : clamp((now - lastFrame) / 1_000_000_000f, .001f, .05f);
        lastFrame = now;
        openAnim = Math.min(1f, openAnim + dt * 5f);
        if (now - lastDiffTick >= DIFF_INTERVAL) { lastDiffTick = now; tick(); }
        if (playing && state.hasSchematic()) {
            playAccumulator += dt * SPEEDS[speedIndex] * 2f;
            while (playAccumulator >= 1f) {
                playAccumulator -= 1f;
                if (state.layer() >= state.layerCount() - 1) { playing = false; break; }
                state.stepLayer(1);
            }
        }
        SchematicUi.syncTheme();
        if (bounds != null) {
            searchInput.layout(bounds.searchX + 43f, Math.max(40f, bounds.searchW - 88f),
                    currentSearchFont(), 17f);
        }
        if (!litematicaSettingsMode) refreshFiltered();
        for (SchematicActions.Feedback item : SchematicActions.feedbackSince(lastFeedbackSequence)) {
            lastFeedbackSequence = item.sequence();
            LocalToast duplicate = null;
            for (LocalToast itemToast : localToasts) {
                if (itemToast.message.equals(item.message()) && itemToast.variant.equals(item.variant())) {
                    duplicate = itemToast; break;
                }
            }
            if (duplicate != null) {
                duplicate.count++;
                duplicate.started = now;
                duplicate.dismissedAt = 0L;
                localToasts.remove(duplicate);
                localToasts.add(duplicate); // newer feedback rises to the top of the stack
            } else localToasts.add(new LocalToast(item.sequence(), item.message(), item.variant(), now));
        }
        localToasts.removeIf(item -> now - item.started() >= TOAST_DURATION ||
                (item.dismissedAt != 0L && now - item.dismissedAt >= TOAST_DISMISS_NS));
        if (localToasts.size() > 3) localToasts.subList(0, localToasts.size() - 3).clear();
        for (int i = 0; i < localToasts.size(); i++) {
            LocalToast toast = localToasts.get(i);
            float target = (localToasts.size() - i - 1) * 73f;
            if (toast.stack < 0f) toast.stack = target + 12f;
            toast.stack += (target - toast.stack) * Math.min(1f, dt * 13f);
        }
        float desiredDrawer = "none".equals(drawerMode) || libraryMode || litematicaSettingsMode ? 0f : 1f;
        if (desiredDrawer == 0f) drawerEnterPending = false;
        if (drawerEnterPending && (runtime != null && findUiNode(runtime.root(), "drawer:reveal") != null
                || now - drawerEnterStarted > 650_000_000L)) drawerEnterPending = false;
        if (!drawerEnterPending)
            drawerProgress += (desiredDrawer - drawerProgress) * Math.min(1f, dt * 9f);
        if (Math.abs(desiredDrawer - drawerProgress) < .005f) drawerProgress = desiredDrawer;
        if (!"none".equals(drawerMode)) visibleDrawer = drawerMode;
        else if (drawerProgress == 0f) visibleDrawer = "none";
        if (bounds != null) {
            bounds = Layout.compute(bounds.x, bounds.y, bounds.w, bounds.h,
                    onClose != null, libraryMode || litematicaSettingsMode, drawerProgress);
            if (originEdit >= 0 && searchFont != null) {
                originInput.layout(bounds.x + bounds.inspectorX + 84f,
                        Math.max(1f, bounds.inspectorW - 155f), searchFont, 17f);
            }
        }
        LitematicaWorkspace bridge = lite();
        linkedLiteIndex = -1;
        Path currentNativeFile = null;
        if (bridge != null) for (LitematicaWorkspace.Placement entry : bridge.placements()) {
            if (entry.selected()) {
                selectedLiteIndex = linkedLiteIndex = entry.index();
                currentNativeFile = entry.file();
                if (state.placement() != null && !entry.origin().equals(state.placement().origin())
                        && originEdit < 0) state.setOrigin(entry.origin());
                break;
            }
        }
        if (previewedLiteIndex != linkedLiteIndex && SchematicActions.loadingPath() == null) {
            previewedLiteIndex = linkedLiteIndex;
            state.clear();
            if (linkedLiteIndex >= 0 && currentNativeFile != null)
                SchematicActions.preview(currentNativeFile);
            if (linkedLiteIndex < 0 && !libraryMode) {
                libraryMode = true;
                drawerMode = "none";
            }
        }
        if (!litematicaSettingsMode) {
            refreshMaterials(now);
            refreshIssues();
        }
        Renderer2D.COLOR.blurRect(bounds.x, bounds.y, bounds.w, bounds.h,
                0f, 29f, 1f, .94f, 0xFFFFFF);
        if (!libraryMode && !litematicaSettingsMode)
            Renderer2D.COLOR.quad(bounds.x, bounds.y, bounds.w, bounds.h, 0xA8171717);
        if (!litematicaSettingsMode) renderCanvas(dt);
        renderScript();
        if (litematicaSettingsMode) {
            litematicaSettings.updateInput(bounds.x + 54f, bounds.y + bounds.header,
                    Math.max(1f, bounds.w - 54f), Math.max(1f, bounds.h - bounds.header),
                    currentSearchFont(), runtime);
        } else renderCanvasTooltip();
        if (!litematicaSettingsMode && originEdit >= 0 && bounds != null && !libraryMode && "properties".equals(drawerMode)) {
            float fx = bounds.x + bounds.inspectorX + 70f;
            float fy = bounds.y + bounds.header + 95f + originEdit * 45f;
            CombatantTextInputBridge.focus(originInput, Math.round(fx), Math.round(fy),
                    Math.round(fx + Math.max(1f, bounds.inspectorW - 129f)), Math.round(fy + 40f));
            CombatantTextInputBridge.blur(searchInput);
        } else if (!litematicaSettingsMode && searchFocused && bounds != null && libraryMode) {
            CombatantTextInputBridge.focus(searchInput,
                    Math.round(bounds.searchX), Math.round(bounds.searchY),
                    Math.round(bounds.searchX + bounds.searchW), Math.round(bounds.searchY + 44f));
            CombatantTextInputBridge.blur(originInput);
        } else {
            CombatantTextInputBridge.blur(searchInput);
            CombatantTextInputBridge.blur(originInput);
        }
        settingsPanel.render(0f, 0f, 0f, 0f, mouseX, mouseY, SETTINGS_SCALE);
    }

    private void renderScript() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getResourceManager() == null) return;
        HudScriptLayouts.pollReloadCombo(mc);
        if (moduleHandle.consumeChanged()) { script.reset(); runtime = null; }
        if (!moduleHandle.ensureLoaded(mc.getResourceManager())) {
            HudScriptLayouts.reportLoadError(moduleHandle);
            return;
        }
        UiScriptModule module = moduleHandle.module();
        if (module == null) return;
        if (litematicaSettingsMode) litematicaSettings.syncViewport(runtime);
        Map<String, Object> props = props();
        runtime = script.bake(moduleHandle, module, "schematic-workspace",
                CachedUiScriptRuntime.signature(props), bounds.w, bounds.h,
                BuiltinFontCatalog.ONEST_MEDIUM.renderer(), bounds.x, bounds.y, bounds.w, bounds.h, () -> props);
        if (runtime == null) return;
        registerActions();
        if (litematicaSettingsMode) litematicaSettings.registerActions(runtime);
        runtime.render(new UiRenderContext(Renderer2D.COLOR, BuiltinFontCatalog.ONEST_MEDIUM.renderer(),
                        null, 0f, UiProjectionMode.CURRENT, AnimationUtility.easeOutCubic(openAnim)),
                () -> {
                    animateRetainedDrawer();
                    if (litematicaSettingsMode) litematicaSettings.paintLiveColor(runtime);
                    runtime.input().updateHover(runtime.root(), mouseX, mouseY);
                });
    }

    private void animateRetainedDrawer() {
        if (runtime == null || bounds == null || libraryMode || litematicaSettingsMode) return;
        UiNode drawer = findUiNode(runtime.root(), "drawer:reveal");
        if (drawer == null) return;
        float targetX = bounds.x + bounds.inspectorX;
        float delta = targetX - drawer.bounds().x();
        if (Math.abs(delta) < 0.01f) return;
        shiftSubtreeX(drawer, delta);
    }

    private static void shiftSubtreeX(UiNode node, float dx) {
        UiBounds old = node.bounds();
        node.setBounds(new UiBounds(old.x() + dx, old.y(), old.width(), old.height()));
        for (UiNode child : node.children()) shiftSubtreeX(child, dx);
    }

    private static UiNode findUiNode(UiNode node, String key) {
        if (node == null) return null;
        if (key.equals(node.key())) return node;
        for (UiNode child : node.children()) {
            UiNode result = findUiNode(child, key);
            if (result != null) return result;
        }
        return null;
    }

    private Map<String, Object> liteProps() {
        int synced = Theme.theme() == null ? 0xFF4069FD : blend(0xFF4069FD, Theme.theme().accent(), .82f);
        int background = Theme.theme() == null ? 0xFF1D1D1D : blend(0xFF1D1D1D, Theme.theme().windowBg(), .15f);
        int surface = Theme.theme() == null ? 0xFF292929 : blend(0xFF292929, Theme.theme().surface(), .18f);
        int border = Theme.theme() == null ? 0xFF737373 : blend(0xFF737373, Theme.theme().strokeSoft(), .18f);
        Map<String,Object> colors = map("accent",color(synced),
                "accentHover",color(blend(synced,0xFFFFFFFF,.14f)),
                "accentPress",color(blend(synced,0xFFFFFFFF,.27f)),
                "focus",color(blend(synced,0xFFFFFFFF,.36f)),
                "selected",color(blend(0xFF333333,synced,.31f)),
                "background",color(background),"surface",color(surface),"border",color(border),
                "raised",color(blend(surface,0xFFFFFFFF,.05f)));
        Layout configLayout = Layout.compute(bounds.x, bounds.y, bounds.w, bounds.h,
                onClose != null, true, 0f);
        return map("width",bounds.w,"height",bounds.h,"layout",configLayout.props(),
                "colors",colors,"liteSettingsMode",true,"liteCategory",litematicaSettings.selectedGroup(),
                "lite",litematicaSettings.snapshot(),"libraryMode",false,"loaded",linkedLiteIndex>=0,
                "loading",false,"standalone",onClose!=null,"toasts",toastSnapshot());
    }

    private List<Map<String,Object>> toastSnapshot() {
        List<Map<String,Object>> result = new ArrayList<>(localToasts.size());
        long now = System.nanoTime();
        for (LocalToast toast : localToasts) {
            float age = clamp((now - toast.started()) / (float)TOAST_DURATION, 0f, 1f);
            float entering = clamp(age * 12f, 0f, 1f);
            float leaving = clamp((1f - age) * 12f, 0f, 1f);
            float fade = Math.min(entering * entering * (3f - 2f * entering),
                    leaving * leaving * (3f - 2f * leaving));
            if (toast.dismissedAt != 0L) {
                float remaining = clamp(1f - (now - toast.dismissedAt) / (float)TOAST_DISMISS_NS, 0f, 1f);
                fade = Math.min(fade, remaining * remaining * (3f - 2f * remaining));
            }
            result.add(map("id", toast.id(), "message", toast.message(), "variant", toast.variant(),
                    "alpha", Math.round(clamp(fade, 0f, 1f) * 10f) / 10f,
                    "count", toast.count(), "stack", Math.round(toast.stack / 3f) * 3f));
        }
        return result;
    }

    private Map<String, Object> props() {
        if (litematicaSettingsMode) return liteProps();
        Schematic schematic = state.schematic();
        SchematicDiff diff = state.currentDiff();
        SchematicPlacement placement = state.placement();
        if (diff != null && diff.schematic() != schematic) diff = null;
        String searchText = searchInput.text();
        float searchScroll = searchInput.viewportOffset();
        float caret = textTo(searchText, searchInput.cursor());
        float selectFrom = textTo(searchText, searchInput.selectionStart());
        float selectTo = textTo(searchText, searchInput.selectionEnd());
        List<Map<String, Object>> files = new ArrayList<>();
        boolean canLoad = false;
        for (int i = 0; i < filtered.size(); i++) {
            SchematicHeader file = filtered.get(i);
            boolean selected = file.path().equals(selectedPath);
            canLoad |= selected && !file.failed();
            String extension = SchematicFormats.extensionOf(file.path());
            files.add(map("id", String.valueOf(i), "title", file.name(), "file", file.fileName(),
                    "format", extension == null ? "FILE" : extension.substring(1).toUpperCase(Locale.ROOT),
                    "meta", file.failed() ? file.error() : file.dimensions() + " · " + file.totalBlocks(),
                    "size", file.dimensions(), "blocks", file.totalBlocks(), "author", file.author(),
                    "regions", file.regionCount(), "modified", file.lastModified(),
                    "selected", selected, "failed", file.failed()));
        }
        List<Map<String, Object>> info = new ArrayList<>();
        if (schematic != null) {
            info.add(map("label", tr("screen.combatant.schematic.info.size"),
                    "value", schematic.sizeX() + " × " + schematic.sizeY() + " × " + schematic.sizeZ()));
            info.add(map("label", tr("screen.combatant.schematic.info.blocks"), "value", String.valueOf(schematic.totalBlocks())));
            info.add(map("label", tr("screen.combatant.schematic.info.author"), "value", schematic.author().isBlank() ? "—" : schematic.author()));
            if (placement != null) {
                BlockPos origin = placement.origin();
                info.add(map("label", tr("screen.combatant.schematic.info.origin"),
                        "value", origin.getX() + ", " + origin.getY() + ", " + origin.getZ()));
                String rotation = switch (placement.rotation()) {
                    case NONE -> "0°";
                    case CLOCKWISE_90 -> "90°";
                    case CLOCKWISE_180 -> "180°";
                    case COUNTERCLOCKWISE_90 -> "270°";
                };
                info.add(map("label", tr("screen.combatant.schematic.info.rotation"), "value", rotation));
                info.add(map("label", tr("screen.combatant.schematic.info.mirror"),
                        "value", tr("screen.combatant.schematic.mirror." + placement.mirror().name().toLowerCase(Locale.ROOT))));
            } else {
                info.add(map("label", tr("screen.combatant.schematic.info.origin"),
                        "value", tr("screen.combatant.schematic.info.not_placed")));
            }
            info.add(map("label", tr("screen.combatant.schematic.info.palette"), "value", String.valueOf(schematic.paletteSize() - 1)));
            info.add(map("label", tr("screen.combatant.schematic.info.regions"), "value", String.valueOf(schematic.regionCount())));
            info.add(map("label", tr("screen.combatant.schematic.info.warnings"), "value", String.valueOf(schematic.warnings().size())));
        }
        List<Map<String, Object>> inventory = new ArrayList<>();
        if (issuesShown) {
            for (int i = 0; i < issues.size(); i++) {
                SchematicAttention.Entry entry = issues.get(i);
                String place = entry.pos() == null ? "×" + entry.count()
                        : entry.pos().getX() + ", " + entry.pos().getY() + ", " + entry.pos().getZ();
                inventory.add(map("id", String.valueOf(i), "name", entry.state().getBlock().getName().getString(),
                        "item", BuiltInRegistries.ITEM.getKey(entry.state().getBlock().asItem()).toString(),
                        "detail", tr("screen.combatant.schematic.attention." + entry.reason().name().toLowerCase(Locale.ROOT)),
                        "quantity", place, "ratio", 0, "warning", true));
            }
        } else {
            for (int i = 0; i < materials.size(); i++) {
                SchematicMaterials.Entry entry = materials.get(i);
                int have = i < materialHave.length ? materialHave[i] : 0;
                boolean missing = state.missingMaterials().stream().anyMatch(m -> m.item() == entry.item());
                inventory.add(map("id", String.valueOf(i), "name", new ItemStack(entry.item()).getHoverName().getString(),
                        "item", BuiltInRegistries.ITEM.getKey(entry.item()).toString(),
                        "detail", missing ? tr("screen.combatant.schematic.materials_missing") : "",
                        "quantity", have + " / " + entry.count(),
                        "ratio", entry.count() <= 0 ? 1d : Math.min(1d, (double) have / entry.count()),
                        "warning", missing));
            }
        }
        List<Map<String, Object>> layers = new ArrayList<>();
        if (schematic != null) {
            int n = Math.min(100, state.layerCount());
            for (int i = 0; i < n; i++) {
                int low = i * state.layerCount() / n;
                int high = Math.max(low + 1, (i + 1) * state.layerCount() / n);
                int ok = 0, all = 0;
                if (diff != null) for (int j = low; j < high; j++) { ok += diff.correct(j); all += diff.total(j); }
                layers.add(map("id", i, "index", low, "active", state.layer() >= low && state.layer() < high,
                        "ratio", all == 0 ? 0d : (double) ok / all));
            }
        }
        SchematicHeader selectedFile = filtered.stream().filter(f -> f.path().equals(selectedPath))
                .findFirst().orElse(null);
        Map<String, Object> fileDetails = selectedFile == null ? map() : map(
                "name", selectedFile.name(), "file", selectedFile.fileName(),
                "format", SchematicFormats.extensionOf(selectedFile.path()) == null ? "FILE" :
                        SchematicFormats.extensionOf(selectedFile.path()).substring(1).toUpperCase(Locale.ROOT),
                "author", selectedFile.author(),
                "size", selectedFile.dimensions(), "blocks", selectedFile.totalBlocks(),
                "regions", selectedFile.regionCount(), "error", selectedFile.error(),
                "modified", selectedFile.lastModified());
        List<Map<String, Object>> toastProps = toastSnapshot();
        int correct = 0, total = 0;
        if (diff != null) for (int i = 0; i < state.layerCount(); i++) {
            correct += diff.correct(i); total += diff.total(i);
        }
        double progress = total > 0 ? (double) correct / total : 0d;
        int themeAccent = Theme.theme() == null ? 0xFF4069FD : Theme.theme().accent();
        int synced = blend(0xFF4069FD, themeAccent, .82f);
        int background = Theme.theme() == null ? 0xFF1D1D1D : blend(0xFF1D1D1D, Theme.theme().windowBg(), .15f);
        int surface = Theme.theme() == null ? 0xFF292929 : blend(0xFF292929, Theme.theme().surface(), .18f);
        int border = Theme.theme() == null ? 0xFF737373 : blend(0xFF737373, Theme.theme().strokeSoft(), .18f);
        Map<String, Object> colors = map("accent", color(synced), "accentHover", color(blend(synced, 0xFFFFFFFF, .14f)),
                "accentPress", color(blend(synced, 0xFFFFFFFF, .27f)),
                "focus", color(blend(synced, 0xFFFFFFFF, .36f)),
                "selected", color(blend(0xFF333333, synced, .31f)),
                "background", color(background), "surface", color(surface), "border", color(border),
                "raised", color(blend(surface, 0xFFFFFFFF, .05f)));
        LitematicaWorkspace bridge = lite();
        BlockPos origin = placement != null ? placement.origin() : BlockPos.ZERO;
        LitematicaWorkspace.Placement nativeActive = null;
        if (bridge != null) for (LitematicaWorkspace.Placement entry : bridge.placements())
            if (entry.selected()) { nativeActive = entry; origin = entry.origin(); break; }
        if (schematic == null && bridge != null && selectedLiteIndex >= 0) {
            for (LitematicaWorkspace.Placement entry : bridge.placements()) {
                if (entry.index() == selectedLiteIndex) { origin = entry.origin(); break; }
            }
        }
        float originCaret = originEdit < 0 ? 0f :
                LayoutRender2D.textWidth(searchFont,
                        originInput.text().substring(0, Math.min(originInput.cursor(), originInput.text().length())), 17f)
                        - originInput.viewportOffset();
        float originSelectFrom = originEdit < 0 ? 0f : textWidth(originInput.text(), originInput.selectionStart())
                - originInput.viewportOffset();
        float originSelectTo = originEdit < 0 ? 0f : textWidth(originInput.text(), originInput.selectionEnd())
                - originInput.viewportOffset();
        float scriptDrawer = Math.round(drawerProgress * 12f) / 12f;
        Layout scriptLayout = Layout.compute(bounds.x, bounds.y, bounds.w, bounds.h,
                onClose != null, libraryMode, scriptDrawer);
        return map("width", bounds.w, "height", bounds.h, "layout", scriptLayout.props(), "colors", colors,
                "origin", map("x", origin.getX(), "y", origin.getY(), "z", origin.getZ()),
                "coordActive", originEdit < 0 ? "" : switch(originEdit){case 0 -> "x"; case 1 -> "y";default -> "z";},
                "coordText", originInput.text(), "coordCaret", originCaret,
                "coordScroll", originInput.viewportOffset(),
                "coordSelectionStart", originSelectFrom, "coordSelectionEnd", originSelectTo,
                "litematica", bridge != null,
                "liteSettingsMode", litematicaSettingsMode,
                "liteCategory", litematicaSettings.selectedGroup(),
                "liteCategoryLabel", litematicaSettings.selectedGroupLabel(),
                "liteClip", bridge == null ? "" : bridge.clipMode(),
                "standalone", onClose != null, "fileCount", library.headers().size(),
                "scanning", library.isScanning(), "files", files, "search", searchText, "searchFocused", searchFocused,
                "searchScroll", searchScroll, "caretOffset", caret, "selectionStart", selectFrom, "selectionEnd", selectTo,
                "selected", selectedFile != null, "selectedName", selectedFile == null ? "" : selectedFile.name(),
                "canLoad", canLoad, "libraryMode", libraryMode, "drawerMode", visibleDrawer,
                "drawerProgress", scriptDrawer, "cardsMode", cardsMode,
                "fileDetails", fileDetails, "toasts", toastProps,
                "loaded", nativeActive != null, "previewAvailable", schematic != null,
                "title", nativeActive != null ? nativeActive.name() : "",
                "subtitle", schematic == null ? tr("screen.combatant.schematic.pick_file") : schematic.fileName(),
                "loading", SchematicActions.loadingPath() != null, "error", SchematicActions.lastError(),
                "placed", nativeActive != null, "building", SchematicActions.builderEnabled(),
                "projecting", SchematicActions.projectionEnabled(), "layer", state.layer() + 1,
                "layerCount", state.layerCount(), "layerMode", bridge == null ? "" : bridge.clipMode(),
                "playing", playing, "speed", SPEEDS[speedIndex], "view", view3d ? "3d" : "2d",
                "freeFly", viewport.freeFly(), "flySpeed", Math.round(viewport.flySpeed()),
                "viewMode", viewport.mode().name().toLowerCase(Locale.ROOT),
                "viewModeLabel", tr("screen.combatant.schematic.view.mode." + viewport.mode().name().toLowerCase(Locale.ROOT)),
                "autoRotate", viewport.autoRotate(), "gap", viewport.gap(), "exploded", viewport.mode().exploded(),
                "renderFpsTarget", viewport.targetFps(), "renderDistanceScale", viewport.renderDistanceScale(),
                "materialView", issuesShown ? "issues" : materialsTotal ? "total" : "layer",
                "issueCount", issues.size(), "inventory", inventory, "info", info,
                "layers", layers, "progress", progress, "progressLabel", correct + " / " + total,
                "builderStatus", builderStatus(), "closeLabel", tr("screen.combatant.schematic.close"),
                "settingsLabel", tr("screen.combatant.schematic.module_settings"), "labels", translations());
    }

    private float textWidth(String text, int index) {
        if (searchFont == null || text == null || index <= 0) return 0f;
        return LayoutRender2D.textWidth(searchFont, text.substring(0, Math.min(index, text.length())), 17f);
    }

    private float textTo(String text, int index) {
        return LayoutRender2D.textWidth(searchFont, text.substring(0, Math.min(Math.max(0,index), text.length())), 17f);
    }

    private Map<String, Object> translations() {
        String base = "screen.combatant.schematic.";
        return map("title", tr(base + "title"), "refresh", tr(base + "refresh"),
                "scanning", tr(base + "scanning"), "empty", tr(base + "empty"),
                "emptyHint", tr(base + "empty_hint"), "noMatches", tr(base + "no_matches"),
                "load", tr(base + "load"), "folder", tr(base + "open_folder"),
                "search", tr(base + "search"),
                "details", tr(base + "panel.details"), "materials", tr(base + "panel.materials"),
                "files", tr(base + "info.files"), "pick", tr(base + "pick_file"),
                "play", tr(base + "play"), "pause", tr(base + "pause"),
                "originHere", tr(base + "origin_here"), "originLook", tr(base + "origin_look"),
                "rotate", tr(base + "rotate"), "mirror", tr(base + "mirror"),
                "builderStart", tr(base + "builder_start"), "builderStop", tr(base + "builder_stop"),
                "projectionShow", tr(base + "projection_show"), "projectionHide", tr(base + "projection_hide"),
                "settings", tr(base + "module_settings"), "unload", tr(base + "unload"),
                "layerMaterials", tr(base + "materials_layer"), "allMaterials", tr(base + "materials_total"),
                "materialsNone", tr(base + "materials_none"), "attentionNone", tr(base + "attention_none"),
                "attention", tr(base + "attention_tab", issues.size()));
    }

    private static int blend(int a, int b, float t) {
        int out = 0xFF000000;
        for (int shift : new int[]{16, 8, 0}) {
            int aa = (a >>> shift) & 255, bb = (b >>> shift) & 255;
            out |= Math.round(aa + (bb - aa) * t) << shift;
        }
        return out;
    }

    private String builderStatus() {
        Printer printer = ModuleManager.get(Printer.class);
        return printer != null && printer.isEnabled() ? "Printer active (Litematica)" : "";
    }

    private void registerActions() {
        register("close", this::requestClose);
        register("refresh", library::refresh);
        register("folder", SchematicActions::openFolder);
        register("load", () -> { if (selectedPath != null) {
            SchematicActions.load(selectedPath);
            if (SchematicActions.lastError().isBlank()) { libraryMode = false; drawerMode = "none"; }
            searchFocused = false; searchInput.unfocus();
        } });
        register("unload", () -> { SchematicActions.unload(); libraryMode = true; drawerMode = "none";
            linkedLiteIndex = -1;
            searchFocused = false; searchInput.unfocus(); });
        register("search", () -> searchFocused = true);
        register("list-mode", () -> cardsMode = false);
        register("cards-mode", () -> cardsMode = true);
        register("search-clear", () -> { searchInput.clear(); searchFocused = true; });
        register("library", () -> { leaveLitematicaSettings(); libraryMode = true; drawerMode = "none"; });
        register("editor", () -> {
            // Returning from config must always work; if no placement exists, show Library.
            boolean hasDocument = state.hasSchematic() || linkedLiteIndex >= 0
                    || SchematicActions.loadingPath() != null;
            if (litematicaSettingsMode || hasDocument) {
                leaveLitematicaSettings(); libraryMode = !hasDocument;
                searchFocused = false; searchInput.unfocus();
            }
        });
        for (String category : new String[]{"generic", "visuals", "overlays", "colors", "hotkeys"})
            register("lite-category:" + category, () -> {
                if (originEdit >= 0) commitOriginEdit();
                searchFocused = false;
                searchInput.unfocus();
                drawerMode = "none";
                if (settingsPanel.isActive()) {settingsPanel.close();settingsId = null;}
                litematicaSettingsMode = true;
                litematicaSettings.selectGroup(category);
            });
        register("properties-toggle", () -> toggleDrawer("properties"));
        register("materials-toggle", () -> toggleDrawer("materials"));
        register("layers-toggle", () -> toggleDrawer("layers"));
        LitematicaWorkspace bridge = lite();
        register("origin-edit:x", () -> startOriginEdit(0));
        register("origin-edit:y", () -> startOriginEdit(1));
        register("origin-edit:z", () -> startOriginEdit(2));
        for (int axis = 0; axis < 3; axis++) {
            int target = axis;
            String name = switch(axis) { case 0 -> "x"; case 1 -> "y"; default -> "z"; };
            register("origin-step:" + name + ":up", () -> stepOrigin(target, 1));
            register("origin-step:" + name + ":down", () -> stepOrigin(target, -1));
        }
        register("drawer-close", () -> {
            if (originEdit >= 0) commitOriginEdit();
            drawerMode = "none";
        });
        for (LocalToast toast : localToasts) {
            runtime.actions().register("schematic:toast-close:" + toast.id(), ctx -> {
                for (LocalToast item : localToasts)
                    if (item.id() == toast.id() && item.dismissedAt == 0L)
                        item.dismissedAt = System.nanoTime();
                return true;
            });
        }
        register("origin-here", () -> {if (SchematicActions.setOriginHere()) syncLiteOrigin();});
        register("origin-look", () -> {if (SchematicActions.setOriginLooked()) syncLiteOrigin();});
        register("rotate", () -> {
            LitematicaWorkspace b = lite();
            if (b != null && linkedLiteIndex >= 0 && b.rotate(linkedLiteIndex))
                state.rotateClockwise();
        });
        register("mirror", () -> {
            LitematicaWorkspace b = lite();
            if (b != null && linkedLiteIndex >= 0 && b.mirror(linkedLiteIndex))
                state.cycleMirror();
        });
        register("builder", () -> {
            if (linkedLiteIndex >= 0 || SchematicActions.builderEnabled()) SchematicActions.toggleBuilder();
        });
        register("projection", SchematicActions::toggleProjection);
        register("layer-mode", SchematicActions::cycleLayerMode);
        register("builder-settings", () -> toggleSettings(Printer.class, "Printer"));
        register("view-3d", () -> view3d = true);
        register("view-2d", () -> view3d = false);
        register("view-mode", viewport::cycleMode);
        register("gap-decrease", () -> viewport.setGap(clamp(viewport.gap() - .25f, 0f, SchematicViewport.MAX_GAP)));
        register("gap-increase", () -> viewport.setGap(clamp(viewport.gap() + .25f, 0f, SchematicViewport.MAX_GAP)));
        register("auto-rotate", viewport::toggleAutoRotate);
        register("reset-camera", this::resetView);
        register("free-fly", viewport::toggleFreeFly);
        register("render-fps-down", () -> viewport.setTargetFps(viewport.targetFps() - 5));
        register("render-fps-up", () -> viewport.setTargetFps(viewport.targetFps() + 5));
        register("render-distance-down", () -> viewport.setRenderDistanceScale(viewport.renderDistanceScale() - .25f));
        register("render-distance-up", () -> viewport.setRenderDistanceScale(viewport.renderDistanceScale() + .25f));
        register("layer-prev", () -> state.stepLayer(-1));
        register("layer-next", () -> state.stepLayer(1));
        register("layer-prev10", () -> state.stepLayer(-10));
        register("layer-next10", () -> state.stepLayer(10));
        register("play", () -> { playing = !playing && state.hasSchematic(); playAccumulator = 0f; });
        register("speed", () -> speedIndex = (speedIndex + 1) % SPEEDS.length);
        register("material-layer", () -> { materialsTotal = false; issuesShown = false; });
        register("material-total", () -> { materialsTotal = true; issuesShown = false; });
        register("material-issues", () -> issuesShown = true);
        for (int i = 0; i < filtered.size(); i++) {
            int row = i;
            runtime.actions().register("schematic:file:" + i, context -> {
                if (row >= filtered.size()) return true;
                SchematicHeader file = filtered.get(row);
                selectedPath = file.path();
                if (doubleClickFile && !file.failed()) {
                    SchematicActions.load(selectedPath);
            if (SchematicActions.lastError().isBlank()) { libraryMode = false; drawerMode = "none"; }
            searchFocused = false; searchInput.unfocus();
                }
                searchFocused = false;
                return true;
            });
        }
    }

    private BlockPos displayedOrigin() {
        LitematicaWorkspace bridge = lite();
        if (bridge != null) for (LitematicaWorkspace.Placement entry : bridge.placements())
            if (entry.index() == selectedLiteIndex) return entry.origin();
        return state.placement() != null ? state.placement().origin() : BlockPos.ZERO;
    }

    private void startOriginEdit(int axis) {
        if (libraryMode || !"properties".equals(drawerMode) ||
                linkedLiteIndex < 0) return;
        if (originEdit >= 0 && originEdit != axis) commitOriginEdit();
        originEdit = axis;
        BlockPos origin = displayedOrigin();
        int value = axis == 0 ? origin.getX() : axis == 1 ? origin.getY() : origin.getZ();
        originInput.setText(Integer.toString(value));
        originInput.moveToEnd();
    }

    private void stepOrigin(int axis, int step) {
        if (linkedLiteIndex < 0) return;
        if (originEdit >= 0) commitOriginEdit();
        BlockPos current = displayedOrigin();
        long value = (axis == 0 ? current.getX() : axis == 1 ? current.getY() : current.getZ()) + (long)step;
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) return;
        BlockPos next = new BlockPos(axis == 0 ? (int)value : current.getX(),
                axis == 1 ? (int)value : current.getY(), axis == 2 ? (int)value : current.getZ());
        LitematicaWorkspace bridge = lite();
        if (bridge != null && bridge.setOrigin(linkedLiteIndex, next) && state.hasSchematic())
            state.setOrigin(next);
    }

    private void syncLiteOrigin() {
        LitematicaWorkspace bridge = lite();
        if (bridge != null && linkedLiteIndex >= 0 && state.placement() != null)
            bridge.setOrigin(linkedLiteIndex, state.placement().origin());
    }

    private void commitOriginEdit() {
        if (originEdit < 0) return;
        int axis = originEdit;
        try {
            int value = Integer.parseInt(originInput.text().trim());
            BlockPos pos = displayedOrigin();
            BlockPos next = new BlockPos(axis == 0 ? value : pos.getX(),
                    axis == 1 ? value : pos.getY(), axis == 2 ? value : pos.getZ());
            LitematicaWorkspace bridge = lite();
            int index = linkedLiteIndex;
            if (bridge != null && index >= 0) {
                bridge.setOrigin(index, next);
                // Native coordinate-lock masks may reject individual axes.
                for (LitematicaWorkspace.Placement entry : bridge.placements()) {
                    if (entry.index() == index) { next = entry.origin(); break; }
                }
            }
            if (state.hasSchematic()) state.setOrigin(next);
        } catch (NumberFormatException ignored) {
        }
        originEdit = -1;
        originInput.unfocus();
    }

    private void leaveLitematicaSettings() {
        if (litematicaSettingsMode) {
            litematicaSettings.removed();
            litematicaSettingsMode = false;
        }
    }

    private void toggleDrawer(String requested) {
        if (libraryMode || litematicaSettingsMode) return;
        if (originEdit >= 0) commitOriginEdit();
        String next = requested.equals(drawerMode) ? "none" : requested;
        if (!"none".equals(next) && drawerProgress < .01f) {
            // Give the worker a stable first snapshot that actually includes the drawer.
            drawerProgress = .09f;
            drawerEnterPending = true;
            drawerEnterStarted = System.nanoTime();
        } else if ("none".equals(next)) drawerEnterPending = false;
        drawerMode = next;
        searchFocused = false;
    }

    private boolean editorVisible() {
        return !libraryMode && !litematicaSettingsMode &&
                !(bounds != null && bounds.compact && !"none".equals(drawerMode));
    }

    private void register(String key, Runnable action) {
        runtime.actions().register("schematic:" + key, ctx -> { action.run(); return true; });
    }

    private void renderCanvas(float dt) {
        Schematic schematic = state.schematic();
        if (bounds == null || schematic == null || SchematicActions.loadingPath() != null
                || !editorVisible()) return;
        SchematicDiff diff = state.currentDiff();
        if (diff != null && diff.schematic() != schematic) diff = null;
        float vx = canvasNativeX(), vy = canvasNativeY();
        float vw = canvasNativeW(), vh = canvasNativeH();
        boolean hot = inside(mouseX, mouseY, vx, vy, vw, vh) && !layerDragging && !gapDragging;
        if (view3d) {
            if (viewport.freeFly() && !settingsPanel.isActive() && originEdit < 0 && !searchFocused) {
                viewport.fly(keyAxis(InputKeys.D, InputKeys.A),
                        keyAxis(InputKeys.SPACE, -1) - (shiftDown() ? 1f : 0f),
                        keyAxis(InputKeys.W, InputKeys.S), dt, ctrlDown());
            }
            viewport.render(schematic, grid.paletteColors(schematic), state.layer(), diff, vx, vy, vw, vh,
                    dt, mouseX, mouseY, hot && !insideInset(mouseX, mouseY), 1f);
            float side = insetSize(vw, vh);
            float ix = vx + 12f, iy = vy + vh - side - 14f;
            grid.layout(schematic, ix + 4f, iy + 4f, side - 8f, side - 8f);
            Renderer2D.COLOR.roundedRect(ix, iy, side, side, 8f, 0xEA1D1D1D);
            grid.render(schematic, state.layer(), diff, 1f);
        } else {
            grid.layout(schematic, vx + 10f, vy + 10f, vw - 20f, vh - 20f,
                    gridZoom, gridPanX, gridPanY);
            boolean clipped = ScissorFunction.pushRaw(vx, vy, vw, vh);
            try { grid.render(schematic, state.layer(), diff, 1f); }
            finally { if (clipped) ScissorFunction.pop(); }
        }
    }

    private float canvasNativeX() { return bounds.canvasX; }
    private float canvasNativeY() { return bounds.canvasY; }
    private float canvasNativeW() { return Math.max(20f, bounds.canvasW); }
    private float canvasNativeH() { return Math.max(20f, bounds.canvasH); }
    private static float insetSize(float vw, float vh) {
        return Math.min(170f, Math.max(74f, Math.min(vw, vh) * .24f));
    }
    private boolean insideInset(float mx, float my) {
        if (!view3d || bounds == null || !editorVisible()) return false;
        float vx = canvasNativeX(), vy = canvasNativeY();
        float vw = canvasNativeW(), vh = canvasNativeH(), side = insetSize(vw, vh);
        return inside(mx, my, vx + 12f, vy + vh - side - 14f, side, side);
    }

    private void renderCanvasTooltip() {
        if (bounds == null || state.schematic() == null || settingsPanel.isActive()
                || layerDragging || gapDragging || viewport.dragging()
                || !editorVisible()
                || !inside(mouseX, mouseY, canvasNativeX(), canvasNativeY(), canvasNativeW(), canvasNativeH())) return;
        Schematic schematic = state.schematic();
        int cx, cy, cz;
        if (view3d) {
            int hit = viewport.hovered();
            if (hit < 0) return;
            cx = schematic.xOf(hit); cy = schematic.yOf(hit); cz = schematic.zOf(hit);
        } else {
            int[] hit = grid.cellAt(schematic, mouseX, mouseY);
            if (hit == null) return;
            cx = hit[0]; cy = state.layer(); cz = hit[1];
        }
        int palette = schematic.paletteIndex(cx, cy, cz);
        if (palette <= 0) return;
        String name = schematic.paletteState(palette).getBlock().getName().getString();
        String id = schematic.paletteId(palette);
        String coords = tr("screen.combatant.schematic.tooltip.local", cx, cy, cz);
        if (state.placement() != null) {
            BlockPos world = state.placement().toWorld(cx, cy, cz);
            coords += " · " + tr("screen.combatant.schematic.tooltip.world", world.getX(), world.getY(), world.getZ());
        }
        float width = Math.min(Math.max(174f,
                Math.max(nativeUi.width(name, SchematicUi.BODY, true),
                         Math.max(nativeUi.width(id, SchematicUi.SMALL, false),
                                  nativeUi.width(coords, SchematicUi.SMALL, false))) + 28f),
                Math.max(174f, bounds.w - 20f));
        float height = 74f;
        float tx = Math.max(bounds.x + 8f, Math.min(mouseX + 16f, bounds.x + bounds.w - width - 8f));
        float ty = Math.max(bounds.y + 8f, Math.min(mouseY + 16f, bounds.y + bounds.h - height - 8f));
        int swatch = grid.paletteColor(schematic, palette) | 0xFF000000;
        Renderer2D.COLOR.roundedRect(tx, ty, width, height, 7f, swatch);
        boolean bodyClip = ScissorFunction.pushRaw(tx + 4f, ty, width - 4f, height);
        try { Renderer2D.COLOR.roundedRect(tx, ty, width, height, 7f, 0xF6292929); }
        finally { if (bodyClip) ScissorFunction.pop(); }
        Renderer2D.COLOR.roundedRectStroke(tx, ty, width, height, 7f, 1f, 0xFF737373);
        nativeUi.text(nativeUi.ellipsize(name, SchematicUi.BODY, width - 26f), tx + 12f, ty + 7f,
                SchematicUi.BODY, 0xFFF4F4F4, true);
        nativeUi.text(nativeUi.ellipsize(id, SchematicUi.SMALL, width - 26f), tx + 12f, ty + 29f,
                SchematicUi.SMALL, 0xFFB9B9B9, false);
        nativeUi.text(nativeUi.ellipsize(coords, SchematicUi.SMALL, width - 26f), tx + 12f, ty + 49f,
                SchematicUi.SMALL, 0xFFB9B9B9, false);
    }

    private void refreshFiltered() {
        String query = searchInput.text().trim().toLowerCase(Locale.ROOT);
        if (query.equals(filterKey) && filterGeneration == library.scanGeneration()) return;
        filterKey = query;
        filterGeneration = library.scanGeneration();
        List<SchematicHeader> result = new ArrayList<>();
        for (SchematicHeader f : library.headers()) {
            if (query.isEmpty() || f.name().toLowerCase(Locale.ROOT).contains(query)
                    || f.fileName().toLowerCase(Locale.ROOT).contains(query)) result.add(f);
        }
        filtered = result;
    }

    private void refreshMaterials(long now) {
        Schematic schematic = state.schematic();
        LayerStats stats = state.stats();
        if (schematic == null || stats == null) { materials = List.of(); materialKey = null; return; }
        List<Object> key = List.of(schematic, materialsTotal ? -1 : state.layer());
        if (!key.equals(materialKey)) {
            materialKey = key;
            materials = SchematicMaterials.materials(schematic,
                    materialsTotal ? stats.paletteTotals() : stats.layerHistogram(state.layer()));
            materialHave = new int[materials.size()];
            materialHaveAt = 0L;
        }
        if (now - materialHaveAt < INVENTORY_INTERVAL) return;
        materialHaveAt = now;
        for (int i = 0; i < materials.size(); i++)
            materialHave[i] = SchematicMaterials.countInInventory(Minecraft.getInstance().player, materials.get(i).item());
    }

    private void refreshIssues() {
        Schematic schematic = state.schematic();
        LayerStats stats = state.stats();
        if (schematic == null || stats == null) { issues = List.of(); issueKey = null; return; }
        List<Object> key = List.of(schematic, state.attentionRevision());
        if (key.equals(issueKey)) return;
        issueKey = key;
        List<SchematicAttention.Entry> result = new ArrayList<>(SchematicAttention.staticIssues(schematic, stats.paletteTotals()));
        result.addAll(state.attention());
        issues = result;
    }

    private <T extends Module> void toggleSettings(Class<T> type, String titleKey) {
        T module = ModuleManager.get(type);
        if (module == null || bounds == null) return;
        if (settingsPanel.isActive() && module.name().equals(settingsId)) {
            settingsPanel.close(); settingsId = null; return;
        }
        settingsId = module.name();
        settingsPanel.open(module.name(), tr(titleKey),
                SettingErrorView.withDiagnostics(module, module.getSettings()), false);
        settingsPanel.setAnchor(bounds.x + bounds.w - settingsPanel.panelWidth(SETTINGS_SCALE) - 12f,
                bounds.y + bounds.header + 10f);
    }

    public boolean mousePressed(float mx, float my, int button, boolean doubled) {
        mouseX = mx; mouseY = my;
        doubleClickFile = doubled;
        if (settingsPanel.isActive()) {
            int mapped = button == 0 ? InputKeys.MOUSE_LEFT : button == 1 ? InputKeys.MOUSE_RIGHT : InputKeys.MOUSE_MIDDLE;
            if (settingsPanel.mouseClicked(mx, my, mapped, SETTINGS_SCALE)) return true;
            settingsPanel.close(); settingsId = null;
        }
        if (bounds == null || !inside(mx, my, bounds.x, bounds.y, bounds.w, bounds.h)) return false;
        if (litematicaSettingsMode) {
            boolean textInput = litematicaSettings.mousePressed(mx,my,button,bounds.x+54f,bounds.y+bounds.header,
                    Math.max(1f,bounds.w-54f),Math.max(1f,bounds.h-bounds.header));
            boolean handled = runtime != null && runtime.input().pointerDown(runtime.root(),
                    new UiPointerEvent(mx, my, button));
            return textInput || handled;
        }
        if (button == 0 && !libraryMode && "properties".equals(drawerMode)) {
            float fx = bounds.x + bounds.inspectorX + 70f;
            float fy = bounds.y + bounds.header + 95f;
            for (int axis = 0; axis < 3; axis++) {
                if (inside(mx, my, fx, fy + axis * 45f,
                        Math.max(1f, bounds.inspectorW - 129f), 40f)) {
                    startOriginEdit(axis);
                    if (searchFont != null) {
                        originInput.layout(fx + 14f, Math.max(1f, bounds.inspectorW - 155f),
                                searchFont, 17f);
                        originInput.beginDrag(mx, shiftDown());
                    }
                    return true;
                }
            }
            if (originEdit >= 0) commitOriginEdit();
        }
        if (button == 0 && libraryMode && inside(mx, my, bounds.searchX,
                bounds.searchY, bounds.searchW, 44f)) {
            searchFocused = true;
            searchInput.beginDrag(mx, shiftDown());
            if (runtime != null) runtime.input().pointerDown(runtime.root(), new UiPointerEvent(mx, my, button));
            return true;
        }
        if (searchFocused) { searchFocused = false; searchInput.unfocus(); }
        if (state.hasSchematic() && editorVisible()) {
            if (button == 0 && inside(mx, my, bounds.stripX - 7f, bounds.stripY - 7f, bounds.stripW + 14f, 33f)) {
                layerDragging = true; setLayerAt(mx); return true;
            }
            if (button == 0 && bounds.viewW >= 810f && view3d && viewport.mode().exploded()
                    && inside(mx, my, bounds.gapX, bounds.gapY - 8f, bounds.gapW, 36f)) {
                gapDragging = true; setGapAt(mx); return true;
            }
            if (button == 0 && insideInset(mx, my)) { view3d = false; return true; }
            if (inside(mx, my, canvasNativeX(), canvasNativeY(), canvasNativeW(), canvasNativeH())) {
                if (doubled) resetView();
                if (view3d) viewport.beginDrag(button, mx, my, shiftDown());
                else if (button == 0) { gridDragging = true; lastGridX = mx; lastGridY = my; }
                else return false;
                return true;
            }
        }
        return runtime == null || runtime.input().pointerDown(runtime.root(), new UiPointerEvent(mx, my, button));
    }

    public boolean mouseDragged(float mx, float my) {
        mouseX = mx; mouseY = my;
        SingleLineTextInput.mouseMoved(mx);
        if (litematicaSettingsMode && litematicaSettings.mouseDragged(mx,my)) return true;
        if (layerDragging) { setLayerAt(mx); return true; }
        if (gapDragging) { setGapAt(mx); return true; }
        if (viewport.dragging()) { viewport.drag(mx, my); return true; }
        if (gridDragging) {
            gridPanX += mx - lastGridX; gridPanY += my - lastGridY;
            lastGridX = mx; lastGridY = my;
            return true;
        }
        return false;
    }

    public void mouseReleased(float mx, float my) {
        SingleLineTextInput.mouseReleased();
        if (litematicaSettingsMode) litematicaSettings.mouseReleased();
        if (bounds == null) return;
        settingsPanel.mouseReleased(mx, my, InputKeys.MOUSE_LEFT);
        if (viewport.dragging()) {
            int pickedLayer = viewport.endDrag(mx, my);
            if (pickedLayer >= 0) state.setLayer(pickedLayer);
        }
        layerDragging = false; gapDragging = false; gridDragging = false;
        if (runtime != null) runtime.input().pointerUp(runtime.root(), new UiPointerEvent(mx, my, 0));
    }

    public boolean mouseScrolled(float mx, float my, double amount) {
        if (settingsPanel.isActive() && settingsPanel.mouseScrolled(mx, my, amount)) return true;
        if (bounds == null) return false;
        if (litematicaSettingsMode)
            return runtime != null && runtime.input().scroll(runtime.root(),
                    new UiScrollEvent(mx, my, 0d, -amount));
        if (runtime != null && runtime.input().scroll(runtime.root(), new UiScrollEvent(mx, my, 0d, -amount))) return true;
        int direction = amount > 0 ? 1 : amount < 0 ? -1 : 0;
        if (direction == 0) return false;
        if (editorVisible() && inside(mx, my, canvasNativeX(), canvasNativeY(), canvasNativeW(), canvasNativeH())) {
            if (shiftDown()) state.stepLayer(direction);
            else if (view3d) viewport.zoom(direction);
            else zoomGrid(mx, my, direction);
            return true;
        }
        return false;
    }

    private void zoomGrid(float mx, float my, int direction) {
        float factor = direction > 0 ? 1.15f : 1f / 1.15f;
        float next = clamp(gridZoom * factor, .4f, 12f);
        float actualFactor = next / gridZoom;
        float cx = canvasNativeX() + canvasNativeW() * .5f;
        float cy = canvasNativeY() + canvasNativeH() * .5f;
        // Keep the world point below the mouse stationary under zoom.
        gridPanX += (mx - cx - gridPanX) * (1f - actualFactor);
        gridPanY += (my - cy - gridPanY) * (1f - actualFactor);
        gridZoom = next;
    }

    private void resetView() {
        viewport.resetCamera();
        gridZoom = 1f; gridPanX = 0f; gridPanY = 0f;
    }

    private void setLayerAt(float mx) {
        if (bounds == null || state.layerCount() <= 0) return;
        float fraction = clamp((mx - bounds.stripX) / Math.max(1f, bounds.stripW), 0f, 1f);
        state.setLayer(Math.round(fraction * (state.layerCount() - 1)));
    }
    private void setGapAt(float mx) {
        if (bounds != null) viewport.setGap(clamp((mx - bounds.gapX) / bounds.gapW, 0f, 1f) * SchematicViewport.MAX_GAP);
    }

    public boolean keyPressed(int key) {
        if (litematicaSettingsMode) {
            if (litematicaSettings.keyPressed(key)) return true;
            if (key == InputKeys.ESCAPE) { leaveLitematicaSettings(); return true; }
            return false;
        }
        if (settingsPanel.isActive() && settingsPanel.keyPressed(key, 0, 0)) return true;
        if (originEdit >= 0) {
            if (key == InputKeys.ESCAPE) { originEdit = -1; originInput.unfocus(); return true; }
            if (key == InputKeys.ENTER || key == InputKeys.KP_ENTER) { commitOriginEdit(); return true; }
            if (key == InputKeys.TAB) {
                int next = (originEdit + (shiftDown() ? 2 : 1)) % 3;
                commitOriginEdit(); startOriginEdit(next); return true;
            }
            int mask = (ctrlDown() ? InputModifierMask.SHORTCUT : 0)
                    | (shiftDown() ? InputModifierMask.SHIFT : 0);
            int shortcut = key >= InputKeys.A && key <= InputKeys.Z ? 'a' + key - InputKeys.A : key;
            originInput.keyPressed(key, shortcut, mask);
            return true;
        }
        if (searchFocused) {
            if (key == InputKeys.ESCAPE || key == InputKeys.ENTER || key == InputKeys.KP_ENTER) {
                searchFocused = false; searchInput.unfocus(); return true;
            }
            int mask = (ctrlDown() ? InputModifierMask.SHORTCUT : 0)
                    | (shiftDown() ? InputModifierMask.SHIFT : 0);
            int shortcut = key >= InputKeys.A && key <= InputKeys.Z ? 'a' + key - InputKeys.A : key;
            searchInput.keyPressed(key, shortcut, mask);
            return true;
        }
        if (ctrlDown() && key == InputKeys.F) { libraryMode = true; drawerMode = "none";
            searchFocused = true; searchInput.selectAll(); return true; }
        if (key == InputKeys.ESCAPE) {
            if (cancelCurrentAction()) return true;
            if (onClose == null) return false; // ClickGUI owns root-level close.
            requestClose();
            return true;
        }
        switch (key) {
            case InputKeys.F -> {
                if (!editorVisible() || !view3d || !state.hasSchematic()) return false;
                viewport.toggleFreeFly();
            }
            case InputKeys.W, InputKeys.A, InputKeys.S, InputKeys.D,
                    InputKeys.LEFT_SHIFT, InputKeys.RIGHT_SHIFT -> {
                if (!viewport.freeFly() || !view3d || !editorVisible()) return false;
            }
            case InputKeys.UP -> state.stepLayer(1);
            case InputKeys.DOWN -> state.stepLayer(-1);
            case InputKeys.PAGE_UP -> state.stepLayer(10);
            case InputKeys.PAGE_DOWN -> state.stepLayer(-10);
            case InputKeys.HOME -> state.setLayer(0);
            case InputKeys.END -> state.setLayer(state.layerCount() - 1);
            case InputKeys.SPACE -> {
                if (!viewport.freeFly() || !view3d || !editorVisible()) {
                    playing = !playing && state.hasSchematic(); playAccumulator = 0f;
                }
            }
            case InputKeys.ENTER, InputKeys.KP_ENTER -> SchematicActions.load(selectedPath);
            case InputKeys.R -> resetView();
            case InputKeys.T -> view3d = !view3d;
            case InputKeys.E -> viewport.cycleMode();
            default -> { return false; }
        }
        return true;
    }
    private float keyAxis(int positive, int negative) {
        float value = positive >= 0 && CombatantInput.state().isPressed(InputDevice.KEYBOARD, positive) ? 1f : 0f;
        if (negative >= 0 && CombatantInput.state().isPressed(InputDevice.KEYBOARD, negative)) value -= 1f;
        return value;
    }

    private boolean ctrlDown() {
        return CombatantInput.state().isPressed(InputDevice.KEYBOARD, InputKeys.LEFT_CONTROL)
                || CombatantInput.state().isPressed(InputDevice.KEYBOARD, InputKeys.RIGHT_CONTROL);
    }
    private boolean shiftDown() {
        return CombatantInput.state().isPressed(InputDevice.KEYBOARD, InputKeys.LEFT_SHIFT)
                || CombatantInput.state().isPressed(InputDevice.KEYBOARD, InputKeys.RIGHT_SHIFT);
    }
    public boolean charTyped(int codepoint) {
        if (litematicaSettingsMode) return litematicaSettings.charTyped((char) codepoint);
        if (settingsPanel.isActive() && settingsPanel.charTyped((char) codepoint, 0)) return true;
        if (originEdit >= 0) {
            if (!CombatantTextInputBridge.owns(originInput) && codepoint >= 32 && !Character.isISOControl(codepoint))
                originInput.insert(new String(Character.toChars(codepoint)));
            return true;
        }
        if (!searchFocused) return false;
        if (!CombatantTextInputBridge.owns(searchInput) && codepoint >= 32 && !Character.isISOControl(codepoint))
            searchInput.insert(new String(Character.toChars(codepoint)));
        return true;
    }
    public boolean cancelCurrentAction() {
        if (litematicaSettingsMode) {
            if (litematicaSettings.cancel()) return true;
            leaveLitematicaSettings();
            return true;
        }
        if (originEdit >= 0) { originEdit = -1; originInput.unfocus(); return true; }
        if (settingsPanel.isActive()) { settingsPanel.close(); settingsId = null; return true; }
        if (searchFocused) { searchFocused = false; searchInput.unfocus(); return true; }
        if (layerDragging || gapDragging || gridDragging || viewport.dragging()) {
            mouseReleased(mouseX, mouseY);
            return true;
        }
        if (!"none".equals(drawerMode)) { drawerMode = "none"; return true; }
        if (libraryMode && (state.hasSchematic() || linkedLiteIndex >= 0)) { libraryMode = false; return true; }
        return false;
    }

    public boolean isTextInputActive() {
        return litematicaSettingsMode && litematicaSettings.blocksMovement() ||
                searchFocused || originEdit >= 0 || settingsPanel.isActive();
    }

    public void removed() {
        viewport.dispose();
        leaveLitematicaSettings();
        drawerMode = "none";
        visibleDrawer = "none";
        drawerProgress = 0f;
        drawerEnterPending = false;
        localToasts.clear();
        settingsPanel.close();
        playing = false;
        searchInput.unfocus();
        originEdit = -1;
        originInput.unfocus();
        CombatantTextInputBridge.blur(searchInput);
        searchFont = null;
        searchFontGeneration = -1L;
        runtime = null;
        script.reset();
    }
    private void requestClose() { if (onClose != null) onClose.run(); }

    public static float toFixed(float screen) {
        Minecraft mc = Minecraft.getInstance();
        float scale = HudScale.scale(mc.getWindow().getWidth(), mc.getWindow().getHeight());
        return scale <= 0f ? screen : screen * mc.getWindow().getGuiScale() / scale;
    }

    private record Layout(float x, float y, float w, float h, float header,
                          float libraryX, float libraryY, float libraryW, float libraryH,
                          float viewX, float viewY, float viewW, float viewH,
                          float canvasX, float canvasY, float canvasW, float canvasH,
                          float inspectorX, float inspectorY, float inspectorW, float inspectorH,
                          float searchX, float searchY, float searchW,
                          float stripX, float stripY, float stripW, float stripH,
                          float gapX, float gapY, float gapW,
                          boolean compact) {
        static Layout compute(float x, float y, float w, float h, boolean standalone,
                              boolean browsing, float drawerProgress) {
            float head = standalone ? 54f : 52f;
            float rail = 54f;
            boolean overlayDrawer = w < 760f;
            float drawerW = overlayDrawer ? w - rail : clamp(w * .27f, 250f, 314f);
            float t = clamp(drawerProgress, 0f, 1f);
            float smooth = t * t * (3f - 2f * t);
            float workingW = Math.max(130f, w - rail - (!browsing && !overlayDrawer ? drawerW * smooth : 0f));
            float workingH = Math.max(120f, h - head);
            float drawerX = overlayDrawer ? w - (w - rail) * smooth : w - drawerW * smooth;
            float drawerWidth = overlayDrawer ? w - rail : drawerW;
            float canvasX = rail + 10f;
            float canvasY = head + 56f;
            float canvasW = Math.max(80f, workingW - 20f);
            float canvasH = Math.max(40f, workingH - 56f - 75f);
            float scrubX = rail + 154f;
            float scrubY = h - 44f;
            float scrubW = Math.max(35f, workingW - (workingW >= 470f ? 277f : 170f));
            float gapW = 95f;
            float gapX = rail + Math.max(475f, workingW - 230f);
            float gapY = head + 7f;
            float searchX = rail + 20f;
            float searchW = Math.max(220f, Math.min(460f, workingW - 46f));
            return new Layout(x, y, w, h, head,
                    rail, head, w - rail, workingH,
                    rail, head, workingW, workingH,
                    x + canvasX, y + canvasY, canvasW, canvasH,
                    drawerX, head, drawerWidth, workingH,
                    x + searchX, y + head + 89f, searchW,
                    x + scrubX, y + scrubY, scrubW, 25f,
                    x + gapX, y + gapY, gapW, overlayDrawer);
        }
        Map<String, Object> props() {
            return map("header", header, "rail", 54f,
                    "library", rect(libraryX, libraryY, libraryW, libraryH),
                    "view", rect(viewX, viewY, viewW, viewH),
                    "inspector", rect(inspectorX, inspectorY, inspectorW, inspectorH),
                    "canvas", rect(canvasX - x, canvasY - y, canvasW, canvasH),
                    "search", rect(searchX - x, searchY - y, searchW, 44f),
                    "compact", compact,
                    "strip", rect(stripX - x, stripY - y, stripW, stripH),
                    "gap", rect(gapX - x, gapY - y, gapW, 32f));
        }
        private static Map<String, Object> rect(float x, float y, float w, float h) {
            return map("x", x, "y", y, "w", w, "h", h);
        }
    }
}

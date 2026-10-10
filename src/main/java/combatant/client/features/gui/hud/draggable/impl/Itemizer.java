/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.gui.hud.draggable.impl;

import combatant.client.config.SettingDef;
import combatant.client.config.values.BooleanMapValue;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.features.gui.hud.HudAnchorX;
import combatant.client.features.gui.hud.HudAnchorY;
import combatant.client.features.gui.hud.HudElementRegister;
import combatant.client.features.gui.hud.HudRenderUtil;
import combatant.client.features.gui.hud.actions.HudActionRegistry;
import combatant.client.features.gui.hud.draggable.DraggableHudElement;
import combatant.client.features.gui.hud.draggable.DraggableHudElementRegistry;
import combatant.client.features.gui.hud.script.HudScriptLayouts;
import combatant.client.features.module.HudPhase;
import combatant.client.features.theme.Theme;
import combatant.client.features.theme.Themes;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.render.engine.math.HudScale;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.renderer.ui.runtime.core.UiRuntime;
import combatant.client.render.engine.renderer.ui.runtime.render.UiProjectionMode;
import combatant.client.render.engine.renderer.ui.runtime.render.UiRenderContext;
import combatant.client.render.engine.renderer.ui.runtime.script.CachedUiScriptRuntime;
import combatant.client.render.engine.renderer.ui.runtime.script.UiScriptColor;
import combatant.client.render.engine.renderer.ui.runtime.script.UiScriptModule;
import combatant.client.render.engine.renderer.ui.runtime.script.UiScriptModuleHandle;
import combatant.client.render.engine.text.BuiltinFontCatalog;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.util.resources.asset.UiScriptAsset;
import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@HudElementRegister(order = 200)
@UiScriptAsset("combatant:modules/hud/draggable/itemizer")
public final class Itemizer extends DraggableHudElement {
    private static final float CARD_HEIGHT = 16.0f;
    private static final float ROW_STEP = 22.0f;
    private static final float ICON_AREA = 16.0f;
    private static final float ICON_SCALE = 0.5f;
    private static final float CARD_GAP = 3.0f;
    private static final float KEY_X = 19.0f;
    private static final float KEY_FONT_SIZE = 11.0f;
    private static final float KEY_RIGHT = 7.0f;
    private static final float FADE_EPSILON = 0.013f;
    private static final float EVENT_LIFE = 1.8f;
    public static Itemizer INSTANCE;

    private final Minecraft mc = Minecraft.getInstance();
    private final UiScriptModuleHandle moduleHandle = HudScriptLayouts.handle(Itemizer.class);
    private final CachedUiScriptRuntime runtime = new CachedUiScriptRuntime(HudScriptLayouts.runtimeReporter());
    private final Map<String, Burst> bursts = new LinkedHashMap<>();
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final List<Entry> drawn = new ArrayList<>();
    private final List<ItemTask> foregroundItems = new ArrayList<>();

    private final NumberValue<Double> scale = num("itemizer_scale", "scale", 1.0, 0.5, 2.5);
    private final NumberValue<Integer> columns = num("itemizer_columns", "columns", 5, 1, 6);
    private final NumberValue<Integer> maxEntries = num("itemizer_max_entries", "max_entries", 10, 1, 12);
    private final BooleanValue showUnavailable = bool("itemizer_show_unavailable", "show_unavailable", false);
    private final BooleanValue blur = bool("itemizer_blur", "blur", true);
    private final NumberValue<Integer> bgAlpha = num("itemizer_bg_alpha", "bg_alpha", 168, 0, 255);
    private final NumberValue<Integer> themeMix = num("itemizer_theme_mix", "theme_mix", 72, 0, 100);
    private final ActionToggles visibleActions = declareActions("itemizer_visible_actions", "visible_actions");
    private final ActionToggles enabledActions = declareActions("itemizer_enabled_actions", "enabled_actions");

    private float drawnX;
    private float drawnY;
    private float drawnScale;
    private float layoutW;
    private float layoutH;
    private boolean foregroundReady;

    public Itemizer() {
        super("itemizer", "Itemizer", "hud.draggable.itemizer.description", true);
        defaultLayout(-16.0f, 48.0f, "CENTER", "CENTER");
        INSTANCE = this;
    }

    private ActionToggles declareActions(String name, String id) {
        ActionToggles value = new ActionToggles(name);
        declareSetting(value, SettingDef.group(id, value));
        return value;
    }

    public static boolean isActionEnabled(String module, String action) {
        Itemizer instance = INSTANCE;
        return instance == null || instance.enabledActions.get(module + ":" + action);
    }

    public static void showAutoEat(ItemStack stack) {
        burst("offhand:consume", stack);
    }

    public static void showAutoTotem(ItemStack stack) {
        burst("offhand:totem_priority", stack);
    }

    public static void showElytraSwap(ItemStack stack) {
        burst("elytrahelper:swap_elytra", stack);
    }

    public static void hideElytraSwap() {
        if (INSTANCE != null) INSTANCE.bursts.remove("elytrahelper:swap_elytra");
    }

    private static void burst(String id, ItemStack stack) {
        Itemizer instance = INSTANCE;
        if (instance == null || stack == null || stack.isEmpty()) return;
        instance.bursts.put(id, new Burst(stack.copy(), EVENT_LIFE));
    }

    private void syncActions() {
        Set<String> validIds = new LinkedHashSet<>(HudActionRegistry.actionIds());
        visibleActions.sync(validIds);
        enabledActions.sync(validIds);
    }

    @Override
    public void onTick() {
        syncActions();
    }

    @Override
    public void applyDefaultPosition(int screenW, int screenH) {
        x = (screenW - 140.0f) * 0.5f;
        y = screenH * 0.5f + 48.0f;
        setAnchors(HudAnchorX.FREE, HudAnchorY.FREE, x, y);
    }

    @Override public boolean usesEngineRenderer() { return true; }
    @Override public HudPhase getHudPhase() { return HudPhase.LAST; }
    @Override public int getRenderOrder() { return 82; }

    @Override
    public void renderEngine(Renderer2D renderer, TextRenderer textRenderer, GuiGraphicsExtractor graphics,
                             float tickDelta, int screenW, int screenH) {
        foregroundReady = false;
        foregroundItems.clear();
        drawn.clear();
        boolean preview = DraggableHudElementRegistry.isForceVisible();
        if (!isEnabled() && !preview) {
            width = height = 0.0f;
            entries.clear();
            return;
        }

        float dt = Math.min(0.1f, Math.max(0.0f, AnimationUtility.deltaTime()));
        boolean chat = ClientScreen.current() instanceof ChatScreen;
        List<HudActionRegistry.Snapshot> states = mc != null && mc.player != null
                ? HudActionRegistry.snapshots() : List.of();
        for (Burst burst : bursts.values()) burst.remaining = Math.max(0.0f, burst.remaining - dt);
        bursts.values().removeIf(burst -> burst.remaining <= 0.0f);

        for (Entry entry : entries.values()) entry.targetVisible = false;
        int count = 0;
        for (HudActionRegistry.Snapshot state : states) {
            if (count >= maxEntries.get()) break;
            if (!visibleActions.get(state.id()) && !chat && !preview) continue;
            if (!state.moduleEnabled() && !chat && !preview) continue;
            if (!state.available() && !state.aware() && !state.active() && !state.inProgress()
                    && !showUnavailable.get() && !chat && !preview) continue;
            if (unbound(state.bind()) && !state.aware() && !state.inProgress() && !chat && !preview) continue;
            Entry entry = entries.computeIfAbsent(state.id(), Entry::new);
            entry.snapshot = state;
            entry.targetVisible = true;
            count++;
        }
        if ((preview || chat) && count == 0 && entries.isEmpty() && mc != null && mc.player != null) {
            HudActionRegistry.Snapshot sample = new HudActionRegistry.Snapshot("preview:elytra", "Elytra", "", "R", "",
                    new ItemStack(Items.ELYTRA), true, true, false, false, 0.0f);
            Entry entry = entries.computeIfAbsent(sample.id(), Entry::new);
            entry.snapshot = sample;
            entry.targetVisible = true;
        }

        TextRenderer font = BuiltinFontCatalog.INTER_REGULAR.renderer(textRenderer != null ? textRenderer : TextRenderer.get());
        if (font == null) font = TextRenderer.get();
        font.beginSize(KEY_FONT_SIZE);
        try {
            for (Entry entry : entries.values()) {
                if (entry.snapshot != null) {
                    String bind = bindLabel(entry.snapshot.bind());
                    entry.key = bind;
                    entry.w = Math.max(KEY_X + 8.0f, KEY_X + (float) font.getWidth(bind, false) + KEY_RIGHT);
                }
            }
        } finally {
            font.end();
        }

        List<Entry> active = new ArrayList<>();
        for (Entry entry : entries.values()) {
            if (entry.targetVisible) active.add(entry);
        }
        int perRow = Math.max(1, columns.get());
        int rows = (active.size() + perRow - 1) / perRow;
        float[] rowWidths = new float[rows];
        for (int i = 0; i < active.size(); i++) {
            int row = i / perRow;
            rowWidths[row] += active.get(i).w + (i % perRow == 0 ? 0 : CARD_GAP);
        }
        float maxWidth = 0.0f;
        for (float rowWidth : rowWidths) maxWidth = Math.max(maxWidth, rowWidth);
        float targetHeight = rows == 0 ? 0.0f : (rows - 1) * ROW_STEP + CARD_HEIGHT;
        float[] nextX = new float[rows];
        for (int row = 0; row < rows; row++) nextX[row] = (maxWidth - rowWidths[row]) * 0.5f;
        for (int i = 0; i < active.size(); i++) {
            Entry entry = active.get(i);
            int row = i / perRow;
            float destX = nextX[row];
            float destY = row * ROW_STEP;
            nextX[row] += entry.w + CARD_GAP;
            if (!entry.positioned) {
                entry.x = destX;
                entry.y = destY + 5.0f;
                entry.positioned = true;
            }
            entry.x = AnimationUtility.approach(entry.x, destX, dt, 18.0f);
            entry.y = AnimationUtility.approach(entry.y, destY, dt, 18.0f);
        }
        for (Entry entry : entries.values()) {
            entry.alpha = AnimationUtility.approach(entry.alpha, entry.targetVisible ? 1.0f : 0.0f,
                    dt, entry.targetVisible ? 15.0f : 10.0f);
            boolean hot = entry.targetVisible && entry.snapshot != null
                    && (entry.snapshot.aware() || entry.snapshot.inProgress());
            entry.heat = AnimationUtility.approach(entry.heat, hot ? 1.0f : 0.0f, dt, 10.0f);
        }
        entries.values().removeIf(entry -> !entry.targetVisible && entry.alpha <= FADE_EPSILON);
        for (Entry entry : entries.values()) if (entry.snapshot != null && entry.alpha > FADE_EPSILON) drawn.add(entry);

        layoutW = maxWidth;
        layoutH = targetHeight;
        for (Entry entry : drawn) {
            layoutW = Math.max(layoutW, entry.x + entry.w);
            layoutH = Math.max(layoutH, entry.y + CARD_HEIGHT);
        }
        if (drawn.isEmpty()) {
            width = height = 0.0f;
            return;
        }
        drawnScale = scale.get().floatValue();
        drawnX = x;
        drawnY = y;
        width = layoutW * drawnScale;
        height = layoutH * drawnScale;

        float mx = 0.0f;
        float my = 0.0f;
        if (chat && mc.mouseHandler != null) {
            float uiScale = HudScale.scale(mc.getWindow().getWidth(), mc.getWindow().getHeight());
            mx = HudScale.toVirtual((float) mc.mouseHandler.xpos(), uiScale);
            my = HudScale.toVirtual((float) mc.mouseHandler.ypos(), uiScale);
        }
        int hovered = chat ? hoveredCard(mx, my) : -1;
        UiScriptModule script = ensureModule();
        if (script == null) return;
        LinkedHashMap<String, Object> props = buildProps(hovered, chat);
        TextRenderer fallback = textRenderer != null ? textRenderer : TextRenderer.get();
        UiRuntime baked = runtime.bake(moduleHandle, script, "itemizer", signature(props),
                layoutW, layoutH, fallback, 0.0f, 0.0f, layoutW, layoutH, () -> props);
        if (baked == null) return;
        baked.render(new UiRenderContext(renderer, fallback, graphics, tickDelta, UiProjectionMode.CURRENT)
                .at(drawnX, drawnY, drawnScale));
        for (int i = 0; i < drawn.size(); i++) {
            Entry entry = drawn.get(i);
            if (chat && hovered == i) continue;
            ItemStack icon = entry.snapshot.stack();
            if ((icon == null || icon.isEmpty()) && bursts.containsKey(entry.id)) icon = bursts.get(entry.id).stack;
            if (icon == null || icon.isEmpty()) continue;
            ItemStack stack = icon.copy();
            stack.setCount(1);
            foregroundItems.add(new ItemTask(stack, drawnX + (entry.x + 3.0f) * drawnScale,
                    drawnY + (entry.y + 4.0f) * drawnScale, ICON_SCALE * drawnScale,
                    entry.alpha * (visibleActions.get(entry.id) ? 1.0f : 0.35f)));
        }
        foregroundReady = true;
    }

    @Override
    public void renderEngineForeground(Renderer2D renderer, TextRenderer font, GuiGraphicsExtractor graphics,
                                       float tickDelta, int screenW, int screenH) {
        if (foregroundReady) {
            int seed = 0;
            for (ItemTask task : foregroundItems) {
                double prior = renderer.getAlpha();
                renderer.setAlpha(prior * task.alpha());
                try {
                    renderer.item(task.stack(), task.x(), task.y(), task.scale(), seed++, Renderer2D.ITEM_OVERLAY_NONE, null);
                } finally {
                    renderer.setAlpha(prior);
                }
            }
        }
        foregroundItems.clear();
        foregroundReady = false;
    }

    @Override
    public boolean isMouseOverInteractive(float mx, float my) {
        return ClientScreen.current() instanceof ChatScreen && hoveredCard(mx, my) >= 0;
    }

    @Override
    public boolean onMouseClicked(float mx, float my, int button) {
        if (!(ClientScreen.current() instanceof ChatScreen) || (button != 0 && button != 1)) return false;
        int index = hoveredCard(mx, my);
        if (index < 0) return false;
        Entry entry = drawn.get(index);
        if (entry.id.startsWith("preview:")) return false;
        if (button == 0) {
            if (!hit(mx, my, drawnX + entry.x * drawnScale,
                    drawnY + entry.y * drawnScale, ICON_AREA * drawnScale, CARD_HEIGHT * drawnScale)) return false;
            visibleActions.set(entry.id, !visibleActions.get(entry.id));
        } else if (!"offhand:totem_priority".equals(entry.id)) {
            enabledActions.set(entry.id, !enabledActions.get(entry.id));
        }
        combatant.client.config.ConfigSerializer.requestSave(this);
        return true;
    }

    private int hoveredCard(float mx, float my) {
        for (int i = 0; i < drawn.size(); i++) {
            Entry entry = drawn.get(i);
            if (hit(mx, my, drawnX + entry.x * drawnScale, drawnY + entry.y * drawnScale,
                    entry.w * drawnScale, CARD_HEIGHT * drawnScale)) return i;
        }
        return -1;
    }

    private static boolean hit(float mx, float my, float x, float y, float w, float h) {
        return mx >= x && my >= y && mx <= x + w && my <= y + h;
    }

    private UiScriptModule ensureModule() {
        if (mc == null || mc.getResourceManager() == null) return null;
        HudScriptLayouts.pollReloadCombo(mc);
        if (moduleHandle.consumeChanged()) runtime.reset();
        if (!moduleHandle.ensureLoaded(mc.getResourceManager())) {
            HudScriptLayouts.reportLoadError(moduleHandle);
            return null;
        }
        moduleHandle.consumeChanged();
        return moduleHandle.module();
    }

    private LinkedHashMap<String, Object> buildProps(int hovered, boolean chat) {
        LinkedHashMap<String, Object> props = new LinkedHashMap<>();
        props.put("width", layoutW);
        props.put("height", layoutH);
        props.put("blur", blur.get());
        props.put("chat", chat);
        props.put("hovered", hovered);
        Themes.Theme theme = Theme.theme();
        float mix = themeMix.get() / 100.0f;
        LinkedHashMap<String, Object> palette = new LinkedHashMap<>();
        palette.put("bgTop", hex(HudRenderUtil.setAlpha(HudRenderUtil.mixColor(theme.windowBg(), theme.accent(), 0.06f * mix), bgAlpha.get())));
        palette.put("bgBottom", hex(HudRenderUtil.setAlpha(theme.surface(), bgAlpha.get())));
        palette.put("stroke", hex(HudRenderUtil.setAlpha(theme.windowStroke(), 160)));
        palette.put("text", hex(theme.textPrimary()));
        palette.put("accent", hex(theme.accent()));
        props.put("palette", palette);
        ArrayList<Map<String, Object>> cards = new ArrayList<>();
        for (Entry entry : drawn) {
            HudActionRegistry.Snapshot state = entry.snapshot;
            LinkedHashMap<String, Object> card = new LinkedHashMap<>();
            card.put("key", entry.id);
            card.put("bind", entry.key);
            card.put("x", entry.x);
            card.put("y", entry.y);
            card.put("w", entry.w);
            card.put("alpha", entry.alpha);
            card.put("available", state.available());
            card.put("aware", state.aware());
            card.put("heat", entry.heat);
            card.put("active", state.active());
            card.put("inProgress", state.inProgress());
            card.put("progress", state.progress());
            card.put("cooldown", state.cooldown());
            card.put("enabled", enabledActions.get(entry.id));
            card.put("visible", visibleActions.get(entry.id));
            cards.add(card);
        }
        props.put("items", cards.toArray());
        return props;
    }

    private long signature(Map<String, Object> props) {
        long hash = CachedUiScriptRuntime.mix(0xcbf29ce484222325L, props.get("width").toString());
        hash = CachedUiScriptRuntime.mix(hash, props.get("height").toString());
        hash = CachedUiScriptRuntime.mix(hash, props.get("hovered").toString());
        hash = CachedUiScriptRuntime.mix(hash, (boolean) props.get("chat") ? 1 : 0);
        hash = CachedUiScriptRuntime.mix(hash, blur.get() ? 1 : 0);
        hash = CachedUiScriptRuntime.mix(hash, bgAlpha.get());
        hash = CachedUiScriptRuntime.mix(hash, themeMix.get());
        Themes.Theme selectedTheme = Theme.theme();
        hash = CachedUiScriptRuntime.mix(hash, selectedTheme.accent());
        hash = CachedUiScriptRuntime.mix(hash, selectedTheme.windowBg());
        hash = CachedUiScriptRuntime.mix(hash, selectedTheme.surface());
        hash = CachedUiScriptRuntime.mix(hash, selectedTheme.textPrimary());
        for (Entry entry : drawn) {
            HudActionRegistry.Snapshot state = entry.snapshot;
            hash = CachedUiScriptRuntime.mix(hash, entry.id);
            hash = CachedUiScriptRuntime.mix(hash, entry.key);
            hash = CachedUiScriptRuntime.mix(hash, Math.round(entry.x * 100.0f));
            hash = CachedUiScriptRuntime.mix(hash, Math.round(entry.y * 100.0f));
            hash = CachedUiScriptRuntime.mix(hash, Math.round(entry.alpha * 255.0f));
            hash = CachedUiScriptRuntime.mix(hash, Math.round(entry.heat * 255.0f));
            hash = CachedUiScriptRuntime.mix(hash, Math.round(state.progress() * 100.0f));
            hash = CachedUiScriptRuntime.mix(hash, Math.round(state.cooldown() * 100.0f));
            hash = CachedUiScriptRuntime.mix(hash, state.aware() ? 1 : 0);
            hash = CachedUiScriptRuntime.mix(hash, state.available() ? 1 : 0);
            hash = CachedUiScriptRuntime.mix(hash, state.active() ? 1 : 0);
            hash = CachedUiScriptRuntime.mix(hash, state.inProgress() ? 1 : 0);
            hash = CachedUiScriptRuntime.mix(hash, visibleActions.get(entry.id) ? 1 : 0);
            hash = CachedUiScriptRuntime.mix(hash, enabledActions.get(entry.id) ? 1 : 0);
        }
        return hash;
    }

    private static boolean unbound(String bind) { return bind == null || bind.isBlank() || "NONE".equalsIgnoreCase(bind); }
    private static String bindLabel(String bind) { return unbound(bind) ? "—" : bind; }
    private static String hex(int rgba) { return UiScriptColor.hex(rgba); }

    private static final class ActionToggles extends BooleanMapValue {
        private final boolean protectTotem;

        ActionToggles(String name) {
            super(name, Map.of());
            protectTotem = name.endsWith("enabled_actions");
        }

        void sync(Set<String> ids) {
            getAll().keySet().retainAll(ids);
            for (String id : ids) getAll().putIfAbsent(id, true);
            if (protectTotem && ids.contains("offhand:totem_priority")) getAll().put("offhand:totem_priority", true);
        }

        @Override public boolean get(String key) {
            return protectTotem && "offhand:totem_priority".equals(key)
                    || getAll().getOrDefault(key, true);
        }

        @Override public void set(String key, boolean value) {
            if (protectTotem && "offhand:totem_priority".equals(key)) value = true;
            super.set(key, value);
        }

        @Override public void fromJson(Object json) {
            getAll().clear();
            if (json instanceof Map<?, ?> map) {
                for (var item : map.entrySet()) {
                    if (item.getKey() instanceof String key && key.length() < 160 && key.indexOf(':') > 0
                            && item.getValue() instanceof Boolean enabled) getAll().put(key, enabled);
                }
            }
        }
    }

    private static final class Entry {
        final String id;
        HudActionRegistry.Snapshot snapshot;
        String key = "";
        float x, y, w;
        float alpha;
        float heat;
        boolean positioned;
        boolean targetVisible;
        Entry(String id) { this.id = id; }
    }

    private static final class Burst {
        final ItemStack stack;
        float remaining;
        Burst(ItemStack stack, float remaining) { this.stack = stack; this.remaining = remaining; }
    }

    private record ItemTask(ItemStack stack, float x, float y, float scale, float alpha) {}
}

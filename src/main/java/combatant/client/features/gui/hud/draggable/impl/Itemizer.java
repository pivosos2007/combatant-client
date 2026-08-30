/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.hud.draggable.impl;

import combatant.client.util.resources.asset.UiScriptAsset;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.features.gui.hud.HudAnchorX;
import combatant.client.features.gui.hud.HudAnchorY;
import combatant.client.features.gui.hud.HudElementRegister;
import combatant.client.features.gui.hud.HudRenderUtil;
import combatant.client.features.gui.hud.draggable.DraggableHudElement;
import combatant.client.features.gui.hud.draggable.DraggableHudElementRegistry;
import combatant.client.features.gui.hud.script.HudScriptLayouts;
import combatant.client.features.module.HudPhase;
import combatant.client.features.theme.Theme;
import combatant.client.features.theme.Themes;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.render.engine.math.ColorMath;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.renderer.ui.runtime.core.UiRuntime;
import combatant.client.render.engine.renderer.ui.runtime.render.UiProjectionMode;
import combatant.client.render.engine.renderer.ui.runtime.render.UiRenderContext;
import combatant.client.render.engine.renderer.ui.runtime.script.CachedUiScriptRuntime;
import combatant.client.render.engine.renderer.ui.runtime.script.UiScriptModule;
import combatant.client.render.engine.renderer.ui.runtime.script.UiScriptModuleHandle;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.render.helpers.ClipFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@HudElementRegister(order = 200)
@UiScriptAsset("combatant:api/hud/draggable/itemizer")
public final class Itemizer extends DraggableHudElement {
private static final float BASE_ICON_CARD = 24.0f;
    private static final float BASE_COMPACT_W = 72.0f;
    private static final float BASE_COMPACT_H = 24.0f;
    private static final float BASE_GAP = 4.0f;
    private static final float DEFAULT_DURATION = 1.15f;
    private static final int MAX_INTERNAL_ENTRIES = 8;

    public static Itemizer INSTANCE;

    private final Minecraft mc = Minecraft.getInstance();
    private final UiScriptModuleHandle moduleHandle = HudScriptLayouts.handle(Itemizer.class);
    private final CachedUiScriptRuntime runtime = new CachedUiScriptRuntime(HudScriptLayouts.runtimeReporter());
    private final LinkedHashMap<ItemizerEvent, Entry> entries = new LinkedHashMap<>();
    private final List<ItemRenderTask> itemTasks = new ArrayList<>(5);

    private final NumberValue<Double> scale =
            num("itemizer_scale", "scale", 1.0, 0.5, 3.0);
    private final EnumValue<ItemizerDisplayMode> displayMode =
            enumSetting("itemizer_display_mode", "display_mode", ItemizerDisplayMode.ICONS, ItemizerDisplayMode.values());
    private final EnumValue<ItemizerDirection> direction =
            enumSetting("itemizer_direction", "direction", ItemizerDirection.HORIZONTAL, ItemizerDirection.values());
    private final NumberValue<Double> duration =
            num("itemizer_duration", "duration", (double) DEFAULT_DURATION, 0.25, 3.0);
    private final NumberValue<Integer> maxEntries =
            num("itemizer_max_entries", "max_entries", 3, 1, 5);
    private final BooleanValue showAutoEat =
            bool("itemizer_auto_eat", "auto_eat", true);
    private final BooleanValue showAutoTotem =
            bool("itemizer_auto_totem", "auto_totem", true);
    private final BooleanValue showElytraSwap =
            bool("itemizer_elytra_swap", "elytra_swap", true);
    private final BooleanValue blur =
            bool("itemizer_blur", "blur", true);
    private final NumberValue<Integer> bgAlpha =
            num("itemizer_bg_alpha", "bg_alpha", 168, 0, 255);

    private float visibilityAnim;
    private float displayWidth = -1.0f;
    private float displayHeight = -1.0f;
    private boolean foregroundReady;

    public Itemizer() {
        super("itemizer", "Itemizer", true);
        INSTANCE = this;
        defaultLayout(-16.0f, 48.0f, "CENTER", "CENTER");
    }

    public static void showAutoEat(ItemStack stack) {
        show(ItemizerEvent.AUTO_EAT, stack, "Eat");
    }

    public static void showAutoTotem(ItemStack stack) {
        show(ItemizerEvent.AUTO_TOTEM, stack, "Totem");
    }

    public static void showElytraSwap(ItemStack stack) {
        show(ItemizerEvent.ELYTRA_SWAP, stack, "Swap");
    }

    public static void hideElytraSwap() {
        hide(ItemizerEvent.ELYTRA_SWAP);
    }

    private static void show(ItemizerEvent event, ItemStack stack, String label) {
        Itemizer itemizer = INSTANCE;
        if (itemizer == null || event == null || stack == null || stack.isEmpty()) return;
        itemizer.push(event, stack, label);
    }

    private static void hide(ItemizerEvent event) {
        Itemizer itemizer = INSTANCE;
        if (itemizer == null || event == null) return;
        itemizer.entries.remove(event);
    }

    @Override
    public void applyDefaultPosition(int screenW, int screenH) {
        float w = baseEntryWidth() * scale.get().floatValue();
        float h = baseEntryHeight() * scale.get().floatValue();
        this.x = (screenW - w) * 0.5f;
        this.y = screenH * 0.5f + 48.0f;
        setAnchors(HudAnchorX.FREE, HudAnchorY.FREE, this.x, this.y);
    }

    @Override
    public boolean usesEngineRenderer() {
        return true;
    }

    @Override
    public HudPhase getHudPhase() {
        return HudPhase.LAST;
    }

    @Override
    public int getRenderOrder() {
        return 82;
    }

    @Override
    public void renderEngine(Renderer2D renderer,
                             TextRenderer textRenderer,
                             GuiGraphicsExtractor ctx,
                             float tickDelta,
                             int screenW,
                             int screenH) {
        foregroundReady = false;
        itemTasks.clear();
        boolean forceVisible = DraggableHudElementRegistry.isForceVisible();
        if (!isEnabled() && !forceVisible) {
            width = 0.0f;
            height = 0.0f;
            return;
        }

        float dt = AnimationUtility.deltaTime();
        List<Entry> visible = forceVisible && entries.isEmpty()
                ? previewEntries()
                : updateEntries(dt);
        boolean showWidget = !visible.isEmpty();
        visibilityAnim = HudRenderUtil.animateVisibility(visibilityAnim, showWidget);
        if (!showWidget && visibilityAnim <= 0.0f) {
            width = 0.0f;
            height = 0.0f;
            return;
        }

        float baseScale = scale.get().floatValue();
        int count = Math.max(1, visible.size());
        float entryW = baseEntryWidth() * baseScale;
        float entryH = baseEntryHeight() * baseScale;
        float gap = BASE_GAP * baseScale;
        float targetWidth = direction.get() == ItemizerDirection.HORIZONTAL
                ? entryW * count + gap * Math.max(0, count - 1)
                : entryW;
        float targetHeight = direction.get() == ItemizerDirection.HORIZONTAL
                ? entryH
                : entryH * count + gap * Math.max(0, count - 1);

        displayWidth = HudRenderUtil.animateDimension(displayWidth, targetWidth);
        displayHeight = HudRenderUtil.animateDimension(displayHeight, targetHeight);
        width = displayWidth;
        height = displayHeight;

        float drawScale = HudRenderUtil.visibilityScale(visibilityAnim);
        float drawWidth = displayWidth * drawScale;
        float drawHeight = displayHeight * drawScale;
        if (drawWidth <= 0.0f || drawHeight <= 0.0f) return;

        float drawX = x + (displayWidth - drawWidth) * 0.5f;
        float drawY = y + (displayHeight - drawHeight) * 0.5f;
        float drawBaseScale = baseScale * drawScale;
        drawCards(renderer, visible, drawX, drawY, drawBaseScale, AnimationUtility.clamp01(visibilityAnim));

        UiScriptModule module = ensureModule();
        if (module == null) return;

        TextRenderer fallback = textRenderer != null ? textRenderer : TextRenderer.get();
        LinkedHashMap<String, Object> props = props(visible, drawWidth, drawHeight, drawBaseScale, drawScale);
        long treeSignature = signature(props);
        long layoutSignature = 0xcbf29ce484222325L;
        layoutSignature = CachedUiScriptRuntime.mix(layoutSignature, drawX);
        layoutSignature = CachedUiScriptRuntime.mix(layoutSignature, drawY);
        layoutSignature = CachedUiScriptRuntime.mix(layoutSignature, drawWidth);
        layoutSignature = CachedUiScriptRuntime.mix(layoutSignature, drawHeight);

        UiRuntime baked = runtime.bake(
                moduleHandle,
                module,
                "itemizer",
                treeSignature,
                layoutSignature,
                drawWidth,
                drawHeight,
                fallback,
                drawX,
                drawY,
                drawWidth,
                drawHeight,
                () -> props
        );
        if (baked == null) return;
        baked.render(new UiRenderContext(renderer, fallback, ctx, tickDelta, UiProjectionMode.CURRENT));
        foregroundReady = true;
    }

    @Override
    public void renderEngineForeground(Renderer2D renderer,
                                       TextRenderer textRenderer,
                                       GuiGraphicsExtractor ctx,
                                       float tickDelta,
                                       int screenW,
                                       int screenH) {
        if (!foregroundReady || itemTasks.isEmpty()) {
            itemTasks.clear();
            return;
        }
        int seed = 0;
        for (ItemRenderTask task : itemTasks) {
            drawItem(renderer, task.stack(), task.x(), task.y(), task.scale(), seed++, task.overlayFlags(), task.alpha());
        }
        itemTasks.clear();
        foregroundReady = false;
    }

    private void push(ItemizerEvent event, ItemStack stack, String label) {
        if (!accepts(event)) return;
        Entry entry = entries.remove(event);
        if (entry == null) {
            entry = new Entry(event);
        }
        entry.stack = stack.copy();
        entry.label = label != null && !label.isBlank() ? label : event.fallbackLabel;
        entry.life = Math.max(0.1f, duration.get().floatValue());
        entry.totalLife = entry.life;
        entry.punch = 1.0f;
        entries.put(event, entry);
        trimInternalEntries();
    }

    private boolean accepts(ItemizerEvent event) {
        return switch (event) {
            case AUTO_EAT -> showAutoEat.get();
            case AUTO_TOTEM -> showAutoTotem.get();
            case ELYTRA_SWAP -> showElytraSwap.get();
        };
    }

    private void trimInternalEntries() {
        while (entries.size() > MAX_INTERNAL_ENTRIES) {
            ItemizerEvent first = entries.keySet().iterator().next();
            entries.remove(first);
        }
    }

    private List<Entry> updateEntries(float dt) {
        List<ItemizerEvent> remove = new ArrayList<>();
        List<Entry> out = new ArrayList<>();
        for (Map.Entry<ItemizerEvent, Entry> mapEntry : entries.entrySet()) {
            Entry entry = mapEntry.getValue();
            entry.life = Math.max(0.0f, entry.life - dt);
            entry.punch = AnimationUtility.approach(entry.punch, 0.0f, dt, 8.0f);
            boolean alive = entry.life > 0.0f;
            entry.anim = AnimationUtility.approach(entry.anim, alive ? 1.0f : 0.0f, dt, 13.0f);
            entry.anim = AnimationUtility.snap(entry.anim, alive ? 1.0f : 0.0f, 0.002f);
            if (!alive && entry.anim <= 0.0f) {
                remove.add(mapEntry.getKey());
                continue;
            }
            if (out.size() < maxEntries.get()) {
                out.add(entry);
            }
        }
        for (ItemizerEvent event : remove) {
            entries.remove(event);
        }
        return out;
    }

    private List<Entry> previewEntries() {
        Entry eat = new Entry(ItemizerEvent.AUTO_EAT);
        eat.stack = new ItemStack(Items.COOKED_BEEF);
        eat.label = "Eat";
        eat.life = 1.0f;
        eat.totalLife = 1.0f;
        eat.anim = 1.0f;

        Entry totem = new Entry(ItemizerEvent.AUTO_TOTEM);
        totem.stack = new ItemStack(Items.TOTEM_OF_UNDYING);
        totem.label = "Totem";
        totem.life = 1.0f;
        totem.totalLife = 1.0f;
        totem.anim = 1.0f;

        Entry swap = new Entry(ItemizerEvent.ELYTRA_SWAP);
        swap.stack = new ItemStack(Items.ELYTRA);
        swap.label = "Swap";
        swap.life = 1.0f;
        swap.totalLife = 1.0f;
        swap.anim = 1.0f;

        List<Entry> list = new ArrayList<>();
        list.add(eat);
        list.add(totem);
        list.add(swap);
        return list.subList(0, Math.min(maxEntries.get(), list.size()));
    }

    private UiScriptModule ensureModule() {
        if (mc == null || mc.getResourceManager() == null) return null;
        HudScriptLayouts.pollReloadCombo(mc);
        if (moduleHandle.consumeChanged()) {
            runtime.reset();
        }
        if (!moduleHandle.ensureLoaded(mc.getResourceManager())) {
            HudScriptLayouts.reportLoadError(moduleHandle);
            return null;
        }
        moduleHandle.consumeChanged();
        return moduleHandle.module();
    }

    private LinkedHashMap<String, Object> props(List<Entry> visible,
                                                float width,
                                                float height,
                                                float baseScale,
                                                float drawScale) {
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        out.put("id", "itemizer");
        out.put("width", width);
        out.put("height", height);
        out.put("baseScale", baseScale);
        out.put("drawScale", drawScale);
        out.put("mode", displayMode.get().id());
        out.put("direction", direction.get().id());
        out.put("entryWidth", baseEntryWidth() * baseScale);
        out.put("entryHeight", baseEntryHeight() * baseScale);
        out.put("gap", BASE_GAP * baseScale);
        out.put("blur", blur.get());
        out.put("palette", paletteProps());
        List<LinkedHashMap<String, Object>> itemProps = new ArrayList<>();
        for (Entry entry : visible) {
            itemProps.add(entryProps(entry));
        }
        out.put("items", itemProps.toArray());
        return out;
    }

    private LinkedHashMap<String, Object> paletteProps() {
        Themes.Theme t = Theme.theme();
        int alpha = bgAlpha.get();
        int bg1 = HudRenderUtil.setAlpha(HudRenderUtil.mixColor(t.windowBg(), t.surface(), 0.24f), alpha);
        int bg2 = HudRenderUtil.setAlpha(HudRenderUtil.mixColor(t.surface(), 0xFF000000, 0.22f), Math.min(255, alpha + 18));
        int stroke = HudRenderUtil.setAlpha(HudRenderUtil.mixColor(t.windowStroke(), t.accentSoft(), 0.22f), 176);
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        out.put("bg1", hex(bg1));
        out.put("bg2", hex(bg2));
        out.put("stroke", hex(stroke));
        out.put("text", hex(t.textPrimary()));
        out.put("muted", hex(t.textMuted()));
        out.put("accent", hex(t.accent()));
        out.put("glow", hex(HudRenderUtil.setAlpha(t.accent(), 155)));
        out.put("blurAlpha", Math.min(1.0f, alpha / 255.0f));
        return out;
    }

    private LinkedHashMap<String, Object> entryProps(Entry entry) {
        ItemStack stack = entry.stack != null ? entry.stack : ItemStack.EMPTY;
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        out.put("key", entry.event.id());
        out.put("kind", entry.event.id());
        out.put("label", entry.label);
        out.put("stack", stack.copy());
        out.put("item", stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        out.put("count", stack.isEmpty() ? 1 : stack.getCount());
        out.put("damage", stack.isEmpty() ? 0 : stack.getDamageValue());
        out.put("maxDamage", stack.isEmpty() ? 0 : stack.getMaxDamage());
        out.put("alpha", AnimationUtility.clamp01(entry.anim));
        out.put("life", entry.totalLife <= 0.0f ? 1.0f : AnimationUtility.clamp01(entry.life / entry.totalLife));
        out.put("pulse", AnimationUtility.clamp01(entry.punch));
        out.put("accent", hex(entryAccent(entry.event)));
        return out;
    }

    private int entryAccent(ItemizerEvent event) {
        Themes.Theme t = Theme.theme();
        int target = switch (event) {
            case AUTO_EAT -> 0xFF74E083;
            case AUTO_TOTEM -> 0xFFFFD15C;
            case ELYTRA_SWAP -> 0xFF77D7FF;
        };
        return ColorMath.colorWithAlpha(HudRenderUtil.mixColor(t.accent(), target, 0.72f), 255);
    }

    private void drawCards(Renderer2D renderer,
                           List<Entry> visible,
                           float x,
                           float y,
                           float scale,
                           float globalAlpha) {
        if (renderer == null || visible == null || visible.isEmpty()) return;
        float entryW = baseEntryWidth() * scale;
        float entryH = baseEntryHeight() * scale;
        float gap = BASE_GAP * scale;
        for (int i = 0; i < visible.size(); i++) {
            Entry entry = visible.get(i);
            float cardX = x;
            float cardY = y;
            if (direction.get() == ItemizerDirection.HORIZONTAL) {
                cardX += i * (entryW + gap);
            } else {
                cardY += i * (entryH + gap);
            }

            float alpha = AnimationUtility.clamp01(globalAlpha * entry.anim);
            if (alpha <= 0.001f) continue;
            float radius = Math.max(3.0f * scale, Math.min(entryW, entryH) * 0.24f);
            drawGlassCard(renderer, cardX, cardY, entryW, entryH, radius, scale, alpha);
            drawClippedTimeStrip(renderer, entry, cardX, cardY, entryW, entryH, radius, scale, alpha);
            queueItem(entry, cardX, cardY, entryW, entryH, scale, alpha);
        }
    }

    private void drawGlassCard(Renderer2D renderer,
                               float x,
                               float y,
                               float width,
                               float height,
                               float radius,
                               float scale,
                               float alpha) {
        if (width <= 0.0f || height <= 0.0f || alpha <= 0.001f) return;
        float blurStrength = blur.get() ? (bgAlpha.get() / 255.0f) * alpha : 0.0f;
        HudRenderUtil.drawLiquidGlass(x, y, width, height, radius, Math.max(0.001f, scale), true, blurStrength, alpha);
        int veil = HudRenderUtil.glassSmallBackground(alpha);
        renderer.roundedRectCorners(x, y, width, height, radius, radius, radius, radius, 1.0f, veil);
    }

    private void drawClippedTimeStrip(Renderer2D renderer,
                                      Entry entry,
                                      float x,
                                      float y,
                                      float width,
                                      float height,
                                      float cardRadius,
                                      float scale,
                                      float alpha) {
        boolean clipped = ClipFunction.pushRoundedRect(x, y, width, height, cardRadius);
        if (!clipped) return;
        try {
            drawTimeStrip(renderer, entry, x, y, width, height, cardRadius, scale, alpha);
        } finally {
            ClipFunction.pop();
        }
    }

    private void drawTimeStrip(Renderer2D renderer,
                               Entry entry,
                               float x,
                               float y,
                               float width,
                               float height,
                               float cardRadius,
                               float scale,
                               float alpha) {
        if (renderer == null || entry == null || width <= 0.0f || height <= 0.0f || alpha <= 0.001f) return;
        float life = entry.totalLife <= 0.0f ? 1.0f : AnimationUtility.clamp01(entry.life / entry.totalLife);
        if (life <= 0.001f) return;

        float stripH = Math.max(1.45f * scale, Math.min(3.0f * scale, height * 0.13f));
        float bleed = Math.max(0.65f, 0.45f * scale);
        float stripDrawH = stripH + bleed;
        float stripY = y + height - stripH;
        float bottomR = Math.min(cardRadius, stripDrawH);
        int accent = entryAccent(entry.event);
        int trackTop = HudRenderUtil.scaleAlpha(HudRenderUtil.mixColor(0xFF07090D, accent, 0.08f), 0.30f * alpha);
        int trackBottom = HudRenderUtil.scaleAlpha(HudRenderUtil.mixColor(0xFF020305, accent, 0.18f), 0.48f * alpha);
        renderer.roundedRectCornersQuad(
                x,
                stripY,
                width,
                stripDrawH,
                0.0f,
                0.0f,
                bottomR,
                bottomR,
                0.0f,
                trackTop,
                trackTop,
                trackBottom,
                trackBottom
        );

        float fillW = Math.max(0.0f, width * life);
        if (fillW <= 0.35f) return;
        float fillRLeft = Math.min(bottomR, fillW * 0.5f);
        float fillRRight = life >= 0.985f ? bottomR : Math.min(stripDrawH * 0.5f, fillW * 0.5f);
        int fillTopLeft = HudRenderUtil.scaleAlpha(HudRenderUtil.mixColor(accent, 0xFFFFFFFF, 0.28f), 0.92f * alpha);
        int fillTopRight = HudRenderUtil.scaleAlpha(HudRenderUtil.mixColor(accent, 0xFFFFFFFF, 0.10f), 0.84f * alpha);
        int fillBottomRight = HudRenderUtil.scaleAlpha(HudRenderUtil.mixColor(accent, 0xFF05070A, 0.22f), 0.78f * alpha);
        int fillBottomLeft = HudRenderUtil.scaleAlpha(HudRenderUtil.mixColor(accent, 0xFFFFFFFF, 0.16f), 0.88f * alpha);
        renderer.roundedRectCornersQuad(
                x,
                stripY,
                fillW,
                stripDrawH,
                0.0f,
                life >= 0.985f ? 0.0f : fillRRight,
                fillRRight,
                fillRLeft,
                0.0f,
                fillTopLeft,
                fillTopRight,
                fillBottomRight,
                fillBottomLeft
        );
    }

    private void queueItem(Entry entry,
                           float cardX,
                           float cardY,
                           float cardW,
                           float cardH,
                           float scale,
                           float alpha) {
        if (entry == null || entry.stack == null || entry.stack.isEmpty()) return;
        boolean compact = displayMode.get() == ItemizerDisplayMode.COMPACT;
        float iconSize = compact
                ? Math.min(16.0f * scale, cardH - 7.0f * scale)
                : Math.min(Math.min(16.0f * scale, cardW - 8.0f * scale), cardH - 8.0f * scale);
        if (iconSize <= 0.0f) return;
        float iconX = compact ? cardX + 4.5f * scale : cardX + (cardW - iconSize) * 0.5f;
        float iconY = cardY + (cardH - iconSize) * 0.5f - 0.5f * scale;
        int overlayFlags = compact ? Renderer2D.ITEM_OVERLAY_ALL : Renderer2D.ITEM_OVERLAY_NONE;
        itemTasks.add(new ItemRenderTask(entry.stack.copy(), iconX, iconY, Math.max(0.05f, iconSize / 16.0f), overlayFlags, alpha));
    }

    private void drawItem(Renderer2D renderer,
                          ItemStack stack,
                          float x,
                          float y,
                          float itemScale,
                          int seed,
                          int overlayFlags,
                          float alpha) {
        if (renderer == null || stack == null || stack.isEmpty() || alpha <= 0.001f) return;
        double previousAlpha = renderer.getAlpha();
        renderer.setAlpha(previousAlpha * AnimationUtility.clamp01(alpha));
        try {
            renderer.item(stack, x, y, itemScale, seed, overlayFlags, null);
        } finally {
            renderer.setAlpha(previousAlpha);
        }
    }

    private long signature(Map<String, Object> props) {
        long h = 0xcbf29ce484222325L;
        h = CachedUiScriptRuntime.mix(h, displayMode.get().id());
        h = CachedUiScriptRuntime.mix(h, direction.get().id());
        h = CachedUiScriptRuntime.mix(h, width);
        h = CachedUiScriptRuntime.mix(h, height);
        h = CachedUiScriptRuntime.mix(h, bgAlpha.get());
        h = CachedUiScriptRuntime.mix(h, blur.get());
        Object itemsValue = props.get("items");
        Object[] items = itemsValue instanceof Object[] arr ? arr : new Object[0];
        h = CachedUiScriptRuntime.mix(h, items.length);
        for (Object itemValue : items) {
            if (!(itemValue instanceof Map<?, ?> item)) continue;
            h = CachedUiScriptRuntime.mix(h, string(item.get("key")));
            h = CachedUiScriptRuntime.mix(h, string(item.get("label")));
            h = CachedUiScriptRuntime.mix(h, string(item.get("item")));
            h = CachedUiScriptRuntime.mix(h, intValue(item.get("count")));
            h = CachedUiScriptRuntime.mix(h, Math.round(floatValue(item.get("alpha")) * 1000.0f));
            h = CachedUiScriptRuntime.mix(h, Math.round(floatValue(item.get("life")) * 1000.0f));
            h = CachedUiScriptRuntime.mix(h, Math.round(floatValue(item.get("pulse")) * 1000.0f));
        }
        return h;
    }

    private float baseEntryWidth() {
        return displayMode.get() == ItemizerDisplayMode.COMPACT ? BASE_COMPACT_W : BASE_ICON_CARD;
    }

    private float baseEntryHeight() {
        return displayMode.get() == ItemizerDisplayMode.COMPACT ? BASE_COMPACT_H : BASE_ICON_CARD;
    }

    private static String hex(int argb) {
        return String.format("#%08X", argb);
    }

    private static String string(Object value) {
        return value instanceof String s ? s : "";
    }

    private static int intValue(Object value) {
        return value instanceof Number n ? n.intValue() : 0;
    }

    private static float floatValue(Object value) {
        return value instanceof Number n ? n.floatValue() : 0.0f;
    }

    public enum ItemizerEvent implements EnumValue.IdProvider {
        AUTO_EAT("auto_eat", "Eat"),
        AUTO_TOTEM("auto_totem", "Totem"),
        ELYTRA_SWAP("elytra_swap", "Swap");

        private final String id;
        private final String fallbackLabel;

        ItemizerEvent(String id, String fallbackLabel) {
            this.id = id;
            this.fallbackLabel = fallbackLabel;
        }

        @Override
        public String id() {
            return id;
        }
    }

    private enum ItemizerDisplayMode implements EnumValue.IdProvider {
        ICONS("icons"),
        COMPACT("compact");

        private final String id;

        ItemizerDisplayMode(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }
    }

    private enum ItemizerDirection implements EnumValue.IdProvider {
        HORIZONTAL("horizontal"),
        VERTICAL("vertical");

        private final String id;

        ItemizerDirection(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }
    }

    private static final class Entry {
        private final ItemizerEvent event;
        private ItemStack stack = ItemStack.EMPTY;
        private String label;
        private float life;
        private float totalLife;
        private float anim;
        private float punch;

        private Entry(ItemizerEvent event) {
            this.event = event;
            this.label = event.fallbackLabel;
        }
    }

    private record ItemRenderTask(ItemStack stack, float x, float y, float scale, int overlayFlags, float alpha) {
    }
}

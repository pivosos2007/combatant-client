/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.hud.nondraggable.impl;


import combatant.client.features.theme.Theme;
import combatant.client.config.values.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import org.joml.Vector2ic;
import combatant.client.config.SettingDef;
import combatant.client.features.gui.hud.AbstractHudElement;
import combatant.client.features.gui.hud.HudElementRegister;
import combatant.client.features.gui.hud.HudRenderUtil;
import combatant.client.features.gui.hud.script.HudScriptLayouts;
import combatant.client.features.theme.Themes;
import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.core.ViewportContext;
import combatant.client.render.engine.profiler.ProfilerPhase;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.renderer.ui.runtime.core.UiRuntime;
import combatant.client.render.engine.renderer.ui.runtime.render.UiProjectionMode;
import combatant.client.render.engine.renderer.ui.runtime.render.UiRenderContext;
import combatant.client.render.engine.renderer.ui.runtime.script.CachedUiScriptRuntime;
import combatant.client.render.engine.renderer.ui.runtime.script.UiScriptModule;
import combatant.client.render.engine.renderer.ui.runtime.script.UiScriptModuleHandle;
import combatant.client.render.engine.text.FontInfo;
import combatant.client.render.engine.text.Fonts;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.runtime.RuntimeGate;
import combatant.client.util.input.KeyManager;
import combatant.client.util.item.TopEnchantUtil;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@HudElementRegister(order = 20)
public final class BetterTooltips extends AbstractHudElement {

    public static final BetterTooltips INSTANCE = new BetterTooltips();
    public static final String GROUP_TOOLTIP_GUI = "vanilla_tooltip_gui";
    public static final String GROUP_TOOLTIP_ITEMS = "vanilla_tooltip_items";
    public static final String GROUP_SHULKER_PREVIEW = "vanilla_shulker_preview";
    private static final String TOOLTIP_PANEL_LAYOUT_ID = "combatant:api/hud/static/tooltip_panel";
    private static final int TOOLTIP_PAD_X = 4;
    private static final int TOOLTIP_PAD_Y = 3;
    private static final float TOOLTIP_STROKE_WIDTH = 0.55f;
    private static final float TOOLTIP_GLOW_SIZE = 3.75f;
    private static final int TOOLTIP_BG_ALPHA = 0xEE;
    private static final int TOOLTIP_STROKE_ALPHA = 0xDC;
    private static final int TOOLTIP_GLOW_ALPHA = 0x5C;
    private static final float TOOLTIP_TEXT_SCALE = 0.35f;
    private static final int TOOLTIP_LINE_GAP = 0;
    private static final int TOOLTIP_PREVIEW_GAP = 3;
    private static final int TOOLTIP_TITLE_GAP = 2;
    private static final int SHULKER_HINT_COLOR = 0xFFFFAA00;
    private static final float SHULKER_BG_RADIUS = 1.5f;
    private static final float SHULKER_BG_SOFTNESS = 0.45f;
    private static final int SHULKER_SLOT_BG = 0xFF1A1C20;
    private static final int SHULKER_SLOT_BORDER = 0x8A34383E;
    private static final float SHULKER_SLOT_RADIUS = 2.0f;
    private static final float SHULKER_SLOT_SOFTNESS = 0.65f;
    private static final float SHULKER_SLOT_GAP = 2.0f;
    private static final float SHULKER_PADDING = 4.0f;
    private static List<TooltipLine> TOOLTIP_LINES;
    private static ClientTooltipPositioner TOOLTIP_POSITIONER;
    private static int TOOLTIP_MOUSE_X;
    private static int TOOLTIP_MOUSE_Y;
    private static ItemStack TOOLTIP_STACK;
    private static ItemStack LAST_TOOLTIP_STACK;
    private static GuiGraphicsExtractor TOOLTIP_CTX;
    private static float TOOLTIP_SCALE_OVERRIDE = 1.0f;
    private static PreviewLayout SHULKER_CACHE;
    private static int SHULKER_CACHE_SIGNATURE;
    private static float SHULKER_CACHE_PAD;
    private static float SHULKER_CACHE_SLOT_SIZE;
    private static float SHULKER_CACHE_SLOT_GAP;
    private static boolean SHULKER_CACHE_HAS_ITEMS;
    private final BooleanValue itemTooltipEnabled = bool("item_tooltip", true);
    private final BooleanValue shulkerPreviewEnabled = bool("shulker_preview", true);
    private final BooleanValue itemInfoColorize = bool("item_info_colorize", true);
    private final ItemIdSetValue topIgnore = registerTopIgnore();
    private final NumberValue<Float> shulkerPreviewSlotSize =
            visibleWhen(num("shulker_slot_size", 18.0f, 12.0f, 24.0f), this::isShulkerPreviewEnabled);
    private final KeyBindValue shulkerPreviewHold =
            visibleWhen(bind("shulker_preview_hold", "LEFT_SHIFT", BindMode.HOLD), this::isShulkerPreviewEnabled);
    private final UiScriptModuleHandle tooltipPanelHandle = HudScriptLayouts.handle(TOOLTIP_PANEL_LAYOUT_ID);
    private final CachedUiScriptRuntime tooltipRuntime = new CachedUiScriptRuntime(HudScriptLayouts.runtimeReporter());
    private BetterTooltips() {
        super("vanilla_tooltips", "Tooltips", true);
    }

    public static BetterTooltips get() {
        return INSTANCE;
    }

    public static boolean isGroupId(String id) {
        return SettingsGroup.fromId(id) != null;
    }

    public static void beginTooltipFrame() {
        TOOLTIP_LINES = null;
        TOOLTIP_POSITIONER = null;
        TOOLTIP_STACK = null;
        TOOLTIP_CTX = null;
        LAST_TOOLTIP_STACK = null;
        TOOLTIP_SCALE_OVERRIDE = 1.0f;
    }

    public static boolean hasTooltip() {
        return TOOLTIP_LINES != null && !TOOLTIP_LINES.isEmpty();
    }

    public static void captureTooltipOrdered(List<? extends FormattedCharSequence> lines,
                                             ClientTooltipPositioner positioner,
                                             int mouseX, int mouseY) {
        if (lines == null || lines.isEmpty()) return;
        TOOLTIP_LINES = convertOrdered(lines);
        TOOLTIP_POSITIONER = positioner != null ? positioner : DefaultTooltipPositioner.INSTANCE;
        TOOLTIP_MOUSE_X = mouseX;
        TOOLTIP_MOUSE_Y = mouseY;
        TOOLTIP_STACK = ItemStack.EMPTY;
    }

    public static void captureItemTooltipOrdered(List<? extends FormattedCharSequence> lines,
                                                 ClientTooltipPositioner positioner,
                                                 int mouseX, int mouseY,
                                                 ItemStack stack,
                                                 GuiGraphicsExtractor ctx) {
        if (lines == null || lines.isEmpty()) return;
        TOOLTIP_LINES = convertOrdered(lines);
        TOOLTIP_POSITIONER = positioner != null ? positioner : DefaultTooltipPositioner.INSTANCE;
        TOOLTIP_MOUSE_X = mouseX;
        TOOLTIP_MOUSE_Y = mouseY;
        TOOLTIP_STACK = stack;
        TOOLTIP_CTX = ctx;
    }

    public static void setDrawContext(GuiGraphicsExtractor ctx) {
        TOOLTIP_CTX = ctx;
    }

    public static void setLastTooltipStack(ItemStack stack) {
        LAST_TOOLTIP_STACK = stack;
    }

    public static ItemStack consumeLastTooltipStack() {
        ItemStack stack = LAST_TOOLTIP_STACK;
        LAST_TOOLTIP_STACK = null;
        return stack;
    }

    public static void renderTooltip() {
        if (!hasTooltip()) return;
        renderTooltipInternal(
                TOOLTIP_LINES,
                TOOLTIP_POSITIONER,
                TOOLTIP_MOUSE_X,
                TOOLTIP_MOUSE_Y
        );
    }

    public static void setTooltipScaleOverride(float scale) {
        TOOLTIP_SCALE_OVERRIDE = Math.max(0.5f, Math.min(1.5f, scale));
    }

    public static void resetTooltipScaleOverride() {
        TOOLTIP_SCALE_OVERRIDE = 1.0f;
    }

    public static void renderTooltipWithContext(GuiGraphicsExtractor ctx) {
        if (ctx != null && TOOLTIP_CTX == null) {
            TOOLTIP_CTX = ctx;
        }
        renderTooltip();
    }

    private static void renderTooltipInternal(List<TooltipLine> lines,
                                              ClientTooltipPositioner positioner,
                                              int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;

        TextRenderer tr = Fonts.renderer("Iosevka", FontInfo.Type.Regular, TextRenderer.get());
        if (tr == null) return;

        PreviewLayout preview = buildShulkerPreviewLayout();
        boolean previewOpen = preview != null;
        List<TooltipLine> finalLines = prepareTooltipLines(lines, previewOpen);

        float textScale = TOOLTIP_TEXT_SCALE * TOOLTIP_SCALE_OVERRIDE;
        tr.begin(textScale, false, false);
        int maxWidth = 0;
        for (TooltipLine line : finalLines) {
            maxWidth = Math.max(maxWidth, (int) Math.ceil(tr.getWidth(line.text, false)));
        }
        int lineHeight = (int) Math.ceil(tr.getHeight(false));
        tr.end();

        if (finalLines.isEmpty()) {
            finalLines = List.of(new TooltipLine(" ", Theme.theme().textPrimary()));
        }

        int textHeight = finalLines.size() * lineHeight;
        if (finalLines.size() > 1) {
            textHeight += TOOLTIP_TITLE_GAP;
            textHeight += Math.max(0, finalLines.size() - 2) * TOOLTIP_LINE_GAP;
        }
        int previewW = preview != null ? preview.width : 0;
        int previewH = preview != null ? preview.height : 0;
        int contentWidth = Math.max(maxWidth, previewW);
        int totalW = contentWidth + TOOLTIP_PAD_X * 2;
        int totalH = TOOLTIP_PAD_Y * 2 + textHeight;
        if (preview != null) {
            totalH += TOOLTIP_PREVIEW_GAP + previewH;
        }

        int x = mouseX;
        int y = mouseY;
        if (positioner != null) {
            Vector2ic pos = positioner.positionTooltip(
                    mc.getWindow().getGuiScaledWidth(),
                    mc.getWindow().getGuiScaledHeight(),
                    mouseX, mouseY,
                    totalW, totalH
            );
            x = pos.x();
            y = pos.y();
        }

        Themes.Theme theme = Theme.theme();
        int bg = HudRenderUtil.setAlpha(theme.windowBg(), TOOLTIP_BG_ALPHA);
        int edgeBase = HudRenderUtil.mixColor(theme.accentSoft(), theme.accent(), 0.34f);
        int glowColor = HudRenderUtil.setAlpha(edgeBase, TOOLTIP_GLOW_ALPHA);
        int strokeTopLeft = HudRenderUtil.setAlpha(HudRenderUtil.mixColor(theme.accentSoft(), theme.accent(), 0.14f), TOOLTIP_STROKE_ALPHA);
        int strokeTopRight = HudRenderUtil.setAlpha(HudRenderUtil.mixColor(theme.accentSoft(), theme.accent(), 0.82f), TOOLTIP_STROKE_ALPHA);
        int strokeBottomRight = HudRenderUtil.setAlpha(HudRenderUtil.mixColor(theme.accentSoft(), theme.accent(), 0.58f), TOOLTIP_STROKE_ALPHA);
        int strokeBottomLeft = HudRenderUtil.setAlpha(HudRenderUtil.mixColor(theme.accentSoft(), theme.accent(), 0.24f), TOOLTIP_STROKE_ALPHA);

        ViewportContext.beginScaled(TOOLTIP_CTX);
        boolean panelRendered = INSTANCE.renderTooltipPanelScripted(
                mc,
                Renderer2D.COLOR,
                tr,
                x,
                y,
                totalW,
                totalH,
                bg,
                strokeTopLeft,
                strokeTopRight,
                strokeBottomRight,
                strokeBottomLeft,
                glowColor,
                TOOLTIP_STROKE_WIDTH,
                TOOLTIP_GLOW_SIZE,
                1.0f
        );
        if (!panelRendered) {
            drawTooltipPanelFallback(x, y, totalW, totalH, bg,
                    strokeTopLeft, strokeTopRight, strokeBottomRight, strokeBottomLeft,
                    glowColor);
        }

        int textX = x + TOOLTIP_PAD_X;
        int textY = y + TOOLTIP_PAD_Y;
        tr.begin(textScale, false, false);
        for (int i = 0; i < finalLines.size(); i++) {
            TooltipLine line = finalLines.get(i);
            tr.render(line.text, textX, textY, new RenderColor(line.color), true);
            textY += lineHeight;
            if (i == 0 && finalLines.size() > 1) {
                textY += TOOLTIP_TITLE_GAP;
            } else if (i + 1 < finalLines.size()) {
                textY += TOOLTIP_LINE_GAP;
            }
        }
        tr.end();
        ViewportContext.end(TOOLTIP_CTX);

        if (preview != null) {
            int contentW = totalW - TOOLTIP_PAD_X * 2;
            int previewX = x + TOOLTIP_PAD_X + Math.max(0, (contentW - preview.width) / 2);
            int previewY = y + TOOLTIP_PAD_Y + textHeight + TOOLTIP_PREVIEW_GAP;
            renderShulkerPreview(preview, previewX, previewY);
        }
    }

    private static String hex(int argb) {
        return String.format("#%08X", argb);
    }

    private static void drawTooltipPanelFallback(float x, float y, float width, float height,
                                                 int bg,
                                                 int strokeTopLeft,
                                                 int strokeTopRight,
                                                 int strokeBottomRight,
                                                 int strokeBottomLeft,
                                                 int glow) {
        float scale = ViewportContext.getScaleFactor();
        float xb = x * scale;
        float yb = y * scale;
        float wb = width * scale;
        float hb = height * scale;
        float strokeWidth = TOOLTIP_STROKE_WIDTH * scale;
        ViewportContext.unscaledProjection();
        Renderer2D.COLOR.roundedRectGlow(
                xb, yb, wb, hb,
                0.0f,
                0.0f,
                TOOLTIP_GLOW_SIZE * scale,
                glow
        );
        Renderer2D.COLOR.quad(xb, yb, wb, hb, bg);
        drawQuadStrokeGradient(Renderer2D.COLOR, xb, yb, wb, hb, strokeWidth,
                strokeTopLeft, strokeTopRight, strokeBottomRight, strokeBottomLeft);
        ViewportContext.scaledProjection();
    }

    private static void drawQuadStrokeGradient(Renderer2D renderer,
                                               float x, float y, float width, float height,
                                               float thickness,
                                               int cTopLeft, int cTopRight,
                                               int cBottomRight, int cBottomLeft) {
        if (renderer == null || thickness <= 0.0f || width <= 0.0f || height <= 0.0f) return;
        float t = Math.min(thickness, Math.min(width, height) * 0.5f);
        renderer.quadGradient(x, y, width, t, cTopLeft, cTopRight, cTopRight, cTopLeft);
        renderer.quadGradient(x, y + height - t, width, t, cBottomLeft, cBottomRight, cBottomRight, cBottomLeft);
        float innerHeight = Math.max(0.0f, height - t * 2.0f);
        if (innerHeight > 0.0f) {
            renderer.quadGradient(x, y + t, t, innerHeight, cTopLeft, cTopLeft, cBottomLeft, cBottomLeft);
            renderer.quadGradient(x + width - t, y + t, t, innerHeight, cTopRight, cTopRight, cBottomRight, cBottomRight);
        }
    }

    private static List<TooltipLine> convertOrdered(List<? extends FormattedCharSequence> lines) {
        List<TooltipLine> out = new ArrayList<>();
        int fallback = Theme.theme().textPrimary();
        for (FormattedCharSequence line : lines) {
            StringBuilder sb = new StringBuilder();
            int[] color = new int[]{0};
            line.accept((idx, style, codePoint) -> {
                sb.appendCodePoint(codePoint);
                if (color[0] == 0 && style != null && style.getColor() != null) {
                    color[0] = 0xFF000000 | style.getColor().getValue();
                }
                return true;
            });
            if (sb.length() == 0) sb.append(' ');
            int c = color[0] != 0 ? color[0] : fallback;
            out.add(new TooltipLine(sb.toString(), c));
        }
        return out;
    }

    private static List<TooltipLine> prepareTooltipLines(List<TooltipLine> lines, boolean previewOpen) {
        if (lines == null || lines.isEmpty()) return lines;
        boolean isShulker = isShulkerBox(TOOLTIP_STACK);
        boolean showHint = shouldShowShulkerHint(isShulker);

        if (!previewOpen && !showHint) {
            return lines;
        }

        List<TooltipLine> out = new ArrayList<>(lines.size() + (showHint ? 1 : 0));
        for (TooltipLine line : lines) {
            if (previewOpen && isShulker && (isShulkerMoreLine(line.text) || isShulkerItemLine(line.text))) {
                continue;
            }
            out.add(line);
        }

        if (showHint) {
            out.add(new TooltipLine(buildShulkerHint(), SHULKER_HINT_COLOR));
        }
        return out;
    }

    private static boolean shouldShowShulkerHint(boolean isShulker) {
        if (!isShulker) return false;
        if (!INSTANCE.isShulkerPreviewEnabled()) return false;
        if (!hasShulkerContents()) return false;
        return !INSTANCE.isShulkerPreviewHeld();
    }

    private static String buildShulkerHint() {
        String bind = INSTANCE.getShulkerPreviewBind();
        return "<<" + bind + ">> показать содержимое";
    }

    private static boolean isShulkerMoreLine(String text) {
        if (text == null) return false;
        String t = text.trim();
        if (t.isEmpty()) return false;
        String lower = t.toLowerCase(Locale.ROOT);
        if (lower.startsWith("and ") || lower.startsWith("и ")) {
            return t.contains("...") || t.contains("…");
        }
        if (lower.startsWith("и еще") || lower.startsWith("и ещё")) {
            return t.contains("...") || t.contains("…");
        }
        return false;
    }

    private static boolean isShulkerItemLine(String text) {
        if (text == null) return false;
        String t = text.trim();
        if (t.isEmpty()) return false;
        int l = t.lastIndexOf('(');
        int r = t.lastIndexOf(')');
        if (l < 0 || r <= l) return false;
        for (int i = l + 1; i < r; i++) {
            char c = t.charAt(i);
            if (c >= '0' && c <= '9') {
                return true;
            }
        }
        return false;
    }

    private static boolean hasShulkerContents() {
        if (!isShulkerBox(TOOLTIP_STACK)) return false;
        ItemContainerContents container = TOOLTIP_STACK.get(DataComponents.CONTAINER);
        if (container == null) return false;
        for (net.minecraft.world.item.ItemStackTemplate template : container.nonEmptyItems()) {
            ItemStack stack = template.create();
            if (stack != null && !stack.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasAnyStacks(NonNullList<ItemStack> stacks) {
        if (stacks == null) return false;
        for (ItemStack s : stacks) {
            if (s != null && !s.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static PreviewLayout buildShulkerPreviewLayout() {
        BetterTooltips shulker = INSTANCE;
        if (!shulker.isShulkerPreviewEnabled()) {
            return null;
        }
        if (!shulker.isShulkerPreviewHeld()) {
            return null;
        }
        if (TOOLTIP_STACK == null || TOOLTIP_STACK.isEmpty()) {
            return null;
        }
        if (!isShulkerBox(TOOLTIP_STACK)) {
            return null;
        }
        if (TOOLTIP_CTX == null) {
            return null;
        }

        ItemContainerContents container = TOOLTIP_STACK.get(DataComponents.CONTAINER);
        if (container == null) {
            return null;
        }

        int cols = 9;
        int rows = 3;
        NonNullList<ItemStack> stacks = NonNullList.withSize(cols * rows, ItemStack.EMPTY);
        container.copyInto(stacks);

        float pad = SHULKER_PADDING;
        float slotSize = shulker.shulkerPreviewSlotSize();
        float slotGap = SHULKER_SLOT_GAP;
        int containerSignature = computeShulkerSignature(TOOLTIP_STACK, stacks);

        if (SHULKER_CACHE != null
                && SHULKER_CACHE_SIGNATURE == containerSignature
                && Float.compare(SHULKER_CACHE_PAD, pad) == 0
                && Float.compare(SHULKER_CACHE_SLOT_SIZE, slotSize) == 0
                && Float.compare(SHULKER_CACHE_SLOT_GAP, slotGap) == 0) {
            return SHULKER_CACHE_HAS_ITEMS ? SHULKER_CACHE : null;
        }

        boolean hasItems = hasAnyStacks(stacks);
        SHULKER_CACHE_SIGNATURE = containerSignature;
        SHULKER_CACHE_PAD = pad;
        SHULKER_CACHE_SLOT_SIZE = slotSize;
        SHULKER_CACHE_SLOT_GAP = slotGap;
        SHULKER_CACHE_HAS_ITEMS = hasItems;
        if (!hasItems) {
            SHULKER_CACHE = null;
            return null;
        }

        int bgW = Math.round(pad * 2f + cols * slotSize + (cols - 1) * slotGap);
        int bgH = Math.round(pad * 2f + rows * slotSize + (rows - 1) * slotGap);

        SHULKER_CACHE = new PreviewLayout(
                bgW,
                bgH,
                cols,
                rows,
                pad,
                slotSize,
                slotGap,
                copyStacks(stacks),
                containerSignature
        );
        return SHULKER_CACHE;
    }

    private static void renderShulkerPreview(PreviewLayout preview, int baseX, int baseY) {
        if (preview == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) return;

        int slotBg = SHULKER_SLOT_BG;
        int border = SHULKER_SLOT_BORDER;

        float scale = ViewportContext.getScaleFactor();

        ViewportContext.beginUnscaled(TOOLTIP_CTX);
        Renderer2D.COLOR.begin();

        float slotStroke = 0.35f * scale;

        for (int row = 0; row < preview.rows; row++) {
            for (int col = 0; col < preview.cols; col++) {
                float sx = baseX + preview.pad + col * (preview.slotSize + preview.slotGap);
                float sy = baseY + preview.pad + row * (preview.slotSize + preview.slotGap);
                float sxb = sx * scale;
                float syb = sy * scale;
                float swb = preview.slotSize * scale;
                float shb = preview.slotSize * scale;

                Renderer2D.COLOR.roundedRect(sxb, syb, swb, shb,
                        SHULKER_SLOT_RADIUS * scale,
                        SHULKER_SLOT_SOFTNESS * scale,
                        slotBg);
                Renderer2D.COLOR.roundedRectStroke(sxb, syb, swb, shb,
                        SHULKER_SLOT_RADIUS * scale,
                        SHULKER_SLOT_SOFTNESS * scale,
                        slotStroke,
                        border);
            }
        }
        Renderer2D.COLOR.render();
        ViewportContext.end(TOOLTIP_CTX);

        if (TOOLTIP_CTX == null) return;

        // Slot geometry uses raw framebuffer coordinates, so item icons use the same
        // projection and scale to remain centered in the grid.
        ViewportContext.beginUnscaled(TOOLTIP_CTX);
        Renderer2D.COLOR.begin();
        float offset = (preview.slotSize - 16f) * 0.5f;
        try (ProfilerPhase.Scope ignored = ProfilerPhase.scope("2d:tooltip:shulker_preview")) {
            int seed = 1;
            int index = 0;
            for (int row = 0; row < preview.rows; row++) {
                for (int col = 0; col < preview.cols; col++) {
                    if (index >= preview.stacks.size()) {
                        Renderer2D.COLOR.render();
                        ViewportContext.end(TOOLTIP_CTX);
                        return;
                    }
                    ItemStack stack = preview.stacks.get(index);
                    if (stack == null || stack.isEmpty()) {
                        index++;
                        continue;
                    }

                    float sx = (baseX + preview.pad + col * (preview.slotSize + preview.slotGap) + offset) * scale;
                    float sy = (baseY + preview.pad + row * (preview.slotSize + preview.slotGap) + offset) * scale;
                    Renderer2D.COLOR.item(stack, sx, sy, scale, seed++, Renderer2D.ITEM_OVERLAY_ALL, null);
                    index++;
                }
            }
            Renderer2D.COLOR.render();
            ViewportContext.end(TOOLTIP_CTX);
        }
    }

    private static boolean isShulkerBox(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (!(stack.getItem() instanceof BlockItem blockItem)) return false;
        return blockItem.getBlock() instanceof ShulkerBoxBlock;
    }

    private static NonNullList<ItemStack> copyStacks(NonNullList<ItemStack> stacks) {
        NonNullList<ItemStack> copy = NonNullList.withSize(stacks.size(), ItemStack.EMPTY);
        for (int i = 0; i < stacks.size(); i++) {
            ItemStack stack = stacks.get(i);
            copy.set(i, stack == null ? ItemStack.EMPTY : stack.copy());
        }
        return copy;
    }

    private static int computeShulkerSignature(ItemStack tooltipStack, NonNullList<ItemStack> stacks) {
        int hash = 1;
        hash = 31 * hash + (tooltipStack == null ? 0 : tooltipStack.getItem().hashCode());
        hash = 31 * hash + stacks.size();
        for (int i = 0; i < stacks.size(); i++) {
            ItemStack stack = stacks.get(i);
            if (stack == null || stack.isEmpty()) {
                hash = 31 * hash;
                continue;
            }
            hash = 31 * hash + stack.getItem().hashCode();
            hash = 31 * hash + stack.getCount();
            hash = 31 * hash + stack.getDamageValue();
            hash = 31 * hash + stack.getComponents().hashCode();
        }
        return hash;
    }

    @Override
    protected void defineSettings(List<SettingDef> defs) {
        defs.add(0, enabledSettingDef());
    }

    @Override
    protected void onLoaded() {
        registerBind(shulkerPreviewHold);
    }

    public List<SettingDef> getSettingsGroup(String groupId) {
        SettingsGroup group = SettingsGroup.fromId(groupId);
        if (group == null) return List.of();
        List<SettingDef> defs = new ArrayList<>();
        switch (group) {
            case TOOLTIP_GUI -> defs.add(enabledSettingDef());
            case TOOLTIP_ITEMS -> add(defs, itemTooltipEnabled, itemInfoColorize, topIgnore);
            case SHULKER_PREVIEW -> addShulkerPreviewSettings(defs);
        }
        return defs;
    }

    private ItemIdSetValue registerTopIgnore() {
        ItemIdSetValue value = TopEnchantUtil.ignoreValue();
        return declareSetting(value, SettingDef.textList(value));
    }

    private void addShulkerPreviewSettings(List<SettingDef> defs) {
        add(defs,
                shulkerPreviewEnabled,
                shulkerPreviewSlotSize,
                shulkerPreviewHold
        );
    }

    private void add(List<SettingDef> defs, ConfigValue<?>... values) {
        for (ConfigValue<?> value : values) {
            SettingDef def = settingDef(value);
            if (def != null) {
                defs.add(def);
            }
        }
    }

    public boolean useCustomGuiTooltips() {
        if (RuntimeGate.isPanic()) return false;
        BetterButtons buttons = BetterButtons.get();
        if (buttons != null && buttons.useUiButtons()) return true;
        return isEnabled();
    }

    public boolean useCustomItemTooltips() {
        if (RuntimeGate.isPanic()) return false;
        BetterButtons buttons = BetterButtons.get();
        if (buttons != null && buttons.useUiButtons()) return true;
        return itemTooltipEnabled.get();
    }

    public boolean isItemInfoColorizeEnabled() {
        return !RuntimeGate.isPanic() && itemInfoColorize.get();
    }

    public boolean isTooltipAlphaEnabled() {
        return !RuntimeGate.isPanic() && anyTooltipsEnabled();
    }

    public int tooltipColor() {
        return (TOOLTIP_BG_ALPHA << 24) | 0x00101114;
    }

    public boolean isShulkerPreviewEnabled() {
        return !RuntimeGate.isPanic() && shulkerPreviewEnabled.get();
    }

    public void setShulkerPreviewEnabled(boolean value) {
        shulkerPreviewEnabled.set(value);
        saveConfig();
    }

    public boolean isGuiTooltipEnabled() {
        return isEnabled();
    }

    public void setGuiTooltipEnabled(boolean value) {
        setEnabled(value);
    }

    public boolean isItemTooltipEnabled() {
        return itemTooltipEnabled.get();
    }

    public void setItemTooltipEnabled(boolean value) {
        itemTooltipEnabled.set(value);
        saveConfig();
    }

    public boolean isShulkerPreviewHeld() {
        if (RuntimeGate.isPanic()) return false;
        return KeyManager.isHeldAllowScreen(bindingName(shulkerPreviewHold));
    }

    public String getShulkerPreviewBind() {
        return shulkerPreviewHold.get();
    }

    public int shulkerPreviewBgRgb() {
        return Theme.theme().windowBg() & 0x00FFFFFF;
    }

    public int shulkerPreviewBgAlpha() {
        return TOOLTIP_BG_ALPHA;
    }

    public float shulkerPreviewBgRadius() {
        return SHULKER_BG_RADIUS;
    }

    public float shulkerPreviewBgSoftness() {
        return SHULKER_BG_SOFTNESS;
    }

    public int shulkerPreviewSlotBgRgb() {
        return SHULKER_SLOT_BG & 0x00FFFFFF;
    }

    public int shulkerPreviewSlotBorderRgb() {
        return SHULKER_SLOT_BORDER & 0x00FFFFFF;
    }

    public int shulkerPreviewSlotBorderAlpha() {
        return (SHULKER_SLOT_BORDER >>> 24) & 0xFF;
    }

    public float shulkerPreviewSlotRadius() {
        return SHULKER_SLOT_RADIUS;
    }

    public float shulkerPreviewSlotSoftness() {
        return SHULKER_SLOT_SOFTNESS;
    }

    public float shulkerPreviewSlotSize() {
        return shulkerPreviewSlotSize.get();
    }

    public float shulkerPreviewSlotGap() {
        return SHULKER_SLOT_GAP;
    }

    public float shulkerPreviewPadding() {
        return SHULKER_PADDING;
    }

    private boolean anyTooltipsEnabled() {
        if (RuntimeGate.isPanic()) return false;
        if (useCustomGuiTooltips()) return true;
        if (itemTooltipEnabled.get()) return true;
        return shulkerPreviewEnabled.get();
    }

    private String bindingName(KeyBindValue value) {
        return name() + ":" + value.getName();
    }

    private void registerBind(KeyBindValue value) {
        String name = bindingName(value);
        KeyManager.unregisterAll(name);
        if (!value.isNone()) {
            KeyManager.registerCombo(name, value.get());
        }
    }

    private boolean renderTooltipPanelScripted(Minecraft mc,
                                               Renderer2D renderer,
                                               TextRenderer textRenderer,
                                               float x,
                                               float y,
                                               float width,
                                               float height,
                                               int bg,
                                               int strokeTopLeft,
                                               int strokeTopRight,
                                               int strokeBottomRight,
                                               int strokeBottomLeft,
                                               int glow,
                                               float strokeWidth,
                                               float glowSize,
                                               float panelAlpha) {
        if (mc == null || renderer == null || textRenderer == null || tooltipPanelHandle.isRuntimeBlocked())
            return false;
        HudScriptLayouts.pollReloadCombo(mc);
        if (tooltipPanelHandle.consumeChanged()) {
            resetTooltipPanelRuntime();
        }
        UiScriptModule module = ensureTooltipPanelModule(mc);
        if (module == null) return false;

        float fbScale = ViewportContext.getScaleFactor();
        float drawX = x * fbScale;
        float drawY = y * fbScale;
        float drawW = width * fbScale;
        float drawH = height * fbScale;

        float strokeWidthPx = strokeWidth * fbScale;
        float glowSizePx = glowSize * fbScale;
        Map<String, Object> props = tooltipPanelProps(
                drawW, drawH, bg, strokeTopLeft, strokeTopRight, strokeBottomRight, strokeBottomLeft, glow, strokeWidthPx, glowSizePx
        );
        long signature = CachedUiScriptRuntime.signature(props);
        long layoutSignature = CachedUiScriptRuntime.mix(
                CachedUiScriptRuntime.mix(signature, drawX),
                drawY
        );
        UiRuntime baked = tooltipRuntime.bake(
                tooltipPanelHandle,
                module,
                "tooltip_panel",
                signature,
                layoutSignature,
                drawW,
                drawH,
                textRenderer,
                drawX,
                drawY,
                drawW,
                drawH,
                () -> props
        );
        if (baked == null) return false;

        ViewportContext.unscaledProjection();
        boolean startedBatch = !Renderer2D.isBatching();
        if (startedBatch) renderer.begin();
        baked.render(new UiRenderContext(renderer, textRenderer, TOOLTIP_CTX, 0.0f, UiProjectionMode.RAW_FRAMEBUFFER, panelAlpha));
        if (startedBatch) {
            renderer.render();
        }
        ViewportContext.scaledProjection();
        return true;
    }

    private Map<String, Object> tooltipPanelProps(float drawW,
                                                  float drawH,
                                                  int bg,
                                                  int strokeTopLeft,
                                                  int strokeTopRight,
                                                  int strokeBottomRight,
                                                  int strokeBottomLeft,
                                                  int glow,
                                                  float strokeWidthPx,
                                                  float glowSizePx) {
        LinkedHashMap<String, Object> props = new LinkedHashMap<>(12);
        props.put("width", drawW);
        props.put("height", drawH);
        props.put("bg", hex(bg));
        props.put("strokeTopLeft", hex(strokeTopLeft));
        props.put("strokeTopRight", hex(strokeTopRight));
        props.put("strokeBottomRight", hex(strokeBottomRight));
        props.put("strokeBottomLeft", hex(strokeBottomLeft));
        props.put("strokeWidth", strokeWidthPx);
        props.put("glow", hex(glow));
        props.put("glowSize", glowSizePx);
        return props;
    }

    private UiScriptModule ensureTooltipPanelModule(Minecraft mc) {
        if (mc == null || mc.getResourceManager() == null) return null;
        if (!tooltipPanelHandle.ensureLoaded(mc.getResourceManager())) {
            HudScriptLayouts.reportLoadError(tooltipPanelHandle);
            return null;
        }
        tooltipPanelHandle.consumeChanged();
        return tooltipPanelHandle.module();
    }

    private void resetTooltipPanelRuntime() {
        tooltipRuntime.reset();
    }

    private enum SettingsGroup {
        TOOLTIP_GUI(GROUP_TOOLTIP_GUI),
        TOOLTIP_ITEMS(GROUP_TOOLTIP_ITEMS),
        SHULKER_PREVIEW(GROUP_SHULKER_PREVIEW);

        private final String id;

        SettingsGroup(String id) {
            this.id = id;
        }

        private static SettingsGroup fromId(String id) {
            if (id == null) return null;
            for (SettingsGroup group : values()) {
                if (group.id.equals(id)) return group;
            }
            return null;
        }
    }

    private record TooltipLine(String text, int color) {
    }

    private record PreviewLayout(int width,
                                 int height,
                                 int cols,
                                 int rows,
                                 float pad,
                                 float slotSize,
                                 float slotGap,
                                 NonNullList<ItemStack> stacks,
                                 int containerSignature) {
    }

}

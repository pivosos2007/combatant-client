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
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTextTooltip;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import combatant.client.util.screen.ClientScreen;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import combatant.client.config.SettingDef;
import combatant.client.features.gui.hud.AbstractHudElement;
import combatant.client.features.gui.hud.HudGlassSettings;
import combatant.client.features.gui.preview.api.PreviewCatalog;
import combatant.client.features.gui.preview.api.HudPreviewCanvas;
import combatant.client.features.gui.hud.HudElementRegister;
import combatant.client.render.engine.core.ViewportContext;
import combatant.client.render.engine.profiler.ProfilerPhase;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.renderer.ui.UiBlurResources;
import combatant.client.runtime.RuntimeGate;
import combatant.client.input.CombatantInput;
import combatant.client.input.InputScope;
import combatant.client.mixins.accessors.ClientTextTooltipAccessor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@HudElementRegister(order = 20)
public final class BetterTooltips extends AbstractHudElement {

    public static final BetterTooltips INSTANCE = new BetterTooltips();
    public static final String GROUP_TOOLTIP_GUI = "vanilla_tooltip_gui";
    public static final String GROUP_TOOLTIP_ITEMS = "vanilla_tooltip_items";
    public static final String GROUP_SHULKER_PREVIEW = "vanilla_shulker_preview";
    private static final int TOOLTIP_BG_ALPHA = 0xEE;
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
    private static long tooltipRenderedFrameId = Long.MIN_VALUE;
    private static PreviewLayout SHULKER_CACHE;
    private static int SHULKER_CACHE_SIGNATURE;
    private static float SHULKER_CACHE_PAD;
    private static float SHULKER_CACHE_SLOT_SIZE;
    private static float SHULKER_CACHE_SLOT_GAP;
    private static boolean SHULKER_CACHE_HAS_ITEMS;
    private final BooleanValue itemTooltipEnabled = bool("bt_items_enabled", true);
    private final BooleanValue itemInfoColorize = bool("bt_item_info_colors", true);
    private final BooleanValue rareModelEnabled = bool("bt_rare_model", true);
    private final NumberValue<Float> modelRotationSpeed = num("bt_model_rotation", 1.0f, 0.0f, 4.0f);
    private final NumberValue<Float> rarityGlow = num("bt_rarity_fresnel", 0.75f, 0.0f, 1.0f);
    private final NumberValue<Float> genericScale = num("bt_generic_scale", 0.64f, 0.5f, 1.5f);
    private final NumberValue<Float> itemScale = num("bt_item_scale", 0.67f, 0.5f, 1.5f);
    private final NumberValue<Float> guiBlur = num("bt_gui_blur", 0.52f, 0.0f, 1.0f);
    private final NumberValue<Float> itemBlur = num("bt_item_blur", 0.52f, 0.0f, 1.0f);
    private final NumberValue<Float> guiDarkness = num("bt_gui_darkness", 0.76f, 0.0f, 1.0f);
    private final NumberValue<Float> itemDarkness = num("bt_item_darkness", 0.76f, 0.0f, 1.0f);
    private boolean lastPresentedItem;
    private final BooleanValue shulkerPreviewEnabled = bool("bt_shulker_preview", true);
    private final NumberValue<Float> shulkerPreviewSlotSize =
            visibleWhen(num("bt_shulker_slot", 18.0f, 12.0f, 24.0f), this::isShulkerPreviewEnabled);
    private final KeyBindValue shulkerPreviewHold =
            visibleWhen(bind("bt_shulker_hold", "LEFT_SHIFT", BindMode.HOLD), this::isShulkerPreviewEnabled);
    private final HudGlassSettings guiGlassSettings = new HudGlassSettings(
            "bt_gui", BetterTooltipPresentation.defaultGlassOptics());
    private final HudGlassSettings itemGlassSettings = new HudGlassSettings(
            "bt_items", BetterTooltipPresentation.defaultGlassOptics());
    private final BetterTooltipPresentation presentation = new BetterTooltipPresentation();
    /** Isolated animation and cached layout; drawing uses the same production presenter. */
    private final BetterTooltipPresentation previewPresentation = new BetterTooltipPresentation();
    private BetterTooltips() {
        super("vanilla_tooltips", "Tooltips", "hud.static.vanilla_tooltips.description", true);
    }

    @Override
    public PreviewCatalog.Definition<Boolean, HudPreviewCanvas> previewDefinition() {
        return PreviewCatalog.definition(
                new PreviewCatalog.Id(PreviewCatalog.Surface.HUD, getId()), HudPreviewCanvas.class,
                frame -> PreviewCatalog.Availability.READY,
                frame -> GROUP_TOOLTIP_ITEMS.equals(frame.scenario())
                        || GROUP_SHULKER_PREVIEW.equals(frame.scenario()),
                (canvas, item) -> {
                    if (canvas.graphics() == null) return;
                    Minecraft mc = Minecraft.getInstance();
                    if (mc == null || mc.getWindow() == null) return;
                    // Reserve the central region for settings. Use native GUI coordinates,
                    // not virtual HUD coordinates, so the tooltip retains its real scale.
                    float scaleX = canvas.guiWidth() * 0.08f;
                    float scaleY = canvas.guiHeight() * 0.38f;
                    List<BetterTooltipPresentation.Line> lines = item
                            ? List.of(new BetterTooltipPresentation.Line("Example Item", 0xFFD8E8FF),
                                    new BetterTooltipPresentation.Line("Preview of item information", 0xFFC4C9D0),
                                    new BetterTooltipPresentation.Line("Additional properties", 0xFF98A3B2))
                            : List.of(new BetterTooltipPresentation.Line("Tooltip preview", 0xFFE6EDFF),
                                    new BetterTooltipPresentation.Line("Settings are applied live", 0xFFB8C5D6));
                    BetterTooltipPresentation.Input example = new BetterTooltipPresentation.Input(
                            lines, ItemStack.EMPTY, item, scaleX, scaleY,
                            item ? 0xF02A111L : 0xF02A110L, 0f, 0f, ClientScreen.current(mc));
                    float selectedScale = item ? itemScale.get() : genericScale.get();
                    // The production presenter manages its own render batch. Flush the
                    // preview host's preceding primitives before letting it take ownership.
                    canvas.renderer().render();
                    try {
                        previewPresentation.render(mc, canvas.graphics(), example, selectedScale,
                            item ? itemBlur.get() : guiBlur.get(),
                            item ? itemDarkness.get() : guiDarkness.get(), rarityGlow.get(),
                            rareModelEnabled.get(), modelRotationSpeed.get(),
                            (item ? itemGlassSettings : guiGlassSettings)
                                    .apply(BetterTooltipPresentation.defaultGlassOptics()));
                    } finally {
                        canvas.renderer().begin();
                    }
                }, PreviewCatalog.PreviewSpace.VANILLA_GUI);
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

    public static boolean captureTooltipComponents(List<ClientTooltipComponent> components,
                                                   ClientTooltipPositioner positioner,
                                                   int mouseX, int mouseY,
                                                   ItemStack stack,
                                                   GuiGraphicsExtractor ctx,
                                                   boolean replaceExisting) {
        BetterTooltips tooltips = INSTANCE;
        boolean item = stack != null && !stack.isEmpty();
        if (item ? !tooltips.useCustomItemTooltips() : !tooltips.useCustomGuiTooltips()) {
            return false;
        }
        if (components == null || components.isEmpty()) return false;
        if (hasTooltip() && !replaceExisting) return true;

        List<FormattedCharSequence> lines = new ArrayList<>(components.size());
        boolean hasNonText = false;
        for (ClientTooltipComponent component : components) {
            if (component instanceof ClientTextTooltip text) {
                FormattedCharSequence sequence = ((ClientTextTooltipAccessor) (Object) text).combatant$getText();
                if (sequence != null) lines.add(sequence);
            } else {
                hasNonText = true;
            }
        }
        if (lines.isEmpty()) return false;
        if (hasNonText) return false;

        setDrawContext(ctx);
        if (item) {
            captureItemTooltipOrdered(lines, positioner, mouseX, mouseY, stack, ctx);
        } else {
            captureTooltipOrdered(lines, positioner, mouseX, mouseY);
        }
        return true;
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

    public static boolean matchesItemTooltip(List<ClientTooltipComponent> components, ItemStack stack) {
        if (components == null || components.isEmpty() || stack == null || stack.isEmpty()) return false;
        String expected = stack.getHoverName().getString();
        if (expected == null || expected.isBlank()) return false;
        expected = expected.trim();

        for (ClientTooltipComponent component : components) {
            if (!(component instanceof ClientTextTooltip text)) continue;
            FormattedCharSequence sequence = ((ClientTextTooltipAccessor) (Object) text).combatant$getText();
            if (sequence == null) continue;
            String actual = plainText(sequence).trim();
            if (actual.isEmpty()) continue;
            return actual.equals(expected);
        }
        return false;
    }

    public static void renderTooltip() {
        long frameId = UiBlurResources.currentFrameId();
        if (frameId != Long.MIN_VALUE && tooltipRenderedFrameId == frameId) return;
        tooltipRenderedFrameId = frameId;
        if (RuntimeGate.isPanic()) {
            INSTANCE.presentation.clear();
            return;
        }
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
        Screen currentScreen = ClientScreen.current(mc);
        PreviewLayout preview = hasTooltip() ? buildShulkerPreviewLayout() : null;
        BetterTooltipPresentation.Input input = null;
        if (lines != null && !lines.isEmpty()) {
            boolean item = TOOLTIP_STACK != null && !TOOLTIP_STACK.isEmpty();
            List<TooltipLine> finalLines = prepareTooltipLines(lines, preview != null);
            ArrayList<BetterTooltipPresentation.Line> converted = new ArrayList<>(finalLines.size());
            for (TooltipLine line : finalLines) {
                converted.add(new BetterTooltipPresentation.Line(line.text(), line.color()));
            }
            float anchorX = mouseX;
            float anchorY = mouseY;
            long anchorKey = ((long) System.identityHashCode(currentScreen) << 32)
                    ^ (item ? 0x12A57EL : converted.hashCode());
            input = new BetterTooltipPresentation.Input(converted,
                    item ? TOOLTIP_STACK : ItemStack.EMPTY,
                    item, anchorX, anchorY, anchorKey,
                    preview != null ? preview.width : 0.0f,
                    preview != null ? preview.height : 0.0f,
                    currentScreen);
        }
        BetterTooltips module = INSTANCE;
        if (input != null) module.lastPresentedItem = input.item();
        boolean itemStyle = module.lastPresentedItem;
        float scale = (itemStyle ? module.itemScale.get() : module.genericScale.get()) * TOOLTIP_SCALE_OVERRIDE;
        BetterTooltipPresentation.Result rendered = module.presentation.render(mc, TOOLTIP_CTX,
                input, scale,
                itemStyle ? module.itemBlur.get() : module.guiBlur.get(),
                itemStyle ? module.itemDarkness.get() : module.guiDarkness.get(),
                module.rarityGlow.get(), module.rareModelEnabled.get(), module.modelRotationSpeed.get(),
                (itemStyle ? module.itemGlassSettings : module.guiGlassSettings)
                        .apply(BetterTooltipPresentation.defaultGlassOptics()));
        if (preview != null && rendered.footerVisible()) {
            renderShulkerPreview(preview, Math.round(rendered.footerX()), Math.round(rendered.footerY()));
        }
    }

    private static String plainText(FormattedCharSequence line) {
        if (line == null) return "";
        StringBuilder sb = new StringBuilder();
        line.accept((idx, style, codePoint) -> {
            sb.appendCodePoint(codePoint);
            return true;
        });
        return sb.toString();
    }

    private static List<TooltipLine> convertOrdered(List<? extends FormattedCharSequence> lines) {
        List<TooltipLine> out = new ArrayList<>();
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
            int c = color[0];
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
        return Component.translatable("combatant.tooltip.shulker.hold", bind).getString();
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
        ViewportContext.ProjectionMode previousProjection = ViewportContext.activeMode();

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
        restoreTooltipProjection(previousProjection);

        if (TOOLTIP_CTX == null) return;
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
                        restoreTooltipProjection(previousProjection);
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
            restoreTooltipProjection(previousProjection);
        }
    }

    private static void restoreTooltipProjection(ViewportContext.ProjectionMode mode) {
        switch (mode) {
            case LOGICAL -> ViewportContext.beginUnscaledLogical(TOOLTIP_CTX);
            case UNSCALED -> ViewportContext.beginUnscaled(TOOLTIP_CTX);
            default -> ViewportContext.end(TOOLTIP_CTX);
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
    protected void collectExtraConfigValues(java.util.Map<String, ConfigValue<?>> values) {
        java.util.List<SettingDef> optics = new java.util.ArrayList<>();
        guiGlassSettings.addSettings(optics);
        itemGlassSettings.addSettings(optics);
        for (SettingDef setting : optics) {
            ConfigValue<?> value = setting.value();
            if (value != null) values.put(value.getName(), value);
        }
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
            case TOOLTIP_GUI -> {
                defs.add(enabledSettingDef());
                addGenericSettings(defs);
            }
            case TOOLTIP_ITEMS -> {
                addItemSettings(defs);
            }
            case SHULKER_PREVIEW -> addShulkerPreviewSettings(defs);
        }
        return defs;
    }

    private void addGenericSettings(List<SettingDef> defs) {
        add(defs, genericScale, guiBlur, guiDarkness);
        guiGlassSettings.addSettings(defs);
    }

    private void addItemSettings(List<SettingDef> defs) {
        add(defs, itemTooltipEnabled, itemScale, itemBlur, itemDarkness, rareModelEnabled,
                modelRotationSpeed, rarityGlow, itemInfoColorize);
        itemGlassSettings.addSettings(defs);
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
        return !RuntimeGate.isPanic() && isEnabled();
    }

    public boolean useCustomItemTooltips() {
        return !RuntimeGate.isPanic() && itemTooltipEnabled.get();
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
        return CombatantInput.bindings().isHeld(bindingName(shulkerPreviewHold), InputScope.GLOBAL);
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
        CombatantInput.bindings().unbind(name);
        if (!value.isNone()) {
            CombatantInput.bindings().bind(name, value.get());
        }
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

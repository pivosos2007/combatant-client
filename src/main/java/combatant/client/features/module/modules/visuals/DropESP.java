/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.visuals;

import combatant.client.render.engine.text.BuiltinFontCatalog;
import combatant.client.config.values.*;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import combatant.client.config.SettingDef;
import combatant.client.config.values.*;
import combatant.client.features.gui.clickgui.settings.TextListSetting;
import combatant.client.features.gui.hud.HudRenderSpace;
import combatant.client.features.module.HudPhase;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.WorldPhase;
import combatant.client.mixininterface.IEntity;
import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.RenderState;
import combatant.client.render.engine.core.ViewportContext;
import combatant.client.render.engine.core.CombatantWorldMatrices;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.render.engine.renderer.ui.ItemBatchRenderer;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.render.engine.text.RuntimeTextLayout;
import combatant.client.render.engine.text.VanillaTextRenderer;
import combatant.client.render.engine.text.WorldTextRenderer;
import combatant.client.render.engine.world.WorldBillboardRenderer;
import combatant.client.render.engine.world.WorldUiPresentationService;
import combatant.client.render.helpers.ScreenProjection;
import combatant.client.render.helpers.ScreenSpaceOverlay2D;
import combatant.client.util.item.IllegalItemUtil;
import combatant.client.util.item.RarityColorUtil;
import combatant.client.util.item.TopEnchantUtil;
import combatant.client.util.text.TextRenderUtil;

@ModuleInfo(id = "dropesp", displayName = "DropESP", category = ModuleCategory.VISUALS, subcategory = ModuleSubcategory.ESP,
        description = "module.dropesp.description")
public class DropESP extends Module {

    private static final String SETTING_LIMIT_COMMON_DISTANCE = "limit_common_distance";
    private static final String SETTING_COMMON_MAX_DISTANCE = "common_max_distance";
    private static final String SETTING_SPECIAL_ITEMS_COLOR = "special_items_color";
    private static final String SETTING_ILLEGAL_ENCHANT_COLOR = "illegal_enchant_color";
    private static final String SETTING_TOP_ENCHANT_IGNORE_LIST = "top_enchant_ignore_list";
    private static final String SETTING_SPECIAL_ITEM_IDS = "special_item_ids";
    private static final String SETTING_MODE = "mode";
    private static final String SETTING_FRAME = "frame";
    private static final String SETTING_ITEM_ICON = "item_icon";
    private static final String SETTING_PRESENTATION_MODE = "presentation_mode";
    private static final String SETTING_WORLD_SIZE = "world_size";
    private static final String SETTING_DYNAMIC_WORLD_SCALE = "dynamic_world_scale";
    private static final String SETTING_DYNAMIC_WORLD_SCALE_COEFFICIENT = "dynamic_world_scale_coefficient";
    private static final double ITEM_BOX_EXPAND_XZ = 0.10;
    private static final double ITEM_BOX_EXPAND_TOP = 0.10;
    // Direct UNSCALED_LOGICAL dimensions. No framebuffer/gui-scale compensation.
    // Slightly larger than rich_src's default presentation for readability.
    private static final double RICH_TEXT_LOGICAL_HEIGHT = 17.0;
    private static final double DROP_TEXT_SCALE = 0.92;
    private static final double MATTE_LABEL_PAD_X = 3.6;
    private static final double MATTE_LABEL_PAD_Y = 1.25;
    private static final float MATTE_LABEL_RADIUS = 3.6f;
    private static final int MATTE_BACKDROP_ALPHA = 148;
    private static final double MATTE_ICON_SIZE = 15.0;
    private static final double MATTE_ICON_GAP = 3.6;
    private static final float MATTE_ICON_SCALE = 0.9375f;
    private static final WorldUiPresentationService.Policy WORLD_PRESENTATION_POLICY =
            new WorldUiPresentationService.Policy(0.0150, 12.0, 18.0, 32.0, 0.45, 4.00);
    private static final long INFO_TTL_TICKS = 10L;
    private static final long INFO_SWEEP_TICKS = 100L;
    private final Minecraft mc = Minecraft.getInstance();
    private final java.util.ArrayList<ItemEntity> nearbyItems = new java.util.ArrayList<>();
    private final it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<DropInfo> infoCache = new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<>();
    private final java.util.ArrayList<DropWorldEntry> worldEntries = new java.util.ArrayList<>();
    private net.minecraft.world.level.Level nearbyLevel;
    private net.minecraft.world.entity.player.Player nearbyPlayer;
    private long nearbyTick = Long.MIN_VALUE;
    private long infoSweepTick;
    private final ModeValue modeValue = modeSetting("dropEspMode", SETTING_MODE, "Matte", "Vanilla", "Matte");
    private final EnumValue<WorldUiPresentationService.Mode> presentationMode =
            enumSetting("dropEspPresentationMode", SETTING_PRESENTATION_MODE,
                    WorldUiPresentationService.Mode.HYBRID, WorldUiPresentationService.Mode.values());
    private final NumberValue<Float> worldSize =
            visibleWhen(num("dropEspWorldSize", SETTING_WORLD_SIZE, 0.72f, 0.35f, 1.50f),
                    () -> presentationMode.get() != WorldUiPresentationService.Mode.SCREEN);
    private final BooleanValue dynamicWorldScale =
            visibleWhen(bool("dropEspDynamicWorldScale", SETTING_DYNAMIC_WORLD_SCALE, true),
                    () -> presentationMode.get() != WorldUiPresentationService.Mode.SCREEN);
    private final NumberValue<Float> dynamicWorldScaleCoefficient =
            visibleWhen(num("dropEspDynamicWorldScaleCoefficient", SETTING_DYNAMIC_WORLD_SCALE_COEFFICIENT, 0.25f, 0.0f, 1.0f),
                    () -> presentationMode.get() != WorldUiPresentationService.Mode.SCREEN && dynamicWorldScale.get());
    private final BooleanValue limitCommonDistanceValue = bool("dropEspLimitCommonDistance", SETTING_LIMIT_COMMON_DISTANCE, true);
    private final NumberValue<Integer> commonMaxDistanceValue =
            visibleWhen(num("dropEspCommonMaxDistance", SETTING_COMMON_MAX_DISTANCE, 32, 4, 256), limitCommonDistanceValue::get);
    private final BooleanValue frameValue =
            visibleWhen(bool("dropEspFrame", SETTING_FRAME, true), this::isOverlayMode);
    private final BooleanValue itemIconValue =
            visibleWhen(bool("dropEspItemIcon", SETTING_ITEM_ICON, false),
                    () -> isMatteMode() || presentationMode.get() != WorldUiPresentationService.Mode.SCREEN);
    private final RGBColorValue specialColorValue = colorNoAlpha("dropEspSpecialColor", SETTING_SPECIAL_ITEMS_COLOR, "#FFAA00");
    private final ItemIdSetValue topIgnore = TopEnchantUtil.ignoreValue();
    private final ItemIdSetValue specialItemsValue =
            itemList("dropEspSpecialItems", SETTING_SPECIAL_ITEM_IDS, TextListSetting.PickerMode.ITEMS);

    {
        setting(SettingDef.colorNoAlpha(SETTING_ILLEGAL_ENCHANT_COLOR, IllegalItemUtil.illegalColorValue()));
        setting(SettingDef.textList(SETTING_TOP_ENCHANT_IGNORE_LIST, topIgnore, TextListSetting.PickerMode.ENCHANTMENTS));
    }

    private static int compareDropRenderOrder(DropOverlayEntry a, DropOverlayEntry b) {
        int rarity = Integer.compare(a.sortPriority(), b.sortPriority());
        if (rarity != 0) return rarity;
        return Double.compare(b.distSq(), a.distSq());
    }

    private static int compareDropRenderOrder(DropVanillaEntry a, DropVanillaEntry b) {
        int rarity = Integer.compare(a.sortPriority(), b.sortPriority());
        if (rarity != 0) return rarity;
        return Double.compare(b.distSq(), a.distSq());
    }

    private static int compareDropWorldRenderOrder(DropWorldEntry a, DropWorldEntry b) {
        int rarity = Integer.compare(a.sortPriority(), b.sortPriority());
        if (rarity != 0) return rarity;
        return Double.compare(b.distSq(), a.distSq());
    }

    @Override
    public HudRenderSpace getHudRenderSpace() {
        return HudRenderSpace.UNSCALED_LOGICAL;
    }

    @Override
    public HudPhase getHudPhase() {
        return HudPhase.FIRST;
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.END_MAIN_BILLBOARD;
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!isEnabled() || renderer == null || mc.level == null || mc.player == null) return;
        if (presentationMode.get() == WorldUiPresentationService.Mode.SCREEN) return;

        TextRenderer labelRenderer = dropLabelRenderer(TextRenderer.get());
        Vec3 cameraPos = presentationCameraPosition(tickDelta);
        boolean framesEnabled = frameValue.get();
        boolean iconsEnabled = itemIconValue.get();
        long tick = mc.level.getGameTime();
        java.util.List<DropWorldEntry> entries = worldEntries;
        entries.clear();

        for (ItemEntity item : nearbyItems()) {
            if (item.isRemoved()) continue;
            DropInfo info = infoFor(item, tick);
            Vec3 pos = obtainEntityLerpedPos(item, tickDelta);
            double distSq = pos.distanceToSqr(cameraPos);
            double dist = Math.sqrt(distSq);
            if (!passesDistanceFilter(info, dist)) continue;
            WorldUiPresentationService.Snapshot presentation = resolvePresentation(dist);
            if (presentation.worldAlpha() <= 0.001f) continue;

            // Same bounds as bb.move(pos - position).inflate(0.1) without building the two AABBs.
            AABB bb = item.getBoundingBox();
            double dx = pos.x - item.getX();
            double dy = pos.y - item.getY();
            double dz = pos.z - item.getZ();
            double minX = (bb.minX + dx) - 0.1, maxX = (bb.maxX + dx) + 0.1;
            double minY = (bb.minY + dy) - 0.1, maxY = (bb.maxY + dy) + 0.1;
            double minZ = (bb.minZ + dz) - 0.1, maxZ = (bb.maxZ + dz) + 0.1;
            Vec3 anchor = new Vec3(minX + (maxX - minX) * 0.5, maxY, minZ + (maxZ - minZ) * 0.5);

            Vec3 frameAnchor = null;
            double frameWorldWidth = 0.0;
            double frameWorldHeight = 0.0;
            if (framesEnabled) {
                frameAnchor = new Vec3(
                        (minX + maxX) * 0.5,
                        (minY + maxY) * 0.5,
                        (minZ + maxZ) * 0.5
                );
                frameWorldWidth = Math.max(maxX - minX, maxZ - minZ);
                frameWorldHeight = maxY - minY;
            }

            entries.add(new DropWorldEntry(
                    info.sortPriority, distSq, anchor, frameAnchor, frameWorldWidth, frameWorldHeight,
                    presentation.worldUnitsPerPixel(), presentation.worldAlpha(), item.getItem(), info));
        }

        entries.sort(DropESP::compareDropWorldRenderOrder);
        // Capture the billboard axes before GuiItemAtlas performs any off-screen rendering.
        // More importantly, currentBasis itself is backed by the immutable captured camera
        // matrix, so item-atlas state can never influence billboard orientation.
        WorldBillboardRenderer.Basis basis = WorldBillboardRenderer.currentBasis();
        java.util.List<ItemBatchRenderer.WorldItemSprite[]> sprites = null;
        if (iconsEnabled && !entries.isEmpty()) {
            java.util.List<ItemBatchRenderer.WorldItemRow> itemRows = new java.util.ArrayList<>(entries.size());
            for (int i = 0; i < entries.size(); i++) {
                itemRows.add(new ItemBatchRenderer.WorldItemRow(null, new ItemStack[]{entries.get(i).stack()}, i));
            }
            sprites = ItemBatchRenderer.resolveWorldItemSprites(itemRows);
        }
        // "Ag" height only depends on the renderer/scale; measure it once per frame, not once per label.
        double measuredTextHeight = entries.isEmpty()
                ? 0.0
                : WorldTextRenderer.measure(labelRenderer, "Ag", DROP_TEXT_SCALE, false).height();
        for (int i = 0; i < entries.size(); i++) {
            ItemBatchRenderer.WorldItemSprite sprite = sprites != null && i < sprites.size() && sprites.get(i).length > 0
                    ? sprites.get(i)[0]
                    : null;
            renderWorldDrop(renderer, basis, labelRenderer, entries.get(i), sprite, iconsEnabled, measuredTextHeight);
        }
        entries.clear();
    }

    @Override
    public void onRenderHudEngine(Renderer2D renderer, TextRenderer textRenderer, GuiGraphicsExtractor ctx, float tickDelta) {
        if (!isEnabled() || mc.level == null || mc.player == null) return;
        if (presentationMode.get() == WorldUiPresentationService.Mode.WORLD) return;

        boolean matteMode = isMatteMode();
        boolean overlayMode = isOverlayMode();
        boolean iconsEnabled = itemIconValue.get();
        TextRenderer labelRenderer = overlayMode ? dropLabelRenderer(textRenderer) : VanillaTextRenderer.INSTANCE;
        boolean measureStarted = false;
        java.util.List<DropOverlayEntry> overlayEntries = new java.util.ArrayList<>();
        java.util.List<DropVanillaEntry> vanillaEntries = new java.util.ArrayList<>();
        double textScale = overlayMode ? DROP_TEXT_SCALE : ScreenSpaceOverlay2D.VANILLA_TEXT_SCALE;

        if (!labelRenderer.isBuilding()) {
            labelRenderer.begin(textScale, true, false);
            measureStarted = true;
        }

        long tick = mc.level.getGameTime();
        Vec3 cameraPos = presentationCameraPosition(tickDelta);
        double measuredTextHeight = Double.NaN;
        for (ItemEntity item : nearbyItems()) {
            if (item.isRemoved()) continue;
            DropInfo info = infoFor(item, tick);
            Vec3 pos = obtainEntityLerpedPos(item, tickDelta);

            double distSq = pos.distanceToSqr(cameraPos);
            double dist = Math.sqrt(distSq);
            if (!passesDistanceFilter(info, dist)) continue;
            float presentationAlpha = resolvePresentation(dist).screenAlpha();
            if (presentationAlpha <= 0.001f) continue;

            ItemStack stack = item.getItem();
            int color = info.color;
            int sortPriority = info.sortPriority;

            if (overlayMode) {
                AABB overlayBox = item.getBoundingBox().move(
                        pos.x - item.getX(),
                        pos.y - item.getY(),
                        pos.z - item.getZ()
                ).inflate(0.1);
                ScreenSpaceOverlay2D.ScreenRect rect = ScreenSpaceOverlay2D.projectBox(overlayBox, tickDelta);
                if (rect == null) continue;

                DropLabelEntry matteLabel = null;
                ScreenSpaceOverlay2D.LabelEntry label = null;
                if (matteMode) {
                    if (Double.isNaN(measuredTextHeight)) {
                        measuredTextHeight = RuntimeTextLayout.height(labelRenderer, DROP_TEXT_SCALE, false);
                    }
                    matteLabel = createMatteDropLabel(labelRenderer, stack, info, rect, iconsEnabled, measuredTextHeight);
                } else {
                    label = ScreenSpaceOverlay2D.createCenteredLabel(labelRenderer, info.plainText, color, rect);
                }
                overlayEntries.add(new DropOverlayEntry(
                        sortPriority, distSq, rect, color, matteLabel, label, presentationAlpha));
            } else {
                AABB box = item.getBoundingBox().move(
                        pos.x - item.getX(),
                        pos.y - item.getY(),
                        pos.z - item.getZ()
                );

                Vec3 center = new Vec3(
                        (box.minX + box.maxX) * 0.5,
                        box.minY + (box.maxY - box.minY) * 0.6,
                        (box.minZ + box.maxZ) * 0.5
                );

                Vec3 screen = ScreenProjection.worldToScreen(center, tickDelta);
                if (screen == null) continue;

                double x = screen.x - (labelRenderer.getWidth(info.plainText, false) / 2.0);
                double y = screen.y;
                vanillaEntries.add(new DropVanillaEntry(
                        sortPriority, distSq, ScreenSpaceOverlay2D.labelAt(info.plainText, x, y, color), presentationAlpha));
            }
        }

        if (measureStarted) {
            labelRenderer.end();
        }
        if (overlayEntries.isEmpty() && vanillaEntries.isEmpty()) return;

        overlayEntries.sort(DropESP::compareDropRenderOrder);
        vanillaEntries.sort(DropESP::compareDropRenderOrder);

        if (overlayMode) {
            for (DropOverlayEntry entry : overlayEntries) {
                renderOverlayEntryPass(renderer, labelRenderer, entry, matteMode, textScale);
            }
        } else {
            for (DropVanillaEntry entry : vanillaEntries) {
                renderSingleLabel(labelRenderer, entry.label(), textScale, entry.alpha());
                Renderer2D.flushBatch(Renderer2D.FlushReason.EXPLICIT);
            }
        }
    }

    private void renderOverlayEntryPass(Renderer2D renderer,
                                        TextRenderer labelRenderer,
                                        DropOverlayEntry entry,
                                        boolean matteMode,
                                        double textScale) {
        double previousAlpha = renderer.getAlpha();
        renderer.setAlpha(previousAlpha * entry.alpha());
        try {
            ScreenSpaceOverlay2D.ScreenRect rect = entry.rect();
            if (frameValue.get()) {
                // DropESP frames are hard rectangular quad outlines in every
                // presentation mode. No rounded/SDF stroke is used here.
                ScreenSpaceOverlay2D.drawFrame(renderer, rect, entry.color());
            }

            if (matteMode) {
                DropLabelEntry label = entry.matteLabel();
                if (label == null) return;
                renderer.roundedRect(label.x(), label.y(), label.width(), label.height(),
                        MATTE_LABEL_RADIUS, 0.0f, withAlpha(0x000000, MATTE_BACKDROP_ALPHA));
                if (label.icon()) {
                    renderer.item(label.stack(), label.iconX(), label.iconY(), MATTE_ICON_SCALE, 0, Renderer2D.ITEM_OVERLAY_NONE, null);
                }
                renderSingleDropLabel(labelRenderer, label, textScale, entry.alpha());
            } else {
                ScreenSpaceOverlay2D.LabelEntry label = entry.label();
                if (label == null) return;
                renderSingleLabel(labelRenderer, label, textScale, entry.alpha());
            }
        } finally {
            renderer.setAlpha(previousAlpha);
        }

        Renderer2D.flushBatch(Renderer2D.FlushReason.EXPLICIT);
    }

    private void renderSingleDropLabel(TextRenderer labelRenderer, DropLabelEntry label, double textScale, float alpha) {
        boolean renderStarted = false;
        if (!labelRenderer.isBuilding()) {
            labelRenderer.begin(textScale);
            renderStarted = true;
        }
        try {
            double cursor = label.textX();
            DropInfo info = label.info();
            for (int i = 0; i < info.safeParts.length; i++) {
                cursor = labelRenderer.render(info.safeParts[i], cursor, label.textY(),
                        new RenderColor(scaleAlpha(info.partColors[i], alpha)), false);
            }
        } finally {
            if (renderStarted) labelRenderer.end();
        }
    }

    private void renderSingleLabel(TextRenderer labelRenderer,
                                   ScreenSpaceOverlay2D.LabelEntry label,
                                   double textScale,
                                   float alpha) {
        boolean renderStarted = false;
        if (!labelRenderer.isBuilding()) {
            labelRenderer.begin(textScale);
            renderStarted = true;
        }
        try {
            ScreenSpaceOverlay2D.renderLabels(labelRenderer, java.util.List.of(label), false, alpha);
        } finally {
            if (renderStarted) labelRenderer.end();
        }
    }

    private DropLabelEntry createMatteDropLabel(TextRenderer labelRenderer,
                                                ItemStack stack,
                                                DropInfo info,
                                                ScreenSpaceOverlay2D.ScreenRect rect,
                                                boolean icon,
                                                double measuredTextHeight) {
        double textWidth = screenTextWidth(labelRenderer, info);
        double textHeight = Math.max(RICH_TEXT_LOGICAL_HEIGHT, measuredTextHeight);
        double iconBlock = icon ? MATTE_ICON_SIZE + MATTE_ICON_GAP : 0.0;
        double width = textWidth + iconBlock + MATTE_LABEL_PAD_X * 2.0;
        double contentHeight = Math.max(textHeight, icon ? MATTE_ICON_SIZE : 0.0);
        double height = contentHeight + MATTE_LABEL_PAD_Y * 2.0;
        double x = rect.minX() + (rect.width() - width) * 0.5;
        double y = rect.minY() - height + MATTE_LABEL_PAD_Y;
        double iconX = x + MATTE_LABEL_PAD_X;
        double iconY = y + (height - MATTE_ICON_SIZE) * 0.5;
        double textX = x + MATTE_LABEL_PAD_X + iconBlock;
        double textY = y + (height - measuredTextHeight) * 0.5;
        return new DropLabelEntry(stack, info, x, y, width, height, icon, iconX, iconY, textX, textY, info.color);
    }

    /** Width of the styled label in the on-screen overlay, measured once per (info, renderer). */
    private static double screenTextWidth(TextRenderer renderer, DropInfo info) {
        if (renderer == null) return 0.0;
        if (info.screenWidthRenderer == renderer) return info.screenTextWidth;
        double width = 0.0;
        for (String part : info.safeParts) {
            width += RuntimeTextLayout.width(renderer, part, DROP_TEXT_SCALE, false);
        }
        info.screenWidthRenderer = renderer;
        info.screenTextWidth = width;
        return width;
    }

    /**
     * Per-part billboard widths for the current renderer. A zero total width means the glyph font was
     * not ready yet, so that result is not cached and the measurement is retried next frame.
     */
    private static void measureWorldParts(TextRenderer renderer, DropInfo info) {
        if (info.worldWidthRenderer == renderer) return;
        double total = 0.0;
        for (int i = 0; i < info.safeParts.length; i++) {
            double w = WorldTextRenderer.measure(renderer, info.safeParts[i], DROP_TEXT_SCALE, false).width();
            info.worldPartWidths[i] = w;
            total += w;
        }
        info.worldTextWidth = total;
        info.worldWidthRenderer = total > 0.0 || info.safeParts.length == 0 ? renderer : null;
    }

    private void renderWorldDrop(Renderer3D renderer,
                                 WorldBillboardRenderer.Basis basis,
                                 TextRenderer labelRenderer,
                                 DropWorldEntry entry,
                                 ItemBatchRenderer.WorldItemSprite itemSprite,
                                 boolean iconsEnabled,
                                 double measuredTextHeight) {
        double textScale = DROP_TEXT_SCALE;
        DropInfo info = entry.info();
        measureWorldParts(labelRenderer, info);
        double textWidth = info.worldTextWidth;
        boolean icon = iconsEnabled && itemSprite != null;
        double iconBlock = icon ? MATTE_ICON_SIZE + MATTE_ICON_GAP : 0.0;
        double textHeight = Math.max(RICH_TEXT_LOGICAL_HEIGHT, measuredTextHeight);
        double contentHeight = Math.max(textHeight, icon ? MATTE_ICON_SIZE : 0.0);
        double width = textWidth + iconBlock + MATTE_LABEL_PAD_X * 2.0;
        double height = contentHeight + MATTE_LABEL_PAD_Y * 2.0;
        double x = -width * 0.5;
        double y = -height + MATTE_LABEL_PAD_Y;

        if (entry.frameAnchor() != null && entry.frameWorldWidth() > 0.0 && entry.frameWorldHeight() > 0.0) {
            double fw = entry.frameWorldWidth() / entry.worldScale();
            double fh = entry.frameWorldHeight() / entry.worldScale();
            double fx = -fw * 0.5;
            double fy = -fh * 0.5;
            double t = 0.5;
            int c = scaleAlpha(info.color, entry.alpha());
            WorldBillboardRenderer.quad(renderer, basis, entry.frameAnchor(), fx - t, fy - t, fw + t * 2.0, t, entry.worldScale(), c);
            WorldBillboardRenderer.quad(renderer, basis, entry.frameAnchor(), fx - t, fy - t, t, fh + t * 2.0, entry.worldScale(), c);
            WorldBillboardRenderer.quad(renderer, basis, entry.frameAnchor(), fx - t, fy + fh, fw + t * 2.0, t, entry.worldScale(), c);
            WorldBillboardRenderer.quad(renderer, basis, entry.frameAnchor(), fx + fw, fy - t, t, fh + t * 2.0, entry.worldScale(), c);
        }

        WorldBillboardRenderer.roundedRect(
                renderer, basis, entry.anchor(), x, y, width, height, MATTE_LABEL_RADIUS, entry.worldScale(),
                withAlpha(0x000000, Math.round(MATTE_BACKDROP_ALPHA * entry.alpha()))
        );

        double cursorX = x + MATTE_LABEL_PAD_X;
        if (icon) {
            double iconY = y + (height - MATTE_ICON_SIZE) * 0.5;
            WorldBillboardRenderer.item(renderer, basis, entry.anchor(), itemSprite,
                    cursorX, iconY, MATTE_ICON_SIZE, entry.worldScale(), entry.alpha());
            cursorX += iconBlock;
        }
        double textY = y + (height - measuredTextHeight) * 0.5;
        double textCursor = cursorX;
        for (int i = 0; i < info.safeParts.length; i++) {
            WorldBillboardRenderer.text(renderer, basis, labelRenderer, info.safeParts[i], entry.anchor(),
                    textCursor, textY, textScale, entry.worldScale(), info.partColors[i], entry.alpha(), false);
            textCursor += info.worldPartWidths[i];
        }
    }

    @Override
    public void onDisable() {
        nearbyItems.clear();
        worldEntries.clear();
        infoCache.clear();
        nearbyLevel = null;
        nearbyPlayer = null;
        nearbyTick = Long.MIN_VALUE;
    }

    private static TextRenderer dropLabelRenderer(TextRenderer fallback) {
        return BuiltinFontCatalog.INTER_MEDIUM.renderer(fallback);
    }

    private static Component formatReferenceItemLabel(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Component.empty();
        MutableComponent text = Component.literal(stack.getHoverName().getString());
        if (stack.getCount() > 1) {
            text.append(Component.literal(" [").withStyle(ChatFormatting.WHITE));
            text.append(Component.literal(Integer.toString(stack.getCount())).withStyle(ChatFormatting.RED));
            text.append(Component.literal("x").withStyle(ChatFormatting.GRAY));
            text.append(Component.literal("]").withStyle(ChatFormatting.WHITE));
        }
        return RuntimeTextLayout.singleLine(text);
    }

    private static int withAlpha(int rgb, int alpha) {
        int a = Math.max(0, Math.min(255, alpha));
        return (rgb & 0x00FFFFFF) | (a << 24);
    }

    private static int scaleAlpha(int argb, float alpha) {
        int source = (argb >>> 24) & 0xFF;
        return withAlpha(argb, Math.round(source * Math.max(0.0f, Math.min(1.0f, alpha))));
    }

    private boolean passesDistanceFilter(DropInfo info, double dist) {
        if (info.special) {
            return true;
        }

        if (limitCommonDistanceValue.get()) {
            if (info.enchanted) {
                return true;
            }
            if (info.common) {
                return dist <= commonMaxDistanceValue.get();
            }
        }

        return true;
    }

    /**
     * Item entities inside the 64 block box around the player. The entity sections are walked once per
     * game tick instead of once per rendered frame per pass.
     */
    private java.util.List<ItemEntity> nearbyItems() {
        var level = mc.level;
        var player = mc.player;
        long tick = level.getGameTime();
        if (level != nearbyLevel || player != nearbyPlayer || tick != nearbyTick) {
            nearbyLevel = level;
            nearbyPlayer = player;
            nearbyTick = tick;
            nearbyItems.clear();
            nearbyItems.addAll(level.getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(64), e -> true));
        }
        return nearbyItems;
    }

    /**
     * Everything derived from the item stack and module settings (registry id, illegal/top-enchant
     * checks, label components, styled parts). Rebuilt when the stack instance or count changes, and every
     * {@link #INFO_TTL_TICKS} ticks so setting edits still show up promptly.
     */
    private DropInfo infoFor(ItemEntity item, long tick) {
        ItemStack stack = item.getItem();
        int id = item.getId();
        DropInfo info = infoCache.get(id);
        if (info != null && info.stack == stack && info.count == stack.getCount() && tick < info.expiresTick) {
            info.lastSeenTick = tick;
            return info;
        }
        if (tick - infoSweepTick >= INFO_SWEEP_TICKS) {
            infoSweepTick = tick;
            infoCache.values().removeIf(cached -> tick - cached.lastSeenTick > INFO_SWEEP_TICKS);
        }
        info = buildInfo(stack, tick);
        infoCache.put(id, info);
        return info;
    }

    private DropInfo buildInfo(ItemStack stack, long tick) {
        DropInfo info = new DropInfo();
        info.stack = stack;
        info.count = stack.getCount();
        info.lastSeenTick = tick;
        info.expiresTick = tick + INFO_TTL_TICKS;

        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().toLowerCase();
        boolean special = specialItemsValue.get().contains(id);
        boolean illegal = IllegalItemUtil.isIllegal(stack);
        boolean top = !illegal && TopEnchantUtil.hasTopEnchant(stack);
        Rarity rarity = stack.getOrDefault(DataComponents.RARITY, Rarity.COMMON);

        info.special = special;
        info.enchanted = stack.isEnchanted();
        info.common = rarity == Rarity.COMMON;

        if (illegal) {
            info.sortPriority = 1000;
            info.color = IllegalItemUtil.illegalColor();
        } else if (top) {
            info.sortPriority = 900;
            info.color = TopEnchantUtil.topColor();
        } else if (special) {
            info.sortPriority = 800;
            info.color = specialColorValue.getArgb();
        } else {
            info.sortPriority = switch (rarity) {
                case EPIC -> 400;
                case RARE -> 300;
                case UNCOMMON -> 200;
                case COMMON -> 100;
                default -> 100;
            };
            info.color = rgbaToARGB(RarityColorUtil.INSTANCE.getRarityColor(stack));
        }

        Component text = formatReferenceItemLabel(stack);
        info.plainText = text.getString();
        java.util.List<TextRenderUtil.Part> parts = TextRenderUtil.flattenStyled(text, info.color);
        info.safeParts = new String[parts.size()];
        info.partColors = new int[parts.size()];
        info.worldPartWidths = new double[parts.size()];
        for (int i = 0; i < parts.size(); i++) {
            info.safeParts[i] = RuntimeTextLayout.singleLine(parts.get(i).text());
            info.partColors[i] = parts.get(i).color();
        }
        return info;
    }

    private WorldUiPresentationService.Snapshot resolvePresentation(double distance) {
        double projectionYScale = RenderState.worldProjection.m11();
        ViewportContext viewport = ViewportContext.current();
        double logicalHeight = viewport != null ? viewport.height() : 0.0;
        return WorldUiPresentationService.resolve(
                presentationMode.get(), distance, WORLD_PRESENTATION_POLICY, projectionYScale, logicalHeight,
                worldSize.get(), dynamicWorldScale.get(), dynamicWorldScaleCoefficient.get());
    }

    private Vec3 presentationCameraPosition(float tickDelta) {
        Vec3 captured = CombatantWorldMatrices.cameraPosition();
        if (captured != null) return captured;
        if (mc.gameRenderer != null && mc.gameRenderer.mainCamera() != null) {
            return mc.gameRenderer.mainCamera().position();
        }
        return mc.player != null ? mc.player.getEyePosition(tickDelta) : Vec3.ZERO;
    }

    @Override
    public java.util.List<ConfigValue<?>> getConfigValues() {
        java.util.List<ConfigValue<?>> list = new java.util.ArrayList<>(super.getConfigValues());
        list.add(TopEnchantUtil.topColorValue());
        return list;
    }

    private boolean isMatteMode() {
        return "Matte".equalsIgnoreCase(modeValue.get());
    }

    private boolean isOverlayMode() {
        return isMatteMode();
    }

    private int rgbaToARGB(float[] c) {
        int r = (int) (c[0] * 255);
        int g = (int) (c[1] * 255);
        int b = (int) (c[2] * 255);
        int a = (int) (c[3] * 255);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private Vec3 obtainEntityLerpedPos(Entity e, float f) {
        try {
            return e.getPosition(f);
        } catch (NoSuchMethodError ex) {
            if (e instanceof IEntity access) {
                Vec3 last = access.get$InstantRenderPos();
                return last.lerp(e.position(), f);
            }
            return e.position();
        }
    }


    private record DropOverlayEntry(int sortPriority, double distSq, ScreenSpaceOverlay2D.ScreenRect rect, int color,
                                    DropLabelEntry matteLabel, ScreenSpaceOverlay2D.LabelEntry label, float alpha) {
    }

    private record DropVanillaEntry(int sortPriority, double distSq, ScreenSpaceOverlay2D.LabelEntry label, float alpha) {
    }

    private record DropWorldEntry(int sortPriority,
                                  double distSq,
                                  Vec3 anchor,
                                  Vec3 frameAnchor,
                                  double frameWorldWidth,
                                  double frameWorldHeight,
                                  double worldScale,
                                  float alpha,
                                  ItemStack stack,
                                  DropInfo info) {
    }

    private record DropLabelEntry(ItemStack stack, DropInfo info, double x, double y, double width, double height,
                                  boolean icon, double iconX, double iconY, double textX, double textY, int color) {
    }

    /** Cached per-stack presentation data; see {@link #infoFor}. Only touched on the render thread. */
    private static final class DropInfo {
        ItemStack stack;
        int count;
        long lastSeenTick;
        long expiresTick;
        boolean special;
        boolean enchanted;
        boolean common;
        int sortPriority;
        int color;
        String plainText;
        String[] safeParts;
        int[] partColors;
        double[] worldPartWidths;
        double worldTextWidth;
        TextRenderer worldWidthRenderer;
        double screenTextWidth;
        TextRenderer screenWidthRenderer;
    }
}

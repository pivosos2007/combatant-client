/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.visuals;

import combatant.client.config.values.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
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
import combatant.client.features.module.HudPhase;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.mixininterface.IEntity;
import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.render.engine.text.VanillaTextRenderer;
import combatant.client.render.helpers.MatteHudStyle;
import combatant.client.render.helpers.ScreenProjection;
import combatant.client.render.helpers.ScreenSpaceOverlay2D;
import combatant.client.util.item.IllegalItemUtil;
import combatant.client.util.item.RarityColorUtil;
import combatant.client.util.item.TopEnchantUtil;

//todo Description
@ModuleInfo(id = "dropesp", displayName = "DropESP", category = ModuleCategory.VISUALS)
public class DropESP extends Module {

    private static final String SETTING_LIMIT_COMMON_DISTANCE = "limit_common_distance";
    private static final String SETTING_COMMON_MAX_DISTANCE = "common_max_distance";
    private static final String SETTING_SPECIAL_ITEMS_COLOR = "special_items_color";
    private static final String SETTING_ILLEGAL_ENCHANT_COLOR = "illegal_enchant_color";
    private static final String SETTING_TOP_ENCHANT_IGNORE_LIST = "top_enchant_ignore_list";
    private static final String SETTING_SPECIAL_ITEM_IDS = "special_item_ids";
    private static final String SETTING_MODE = "mode";
    private static final String SETTING_TEXT_SHADOW = "text_shadow";
    private static final String SETTING_FRAME = "frame";
    private static final String SETTING_ITEM_ICON = "item_icon";
    private static final double ITEM_BOX_EXPAND_XZ = 0.05;
    private static final double ITEM_BOX_EXPAND_TOP = 0.15;
    private static final double MATTE_LABEL_PAD_X = 3.5;
    private static final double MATTE_LABEL_PAD_Y = 2.0;
    private static final double MATTE_ICON_SIZE = 14.0;
    private static final double MATTE_ICON_GAP = 4.0;
    private static final float MATTE_ICON_SCALE = (float) (MATTE_ICON_SIZE / 16.0);
    private final Minecraft mc = Minecraft.getInstance();
    private final ModeValue modeValue = modeSetting("dropEspMode", SETTING_MODE, "Matte", "Vanilla", "New", "Matte");
    private final BooleanValue limitCommonDistanceValue = bool("dropEspLimitCommonDistance", SETTING_LIMIT_COMMON_DISTANCE, true);
    private final NumberValue<Integer> commonMaxDistanceValue =
            visibleWhen(num("dropEspCommonMaxDistance", SETTING_COMMON_MAX_DISTANCE, 32, 4, 256), limitCommonDistanceValue::get);
    private final BooleanValue textShadowValue =
            visibleWhen(bool("dropEspTextShadow", SETTING_TEXT_SHADOW, true), this::isNewMode);
    private final BooleanValue frameValue =
            visibleWhen(bool("dropEspFrame", SETTING_FRAME, true), this::isOverlayMode);
    private final BooleanValue itemIconValue =
            visibleWhen(bool("dropEspItemIcon", SETTING_ITEM_ICON, true), this::isMatteMode);
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

    @Override
    public HudPhase getHudPhase() {
        return HudPhase.FIRST;
    }

    @Override
    public void onRenderHudEngine(Renderer2D renderer, TextRenderer textRenderer, GuiGraphicsExtractor ctx, float tickDelta) {
        if (!isEnabled() || mc.level == null || mc.player == null) return;

        boolean matteMode = isMatteMode();
        boolean overlayMode = isOverlayMode();
        TextRenderer labelRenderer = overlayMode ? ScreenSpaceOverlay2D.labelRenderer(textRenderer) : VanillaTextRenderer.INSTANCE;
        boolean measureStarted = false;
        java.util.List<DropOverlayEntry> overlayEntries = new java.util.ArrayList<>();
        java.util.List<DropVanillaEntry> vanillaEntries = new java.util.ArrayList<>();
        double textScale = overlayMode ? ScreenSpaceOverlay2D.TEXT_SCALE : ScreenSpaceOverlay2D.VANILLA_TEXT_SCALE;

        if (!labelRenderer.isBuilding()) {
            labelRenderer.begin(textScale, true, false);
            measureStarted = true;
        }

        Vec3 cameraPos = mc.player.getEyePosition(tickDelta);
        for (ItemEntity item : mc.level.getEntitiesOfClass(
                ItemEntity.class,
                mc.player.getBoundingBox().inflate(64),
                e -> true
        )) {

            Vec3 pos = obtainEntityLerpedPos(item, tickDelta);

            double distSq = pos.distanceToSqr(cameraPos);
            double dist = Math.sqrt(distSq);
            if (!passesDistanceFilter(item, dist)) continue;

            ItemStack stack = item.getItem();
            String text = stack.getHoverName().getString() + " x" + stack.getCount();
            int color = resolveDisplayColor(stack);
            int sortPriority = resolveSortPriority(stack);

            if (overlayMode) {
                ScreenSpaceOverlay2D.ScreenRect rect = ScreenSpaceOverlay2D.projectEntityBox(
                        item,
                        pos,
                        tickDelta,
                        ITEM_BOX_EXPAND_XZ,
                        ITEM_BOX_EXPAND_TOP
                );
                if (rect == null) continue;

                DropLabelEntry matteLabel = matteMode
                        ? createMatteDropLabel(labelRenderer, stack, text, color, rect, itemIconValue.get())
                        : null;
                ScreenSpaceOverlay2D.LabelEntry label = matteMode
                        ? null
                        : ScreenSpaceOverlay2D.createCenteredLabel(labelRenderer, text, color, rect);
                overlayEntries.add(new DropOverlayEntry(sortPriority, distSq, rect, color, matteLabel, label));
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

                double x = screen.x - (labelRenderer.getWidth(text, true) / 2.0);
                double y = screen.y;
                vanillaEntries.add(new DropVanillaEntry(sortPriority, distSq, ScreenSpaceOverlay2D.labelAt(text, x, y, color)));
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
                renderSingleLabel(labelRenderer, entry.label(), true, textScale);
                Renderer2D.flushBatch(Renderer2D.FlushReason.EXPLICIT);
            }
        }
    }

    private void renderOverlayEntryPass(Renderer2D renderer,
                                        TextRenderer labelRenderer,
                                        DropOverlayEntry entry,
                                        boolean matteMode,
                                        double textScale) {
        ScreenSpaceOverlay2D.ScreenRect rect = entry.rect();
        if (frameValue.get()) {
            if (matteMode) {
                MatteHudStyle.drawFrame(renderer, rect.minX(), rect.minY(), rect.width(), rect.height(), entry.color(), 1.0f);
            } else {
                ScreenSpaceOverlay2D.drawFrame(renderer, rect, entry.color());
            }
        }

        if (matteMode) {
            DropLabelEntry label = entry.matteLabel();
            if (label == null) return;
            MatteHudStyle.drawPlate(renderer, label.x(), label.y(), label.width(), label.height(), 2.0f, 1.0f);
            if (label.icon()) {
                renderer.item(label.stack(), label.iconX(), label.iconY(), MATTE_ICON_SCALE, 0, Renderer2D.ITEM_OVERLAY_NONE, null);
            }
            renderSingleDropLabel(labelRenderer, label, textScale);
        } else {
            ScreenSpaceOverlay2D.LabelEntry label = entry.label();
            if (label == null) return;
            if (textShadowValue.get()) {
                ScreenSpaceOverlay2D.renderLabelBackplate(renderer, label);
            }
            renderSingleLabel(labelRenderer, label, false, textScale);
        }

        Renderer2D.flushBatch(Renderer2D.FlushReason.EXPLICIT);
    }

    private void renderSingleDropLabel(TextRenderer labelRenderer, DropLabelEntry label, double textScale) {
        boolean renderStarted = false;
        if (!labelRenderer.isBuilding()) {
            labelRenderer.begin(textScale);
            renderStarted = true;
        }
        try {
            labelRenderer.render(label.text(), label.textX(), label.textY(), new RenderColor(label.color()), false);
        } finally {
            if (renderStarted) labelRenderer.end();
        }
    }

    private void renderSingleLabel(TextRenderer labelRenderer,
                                   ScreenSpaceOverlay2D.LabelEntry label,
                                   boolean shadow,
                                   double textScale) {
        boolean renderStarted = false;
        if (!labelRenderer.isBuilding()) {
            labelRenderer.begin(textScale);
            renderStarted = true;
        }
        try {
            ScreenSpaceOverlay2D.renderLabels(labelRenderer, java.util.List.of(label), shadow);
        } finally {
            if (renderStarted) labelRenderer.end();
        }
    }

    private DropLabelEntry createMatteDropLabel(TextRenderer labelRenderer,
                                                ItemStack stack,
                                                String text,
                                                int color,
                                                ScreenSpaceOverlay2D.ScreenRect rect,
                                                boolean icon) {
        double textWidth = labelRenderer.getWidth(text, false);
        double textHeight = labelRenderer.getHeight(false);
        double iconBlock = icon ? MATTE_ICON_SIZE + MATTE_ICON_GAP : 0.0;
        double width = textWidth + iconBlock + MATTE_LABEL_PAD_X * 2.0;
        double height = Math.max(textHeight + MATTE_LABEL_PAD_Y * 2.0, icon ? MATTE_ICON_SIZE + MATTE_LABEL_PAD_Y * 2.0 : 0.0);
        double x = rect.minX() + (rect.width() - width) * 0.5;
        double y = rect.minY() - height - 4.0;
        x = Math.floor(x + 0.5);
        y = Math.floor(y + 0.5);
        double iconX = x + MATTE_LABEL_PAD_X;
        double iconY = y + (height - MATTE_ICON_SIZE) * 0.5;
        double textX = x + MATTE_LABEL_PAD_X + iconBlock;
        double textY = y + (height - textHeight) * 0.5;
        return new DropLabelEntry(stack, text, x, y, width, height, icon, iconX, iconY, textX, textY, color);
    }

    private boolean passesDistanceFilter(ItemEntity item, double dist) {

        String id = BuiltInRegistries.ITEM.getKey(item.getItem().getItem()).toString().toLowerCase();

        if (specialItemsValue.get().contains(id)) {
            return true;
        }

        if (limitCommonDistanceValue.get()) {

            if (item.getItem().isEnchanted()) {
                return true;
            }

            var rarity = item.getItem().getOrDefault(
                    DataComponents.RARITY,
                    Rarity.COMMON
            );

            if (rarity == Rarity.COMMON) {
                return dist <= commonMaxDistanceValue.get();
            }
        }

        return true;
    }

    @Override
    public java.util.List<ConfigValue<?>> getConfigValues() {
        java.util.List<ConfigValue<?>> list = new java.util.ArrayList<>(super.getConfigValues());
        list.add(TopEnchantUtil.topColorValue());
        return list;
    }

    private boolean isNewMode() {
        return "New".equalsIgnoreCase(modeValue.get());
    }

    private boolean isMatteMode() {
        return "Matte".equalsIgnoreCase(modeValue.get());
    }

    private boolean isOverlayMode() {
        return isNewMode() || isMatteMode();
    }

    private int resolveSortPriority(ItemStack stack) {
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().toLowerCase();
        if (IllegalItemUtil.isIllegal(stack)) {
            return 1000;
        }
        if (TopEnchantUtil.hasTopEnchant(stack)) {
            return 900;
        }
        if (specialItemsValue.get().contains(id)) {
            return 800;
        }

        Rarity rarity = stack.getOrDefault(DataComponents.RARITY, Rarity.COMMON);
        return switch (rarity) {
            case EPIC -> 400;
            case RARE -> 300;
            case UNCOMMON -> 200;
            case COMMON -> 100;
            default -> 100;
        };
    }

    private int resolveDisplayColor(ItemStack stack) {
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().toLowerCase();
        if (IllegalItemUtil.isIllegal(stack)) {
            return IllegalItemUtil.illegalColor();
        }
        if (TopEnchantUtil.hasTopEnchant(stack)) {
            return TopEnchantUtil.topColor();
        }
        if (specialItemsValue.get().contains(id)) {
            return specialColorValue.getArgb();
        }
        float[] c = RarityColorUtil.INSTANCE.getRarityColor(stack);
        return rgbaToARGB(c);
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
                                    DropLabelEntry matteLabel, ScreenSpaceOverlay2D.LabelEntry label) {
    }

    private record DropVanillaEntry(int sortPriority, double distSq, ScreenSpaceOverlay2D.LabelEntry label) {
    }

    private record DropLabelEntry(ItemStack stack, String text, double x, double y, double width, double height,
                                  boolean icon, double iconX, double iconY, double textX, double textY, int color) {
    }
}

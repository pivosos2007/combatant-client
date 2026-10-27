/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.settings;

import combatant.client.config.values.ColorListValue;
import combatant.client.config.values.ColorValue;
import combatant.client.config.values.RGBAColorValue;
import combatant.client.config.values.RGBColorValue;
import combatant.client.features.gui.clickgui.ClickGuiRenderer;
import combatant.client.features.gui.clickgui.layout.screen.settings.SettingsGuiPalette;
import combatant.client.features.gui.clickgui.layout.screen.settings.render.LayoutRender2D;
import combatant.client.features.gui.clickgui.sound.GuiSound;
import combatant.client.features.gui.clickgui.util.ClickGuiI18n;
import combatant.client.input.InputKeys;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.render.helpers.ScissorFunction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Picker-backed editor for a block/item id list where every entry has its own colour.
 * <p>
 * The grid on the left is the regular block/item picker (click adds or removes an entry). The detail
 * panel shows the focused entry with its colour widget, a "use default colour" action and the full
 * entry list (icon, name, swatch, remove) plus a clear-all action.
 */
public final class ColorListSetting extends TextListSetting implements PickerDetailOwner {

    private static final float DETAIL_W = 210f * 2f;
    private static final float PANEL_PAD = 10f;
    private static final float HEADER_H = 112f;
    private static final float ROW_H = 28f;
    private static final float ROW_GAP = 3f;
    private static final float CHIP_H = 22f;
    private static final float SCROLL_STEP = 18f;
    private static final float SCROLLBAR_W = 3.25f;

    private final ColorListValue colors;
    private final Map<String, EntryEditor> editors = new HashMap<>();
    private final Map<String, Resolved> resolved = new HashMap<>();
    private final List<Hit> hits = new ArrayList<>();

    private String focusedId;
    private String lastPickerFocus;
    private float detailScroll;
    private float detailMaxScroll;
    private boolean dirty;

    private float viewX, viewY, viewW, viewH;
    private ColorSetting colorSetting;
    private float colorX, colorY, colorW, colorH;

    public ColorListSetting(String name, ColorListValue colors, PickerMode pickerMode) {
        super(name, colors, pickerMode);
        this.colors = colors;
    }

    private static boolean inside(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private static String fit(TextRenderer font, String text, float size, float width) {
        return ClickGuiRenderer.fitText(font, text == null ? "" : text, size, Math.max(1f, width));
    }

    private static String norm(String id) {
        if (id == null) return null;
        String s = id.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) return null;
        return s.contains(":") ? s : "minecraft:" + s;
    }

    @Override
    public void load(Object o) {
        super.load(o);
        editors.clear();
        resolved.clear();
    }

    @Override
    public float pickerDetailWidth() {
        return DETAIL_W;
    }

    @Override
    public void onPickerFocusChanged(String id) {
        String n = norm(id);
        if (n == null) return;
        focusedId = n;
        lastPickerFocus = n;
        detailScroll = 0f;
    }

    @Override
    public void renderPickerDetails(float x, float y, float w, float h, String focusedId, float mouseX, float mouseY) {
        UnifiedSettingsSkin.syncTheme();
        hits.clear();
        colorSetting = null;
        adoptPickerFocus(focusedId);
        String id = resolveFocus();

        SettingsGuiPalette palette = SettingsGuiPalette.current();
        LayoutRender2D.roundedQuad(x, y, w, h, 12f,
                palette.contentPlaneTop(), palette.contentPlaneTop(),
                palette.contentPlaneBottom(), palette.contentPlaneBottom());
        LayoutRender2D.roundedStroke(x, y, w, h, 12f, 0.42f, palette.glassEdgeSoft());

        drawHeader(x, y, w, id, mouseX, mouseY, palette);

        viewX = x + PANEL_PAD;
        viewY = y + HEADER_H;
        viewH = Math.max(1f, h - HEADER_H - PANEL_PAD);
        float reserve = detailMaxScroll > 0.5f ? SCROLLBAR_W + 5f : 0f;
        viewW = w - PANEL_PAD * 2f - reserve;

        boolean clipped = ScissorFunction.pushRaw(viewX - 1f, viewY - 1f, viewW + 2f, viewH + 2f);
        float top = viewY - detailScroll;
        float cy = drawContent(top, id, mouseX, mouseY);
        if (clipped) ScissorFunction.pop();

        detailMaxScroll = Math.max(0f, cy - top - viewH);
        detailScroll = Math.max(0f, Math.min(detailScroll, detailMaxScroll));
        drawScrollbar(x, w);
        flushSave();
    }

    @Override
    public boolean mouseClickedPickerDetails(float mouseX, float mouseY, int button) {
        if (button != InputKeys.MOUSE_LEFT) return false;
        boolean inView = inside(mouseX, mouseY, viewX, viewY, viewW, viewH);

        for (Hit hit : new ArrayList<>(hits)) {
            if (!inside(mouseX, mouseY, hit.x, hit.y, hit.w, hit.h)) continue;
            if (hit.scrolled && !inView) continue;
            handleAction(hit.action, hit.id);
            return true;
        }

        if (colorSetting != null && inView && inside(mouseX, mouseY, colorX, colorY, colorW, colorH)) {
            colorSetting.mouseClicked(mouseX, mouseY, button);
            return true;
        }
        return false;
    }

    @Override
    public void mouseReleasedPickerDetails(float mouseX, float mouseY, int button) {
        if (colorSetting != null) colorSetting.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolledPickerDetails(float mouseX, float mouseY, double delta) {
        if (detailMaxScroll <= 0.5f) return true;
        detailScroll = Math.max(0f, Math.min(detailScroll - (float) (delta * SCROLL_STEP), detailMaxScroll));
        return true;
    }

    // ------------------------------------------------------------------ drawing

    private void drawHeader(float x, float y, float w, String id, float mx, float my, SettingsGuiPalette palette) {
        TextRenderer titleFont = UnifiedSettingsSkin.fontSemibold();
        TextRenderer font = UnifiedSettingsSkin.fontRegular();

        float chipW = 76f;
        float chipX = x + w - PANEL_PAD - chipW;
        boolean hasEntries = !colors.get().isEmpty();
        ClickGuiRenderer.drawText(titleFont,
                fit(titleFont, ClickGuiI18n.tr("clickgui.color_list.title", "Entry colours"), 18f, chipX - x - PANEL_PAD * 2f),
                x + PANEL_PAD, y + 11f, 18f, UnifiedSettingsSkin.TEXT_PRIMARY, false);
        ClickGuiRenderer.drawText(font,
                fit(font, ClickGuiI18n.tr("clickgui.color_list.subtitle", "Each entry can use its own colour"), 10.5f, chipX - x - PANEL_PAD * 2f),
                x + PANEL_PAD, y + 30f, 10.5f, UnifiedSettingsSkin.TEXT_MUTED, false);
        if (hasEntries) {
            float cy = y + 12f;
            chip(chipX, cy, chipW, CHIP_H, ClickGuiI18n.tr("clickgui.color_list.clear", "Clear"),
                    inside(mx, my, chipX, cy, chipW, CHIP_H), true);
            hits.add(new Hit(Action.CLEAR, null, chipX, cy, chipW, CHIP_H, false));
        }

        float dividerY = y + 47f;
        LayoutRender2D.rectQuad(x + PANEL_PAD, dividerY, w - PANEL_PAD * 2f, 0.5f,
                palette.moduleDividerStart(), palette.moduleDividerEnd(),
                palette.moduleDividerEnd(), palette.moduleDividerStart());

        if (id == null) {
            String line1 = ClickGuiI18n.tr("clickgui.color_list.empty_title", "Nothing selected yet.");
            String line2 = ClickGuiI18n.tr("clickgui.color_list.empty_hint", "Click blocks or items on the left to add them.");
            ClickGuiRenderer.drawText(font, fit(font, line1, 12f, w - PANEL_PAD * 2f), x + PANEL_PAD, y + 62f, 12f, UnifiedSettingsSkin.TEXT_PRIMARY, false);
            ClickGuiRenderer.drawText(font, fit(font, line2, 11f, w - PANEL_PAD * 2f), x + PANEL_PAD, y + 80f, 11f, UnifiedSettingsSkin.TEXT_MUTED, false);
            return;
        }

        Resolved res = resolve(id);
        float iconBox = 46f;
        float iconX = x + PANEL_PAD;
        float iconY = y + 57f;
        LayoutRender2D.roundedQuad(iconX, iconY, iconBox, iconBox, 8f,
                palette.moduleCardTop(), palette.moduleCardTopStrong(), palette.moduleCardBottom(), palette.moduleCardBottomStrong());
        LayoutRender2D.roundedStroke(iconX, iconY, iconBox, iconBox, 8f, 0.38f, palette.glassEdgeSoft());
        if (!res.stack.isEmpty()) {
            float scale = 1.85f;
            float px = 16f * scale;
            ClickGuiRenderer.queuePickerIcon(res.stack, iconX + (iconBox - px) * 0.5f, iconY + (iconBox - px) * 0.5f,
                    scale, iconX, iconY, iconBox, iconBox);
        }

        boolean inList = colors.get().contains(id);
        float textX = iconX + iconBox + 11f;
        float buttonW = inList ? 76f : 68f;
        float buttonX = x + w - PANEL_PAD - buttonW;
        float buttonY = y + 69f;
        ClickGuiRenderer.drawText(titleFont, fit(titleFont, res.name, 15f, buttonX - textX - 10f), textX, y + 60f, 15f,
                UnifiedSettingsSkin.TEXT_PRIMARY, false);
        ClickGuiRenderer.drawText(font, fit(font, id, 11.5f, buttonX - textX - 10f), textX, y + 80f, 11.5f,
                UnifiedSettingsSkin.TEXT_MUTED, false);

        String label = inList
                ? ClickGuiI18n.tr("clickgui.color_list.remove", "Remove")
                : ClickGuiI18n.tr("clickgui.color_list.add", "Add");
        chip(buttonX, buttonY, buttonW, CHIP_H, label, inside(mx, my, buttonX, buttonY, buttonW, CHIP_H), inList);
        hits.add(new Hit(inList ? Action.REMOVE : Action.ADD, id, buttonX, buttonY, buttonW, CHIP_H, false));
    }

    /** Draws the scrolling column and returns the y just below the last element. */
    private float drawContent(float top, String id, float mx, float my) {
        TextRenderer font = UnifiedSettingsSkin.fontRegular();
        float cx = viewX;
        float cy = top;

        if (id != null && colors.get().contains(id)) {
            EntryEditor editor = editors.computeIfAbsent(id, EntryEditor::new);
            editor.sync(id);
            float rowH = editor.setting.getHeight();
            if (cy + rowH >= viewY - 2f && cy <= viewY + viewH + 2f) {
                editor.setting.render(cx, cy, viewW, mx, my);
                colorSetting = editor.setting;
                colorX = cx;
                colorY = cy;
                colorW = viewW;
                colorH = rowH;
            }
            cy += rowH + 4f;

            if (colors.hasOverride(id)) {
                float chipW = 150f;
                chip(cx, cy, chipW, CHIP_H, ClickGuiI18n.tr("clickgui.color_list.reset", "Use default colour"),
                        inside(mx, my, cx, cy, chipW, CHIP_H) && inside(mx, my, viewX, viewY, viewW, viewH), false);
                hits.add(new Hit(Action.RESET, id, cx, cy, chipW, CHIP_H, true));
                cy += CHIP_H + 6f;
            }
        } else if (id != null) {
            String hint = ClickGuiI18n.tr("clickgui.color_list.not_added", "Not in the list yet. Press Add to give it a colour.");
            ClickGuiRenderer.drawText(font, fit(font, hint, 11f, viewW), cx, cy + 2f, 11f, UnifiedSettingsSkin.TEXT_MUTED, false);
            cy += 20f;
        }

        Set<String> ids = colors.get();
        if (ids.isEmpty()) return cy;

        cy += 6f;
        ClickGuiRenderer.drawText(UnifiedSettingsSkin.fontSemibold(),
                ClickGuiI18n.tr("clickgui.color_list.entries", "Entries (%d)", ids.size()),
                cx, cy, 12f, UnifiedSettingsSkin.TEXT_PRIMARY, false);
        cy += 20f;

        for (String entry : ids) {
            if (cy + ROW_H >= viewY - 2f && cy <= viewY + viewH + 2f) {
                drawRow(cx, cy, viewW, entry, entry.equals(id), mx, my);
            }
            cy += ROW_H + ROW_GAP;
        }
        return cy;
    }

    private void drawRow(float x, float y, float w, String id, boolean focused, float mx, float my) {
        TextRenderer font = UnifiedSettingsSkin.fontRegular();
        boolean inView = inside(mx, my, viewX, viewY, viewW, viewH);
        boolean hover = inView && inside(mx, my, x, y, w, ROW_H);
        int bg = focused
                ? UnifiedSettingsSkin.ACCENT_SOFT
                : UnifiedSettingsSkin.withAlpha(UnifiedSettingsSkin.TEXT_MUTED, hover ? 34 : 16);
        ClickGuiRenderer.drawRoundedRect(x, y, w, ROW_H, 6f, bg);
        if (focused) {
            ClickGuiRenderer.drawRoundedRectStroke(x, y, w, ROW_H, 6f, 0.35f, UnifiedSettingsSkin.ACCENT);
        }

        Resolved res = resolve(id);
        float icon = 16f * 1.3f;
        if (!res.stack.isEmpty()) {
            ClickGuiRenderer.queuePickerIcon(res.stack, x + 7f, y + (ROW_H - icon) * 0.5f, 1.3f, viewX, viewY, viewW, viewH);
        }

        float removeSize = 18f;
        float removeX = x + w - 7f - removeSize;
        float removeY = y + (ROW_H - removeSize) * 0.5f;
        float swatchW = 30f;
        float swatchH = 14f;
        float swatchX = removeX - 8f - swatchW;
        float swatchY = y + (ROW_H - swatchH) * 0.5f;
        float textX = x + 7f + icon + 8f;
        ClickGuiRenderer.drawText(font, fit(font, res.name, 12f, swatchX - textX - 8f), textX,
                y + (ROW_H - ClickGuiRenderer.textHeight(font, 12f)) * 0.5f, 12f, UnifiedSettingsSkin.TEXT_PRIMARY, false);

        swatch(swatchX, swatchY, swatchW, swatchH, colors.argbFor(id), colors.hasOverride(id));

        boolean removeHover = inView && inside(mx, my, removeX, removeY, removeSize, removeSize);
        ClickGuiRenderer.drawRoundedRect(removeX, removeY, removeSize, removeSize, 4f,
                removeHover ? UnifiedSettingsSkin.withAlpha(0xFFFF5555, 70) : UnifiedSettingsSkin.withAlpha(0xFFFF5555, 24));
        float pad = 5.5f;
        int xc = removeHover ? 0xFFFFFFFF : UnifiedSettingsSkin.TEXT_MUTED;
        ClickGuiRenderer.drawLine(removeX + pad, removeY + pad, removeX + removeSize - pad, removeY + removeSize - pad, xc);
        ClickGuiRenderer.drawLine(removeX + removeSize - pad, removeY + pad, removeX + pad, removeY + removeSize - pad, xc);

        hits.add(new Hit(Action.REMOVE, id, removeX, removeY, removeSize, removeSize, true));
        hits.add(new Hit(Action.FOCUS, id, x, y, w, ROW_H, true));
    }

    private void swatch(float x, float y, float w, float h, int argb, boolean custom) {
        ClickGuiRenderer.drawRoundedRect(x, y, w, h, 3.5f, 0xFF14171D);
        ClickGuiRenderer.drawRoundedRect(x, y, w, h, 3.5f, argb);
        ClickGuiRenderer.drawRoundedRectStroke(x, y, w, h, 3.5f, 0.45f,
                custom ? UnifiedSettingsSkin.withAlpha(0xFFFFFFFF, 150) : UnifiedSettingsSkin.withAlpha(0xFFFFFFFF, 60));
    }

    private void chip(float x, float y, float w, float h, String text, boolean hover, boolean destructive) {
        int bg = destructive ? UnifiedSettingsSkin.withAlpha(0xFFFF5555, 32) : UnifiedSettingsSkin.ACCENT_SOFT;
        int stroke = destructive ? UnifiedSettingsSkin.withAlpha(0xFFFF7777, 120) : UnifiedSettingsSkin.ACCENT;
        if (hover) bg = UnifiedSettingsSkin.mix(bg, UnifiedSettingsSkin.SURFACE_HOVER, 0.45f);
        ClickGuiRenderer.drawRoundedRect(x, y, w, h, 4f, bg);
        ClickGuiRenderer.drawRoundedRectStroke(x, y, w, h, 4f, 0.35f, stroke);
        TextRenderer font = UnifiedSettingsSkin.fontRegular();
        float size = 10.5f;
        String shown = fit(font, text, size, w - 8f);
        float tw = ClickGuiRenderer.textWidth(font, shown, size);
        float th = ClickGuiRenderer.textHeight(font, size);
        ClickGuiRenderer.drawText(font, shown, x + (w - tw) * 0.5f, y + (h - th) * 0.5f, size, UnifiedSettingsSkin.TEXT_PRIMARY, false);
    }

    private void drawScrollbar(float panelX, float panelW) {
        if (detailMaxScroll <= 0.5f) return;
        float trackX = panelX + panelW - PANEL_PAD - SCROLLBAR_W;
        float trackY = viewY + 1f;
        float trackH = Math.max(1f, viewH - 2f);
        ClickGuiRenderer.drawRoundedRect(trackX, trackY, SCROLLBAR_W, trackH, SCROLLBAR_W * 0.5f,
                UnifiedSettingsSkin.withAlpha(UnifiedSettingsSkin.TEXT_MUTED, 38));
        float ratio = viewH / (viewH + detailMaxScroll);
        float thumbH = Math.max(18f, trackH * ratio);
        float thumbY = trackY + Math.max(1f, trackH - thumbH) * (detailScroll / Math.max(1f, detailMaxScroll));
        ClickGuiRenderer.drawRoundedRect(trackX, thumbY, SCROLLBAR_W, thumbH, SCROLLBAR_W * 0.5f,
                UnifiedSettingsSkin.withAlpha(UnifiedSettingsSkin.TEXT_MUTED, 120));
    }

    // ------------------------------------------------------------------ state

    private void adoptPickerFocus(String passed) {
        String n = norm(passed);
        if (n != null && !n.equals(lastPickerFocus)) {
            focusedId = n;
            detailScroll = 0f;
        }
        lastPickerFocus = n;
    }

    private String resolveFocus() {
        if (focusedId != null && !focusedId.isBlank()) return focusedId;
        Set<String> ids = colors.get();
        if (ids.isEmpty()) return null;
        focusedId = ids.iterator().next();
        return focusedId;
    }

    private void handleAction(Action action, String id) {
        switch (action) {
            case CLEAR -> {
                editors.clear();
                setValueSet(new LinkedHashSet<>());
            }
            case ADD -> {
                if (id == null) return;
                Set<String> next = getValueSet();
                next.add(id);
                setValueSet(next);
                focusedId = id;
            }
            case REMOVE -> {
                if (id == null) return;
                Set<String> next = getValueSet();
                next.remove(id);
                editors.remove(id);
                setValueSet(next);
                if (id.equals(focusedId)) {
                    focusedId = next.isEmpty() ? null : next.iterator().next();
                }
            }
            case RESET -> {
                if (id == null) return;
                colors.clearOverride(id);
                dirty = true;
            }
            case FOCUS -> {
                if (id == null) return;
                if (!id.equals(focusedId)) detailScroll = 0f;
                focusedId = id;
            }
        }
        GuiSound.CHANGE_MODE.feedback();
    }

    private void flushSave() {
        if (!dirty) return;
        if (colorSetting != null && colorSetting.ui().busy()) return;
        dirty = false;
        if (getParent() != null) getParent().saveConfig();
    }

    private Resolved resolve(String id) {
        return resolved.computeIfAbsent(id, key -> {
            Identifier rl = Identifier.tryParse(key);
            if (rl == null) return new Resolved(ItemStack.EMPTY, key);
            Item item = BuiltInRegistries.ITEM.getValue(rl);
            Block block = BuiltInRegistries.BLOCK.getValue(rl);
            boolean hasBlock = block != Blocks.AIR;
            if (item == Items.AIR && hasBlock) item = block.asItem();
            ItemStack stack = item == Items.AIR ? ItemStack.EMPTY : item.getDefaultInstance();
            String name;
            if (getPickerMode() == PickerMode.BLOCKS && hasBlock) {
                name = block.getName().getString();
            } else if (!stack.isEmpty()) {
                name = stack.getHoverName().getString();
            } else {
                name = key;
            }
            return new Resolved(stack, name);
        });
    }

    private enum Action {
        CLEAR,
        ADD,
        REMOVE,
        RESET,
        FOCUS
    }

    private record Hit(Action action, String id, float x, float y, float w, float h, boolean scrolled) {
    }

    private record Resolved(ItemStack stack, String name) {
    }

    /**
     * Detached colour value shown by the standard colour widget. Edits are mirrored into the list entry
     * (creating its own colour); while an entry has no own colour the editor follows the default colour.
     */
    private final class EntryEditor {
        final ColorValue value;
        final ColorSetting setting;
        String synced;

        EntryEditor(String id) {
            String hex = colors.hexFor(id);
            value = colors.supportsAlpha()
                    ? new RGBAColorValue("color_list_entry", hex)
                    : new RGBColorValue("color_list_entry", hex);
            setting = new ColorSetting(ClickGuiI18n.tr("clickgui.color_list.entry_color", "Colour"), value);
            setting.setI18nEnabled(false, false);
            synced = value.get();
        }

        void sync(String id) {
            String current = value.get();
            if (!current.equals(synced)) {
                colors.setOverride(id, current);
                dirty = true;
            }
            String target = colors.hexFor(id);
            if (!target.equals(current) && !setting.ui().busy()) {
                value.set(target);
            }
            synced = value.get();
        }
    }
}

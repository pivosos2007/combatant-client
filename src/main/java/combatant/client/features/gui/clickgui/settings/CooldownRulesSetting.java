/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.settings;

import combatant.client.config.values.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import combatant.client.config.values.*;
import combatant.client.features.gui.clickgui.ClickGuiRenderer;
import combatant.client.features.gui.clickgui.layout.screen.settings.SettingsGuiPalette;
import combatant.client.features.gui.clickgui.layout.screen.settings.render.LayoutRender2D;
import combatant.client.features.gui.clickgui.util.ClickGuiI18n;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.render.helpers.ScissorFunction;
import combatant.client.util.wav.ClickGuiSounds;

import java.util.*;

/**
 * Picker-backed editor for per-item cooldown rules.
 * <p>
 * Grid clicks only focus an item. Adding/removing a rule and editing its fields happens in the
 * picker detail panel. The field rows intentionally reuse the regular settings renderer instead
 * of custom miniature buttons, and numeric values use the normal slider setting.
 */
public final class CooldownRulesSetting extends TextListSetting implements PickerDetailOwner {

    private static final float DETAIL_W = 210f * 2f;
    private static final float PANEL_PAD = 10f;
    private static final float HEADER_H = 112f;
    private static final float DETAIL_SCROLL_STEP = 18f;
    private static final float DETAIL_SCROLLBAR_W = 3.25f;

    private final ItemCooldownRulesValue rules;
    private final Ui ui = new Ui();
    private final Map<String, RuleEditor> editors = new HashMap<>();
    private String focusedItemId;
    private float detailScroll;
    private float detailMaxScroll;

    public CooldownRulesSetting(String name, ItemCooldownRulesValue rules) {
        super(name, bridgeValue(rules), TextListSetting.PickerMode.ITEMS);
        this.rules = rules;
        syncBridge();
    }

    private static SetValue bridgeValue(ItemCooldownRulesValue rules) {
        return new SetValue(rules != null ? rules.getName() + "_picker_items" : "cooldown_rules_picker_items",
                rules != null ? rules.getItemIds() : Set.of());
    }

    private static float computeSettingsContentHeight(List<Setting> settings) {
        if (settings == null || settings.isEmpty()) return 0f;
        float h = 0f;
        for (int i = 0; i < settings.size(); i++) {
            h += settings.get(i).getHeight();
            if (i + 1 < settings.size()) h += 2f;
        }
        return h;
    }

    private static Item resolveItem(String itemId) {
        try {
            Identifier id = Identifier.tryParse(itemId);
            if (id == null) return Items.AIR;
            return BuiltInRegistries.ITEM.getValue(id);
        } catch (Throwable ignored) {
            return Items.AIR;
        }
    }

    private static String fit(TextRenderer font, String text, float size, float width) {
        return ClickGuiRenderer.fitText(font, text == null ? "" : text, size, Math.max(1f, width));
    }

    private static boolean inside(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private static ItemCooldownRulesValue.Scope scopeById(String id) {
        if (id != null) {
            for (ItemCooldownRulesValue.Scope scope : ItemCooldownRulesValue.Scope.values()) {
                if (scope.getId().equals(id) || scope.name().equalsIgnoreCase(id)) return scope;
            }
        }
        return ItemCooldownRulesValue.Scope.PVP_GRACE;
    }

    private static ItemCooldownRulesValue.Trigger triggerById(String id) {
        if (id != null) {
            for (ItemCooldownRulesValue.Trigger trigger : ItemCooldownRulesValue.Trigger.values()) {
                if (trigger.getId().equals(id) || trigger.name().equalsIgnoreCase(id)) return trigger;
            }
        }
        return ItemCooldownRulesValue.Trigger.CONSUME_FINISH;
    }

    private static ItemCooldownRulesValue.UseBlockMode blockById(String id) {
        if (id != null) {
            for (ItemCooldownRulesValue.UseBlockMode mode : ItemCooldownRulesValue.UseBlockMode.values()) {
                if (mode.getId().equals(id) || mode.name().equalsIgnoreCase(id)) return mode;
            }
        }
        return ItemCooldownRulesValue.UseBlockMode.NONE;
    }

    @Override
    public ConfigValue<?> getConfigValue() {
        return rules;
    }

    @Override
    public Object save() {
        return rules.toJson();
    }

    @Override
    public void load(Object o) {
        rules.fromJson(o);
        editors.clear();
        syncBridge();
    }

    @Override
    public Set<String> getValueSet() {
        return rules == null ? new LinkedHashSet<>() : rules.getItemIds();
    }

    @Override
    public void setValueSet(Set<String> next) {
        if (rules == null) return;

        Set<String> normalized = new LinkedHashSet<>();
        if (next != null) {
            for (String raw : next) {
                String id = normalizeEntry(raw);
                if (id != null && !id.isBlank()) normalized.add(id);
            }
        }

        for (String existing : new ArrayList<>(rules.getItemIds())) {
            if (!normalized.contains(existing)) {
                rules.removeRule(existing);
                editors.remove(existing);
            }
        }

        for (String id : normalized) {
            rules.ensureRule(id);
        }

        if (focusedItemId != null && !normalized.contains(focusedItemId) && normalized.isEmpty()) {
            focusedItemId = null;
        }

        syncBridge();
        saveParentConfig();
    }

    @Override
    public String normalizeEntry(String line) {
        String normalized = ItemCooldownRulesValue.normalizeItemId(line);
        return normalized != null ? normalized : "";
    }

    @Override
    public String getEditorText() {
        return String.join("\n", getValueSet());
    }

    @Override
    public float pickerDetailWidth() {
        return DETAIL_W;
    }

    @Override
    public boolean shouldToggleSelectionOnCardClick() {
        return false;
    }

    @Override
    public void onPickerFocusChanged(String id) {
        String normalized = normalizeEntry(id);
        if (normalized == null || normalized.isBlank()) return;
        if (!normalized.equals(focusedItemId)) {
            detailScroll = 0f;
        }
        focusedItemId = normalized;
    }

    @Override
    public void renderPickerDetails(float x, float y, float w, float h, String focusedId, float mouseX, float mouseY) {
        UnifiedSettingsSkin.syncTheme();
        ui.actionHits.clear();
        ui.settingHits.clear();

        String itemId = resolveFocusedId(focusedId);
        drawPanel(x, y, w, h, itemId, mouseX, mouseY);
    }

    @Override
    public boolean mouseClickedPickerDetails(float mouseX, float mouseY, int button) {
        if (button != 0) return false;

        for (ActionHit hit : new ArrayList<>(ui.actionHits)) {
            if (!inside(mouseX, mouseY, hit.x, hit.y, hit.w, hit.h)) continue;
            handleAction(hit.action, hit.itemId);
            return true;
        }

        for (SettingHit hit : new ArrayList<>(ui.settingHits)) {
            if (!inside(mouseX, mouseY, hit.x, hit.y, hit.w, hit.h)) continue;
            hit.setting.mouseClicked(mouseX, mouseY, button);
            return true;
        }

        return false;
    }

    @Override
    public void mouseReleasedPickerDetails(float mouseX, float mouseY, int button) {
        for (SettingHit hit : new ArrayList<>(ui.settingHits)) {
            hit.setting.mouseReleased(mouseX, mouseY, button);
        }
    }

    @Override
    public boolean mouseScrolledPickerDetails(float mouseX, float mouseY, double delta) {
        if (detailMaxScroll <= 0.5f) return true;
        detailScroll -= (float) (delta * DETAIL_SCROLL_STEP);
        clampDetailScroll();
        return true;
    }

    private void drawPanel(float x, float y, float w, float h, String itemId, float mx, float my) {
        SettingsGuiPalette palette = SettingsGuiPalette.current();
        LayoutRender2D.roundedQuad(
                x, y, w, h, 12f,
                palette.contentPlaneTop(),
                palette.contentPlaneTop(),
                palette.contentPlaneBottom(),
                palette.contentPlaneBottom()
        );
        LayoutRender2D.roundedStroke(x, y, w, h, 12f, 0.42f, palette.glassEdgeSoft());

        TextRenderer titleFont = UnifiedSettingsSkin.fontSemibold();
        float titleSize = 18f;
        ClickGuiRenderer.drawText(titleFont, ClickGuiI18n.tr("clickgui.pvp_rules.selected_rule", "Selected rule"),
                x + PANEL_PAD, y + 11f, titleSize, UnifiedSettingsSkin.TEXT_PRIMARY, false);
        ClickGuiRenderer.drawText(UnifiedSettingsSkin.fontRegular(),
                ClickGuiI18n.tr("clickgui.pvp_rules.subtitle", "Configure local PvP cooldown behavior"), x + PANEL_PAD, y + 30f,
                10.5f, UnifiedSettingsSkin.TEXT_MUTED, false);
        float dividerY = y + 47f;
        LayoutRender2D.rectQuad(
                x + PANEL_PAD,
                dividerY,
                w - PANEL_PAD * 2f,
                0.5f,
                palette.moduleDividerStart(),
                palette.moduleDividerEnd(),
                palette.moduleDividerEnd(),
                palette.moduleDividerStart()
        );

        if (itemId == null || itemId.isBlank() || rules == null) {
            detailScroll = 0f;
            detailMaxScroll = 0f;
            drawEmptyDetails(x, y, w, h);
            return;
        }

        Item item = resolveItem(itemId);
        ItemStack stack = item == Items.AIR ? ItemStack.EMPTY : item.getDefaultInstance();
        String name = stack.isEmpty() ? itemId : stack.getHoverName().getString();
        boolean hasRule = rules.containsRule(itemId);

        drawItemHeader(x, y, w, itemId, name, stack, hasRule, mx, my);

        if (!hasRule) {
            detailScroll = 0f;
            detailMaxScroll = 0f;
            drawMissingRuleHint(x, y, w, h);
            return;
        }

        RuleEditor editor = editorFor(itemId);
        if (!editor.isDraggingSlider()) {
            editor.syncFromRule();
        }

        float contentX = x + PANEL_PAD;
        float contentY = y + HEADER_H;
        float contentH = Math.max(1f, h - HEADER_H - PANEL_PAD);
        float totalContentH = computeSettingsContentHeight(editor.settings);
        detailMaxScroll = Math.max(0f, totalContentH - contentH);
        clampDetailScroll();

        float scrollbarReserve = detailMaxScroll > 0.5f ? DETAIL_SCROLLBAR_W + 5f : 0f;
        float contentW = w - PANEL_PAD * 2f - scrollbarReserve;
        boolean clipped = ScissorFunction.pushRaw(contentX - 1f, contentY - 1f, contentW + 2f, contentH + 2f);

        float cy = contentY - detailScroll;
        for (Setting setting : editor.settings) {
            float rowH = setting.getHeight();
            if (cy + rowH >= contentY - 2f && cy <= contentY + contentH + 2f) {
                setting.render(contentX, cy, contentW, mx, my);
                ui.settingHits.add(new SettingHit(setting, contentX, cy, contentW, rowH));
            }
            cy += rowH + 2f;
        }

        if (clipped) ScissorFunction.pop();
        drawDetailScrollbar(x, contentY, w, contentH);
    }

    private void drawItemHeader(float x,
                                float y,
                                float w,
                                String itemId,
                                String name,
                                ItemStack stack,
                                boolean hasRule,
                                float mx,
                                float my) {
        TextRenderer titleFont = UnifiedSettingsSkin.fontSemibold();
        TextRenderer font = UnifiedSettingsSkin.fontRegular();
        float small = 11.5f;

        float iconBox = 46f;
        float iconX = x + PANEL_PAD;
        float iconY = y + 57f;
        SettingsGuiPalette palette = SettingsGuiPalette.current();
        LayoutRender2D.roundedQuad(iconX, iconY, iconBox, iconBox, 8f,
                palette.moduleCardTop(), palette.moduleCardTopStrong(), palette.moduleCardBottom(), palette.moduleCardBottomStrong());
        LayoutRender2D.roundedStroke(iconX, iconY, iconBox, iconBox, 8f, 0.38f, palette.glassEdgeSoft());
        if (!stack.isEmpty()) {
            float scale = 1.85f;
            float px = 16f * scale;
            ClickGuiRenderer.queuePickerIcon(stack, iconX + (iconBox - px) * 0.5f, iconY + (iconBox - px) * 0.5f, scale, iconX, iconY, iconBox, iconBox);
        }

        float textX = iconX + iconBox + 11f;
        float buttonW = hasRule ? 76f : 68f;
        float buttonH = 22f;
        float buttonX = x + w - PANEL_PAD - buttonW;
        float buttonY = y + 69f;
        ClickGuiRenderer.drawText(titleFont, fit(titleFont, name, 15f, buttonX - textX - 10f), textX, y + 60f, 15f, UnifiedSettingsSkin.TEXT_PRIMARY, false);
        ClickGuiRenderer.drawText(font, fit(font, itemId, small, buttonX - textX - 10f), textX, y + 80f, small, UnifiedSettingsSkin.TEXT_MUTED, false);

        Action action = hasRule ? Action.REMOVE_RULE : Action.ADD_RULE;
        String label = hasRule ? "Remove" : "Add";
        chip(buttonX, buttonY, buttonW, buttonH, label, inside(mx, my, buttonX, buttonY, buttonW, buttonH), hasRule);
        ui.actionHits.add(new ActionHit(action, itemId, buttonX, buttonY, buttonW, buttonH));
    }

    private void drawEmptyDetails(float x, float y, float w, float h) {
        TextRenderer font = UnifiedSettingsSkin.fontRegular();
        String line1 = "Select an item on the left.";
        String line2 = "A grid click only focuses it; rule creation is explicit.";
        float size = 12f;
        float lineH = ClickGuiRenderer.textHeight(font, size) + 4f;
        float top = y + h * 0.5f - lineH;
        ClickGuiRenderer.drawText(font, line1, x + PANEL_PAD, top, size, UnifiedSettingsSkin.TEXT_MUTED, false);
        ClickGuiRenderer.drawText(font, fit(font, line2, size, w - PANEL_PAD * 2f), x + PANEL_PAD, top + lineH, size, UnifiedSettingsSkin.TEXT_MUTED, false);
    }

    private void drawMissingRuleHint(float x, float y, float w, float h) {
        TextRenderer font = UnifiedSettingsSkin.fontRegular();
        String line1 = "No cooldown rule for this item.";
        String line2 = "Press Add to create one, then configure it below.";
        float size = 12f;
        float lineH = ClickGuiRenderer.textHeight(font, size) + 4f;
        float top = y + HEADER_H + 14f;
        ClickGuiRenderer.drawText(font, line1, x + PANEL_PAD, top, size, UnifiedSettingsSkin.TEXT_MUTED, false);
        ClickGuiRenderer.drawText(font, fit(font, line2, size, w - PANEL_PAD * 2f), x + PANEL_PAD, top + lineH, size, UnifiedSettingsSkin.TEXT_MUTED, false);
    }

    private void chip(float x, float y, float w, float h, String text, boolean hover, boolean destructive) {
        int bg = destructive ? UnifiedSettingsSkin.withAlpha(0xFFFF5555, 32) : UnifiedSettingsSkin.ACCENT_SOFT;
        int stroke = destructive ? UnifiedSettingsSkin.withAlpha(0xFFFF7777, 120) : UnifiedSettingsSkin.ACCENT;
        if (hover) bg = UnifiedSettingsSkin.mix(bg, UnifiedSettingsSkin.SURFACE_HOVER, 0.45f);
        ClickGuiRenderer.drawRoundedRect(x, y, w, h, 4f, bg);
        ClickGuiRenderer.drawRoundedRectStroke(x, y, w, h, 4f, 0.35f, stroke);
        TextRenderer font = UnifiedSettingsSkin.fontRegular();
        float size = 10.5f;
        float tw = ClickGuiRenderer.textWidth(font, text, size);
        float th = ClickGuiRenderer.textHeight(font, size);
        ClickGuiRenderer.drawText(font, text, x + (w - tw) * 0.5f, y + (h - th) * 0.5f, size, UnifiedSettingsSkin.TEXT_PRIMARY, false);
    }

    private void drawDetailScrollbar(float panelX, float contentY, float panelW, float contentH) {
        if (detailMaxScroll <= 0.5f) return;

        float trackX = panelX + panelW - PANEL_PAD - DETAIL_SCROLLBAR_W;
        float trackY = contentY + 1f;
        float trackH = Math.max(1f, contentH - 2f);
        ClickGuiRenderer.drawRoundedRect(
                trackX,
                trackY,
                DETAIL_SCROLLBAR_W,
                trackH,
                DETAIL_SCROLLBAR_W * 0.5f,
                UnifiedSettingsSkin.withAlpha(UnifiedSettingsSkin.TEXT_MUTED, 38)
        );

        float ratio = contentH / (contentH + detailMaxScroll);
        float thumbH = Math.max(18f, trackH * ratio);
        float thumbRange = Math.max(1f, trackH - thumbH);
        float thumbY = trackY + thumbRange * (detailScroll / Math.max(1f, detailMaxScroll));
        ClickGuiRenderer.drawRoundedRect(
                trackX,
                thumbY,
                DETAIL_SCROLLBAR_W,
                thumbH,
                DETAIL_SCROLLBAR_W * 0.5f,
                UnifiedSettingsSkin.withAlpha(UnifiedSettingsSkin.TEXT_MUTED, 120)
        );
    }

    private void clampDetailScroll() {
        detailScroll = Math.max(0f, Math.min(detailScroll, detailMaxScroll));
    }

    private void handleAction(Action action, String itemId) {
        if (rules == null || itemId == null || itemId.isBlank()) return;
        switch (action) {
            case ADD_RULE -> {
                rules.ensureRule(itemId);
                focusedItemId = itemId;
                detailScroll = 0f;
                editorFor(itemId).syncFromRule();
                syncBridge();
                saveParentConfig();
                ClickGuiSounds.changeMode();
            }
            case REMOVE_RULE -> {
                rules.removeRule(itemId);
                editors.remove(itemId);
                focusedItemId = itemId;
                detailScroll = 0f;
                syncBridge();
                saveParentConfig();
                ClickGuiSounds.changeMode();
            }
        }
    }

    private RuleEditor editorFor(String itemId) {
        return editors.computeIfAbsent(itemId, RuleEditor::new);
    }

    private String resolveFocusedId(String focusedId) {
        String normalized = normalizeEntry(focusedId);
        if (normalized != null && !normalized.isBlank()) {
            focusedItemId = normalized;
            return normalized;
        }
        if (focusedItemId != null && !focusedItemId.isBlank()) return focusedItemId;
        Set<String> ids = getValueSet();
        if (ids.isEmpty()) return null;
        focusedItemId = ids.iterator().next();
        return focusedItemId;
    }

    private void syncBridge() {
        ConfigValue<?> bridge = super.getConfigValue();
        if (bridge instanceof SetValue set) {
            set.set(getValueSet());
        }
    }

    private void saveParentConfig() {
        if (getParent() != null) getParent().saveConfig();
    }

    private enum Action {
        ADD_RULE,
        REMOVE_RULE
    }

    private record ActionHit(Action action, String itemId, float x, float y, float w, float h) {
    }

    private record SettingHit(Setting setting, float x, float y, float w, float h) {
    }

    private static final class RuleBooleanSetting extends BooleanSetting {
        private final RuleEditor editor;

        RuleBooleanSetting(String name, BooleanValue value, RuleEditor editor) {
            super(name, value);
            this.editor = editor;
        }

        @Override
        public void mouseClicked(double mx, double my, int button) {
            boolean before = get();
            super.mouseClicked(mx, my, button);
            if (before != get()) editor.commit();
        }
    }

    private static final class RuleSliderSetting extends SliderSetting<Integer> {
        private final RuleEditor editor;

        RuleSliderSetting(String name, NumberValue<Integer> value, RuleEditor editor) {
            super(name, value);
            this.editor = editor;
        }

        @Override
        public void mouseClicked(double mx, double my, int button) {
            int before = value().get();
            super.mouseClicked(mx, my, button);
            if (before != value().get()) editor.commit();
        }

        @Override
        public void mouseReleased(double mx, double my, int button) {
            boolean wasDragging = ui().dragging;
            int before = value().get();
            super.mouseReleased(mx, my, button);
            if (wasDragging || before != value().get()) editor.commit();
        }
    }

    private static final class Ui {
        final List<ActionHit> actionHits = new ArrayList<>();
        final List<SettingHit> settingHits = new ArrayList<>();
    }

    private final class RuleEditor {
        final String itemId;
        final BooleanValue enabledValue = new BooleanValue("cooldown_rule_enabled", false);
        final NumberValue<Integer> secondsValue = new NumberValue<>("cooldown_rule_seconds", 0, 0, 600);
        final NumberValue<Integer> usesValue = new NumberValue<>("cooldown_rule_uses", 1, 1, 10);
        final NumberValue<Integer> windowValue = new NumberValue<>("cooldown_rule_window", 0, 0, 300);
        final ModeValue scopeValue = new ModeValue("cooldown_rule_scope", ItemCooldownRulesValue.Scope.PVP_GRACE.getId(), "always", "pvp_only", "pvp_grace");
        final ModeValue triggerValue = new ModeValue("cooldown_rule_trigger", ItemCooldownRulesValue.Trigger.CONSUME_FINISH.getId(), "interact_accept", "consume_finish", "totem_pop");
        final ModeValue blockValue = new ModeValue("cooldown_rule_block", ItemCooldownRulesValue.UseBlockMode.NONE.getId(), "none", "cooldown", "window", "any");

        final List<Setting> settings = new ArrayList<>();

        final RuleBooleanSetting enabledSetting;
        final RuleSliderSetting secondsSetting;
        final RuleSliderSetting usesSetting;
        final RuleSliderSetting windowSetting;
        final ModeSetting scopeSetting;
        final ModeSetting triggerSetting;
        final ModeSetting blockSetting;

        RuleEditor(String itemId) {
            this.itemId = itemId;
            enabledSetting = new RuleBooleanSetting("Enabled", enabledValue, this);
            secondsSetting = new RuleSliderSetting("Time (s)", secondsValue, this);
            usesSetting = new RuleSliderSetting("Uses", usesValue, this);
            windowSetting = new RuleSliderSetting("Window (s)", windowValue, this);
            scopeSetting = new ModeSetting("Scope", scopeValue, this::commit);
            triggerSetting = new ModeSetting("Trigger", triggerValue, this::commit);
            blockSetting = new ModeSetting("Block", blockValue, this::commit);

            settings.add(enabledSetting);
            settings.add(secondsSetting);
            settings.add(usesSetting);
            settings.add(windowSetting);
            settings.add(scopeSetting);
            settings.add(triggerSetting);
            settings.add(blockSetting);
        }

        void syncFromRule() {
            if (rules == null) return;
            ItemCooldownRulesValue.Rule rule = rules.getRule(itemId);
            if (rule == null) return;
            enabledValue.set(rule.enabled());
            secondsValue.set(rule.seconds());
            usesValue.set(rule.uses());
            windowValue.set(rule.windowSeconds());
            scopeValue.set(rule.scope().getId());
            triggerValue.set(rule.trigger().getId());
            blockValue.set(rule.blockMode().getId());
        }

        boolean isDraggingSlider() {
            return secondsSetting.ui().dragging || usesSetting.ui().dragging || windowSetting.ui().dragging;
        }

        void commit() {
            if (rules == null || !rules.containsRule(itemId)) return;
            ItemCooldownRulesValue.Rule current = rules.getRule(itemId);
            if (current == null) current = rules.getDefaultRule(itemId);

            ItemCooldownRulesValue.Rule next = new ItemCooldownRulesValue.Rule(
                    enabledValue.get(),
                    secondsValue.get(),
                    usesValue.get(),
                    windowValue.get(),
                    scopeById(scopeValue.get()),
                    triggerById(triggerValue.get()),
                    blockById(blockValue.get())
            ).normalized();

            rules.putRule(itemId, next);
            syncFromRule();
            syncBridge();
            saveParentConfig();
        }
    }
}

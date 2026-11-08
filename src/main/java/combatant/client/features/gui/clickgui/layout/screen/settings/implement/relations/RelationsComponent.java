/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.gui.clickgui.layout.screen.settings.implement.relations;

import combatant.client.render.engine.text.BuiltinFontCatalog;

import combatant.client.features.gui.clickgui.ClickGuiRenderer;
import combatant.client.features.gui.clickgui.ClickGuiSearch;
import combatant.client.features.gui.clickgui.layout.screen.settings.SettingsGuiPalette;
import combatant.client.render.engine.renderer.ui.draw.LayoutRender2D;
import combatant.client.features.gui.clickgui.layout.screen.settings.render.SettingsCardTransition;
import combatant.client.features.gui.clickgui.sound.GuiSound;
import combatant.client.features.gui.clickgui.util.ClickGuiI18n;
import combatant.client.features.gui.clickgui.util.ClickGuiMath;
import combatant.client.features.module.modules.misc.DefineTarget;
import combatant.client.features.relations.PlayerRelations;
import combatant.client.features.relations.StaffHeuristicsConfig;
import combatant.client.input.InputKeys;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.svg.SvgRenderOptions;
import combatant.client.render.helpers.ScissorFunction;
import combatant.client.render.helpers.SystemCursor;
import combatant.client.util.text.ChatNameUtil;
import combatant.client.util.text.ClipboardUtil;
import combatant.client.util.text.SingleLineTextInput;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class RelationsComponent {
    private enum Tab { FRIENDS, ENEMIES, STAFF }
    private enum Field { NONE, ADD, PREFIX, SUFFIX, CONTAINS }
    private record Rect(float x, float y, float w, float h) {
        static final Rect EMPTY = new Rect(0, 0, 0, 0);
        boolean contains(float mx, float my) { return ClickGuiMath.insideRect(mx, my, x, y, w, h); }
    }
    private record Entry(String name, int latency, String relation) { }
    private record PlayerHit(String name, Rect card, Rect copy, Rect change) { }
    private record RuleHit(Field field, String value, Rect remove) { }

    private static final long STATUS_MS = 2300L;
    private final SingleLineTextInput add = new SingleLineTextInput(32);
    private final SingleLineTextInput prefix = new SingleLineTextInput(48);
    private final SingleLineTextInput suffix = new SingleLineTextInput(48);
    private final SingleLineTextInput contains = new SingleLineTextInput(48);
    private final ArrayList<PlayerHit> playerHits = new ArrayList<>();
    private final ArrayList<RuleHit> ruleHits = new ArrayList<>();
    private final Rect[] tabHits = {Rect.EMPTY, Rect.EMPTY, Rect.EMPTY};
    private final Rect[] viewHits = {Rect.EMPTY, Rect.EMPTY};
    private final Rect[] ruleFieldHits = {Rect.EMPTY, Rect.EMPTY, Rect.EMPTY};
    private final Rect[] ruleAddHits = {Rect.EMPTY, Rect.EMPTY, Rect.EMPTY};
    private Rect addFieldHit = Rect.EMPTY;
    private Rect addActionHit = Rect.EMPTY;
    private Rect rulesHit = Rect.EMPTY;
    private Rect heuristicsHit = Rect.EMPTY;
    private Rect listBounds = Rect.EMPTY;
    private Rect scrollbarTrack = Rect.EMPTY;
    private Rect scrollbarThumb = Rect.EMPTY;

    private Tab tab = Tab.FRIENDS;
    private Field focus = Field.NONE;
    private boolean onlineView;
    private boolean rulesView;
    private String selected;
    private float modePosition;
    private float viewPosition;
    private float scroll;
    private float smoothedScroll;
    private float maxScroll;
    private float scrollbarGrab;
    private boolean scrollbarDragging;
    private float lastScale = 1f;
    private String status = "";
    private long statusUntil;
    private long onlineRefreshed;
    private List<Entry> cachedOnline = List.of();
    private static boolean movementInputBlocked;

    public static boolean isMovementInputBlocked() { return movementInputBlocked; }

    private static String tr(String key, String fallback) {
        return ClickGuiI18n.tr("clickgui.settings.relations." + key, fallback);
    }
    private static String tr(String key, String fallback, Object value) {
        return ClickGuiI18n.tr("clickgui.settings.relations." + key, fallback, value);
    }
    private static List<String> sorted(Set<String> set) {
        ArrayList<String> names = new ArrayList<>();
        for (String value : set) if (value != null && !value.isBlank()) names.add(value);
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }
    private static Set<String> relationSet(Tab type) {
        PlayerRelations relations = PlayerRelations.get();
        return switch (type) {
            case FRIENDS -> relations.getFriends();
            case ENEMIES -> relations.getEnemies();
            case STAFF -> relations.getStaff();
        };
    }
    private static boolean addRelation(Tab type, String name) {
        PlayerRelations relations = PlayerRelations.get();
        boolean updated = switch (type) {
            case FRIENDS -> relations.addFriend(name);
            case ENEMIES -> relations.addEnemy(name);
            case STAFF -> relations.addStaff(name);
        };
        if (updated) relations.save();
        return updated;
    }
    private static boolean removeRelation(Tab type, String name) {
        PlayerRelations relations = PlayerRelations.get();
        boolean updated = switch (type) {
            case FRIENDS -> relations.removeFriend(name);
            case ENEMIES -> relations.removeEnemy(name);
            case STAFF -> relations.removeStaff(name);
        };
        if (updated) relations.save();
        return updated;
    }
    private static String relationName(String player) {
        PlayerRelations relations = PlayerRelations.get();
        if (relations.isFriend(player)) return tr("online_picker.relation.friend", "Friend");
        if (relations.isEnemy(player)) return tr("online_picker.relation.enemy", "Enemy");
        if (relations.isStaff(player)) return tr("online_picker.relation.staff", "Staff");
        return tr("online_picker.relation.default", "Unassigned");
    }
    private static Field field(int index) {
        return switch (index) { case 0 -> Field.PREFIX; case 1 -> Field.SUFFIX; default -> Field.CONTAINS; };
    }
    private static int fieldIndex(Field field) {
        return switch (field) { case PREFIX -> 0; case SUFFIX -> 1; case CONTAINS -> 2; default -> -1; };
    }
    private SingleLineTextInput input(Field field) {
        return switch (field) {
            case ADD -> add;
            case PREFIX -> prefix;
            case SUFFIX -> suffix;
            case CONTAINS -> contains;
            case NONE -> null;
        };
    }
    private static Set<String> ruleSet(Field field) {
        StaffHeuristicsConfig cfg = StaffHeuristicsConfig.get();
        return switch (field) {
            case PREFIX -> cfg.prefixes();
            case SUFFIX -> cfg.suffixes();
            case CONTAINS -> cfg.contains();
            default -> Set.of();
        };
    }
    private List<Entry> onlinePlayers() {
        long now = System.currentTimeMillis();
        if (now - onlineRefreshed < 600L) return cachedOnline;
        onlineRefreshed = now;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getConnection() == null) {
            cachedOnline = List.of();
            return cachedOnline;
        }
        ArrayList<Entry> result = new ArrayList<>();
        for (PlayerInfo player : minecraft.getConnection().getListedOnlinePlayers()) {
            if (player == null || player.getProfile() == null) continue;
            String name = player.getProfile().name();
            if (name != null && !name.isBlank()) result.add(new Entry(name, player.getLatency(), relationName(name)));
        }
        result.sort(Comparator.comparing(Entry::name, String.CASE_INSENSITIVE_ORDER));
        cachedOnline = List.copyOf(result);
        return cachedOnline;
    }
    private List<Entry> entries() {
        ArrayList<Entry> all = new ArrayList<>();
        if (onlineView) all.addAll(onlinePlayers());
        else for (String name : sorted(relationSet(tab))) all.add(new Entry(name, -1, ""));
        if (!ClickGuiSearch.hasQuery()) return all;
        String query = ClickGuiSearch.getText().strip().toLowerCase(Locale.ROOT);
        all.removeIf(entry -> !entry.name().toLowerCase(Locale.ROOT).contains(query));
        return all;
    }
    private void changeTab(Tab next) {
        if (tab == next) return;
        tab = next;
        rulesView = false;
        selected = null;
        blur();
        resetPosition();
        GuiSound.CHANGE_MODE.feedback(.70);
    }
    private void resetPosition() { scroll = smoothedScroll = 0f; scrollbarDragging = false; }
    public void resetScroll() {
        resetPosition();
        onlineView = false;
        rulesView = false;
        selected = null;
        blur();
        movementInputBlocked = false;
    }
    private void blur() {
        SingleLineTextInput input = input(focus);
        if (input != null) input.unfocus();
        focus = Field.NONE;
        movementInputBlocked = onlineView;
    }
    private void focus(Field next) {
        if (focus == next) return;
        blur();
        focus = next;
        movementInputBlocked = true;
    }
    private void feedback(String key, String fallback, Object value) {
        status = tr(key, fallback, value);
        statusUntil = System.currentTimeMillis() + STATUS_MS;
    }
    private void feedback(String key, String fallback) {
        status = tr(key, fallback);
        statusUntil = System.currentTimeMillis() + STATUS_MS;
    }
    private void commitAdd() {
        String nick = ChatNameUtil.normalizeNickCandidate(add.text());
        if (!ChatNameUtil.isNickLike(nick)) { feedback("status.invalid_nick", "Invalid nickname"); return; }
        if (addRelation(tab, nick)) {
            add.clear();
            blur();
            selected = nick;
            feedback("status.added", "Added: %s", nick);
        } else feedback("status.already_exists", "Already exists: %s", nick);
    }
    private void commitRule(Field field) {
        SingleLineTextInput editor = input(field);
        if (editor == null) return;
        String value = StaffHeuristicsConfig.cleanRule(editor.text());
        if (value.isBlank()) { feedback("status.rule_empty", "Rule cannot be empty"); return; }
        StaffHeuristicsConfig cfg = StaffHeuristicsConfig.get();
        boolean added = switch (field) {
            case PREFIX -> cfg.addPrefix(value);
            case SUFFIX -> cfg.addSuffix(value);
            case CONTAINS -> cfg.addContains(value);
            default -> false;
        };
        if (added) { editor.clear(); blur(); feedback("status.rule_added", "Rule added"); }
        else feedback("status.rule_exists", "Rule already exists");
    }
    private void deleteRule(RuleHit hit) {
        StaffHeuristicsConfig cfg = StaffHeuristicsConfig.get();
        boolean removed = switch (hit.field()) {
            case PREFIX -> cfg.removePrefix(hit.value());
            case SUFFIX -> cfg.removeSuffix(hit.value());
            case CONTAINS -> cfg.removeContains(hit.value());
            default -> false;
        };
        if (removed) feedback("status.rule_removed", "Rule removed");
    }
    private void assignOnline(String name) {
        DefineTarget.RelationTargetMode mode = switch (tab) {
            case FRIENDS -> DefineTarget.RelationTargetMode.FRIEND;
            case ENEMIES -> DefineTarget.RelationTargetMode.ENEMY;
            case STAFF -> DefineTarget.RelationTargetMode.STAFF;
        };
        boolean wasAssigned = DefineTarget.isPlayerInRelationMode(mode, name);
        DefineTarget.togglePlayerRelation(mode, name);
        onlineRefreshed = 0L;
        feedback(wasAssigned ? "status.removed" : "status.added", wasAssigned ? "Removed: %s" : "Added: %s", name);
    }
    private static void drawText(String text, float x, float y, float size, int color, boolean medium) {
        LayoutRender2D.drawText(medium ? BuiltinFontCatalog.ONEST_MEDIUM.renderer() : BuiltinFontCatalog.ONEST_REGULAR.renderer(),
                text, x, y, size, color, false);
    }
    private static String fit(String text, float size, float width, boolean medium) {
        return LayoutRender2D.fitText(medium ? BuiltinFontCatalog.ONEST_MEDIUM.renderer() : BuiltinFontCatalog.ONEST_REGULAR.renderer(),
                text, size, Math.max(1f, width));
    }
    private static void cardSurface(float x, float y, float w, float h, boolean selected, boolean hovered,
                                    float scale, SettingsGuiPalette p) {
        int top = selected ? SettingsGuiPalette.mix(p.moduleCardTop(), p.menuCategorySelectedLeft(), .16f) : p.moduleCardTop();
        int topRight = selected ? SettingsGuiPalette.mix(p.moduleCardTopStrong(), p.menuCategorySelectedRight(), .16f) : p.moduleCardTopStrong();
        int bottom = hovered ? SettingsGuiPalette.mix(p.moduleCardBottom(), p.menuCategoryHoverLeft(), .18f) : p.moduleCardBottom();
        int bottomRight = hovered ? SettingsGuiPalette.mix(p.moduleCardBottomStrong(), p.menuCategoryHoverRight(), .14f) : p.moduleCardBottomStrong();
        LayoutRender2D.drawBlur(x, y, w, h, 5f * scale, 0xFF000000, 200f / 255f);
        LayoutRender2D.roundedQuad(x, y, w, h, 5f * scale, top, topRight, bottom, bottomRight);
    }
    private static void iconButton(Rect hit, String svg, boolean active, float mx, float my, float scale, SettingsGuiPalette p) {
        if (hit.w() <= 0f) return;
        boolean hover = hit.contains(mx, my);
        int a = SettingsGuiPalette.mix(p.panelPillBase(), active ? p.menuCategorySelectedLeft() : p.menuCategoryHoverLeft(),
                active ? .46f : hover ? .36f : .14f);
        int b = SettingsGuiPalette.mix(p.panelPillBase(), active ? p.menuCategorySelectedRight() : p.menuCategoryHoverRight(),
                active ? .46f : hover ? .36f : .14f);
        LayoutRender2D.roundedQuad(hit.x(), hit.y(), hit.w(), hit.h(), 3.5f * scale, a, b, b, a);
        float inset = hit.w() * .26f;
        Renderer2D.COLOR.svg(svg, hit.x() + inset, hit.y() + inset, hit.w() - 2f * inset,
                hit.h() - 2f * inset, SvgRenderOptions.overrideColor(p.menuCategoryText()));
        if (hover) SystemCursor.set(SystemCursor.CursorType.MOVE);
    }
    private static void pillTrack(float x, float y, float w, float h, SettingsGuiPalette p, float scale) {
        LayoutRender2D.drawBlur(x, y, w, h, 4f * scale, p.panelBlurTint(), 145f / 255f);
        int a = SettingsGuiPalette.mix(p.panelPillBase(), p.panelBgLeft(), .34f);
        int b = SettingsGuiPalette.mix(p.panelPillBase(), p.panelBgRight(), .44f);
        LayoutRender2D.roundedQuad(x, y, w, h, 3.5f * scale, a, b, b, a);
        LayoutRender2D.roundedStrokeQuad(x, y, w, h, 3.5f * scale, .5f * scale,
                p.panelStroke(), p.panelStroke(), p.panelStroke(), p.panelStroke());
    }
    private static void pillIndicator(float x, float y, float w, float h, SettingsGuiPalette p, float scale) {
        int a = SettingsGuiPalette.mix(p.panelPillActive(), p.menuCategorySelectedLeft(), .22f);
        int b = SettingsGuiPalette.mix(p.panelPillActive(), p.menuCategorySelectedRight(), .22f);
        LayoutRender2D.roundedQuad(x, y, w, h, 3f * scale, a, b, b, a);
    }
    private void renderPills(float x, float y, float mx, float my, float scale, SettingsGuiPalette p) {
        float height = 15f * scale;
        float tabsWidth = 132f * scale;
        pillTrack(x, y, tabsWidth, height, p, scale);
        float segment = tabsWidth / 3f;
        float dt = AnimationUtility.deltaTime();
        modePosition = AnimationUtility.approach(modePosition, tab.ordinal(), dt, 12f);
        modePosition = AnimationUtility.snap(modePosition, tab.ordinal(), .01f);
        pillIndicator(x + 1.2f * scale + (segment - .8f * scale) * modePosition,
                y + 1.2f * scale, segment - 1.6f * scale, height - 2.4f * scale, p, scale);
        String[] labels = {
                tr("tab.friends", "Friends"), tr("tab.enemies", "Enemies"), tr("tab.staff", "Staff")
        };
        for (int i = 0; i < 3; i++) {
            float bx = x + i * segment;
            tabHits[i] = new Rect(bx, y, segment, height);
            boolean hover = tabHits[i].contains(mx, my);
            float fontSize = 7.7f * scale;
            String text = fit(labels[i], fontSize, segment - 6f * scale, true);
            float tw = LayoutRender2D.textWidth(BuiltinFontCatalog.ONEST_MEDIUM.renderer(), text, fontSize);
            drawText(text, bx + (segment - tw) / 2f, y + 3.6f * scale, fontSize,
                    i == tab.ordinal() ? p.panelText() : SettingsGuiPalette.mix(p.panelMuted(), p.panelText(), hover ? .28f : 0f), true);
            if (hover) SystemCursor.set(SystemCursor.CursorType.MOVE);
        }
    }
    private void renderViewPills(float x, float y, float width, int storedCount, int onlineCount,
                                 float mx, float my, float scale, SettingsGuiPalette p) {
        float height = 15f * scale;
        pillTrack(x, y, width, height, p, scale);
        float segment = width / 2f;
        float dt = AnimationUtility.deltaTime();
        float target = onlineView ? 1f : 0f;
        viewPosition = AnimationUtility.approach(viewPosition, target, dt, 12f);
        viewPosition = AnimationUtility.snap(viewPosition, target, .01f);
        pillIndicator(x + 1.2f * scale + (segment - .8f * scale) * viewPosition,
                y + 1.2f * scale, segment - 1.6f * scale, height - 2.4f * scale, p, scale);
        String[] labels = {
                tr("view.saved", "Saved") + " " + storedCount,
                tr("view.online", "Online") + " " + onlineCount
        };
        for (int i = 0; i < 2; i++) {
            float bx = x + i * segment;
            viewHits[i] = new Rect(bx, y, segment, height);
            boolean hover = viewHits[i].contains(mx, my);
            float fontSize = 7.2f * scale;
            String text = fit(labels[i], fontSize, segment - 5f * scale, true);
            float tw = LayoutRender2D.textWidth(BuiltinFontCatalog.ONEST_MEDIUM.renderer(), text, fontSize);
            drawText(text, bx + (segment - tw) / 2f, y + 3.7f * scale, fontSize,
                    (i == 1) == onlineView ? p.panelText() : SettingsGuiPalette.mix(p.panelMuted(), p.panelText(), hover ? .28f : 0f), true);
            if (hover) SystemCursor.set(SystemCursor.CursorType.MOVE);
        }
    }
    private void renderField(SingleLineTextInput editor, Field field, Rect bounds, String placeholder,
                             float mx, float my, float scale, SettingsGuiPalette p, String actionIcon) {
        boolean active = focus == field;
        int a = SettingsGuiPalette.mix(p.panelPillBase(), p.moduleCardTop(), .25f);
        int b = SettingsGuiPalette.mix(p.panelPillBase(), p.moduleCardTopStrong(), .21f);
        LayoutRender2D.roundedQuad(bounds.x(), bounds.y(), bounds.w(), bounds.h(), 3.5f * scale, a, b, b, a);
        LayoutRender2D.roundedStrokeQuad(bounds.x(), bounds.y(), bounds.w(), bounds.h(), 3.5f * scale,
                active ? .85f * scale : .45f * scale,
                active ? p.menuCategorySelectedLeft() : p.panelStroke(),
                active ? p.menuCategorySelectedRight() : p.panelStroke(),
                active ? p.menuCategorySelectedRight() : p.panelStroke(),
                active ? p.menuCategorySelectedLeft() : p.panelStroke());
        float fieldX = bounds.x() + 7f * scale;
        float fieldW = bounds.w() - 24f * scale;
        float fontSize = 8.3f * scale;
        if (editor.text().isEmpty() && !active) {
            drawText(fit(placeholder, fontSize, fieldW, false), fieldX, bounds.y() + 4f * scale,
                    fontSize, p.panelMuted(), false);
        }
        editor.render(fieldX, bounds.y() + 4f * scale, fieldW, bounds.h() * .68f,
                BuiltinFontCatalog.ONEST_REGULAR.renderer(), fontSize, p.panelText(),
                SettingsGuiPalette.withAlpha(p.menuCategorySelectedLeft(), 100), active);
        float divider = bounds.x() + bounds.w() - 18f * scale;
        LayoutRender2D.rectQuad(divider, bounds.y() + 2.7f * scale, .55f * scale, bounds.h() - 5.4f * scale,
                p.moduleDividerStart(), p.moduleDividerEnd(), p.moduleDividerEnd(), p.moduleDividerStart());
        Rect action = new Rect(divider, bounds.y(), 18f * scale, bounds.h());
        if (action.contains(mx, my)) SystemCursor.set(SystemCursor.CursorType.MOVE);
        float icon = 8.2f * scale;
        Renderer2D.COLOR.svg(actionIcon, divider + (action.w() - icon) / 2f,
                bounds.y() + (bounds.h() - icon) / 2f, icon, icon,
                SvgRenderOptions.overrideColor(p.menuCategoryText()));
    }
    private void renderRuleInput(SingleLineTextInput editor, Field field, Rect bounds,
                                 float mx, float my, float scale, SettingsGuiPalette p) {
        renderField(editor, field, bounds, tr("placeholder.rule", "Rule"), mx, my, scale, p, "check");
    }
    private void renderToolbar(float x, float y, float width, float mx, float my, float scale,
                               SettingsGuiPalette p) {
        renderPills(x, y, mx, my, scale, p);
        float lastX = x + width;
        float modeW = 93f * scale;
        lastX -= modeW;
        renderViewPills(lastX, y, modeW, relationSet(tab).size(), onlinePlayers().size(), mx, my, scale, p);
        float slotX = x + 139f * scale;
        float slotW = lastX - slotX - 6f * scale;
        rulesHit = Rect.EMPTY;
        heuristicsHit = Rect.EMPTY;
        if (tab == Tab.STAFF) {
            float control = 15f * scale;
            rulesHit = new Rect(lastX - control - 5f * scale, y, control, control);
            iconButton(rulesHit, "sliders-horizontal", rulesView, mx, my, scale, p);
            slotW -= control + 5f * scale;
        }
        addFieldHit = addActionHit = Rect.EMPTY;
        if (rulesView && tab == Tab.STAFF) {
            float control = Math.min(slotW, 96f * scale);
            heuristicsHit = new Rect(slotX, y, control, 15f * scale);
            boolean enabled = StaffHeuristicsConfig.get().enabled();
            pillTrack(heuristicsHit.x(), heuristicsHit.y(), heuristicsHit.w(), heuristicsHit.h(), p, scale);
            if (enabled) pillIndicator(heuristicsHit.x() + scale, heuristicsHit.y() + scale,
                    heuristicsHit.w() - 2f * scale, heuristicsHit.h() - 2f * scale, p, scale);
            String text = enabled ? tr("heuristics.enabled", "Enabled") : tr("heuristics.disabled", "Disabled");
            float fontSize = 7.6f * scale;
            text = fit(text, fontSize, heuristicsHit.w() - 7f * scale, true);
            float textW = LayoutRender2D.textWidth(BuiltinFontCatalog.ONEST_MEDIUM.renderer(), text, fontSize);
            drawText(text, slotX + (control - textW) / 2f, y + 3.6f * scale, fontSize,
                    enabled ? p.panelText() : p.panelMuted(), true);
        } else if (!onlineView && slotW >= 55f * scale) {
            addFieldHit = new Rect(slotX, y, slotW, 15f * scale);
            addActionHit = new Rect(slotX + slotW - 18f * scale, y, 18f * scale, 15f * scale);
            renderField(add, Field.ADD, addFieldHit, tr("placeholder.nick", "Nickname"), mx, my, scale, p, "user-plus");
        }
    }
    public void render(float menuX, float menuY, float menuW, float menuH,
                       float mx, float my, float scale) {
        lastScale = scale;
        SettingsGuiPalette palette = SettingsGuiPalette.current();
        float x = menuX + 31f * scale;
        float y = menuY + 33f * scale;
        float width = menuW - 42f * scale;
        float height = menuH - 39f * scale;
        float toolbar = 24f * scale;
        renderToolbar(x, y, width, mx, my, scale, palette);
        listBounds = new Rect(x, y + toolbar, width, height - toolbar);
        playerHits.clear();
        ruleHits.clear();
        for (int i = 0; i < 3; i++) { ruleFieldHits[i] = Rect.EMPTY; ruleAddHits[i] = Rect.EMPTY; }
        if (rulesView && tab == Tab.STAFF) renderRules(mx, my, scale, palette);
        else renderPlayers(entries(), mx, my, scale, palette);
        renderScrollbar(mx, my, scale, palette);
        if (System.currentTimeMillis() < statusUntil && !status.isEmpty()) {
            float fontSize = 7.4f * scale;
            String value = fit(status, fontSize, width - 14f * scale, false);
            float tw = LayoutRender2D.textWidth(BuiltinFontCatalog.ONEST_REGULAR.renderer(), value, fontSize);
            drawText(value, x + width - tw - 7f * scale,
                    y + height - 9f * scale, fontSize, palette.panelText(), false);
        }
        movementInputBlocked = focus != Field.NONE || onlineView;
    }
    private void renderPlayers(List<Entry> players, float mx, float my, float scale, SettingsGuiPalette p) {
        Rect r = listBounds;
        float gap = 6f * scale;
        int columns = r.w() > 230f * scale ? 2 : 1;
        float cardW = (r.w() - gap * (columns - 1)) / columns;
        float cardH = 58f * scale;
        float step = cardH + gap;
        int rows = (players.size() + columns - 1) / columns;
        updateScroll(rows <= 0 ? 0f : rows * step - gap, r.h());
        if (players.isEmpty()) {
            String text = ClickGuiSearch.hasQuery() ? tr("empty.no_matches", "No matching players") :
                    onlineView ? tr("online_picker.empty", "No online players") : tr("empty.none_added", "No players added");
            float fontSize = 8.8f * scale;
            float tw = LayoutRender2D.textWidth(BuiltinFontCatalog.ONEST_REGULAR.renderer(), text, fontSize);
            drawText(text, r.x() + (r.w() - tw) / 2f, r.y() + r.h() * .42f, fontSize, p.panelMuted(), false);
            return;
        }
        boolean clipped = ScissorFunction.pushRaw(r.x(), r.y(), r.w(), r.h());
        for (int i = 0; i < players.size(); i++) {
            int column = i % columns, row = i / columns;
            float cx = r.x() + column * (cardW + gap);
            float cy = r.y() + row * step + smoothedScroll;
            if (cy + cardH <= r.y() || cy >= r.y() + r.h()) continue;
            Entry entry = players.get(i);
            renderPlayerCard(entry, cx, cy, cardW, cardH, mx, my, scale, p);
        }
        if (clipped) ScissorFunction.pop();
    }
    private void renderPlayerCard(Entry entry, float x, float y, float w, float h,
                                  float mx, float my, float scale, SettingsGuiPalette p) {
        boolean selectedCard = entry.name().equalsIgnoreCase(selected);
        Rect card = new Rect(x, y, w, h);
        boolean hovered = card.contains(mx, my);
        try (var transition = SettingsCardTransition.beginCard(x, y, w, h, 5f * scale, scale, p)) {
            cardSurface(x, y, w, h, selectedCard, hovered, scale, p);
            float iconW = 18f * scale;
            float iconX = x + 6f * scale;
            float iconY = y + 6f * scale;
            int iconBg = SettingsGuiPalette.mix(p.menuCategorySelectedLeft(), p.panelPillBase(), .55f);
            LayoutRender2D.roundedQuad(iconX, iconY, iconW, iconW, 4f * scale,
                    iconBg, iconBg, iconBg, iconBg);
            Renderer2D.COLOR.svg("user", iconX + 4f * scale, iconY + 4f * scale,
                    10f * scale, 10f * scale, SvgRenderOptions.overrideColor(p.panelText()));
            float titleX = x + 29f * scale;
            float titleW = w - 39f * scale;
            drawText(fit(entry.name(), 10.3f * scale, titleW, true), titleX,
                    y + 7.1f * scale, 10.3f * scale, p.moduleTitleText(), true);
            Entry presence = onlinePlayers().stream()
                    .filter(player -> player.name().equalsIgnoreCase(entry.name())).findFirst().orElse(null);
            String meta = onlineView
                    ? entry.relation() + "  ·  " + tr("card.latency", "%s ms", entry.latency())
                    : presence == null ? tr("card.offline", "Offline")
                    : tr("card.online", "Online") + "  ·  " + tr("card.latency", "%s ms", presence.latency());
            drawText(fit(meta, 7.3f * scale, titleW, false), titleX,
                    y + 20f * scale, 7.3f * scale, p.moduleDescriptionText(), false);
            LayoutRender2D.rectQuad(x + 7f * scale, y + 32f * scale, w - 14f * scale, .5f * scale,
                    p.moduleDividerStart(), p.moduleDividerEnd(), p.moduleDividerEnd(), p.moduleDividerStart());
            float actionSize = 16f * scale;
            Rect change = new Rect(x + w - 6f * scale - actionSize, y + h - 20f * scale,
                    actionSize, actionSize);
            Rect copy = new Rect(change.x() - 4f * scale - actionSize, change.y(), actionSize, actionSize);
            boolean assigned = relationSet(tab).stream().anyMatch(name -> name.equalsIgnoreCase(entry.name()));
            iconButton(copy, "clipboard", false, mx, my, scale, p);
            iconButton(change, onlineView ? (assigned ? "user-minus" : "user-plus") : "trash-2",
                    onlineView && assigned, mx, my, scale, p);
            String hint = onlineView ? tr("view.online", "Online") : tr("view.saved", "Saved");
            drawText(hint, x + 8f * scale, y + h - 16.2f * scale, 7.4f * scale,
                    p.moduleDescriptionIcon(), false);
            playerHits.add(new PlayerHit(entry.name(), card, copy, change));
        }
    }
    private void renderRules(float mx, float my, float scale, SettingsGuiPalette p) {
        Rect r = listBounds;
        float gap = 6f * scale;
        int columns = r.w() > 230f * scale ? 2 : 1;
        float cardW = (r.w() - gap * (columns - 1)) / columns;
        float[] heights = new float[3];
        for (int i = 0; i < 3; i++) {
            heights[i] = (68f + Math.max(1, ruleSet(field(i)).size()) * 21f) * scale;
        }
        float[] tops = new float[3];
        float position = 0f;
        for (int i = 0; i < 3; i += columns) {
            float height = heights[i];
            if (columns == 2 && i + 1 < 3) height = Math.max(height, heights[i + 1]);
            for (int col = 0; col < columns && i + col < 3; col++) tops[i + col] = position;
            position += height + gap;
        }
        updateScroll(Math.max(0, position - gap), r.h());
        boolean clipped = ScissorFunction.pushRaw(r.x(), r.y(), r.w(), r.h());
        for (int i = 0; i < 3; i++) {
            int col = i % columns;
            float cx = r.x() + col * (cardW + gap);
            float cy = r.y() + tops[i] + smoothedScroll;
            if (cy > r.y() + r.h() || cy + heights[i] < r.y()) continue;
            renderRuleCard(field(i), cx, cy, cardW, heights[i], mx, my, scale, p);
        }
        if (clipped) ScissorFunction.pop();
    }
    private void renderRuleCard(Field field, float x, float y, float width, float height,
                                float mx, float my, float scale, SettingsGuiPalette p) {
        List<String> values = sorted(ruleSet(field));
        try (var transition = SettingsCardTransition.beginCard(x, y, width, height, 5f * scale, scale, p)) {
            cardSurface(x, y, width, height, false,
                    ClickGuiMath.insideRect(mx, my, x, y, width, height), scale, p);
            String title = switch (field) {
                case PREFIX -> tr("heuristics.prefixes", "Prefixes");
                case SUFFIX -> tr("heuristics.suffixes", "Suffixes");
                case CONTAINS -> tr("heuristics.contains", "Contains");
                default -> "";
            };
            drawText(title, x + 9f * scale, y + 7f * scale, 9.5f * scale, p.moduleTitleText(), true);
            Rect inputBounds = new Rect(x + 8f * scale, y + 23f * scale, width - 16f * scale, 18f * scale);
            int index = fieldIndex(field);
            ruleFieldHits[index] = inputBounds;
            ruleAddHits[index] = new Rect(inputBounds.x() + inputBounds.w() - 18f * scale,
                    inputBounds.y(), 18f * scale, inputBounds.h());
            renderRuleInput(input(field), field, inputBounds, mx, my, scale, p);
            float iy = y + 46f * scale;
            if (values.isEmpty()) {
                drawText(tr("rules.empty", "No rules"), x + 10f * scale, iy + 2f * scale,
                        7.5f * scale, p.moduleDescriptionText(), false);
            }
            for (String value : values) {
                Rect chip = new Rect(x + 8f * scale, iy, width - 16f * scale, 18f * scale);
                int a = SettingsGuiPalette.mix(p.panelPillBase(), p.moduleCardTop(), .30f);
                int b = SettingsGuiPalette.mix(p.panelPillBase(), p.moduleCardTopStrong(), .18f);
                LayoutRender2D.roundedQuad(chip.x(), chip.y(), chip.w(), chip.h(), 3.5f * scale, a, b, b, a);
                drawText(fit(value, 7.9f * scale, chip.w() - 30f * scale, false),
                        chip.x() + 6f * scale, chip.y() + 4f * scale,
                        7.9f * scale, p.moduleDescriptionText(), false);
                float separatorX = chip.x() + chip.w() - 18f * scale;
                LayoutRender2D.rectQuad(separatorX, chip.y() + 3f * scale, .6f * scale,
                        chip.h() - 6f * scale, p.moduleDividerStart(), p.moduleDividerEnd(),
                        p.moduleDividerEnd(), p.moduleDividerStart());
                Rect remove = new Rect(separatorX, chip.y(), 18f * scale, chip.h());
                float icon = 7.8f * scale;
                Renderer2D.COLOR.svg("x", remove.x() + (remove.w() - icon) / 2f,
                        remove.y() + (remove.h() - icon) / 2f, icon, icon,
                        SvgRenderOptions.overrideColor(p.moduleDescriptionText()));
                ruleHits.add(new RuleHit(field, value, remove));
                if (remove.contains(mx, my)) SystemCursor.set(SystemCursor.CursorType.MOVE);
                iy += 21f * scale;
            }
        }
    }
    private void updateScroll(float contentHeight, float viewHeight) {
        maxScroll = Math.max(0f, contentHeight - viewHeight);
        scroll = ClickGuiMath.clamp(scroll, -maxScroll, 0f);
        smoothedScroll = AnimationUtility.approach(smoothedScroll, scroll, scrollbarDragging ? .55f : .2f);
        smoothedScroll = AnimationUtility.snap(smoothedScroll, scroll, scrollbarDragging ? .01f : .05f);
        Rect r = listBounds;
        if (maxScroll <= .5f) { scrollbarTrack = scrollbarThumb = Rect.EMPTY; scrollbarDragging = false; return; }
        float width = 3f * lastScale;
        scrollbarTrack = new Rect(r.x() + r.w() - width - 2f * lastScale,
                r.y() + 3f * lastScale, width, Math.max(1f, r.h() - 6f * lastScale));
        float thumbH = Math.min(scrollbarTrack.h(), Math.max(18f * lastScale,
                scrollbarTrack.h() * scrollbarTrack.h() / (maxScroll + scrollbarTrack.h())));
        float ratio = -smoothedScroll / maxScroll;
        scrollbarThumb = new Rect(scrollbarTrack.x(),
                scrollbarTrack.y() + (scrollbarTrack.h() - thumbH) * AnimationUtility.clamp(ratio, 0f, 1f),
                width, thumbH);
        if (scrollbarDragging) dragScrollbar(ClickGuiRenderer.getMouseY());
    }
    private void renderScrollbar(float mx, float my, float scale, SettingsGuiPalette p) {
        if (scrollbarTrack.w() <= 0f) return;
        LayoutRender2D.roundedQuad(scrollbarTrack.x(), scrollbarTrack.y(), scrollbarTrack.w(), scrollbarTrack.h(),
                2f * scale, p.moduleScrollTrackA(), p.moduleScrollTrackB(), p.moduleScrollTrackB(), p.moduleScrollTrackA());
        LayoutRender2D.roundedQuad(scrollbarThumb.x(), scrollbarThumb.y(), scrollbarThumb.w(), scrollbarThumb.h(),
                2f * scale, p.moduleScrollHandleA(), p.moduleScrollHandleB(), p.moduleScrollHandleB(), p.moduleScrollHandleA());
        if (scrollbarTrack.contains(mx, my)) SystemCursor.set(SystemCursor.CursorType.SCROLL);
    }
    private void dragScrollbar(float my) {
        if (maxScroll <= 0f || scrollbarTrack.h() <= scrollbarThumb.h()) return;
        float span = scrollbarTrack.h() - scrollbarThumb.h();
        float top = ClickGuiMath.clamp(my - scrollbarGrab, scrollbarTrack.y(), scrollbarTrack.y() + span);
        scroll = -maxScroll * (top - scrollbarTrack.y()) / span;
    }
    public boolean mousePressedScrollbar(float mx, float my, int button) {
        if (button != InputKeys.MOUSE_LEFT || scrollbarTrack.w() <= 0f ||
                !ClickGuiMath.insideRect(mx, my, scrollbarTrack.x() - 4f,
                        scrollbarTrack.y() - 4f, scrollbarTrack.w() + 8f, scrollbarTrack.h() + 8f)) return false;
        scrollbarDragging = true;
        scrollbarGrab = scrollbarThumb.contains(mx, my) ? my - scrollbarThumb.y() : scrollbarThumb.h() / 2f;
        dragScrollbar(my);
        return true;
    }
    public boolean click(float mx, float my, int button) {
        if (button != InputKeys.MOUSE_LEFT) return false;
        for (int i = 0; i < tabHits.length; i++) if (tabHits[i].contains(mx, my)) {
            changeTab(Tab.values()[i]);
            return true;
        }
        for (int i = 0; i < viewHits.length; i++) if (viewHits[i].contains(mx, my)) {
            if (onlineView != (i == 1)) {
                onlineView = i == 1;
                selected = null;
                rulesView = false;
                blur();
                resetPosition();
                GuiSound.CHANGE_MODE.feedback(.70);
            }
            return true;
        }
        if (rulesHit.contains(mx, my)) {
            rulesView = !rulesView;
            if (rulesView) onlineView = false;
            blur();
            resetPosition();
            GuiSound.CHANGE_MODE.feedback(.70);
            return true;
        }
        if (heuristicsHit.contains(mx, my)) {
            StaffHeuristicsConfig cfg = StaffHeuristicsConfig.get();
            cfg.setEnabled(!cfg.enabled());
            GuiSound.booleanFeedback(cfg.enabled());
            return true;
        }
        if (addActionHit.contains(mx, my)) { commitAdd(); return true; }
        if (addFieldHit.contains(mx, my)) { focus(Field.ADD); add.beginDrag(mx, false); return true; }
        for (int i = 0; i < 3; i++) {
            if (rulesView && listBounds.contains(mx, my) && ruleAddHits[i].contains(mx, my)) { commitRule(field(i)); return true; }
            if (rulesView && listBounds.contains(mx, my) && ruleFieldHits[i].contains(mx, my)) {
                focus(field(i));
                input(field(i)).beginDrag(mx, false);
                return true;
            }
        }
        if (listBounds.contains(mx, my)) {
            for (RuleHit hit : ruleHits) if (hit.remove().contains(mx, my)) { deleteRule(hit); return true; }
            for (PlayerHit hit : playerHits) {
                if (hit.copy().contains(mx, my)) {
                    ClipboardUtil.copy(hit.name());
                    feedback("status.copied", "Copied: %s", hit.name());
                    return true;
                }
                if (hit.change().contains(mx, my)) {
                    if (onlineView) assignOnline(hit.name());
                    else if (removeRelation(tab, hit.name())) {
                        selected = null;
                        feedback("status.removed", "Removed: %s", hit.name());
                    }
                    return true;
                }
                if (hit.card().contains(mx, my)) {
                    selected = hit.name().equalsIgnoreCase(selected) ? null : hit.name();
                    blur();
                    return true;
                }
            }
            blur();
            return true;
        }
        if (focus != Field.NONE) blur();
        return false;
    }
    public void mouseReleased(int button) {
        if (button == InputKeys.MOUSE_LEFT) {
            scrollbarDragging = false;
            SingleLineTextInput.mouseReleased();
        }
    }
    public void scroll(float mx, float my, double amount) {
        if (!listBounds.contains(mx, my)) return;
        scroll += (float) amount * 22f;
    }
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == InputKeys.ESCAPE && focus != Field.NONE) { blur(); return true; }
        SingleLineTextInput editor = input(focus);
        if (editor == null) return false;
        if (keyCode == InputKeys.ENTER || keyCode == InputKeys.KP_ENTER) {
            if (focus == Field.ADD) commitAdd();
            else commitRule(focus);
            return true;
        }
        editor.keyPressed(keyCode, scanCode, modifiers);
        return true;
    }
    public boolean charTyped(char chr, int modifiers) {
        SingleLineTextInput editor = input(focus);
        if (editor == null) return false;
        editor.type(chr);
        return true;
    }
}

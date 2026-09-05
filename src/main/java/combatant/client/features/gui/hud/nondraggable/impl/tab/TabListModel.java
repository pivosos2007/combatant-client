/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.hud.nondraggable.impl.tab;

import com.mojang.authlib.GameProfile;
import combatant.client.features.relations.CategoryService;
import combatant.client.features.relations.CategoryType;
import combatant.client.mixins.accessors.PlayerTabOverlayAccessor;
import combatant.client.util.player.PlayerSkinResolver;
import combatant.client.util.text.LegacyTextUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.level.GameType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

final class TabListModel {
    private static final int MAX_PLAYERS = 80;
    private static final int MAX_ROWS_PER_COLUMN = 24;

    private static final float SHELL_PAD_X = 5f;
    private static final float SHELL_PAD_TOP = 5f;
    private static final float SHELL_PAD_BOTTOM = 5f;
    private static final float HEADER_LINE_HEIGHT = 24f;
    private static final float HEADER_PAD_Y = 3f;
    private static final float HEADER_TO_ROWS_GAP = 2f;
    private static final float ROW_HEIGHT = 30f;
    private static final float FOOTER_LINE_HEIGHT = 21f;
    private static final float FOOTER_PAD_Y = 3f;
    private static final float ROWS_TO_FOOTER_GAP = 2f;
    private static final float COLUMN_GAP = 3f;
    private static final float MIN_COLUMN_WIDTH = 190f;
    private static final float MAX_COLUMN_WIDTH = 330f;
    private static final float RESERVED_RIGHT_WIDTH = 55f;
    private static final float NAME_TEXT_SCALE_FROM_VANILLA = 20.5f / 9.0f;
    private static final float SCORE_TEXT_SCALE_FROM_VANILLA = 18.0f / 9.0f;
    private static final float HEADER_TEXT_SCALE_FROM_VANILLA = 21.0f / 9.0f;
    private static final float FOOTER_TEXT_SCALE_FROM_VANILLA = 18.0f / 9.0f;

    private TabListModel() {
    }

    static int maxPlayers() {
        return MAX_PLAYERS;
    }

    static int maxRowsPerColumn() {
        return MAX_ROWS_PER_COLUMN;
    }

    static Snapshot collect(Minecraft mc, int screenW) {
        if (mc == null || mc.getConnection() == null) return Snapshot.empty();

        PlayerTabOverlay overlay = mc.gui != null && mc.gui.hud != null ? mc.gui.hud.getTabList() : null;
        Component header = tabText(overlay, true);
        Component footer = tabText(overlay, false);
        List<Component> headerLines = splitTabText(mc, header, Math.max(40, screenW - 50));
        List<Component> footerLines = splitTabText(mc, footer, Math.max(40, screenW - 50));
        Objective listObjective = listObjective(mc);
        List<Entry> entries = new ArrayList<>();

        Collection<PlayerInfo> online = mc.getConnection().getListedOnlinePlayers();
        List<PlayerInfo> players = new ArrayList<>(online);
        players.sort(Comparator
                .comparingInt((PlayerInfo info) -> -info.getTabListOrder())
                .thenComparing(info -> info.getGameMode() == GameType.SPECTATOR)
                .thenComparing(info -> teamName(info).toLowerCase(Locale.ROOT))
                .thenComparing(info -> profileName(info).toLowerCase(Locale.ROOT), String.CASE_INSENSITIVE_ORDER));

        int maxNameVanillaWidth = 0;
        int maxScoreVanillaWidth = 0;
        Set<UUID> visibleIds = new HashSet<>();
        int limit = Math.min(players.size(), MAX_PLAYERS);
        for (int i = 0; i < limit; i++) {
            PlayerInfo info = players.get(i);
            GameProfile profile = info.getProfile();
            if (profile == null || profile.id() == null) continue;

            UUID id = profile.id();
            String name = profile.name() != null ? profile.name() : "";
            Component display = displayName(mc, info, name);
            Component scoreText = scoreText(mc, profile, listObjective);
            CategoryType relation = CategoryService.get(name);
            int relationColor = relation == CategoryType.DEFAULT ? 0 : CategoryService.getColor(name);
            Identifier skin = PlayerSkinResolver.resolveProfileSkin(profile);

            if (mc.font != null) {
                maxNameVanillaWidth = Math.max(maxNameVanillaWidth, mc.font.width(display));
                if (scoreText != null) {
                    maxScoreVanillaWidth = Math.max(maxScoreVanillaWidth, mc.font.width(scoreText));
                }
            }

            int latency = info.getLatency();
            EntryMetrics metrics = entryMetrics(scoreText, latency);
            entries.add(new Entry(
                    id,
                    name,
                    display,
                    skin,
                    relation,
                    relationColor,
                    latency,
                    metrics.pingText(),
                    metrics.pingTextWidth(),
                    metrics.pingColumnWidth(),
                    info.getGameMode() == GameType.SPECTATOR,
                    scoreText,
                    metrics.scoreWidth()
            ));
            visibleIds.add(id);
        }

        int count = entries.size();
        int columns = Math.max(1, (int) Math.ceil(count / (float) MAX_ROWS_PER_COLUMN));
        int rows = Math.max(1, Math.min(MAX_ROWS_PER_COLUMN, count));

        float nameDrivenWidth = 39f + maxNameVanillaWidth * NAME_TEXT_SCALE_FROM_VANILLA;
        float scoreDrivenWidth = maxScoreVanillaWidth > 0 ? maxScoreVanillaWidth * SCORE_TEXT_SCALE_FROM_VANILLA + 8f : 0f;
        float wantedColumnWidth = nameDrivenWidth + scoreDrivenWidth + RESERVED_RIGHT_WIDTH;
        float maxUsableWidth = Math.max(MIN_COLUMN_WIDTH, screenW - 58f - (columns - 1) * COLUMN_GAP - SHELL_PAD_X * 2f);
        float columnWidth = Math.max(MIN_COLUMN_WIDTH, Math.min(MAX_COLUMN_WIDTH, Math.min(wantedColumnWidth, maxUsableWidth / Math.max(1, columns))));
        float columnGap = columns > 1 ? COLUMN_GAP : 0f;
        float contentWidth = columns * columnWidth + (columns - 1) * columnGap;

        float headerWidth = maxVanillaLineWidth(mc, headerLines, HEADER_TEXT_SCALE_FROM_VANILLA);
        float footerWidth = maxVanillaLineWidth(mc, footerLines, FOOTER_TEXT_SCALE_FROM_VANILLA);
        float width = Math.max(contentWidth, Math.max(headerWidth, footerWidth)) + SHELL_PAD_X * 2f;
        float headerTop = SHELL_PAD_TOP;
        float headerHeight = headerLines.isEmpty() ? 0f : headerLines.size() * HEADER_LINE_HEIGHT + HEADER_PAD_Y * 2f;
        float rowTop = SHELL_PAD_TOP + (headerLines.isEmpty() ? 0f : headerHeight + HEADER_TO_ROWS_GAP);
        float footerTop = rowTop + rows * ROW_HEIGHT + (footerLines.isEmpty() ? 0f : ROWS_TO_FOOTER_GAP);
        float footerHeight = footerLines.isEmpty() ? 0f : footerLines.size() * FOOTER_LINE_HEIGHT + FOOTER_PAD_Y * 2f;
        float height = footerTop + footerHeight + SHELL_PAD_BOTTOM;

        return new Snapshot(entries, Set.copyOf(visibleIds), header, footer, headerLines, footerLines, columns, rows, columnWidth, ROW_HEIGHT,
                columnGap, rowTop, headerTop, headerHeight, footerTop, footerHeight, width, height);
    }

    static Snapshot refreshDynamic(Minecraft mc, Snapshot previous) {
        if (mc == null || mc.getConnection() == null || previous == null || previous.entries().isEmpty()) return previous;
        Objective listObjective = listObjective(mc);
        List<Entry> entries = new ArrayList<>(previous.entries().size());
        boolean changed = false;
        for (Entry entry : previous.entries()) {
            PlayerInfo info = mc.getConnection().getPlayerInfo(entry.id());
            if (info == null || info.getProfile() == null) {
                return previous;
            }
            Component scoreText = scoreText(mc, info.getProfile(), listObjective);
            int latency = info.getLatency();
            Entry next = withDynamic(entry, latency, scoreText);
            changed |= next != entry;
            entries.add(next);
        }
        if (!changed) return previous;
        return previous.withEntries(entries);
    }

    static long structureSignature(Minecraft mc, int screenW) {
        if (mc == null || mc.getConnection() == null) return 0L;
        long h = 0x6A09E667F3BCC909L;
        h = mix(h, screenW);
        PlayerTabOverlay overlay = mc.gui != null && mc.gui.hud != null ? mc.gui.hud.getTabList() : null;
        h = mix(h, tabText(overlay, true));
        h = mix(h, tabText(overlay, false));
        Objective objective = listObjective(mc);
        h = mix(h, objective != null ? objective.getName() : "");
        h = mix(h, objective != null && objective.getRenderType() != null ? objective.getRenderType().name() : "");
        for (PlayerInfo info : mc.getConnection().getListedOnlinePlayers()) {
            GameProfile profile = info.getProfile();
            if (profile == null || profile.id() == null) continue;
            h = mix(h, profile.id());
            h = mix(h, profile.name());
            h = mix(h, info.getTabListOrder());
            h = mix(h, info.getGameMode() == GameType.SPECTATOR ? 1 : 0);
            h = mix(h, teamName(info));
            h = mix(h, info.getTabListDisplayName());
            String name = profile.name() != null ? profile.name() : "";
            CategoryType relation = CategoryService.get(name);
            h = mix(h, relation.ordinal());
            h = mix(h, relation == CategoryType.DEFAULT ? 0 : CategoryService.getColor(name));
            Component score = scoreText(mc, profile, objective);
            h = mix(h, score);
        }
        return h;
    }

    private static Entry withDynamic(Entry entry, int latency, Component scoreText) {
        boolean sameScore = sameText(entry.scoreText(), scoreText);
        if (entry.latency() == latency && sameScore) return entry;
        EntryMetrics metrics = entryMetrics(scoreText, latency);
        return new Entry(
                entry.id(),
                entry.name(),
                entry.displayName(),
                entry.skin(),
                entry.relation(),
                entry.relationColor(),
                latency,
                metrics.pingText(),
                metrics.pingTextWidth(),
                metrics.pingColumnWidth(),
                entry.spectator(),
                scoreText,
                metrics.scoreWidth()
        );
    }

    private static EntryMetrics entryMetrics(Component scoreText, int latency) {
        String pingText = latency < 0 ? "?ms" : latency + "ms";
        float pingWidth = TabRichTextRenderer.widthPlain(pingText, 16.5f);
        float pingColumnWidth = Math.max(42f, pingWidth);
        float scoreWidth = scoreText != null ? Math.min(56f, TabRichTextRenderer.width(scoreText, 18.0f)) : 0f;
        return new EntryMetrics(pingText, pingWidth, pingColumnWidth, scoreWidth);
    }

    private static boolean sameText(Component a, Component b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.getString().equals(b.getString());
    }

    private static Component displayName(Minecraft mc, PlayerInfo info, String fallbackName) {
        Component display = null;
        if (mc != null && mc.gui != null && mc.gui.hud != null && mc.gui.hud.getTabList() != null) {
            display = mc.gui.hud.getTabList().getNameForDisplay(info);
        }
        if (display == null) {
            display = info.getTabListDisplayName();
        }
        if (display == null) {
            display = Component.literal(fallbackName);
        }
        return LegacyTextUtil.convertLegacyCodesRobust(display);
    }

    private static Component tabText(PlayerTabOverlay overlay, boolean header) {
        if (!(overlay instanceof PlayerTabOverlayAccessor accessor)) return null;
        Component value = header ? accessor.combatant$getHeader() : accessor.combatant$getFooter();
        if (value == null || value.getString().isBlank()) return null;
        return LegacyTextUtil.convertLegacyCodesRobust(value);
    }

    private static List<Component> splitTabText(Minecraft mc, Component component, int maxVanillaWidth) {
        if (component == null || component.getString().isBlank()) return List.of();
        if (mc == null || mc.font == null || maxVanillaWidth <= 0) {
            return List.of(component);
        }

        List<StyledFragment> fragments = flatten(component);
        if (fragments.isEmpty()) return List.of(component);

        List<Component> lines = new ArrayList<>();
        MutableComponentBuilder current = new MutableComponentBuilder();
        for (StyledFragment fragment : fragments) {
            appendStyledFragment(mc, lines, current, fragment, maxVanillaWidth);
        }
        flushStyledLine(lines, current);

        if (lines.isEmpty()) return List.of(component);
        if (lines.size() == 1 && lines.get(0).getString().equals(component.getString())) return List.of(component);
        return List.copyOf(lines);
    }

    private static List<StyledFragment> flatten(Component component) {
        List<StyledFragment> out = new ArrayList<>();
        component.visit((style, text) -> {
            if (text != null && !text.isEmpty()) {
                out.add(new StyledFragment(text, style == null ? Style.EMPTY : style));
            }
            return java.util.Optional.empty();
        }, Style.EMPTY);
        return out;
    }

    private static void appendStyledFragment(Minecraft mc,
                                             List<Component> out,
                                             MutableComponentBuilder current,
                                             StyledFragment fragment,
                                             int maxVanillaWidth) {
        String text = fragment.text();
        Style style = fragment.style();
        StringBuilder token = new StringBuilder();
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);

            if (cp == '\r') {
                if (i < text.length() && text.charAt(i) == '\n') i++;
                appendStyledToken(mc, out, current, token.toString(), style, maxVanillaWidth);
                token.setLength(0);
                flushStyledLine(out, current);
                continue;
            }
            if (cp == '\n') {
                appendStyledToken(mc, out, current, token.toString(), style, maxVanillaWidth);
                token.setLength(0);
                flushStyledLine(out, current);
                continue;
            }

            boolean whitespace = Character.isWhitespace(cp);
            if (whitespace && token.length() > 0 && !isWhitespaceToken(token)) {
                appendStyledToken(mc, out, current, token.toString(), style, maxVanillaWidth);
                token.setLength(0);
            } else if (!whitespace && token.length() > 0 && isWhitespaceToken(token)) {
                appendStyledToken(mc, out, current, token.toString(), style, maxVanillaWidth);
                token.setLength(0);
            }
            token.appendCodePoint(cp);
        }
        appendStyledToken(mc, out, current, token.toString(), style, maxVanillaWidth);
    }

    private static boolean isWhitespaceToken(StringBuilder token) {
        if (token == null || token.length() == 0) return false;
        for (int i = 0; i < token.length(); ) {
            int cp = token.codePointAt(i);
            if (!Character.isWhitespace(cp)) return false;
            i += Character.charCount(cp);
        }
        return true;
    }

    private static void appendStyledToken(Minecraft mc,
                                          List<Component> out,
                                          MutableComponentBuilder current,
                                          String token,
                                          Style style,
                                          int maxVanillaWidth) {
        if (token == null || token.isEmpty()) return;
        if (current.isEmpty() && token.isBlank()) return;

        Component tokenComponent = Component.literal(token).withStyle(style);
        int tokenWidth = mc.font.width(tokenComponent);
        if (!current.isEmpty() && current.width + tokenWidth > maxVanillaWidth) {
            flushStyledLine(out, current);
            if (token.isBlank()) return;
        }

        if (tokenWidth <= maxVanillaWidth) {
            current.append(tokenComponent, tokenWidth);
            return;
        }

        for (int i = 0; i < token.length(); ) {
            int cp = token.codePointAt(i);
            String glyph = new String(Character.toChars(cp));
            Component glyphComponent = Component.literal(glyph).withStyle(style);
            int glyphWidth = mc.font.width(glyphComponent);
            if (!current.isEmpty() && current.width + glyphWidth > maxVanillaWidth) {
                flushStyledLine(out, current);
            }
            current.append(glyphComponent, glyphWidth);
            i += Character.charCount(cp);
        }
    }

    private static void flushStyledLine(List<Component> out, MutableComponentBuilder current) {
        if (current == null || current.isEmpty()) return;
        out.add(current.build());
        current.clear();
    }

    private static float maxVanillaLineWidth(Minecraft mc, List<Component> lines, float scale) {
        if (mc == null || mc.font == null || lines == null || lines.isEmpty()) return 0f;
        int max = 0;
        for (Component line : lines) {
            if (line != null) max = Math.max(max, mc.font.width(line));
        }
        return max * scale;
    }

    private static Objective listObjective(Minecraft mc) {
        if (mc == null || mc.level == null) return null;
        Scoreboard scoreboard = mc.level.getScoreboard();
        return scoreboard != null ? scoreboard.getDisplayObjective(DisplaySlot.LIST) : null;
    }

    private static Component scoreText(Minecraft mc, GameProfile profile, Objective objective) {
        if (mc == null || mc.level == null || profile == null || objective == null) return null;
        Scoreboard scoreboard = mc.level.getScoreboard();
        if (scoreboard == null) return null;
        ReadOnlyScoreInfo score = scoreboard.getPlayerScoreInfo(ScoreHolder.fromGameProfile(profile), objective);
        if (score == null && profile.name() != null && !profile.name().isBlank()) {
            score = scoreboard.getPlayerScoreInfo(ScoreHolder.forNameOnly(profile.name()), objective);
        }
        return score == null ? null : Component.literal(String.valueOf(score.value()));
    }

    private static String teamName(PlayerInfo info) {
        return info.getTeam() != null && info.getTeam().getName() != null ? info.getTeam().getName() : "";
    }

    private static String profileName(PlayerInfo info) {
        GameProfile profile = info.getProfile();
        return profile != null && profile.name() != null ? profile.name() : "";
    }

    private static long mix(long h, int value) {
        return (h ^ value) * 0x100000001B3L;
    }

    private static long mix(long h, UUID value) {
        if (value == null) return mix(h, 0);
        h = (h ^ value.getMostSignificantBits()) * 0x100000001B3L;
        return (h ^ value.getLeastSignificantBits()) * 0x100000001B3L;
    }

    private static long mix(long h, String value) {
        return (h ^ (value != null ? value.hashCode() : 0)) * 0x100000001B3L;
    }

    private static long mix(long h, Component value) {
        return mix(h, value != null ? value.getString() : "");
    }

    record Entry(UUID id,
                 String name,
                 Component displayName,
                 Identifier skin,
                 CategoryType relation,
                 int relationColor,
                 int latency,
                 String pingText,
                 float pingTextWidth,
                 float pingColumnWidth,
                 boolean spectator,
                 Component scoreText,
                 float scoreWidth) {
    }

    private record EntryMetrics(String pingText,
                                float pingTextWidth,
                                float pingColumnWidth,
                                float scoreWidth) {
    }

    private record StyledFragment(String text, Style style) {
    }

    private static final class MutableComponentBuilder {
        private MutableComponent line = Component.empty();
        private int width;
        private boolean empty = true;

        private boolean isEmpty() {
            return empty;
        }

        private void append(Component component, int componentWidth) {
            if (component == null) return;
            line.append(component);
            width += Math.max(0, componentWidth);
            empty = false;
        }

        private Component build() {
            return line;
        }

        private void clear() {
            line = Component.empty();
            width = 0;
            empty = true;
        }
    }

    record Snapshot(List<Entry> entries,
                    Set<UUID> visibleIds,
                    Component header,
                    Component footer,
                    List<Component> headerLines,
                    List<Component> footerLines,
                    int columns,
                    int rows,
                    float columnWidth,
                    float rowHeight,
                    float columnGap,
                    float rowTop,
                    float headerTop,
                    float headerHeight,
                    float footerTop,
                    float footerHeight,
                    float width,
                    float height) {
        Snapshot withEntries(List<Entry> nextEntries) {
            if (nextEntries == null || nextEntries.isEmpty()) return this;
            Set<UUID> ids = new HashSet<>();
            for (Entry entry : nextEntries) {
                if (entry != null && entry.id() != null) ids.add(entry.id());
            }
            return new Snapshot(List.copyOf(nextEntries), Set.copyOf(ids), header, footer, headerLines, footerLines, columns, rows,
                    columnWidth, rowHeight, columnGap, rowTop, headerTop, headerHeight, footerTop, footerHeight, width, height);
        }

        static Snapshot empty() {
            return new Snapshot(List.of(), Set.of(), null, null, List.of(), List.of(), 1, 1, 322f, ROW_HEIGHT, 0f,
                    SHELL_PAD_TOP, SHELL_PAD_TOP, 0f, SHELL_PAD_TOP + ROW_HEIGHT, 0f, 350f,
                    SHELL_PAD_TOP + ROW_HEIGHT + SHELL_PAD_BOTTOM);
        }
    }
}

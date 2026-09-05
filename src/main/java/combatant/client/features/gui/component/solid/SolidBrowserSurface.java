/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.component.solid;

import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.renderer.ui.runtime.asset.UiAssetRef;
import combatant.client.render.engine.renderer.ui.runtime.core.UiDocument;
import combatant.client.render.engine.renderer.ui.runtime.core.UiNode;
import combatant.client.render.engine.renderer.ui.runtime.core.UiNodeSpec;
import combatant.client.render.engine.renderer.ui.runtime.core.UiNodeType;
import combatant.client.render.engine.renderer.ui.runtime.core.UiProps;
import combatant.client.render.engine.renderer.ui.runtime.input.UiPointerEvent;
import combatant.client.render.engine.renderer.ui.runtime.render.UiProjectionMode;
import combatant.client.render.engine.renderer.ui.runtime.render.UiRenderContext;
import combatant.client.render.engine.renderer.ui.runtime.core.UiRuntime;
import combatant.client.render.engine.renderer.ui.runtime.style.UiAlign;
import combatant.client.render.engine.renderer.ui.runtime.style.UiJustify;
import combatant.client.render.engine.renderer.ui.runtime.style.UiStyle;
import combatant.client.render.engine.text.TextRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Reusable three-pane Modern/Solid browser surface.
 *
 * <p>The consumer supplies semantic navigation, collection data and an arbitrary
 * detail subtree. This class owns geometry, retained row motion, search state,
 * scrolling, hit regions and neutral input dispatch.</p>
 */
public final class SolidBrowserSurface<N, I> {
    private static final String COLLECTION_SCROLL = "solid:collection-scroll";
    private static final String DETAIL_SCROLL = "solid:detail-scroll";
    private static final String COLLECTION_THUMB = "solid:collection-scrollbar-thumb";
    private static final String COLLECTION_TRACK = "solid:collection-scrollbar-track";
    private static final String DETAIL_THUMB = "solid:detail-scrollbar-thumb";
    private static final String DETAIL_TRACK = "solid:detail-scrollbar-track";

    private final UiRuntime runtime = new UiRuntime();
    private final SolidAssetBindings assets;
    private final SolidBrowserCallbacks<N, I> callbacks;
    private final LinkedHashMap<String, RowView<I>> rows = new LinkedHashMap<>();
    private final Map<String, NavigationView> navigationViews = new HashMap<>();
    private final SolidScrollState collectionScroll = new SolidScrollState();
    private final SolidScrollState detailScroll = new SolidScrollState();
    private final SolidSearchState search = new SolidSearchState();
    private final SolidMotionSignal searchHover = new SolidMotionSignal(0.0f);
    private final SolidMotionSignal closeHover = new SolidMotionSignal(0.0f);
    private SolidStyleTokens tokens;
    private SolidBrowserModel<N, I> model;
    private SolidMetrics metrics = SolidMetrics.floating(500.0f, 330.0f);
    private String autoFocusedRow;
    private String lastQuery = "";
    private boolean searchFocused;
    private boolean anyDragging;
    private ScrollDrag scrollDrag = ScrollDrag.NONE;
    private boolean floatingDraggable;
    private float viewportWidth;
    private float viewportHeight;
    private float windowDragOffsetX;
    private float windowDragOffsetY;
    private boolean windowDragging;
    private TextRenderer lastTextRenderer;
    private float caretClockMs;

    public SolidBrowserSurface(SolidStyleTokens tokens,
                               SolidAssetBindings assets,
                               SolidBrowserCallbacks<N, I> callbacks) {
        this.tokens = tokens != null ? tokens : SolidStyleTokens.currentTheme();
        this.assets = assets != null ? assets : SolidAssetBindings.defaults();
        this.callbacks = callbacks != null ? callbacks : new SolidBrowserCallbacks<>() {};
        registerActions();
    }

    public UiRuntime runtime() { return runtime; }
    public SolidMetrics metrics() { return metrics; }
    public SolidSearchState searchState() { return search; }

    public void setTokens(SolidStyleTokens tokens) {
        this.tokens = tokens != null ? tokens : SolidStyleTokens.currentTheme();
        rebuild();
    }

    public void setBounds(float x, float y, float width, float height) {
        this.metrics = SolidMetrics.at(x, y, width, height);
        rebuild();
    }

    public void setFloatingViewport(float width, float height, boolean draggable) {
        viewportWidth = width;
        viewportHeight = height;
        floatingDraggable = draggable;
        metrics = SolidMetrics.floating(width, height);
        rebuild();
    }

    public void setModel(SolidBrowserModel<N, I> model) {
        this.model = model;
        if (model == null) {
            rows.clear();
            runtime.setTree(null);
            return;
        }
        validateIds(model.navigation(), model.collection());
        if (!model.searchText().equals(search.text())) search.setText(model.searchText());
        reconcileRows(filtered(model.collection(), search.text()));
        chooseSearchFocus();
        rebuild();
    }

    /** Updates hover, exact source motions, smooth scroll and lifecycle collapse. */
    public void tick(float deltaMs, float pointerX, float pointerY, TextRenderer textRenderer) {
        if (model == null) return;
        lastTextRenderer = textRenderer;
        caretClockMs = (caretClockMs + Math.max(0.0f, deltaMs)) % 1000.0f;
        if (windowDragging) {
            metrics = SolidMetrics.at(pointerX - windowDragOffsetX, pointerY - windowDragOffsetY, metrics.width(), metrics.height());
        }
        if (scrollDrag != ScrollDrag.NONE) updateScrollDrag(pointerX, pointerY);

        layout(textRenderer);
        runtime.input().updateHover(runtime.root(), pointerX, pointerY);
        String hoveredKey = runtime.input().state().hoveredNode() != null ? runtime.input().state().hoveredNode().key() : "";

        for (SolidNavigationEntry<N> entry : model.navigation()) {
            NavigationView view = navigationViews.computeIfAbsent(entry.stableId(), ignored -> new NavigationView(entry.selected()));
            boolean hovered = hoveredKey.equals("solid:nav:" + entry.stableId()) || hoveredKey.equals("solid:nav-icon:" + entry.stableId());
            view.selected.target(entry.selected() ? 1.0f : 0.0f, SolidStyleTokens.STATE_MOTION_MS);
            view.hover.target(hovered ? 1.0f : 0.0f, SolidStyleTokens.HOVER_MOTION_MS);
            view.selected.advance(deltaMs);
            view.hover.advance(deltaMs);
        }
        navigationViews.keySet().retainAll(model.navigation().stream().map(SolidNavigationEntry::stableId).collect(java.util.stream.Collectors.toSet()));
        searchHover.target(hoveredKey.equals("solid:search-field") || hoveredKey.equals("solid:search-icon") || hoveredKey.equals("solid:search-text") ? 1.0f : 0.0f,
                SolidStyleTokens.HOVER_MOTION_MS);
        closeHover.target(hoveredKey.equals("solid:close") || hoveredKey.equals("solid:close-icon") ? 1.0f : 0.0f,
                SolidStyleTokens.HOVER_MOTION_MS);
        searchHover.advance(deltaMs);
        closeHover.advance(deltaMs);

        List<String> dead = new ArrayList<>();
        for (RowView<I> row : rows.values()) {
            boolean hovered = hoveredKey.equals(rowNodeKey(row.id)) || hoveredKey.equals(labelNodeKey(row.id));
            row.hover.target(hovered ? 1.0f : 0.0f, SolidStyleTokens.HOVER_MOTION_MS);
            row.selected.target(row.selectedTarget || row.id.equals(autoFocusedRow) ? 1.0f : 0.0f, SolidStyleTokens.STATE_MOTION_MS);
            row.emphasized.target(row.emphasizedTarget ? 1.0f : 0.0f, SolidStyleTokens.STATE_MOTION_MS);
            row.life.target(row.exiting ? 0.0f : 1.0f, SolidStyleTokens.APPEAR_MOTION_MS);
            row.hover.advance(deltaMs);
            row.selected.advance(deltaMs);
            row.emphasized.advance(deltaMs);
            row.life.advance(deltaMs);
            UiNode label = find(runtime.root(), labelNodeKey(row.id));
            float overflow = label == null ? 0.0f : Math.max(0.0f, label.measuredWidth() - label.bounds().width());
            row.tickMarquee(deltaMs, overflow, hovered, anyDragging);
            if (row.exiting && row.life.value() <= 0.001f && row.life.settled()) dead.add(row.id);
        }
        dead.forEach(rows::remove);

        boolean collectionBarHovered = hoveredKey.equals(COLLECTION_TRACK) || hoveredKey.equals(COLLECTION_THUMB);
        boolean detailBarHovered = hoveredKey.equals(DETAIL_TRACK) || hoveredKey.equals(DETAIL_THUMB);
        updateScrollMaximum(COLLECTION_SCROLL, collectionScroll);
        updateScrollMaximum(DETAIL_SCROLL, detailScroll);
        collectionScroll.tick(deltaMs, collectionBarHovered);
        detailScroll.tick(deltaMs, detailBarHovered);

        rebuild();
        layout(textRenderer);
        applyScrollOffsets();
        layout(textRenderer);
    }

    public void render(Renderer2D renderer,
                       TextRenderer textRenderer,
                       GuiGraphicsExtractor drawContext,
                       float tickDelta) {
        runtime.render(new UiRenderContext(renderer, textRenderer, drawContext, tickDelta, UiProjectionMode.CURRENT));
    }

    public boolean pointerDown(float x, float y, int button) {
        if (model == null) return false;
        UiNode hit = runtime.input().updateHover(runtime.root(), x, y).node();
        if (!isSearchNode(hit)) searchFocused = false;
        if (button == 0 && beginScrollbarDrag(hit, x, y)) return true;
        if (button == 0 && floatingDraggable && isWindowDragSurface(hit, x, y)) {
            windowDragging = anyDragging = true;
            windowDragOffsetX = x - metrics.x();
            windowDragOffsetY = y - metrics.y();
            return true;
        }
        return runtime.input().pointerDown(runtime.root(), new UiPointerEvent(x, y, button));
    }

    public boolean pointerUp(float x, float y, int button) {
        if (scrollDrag != ScrollDrag.NONE) {
            collectionScroll.endDrag();
            detailScroll.endDrag();
            scrollDrag = ScrollDrag.NONE;
            anyDragging = false;
            return true;
        }
        if (windowDragging) {
            windowDragging = anyDragging = false;
            recenterIfMostlyOutside();
            return true;
        }
        return runtime.input().pointerUp(runtime.root(), new UiPointerEvent(x, y, button));
    }

    public boolean scroll(float x, float y, double horizontal, double vertical) {
        UiNode hit = runtime.input().updateHover(runtime.root(), x, y).node();
        UiNode owner = ancestorOfType(hit, UiNodeType.SCROLL);
        if (owner == null) return false;
        SolidScrollState state = owner.key().equals(DETAIL_SCROLL) ? detailScroll : collectionScroll;
        state.wheel((float) (Math.abs(vertical) >= Math.abs(horizontal) ? vertical : horizontal));
        return true;
    }

    public boolean charTyped(char character) {
        if (!searchFocused || !search.type(character)) return false;
        searchChanged();
        return true;
    }

    public boolean keyPressed(int keyCode, boolean shift) {
        if (!searchFocused || !search.keyPressed(keyCode, shift)) return false;
        searchChanged();
        return true;
    }

    /** Visible retained keys, including rows currently playing their exit lifecycle. */
    public List<String> retainedRowIds() { return List.copyOf(rows.keySet()); }

    private void searchChanged() {
        reconcileRows(filtered(model.collection(), search.text()));
        chooseSearchFocus();
        callbacks.onSearchChanged(search.text());
        rebuild();
    }

    private void chooseSearchFocus() {
        String query = normalize(search.text());
        if (!query.equals(lastQuery)) {
            lastQuery = query;
            autoFocusedRow = query.isEmpty() ? null : rows.values().stream().filter(row -> !row.exiting).map(row -> row.id).findFirst().orElse(null);
            if (model.searchSelectsFirstResult() && autoFocusedRow != null) {
                RowView<I> row = rows.get(autoFocusedRow);
                if (row != null) callbacks.onSearchResultFocused(row.value);
            }
        }
    }

    private void reconcileRows(List<SolidCollectionEntry<I>> nextEntries) {
        Set<String> wanted = new HashSet<>();
        for (SolidCollectionEntry<I> entry : nextEntries) wanted.add(entry.stableId());
        for (RowView<I> row : rows.values()) if (!wanted.contains(row.id)) row.exiting = true;

        LinkedHashMap<String, RowView<I>> next = new LinkedHashMap<>();
        for (SolidCollectionEntry<I> entry : nextEntries) {
            RowView<I> row = rows.get(entry.stableId());
            if (row == null) row = new RowView<>(entry);
            row.update(entry);
            row.exiting = false;
            next.put(row.id, row);
        }
        for (RowView<I> row : rows.values()) if (row.exiting) next.putIfAbsent(row.id, row);
        rows.clear();
        rows.putAll(next);
    }

    private List<SolidCollectionEntry<I>> filtered(List<SolidCollectionEntry<I>> source, String rawQuery) {
        String query = normalize(rawQuery);
        if (query.isEmpty()) return source;
        return source.stream()
                .map(entry -> Map.entry(entry, fuzzyScore(normalize(entry.label()), query)))
                .filter(entry -> entry.getValue() != Integer.MAX_VALUE)
                .sorted(Comparator.<Map.Entry<SolidCollectionEntry<I>, Integer>>comparingInt(Map.Entry::getValue)
                        .thenComparing(entry -> entry.getKey().label(), String.CASE_INSENSITIVE_ORDER))
                .map(Map.Entry::getKey)
                .toList();
    }

    private static int fuzzyScore(String value, String query) {
        int score = 0;
        int at = 0;
        int previous = -1;
        for (int i = 0; i < query.length(); i++) {
            int found = value.indexOf(query.charAt(i), at);
            if (found < 0) return Integer.MAX_VALUE;
            score += found + (previous >= 0 ? Math.max(0, found - previous - 1) * 3 : 0);
            previous = found;
            at = found + 1;
        }
        return score;
    }

    private void rebuild() {
        if (model == null) return;
        runtime.setDocument(UiDocument.of("combatant:solid-browser", buildTree()));
    }

    private UiNodeSpec buildTree() {
        float nav = metrics.navigationWidth();
        float collection = metrics.collectionWidth();
        float detail = metrics.detailWidth();
        float header = metrics.headerHeight();

        List<UiNodeSpec> children = new ArrayList<>();
        children.add(shape("solid:root-material", 0, 0, metrics.width(), metrics.height(), 12.0f, tokens.surface(), Map.of(
                "blur", true, "blurQuality", 45.0f, "blurBrightness", 1.0f, "blurAlpha", 1.0f,
                "sourceSquircle", SolidStyleTokens.ROOT_SQUIRCLE
        )));
        children.add(navigationPane(nav, header));
        children.add(collectionPane(nav, collection, header));
        children.add(detailPane(nav + collection, detail, header));
        children.add(shape("solid:divider-nav", nav - 1.0f, 1.0f, 1.0f, metrics.height() - 2.0f, 0, tokens.separator(), Map.of()));
        children.add(shape("solid:divider-collection", nav + collection - 1.0f, 1.0f, 1.0f, metrics.height() - 2.0f, 0, tokens.separator(), Map.of()));
        children.add(shape("solid:divider-detail-header", nav + collection, header - 1.0f, detail - 1.0f, 1.0f, 0, tokens.separator(), Map.of()));

        return new UiNodeSpec("solid:root", UiNodeType.STACK, UiProps.EMPTY,
                UiStyle.builder().width(metrics.width()).height(metrics.height()).radius(12.0f).clip(true).build(),
                "solid-browser-surface", Map.of(), Map.of(
                "designWidth", 488.0f, "designHeight", 318.0f, "rootRadius", 12.0f, "rootSquircle", 2.0f
        ), children);
    }

    private UiNodeSpec navigationPane(float width, float header) {
        List<UiNodeSpec> navItems = new ArrayList<>();
        for (SolidNavigationEntry<N> entry : model.navigation()) {
            NavigationView view = navigationViews.computeIfAbsent(entry.stableId(), ignored -> new NavigationView(entry.selected()));
            float selected = view.selected.value();
            float hover = view.hover.value();
            int background = SolidStyleTokens.mix(SolidStyleTokens.withAlpha(tokens.surface(), 0.40f),
                    SolidStyleTokens.withAlpha(tokens.foreground(), 0.40f), 0.018f * selected + 0.025f * hover);
            UiNodeSpec icon = image("solid:nav-icon:" + entry.stableId(), entry.icon(), 4.0f, 4.0f, 9.0f,
                    SolidStyleTokens.withAlpha(SolidStyleTokens.mix(tokens.foreground(), tokens.accent(), selected), 0.62f + 0.38f * selected),
                    event("click", "solid.nav:" + entry.stableId()));
            navItems.add(new UiNodeSpec("solid:nav:" + entry.stableId(), UiNodeType.BUTTON, UiProps.EMPTY,
                    UiStyle.builder().width(17.0f).height(17.0f).radius(4.0f).backgroundColor(background).cursor("pointer").build(),
                    "solid-navigation-item", event("click", "solid.nav:" + entry.stableId()),
                    Map.of("label", entry.label(), "selectedSignal", selected), List.of(icon)));
        }
        UiNodeSpec logo = model.brandingSlot() != null ? model.brandingSlot()
                : image("solid:logo", assets.logo(), (width - 11.0f) * 0.5f, 11.0f, 11.0f, tokens.foreground(), Map.of());
        UiNodeSpec top = new UiNodeSpec("solid:navigation-top", UiNodeType.COLUMN, UiProps.EMPTY,
                UiStyle.builder().width(width).align(UiAlign.CENTER).gap(9.0f).build(), "solid-navigation-top",
                List.of(new UiNodeSpec("solid:logo-header", UiNodeType.STACK, UiProps.EMPTY,
                        UiStyle.builder().width(width).height(header).build(), "solid-logo-header", List.of(logo)),
                        new UiNodeSpec("solid:navigation-stack", UiNodeType.COLUMN, UiProps.EMPTY,
                                UiStyle.builder().gap(3.0f).align(UiAlign.CENTER).build(), "solid-navigation-stack", navItems)));
        UiNodeSpec footer = model.footerSlot() != null ? model.footerSlot()
                : new UiNodeSpec("solid:footer-action", UiNodeType.BUTTON, UiProps.EMPTY,
                UiStyle.builder().width(17).height(17).radius(4).cursor("pointer").build(), "solid-footer-action",
                event("click", "solid.footer"), Map.of(), List.of(image("solid:footer-icon", assets.footerAction(), 4, 4, 9,
                tokens.foreground(0.58f), event("click", "solid.footer"))));
        return new UiNodeSpec("solid:navigation-pane", UiNodeType.COLUMN, UiProps.EMPTY,
                absolute(0, 0, width, metrics.height()).align(UiAlign.CENTER).justify(UiJustify.BETWEEN).padding(0, 0, 0, 8).build(),
                "solid-navigation-pane", List.of(top, footer));
    }

    private UiNodeSpec collectionPane(float x, float width, float header) {
        UiNodeSpec title = text("solid:collection-title", model.collectionTitle(), 0, 0, width, header, 8, 7.5f, 9.0f,
                tokens.foreground(), Map.of());
        List<UiNodeSpec> rowSpecs = new ArrayList<>();
        for (RowView<I> row : rows.values()) rowSpecs.add(collectionRow(row, width - 9.0f));
        UiNodeSpec list = new UiNodeSpec("solid:collection-list", UiNodeType.COLUMN, UiProps.EMPTY,
                UiStyle.builder().width(width - 9.0f).gap(2.0f).align(UiAlign.STRETCH).build(), "solid-collection-list", rowSpecs);
        float bodyHeight = Math.max(0, metrics.height() - header);
        UiNodeSpec scroll = new UiNodeSpec(COLLECTION_SCROLL, UiNodeType.SCROLL, UiProps.EMPTY,
                absolute(4, header + 1, width - 9, bodyHeight - 6).clip(true).build(), "solid-collection-scroll", List.of(list));
        List<UiNodeSpec> children = new ArrayList<>(List.of(title, scroll));
        children.addAll(scrollbar(false, width - 3.0f, header + 3.0f, bodyHeight - 4.0f, collectionScroll));
        return new UiNodeSpec("solid:collection-pane", UiNodeType.STACK, UiProps.EMPTY,
                absolute(x, 0, width, metrics.height()).build(), "solid-collection-pane", children);
    }

    private UiNodeSpec collectionRow(RowView<I> row, float width) {
        float life = row.life.value();
        float height = SolidStyleTokens.COLLECTION_ROW_HEIGHT * life;
        int background = tokens.rowBackground(row.emphasized.value(), row.selected.value(), row.hover.value());
        int textColor = tokens.rowText(row.emphasized.value(), row.selected.value());
        Map<String, String> events = event("click", "solid.row:" + row.id);
        float labelLeft = row.icon == null ? 5.0f : 16.0f;
        Map<String, Object> labelProps = new HashMap<>();
        labelProps.put("text", row.label);
        labelProps.put("color", String.format("#%08X", textColor));
        labelProps.put("textFade", true);
        labelProps.put("fadeRight", 4.0f);
        labelProps.put("textOffsetX", -row.marqueeOffset);
        UiNodeSpec label = new UiNodeSpec(labelNodeKey(row.id), UiNodeType.TEXT, new UiProps(labelProps),
                UiStyle.builder().height(15.0f).margin(labelLeft, 0, 5.0f, 0).padding(0, 4.0f, 0, 0)
                        .textScale(7.0f / 9.0f).textColor(textColor).clip(true).build(),
                "solid-row-label", events, Map.of("designFontSize", 7.0f), List.of());
        List<UiNodeSpec> children = new ArrayList<>();
        if (row.icon != null) children.add(image("solid:row-icon:" + row.id, row.icon, 4, 4, 7,
                SolidStyleTokens.withAlpha(textColor, 0.9f), events));
        children.add(label);
        return new UiNodeSpec(rowNodeKey(row.id), UiNodeType.BUTTON,
                new UiProps(Map.of("renderRadius", 3.0f)),
                UiStyle.builder().width(width).height(height).radius(3.0f).backgroundColor(background).clip(true).cursor("pointer").build(),
                "solid-collection-row", events,
                Map.of("selectedSignal", row.selected.value(), "emphasizedSignal", row.emphasized.value(),
                        "hoverSignal", row.hover.value(), "life", life, "lifecycle", row.exiting ? "exiting" : life < 0.999f ? "entering" : "visible"),
                children);
    }

    private UiNodeSpec detailPane(float x, float width, float header) {
        List<UiNodeSpec> children = new ArrayList<>();
        children.add(searchField(width, header));
        if (model.detail().headerActions() != null) children.add(model.detail().headerActions());
        if (model.detail().open()) {
            children.add(shape("solid:detail-local-backdrop", 11.0f, header, Math.max(0, width - 22.0f), 32.5f,
                    0, 0x00000000, Map.of("blur", true, "blurQuality", 1.5f, "blurBrightness", 1.0f, "blurAlpha", 0.5f)));
        }
        float innerWidth = Math.max(0, width - 22.0f);
        float bodyTop = header + 10.5f;
        float bodyHeight = Math.max(0, metrics.height() - bodyTop - 2.0f);
        List<UiNodeSpec> content = new ArrayList<>();
        if (model.detail().open()) {
            content.add(text("solid:detail-title", model.detail().title(), 0, 0, innerWidth, 12, 0, 0, 12,
                    tokens.foreground(), Map.of()));
            if (!model.detail().subtitle().isBlank()) {
                content.add(text("solid:detail-subtitle", model.detail().subtitle(), 0, 0, innerWidth, 7, 0, 0, 7,
                        tokens.foreground(0.58f), Map.of()));
            }
            UiNodeSpec chosen = model.detail().empty() ? model.detail().emptyState() : model.detail().body();
            if (chosen != null) content.add(chosen);
            else if (model.detail().empty() && !model.detail().emptyTitle().isBlank()) {
                content.add(defaultEmpty(model.detail().emptyTitle(), innerWidth, bodyHeight));
            }
        } else if (model.detail().body() != null) {
            content.add(model.detail().body());
        }
        UiNodeSpec column = new UiNodeSpec("solid:detail-content", UiNodeType.COLUMN, UiProps.EMPTY,
                UiStyle.builder().width(innerWidth - 5.0f).gap(5.0f).align(UiAlign.STRETCH).build(), "solid-detail-content", content);
        UiNodeSpec scroll = new UiNodeSpec(DETAIL_SCROLL, UiNodeType.SCROLL, UiProps.EMPTY,
                absolute(11, bodyTop, innerWidth, bodyHeight).padding(0, 0, 5, 0).clip(true).build(),
                "solid-detail-scroll", List.of(column));
        children.add(scroll);
        children.addAll(scrollbar(true, width - 9.0f, bodyTop + 2.0f, bodyHeight - 4.0f, detailScroll));
        if (model.detail().closeActionVisible()) children.add(closeButton(width));
        return new UiNodeSpec("solid:detail-pane", UiNodeType.STACK, UiProps.EMPTY,
                absolute(x, 0, width, metrics.height()).clip(true).build(), "solid-detail-pane", children);
    }

    private UiNodeSpec searchField(float detailWidth, float header) {
        float x = 5.0f;
        float y = (header - 12.0f) * 0.5f;
        float hover = searchHover.value();
        Map<String, String> events = event("click", "solid.search");
        List<UiNodeSpec> children = new ArrayList<>();
        if (searchFocused && search.selectionEnd() > search.selectionStart()) {
            float selectionX = 8.5f + searchPrefixWidth(search.selectionStart());
            float selectionWidth = Math.max(0.5f, searchPrefixWidth(search.selectionEnd()) - searchPrefixWidth(search.selectionStart()));
            children.add(shape("solid:search-selection", selectionX, 2.0f, selectionWidth, 8.0f, 1.0f,
                    SolidStyleTokens.withAlpha(tokens.accent(), 0.28f), Map.of("role", "selection")));
        }
        children.add(image("solid:search-icon", assets.search(), 3.5f, 3.5f, 5.0f, tokens.foreground(0.48f), events));
        String shown = search.text().isEmpty() ? model.searchPlaceholder() : search.text();
        int color = tokens.foreground(search.text().isEmpty() ? 0.55f : 0.72f);
        if (searchFocused && caretClockMs < 500.0f) {
            float caretX = Math.min(78.5f, 8.5f + searchPrefixWidth(search.cursor()));
            children.add(shape("solid:search-caret", caretX, 2.5f, 0.75f, 7.0f, 0.35f,
                    tokens.foreground(0.86f), Map.of("role", "caret")));
        }
        children.add(text("solid:search-text", shown, 8.5f, 0, 70.5f, 12, 0, 3.0f, 7.0f, color, events));
        return new UiNodeSpec("solid:search-field", UiNodeType.INPUT_TEXT,
                new UiProps(Map.of("value", search.text(), "cursor", search.cursor(),
                        "selectionStart", search.selectionStart(), "selectionEnd", search.selectionEnd())),
                absolute(x, y, 81, 12).radius(3).backgroundColor(tokens.searchBackground(hover)).cursor("text").build(),
                "solid-search-field", events, Map.of("rightInset", 2.0f), children);
    }

    private UiNodeSpec closeButton(float detailWidth) {
        Map<String, String> events = event("click", "solid.close");
        float hover = closeHover.value();
        return new UiNodeSpec("solid:close", UiNodeType.BUTTON, UiProps.EMPTY,
                absolute(detailWidth - 21, (metrics.headerHeight() - 16) * 0.5f, 16, 16).radius(4).cursor("pointer").build(),
                "solid-close-action", events, Map.of(), List.of(image("solid:close-icon", assets.close(), 3.5f, 3.5f, 9,
                tokens.foreground(0.8f + 0.2f * hover), events)));
    }

    private UiNodeSpec defaultEmpty(String title, float width, float height) {
        return text("solid:empty", title, 0, 0, width, Math.max(9, height - 30), 0,
                Math.max(0, (height - 39) * 0.5f), 9, tokens.foreground(0.55f), Map.of());
    }

    private List<UiNodeSpec> scrollbar(boolean detail, float x, float y, float height, SolidScrollState state) {
        String trackKey = detail ? DETAIL_TRACK : COLLECTION_TRACK;
        String thumbKey = detail ? DETAIL_THUMB : COLLECTION_THUMB;
        float trackLength = Math.max(0, height - 4.0f);
        UiNode scrollNode = find(runtime.root(), detail ? DETAIL_SCROLL : COLLECTION_SCROLL);
        float viewport = scrollNode != null ? scrollNode.bounds().height() : height;
        float content = scrollNode != null ? scrollNode.state().contentHeight() : viewport;
        float thumb = content > 0 ? Math.max(18.0f, trackLength * Math.min(1.0f, viewport / content)) : trackLength;
        thumb = Math.min(trackLength, thumb);
        float travel = Math.max(0, trackLength - thumb);
        float thumbY = y + 2.0f + (state.maximum() > 0 ? state.actual() / state.maximum() * travel : 0);
        float alpha = state.visibility();
        float thumbAlpha = alpha * (0.28f + 0.24f * state.hover() + 0.28f * (state.dragging() ? 1.0f : 0.0f));
        return List.of(
                shape(trackKey, x, y + 2, 2, trackLength, 1, 0x00000000,
                        Map.of("scrollbar", detail ? "detail" : "collection", "role", "track")),
                shape(thumbKey, x, thumbY, 2, thumb, 1, tokens.foreground(thumbAlpha),
                        Map.of("scrollbar", detail ? "detail" : "collection", "role", "thumb"))
        );
    }

    private void registerActions() {
        runtime.actions().register("solid.nav", context -> {
            if (model == null) return false;
            model.navigation().stream().filter(entry -> entry.stableId().equals(context.ref().argument())).findFirst()
                    .ifPresent(entry -> callbacks.onNavigation(entry.value()));
            return true;
        });
        runtime.actions().register("solid.row", context -> {
            RowView<I> row = rows.get(context.ref().argument());
            if (row == null || row.exiting) return false;
            int button = context.event() instanceof UiPointerEvent event ? event.button() : 0;
            if (button == 1) callbacks.onSecondaryClick(row.value);
            else if (button == 2) callbacks.onAuxiliaryClick(row.value);
            else callbacks.onPrimaryClick(row.value);
            return true;
        });
        runtime.actions().register("solid.search", context -> {
            searchFocused = true;
            if (context.event() instanceof UiPointerEvent event) {
                float localTextX = event.x() - metrics.detailX() - 5.0f - 8.5f;
                search.placeCursor(searchIndexAt(localTextX), false);
                caretClockMs = 0.0f;
            }
            return true;
        });
        runtime.actions().register("solid.close", context -> { callbacks.onCloseDetail(); return true; });
        runtime.actions().register("solid.footer", context -> { callbacks.onFooterAction(); return true; });
    }

    private void layout(TextRenderer textRenderer) {
        runtime.layout(textRenderer, metrics.x(), metrics.y(), metrics.width(), metrics.height());
    }

    private void applyScrollOffsets() {
        UiNode collection = find(runtime.root(), COLLECTION_SCROLL);
        UiNode detail = find(runtime.root(), DETAIL_SCROLL);
        if (collection != null) collection.state().setScroll(0, collectionScroll.actual());
        if (detail != null) detail.state().setScroll(0, detailScroll.actual());
    }

    private void updateScrollMaximum(String key, SolidScrollState state) {
        UiNode node = find(runtime.root(), key);
        if (node != null) state.maximum(node.state().contentHeight() - node.bounds().height());
    }

    private boolean beginScrollbarDrag(UiNode hit, float x, float y) {
        if (hit == null) return false;
        if (hit.key().equals(COLLECTION_THUMB) || hit.key().equals(DETAIL_THUMB)) {
            scrollDrag = hit.key().equals(DETAIL_THUMB) ? ScrollDrag.DETAIL : ScrollDrag.COLLECTION;
            SolidScrollState state = scrollDrag == ScrollDrag.DETAIL ? detailScroll : collectionScroll;
            state.beginDrag(y - hit.bounds().y());
            anyDragging = true;
            return true;
        }
        if (hit.key().equals(COLLECTION_TRACK) || hit.key().equals(DETAIL_TRACK)) {
            SolidScrollState state = hit.key().equals(DETAIL_TRACK) ? detailScroll : collectionScroll;
            state.trackTo(y, hit.bounds().y(), hit.bounds().height());
            return true;
        }
        return false;
    }

    private void updateScrollDrag(float x, float y) {
        boolean detail = scrollDrag == ScrollDrag.DETAIL;
        UiNode track = find(runtime.root(), detail ? DETAIL_TRACK : COLLECTION_TRACK);
        UiNode thumb = find(runtime.root(), detail ? DETAIL_THUMB : COLLECTION_THUMB);
        if (track == null || thumb == null) return;
        (detail ? detailScroll : collectionScroll).dragTo(y, track.bounds().y(), Math.max(0, track.bounds().height() - thumb.bounds().height()));
    }

    private boolean isWindowDragSurface(UiNode hit, float x, float y) {
        if (hit != null && (hit.key().contains("search") || !hit.events().isEmpty())) return false;
        return y >= metrics.y() && y <= metrics.y() + metrics.headerHeight()
                && x >= metrics.x() && x <= metrics.x() + metrics.width();
    }

    private void recenterIfMostlyOutside() {
        if (viewportWidth <= 0 || viewportHeight <= 0) return;
        float left = Math.max(0, metrics.x());
        float top = Math.max(0, metrics.y());
        float right = Math.min(viewportWidth, metrics.x() + metrics.width());
        float bottom = Math.min(viewportHeight, metrics.y() + metrics.height());
        float inside = Math.max(0, right - left) * Math.max(0, bottom - top);
        float outside = 1.0f - inside / Math.max(1.0f, metrics.width() * metrics.height());
        if (outside >= 0.35f) metrics = SolidMetrics.floating(viewportWidth, viewportHeight);
        rebuild();
    }

    private static UiNode ancestorOfType(UiNode node, UiNodeType type) {
        for (UiNode current = node; current != null; current = current.parent()) if (current.type() == type) return current;
        return null;
    }

    private static boolean isSearchNode(UiNode node) {
        for (UiNode current = node; current != null; current = current.parent()) {
            if (current.key().equals("solid:search-field")) return true;
        }
        return false;
    }

    private int searchIndexAt(float localX) {
        if (localX <= 0 || search.text().isEmpty()) return 0;
        for (int i = 1; i <= search.text().length(); i++) {
            float previous = searchPrefixWidth(i - 1);
            float next = searchPrefixWidth(i);
            if (localX < (previous + next) * 0.5f) return i - 1;
        }
        return search.text().length();
    }

    private float searchPrefixWidth(int length) {
        if (lastTextRenderer == null || length <= 0 || search.text().isEmpty()) return 0.0f;
        int safeLength = Math.min(length, search.text().length());
        lastTextRenderer.begin(7.0f / 9.0f, true, false);
        try {
            return (float) lastTextRenderer.getWidth(search.text(), safeLength, false);
        } finally {
            lastTextRenderer.end();
        }
    }

    private static UiNode find(UiNode node, String key) {
        if (node == null) return null;
        if (node.key().equals(key)) return node;
        for (UiNode child : node.children()) {
            UiNode found = find(child, key);
            if (found != null) return found;
        }
        return null;
    }

    private static UiStyle.Builder absolute(float x, float y, float width, float height) {
        return UiStyle.builder().absolute(true).offsetX(x).offsetY(y).width(width).height(height);
    }

    private static UiNodeSpec shape(String key, float x, float y, float width, float height, float radius, int color, Map<String, ?> metadata) {
        Map<String, Object> props = new HashMap<>();
        props.put("shape", "rounded");
        props.put("radius", radius);
        props.put("fill", color);
        props.putAll(metadata);
        return new UiNodeSpec(key, UiNodeType.SHAPE, new UiProps(props),
                absolute(x, y, width, height).build(), "solid-shape", List.of());
    }

    private static UiNodeSpec image(String key, UiAssetRef asset, float x, float y, float size, int tint, Map<String, String> events) {
        if (asset == null) return new UiNodeSpec(key, UiNodeType.SPACER, UiProps.EMPTY, absolute(x, y, size, size).build(), "", List.of());
        UiProps props = new UiProps(Map.of("assetType", asset.kind().name().toLowerCase(Locale.ROOT), "asset", asset.id(),
                "intrinsicWidth", size, "intrinsicHeight", size, "tint", String.format("#%08X", tint), "mask", true));
        return new UiNodeSpec(key, asset.kind().name().equals("SVG") ? UiNodeType.SVG : UiNodeType.IMAGE,
                props, absolute(x, y, size, size).build(), "solid-semantic-asset", events, Map.of(), List.of());
    }

    private static UiNodeSpec text(String key, String value, float x, float y, float width, float height,
                                   float leftPadding, float topPadding, float designFontSize, int color,
                                   Map<String, String> events) {
        return text(key, value, x, y, width, height, leftPadding, topPadding, designFontSize, color, events, Map.of());
    }

    private static UiNodeSpec text(String key, String value, float x, float y, float width, float height,
                                   float leftPadding, float topPadding, float designFontSize, int color,
                                   Map<String, String> events, Map<String, ?> extraProps) {
        Map<String, Object> props = new HashMap<>(extraProps);
        props.put("text", value != null ? value : "");
        props.put("color", String.format("#%08X", color));
        return new UiNodeSpec(key, UiNodeType.TEXT, new UiProps(props),
                absolute(x, y, width, height).padding(leftPadding, topPadding, 0, 0)
                        .textScale(designFontSize / 9.0f).textColor(color).clip(true).build(),
                "solid-text", events, Map.of("designFontSize", designFontSize), List.of());
    }

    private static Map<String, String> event(String name, String ref) { return Map.of(name, ref); }
    private static String rowNodeKey(String id) { return "solid:row:" + id; }
    private static String labelNodeKey(String id) { return "solid:row-label:" + id; }
    private static String normalize(String value) { return value == null ? "" : value.strip().toLowerCase(Locale.ROOT); }

    private static void validateIds(List<? extends SolidNavigationEntry<?>> navigation,
                                    List<? extends SolidCollectionEntry<?>> collection) {
        Set<String> ids = new HashSet<>();
        for (SolidNavigationEntry<?> entry : navigation) if (!ids.add(entry.stableId())) throw new IllegalArgumentException("Duplicate navigation id: " + entry.stableId());
        ids.clear();
        for (SolidCollectionEntry<?> entry : collection) if (!ids.add(entry.stableId())) throw new IllegalArgumentException("Duplicate collection id: " + entry.stableId());
    }

    private enum ScrollDrag { NONE, COLLECTION, DETAIL }

    private static final class RowView<T> {
        final String id;
        T value;
        String label;
        UiAssetRef icon;
        boolean selectedTarget;
        boolean emphasizedTarget;
        boolean exiting;
        final SolidMotionSignal hover = new SolidMotionSignal(0);
        final SolidMotionSignal selected = new SolidMotionSignal(0);
        final SolidMotionSignal emphasized = new SolidMotionSignal(0);
        final SolidMotionSignal life = new SolidMotionSignal(0);
        float marqueeOffset;
        float marqueeHold;
        boolean marqueeForward = true;

        RowView(SolidCollectionEntry<T> entry) {
            id = entry.stableId();
            update(entry);
        }

        void update(SolidCollectionEntry<T> entry) {
            value = entry.value();
            label = entry.label();
            icon = entry.icon();
            selectedTarget = entry.selected();
            emphasizedTarget = entry.emphasized();
        }

        void tickMarquee(float deltaMs, float overflow, boolean hovered, boolean dragging) {
            if (overflow <= 0.0f) {
                marqueeOffset = marqueeHold = 0.0f;
                marqueeForward = true;
                return;
            }
            if (hovered && !dragging) {
                marqueeOffset = Math.min(marqueeOffset, overflow);
                if (marqueeHold > 0) marqueeHold = Math.max(0, marqueeHold - deltaMs);
                else if (marqueeForward) {
                    marqueeOffset = Math.min(overflow, marqueeOffset + deltaMs / 1000.0f * SolidStyleTokens.MARQUEE_SPEED);
                    if (marqueeOffset >= overflow) { marqueeForward = false; marqueeHold = SolidStyleTokens.MARQUEE_HOLD_MS; }
                } else {
                    marqueeOffset = Math.max(0, marqueeOffset - deltaMs / 1000.0f * SolidStyleTokens.MARQUEE_SPEED);
                    if (marqueeOffset <= 0) { marqueeForward = true; marqueeHold = SolidStyleTokens.MARQUEE_HOLD_MS; }
                }
            } else if (marqueeOffset > 0) {
                marqueeOffset = Math.max(0, marqueeOffset - deltaMs / 1000.0f * SolidStyleTokens.MARQUEE_SPEED);
            }
        }
    }

    private static final class NavigationView {
        final SolidMotionSignal selected;
        final SolidMotionSignal hover = new SolidMotionSignal(0.0f);

        NavigationView(boolean selected) {
            this.selected = new SolidMotionSignal(selected ? 1.0f : 0.0f);
        }
    }
}

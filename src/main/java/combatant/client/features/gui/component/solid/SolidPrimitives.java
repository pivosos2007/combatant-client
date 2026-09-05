package combatant.client.features.gui.component.solid;

import combatant.client.render.engine.renderer.ui.runtime.core.UiNodeSpec;
import combatant.client.render.engine.renderer.ui.runtime.core.UiNodeType;
import combatant.client.render.engine.renderer.ui.runtime.core.UiProps;
import combatant.client.render.engine.renderer.ui.runtime.style.UiAlign;
import combatant.client.render.engine.renderer.ui.runtime.style.UiStyle;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Generic cards, section boundaries and the exact two-column detail layout. */
public final class SolidPrimitives {
    private SolidPrimitives() {}

    public static UiNodeSpec card(String key, UiNodeSpec content, SolidStyleTokens tokens) {
        return new UiNodeSpec(key, UiNodeType.PANEL, UiProps.EMPTY,
                UiStyle.builder()
                        .padding(4.0f)
                        .radius(SolidStyleTokens.CARD_RADIUS)
                        .backgroundColor(SolidStyleTokens.withAlpha(tokens.surfaceStrong(), 0.40f))
                        .build(), "solid-card", content == null ? List.of() : List.of(content));
    }

    public static UiNodeSpec sectionHeader(String key, UiNodeSpec content) {
        return new UiNodeSpec(key, UiNodeType.PANEL, UiProps.EMPTY,
                UiStyle.builder().build(), "solid-section-header",
                content == null ? List.of() : List.of(content));
    }

    public static UiNodeSpec twoColumnGrid(String key, List<UiNodeSpec> entries, float columnWidth) {
        List<UiNodeSpec> left = new ArrayList<>();
        List<UiNodeSpec> right = new ArrayList<>();
        List<UiNodeSpec> safe = entries != null ? entries : List.of();
        for (int i = 0; i < safe.size(); i++) (i % 2 == 0 ? left : right).add(safe.get(i));
        UiStyle column = UiStyle.builder().width(columnWidth).gap(5.0f).align(UiAlign.STRETCH).build();
        UiNodeSpec leftColumn = new UiNodeSpec(key + ":left", UiNodeType.COLUMN, UiProps.EMPTY, column, "solid-grid-column", left);
        UiNodeSpec rightColumn = new UiNodeSpec(key + ":right", UiNodeType.COLUMN, UiProps.EMPTY, column, "solid-grid-column", right);
        return new UiNodeSpec(key, UiNodeType.ROW, UiProps.EMPTY,
                UiStyle.builder().gap(5.0f).align(UiAlign.START).build(), "solid-grid", Map.of(),
                Map.of("columns", 2, "columnGap", 5.0f, "groupGap", 5.0f), List.of(leftColumn, rightColumn));
    }
}

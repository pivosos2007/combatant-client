package combatant.client.features.gui.hud.draggable.impl;

import combatant.client.render.engine.renderer.ui.runtime.core.*;
import combatant.client.render.engine.renderer.ui.runtime.style.UiStyle;
import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class RetainedHudPanelTest {
    private static ScriptedListHudPanel.Panel panel(float scale, float height, float progress, int count) {
        return panel(scale, 100, height, progress, count);
    }

    private static ScriptedListHudPanel.Panel panel(float scale, float width, float height, float progress, int count) {
        var row = new LinkedHashMap<String, Object>(Map.of("key", "test", "layoutProgress", progress));
        var palette = new ScriptedListHudPanel.Palette(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        return new ScriptedListHudPanel.Panel(ScriptedListHudPanel.POTIONS, palette,
                20, 30, width * scale, height * scale, scale,
                scale, 8 * scale, 8 * scale, 8 * scale, 20 * scale, 6 * scale,
                count, true, 0.5f, 0, false, 0, 0, 45,
                "Split Header", true, 0.5f, false, 0, 0, true, List.of(row), "");
    }

    @Test
    void animationAndSameWidthCounterUpdatesReuseTheTemplate() {
        var first = panel(1, 45, 1, 1);
        var animated = panel(0.8137f, 29, 0.2f, 2);
        assertEquals(first.treeSignature(), animated.treeSignature());
        assertEquals(first.templateHeight(), animated.templateHeight());
        assertEquals(first.treeSignature(), panel(1, 160, 45, 1, 1).treeSignature());
        assertEquals("2", animated.patches().get("header:count-value").get("text"));
        assertNotEquals(first.treeSignature(), panel(1, 45, 1, 10).treeSignature());
    }

    @Test
    void centeredRowClipsMoveChildrenWithoutAccumulatingAnOffset() {
        UiNode root = UiNode.fromSpec(UiNodeSpec.node("panel:potions", UiNodeType.ROOT, UiStyle.DEFAULT,
                UiNodeSpec.node("rows:clip", UiNodeType.STACK, UiStyle.DEFAULT,
                        UiNodeSpec.node("row:test:clip", UiNodeType.STACK, UiStyle.DEFAULT,
                                UiNodeSpec.node("label", UiNodeType.TEXT, UiStyle.DEFAULT)))));
        UiNode clip = root.children().getFirst().children().getFirst();
        UiNode label = clip.children().getFirst();
        clip.setBounds(new UiBounds(0, 21.6f, 100, 11));
        label.setBounds(new UiBounds(10, 23, 30, 8));
        var animated = panel(1, 30, 0.25f, 1);
        animated.patchAnimatedBounds(root);
        assertEquals(2.75f, clip.bounds().height(), 0.0001f);
        assertEquals(27.1f, clip.bounds().y() + clip.bounds().height() / 2, 0.0001f);
        UiBounds once = label.bounds();
        animated.patchAnimatedBounds(root);
        assertEquals(once.y(), label.bounds().y(), 0.0001f);
        assertEquals(30, root.bounds().height());
        panel(1, 30, 0, 1).patchAnimatedBounds(root);
        assertEquals(0, clip.bounds().height());
    }

    @Test
    void widthAnimationKeepsRightAlignmentAndRestoresClampedTitleWidth() {
        UiNode root = UiNode.fromSpec(UiNodeSpec.node("panel:potions", UiNodeType.ROOT, UiStyle.DEFAULT,
                UiNodeSpec.node("header:count-label", UiNodeType.TEXT, UiStyle.DEFAULT),
                UiNodeSpec.node("header:title", UiNodeType.TEXT, UiStyle.DEFAULT),
                UiNodeSpec.node("row:test:clip", UiNodeType.STACK, UiStyle.DEFAULT,
                        UiNodeSpec.node("row:test:time-pill", UiNodeType.SHAPE, UiStyle.DEFAULT))));
        UiNode count = root.children().get(0);
        UiNode title = root.children().get(1);
        UiNode clip = root.children().get(2);
        UiNode pill = clip.children().getFirst();
        root.setBounds(new UiBounds(0, 0, 100, 45));
        count.setBounds(new UiBounds(60, 2, 20, 8));
        title.setBounds(new UiBounds(25, 2, 31, 8));
        clip.setBounds(new UiBounds(0, 21.6f, 100, 11));
        pill.setBounds(new UiBounds(75, 23, 20, 8));

        panel(1, 50, 45, 1, 1).patchAnimatedBounds(root);
        assertEquals(0, title.bounds().width());
        assertEquals(25, pill.bounds().x());
        panel(1, 140, 45, 1, 1).patchAnimatedBounds(root);
        assertEquals(100, count.bounds().x());
        assertEquals(71, title.bounds().width());
        assertEquals(115, pill.bounds().x());
        panel(1, 140, 45, 1, 1).patchAnimatedBounds(root);
        assertEquals(71, title.bounds().width());
        assertEquals(115, pill.bounds().x());
    }
}

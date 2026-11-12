/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.renderer.ui.runtime.script;

import combatant.client.render.engine.color.RenderColor;
import combatant.client.features.gui.clickgui.layout.screen.settings.implement.config.ConfigBindingsView;
import combatant.client.render.engine.renderer.ui.runtime.core.UiNode;
import combatant.client.render.engine.renderer.ui.runtime.core.UiProps;
import combatant.client.render.engine.renderer.ui.runtime.core.UiRuntime;
import combatant.client.render.engine.renderer.ui.runtime.input.UiPointerEvent;
import combatant.client.render.engine.renderer.ui.runtime.input.UiScrollEvent;
import combatant.client.render.engine.renderer.ui.runtime.render.UiRenderTransform;
import combatant.client.render.engine.text.TextRenderer;
import net.minecraft.server.packs.resources.ResourceManager;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ConfigAssignmentsRuntimeTest {
    @Test
    void loadsRealSpectrumImportsAndRoutesTranslatedClicksAndScroll() throws Exception {
        ResourceManager resources = (ResourceManager) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{ResourceManager.class}, (proxy, method, args) -> {
                    throw new AssertionError("Missing UI source: " + method.getName());
                });
        var module = new UiScriptModuleLoader().load(resources,
                UiScriptModuleId.of("combatant:modules/clickgui/config_assignments"));
        try (var engine = new JavetUiEngine()) {
            for (boolean picker : List.of(false, true)) {
                var result = engine.renderTracked(module,
                        new UiScriptRenderContext(1, 0, 0, 716, 422, new UiProps(props(picker))));
                assertTrue(result.success(), () -> String.valueOf(result.error()));
                var runtime = new UiRuntime();
                runtime.setTree(result.root());
                new ConfigBindingsView().update(runtime, props(picker));
                runtime.layout(new MetricsFont(), 0, 0, 716, 422);
                UiNode back = find(runtime.root(), "assignments:back");
                UiNode field = find(runtime.root(), "assignments:address");
                UiNode save = find(runtime.root(), "assignments:save");
                assertEquals(0f, back.bounds().x(), 0.01f);
                assertTrue(field.bounds().x() > 350, "Field must occupy the right column");
                assertTrue(save.bounds().y() > field.bounds().y() + field.bounds().height());
                assertTrue(save.bounds().x() + save.bounds().width() <= 716);
                assertTrue(save.bounds().y() + save.bounds().height() <= 422);
                for (var transform : List.of(new UiRenderTransform(500, 290, 1),
                        new UiRenderTransform(180, 100, 0.75f), new UiRenderTransform(80, 40, 1.5f))) {
                    runtime.input().setCoordinateTransform(transform);
                    AtomicInteger selected = new AtomicInteger();
                    runtime.actions().register("assignments:back", action -> { selected.incrementAndGet(); return true; });
                    float x = transform.x(back.bounds().x() + back.bounds().width() / 2);
                    float y = transform.y(back.bounds().y() + back.bounds().height() / 2);
                    runtime.input().pointerDown(runtime.root(), new UiPointerEvent(x, y, 0));
                    runtime.input().pointerUp(runtime.root(), new UiPointerEvent(x, y, 0));
                    assertEquals(1, selected.get());
                    UiNode list = find(runtime.root(), picker ? "assignments:picker_list" : "assignments:list");
                    runtime.input().scroll(runtime.root(), new UiScrollEvent(
                            transform.x(list.bounds().x() + 20), transform.y(list.bounds().y() + 20), 0, 4));
                    assertTrue(list.state().targetScrollY() > 0, "Long lists must scroll");
                }
            }
        }
    }

    @Test
    void switchesScopeAndEditsRetainedNodesWithoutAnotherScriptEvaluation() throws Exception {
        ResourceManager resources = (ResourceManager) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{ResourceManager.class}, (proxy, method, args) -> {
                    throw new AssertionError("Missing UI source: " + method.getName());
                });
        var module = new UiScriptModuleLoader().load(resources,
                UiScriptModuleId.of("combatant:modules/clickgui/config_assignments"));
        var runtime = new UiRuntime();
        try (var engine = new JavetUiEngine()) {
            var result = engine.render(module, new UiScriptRenderContext(1, 0, 0, 716, 422, new UiProps(props(false))));
            assertTrue(result.success(), () -> String.valueOf(result.error()));
            runtime.setTree(result.root());
        }
        var view = new ConfigBindingsView();
        var values = props(false);
        values.put("all", false);
        view.update(runtime, values);
        runtime.layout(new MetricsFont(), 0, 0, 716, 422);
        assertTrue(find(runtime.root(), "assignments:rows:item:server0").state().visible());
        assertFalse(find(runtime.root(), "assignments:rows:item:server1").state().visible());
        assertFalse(find(runtime.root(), "assignments:picker_list").state().visible());
        assertNull(find(runtime.root(), "assignments:backup"));
        assertNull(find(runtime.root(), "assignments:kind:item:local"));
        values.put("all", true);
        values.put("pickerOpen", true);
        values.put("profileId", "profile12");
        values.put("targetLocked", true);
        view.update(runtime, values);
        runtime.layout(new MetricsFont(), 0, 0, 716, 422);
        assertTrue(find(runtime.root(), "assignments:rows:item:server1").state().visible());
        assertTrue(find(runtime.root(), "assignments:picker_list").state().visible());
        assertEquals("Profile 12", find(runtime.root(), "assignments:profile:selected").props().string("text", ""));
        assertTrue(find(runtime.root(), "assignments:address").state().disabled());
        UiNode scrolled = find(runtime.root(), "assignments:list");
        scrolled.state().setScroll(0, 500);
        scrolled.state().setTargetScroll(0, 500);
        values.put("all", false);
        values.put("pickerOpen", false);
        view.update(runtime, values);
        runtime.layout(new MetricsFont(), 0, 0, 716, 422);
        UiNode row = find(runtime.root(), "assignments:rows:item:server12");
        assertTrue(row.state().visible());
        assertEquals(118, row.bounds().y(), .01);
        assertEquals(0f, scrolled.state().scrollY());
        assertEquals(0f, scrolled.state().targetScrollY());
        AtomicInteger clicks = new AtomicInteger();
        runtime.actions().register("assignments:edit:server12", action -> { clicks.incrementAndGet(); return true; });
        runtime.input().pointerDown(runtime.root(), new UiPointerEvent(row.bounds().x() + 20, row.bounds().y() + 20, 0));
        runtime.input().pointerUp(runtime.root(), new UiPointerEvent(row.bounds().x() + 20, row.bounds().y() + 20, 0));
        assertEquals(1, clicks.get());
    }

    private static Map<String, Object> props(boolean picker) {
        var labels = new LinkedHashMap<String, String>();
        for (String key : List.of("back", "title", "reload", "profile_scope", "all_scope", "empty", "profile",
                "choose_profile", "server", "local", "world_folder", "server_address", "world_hint",
                "server_hint", "current_world", "new_server", "save", "remove")) labels.put(key, key);
        var rows = new ArrayList<Map<String, String>>();
        var profiles = new ArrayList<Map<String, String>>();
        for (int i = 0; i < 30; i++) {
            rows.add(Map.of("context", "server" + i, "profileId", "profile" + i,
                    "label", "server" + i, "description", "Profile " + i));
            profiles.add(Map.of("id", "profile" + i, "label", "Profile " + i));
        }
        var props = new LinkedHashMap<String, Object>();
        props.put("labels", labels);
        props.put("colors", Map.of());
        props.put("profiles", profiles);
        props.put("rows", rows);
        props.put("profileId", "profile0");
        props.put("context", "server0");
        props.put("value", "server0");
        props.put("currentSummary", "Current world");
        props.put("assignedSummary", "Assigned profile");
        props.put("all", true);
        props.put("canSave", true);
        props.put("canRemove", true);
        props.put("currentAvailable", true);
        props.put("pickerOpen", picker);
        return props;
    }

    private static UiNode find(UiNode root, String key) {
        if (root.key().equals(key)) return root;
        for (var child : root.children()) {
            UiNode found = find(child, key);
            if (found != null) return found;
        }
        return null;
    }

    private static final class MetricsFont implements TextRenderer {
        private double size;
        private boolean building;
        @Override public void setAlpha(double alpha) { }
        @Override public void begin(double scale, boolean scaleOnly, boolean big) { size = scale * 10; building = true; }
        @Override public double getWidth(String text, int length, boolean shadow) { return length * size * .5; }
        @Override public double getHeight(boolean shadow) { return size; }
        @Override public double render(String text, double x, double y, RenderColor color, boolean shadow) { return x; }
        @Override public boolean isBuilding() { return building; }
        @Override public void end() { building = false; }
    }
}

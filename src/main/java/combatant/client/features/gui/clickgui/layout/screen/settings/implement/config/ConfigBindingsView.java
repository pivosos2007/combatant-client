/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.gui.clickgui.layout.screen.settings.implement.config;

import combatant.client.render.engine.renderer.ui.runtime.core.UiNode;
import combatant.client.render.engine.renderer.ui.runtime.core.UiRuntime;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Patch only the mutable state of a profile-scoped bindings view. */
public final class ConfigBindingsView {
    private final Map<String, UiNode> nodes = new HashMap<>();
    private Map<?, ?> colors;

    public boolean update(UiRuntime runtime, Map<String, Object> props) {
        textChanged = false;
        nodes.clear();
        index(runtime.root());
        colors = (Map<?, ?>) props.get("colors");
        Map<?, ?> labels = (Map<?, ?>) props.get("labels");
        String selected = (String) props.get("context");
        boolean local = Boolean.TRUE.equals(props.get("local"));
        boolean editing = Boolean.TRUE.equals(props.get("fieldEditing"));
        text("profile_name", props.get("profileName"));
        text("edit_mode", labels.get(Boolean.TRUE.equals(props.get("pendingRemoval"))
                ? "deleting_assignment" : Boolean.TRUE.equals(props.get("editingExisting"))
                ? "editing_assignment" : "new_assignment"));
        text("kind", labels.get(local ? "local" : "server"));
        text("address_label", labels.get(local ? "world_folder" : "server_address"));
        String value = (String) props.get("value");
        text("address:value", editing ? "" : value.isEmpty()
                ? labels.get(local ? "world_hint" : "server_hint") : value);
        patch("address:value", Map.of("color", color(value.isEmpty() ? "muted" : "text")));
        patch("address", Map.of("stateValues", Map.of("focusVisual", editing,
                "disabled", Boolean.TRUE.equals(props.get("fieldDisabled")))));
        button("save", Boolean.TRUE.equals(props.get("canSave")));
        button("cancel", Boolean.TRUE.equals(props.get("canCancel")));
        text("save:label", props.get("saveLabel"));
        text("current_world:label", props.get("currentLabel"));
        button("remove", Boolean.TRUE.equals(props.get("canRemove")));
        button("current_world", Boolean.TRUE.equals(props.get("currentAvailable")));
        text("assigned", props.get("assignedSummary"));
        visible("empty", ((List<?>) props.get("rows")).isEmpty());
        for (Object row : (List<?>) props.get("rows")) {
            Map<?, ?> entry = (Map<?, ?>) row;
            rowSelection("rows:item:" + entry.get("context"), selected.equals(entry.get("context")));
        }
        UiNode focused = runtime.input().state().focusedNode();
        for (UiNode ancestor = focused; ancestor != null; ancestor = ancestor.parent()) {
            if (!ancestor.state().visible() || ancestor.state().disabled()) {
                runtime.input().focus(null);
                break;
            }
        }
        // Structural updates are already laid out by CachedUiScriptRuntime.
        // Property changes that affect text bounds trigger the layout in the host.
        return textChanged;
    }

    private boolean textChanged;

    private void rowSelection(String key, boolean selected) {
        patch(key, Map.of("stateValues", Map.of("selected", selected)));
        patch(key + ":bg", Map.of("fill", selected ? color("selected") : "#00000000",
                "fillReactive", Map.of("base", color("accent"), "alphaBase", selected ? .06 : 0,
                        "alphaTerms", Map.of("hover", .08, "press", .14))));
    }

    private void button(String key, boolean enabled) {
        patch(key, Map.of("stateValues", Map.of("disabled", !enabled)));
        patch(key + ":surface", Map.of("fill", color(enabled ? "surface" : "disabledSurface"),
                "stroke", color(enabled ? "border" : "disabled")));
        patch(key + ":label", Map.of("color", color(enabled ? "text" : "disabled")));
    }

    private void text(String key, Object text) {
        textChanged |= patch(key, Map.of("text", text == null ? "" : text));
    }

    private boolean patch(String key, Map<String, ?> props) {
        UiNode node = nodes.get("assignments:" + key);
        return node != null && node.patchProps(props);
    }

    private void visible(String key, boolean value) {
        UiNode node = nodes.get("assignments:" + key);
        if (node != null) node.state().setVisible(value);
    }

    private Object color(String key) {
        Object value = colors.get(key);
        return value == null ? "#FFFFFFFF" : value;
    }

    private void index(UiNode node) {
        if (node == null) return;
        nodes.put(node.key(), node);
        for (UiNode child : node.children()) index(child);
    }
}

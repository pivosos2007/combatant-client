/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.settings;

import combatant.client.events.Events;
import combatant.client.events.impl.I18nPreflightCollectEvent;
import combatant.client.util.logging.DebugLog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public enum I18nPreflightManager {
    ;

    public static void preflight(String reason) {
        if (!Setting.prepareI18nPreflightPass()) {
            DebugLog.warnOnChange(
                    "i18n.preflight.language.not.ready",
                    reason,
                    "I18n preflight skipped because language resources are not ready. reason=%s",
                    reason
            );
            return;
        }

        I18nPreflightCollectEvent event = Events.BUS.post(new I18nPreflightCollectEvent(reason));
        ReportNode root = new ReportNode("root");
        int checked = 0;
        for (I18nPreflightCollectEvent.Entry entry : event.entries()) {
            if (entry == null || entry.setting() == null) continue;
            Setting setting = entry.setting();
            ReportNode node = nodeFor(root, entry.path()).child(setting.getId());
            try (AutoCloseable ignored = Setting.captureMissingI18n(node::missing)) {
                setting.preflightI18n();
            } catch (Exception e) {
                DebugLog.warn("I18n preflight failed for %s/%s: %s", entry.path(), setting.getId(), e.getMessage());
            }
            checked++;
            node.pruneIfClean();
        }

        if (root.hasProblems()) {
            DebugLog.warn("I18n preflight missing keys (reason=%s, settings=%d):%n%s", reason, checked, root.render());
        } else {
            DebugLog.config("I18n preflight complete: reason=%s settings=%d missing=0", reason, checked);
        }
    }

    private static ReportNode nodeFor(ReportNode root, String path) {
        ReportNode node = root;
        if (path == null || path.isBlank()) return node.child("settings");
        for (String part : path.split("/")) {
            if (part == null || part.isBlank()) continue;
            node = node.child(part);
        }
        return node;
    }

    private static final class ReportNode {
        private final String label;
        private final LinkedHashMap<String, ReportNode> children = new LinkedHashMap<>();
        private final LinkedHashSet<String> missing = new LinkedHashSet<>();

        private ReportNode(String label) {
            this.label = label == null || label.isBlank() ? "<unknown>" : label;
        }

        private ReportNode child(String label) {
            String safe = label == null || label.isBlank() ? "<unknown>" : label;
            return children.computeIfAbsent(safe, ReportNode::new);
        }

        private void missing(List<String> keyChain) {
            if (keyChain == null || keyChain.isEmpty()) return;
            ArrayList<String> cleaned = new ArrayList<>(keyChain.size());
            for (String key : keyChain) {
                if (key != null && !key.isBlank()) cleaned.add(key);
            }
            if (!cleaned.isEmpty()) {
                missing.add(String.join(" -> ", cleaned));
            }
        }

        private boolean hasProblems() {
            if (!missing.isEmpty()) return true;
            for (ReportNode child : children.values()) {
                if (child.hasProblems()) return true;
            }
            return false;
        }

        private void pruneIfClean() {
            children.entrySet().removeIf(entry -> !entry.getValue().hasProblems());
        }

        private String render() {
            StringBuilder out = new StringBuilder();
            for (ReportNode child : children.values()) {
                child.render(out, 0);
            }
            return out.toString();
        }

        private void render(StringBuilder out, int depth) {
            if (!hasProblems()) return;
            indent(out, depth).append("- ").append(label).append('\n');
            for (String chain : missing) {
                indent(out, depth + 1).append("- ").append(chain).append('\n');
            }
            for (Map.Entry<String, ReportNode> entry : children.entrySet()) {
                entry.getValue().render(out, depth + 1);
            }
        }

        private static StringBuilder indent(StringBuilder out, int depth) {
            for (int i = 0; i < depth; i++) {
                out.append("  ");
            }
            return out;
        }
    }
}

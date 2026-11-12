/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.gui.clickgui.layout.screen.settings.implement.config;

import combatant.client.config.profile.ConfigProfileMeta;
import combatant.client.config.profile.ConfigProfileType;
import combatant.client.config.profile.ConfigServerBindings;
import combatant.client.features.command.CommandOutput;
import combatant.client.features.gui.clickgui.layout.screen.settings.SettingsGuiPalette;
import combatant.client.features.gui.hud.script.HudScriptLayouts;
import combatant.client.features.theme.Theme;
import combatant.client.input.InputKeys;
import combatant.client.input.InputModifierMask;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.renderer.ui.draw.LayoutRender2D;
import combatant.client.render.engine.renderer.ui.runtime.action.UiActionContext;
import combatant.client.render.engine.renderer.ui.runtime.core.UiBounds;
import combatant.client.render.engine.renderer.ui.runtime.core.UiNode;
import combatant.client.render.engine.renderer.ui.runtime.core.UiNodeType;
import combatant.client.render.engine.renderer.ui.runtime.core.UiRuntime;
import combatant.client.render.engine.renderer.ui.runtime.input.UiPointerEvent;
import combatant.client.render.engine.renderer.ui.runtime.input.UiScrollEvent;
import combatant.client.render.engine.renderer.ui.runtime.render.UiProjectionMode;
import combatant.client.render.engine.renderer.ui.runtime.render.UiRenderContext;
import combatant.client.render.engine.renderer.ui.runtime.render.UiRenderTransform;
import combatant.client.render.engine.renderer.ui.runtime.script.CachedUiScriptRuntime;
import combatant.client.render.engine.renderer.ui.runtime.script.UiScriptModuleHandle;
import combatant.client.render.engine.text.BuiltinFontCatalog;
import combatant.client.util.logging.DebugLog;
import combatant.client.util.resources.asset.UiScriptAsset;
import combatant.client.util.text.SingleLineTextInput;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Bindings are edited in the context of a profile selected on its card, never by selecting a second profile here. */
@UiScriptAsset("combatant:modules/clickgui/config_assignments")
public final class ConfigBindingsComponent {
    private final UiScriptModuleHandle module = HudScriptLayouts.handle(ConfigBindingsComponent.class);
    private final CachedUiScriptRuntime scripts = new CachedUiScriptRuntime(HudScriptLayouts.runtimeReporter(), true);
    private final ConfigBindingsView view = new ConfigBindingsView();
    private final SingleLineTextInput address = new SingleLineTextInput(180);
    private UiRuntime runtime;
    private UiRenderTransform transform = UiRenderTransform.IDENTITY;
    private ConfigProfileType type = ConfigProfileType.MODULES;
    private String profileId = "";
    private String profileName = "";
    /** Original key for an edited row. Empty means a new assignment. */
    private String editingContext = "";
    /** The kind is inferred on row selection/current-world selection, not freely changed mid-edit. */
    private boolean local;
    private boolean open, fieldFocused, pendingRemoval;
    private Map<String, String> assignments = Map.of();
    private float reveal;

    private boolean modelDirty = true;
    private boolean structureDirty = true;
    private Map<String, Object> cachedProps = Map.of();
    private long structureSignature;
    private float lastWidth = Float.NaN;
    private float lastHeight = Float.NaN;
    private String lastThemeId;
    private UiRuntime appliedRuntime;
    private long appliedRevision = Long.MIN_VALUE;
    private boolean viewDirty = true;

    public void open(ConfigProfileType type, ConfigProfileMeta profile) {
        // This view is never opened without an actual config card.
        if (profile == null || type != profile.type()) return;
        this.type = type;
        profileId = profile.getId();
        profileName = profile.name();
        newServer();
        assignments = ConfigServerBindings.INSTANCE.bindingsFor(type);
        structureDirty = true;
        modelDirty = true;
        runtime = null;
        appliedRuntime = null;
        appliedRevision = Long.MIN_VALUE;
        viewDirty = true;
        open = true;
    }

    public void close() {
        open = false;
        blurField();
        if (runtime != null) runtime.input().focus(null);
    }

    public boolean isActive() {
        return open || reveal > 0f;
    }

    public void render(float x, float y, float w, float h, float mx, float my) {
        reveal = AnimationUtility.snap(AnimationUtility.approach(reveal, open ? 1f : 0f,
                AnimationUtility.deltaTime(), 14f), open ? 1f : 0f, 0.005f);
        if (!isActive()) {
            runtime = null;
            return;
        }
        transform = UiRenderTransform.scaleAt(x, y, 1f);
        if (!module.ensureLoaded(Minecraft.getInstance().getResourceManager())) {
            HudScriptLayouts.reportLoadError(module);
            return;
        }
        if (module.consumeChanged()) {
            scripts.reset();
            structureDirty = true;
            modelDirty = true;
            appliedRevision = Long.MIN_VALUE;
        }
        String currentThemeId = Theme.currentId();
        if (Float.compare(lastWidth, w) != 0 || Float.compare(lastHeight, h) != 0
                || !Objects.equals(lastThemeId, currentThemeId)) {
            structureDirty = true;
            modelDirty = true;
            lastWidth = w;
            lastHeight = h;
            lastThemeId = currentThemeId;
        }
        if (modelDirty || structureDirty) {
            cachedProps = props();
            modelDirty = false;
            viewDirty = true;
            if (structureDirty) {
                structureSignature = CachedUiScriptRuntime.signature(Map.of("width", w, "height", h,
                        "rows", cachedProps.get("rows"), "labels", cachedProps.get("labels"),
                        "colors", cachedProps.get("colors")));
                structureDirty = false;
            }
        }
        UiRuntime nextRuntime = scripts.bake(module, module.module(), "assignments", structureSignature,
                w, h, BuiltinFontCatalog.ONEST_REGULAR.renderer(), 0, 0, w, h, () -> cachedProps);
        if (nextRuntime == null) return;
        runtime = nextRuntime;
        long revision = runtime.treeRevision();
        if (runtime != appliedRuntime || revision != appliedRevision) {
            appliedRuntime = runtime;
            appliedRevision = revision;
            viewDirty = true;
            registerActions();
        }
        if (viewDirty) {
            if (view.update(runtime, cachedProps))
                runtime.layout(BuiltinFontCatalog.ONEST_REGULAR.renderer(), 0, 0, w, h);
            viewDirty = false;
        }
        runtime.input().setCoordinateTransform(transform);
        float alpha = reveal * LayoutRender2D.getRenderAlphaMultiplier();
        LayoutRender2D.flushRenderer();
        runtime.render(new UiRenderContext(Renderer2D.COLOR, BuiltinFontCatalog.ONEST_REGULAR.renderer(),
                null, 0f, UiProjectionMode.CURRENT, alpha, transform),
                () -> { if (open) runtime.input().updateHover(runtime.root(), mx, my); });
        LayoutRender2D.flushRenderer();
        renderField(alpha);
    }

    private Map<String, Object> props() {
        String current = Minecraft.getInstance().level == null ? "" : ConfigServerBindings.currentAddress();
        String target = context();
        String assigned = assignments.get(target);
        var rows = new ArrayList<Map<String, String>>();
        assignments.forEach((key, id) -> {
            if (!profileId.equals(id)) return;
            boolean world = key.startsWith("local:");
            rows.add(Map.of("context", key,
                    "label", world ? key.substring(6) : key,
                    "description", tr(world ? "local" : "server")));
        });
        var labels = new LinkedHashMap<String, String>();
        for (String key : List.of("back", "title", "connections", "empty_profile", "server", "local",
                "world_folder", "server_address", "world_hint", "server_hint", "current_world",
                "new_server", "current_server", "save", "remove", "cancel", "new_assignment",
                "editing_assignment", "deleting_assignment", "empty_target", "invalid_target",
                "unassigned", "assigned_other", "assigned_here", "unsaved_move", "unsaved_new",
                "unsaved_remove", "unchanged", "remove_confirm", "discard_hint")) labels.put(key, tr(key));
        var palette = SettingsGuiPalette.current();
        var colors = Map.ofEntries(
                Map.entry("background", css(palette.panelBgLeft() | 0xFF000000)),
                Map.entry("surface", css(palette.moduleCardTop())),
                Map.entry("raised", css(palette.panelPillBase())),
                Map.entry("disabled", css(palette.panelMuted())),
                Map.entry("disabledSurface", css(palette.panelPillBase())),
                Map.entry("field", css(palette.panelPillBase())),
                Map.entry("border", css(palette.panelStroke())),
                Map.entry("text", css(palette.panelText())),
                Map.entry("muted", css(palette.panelMuted())),
                Map.entry("accent", css(palette.menuCategorySelectedRight())),
                Map.entry("selected", css(palette.menuCategorySelectedLeft())),
                Map.entry("onAccent", css(palette.menuCategoryText())));
        var props = new LinkedHashMap<String, Object>();
        boolean editingExisting = !editingContext.isEmpty();
        boolean valid = validContext();
        boolean changed = editingExisting && !target.equals(editingContext);
        boolean alreadyHere = profileId.equals(assigned);
        boolean canSave = pendingRemoval || (valid && (changed || !alreadyHere));
        String summary;
        if (pendingRemoval) summary = tr("unsaved_remove");
        else if (address.text().isBlank()) summary = tr("empty_target");
        else if (!valid) summary = tr("invalid_target");
        else if (assigned != null && !alreadyHere) summary = tr("assigned_other");
        else if (changed) summary = tr("unsaved_move");
        else if (editingExisting || alreadyHere) summary = tr("unchanged");
        else summary = tr("unsaved_new");
        props.put("labels", labels);
        props.put("colors", colors);
        props.put("rows", rows);
        props.put("profileName", profileName);
        props.put("context", editingContext);
        props.put("value", address.text());
        props.put("fieldEditing", fieldFocused && !pendingRemoval);
        props.put("fieldDisabled", pendingRemoval);
        props.put("local", local);
        props.put("currentAvailable", !current.isEmpty());
        props.put("currentLabel", tr(current.startsWith("local:") ? "current_world" : "current_server"));
        props.put("editingExisting", editingExisting);
        props.put("pendingRemoval", pendingRemoval);
        props.put("assignedSummary", summary);
        props.put("saveLabel", pendingRemoval ? tr("remove_confirm") : tr("save"));
        props.put("canSave", canSave);
        props.put("canCancel", pendingRemoval || (editingExisting ? changed : !address.text().isEmpty()));
        props.put("canRemove", editingExisting && !pendingRemoval
                && profileId.equals(assignments.get(editingContext)));
        return props;
    }

    private void registerActions() {
        runtime.actions().clear();
        action("back", this::close);
        action("new_server", () -> {
            newServer();
            focusAddressEditor(false);
        });
        action("current_world", () -> {
            String current = Minecraft.getInstance().level == null ? "" : ConfigServerBindings.currentAddress();
            if (current.isEmpty()) return;
            // The quick action creates a draft for another target; it does not silently
            // relocate the selected existing assignment.
            editingContext = "";
            pendingRemoval = false;
            setAddress(current);
            focusAddressEditor(true);
        });
        action("cancel", this::discardDraft);
        action("save", () -> operation(() -> {
            if (pendingRemoval) {
                if (editingContext.isEmpty()
                        || !profileId.equals(ConfigServerBindings.INSTANCE.boundProfile(type, editingContext)))
                    return;
                ConfigServerBindings.INSTANCE.unbind(type, editingContext);
                assignments = ConfigServerBindings.INSTANCE.bindingsFor(type);
                newServer();
                structureDirty = true;
                CommandOutput.send(tr("removed"));
                return;
            }
            if (!validContext()) return;
            String target = context();
            if (target.equals(editingContext) || (editingContext.isEmpty()
                    && profileId.equals(assignments.get(target)))) return;
            ConfigServerBindings.INSTANCE.replace(type, profileId, editingContext, target);
            assignments = ConfigServerBindings.INSTANCE.bindingsFor(type);
            editingContext = target;
            pendingRemoval = false;
            setAddress(target);
            structureDirty = true;
            CommandOutput.send(tr("saved"));
        }));
        // Removal is a staged change. Nothing is written until Save is pressed.
        action("remove", () -> {
            if (editingContext.isEmpty() || !profileId.equals(assignments.get(editingContext))) return;
            pendingRemoval = true;
        });
        assignments.forEach((key, id) -> {
            if (profileId.equals(id)) action("edit:" + key, () -> {
                editingContext = key;
                pendingRemoval = false;
                setAddress(key);
                focusAddressEditor(true);
            });
        });
    }

    private void discardDraft() {
        pendingRemoval = false;
        if (editingContext.isEmpty()) setAddress("");
        else setAddress(editingContext);
    }

    private void action(String id, Runnable action) {
        runtime.actions().register("assignments:" + id, context -> {
            if (!open) return true;
            if (!id.equals("address")) blurField();
            action.run();
            modelDirty = true;
            return true;
        });
    }

    private String context() {
        return ConfigServerBindings.normalize((local ? "local:" : "") + address.text());
    }

    private boolean validContext() {
        String normalized = context();
        return !address.text().isBlank() && !normalized.equals("local:")
                && normalized.indexOf('|') < 0 && normalized.indexOf('\n') < 0 && normalized.indexOf('\r') < 0
                && (local || !normalized.startsWith("local:"));
    }

    private void newServer() {
        editingContext = "";
        pendingRemoval = false;
        setAddress("");
    }

    private void setAddress(String context) {
        blurField();
        String normalized = ConfigServerBindings.normalize(context);
        local = normalized.startsWith("local:");
        address.setText(local ? normalized.substring(6) : normalized);
        address.moveToEnd();
        modelDirty = true;
    }

    /** Reuse the shared text input's caret, selection, clipboard and drag support. */
    private void focusAddressEditor(boolean selectAll) {
        if (runtime == null || pendingRemoval) return;
        UiNode fieldNode = find(runtime.root(), "assignments:address");
        if (fieldNode == null) return;
        runtime.input().focus(fieldNode);
        UiBounds bounds = transform.bounds(fieldNode.bounds());
        address.layout(bounds.x() + 15f, bounds.width() - 30f,
                BuiltinFontCatalog.ONEST_REGULAR.renderer(), 20f);
        if (selectAll) address.selectAll();
        else address.moveToEnd();
        fieldFocused = true;
        modelDirty = true;
    }

    private void blurField() {
        fieldFocused = false;
        address.unfocus();
        modelDirty = true;
    }

    private void renderField(float alpha) {
        if (!fieldFocused || pendingRemoval || runtime == null) return;
        UiNode node = find(runtime.root(), "assignments:address");
        if (node == null) return;
        UiBounds field = transform.bounds(node.bounds());
        var palette = SettingsGuiPalette.current();
        address.render(field.x() + 15f, field.y() + 16f,
                field.width() - 30f, 30f, BuiltinFontCatalog.ONEST_REGULAR.renderer(),
                20f, LayoutRender2D.alpha(palette.panelText(), alpha),
                LayoutRender2D.alpha(palette.menuCategorySelectedRight(), alpha * 0.5f), open);
    }

    public boolean click(float mx, float my, int button) {
        if (!open || runtime == null || button != InputKeys.MOUSE_LEFT) return isActive();
        UiPointerEvent event = new UiPointerEvent(mx, my, 0);
        var input = runtime.input();
        UiNode previous = input.state().pressedNode();
        if (previous != null) previous.state().setActive(false);
        input.state().setPressedNode(null);
        input.pointerDown(runtime.root(), event);
        UiNode pressed = input.state().pressedNode();
        if (pressed != null) pressed.state().setActive(false);
        input.state().setPressedNode(null);

        // SpectrumTextField contains a viewport stack which can be the deepest hit.
        // Its button is an ancestor, so checking pressed.key() loses field clicks.
        UiNode fieldNode = find(runtime.root(), "assignments:address");
        if (!pendingRemoval && fieldNode != null && contains(transform.bounds(fieldNode.bounds()), mx, my)) {
            focusAddressEditor(false);
            address.beginDrag(mx, false);
            modelDirty = true;
            return true;
        }
        // Only real action nodes dispatch actions. Never dispatch on a nested
        // viewport/stack with no action, or accidentally focus the text editor.
        String selectedAction = pressed == null ? null : pressed.events().get("click");
        if (selectedAction != null && !selectedAction.isBlank())
            runtime.actions().dispatch(selectedAction, new UiActionContext(pressed, null, event));
        else blurField();
        modelDirty = true;
        return true;
    }

    private static boolean contains(UiBounds bounds, float x, float y) {
        return x >= bounds.x() && x <= bounds.x() + bounds.width()
                && y >= bounds.y() && y <= bounds.y() + bounds.height();
    }

    public void mouseReleased(float mx, float my, int button) {
        if (open && runtime != null && button == InputKeys.MOUSE_LEFT)
            runtime.input().pointerUp(runtime.root(), new UiPointerEvent(mx, my, 0));
    }

    public void scroll(float mx, float my, double amount) {
        if (open && runtime != null)
            runtime.input().scroll(runtime.root(), new UiScrollEvent(mx, my, 0, -amount));
    }

    public boolean keyPressed(int key, int scan, int modifiers) {
        if (!open) return isActive();
        modelDirty = true;
        if (key == InputKeys.ESCAPE) {
            if (fieldFocused) blurField();
            else close();
        } else if (runtime == null) {
            return true;
        } else if (key == InputKeys.TAB) {
            var controls = new ArrayList<UiNode>();
            focusable(runtime.root(), controls);
            if (!controls.isEmpty()) {
                int index = controls.indexOf(runtime.input().state().focusedNode());
                int step = (modifiers & InputModifierMask.SHIFT) != 0 ? -1 : 1;
                UiNode next = controls.get(Math.floorMod(index + step, controls.size()));
                blurField();
                runtime.input().focus(next);
                fieldFocused = next.key().equals("assignments:address");
                revealFocused(next);
            }
        } else if (fieldFocused) {
            if (key == InputKeys.ENTER || key == InputKeys.KP_ENTER) blurField();
            else address.keyPressed(key, scan, modifiers);
        } else if (key == InputKeys.ENTER || key == InputKeys.KP_ENTER || key == InputKeys.SPACE) {
            UiNode focused = runtime.input().state().focusedNode();
            if (focused != null && !focused.state().disabled()) runtime.actions().dispatch(
                    focused.events().get("click"), new UiActionContext(focused, null, null));
        }
        return true;
    }

    public boolean charTyped(char chr) {
        if (open && fieldFocused && !pendingRemoval) {
            address.type(chr);
            modelDirty = true;
        }
        return isActive();
    }

    private void focusable(UiNode node, List<UiNode> result) {
        if (node == null || !node.state().visible()) return;
        if (!node.state().disabled() && node.events().containsKey("click")) result.add(node);
        for (UiNode child : node.children()) focusable(child, result);
    }

    private static void revealFocused(UiNode node) {
        UiNode scroll = node.parent();
        while (scroll != null && scroll.type() != UiNodeType.SCROLL) scroll = scroll.parent();
        if (scroll == null) return;
        float delta = node.bounds().y() < scroll.bounds().y() ? node.bounds().y() - scroll.bounds().y()
                : Math.max(0f, node.bounds().y() + node.bounds().height()
                - scroll.bounds().y() - scroll.bounds().height());
        scroll.state().setTargetScroll(0, Math.max(0, scroll.state().targetScrollY() + delta));
    }

    private static UiNode find(UiNode node, String key) {
        if (node == null || node.key().equals(key)) return node;
        for (UiNode child : node.children()) {
            UiNode found = find(child, key);
            if (found != null) return found;
        }
        return null;
    }

    private static String css(int color) { return String.format("#%08X", color); }
    private static String tr(String key, Object... args) {
        return I18n.get("clickgui.settings.config.assignments." + key, args);
    }

    private static void operation(IoAction action) {
        try {
            action.run();
        } catch (IOException | RuntimeException error) {
            CommandOutput.send(tr("failed"));
            DebugLog.error("Config assignment operation failed", error);
        }
    }

    @FunctionalInterface
    private interface IoAction { void run() throws IOException; }
}

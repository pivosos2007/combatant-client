/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.gui.clickgui.settings;

import combatant.client.render.engine.text.BuiltinFontCatalog;

import combatant.client.render.engine.renderer.ui.draw.LayoutRender2D;

import combatant.client.config.ConfigPaths;
import combatant.client.config.ConfigurationRootManager;
import combatant.client.features.gui.hud.script.HudScriptLayouts;
import combatant.client.features.theme.Theme;
import combatant.client.input.InputKeys;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.renderer.ui.runtime.core.UiRuntime;
import combatant.client.render.engine.renderer.ui.runtime.input.UiPointerEvent;
import combatant.client.render.engine.renderer.ui.runtime.render.UiProjectionMode;
import combatant.client.render.engine.renderer.ui.runtime.render.UiRenderContext;
import combatant.client.render.engine.renderer.ui.runtime.script.CachedUiScriptRuntime;
import combatant.client.render.engine.renderer.ui.runtime.script.UiScriptModuleHandle;
import combatant.client.util.filedialog.NativeFileDialogService;
import combatant.client.util.resources.asset.UiScriptAsset;
import combatant.client.util.text.ClipboardUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.util.Util;

import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

@UiScriptAsset("combatant:modules/clickgui/config_directory_setting")
public final class ConfigurationDirectorySetting extends Setting {
    private final UiScriptModuleHandle moduleHandle = HudScriptLayouts.handle(ConfigurationDirectorySetting.class);
    private final CachedUiScriptRuntime scripts = new CachedUiScriptRuntime(HudScriptLayouts.runtimeReporter());
    private UiRuntime runtime;
    private Path proposed;
    private String error = "";
    private boolean busy;
    private float x, y, width;

    public ConfigurationDirectorySetting() {
        super("configurationDirectory");
        // The label and controls are translated/rendered by the Spectrum 2 script.
        setI18nEnabled(false, false);
    }

    @Override
    public String getDisplayName() {
        return I18n.get("config.directory.title");
    }

    @Override
    public float getHeight() {
        boolean pending = isPending();
        return 186f + (pending ? 124f : proposed != null ? 204f : 0f) + (error.isBlank() ? 0f : 65f);
    }

    @Override
    public void render(float x, float y, float width, float mouseX, float mouseY) {
        this.x = x;
        this.y = y;
        this.width = width;
        Minecraft minecraft = Minecraft.getInstance();
        if (!moduleHandle.ensureLoaded(minecraft.getResourceManager())) {
            HudScriptLayouts.reportLoadError(moduleHandle);
            return;
        }
        if (moduleHandle.consumeChanged()) scripts.reset();
        ConfigurationRootManager manager = ConfigPaths.manager();
        boolean pending = isPending();
        Path pendingDestination = manager.pendingDestination();
        boolean defaultActive = manager.root().equals(manager.defaultRoot());
        String state = manager.migrationState();
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("width", width);
        props.put("busy", busy);
        props.put("pending", pending);
        props.put("canCancel", "PREPARING".equals(state));
        props.put("confirming", !pending && proposed != null);
        props.put("current", manager.root().toString());
        props.put("destination", (pending ? pendingDestination : proposed) == null ? "" :
                (pending ? pendingDestination : proposed).toString());
        props.put("defaultActive", defaultActive);
        props.put("error", error);
        props.put("colors", Map.of(
                "accent", color(Theme.theme().accent()),
                "onAccent", contrast(Theme.theme().accent()),
                "selected", color(Theme.theme().accentSoft())
        ));
        for (String key : new String[] {"title", "currentLabel", "change", "reset", "hint",
                "confirmTitle", "fromLabel", "toLabel", "confirm", "cancel", "pendingTitle",
                "cancelPending", "failureTitle"}) {
            String translationKey = switch (key) {
                case "currentLabel" -> "current";
                case "fromLabel" -> "from";
                case "toLabel" -> "to";
                case "pendingTitle" -> "pending";
                case "failureTitle" -> "failure";
                default -> key;
            };
            props.put(key, I18n.get("config.directory." + translationKey));
        }
        if (pending) props.put("hint", I18n.get("config.directory.pendingHint"));
        float height = getHeight();
        runtime = scripts.bakeTracked(moduleHandle, moduleHandle.module(), "configuration-directory",
                width, height, BuiltinFontCatalog.ONEST_MEDIUM.renderer(),
                x, y, width, height, () -> props);
        if (runtime == null) return;
        runtime.actions().register("configdir:change", action -> { chooseDirectory(); return true; });
        runtime.actions().register("configdir:restore", action -> { proposeDefault(); return true; });
        runtime.actions().register("configdir:confirm", action -> { confirm(); return true; });
        runtime.actions().register("configdir:cancel", action -> { proposed = null; error = ""; return true; });
        runtime.actions().register("configdir:cancel-pending", action -> { cancelPending(); return true; });
        LayoutRender2D.flushRenderer();
        runtime.render(new UiRenderContext(Renderer2D.COLOR, BuiltinFontCatalog.ONEST_MEDIUM.renderer(),
                null, 0f, UiProjectionMode.CURRENT, 1f),
                () -> runtime.input().updateHover(runtime.root(), mouseX, mouseY));
        LayoutRender2D.flushRenderer();
    }

    private static String color(int argb) { return String.format("#%08X", argb); }

    private static String contrast(int argb) {
        double l = 0;
        for (int i = 0; i < 3; i++) {
            double v = ((argb >>> (16 - i * 8)) & 255) / 255.0;
            double weight = i == 0 ? .2126 : i == 1 ? .7152 : .0722;
            l += weight * (v <= .04045 ? v / 12.92 : Math.pow((v + .055) / 1.055, 2.4));
        }
        return l > .179 ? "#FF000000" : "#FFFFFFFF";
    }

    private boolean isPending() {
        String state = ConfigPaths.manager().migrationState();
        return !state.equals("IDLE") && !state.equals("COMMITTED");
    }

    private void chooseDirectory() {
        if (busy || isPending()) return;
        ConfigurationRootManager manager = ConfigPaths.manager();
        Path initial = manager.root().getParent();
        busy = true;
        error = "";
        NativeFileDialogService.pickFolder(initial, Minecraft.getInstance()::schedule)
                .whenComplete((result, exception) -> Minecraft.getInstance().schedule(() -> {
                    busy = false;
                    if (exception != null) {
                        error = message(exception);
                        return;
                    }
                    if (result.status() == NativeFileDialogService.Status.CANCELLED) return;
                    if (result.status() != NativeFileDialogService.Status.SELECTED) {
                        error = result.detail().isBlank() ? result.status().name() : result.detail();
                        return;
                    }
                    // Destination validation may query a remote filesystem.
                    // Keep it off Minecraft's render/client thread.
                    busy = true;
                    Util.nonCriticalIoPool().execute(() -> {
                        Path target = null;
                        Exception failure = null;
                        try {
                            // A picked folder is a PARENT. Never scatter files into arbitrary folders.
                            target = manager.destinationUnder(result.path());
                        } catch (Exception e) {
                            failure = e;
                        }
                        Path checkedTarget = target;
                        Exception checkedFailure = failure;
                        Minecraft.getInstance().schedule(() -> {
                            busy = false;
                            if (checkedFailure != null) error = message(checkedFailure);
                            else if (checkedTarget.equals(manager.root()))
                                error = I18n.get("config.directory.samePath");
                            else proposed = checkedTarget;
                        });
                    });
                }));
    }

    private void proposeDefault() {
        if (busy || isPending()) return;
        ConfigurationRootManager manager = ConfigPaths.manager();
        if (manager.root().equals(manager.defaultRoot())) return;
        proposed = manager.defaultRoot();
        error = "";
    }

    private void confirm() {
        if (busy || isPending() || proposed == null) return;
        ConfigurationRootManager manager = ConfigPaths.manager();
        Path target = proposed;
        busy = true;
        error = "";
        // Filesystem preflight may touch remote mounts. Never block the rendering thread.
        Util.nonCriticalIoPool().execute(() -> {
            Exception failure = null;
            try {
                manager.schedule(target);
            } catch (Exception e) {
                failure = e;
            }
            Exception finalFailure = failure;
            Minecraft.getInstance().schedule(() -> {
                busy = false;
                if (finalFailure == null) proposed = null;
                else error = message(finalFailure);
            });
        });
    }

    private void cancelPending() {
        if (busy || !"PREPARING".equals(ConfigPaths.manager().migrationState())) return;
        busy = true;
        Util.nonCriticalIoPool().execute(() -> {
            Exception failure = null;
            try { ConfigPaths.manager().cancelScheduled(); }
            catch (Exception e) { failure = e; }
            Exception finalFailure = failure;
            Minecraft.getInstance().schedule(() -> {
                busy = false;
                error = finalFailure == null ? "" : message(finalFailure);
            });
        });
    }

    private static String message(Throwable failure) {
        Throwable cause = failure;
        while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
        if (cause instanceof FileAlreadyExistsException) {
            return I18n.get("config.directory.destinationExists", cause.getMessage());
        }
        String detail = cause.getMessage();
        return detail == null || detail.isBlank() ? cause.toString() : detail;
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (button == InputKeys.MOUSE_RIGHT && mouseX >= x && mouseX <= x + width
                && mouseY >= y + 48f && mouseY <= y + 112f) {
            ClipboardUtil.copy(ConfigPaths.manager().root().toString());
            return;
        }
        if (runtime != null && button == InputKeys.MOUSE_LEFT) {
            runtime.input().pointerDown(runtime.root(), new UiPointerEvent((float) mouseX, (float) mouseY, 0));
        }
    }

    @Override
    public void mouseReleased(double mouseX, double mouseY, int button) {
        if (runtime != null && button == InputKeys.MOUSE_LEFT) {
            runtime.input().pointerUp(runtime.root(), new UiPointerEvent((float) mouseX, (float) mouseY, 0));
        }
    }
}

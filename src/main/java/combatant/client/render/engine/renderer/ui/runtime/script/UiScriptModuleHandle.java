/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.script;

import net.minecraft.server.packs.resources.ResourceManager;
import combatant.client.util.logging.DebugLog;

import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

public final class UiScriptModuleHandle {
    private final UiScriptModuleId id;
    private final UiScriptModuleLoader loader;
    private UiScriptModule module;
    private long checksum = Long.MIN_VALUE;
    private boolean changed;
    private String lastError = "";
    private Throwable lastErrorCause;
    private boolean runtimeBlocked;
    private String lastReportedError = "";

    public UiScriptModuleHandle(UiScriptModuleId id) {
        this(id, new UiScriptModuleLoader());
    }

    public UiScriptModuleHandle(UiScriptModuleId id, UiScriptModuleLoader loader) {
        this.id = id != null ? id : UiScriptModuleId.of("combatant:main");
        this.loader = loader != null ? loader : new UiScriptModuleLoader();
        DebugLog.renderThread("[UI Scripts] handle init id=%s", this.id);
    }

    private static long checksum(UiScriptModule module) {
        CRC32 crc = new CRC32();
        if (module != null && module.source() != null) {
            byte[] bytes = module.source().getBytes(StandardCharsets.UTF_8);
            crc.update(bytes, 0, bytes.length);
        }
        return crc.getValue();
    }

    public UiScriptModuleId id() {
        return id;
    }

    public UiScriptModuleId getId() {
        return id;
    }

    public UiScriptModule module() {
        return module;
    }

    public String lastError() {
        return lastError;
    }

    public Throwable lastErrorCause() {
        return lastErrorCause;
    }

    public boolean isRuntimeBlocked() {
        return runtimeBlocked;
    }

    public boolean recordRuntimeError(UiScriptRuntimeError error) {
        runtimeBlocked = true;
        String phase = error != null && error.phase() != null && !error.phase().isBlank() ? error.phase() : "runtime";
        String message = error != null && error.message() != null && !error.message().isBlank() ? error.message() : "unknown error";
        lastError = phase + ": " + message;
        lastErrorCause = error != null ? error.cause() : null;
        return shouldReport(lastError);
    }

    public boolean recordLoadError(String message, Throwable cause) {
        String line = message != null && !message.isBlank() ? message : "unknown error";
        lastError = "load: " + line;
        lastErrorCause = cause;
        return shouldReport(lastError);
    }

    public boolean ensureLoaded(ResourceManager manager) {
        if (module != null) {
            return true;
        }
        return reload(manager, true) == ReloadResult.CHANGED;
    }

    public ReloadResult reloadIfChanged(ResourceManager manager) {
        return reload(manager, false);
    }

    public boolean consumeChanged() {
        boolean out = changed;
        changed = false;
        return out;
    }

    private ReloadResult reload(ResourceManager manager, boolean initial) {
        if (manager == null) {
            lastError = "ResourceManager is null.";
            lastErrorCause = null;
            return ReloadResult.ERROR;
        }
        try {
            DebugLog.renderThread("[UI Scripts] load begin id=%s initial=%s", id, initial);
            UiScriptModule fresh = loader.load(manager, id);
            long freshChecksum = checksum(fresh);
            if (!initial && module != null && freshChecksum == checksum) {
                lastError = "";
                lastErrorCause = null;
                DebugLog.renderThread("[UI Scripts] load unchanged id=%s checksum=%s resource=%s",
                        id, Long.toUnsignedString(checksum, 16), module.metadata().string("resource", ""));
                return ReloadResult.UNCHANGED;
            }
            module = fresh;
            checksum = freshChecksum;
            changed = true;
            lastError = "";
            lastErrorCause = null;
            runtimeBlocked = false;
            lastReportedError = "";
            DebugLog.renderThread("[UI Scripts] load ok id=%s initial=%s checksum=%s bytes=%d kind=%s resource=%s",
                    id,
                    initial,
                    Long.toUnsignedString(checksum, 16),
                    fresh.source() != null ? fresh.source().getBytes(StandardCharsets.UTF_8).length : 0,
                    fresh.sourceKind(),
                    fresh.metadata().string("resource", ""));
            return ReloadResult.CHANGED;
        } catch (Exception e) {
            lastError = e.getMessage();
            lastErrorCause = e;
            return ReloadResult.ERROR;
        }
    }

    private boolean shouldReport(String line) {
        String key = line != null && !line.isBlank() ? line : "unknown error";
        if (key.equals(lastReportedError)) return false;
        lastReportedError = key;
        return true;
    }

    public enum ReloadResult {
        CHANGED,
        UNCHANGED,
        ERROR
    }
}

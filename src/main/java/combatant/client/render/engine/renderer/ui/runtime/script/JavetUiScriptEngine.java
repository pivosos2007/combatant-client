/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.script;

import com.caoccao.javet.exceptions.JavetException;
import com.caoccao.javet.interop.V8Host;
import com.caoccao.javet.interop.V8Runtime;
import com.caoccao.javet.interop.converters.JavetObjectConverter;
import combatant.client.render.engine.renderer.ui.runtime.core.UiNodeSpec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class JavetUiScriptEngine implements UiScriptEngine {
    private static final String BOOTSTRAP_SOURCE = """
            globalThis.global = globalThis;
            globalThis.console = globalThis.console || {
              log() {}, info() {}, warn() {}, error() {}, debug() {}, trace() {}
            };
            globalThis.__ui_default = undefined;
            globalThis.__combatant_render = undefined;
            globalThis.__combatant_render_ready = false;
            """;

    private static final String RESOLVE_RENDER_SOURCE = """
            globalThis.__combatant_render = (() => {
              const direct = globalThis.render;
              if (typeof direct === "function") return direct;

              const template = globalThis.buildTemplate;
              if (typeof template === "function") return template;

              const defaultExport = globalThis.__ui_default;
              if (typeof defaultExport === "function") return defaultExport;
              if (defaultExport && typeof defaultExport.render === "function") return defaultExport.render.bind(defaultExport);
              if (defaultExport && typeof defaultExport.buildTemplate === "function") return defaultExport.buildTemplate.bind(defaultExport);

              const commonModule = globalThis.module;
              const exports = commonModule && commonModule.exports;
              if (typeof exports === "function") return exports;
              if (exports && typeof exports.render === "function") return exports.render.bind(exports);
              if (exports && typeof exports.buildTemplate === "function") return exports.buildTemplate.bind(exports);
              return undefined;
            })();
            globalThis.__combatant_render_ready = typeof globalThis.__combatant_render === "function";
            """;

    private final UiScriptObjectConverter converter = new UiScriptObjectConverter();
    private V8Runtime runtime;
    private UiScriptModule loadedModule;
    private String loadedSource = "";

    @Override
    public UiScriptRenderResult render(UiScriptModule module, UiScriptRenderContext context) {
        if (module == null) {
            return UiScriptRenderResult.failed(new UiScriptRuntimeError(null, "load", "Script module is null.", null));
        }

        try {
            prepare(module);
            if (!runtime.getGlobalObject().getBoolean("__combatant_render_ready")) {
                return UiScriptRenderResult.failed(new UiScriptRuntimeError(module.getId(), "render", "Script module does not export render(ctx).", null));
            }

            Object raw = runtime.getGlobalObject().invokeObject("__combatant_render", contextMap(context));
            UiNodeSpec root = converter.convert(raw);
            return UiScriptRenderResult.ok(root);
        } catch (Throwable t) {
            return UiScriptRenderResult.failed(new UiScriptRuntimeError(module.getId(), "render", message(t), t));
        }
    }

    @Override
    public void close() {
        closeRuntime();
    }

    private void prepare(UiScriptModule module) throws JavetException {
        if (isLoaded(module)) return;
        closeRuntime();

        JavetNativeResourceLoader.install();
        runtime = V8Host.getV8Instance().createV8Runtime();
        runtime.setConverter(new JavetObjectConverter());
        runtime.setMemorySaverModeEnabled(false);
        runtime.setBatterySaverModeEnabled(false);
        runtime.getExecutor(BOOTSTRAP_SOURCE).executeVoid();
        runtime.getExecutor("globalThis.ui = " + UiScriptRuntimeSource.UI_FACTORY + ";").executeVoid();
        runtime.getExecutor(executableSource(module)).setResourceName(module.getId().toString()).executeVoid();
        runtime.getExecutor(RESOLVE_RENDER_SOURCE).executeVoid();
        loadedModule = module;
        loadedSource = module.source();
    }

    private boolean isLoaded(UiScriptModule module) {
        return runtime != null
                && loadedModule != null
                && module != null
                && Objects.equals(loadedModule.getId(), module.getId())
                && loadedModule.sourceKind() == module.sourceKind()
                && Objects.equals(loadedSource, module.source());
    }

    private static String executableSource(UiScriptModule module) {
        String source = module.sourceKind() == UiScriptSourceKind.TYPESCRIPT
                ? UiTypeScriptStripper.strip(module.source())
                : module.source();
        return UiScriptModuleTransform.toExecutableScript(source);
    }

    private static Map<String, Object> contextMap(UiScriptRenderContext context) {
        UiScriptRenderContext ctx = context != null
                ? context
                : new UiScriptRenderContext(0L, 0.0, 0.0, 0.0f, 0.0f, null);
        Map<String, Object> map = new LinkedHashMap<>(6);
        map.put("frame", jsNumber(ctx.frame()));
        map.put("time", ctx.time());
        map.put("delta", ctx.delta());
        map.put("width", ctx.width());
        map.put("height", ctx.height());
        map.put("props", plainValue(ctx.props() != null ? ctx.props().values() : Map.of()));
        return map;
    }

    static Object plainValue(Object value) {
        if (value instanceof Map<?, ?> rawMap) {
            Map<String, Object> map = new LinkedHashMap<>(rawMap.size());
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                if (entry.getKey() != null) {
                    map.put(String.valueOf(entry.getKey()), plainValue(entry.getValue()));
                }
            }
            return map;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> list = new ArrayList<>();
            for (Object item : iterable) {
                list.add(plainValue(item));
            }
            return list;
        }
        if (value instanceof Object[] array) {
            List<Object> list = new ArrayList<>(array.length);
            for (Object item : array) {
                list.add(plainValue(item));
            }
            return list;
        }
        if (value instanceof Long number) {
            return jsNumber(number);
        }
        return value;
    }

    private static Number jsNumber(long value) {
        if (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
            return (int) value;
        }
        // UI scripts operate on JavaScript Number values. Passing Long directly makes
        // Javet expose it as bigint, which fails ordinary numeric guards (`typeof === "number"`).
        return (double) value;
    }

    private void closeRuntime() {
        loadedModule = null;
        loadedSource = "";
        if (runtime == null) return;
        try {
            runtime.close();
        } catch (Throwable ignored) {
        } finally {
            runtime = null;
        }
    }

    private static String message(Throwable t) {
        String message = t != null ? t.getMessage() : null;
        return message != null && !message.isBlank() ? message : String.valueOf(t);
    }
}

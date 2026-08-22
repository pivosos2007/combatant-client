/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

/* HoldMyItems Recode; original project by sapling, CC0-1.0. */
package combatant.client.features.hmi_recode.script;

import com.caoccao.javet.interop.V8Host;
import com.caoccao.javet.interop.V8Runtime;
import com.caoccao.javet.interop.converters.JavetObjectConverter;
import combatant.client.features.hmi_recode.HmiScriptKind;
import combatant.client.features.hmi_recode.render.HmiModelCommand;
import combatant.client.features.hmi_recode.render.HmiTransformCommand;
import combatant.client.render.engine.profiler.ProfilerPhase;
import combatant.client.render.engine.renderer.ui.runtime.script.JavetRuntimeBootstrap;
import combatant.client.util.logging.DebugLog;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Isolated HMI V8 runtime. Resource-pack scripts receive plain JS data only;
 * no Java object proxy is exposed to V8.
 */
public final class HmiScriptRuntime implements AutoCloseable {
    private static final String BOOTSTRAP = """
            globalThis.__hmi_registry = globalThis.__hmi_registry || Object.create(null);
            globalThis.__hmi_commands = [];
            globalThis.__hmi_model_commands = [];
            globalThis.__hmi_sound_events = [];
            globalThis.__hmi_collect_geometry = true;
            globalThis.global = globalThis;
            globalThis.console = globalThis.console || { log(){}, info(){}, warn(){}, error(){}, debug(){}, trace(){} };

            // Keep the Java boundary compact. Positional arrays avoid materializing a Map plus a
            // nested argument list for every tiny transform command in Javet's object converter.
            const __hmi_cmd = (op, args) => {
              if (__hmi_collect_geometry) __hmi_commands.push([op, ...args]);
            };
            const __hmi_model = (from, to, op, args) => {
              if (__hmi_collect_geometry) __hmi_model_commands.push([from, to, op, ...args]);
            };
            const __hmi_map = () => {
              const m = new Map();
              m.put = (k, v) => { m.set(k, v); return v; };
              m.getOrDefault = (k, v) => m.has(k) ? m.get(k) : v;
              return m;
            };

            globalThis.renderAsBlock = __hmi_map();
            globalThis.translateItem = __hmi_map();
            globalThis.itemSwingSpeed = __hmi_map();
            globalThis.useDuration = __hmi_map();
            globalThis.usingItem = __hmi_map();
            globalThis.applyBlockRotation = __hmi_map();

            globalThis.M = {
              PI: Math.PI,
              moveX: (_m,x) => __hmi_cmd('moveX',[x]),
              moveY: (_m,y) => __hmi_cmd('moveY',[y]),
              moveZ: (_m,z) => __hmi_cmd('moveZ',[z]),
              translate: (_m,x,y,z) => __hmi_cmd('translate',[x,y,z]),
              scale: (_m,x,y,z) => __hmi_cmd('scale',[x,y,z]),
              rotateX: (_m,...a) => __hmi_cmd('rotateX',a),
              rotateY: (_m,...a) => __hmi_cmd('rotateY',a),
              rotateZ: (_m,...a) => __hmi_cmd('rotateZ',a),
              shear: (_m,x,y,z) => __hmi_cmd('shear',[x,y,z]),
              push: () => __hmi_cmd('push',[]),
              pop: () => __hmi_cmd('pop',[]),
              sin: Math.sin,
              cos: Math.cos,
              floor: Math.floor,
              ceil: Math.ceil,
              abs: Math.abs,
              pow: Math.pow,
              round: Math.round,
              clamp: (v,min,max) => Math.max(min, Math.min(max,v)),
              lerp: (t,a,b) => a + (b-a)*t
            };

            const __hmi_easeOutBounce = x => {
              const n1=7.5625,d1=2.75;
              if (x < 1/d1) return n1*x*x;
              if (x < 2/d1) { x-=1.5/d1; return n1*x*x+.75; }
              if (x < 2.5/d1) { x-=2.25/d1; return n1*x*x+.9375; }
              x-=2.625/d1; return n1*x*x+.984375;
            };
            globalThis.Easings = {
              easeInSine:x=>1-Math.cos((x*Math.PI)/2),
              easeOutSine:x=>Math.sin((x*Math.PI)/2),
              easeInOutSine:x=>-(Math.cos(Math.PI*x)-1)/2,
              easeInQuad:x=>x*x, easeOutQuad:x=>1-(1-x)*(1-x),
              easeInOutQuad:x=>x<.5?2*x*x:1-Math.pow(-2*x+2,2)/2,
              easeInCubic:x=>x*x*x, easeOutCubic:x=>1-Math.pow(1-x,3),
              easeInOutCubic:x=>x<.5?4*x*x*x:1-Math.pow(-2*x+2,3)/2,
              easeInQuart:x=>x*x*x*x, easeOutQuart:x=>1-Math.pow(1-x,4),
              easeInOutQuart:x=>x<.5?8*x*x*x*x:1-Math.pow(-2*x+2,4)/2,
              easeInQuint:x=>x*x*x*x*x, easeOutQuint:x=>1-Math.pow(1-x,5),
              easeInOutQuint:x=>x<.5?16*x*x*x*x*x:1-Math.pow(-2*x+2,5)/2,
              easeInExpo:x=>x===0?0:Math.pow(2,10*x-10),
              easeOutExpo:x=>x===1?1:1-Math.pow(2,-10*x),
              easeInOutExpo:x=>x===0?0:x===1?1:x<.5?Math.pow(2,20*x-10)/2:(2-Math.pow(2,-20*x+10))/2,
              easeInCirc:x=>1-Math.sqrt(1-x*x),
              easeOutCirc:x=>Math.sqrt(1-Math.pow(x-1,2)),
              easeInOutCirc:x=>x<.5?(1-Math.sqrt(1-Math.pow(2*x,2)))/2:(Math.sqrt(1-Math.pow(-2*x+2,2))+1)/2,
              easeInBack:x=>2.70158*x*x*x-1.70158*x*x,
              easeOutBack:x=>1+2.70158*Math.pow(x-1,3)+1.70158*Math.pow(x-1,2),
              easeInOutBack:x=>x<.5?(Math.pow(2*x,2)*((2.5949095+1)*2*x-2.5949095))/2:(Math.pow(2*x-2,2)*((2.5949095+1)*(x*2-2)+2.5949095)+2)/2,
              easeOutBounce:__hmi_easeOutBounce,
              easeInBounce:x=>1-__hmi_easeOutBounce(1-x),
              easeInOutBounce:x=>x<.5?(1-__hmi_easeOutBounce(1-2*x))/2:(1+__hmi_easeOutBounce(2*x-1))/2,
              cubicEase:x=>x*x*(3-2*x)
            };

            globalThis.Items = { get:id=>String(id) };
            globalThis.Tags = {
              getVanillaTag:id=>'minecraft:'+String(id),
              getFabricTag:id=>'c:'+String(id)
            };
            globalThis.P = {
              getHealth:p=>p.health,
              isSneaking:p=>!!p.sneaking,
              isOnGround:p=>!!p.onGround,
              isSwimming:p=>!!p.swimming,
              isClimbing:p=>!!p.climbing,
              isCrawling:p=>!!p.crawling,
              isSubmergedInWater:p=>!!p.underWater,
              isTouchingWater:p=>!!p.inWater,
              isUsingRiptide:p=>!!p.riptide,
              getX:p=>p.x, getY:p=>p.y, getZ:p=>p.z,
              getXSpeed:p=>p.velocity.x, getYSpeed:p=>p.velocity.y, getZSpeed:p=>p.velocity.z,
              getSpeed:p=>Math.hypot(p.velocity.x,p.velocity.z),
              isUsingItem:p=>!!p.usingItem,
              getYaw:p=>p.yaw, getPitch:p=>p.pitch,
              getMainItem:p=>p.mainItem, getOffhandItem:p=>p.offItem,
              getActiveHand:p=>p.activeHand,
              getAge:p=>p.age,
              isItemCoolingDown:(_p,item)=>!!item.cooldown,
              getSwingCount:p=>p.swingCount,
              hasVehicle:p=>!!p.hasVehicle
            };
            globalThis.I = {
              isOf:(item,id)=>!!item && item.id===id,
              isIn:(item,tag)=>!!item && Array.isArray(item.tags) && item.tags.includes(tag),
              isEmpty:item=>!item || !!item.empty,
              getUseAction:item=>item?.useAction || 'none',
              getName:item=>item?.id || 'minecraft:air',
              getActualName:item=>item?.name || '',
              isChargedCrossbow:item=>!!item?.chargedCrossbow,
              isBlock:item=>!!item?.block,
              shouldTranslateItem:item=>!!item?.translate,
              isCustomTranslate:item=>!!item && !!translateItem.getOrDefault(item.id, !!item.customTranslate),
              isLantern:item=>!!item?.lantern,
              isThrowable:item=>!!item?.throwable,
              isEnchanted:item=>!!item?.enchanted,
              getSpearData:item=>item?.spearData || {canDamage:false,canDismount:true,canKnockback:true,hitImpact:false},
              setChestOpen:()=>{}, setShulkerOpen:()=>{}
            };
            globalThis.Texture = { of:(namespace,path)=>String(namespace)+':' + String(path) };
            globalThis.string = { find:(value,needle)=>String(value).includes(String(needle)) };
            globalThis.KeyBindManager = { isKeyPressed:key=>Number(key)===74 && !!globalThis.__hmi_inspect_pressed };
            globalThis.S = { playSound:(id,volume)=>__hmi_sound_events.push([String(id),Number(volume)||1]) };
            // These outputs currently have no Java consumer. Keep the compatibility API callable
            // without allocating command payloads that would immediately be discarded.
            globalThis.debugger = { out:()=>{} };
            globalThis.particleManager = { addParticle:()=>{} };
            globalThis.animator = {
              moveX:(f,t,x)=>__hmi_model(f,t,'moveX',[x]),
              moveY:(f,t,y)=>__hmi_model(f,t,'moveY',[y]),
              moveZ:(f,t,z)=>__hmi_model(f,t,'moveZ',[z]),
              scale:(f,t,x,y,z)=>__hmi_model(f,t,'scale',[x,y,z]),
              rotateX:(f,t,...a)=>__hmi_model(f,t,'rotateX',a),
              rotateY:(f,t,...a)=>__hmi_model(f,t,'rotateY',a),
              rotateZ:(f,t,...a)=>__hmi_model(f,t,'rotateZ',a)
            };
            globalThis.__hmi_reset_output = () => {
              __hmi_commands.length = 0;
              __hmi_model_commands.length = 0;
              __hmi_sound_events.length = 0;
            };
            const __hmi_run = name => {
              __hmi_reset_output();
              globalThis[name]();
              // Every following stage resets the collectors, so retain independent snapshots.
              return [__hmi_commands.slice(), __hmi_model_commands.slice(), __hmi_sound_events.slice()];
            };
            const __hmi_run_state_only = name => {
              __hmi_reset_output();
              __hmi_collect_geometry = false;
              try {
                globalThis[name]();
                // Sounds remain observable even when this hand has no visible geometry.
                return [[], [], __hmi_sound_events.slice()];
              } finally {
                __hmi_collect_geometry = true;
              }
            };
            globalThis.__hmi_execute_plan = mask => {
              const context = globalThis.__hmi_context;
              globalThis.mainHandSwitchEvent = !!context.mainHandSwitchEvent;
              globalThis.offHandSwitchEvent = !!context.offHandSwitchEvent;
              globalThis.__hmi_inspect_pressed = !!context.inspectPressed;
              return [
                (mask & 1) !== 0 ? __hmi_run('__hmi_hand_pose')
                                 : ((mask & 16) !== 0 ? __hmi_run_state_only('__hmi_hand_pose') : null),
                (mask & 2) !== 0 ? __hmi_run('__hmi_hand_relative_pose') : null,
                (mask & 4) !== 0 ? __hmi_run('__hmi_item_pose') : null,
                (mask & 8) !== 0 ? __hmi_run('__hmi_item_model') : null
              ];
            };
            """;

    public static final int HAND_POSE = 1;
    public static final int HAND_RELATIVE_POSE = 1 << 1;
    public static final int ITEM_POSE = 1 << 2;
    public static final int ITEM_MODEL = 1 << 3;
    public static final int HAND_POSE_STATE_ONLY = 1 << 4;
    private static final HmiScriptKind[] KINDS = HmiScriptKind.values();

    private final EnumMap<HmiScriptKind, String> loadedSources = new EnumMap<>(HmiScriptKind.class);
    private V8Runtime runtime;
    private Map<String, Object> boundContext;
    private boolean dirty = true;

    public synchronized Result execute(HmiScriptKind kind, Map<String, Object> context) {
        Result[] results = executePlan(mask(kind), context);
        Result result = results[kind.ordinal()];
        return result != null ? result : Result.EMPTY;
    }

    /**
     * Executes all requested HMI stages in one V8 call. The scripts still run in their original
     * hand/relative/item/model order and each stage retains an isolated output collector.
     */
    public synchronized Result[] executePlan(int plan, Map<String, Object> context) {
        Result[] results = new Result[KINDS.length];
        try {
            ensureReady();
            if (runtime == null || plan == 0) return fillMissing(plan, results);

            // Javet's object converter recursively materializes the complete player/item context.
            // Bind it once, then execute every required stage before crossing back into Java.
            if (boundContext != context) {
                boundContext = context;
                runtime.getGlobalObject().set("__hmi_context", context);
            }

            try (ProfilerPhase.Scope ignored = ProfilerPhase.scope("hmi:v8_execute_plan")) {
                Object raw = runtime.getGlobalObject().invokeObject("__hmi_execute_plan", plan);
                if (!(raw instanceof List<?> stages)) return fillMissing(plan, results);
                int count = Math.min(stages.size(), results.length);
                for (int i = 0; i < count; i++) {
                    Object stage = stages.get(i);
                    if (!(stage instanceof List<?> output) || output.size() < 3) continue;
                    results[i] = new Result(
                            HmiTransformCommand.decode(output.get(0)),
                            HmiModelCommand.decode(output.get(1)),
                            HmiSoundCommand.decode(output.get(2))
                    );
                }
            }
        } catch (Throwable t) {
            DebugLog.error("[HMI] JavaScript execution failed for plan %d: %s", t, plan, t.getMessage());
        }
        return fillMissing(plan, results);
    }

    private static Result[] fillMissing(int plan, Result[] results) {
        for (HmiScriptKind kind : KINDS) {
            boolean requested = (plan & mask(kind)) != 0
                    || kind == HmiScriptKind.HAND_POSE && (plan & HAND_POSE_STATE_ONLY) != 0;
            if (requested && results[kind.ordinal()] == null) {
                results[kind.ordinal()] = Result.EMPTY;
            }
        }
        return results;
    }

    private static int mask(HmiScriptKind kind) {
        return switch (kind) {
            case HAND_POSE -> HAND_POSE;
            case HAND_RELATIVE_POSE -> HAND_RELATIVE_POSE;
            case ITEM_POSE -> ITEM_POSE;
            case ITEM_MODEL -> ITEM_MODEL;
        };
    }

    public synchronized void invalidate() {
        dirty = true;
    }

    private void ensureReady() throws Exception {
        if (!dirty && runtime != null) return;
        closeRuntime();
        JavetRuntimeBootstrap.installNativeLoader();
        runtime = V8Host.getV8Instance().createV8Runtime();
        boundContext = null;
        runtime.setConverter(new JavetObjectConverter());
        runtime.setMemorySaverModeEnabled(false);
        runtime.setBatterySaverModeEnabled(false);
        runtime.getExecutor(BOOTSTRAP).setResourceName("combatant:hmi/bootstrap.js").executeVoid();
        ResourceManager manager = Minecraft.getInstance().getResourceManager();
        loadedSources.clear();
        for (HmiScriptKind kind : KINDS) {
            String source = loadStack(manager, kind);
            loadedSources.put(kind, source);
            String function = "__hmi_" + kind.name().toLowerCase();
            String wrapped = "globalThis." + function + " = function() {\n" +
                    "const " + kind.argumentName() + " = globalThis.__hmi_context;\n" +
                    source + "\n};\n" +
                    "globalThis." + function + "_ready = true;";
            runtime.getExecutor(wrapped).setResourceName(kind.resourceId().toString()).executeVoid();
        }
        dirty = false;
    }

    private static String loadStack(ResourceManager manager, HmiScriptKind kind) {
        List<String> chunks = new ArrayList<>();
        manager.getResource(kind.resourceId()).ifPresent(resource -> readResource(kind.resourceId().toString(), resource, chunks));
        for (Resource addon : manager.getResourceStack(kind.addonResourceId())) {
            readResource(kind.addonResourceId().toString(), addon, chunks);
        }
        return String.join("\n", chunks);
    }

    private static void readResource(String id, Resource resource, List<String> chunks) {
        try (BufferedReader reader = resource.openAsReader()) {
            chunks.add(reader.lines().collect(Collectors.joining("\n")));
        } catch (Exception e) {
            DebugLog.error("[HMI] Failed to load %s from %s", e, id, resource.sourcePackId());
        }
    }

    @Override
    public synchronized void close() {
        closeRuntime();
        dirty = true;
    }

    private void closeRuntime() {
        if (runtime == null) return;
        try {
            runtime.close();
        } catch (Throwable ignored) {
        } finally {
            runtime = null;
            boundContext = null;
        }
    }

    public record Result(List<HmiTransformCommand> commands, List<HmiModelCommand> modelCommands,
                          List<HmiSoundCommand> sounds) {
        public static final Result EMPTY = new Result(List.of(), List.of(), List.of());
    }
}

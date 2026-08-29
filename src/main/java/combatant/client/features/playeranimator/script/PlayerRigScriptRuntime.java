/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.playeranimator.script;

import com.caoccao.javet.interop.V8Host;
import com.caoccao.javet.interop.V8Runtime;
import com.caoccao.javet.interop.converters.JavetObjectConverter;
import combatant.client.features.playeranimator.PlayerRigBone;
import combatant.client.features.playeranimator.PlayerRigDeformer;
import combatant.client.render.engine.profiler.ProfilerPhase;
import combatant.client.render.engine.renderer.ui.runtime.script.JavetRuntimeBootstrap;
import combatant.client.util.logging.DebugLog;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Isolated, resource-pack reloadable player animation runtime. Like HMI, it exposes no Java
 * objects to V8: one flat context enters and one compact command batch leaves per evaluation.
 */
public final class PlayerRigScriptRuntime implements AutoCloseable {
    public static final Identifier SCRIPT = Identifier.fromNamespaceAndPath(
            "combatant", "playeranimator/player_rig.js"
    );
    public static final Identifier ADDON_SCRIPT = Identifier.fromNamespaceAndPath(
            "combatant", "playeranimator/player_rig_addon.js"
    );

    private V8Runtime runtime;
    private boolean dirty = true;

    public synchronized List<PlayerRigScriptCommand> execute(Object[] packedContext) {
        try {
            ensureReady();
            if (runtime == null) return List.of();
            try (ProfilerPhase.Scope ignored = ProfilerPhase.scope("player_animator:v8_execute")) {
                Object raw = runtime.getGlobalObject().invokeObject(
                        "__combatant_player_rig_execute",
                        packedContext != null ? packedContext : new Object[0]
                );
                return PlayerRigScriptCommand.decode(raw);
            }
        } catch (Throwable t) {
            DebugLog.error("[PlayerAnimator] JavaScript execution failed: %s", t, t.getMessage());
            return List.of();
        }
    }

    public synchronized void invalidate() {
        dirty = true;
    }

    private void ensureReady() throws Exception {
        if (!dirty && runtime != null) return;
        closeRuntime();

        JavetRuntimeBootstrap.installNativeLoader();
        runtime = V8Host.getV8Instance().createV8Runtime();
        runtime.setConverter(new JavetObjectConverter());
        runtime.setMemorySaverModeEnabled(false);
        runtime.setBatterySaverModeEnabled(false);
        runtime.getExecutor(bootstrap())
                .setResourceName("combatant:playeranimator/bootstrap.js")
                .executeVoid();

        ResourceManager manager = Minecraft.getInstance().getResourceManager();
        String source = loadStack(manager);
        if (!source.isBlank()) {
            runtime.getExecutor(source).setResourceName(SCRIPT.toString()).executeVoid();
        }
        dirty = false;
    }

    private static String bootstrap() {
        return "globalThis.__combatant_player_rig_bones = Object.freeze(" + boneMap() + ");\n"
                + "globalThis.__combatant_player_rig_deforms = Object.freeze(" + deformMap() + ");\n"
                + """
                globalThis.global = globalThis;
                globalThis.console = globalThis.console || { log(){}, info(){}, warn(){}, error(){}, debug(){}, trace(){} };
                const __rig_callbacks = [];
                let __rig_commands = [];
                const __rig_rad = Math.PI / 180;
                const __rig_number = (value, fallback = 0) => {
                  const number = Number(value);
                  return Number.isFinite(number) ? number : fallback;
                };
                const __rig_target = (value, table) => {
                  if (typeof value === 'string') value = table[value];
                  value = Number(value);
                  if (!Number.isInteger(value) || value < 0) return -1;
                  return value;
                };
                const __rig_bone = value => __rig_target(value, __combatant_player_rig_bones);
                const __rig_deform = value => __rig_target(value, __combatant_player_rig_deforms);
                const __rig_push3 = (op, target, x, y, z) => {
                  if (target >= 0) __rig_commands.push([op, target, __rig_number(x), __rig_number(y), __rig_number(z)]);
                };
                const __rig_push_angle = (op, target, angle, falloff) => {
                  if (target >= 0) __rig_commands.push([op, target, __rig_number(angle), __rig_number(falloff, 1)]);
                };

                const playerRig = globalThis.playerRig = Object.freeze({
                  bones: __combatant_player_rig_bones,
                  deformers: __combatant_player_rig_deforms,
                  bone: __rig_bone,
                  deformer: __rig_deform,
                  onPose(callback) { if (typeof callback === 'function') __rig_callbacks.push(callback); },
                  reset() { __rig_commands.push([0, -1]); },
                  resetBone(bone) { const target=__rig_bone(bone); if (target>=0) __rig_commands.push([1,target]); },
                  setPosition(bone,x,y,z) { __rig_push3(2,__rig_bone(bone),x,y,z); },
                  move(bone,x,y,z) { __rig_push3(3,__rig_bone(bone),x,y,z); },
                  setRotation(bone,x,y,z) { __rig_push3(4,__rig_bone(bone),__rig_number(x)*__rig_rad,__rig_number(y)*__rig_rad,__rig_number(z)*__rig_rad); },
                  rotate(bone,x,y,z) { __rig_push3(5,__rig_bone(bone),__rig_number(x)*__rig_rad,__rig_number(y)*__rig_rad,__rig_number(z)*__rig_rad); },
                  setRotationRadians(bone,x,y,z) { __rig_push3(4,__rig_bone(bone),x,y,z); },
                  rotateRadians(bone,x,y,z) { __rig_push3(5,__rig_bone(bone),x,y,z); },
                  setQuaternion(bone,x,y,z,w) { const target=__rig_bone(bone); if(target>=0) __rig_commands.push([6,target,__rig_number(x),__rig_number(y),__rig_number(z),__rig_number(w,1)]); },
                  setScale(bone,x,y,z) { __rig_push3(7,__rig_bone(bone),x,y,z); },
                  bend(deformer,angle,falloff=1) { __rig_push_angle(8,__rig_deform(deformer),__rig_number(angle)*__rig_rad,falloff); },
                  bendRadians(deformer,angle,falloff=1) { __rig_push_angle(8,__rig_deform(deformer),angle,falloff); },
                  twist(deformer,angle,falloff=1) { __rig_push_angle(9,__rig_deform(deformer),__rig_number(angle)*__rig_rad,falloff); },
                  twistRadians(deformer,angle,falloff=1) { __rig_push_angle(9,__rig_deform(deformer),angle,falloff); },
                  clearDeform(deformer) { const target=__rig_deform(deformer); if(target>=0) __rig_commands.push([10,target]); },
                  clamp(value,min,max) { return Math.max(min,Math.min(max,value)); },
                  lerp(value,a,b) { return a+(b-a)*value; },
                  smoothstep(value) { value=Math.max(0,Math.min(1,value)); return value*value*(3-2*value); }
                });
                globalThis.rig = playerRig;

                const __rig_unpack_context = packed => ({
                  playerId: String(packed?.[0] ?? ''),
                  age: __rig_number(packed?.[1]), tickDelta: __rig_number(packed?.[2]), deltaSeconds: __rig_number(packed?.[3]),
                  yaw: __rig_number(packed?.[4]), pitch: __rig_number(packed?.[5]), swing: __rig_number(packed?.[6]),
                  velocity: { x:__rig_number(packed?.[7]), y:__rig_number(packed?.[8]), z:__rig_number(packed?.[9]) },
                  onGround: !!packed?.[10], crouching: !!packed?.[11], sprinting: !!packed?.[12], swimming: !!packed?.[13],
                  fallFlying: !!packed?.[14], passenger: !!packed?.[15], usingItem: !!packed?.[16],
                  pose: String(packed?.[17] ?? 'standing'), mainArm: String(packed?.[18] ?? 'right'),
                  mainItem: String(packed?.[19] ?? 'minecraft:air'), offItem: String(packed?.[20] ?? 'minecraft:air'),
                  style: String(packed?.[21] ?? 'Hybrid'), strength: __rig_number(packed?.[22], 1),
                  climbing: !!packed?.[23], inWater: !!packed?.[24], underWater: !!packed?.[25],
                  crawling: !!packed?.[26], fallDistance: __rig_number(packed?.[27]), y: __rig_number(packed?.[28]),
                  useAction: String(packed?.[29] ?? 'none'), useItem: String(packed?.[30] ?? 'minecraft:air'),
                  useArm: String(packed?.[31] ?? 'none'), useTicks: __rig_number(packed?.[32]),
                  swingIndex: Math.trunc(__rig_number(packed?.[33]))
                });
                globalThis.__combatant_player_rig_execute = packed => {
                  __rig_commands = [];
                  const context = __rig_unpack_context(packed);
                  for (let i=0; i<__rig_callbacks.length; i++) __rig_callbacks[i](context, playerRig);
                  const result = __rig_commands;
                  __rig_commands = [];
                  return result;
                };
                """;
    }

    private static String boneMap() {
        StringBuilder out = new StringBuilder("{");
        PlayerRigBone[] bones = PlayerRigBone.values();
        for (int i = 0; i < bones.length; i++) {
            if (i > 0) out.append(',');
            out.append('"').append(bones[i].id()).append("\":").append(i);
        }
        return out.append('}').toString();
    }

    private static String deformMap() {
        StringBuilder out = new StringBuilder("{");
        PlayerRigDeformer[] deformers = PlayerRigDeformer.values();
        for (int i = 0; i < deformers.length; i++) {
            if (i > 0) out.append(',');
            out.append('"').append(deformers[i].id()).append("\":").append(deformers[i].channel());
        }
        return out.append('}').toString();
    }

    private static String loadStack(ResourceManager manager) {
        List<String> chunks = new ArrayList<>();
        manager.getResource(SCRIPT).ifPresent(resource -> readResource(SCRIPT, resource, chunks));
        for (Resource addon : manager.getResourceStack(ADDON_SCRIPT)) {
            readResource(ADDON_SCRIPT, addon, chunks);
        }
        return String.join("\n", chunks);
    }

    private static void readResource(Identifier id, Resource resource, List<String> chunks) {
        try (BufferedReader reader = resource.openAsReader()) {
            chunks.add(reader.lines().collect(Collectors.joining("\n")));
        } catch (Exception e) {
            DebugLog.error("[PlayerAnimator] Failed to load %s from %s", e, id, resource.sourcePackId());
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
        }
    }
}

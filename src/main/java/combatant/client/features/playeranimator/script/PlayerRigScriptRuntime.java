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
import combatant.client.util.resources.asset.AssetAutoLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Isolated, resource-pack reloadable player animation runtime. No Java objects enter V8: one flat
 * render-state context enters and one compact command batch leaves per evaluation.
 *
 * Animation source data is baked into normal JavaScript classes/functions at development time;
 * runtime never parses animation JSON.
 */
public final class PlayerRigScriptRuntime implements AutoCloseable {

    private V8Runtime runtime;
    private boolean dirty = true;

    public synchronized List<PlayerRigScriptCommand> execute(Object[] packedContext) {
        try {
            ensureReady();
            if (runtime == null) return List.of();
            try (ProfilerPhase.Scope ignored = ProfilerPhase.scope("player_animator:v8_execute")) {
                Object raw = invokePacked(runtime, packedContext);
                return PlayerRigScriptCommand.decode(raw);
            }
        } catch (Throwable t) {
            DebugLog.error("[PlayerAnimator] JavaScript execution failed: %s", t, t.getMessage());
            return List.of();
        }
    }

    /** Keep the packed array as one V8 argument instead of spreading Object[] through varargs. */
    static Object invokePacked(V8Runtime runtime, Object[] packedContext) throws Exception {
        if (runtime == null) throw new IllegalArgumentException("Player rig V8 runtime must not be null");
        Object[] safe = packedContext != null ? packedContext : new Object[0];
        return runtime.getGlobalObject().invokeObject(
                "__combatant_player_rig_execute", invocationArguments(safe)
        );
    }

    static Object[] invocationArguments(Object[] packedContext) {
        return new Object[]{packedContext != null ? packedContext : new Object[0]};
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
        for (AssetAutoLoader.ScriptDefinition definition : AssetAutoLoader.scriptAssets(PlayerRigScriptAssets.class)) {
            executeDefinition(manager, definition);
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
                  reachHand(side,targetBone,x=0,y=0,z=0,hintX=0,hintY=.35,hintZ=.15,weight=1) {
                    side=String(side).toLowerCase();
                    const upper=__rig_bone(side==='left'?'left_upper_arm':side==='right'?'right_upper_arm':'');
                    const target=__rig_bone(targetBone);
                    if(upper>=0 && target>=0) __rig_commands.push([11,upper,target,
                      __rig_number(x),__rig_number(y),__rig_number(z),
                      __rig_number(hintX),__rig_number(hintY,.35),__rig_number(hintZ,.15),
                      Math.max(0,Math.min(1,__rig_number(weight,1)))]);
                  },
                  placeItem(side,targetBone,x=0,y=0,z=0,rx=0,ry=0,rz=0,weight=1) {
                    side=String(side).toLowerCase();
                    const control=__rig_bone(side==='left'?'left_item_control':side==='right'?'right_item_control':'');
                    const target=__rig_bone(targetBone);
                    if(control>=0 && target>=0) __rig_commands.push([12,control,target,
                      __rig_number(x),__rig_number(y),__rig_number(z),
                      __rig_number(rx)*__rig_rad,__rig_number(ry)*__rig_rad,__rig_number(rz)*__rig_rad,
                      Math.max(0,Math.min(1,__rig_number(weight,1)))]);
                  },
                  animation(name) { return globalThis.RigAnimationLibrary?.get(String(name)) ?? null; },
                  play(name,time,weight=1,options=null) {
                    return globalThis.RigAnimationLibrary?.play(String(name), playerRig, __rig_number(time), __rig_number(weight,1), options) ?? false;
                  },
                  animationNames() { return globalThis.RigAnimationLibrary?.names() ?? []; },
                  clamp(value,min,max) { return Math.max(min,Math.min(max,value)); },
                  lerp(value,a,b) { return a+(b-a)*value; },
                  smoothstep(value) { value=Math.max(0,Math.min(1,value)); return value*value*(3-2*value); }
                });
                globalThis.rig = playerRig;

                const __rig_unpack_context = packed => {
                  const useTicks = __rig_number(packed?.[32]);
                  return {
                    // Compatibility bridge retained for existing resource-pack addons.
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
                    useArm: String(packed?.[31] ?? 'none'), useTicks,
                    swingIndex: Math.trunc(__rig_number(packed?.[33])),

                    // AvatarRenderState / continuous render-time extensions.
                    attackTime: __rig_number(packed?.[34]), attackActive: !!packed?.[35],
                    bodyYaw: __rig_number(packed?.[36]), headYaw: __rig_number(packed?.[37]), headPitch: __rig_number(packed?.[38]),
                    walkAnimationPos: __rig_number(packed?.[39]), walkAnimationSpeed: __rig_number(packed?.[40]),
                    speedValue: Math.max(1e-4,__rig_number(packed?.[41],1)), swimAmount: __rig_number(packed?.[42]),
                    fallFlyingTime: __rig_number(packed?.[43]), shouldApplyFlyingYRot: !!packed?.[44], flyingYRot: __rig_number(packed?.[45]),
                    vanillaCrouching: !!packed?.[46], vanillaFallFlying: !!packed?.[47], vanillaSwimming: !!packed?.[48],
                    vanillaPassenger: !!packed?.[49], vanillaUsingItem: !!packed?.[50], vanillaInWater: !!packed?.[51],
                    boatLeft: !!packed?.[52], boatRight: !!packed?.[53],
                    boatLeftTime: __rig_number(packed?.[54]), boatRightTime: __rig_number(packed?.[55]),
                    vehicleType: String(packed?.[56] ?? 'none'), horizontalSpeed: __rig_number(packed?.[57]),
                    continuousSeconds: __rig_number(packed?.[58]),
                    vanillaUseTicks: __rig_number(packed?.[59], useTicks),
                    attackArm: String(packed?.[60] ?? packed?.[18] ?? 'right'),
                    swingAnimationType: String(packed?.[61] ?? 'none'),
                    vanillaAttackTime: __rig_number(packed?.[62], packed?.[6]),
                    renderPosition: {
                      x: __rig_number(packed?.[63]), y: __rig_number(packed?.[64]), z: __rig_number(packed?.[65])
                    },
                    leftArmPose: String(packed?.[66] ?? 'empty'),
                    rightArmPose: String(packed?.[67] ?? 'empty'),
                    maxCrossbowChargeDuration: Math.max(1e-4,__rig_number(packed?.[68],25)),
                    creativeFlying: !!packed?.[69],
                    attackCooldown: Math.max(0,Math.min(1,__rig_number(packed?.[70],1))),
                    attackDuration: Math.max(.05,__rig_number(packed?.[71],.62)),
                    useTimeSeconds: Math.max(0,__rig_number(packed?.[59], useTicks) / 20),
                    walkTime: __rig_number(packed?.[39]) / 20,
                    walkPhase: __rig_number(packed?.[39]) * 0.6662
                  };
                };
                globalThis.__combatant_player_rig_execute = packed => {
                  __rig_commands = [];
                  const context = __rig_unpack_context(packed);
                  globalThis.__combatant_player_rig_context = context;
                  try {
                    for (let i=0; i<__rig_callbacks.length; i++) __rig_callbacks[i](context, playerRig);
                    return __rig_commands;
                  } finally {
                    globalThis.__combatant_player_rig_context = null;
                    __rig_commands = [];
                  }
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

    private void executeDefinition(ResourceManager manager, AssetAutoLoader.ScriptDefinition definition) throws Exception {
        if (definition.tree()) {
            executeDirectory(manager, definition.resource());
        } else {
            executeResource(manager, definition.resource());
        }
        Identifier addon = definition.addonResource();
        if (addon != null) {
            for (Resource resource : manager.getResourceStack(addon)) {
                executeResource(addon, resource);
            }
        }
    }

    private void executeResource(ResourceManager manager, Identifier id) throws Exception {
        Resource resource = manager.getResource(id).orElseThrow(
                () -> new IllegalStateException("Missing player animation resource: " + id)
        );
        executeResource(id, resource);
    }

    /** Loads one declared script tree in deterministic resource-id order. */
    private void executeDirectory(ResourceManager manager, Identifier directory) throws Exception {
        String root = directory.getPath();
        List<Map.Entry<Identifier, Resource>> resources = new ArrayList<>(
                manager.listResources(root, id -> id.getPath().endsWith(".js")).entrySet()
        );
        resources.sort(Comparator.comparing(entry -> entry.getKey().toString()));
        for (Map.Entry<Identifier, Resource> entry : resources) {
            executeResource(entry.getKey(), entry.getValue());
        }
    }

    private void executeResource(Identifier id, Resource resource) throws Exception {
        try (BufferedReader reader = resource.openAsReader()) {
            String source = reader.lines().collect(Collectors.joining("\n"));
            runtime.getExecutor(source).setResourceName(id.toString()).executeVoid();
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

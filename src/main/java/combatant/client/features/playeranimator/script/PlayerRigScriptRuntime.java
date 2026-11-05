/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.playeranimator.script;

import com.caoccao.javet.interop.V8Runtime;
import com.caoccao.javet.interop.converters.JavetObjectConverter;
import com.caoccao.javet.values.reference.V8ValueArrayBuffer;
import com.caoccao.javet.values.reference.V8ValueTypedArray;

import combatant.client.features.playeranimator.PlayerRigBone;
import combatant.client.features.playeranimator.PlayerRigDeformer;
import combatant.client.features.playeranimator.PlayerRigInstance;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import combatant.client.render.engine.profiler.ProfilerPhase;
import combatant.client.render.engine.renderer.ui.runtime.script.JavetRuntimeBootstrap;
import combatant.client.util.logging.DebugLog;
import combatant.client.util.resources.asset.AssetAutoLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.BufferedReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
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
    // Retain the ArrayBuffer handle for the entire lifetime of the native ByteBuffer view.
    // Access is confined to the same synchronized script-runtime thread as V8 execution.
    private V8ValueArrayBuffer packedOutputBuffer;
    private ByteBuffer packedOutputBytes;
    private V8ValueArrayBuffer packedInputBuffer;
    private PlayerRigInputBuffer packedInput;
    private static final Object[] EMPTY_CONTEXT = new Object[0];
    private final Object[] reusableInvocationArguments = new Object[1];

    public synchronized List<PlayerRigScriptCommand> execute(Object[] packedContext) {
        try {
            ensureReady();
            if (runtime == null) return List.of();
            try (ProfilerPhase.Scope ignored = ProfilerPhase.scope("player_animator:v8_execute")) {
                Object raw = invokePackedReusable(packedContext);
                if (packedOutputBytes != null) {
                    if (raw instanceof Number count) {
                        return PlayerRigPackedCommands.decode(packedOutputBytes, count.intValue());
                    }
                    if (raw instanceof List<?> overflow && overflow.size() == 2
                            && overflow.get(0) instanceof Number count) {
                        List<PlayerRigScriptCommand> packed = new ArrayList<>(
                                PlayerRigPackedCommands.decode(packedOutputBytes, count.intValue()));
                        packed.addAll(PlayerRigScriptCommand.decode(overflow.get(1)));
                        return packed;
                    }
                }
                return PlayerRigScriptCommand.decode(raw);
            }
        } catch (Throwable t) {
            DebugLog.error("[PlayerAnimator] JavaScript execution failed: %s", t, t.getMessage());
            return List.of();
        }
    }

    /** Fast render-thread path, retained for consumers that already own a legacy packed Object[]. */
    public synchronized void executeInto(Object[] packedContext, PlayerRigInstance instance) {
        if (instance == null) return;
        try {
            ensureReady();
            if (runtime == null) return;
            try (ProfilerPhase.Scope ignored = ProfilerPhase.scope("player_animator:v8_execute")) {
                applyOutput(invokePackedReusable(packedContext), instance);
            }
        } catch (Throwable t) {
            DebugLog.error("[PlayerAnimator] JavaScript execution failed: %s", t, t.getMessage());
        }
    }

    /** Hot path: sample Minecraft state straight into V8's input Float64Array, with no boxed Object[]. */
    public synchronized void executeFrameInto(AbstractClientPlayer player, AvatarRenderState state,
                                              float tickDelta, float deltaSeconds,
                                              String style, float strength, int swingIndex,
                                              float attackProgress, float attackTimeSeconds,
                                              float attackDurationSeconds, boolean attackActive,
                                              PlayerRigInstance instance) {
        if (instance == null) return;
        try {
            ensureReady();
            if (runtime == null) return;
            try (ProfilerPhase.Scope ignored = ProfilerPhase.scope("player_animator:v8_execute")) {
                Object raw;
                if (packedInput != null) {
                    try (ProfilerPhase.Scope packing = ProfilerPhase.scope("player_animator:context_pack")) {
                        PlayerRigScriptContext.writeNative(packedInput, player, state, tickDelta,
                                deltaSeconds, style, strength, swingIndex, attackProgress,
                                attackTimeSeconds, attackDurationSeconds, attackActive);
                    }
                    reusableInvocationArguments[0] = packedInput.strings();
                    try {
                        try (ProfilerPhase.Scope invoke = ProfilerPhase.scope("player_animator:javet_invoke")) {
                            raw = runtime.getGlobalObject().invokeObject(
                                    "__combatant_player_rig_execute_native", reusableInvocationArguments);
                        }
                    } finally {
                        reusableInvocationArguments[0] = null;
                    }
                } else {
                    raw = invokePackedReusable(PlayerRigScriptContext.pack(player, state, tickDelta,
                            deltaSeconds, style, strength, swingIndex, attackProgress,
                            attackTimeSeconds, attackDurationSeconds, attackActive));
                }
                try (ProfilerPhase.Scope apply = ProfilerPhase.scope("player_animator:apply_output")) {
                    applyOutput(raw, instance);
                }
            }
        } catch (Throwable t) {
            DebugLog.error("[PlayerAnimator] JavaScript execution failed: %s", t, t.getMessage());
        }
    }

    private void applyOutput(Object raw, PlayerRigInstance instance) {
        if (packedOutputBytes != null) {
            if (raw instanceof Number count) {
                PlayerRigPackedCommands.apply(packedOutputBytes, count.intValue(), instance);
                return;
            }
            if (raw instanceof List<?> overflow && overflow.size() == 2
                    && overflow.get(0) instanceof Number count) {
                PlayerRigPackedCommands.apply(packedOutputBytes, count.intValue(), instance);
                for (PlayerRigScriptCommand command : PlayerRigScriptCommand.decode(overflow.get(1))) {
                    command.apply(instance);
                }
                return;
            }
        }
        for (PlayerRigScriptCommand command : PlayerRigScriptCommand.decode(raw)) {
            command.apply(instance);
        }
    }

    /** Reuse the outer varargs array; the packed context is still exactly one JS argument. */
    private Object invokePackedReusable(Object[] packedContext) throws Exception {
        reusableInvocationArguments[0] = packedContext != null ? packedContext : EMPTY_CONTEXT;
        try {
            return runtime.getGlobalObject().invokeObject(
                    "__combatant_player_rig_execute", reusableInvocationArguments);
        } finally {
            reusableInvocationArguments[0] = null;
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

        runtime = JavetRuntimeBootstrap.createRuntime(this);
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
        // Never switch JS into the binary protocol until the native buffer is validated.
        bindPackedOutput();
        bindPackedInput();
        dirty = false;
    }

    private void bindPackedOutput() {
        V8ValueArrayBuffer buffer = null;
        try (V8ValueTypedArray array = runtime.getGlobalObject().get("__combatant_rig_output_buffer")) {
            if (array == null || array.getLength() != PlayerRigPackedCommands.VALUE_COUNT) return;
            buffer = array.getBuffer();
            ByteBuffer bytes = buffer.getByteBuffer();
            if (bytes == null || !bytes.isDirect()
                    || bytes.capacity() < PlayerRigPackedCommands.BYTE_COUNT) return;
            ByteBuffer view = bytes.asReadOnlyBuffer().order(ByteOrder.nativeOrder());
            runtime.getExecutor("globalThis.__combatant_rig_enable_binary()").executeVoid();
            packedOutputBuffer = buffer;
            packedOutputBytes = view;
            buffer = null; // The runtime now owns this handle until closeRuntime().
        } catch (Exception ignored) {
            // Use the existing array-of-commands protocol on unsupported native backends.
        } finally {
            if (buffer != null) {
                try { buffer.close(); } catch (Exception ignored) { }
            }
        }
    }

    private void bindPackedInput() {
        V8ValueArrayBuffer buffer = null;
        try (V8ValueTypedArray array = runtime.getGlobalObject().get("__combatant_rig_input_buffer")) {
            if (array == null || array.getLength() != PlayerRigInputBuffer.VALUE_COUNT) return;
            buffer = array.getBuffer();
            ByteBuffer bytes = buffer.getByteBuffer();
            if (bytes == null || !bytes.isDirect() || bytes.isReadOnly()
                    || bytes.capacity() < PlayerRigInputBuffer.BYTE_COUNT) return;
            packedInput = new PlayerRigInputBuffer(bytes);
            packedInputBuffer = buffer;
            buffer = null;
        } catch (Exception ignored) {
            // Old boxed-context protocol remains available if writable native input is unsupported.
        } finally {
            if (buffer != null) try { buffer.close(); } catch (Exception ignored) { }
        }
    }

    static String bootstrap() {
        return "globalThis.__combatant_player_rig_bones = Object.freeze(" + boneMap() + ");\n"
                + "globalThis.__combatant_player_rig_deforms = Object.freeze(" + deformMap() + ");\n"
                + """
                globalThis.global = globalThis;
                globalThis.console = globalThis.console || { log(){}, info(){}, warn(){}, error(){}, debug(){}, trace(){} };
                const __rig_callbacks = [];
                let __rig_commands = [];
                // One immutable-capacity output buffer per isolate. No per-command JS arrays
                // and no Java↔V8 object conversion on the normal path.
                const __rig_capacity = 256;
                const __rig_stride = 11; // opcode, target, argument count, 8 arguments
                const __rig_output = new Float64Array(__rig_capacity * __rig_stride);
                Object.defineProperty(globalThis, '__combatant_rig_output_buffer', {
                  value: __rig_output, writable: false, configurable: false
                });
                // Input is V8-owned; Java writes Float64 directly into its native backing memory.
                // The sideband contains only the fourteen non-numeric fields.
                const __rig_input = new Float64Array(72);
                Object.defineProperty(globalThis, '__combatant_rig_input_buffer', {
                  value: __rig_input, writable: false, configurable: false
                });
                // Keep the legacy packed path for resource-pack scripts which retain old contexts.
                // The normal native path updates a single context in-place without copying 72 fields.
                const __rig_native_packed = new Array(72);
                const __rig_string_positions = [0,17,18,19,20,21,29,30,31,56,60,61,66,67];
                globalThis.__combatant_rig_reuse_context = true;
                let __rig_binary_enabled = false;
                let __rig_count = 0;
                let __rig_overflow = null;
                globalThis.__combatant_rig_enable_binary = () => { __rig_binary_enabled = true; };
                const __rig_emit = (op,target,n,a,b,c,d,e,f,g,h) => {
                  if (__rig_binary_enabled && __rig_count < __rig_capacity) {
                    const start = __rig_count++ * __rig_stride;
                    __rig_output[start] = op;
                    __rig_output[start+1] = target;
                    __rig_output[start+2] = n;
                    if (n > 0) __rig_output[start+3] = a;
                    if (n > 1) __rig_output[start+4] = b;
                    if (n > 2) __rig_output[start+5] = c;
                    if (n > 3) __rig_output[start+6] = d;
                    if (n > 4) __rig_output[start+7] = e;
                    if (n > 5) __rig_output[start+8] = f;
                    if (n > 6) __rig_output[start+9] = g;
                    if (n > 7) __rig_output[start+10] = h;
                    return;
                  }
                  // Only the overflow tail (or unbound legacy mode) allocates arrays.
                  const command = [op, target];
                  if (n > 0) command.push(a);
                  if (n > 1) command.push(b);
                  if (n > 2) command.push(c);
                  if (n > 3) command.push(d);
                  if (n > 4) command.push(e);
                  if (n > 5) command.push(f);
                  if (n > 6) command.push(g);
                  if (n > 7) command.push(h);
                  if (__rig_binary_enabled) {
                    if (__rig_overflow === null) __rig_overflow = [];
                    __rig_overflow.push(command);
                  } else __rig_commands.push(command);
                };
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
                  if (target >= 0) __rig_emit(op, target, 3, __rig_number(x), __rig_number(y), __rig_number(z));
                };
                const __rig_push_angle = (op, target, angle, falloff) => {
                  if (target >= 0) __rig_emit(op, target, 2, __rig_number(angle), __rig_number(falloff, 1));
                };

                const playerRig = globalThis.playerRig = Object.freeze({
                  bones: __combatant_player_rig_bones,
                  deformers: __combatant_player_rig_deforms,
                  bone: __rig_bone,
                  deformer: __rig_deform,
                  onPose(callback) { if (typeof callback === 'function') __rig_callbacks.push(callback); },
                  reset() { __rig_emit(0, -1, 0); },
                  resetBone(bone) { const target=__rig_bone(bone); if (target>=0) __rig_emit(1,target,0); },
                  setPosition(bone,x,y,z) { __rig_push3(2,__rig_bone(bone),x,y,z); },
                  move(bone,x,y,z) { __rig_push3(3,__rig_bone(bone),x,y,z); },
                  setRotation(bone,x,y,z) { __rig_push3(4,__rig_bone(bone),__rig_number(x)*__rig_rad,__rig_number(y)*__rig_rad,__rig_number(z)*__rig_rad); },
                  rotate(bone,x,y,z) { __rig_push3(5,__rig_bone(bone),__rig_number(x)*__rig_rad,__rig_number(y)*__rig_rad,__rig_number(z)*__rig_rad); },
                  setRotationRadians(bone,x,y,z) { __rig_push3(4,__rig_bone(bone),x,y,z); },
                  rotateRadians(bone,x,y,z) { __rig_push3(5,__rig_bone(bone),x,y,z); },
                  setQuaternion(bone,x,y,z,w) { const target=__rig_bone(bone); if(target>=0) __rig_emit(6,target,4,__rig_number(x),__rig_number(y),__rig_number(z),__rig_number(w,1)); },
                  setScale(bone,x,y,z) { __rig_push3(7,__rig_bone(bone),x,y,z); },
                  bend(deformer,angle,falloff=1) { __rig_push_angle(8,__rig_deform(deformer),__rig_number(angle)*__rig_rad,falloff); },
                  bendRadians(deformer,angle,falloff=1) { __rig_push_angle(8,__rig_deform(deformer),angle,falloff); },
                  twist(deformer,angle,falloff=1) { __rig_push_angle(9,__rig_deform(deformer),__rig_number(angle)*__rig_rad,falloff); },
                  twistRadians(deformer,angle,falloff=1) { __rig_push_angle(9,__rig_deform(deformer),angle,falloff); },
                  clearDeform(deformer) { const target=__rig_deform(deformer); if(target>=0) __rig_emit(10,target,0); },
                  reachHand(side,targetBone,x=0,y=0,z=0,hintX=0,hintY=.35,hintZ=.15,weight=1) {
                    side=String(side).toLowerCase();
                    const upper=__rig_bone(side==='left'?'left_upper_arm':side==='right'?'right_upper_arm':'');
                    const target=__rig_bone(targetBone);
                    if(upper>=0 && target>=0) __rig_emit(11,upper,8,target,
                      __rig_number(x),__rig_number(y),__rig_number(z),
                      __rig_number(hintX),__rig_number(hintY,.35),__rig_number(hintZ,.15),
                      Math.max(0,Math.min(1,__rig_number(weight,1))));
                  },
                  placeItem(side,targetBone,x=0,y=0,z=0,rx=0,ry=0,rz=0,weight=1) {
                    side=String(side).toLowerCase();
                    const control=__rig_bone(side==='left'?'left_item_control':side==='right'?'right_item_control':'');
                    const target=__rig_bone(targetBone);
                    if(control>=0 && target>=0) __rig_emit(12,control,8,target,
                      __rig_number(x),__rig_number(y),__rig_number(z),
                      __rig_number(rx)*__rig_rad,__rig_number(ry)*__rig_rad,__rig_number(rz)*__rig_rad,
                      Math.max(0,Math.min(1,__rig_number(weight,1))));
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
                // The legacy Object[] entry point still constructs independent contexts.
                  // Native path: a single mutable context with stable nested object identities.
                  // This object is borrowed for the duration of a synchronized JS evaluation.
                  const __rig_native_context = __rig_unpack_context([]);
                  const __rig_update_native_context = strings => {
                  const packed=__rig_input, context=__rig_native_context;
                  const useTicks=__rig_number(packed?.[32]);
                  context.playerId = String(strings[0] ?? '');
                  context.age = __rig_number(packed?.[1]);
                  context.tickDelta = __rig_number(packed?.[2]);
                  context.deltaSeconds = __rig_number(packed?.[3]);
                  context.yaw = __rig_number(packed?.[4]);
                  context.pitch = __rig_number(packed?.[5]);
                  context.swing = __rig_number(packed?.[6]);
                  context.velocity.x = __rig_number(packed?.[7]);
                  context.velocity.y = __rig_number(packed?.[8]);
                  context.velocity.z = __rig_number(packed?.[9]);
                  context.onGround = !!packed?.[10];
                  context.crouching = !!packed?.[11];
                  context.sprinting = !!packed?.[12];
                  context.swimming = !!packed?.[13];
                  context.fallFlying = !!packed?.[14];
                  context.passenger = !!packed?.[15];
                  context.usingItem = !!packed?.[16];
                  context.pose = String(strings[1] ?? 'standing');
                  context.mainArm = String(strings[2] ?? 'right');
                  context.mainItem = String(strings[3] ?? 'minecraft:air');
                  context.offItem = String(strings[4] ?? 'minecraft:air');
                  context.style = String(strings[5] ?? 'Hybrid');
                  context.strength = __rig_number(packed?.[22], 1);
                  context.climbing = !!packed?.[23];
                  context.inWater = !!packed?.[24];
                  context.underWater = !!packed?.[25];
                  context.crawling = !!packed?.[26];
                  context.fallDistance = __rig_number(packed?.[27]);
                  context.y = __rig_number(packed?.[28]);
                  context.useAction = String(strings[6] ?? 'none');
                  context.useItem = String(strings[7] ?? 'minecraft:air');
                  context.useArm = String(strings[8] ?? 'none');
                  context.useTicks = useTicks;
                  context.swingIndex = Math.trunc(__rig_number(packed?.[33]));
                  context.attackTime = __rig_number(packed?.[34]);
                  context.attackActive = !!packed?.[35];
                  context.bodyYaw = __rig_number(packed?.[36]);
                  context.headYaw = __rig_number(packed?.[37]);
                  context.headPitch = __rig_number(packed?.[38]);
                  context.walkAnimationPos = __rig_number(packed?.[39]);
                  context.walkAnimationSpeed = __rig_number(packed?.[40]);
                  context.speedValue = Math.max(1e-4,__rig_number(packed?.[41],1));
                  context.swimAmount = __rig_number(packed?.[42]);
                  context.fallFlyingTime = __rig_number(packed?.[43]);
                  context.shouldApplyFlyingYRot = !!packed?.[44];
                  context.flyingYRot = __rig_number(packed?.[45]);
                  context.vanillaCrouching = !!packed?.[46];
                  context.vanillaFallFlying = !!packed?.[47];
                  context.vanillaSwimming = !!packed?.[48];
                  context.vanillaPassenger = !!packed?.[49];
                  context.vanillaUsingItem = !!packed?.[50];
                  context.vanillaInWater = !!packed?.[51];
                  context.boatLeft = !!packed?.[52];
                  context.boatRight = !!packed?.[53];
                  context.boatLeftTime = __rig_number(packed?.[54]);
                  context.boatRightTime = __rig_number(packed?.[55]);
                  context.vehicleType = String(strings[9] ?? 'none');
                  context.horizontalSpeed = __rig_number(packed?.[57]);
                  context.continuousSeconds = __rig_number(packed?.[58]);
                  context.vanillaUseTicks = __rig_number(packed?.[59], useTicks);
                  context.attackArm = String(strings[10] ?? strings[2] ?? 'right');
                  context.swingAnimationType = String(strings[11] ?? 'none');
                  context.vanillaAttackTime = __rig_number(packed?.[62], packed?.[6]);
                  context.renderPosition.x = __rig_number(packed?.[63]);
                  context.renderPosition.y = __rig_number(packed?.[64]);
                  context.renderPosition.z = __rig_number(packed?.[65]);
                  context.leftArmPose = String(strings[12] ?? 'empty');
                  context.rightArmPose = String(strings[13] ?? 'empty');
                  context.maxCrossbowChargeDuration = Math.max(1e-4,__rig_number(packed?.[68],25));
                  context.creativeFlying = !!packed?.[69];
                  context.attackCooldown = Math.max(0,Math.min(1,__rig_number(packed?.[70],1)));
                  context.attackDuration = Math.max(.05,__rig_number(packed?.[71],.62));
                  context.useTimeSeconds = Math.max(0,__rig_number(packed?.[59], useTicks) / 20);
                  context.walkTime = __rig_number(packed?.[39]) / 20;
                  context.walkPhase = __rig_number(packed?.[39]) * 0.6662;
                  return context;
                  };
                const __rig_execute_context = context => {
                  if (__rig_binary_enabled) __rig_commands.length = 0;
                  else __rig_commands = [];
                  __rig_count = 0;
                  __rig_overflow = null;
                  globalThis.__combatant_player_rig_context = context;
                  try {
                    for (let i=0; i<__rig_callbacks.length; i++) __rig_callbacks[i](context, playerRig);
                    if (!__rig_binary_enabled) return __rig_commands;
                    return __rig_overflow === null ? __rig_count : [__rig_count, __rig_overflow];
                  } finally {
                    globalThis.__combatant_player_rig_context = null;
                    if (__rig_binary_enabled) __rig_commands.length = 0;
                    else __rig_commands = [];
                    __rig_count = 0;
                    __rig_overflow = null;
                  }
                };
                globalThis.__combatant_player_rig_execute = packed =>
                  __rig_execute_context(__rig_unpack_context(packed));
                globalThis.__combatant_player_rig_execute_native = strings => {
                  if (!globalThis.__combatant_rig_reuse_context) {
                    for(let i=0;i<72;i++) __rig_native_packed[i]=__rig_input[i];
                    for(let i=0;i<__rig_string_positions.length;i++)
                      __rig_native_packed[__rig_string_positions[i]]=strings[i];
                    return __rig_execute_context(__rig_unpack_context(__rig_native_packed));
                  }
                  return __rig_execute_context(__rig_update_native_context(strings));
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
        reusableInvocationArguments[0] = null;
        packedInput = null;
        if (packedInputBuffer != null) {
            try { packedInputBuffer.close(); } catch (Exception ignored) { }
            packedInputBuffer = null;
        }
        packedOutputBytes = null;
        if (packedOutputBuffer != null) {
            try { packedOutputBuffer.close(); } catch (Exception ignored) { }
            packedOutputBuffer = null;
        }
        if (runtime == null) return;
        try {
            JavetRuntimeBootstrap.closeRuntime(this, runtime);
        } catch (Throwable ignored) {
        } finally {
            runtime = null;
        }
    }
}

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.playeranimator.script;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * The stable, preallocated Float64Array output protocol shared with PlayerRigScriptRuntime's JS.
 * An isolate owns its native buffer; no reference may escape to the render pipeline.
 * Only immutable command objects are returned. The caller owns synchronization.
 */
final class PlayerRigPackedCommands {
    static final int CAPACITY = 256;
    static final int STRIDE = 11; // operation, target, argCount, 8 numeric args
    static final int VALUE_COUNT = CAPACITY * STRIDE;
    static final int BYTE_COUNT = VALUE_COUNT * Double.BYTES;

    private PlayerRigPackedCommands() { }

    static List<PlayerRigScriptCommand> decode(ByteBuffer source, int count) {
        if (source == null || count < 0 || count > CAPACITY || source.capacity() < BYTE_COUNT) {
            throw new IllegalArgumentException("Invalid PlayerRig V8 command buffer");
        }
        if (count == 0) return List.of();
        List<PlayerRigScriptCommand> commands = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int base = i * STRIDE * Double.BYTES;
            int op = (int) source.getDouble(base);
            int target = (int) source.getDouble(base + Double.BYTES);
            int size = (int) source.getDouble(base + 2 * Double.BYTES);
            if (size < 0 || size > STRIDE - 3) {
                throw new IllegalArgumentException("Corrupted PlayerRig command argument count: " + size);
            }
            float[] args = new float[size];
            for (int j = 0; j < size; j++) {
                args[j] = (float) source.getDouble(base + (3 + j) * Double.BYTES);
            }
            commands.add(new PlayerRigScriptCommand(op, target, args));
        }
        return commands;
    }

    /** Execute the native V8 command stream immediately, without per-command Java allocations. */
    static void apply(ByteBuffer source, int count, combatant.client.features.playeranimator.PlayerRigInstance instance) {
        validate(source, count);
        if (instance == null) throw new IllegalArgumentException("PlayerRig instance must not be null");
        // Validate the whole stream before mutating the rig. A corrupt stream must not leave
        // the instance with a half-applied pose.
        for (int i = 0; i < count; i++) {
            int base = i * STRIDE * Double.BYTES;
            int argc = (int) source.getDouble(base + 2 * Double.BYTES);
            if (argc < 0 || argc > STRIDE - 3 || !Double.isFinite(source.getDouble(base + 2 * Double.BYTES))) {
                throw new IllegalArgumentException("Invalid PlayerRig command argument count at " + i);
            }
        }
        for (int i = 0; i < count; i++) {
            int base = i * STRIDE * Double.BYTES;
            int op = (int) source.getDouble(base);
            int target = (int) source.getDouble(base + Double.BYTES);
            int argc = (int) source.getDouble(base + 2 * Double.BYTES);
            switch (op) {
                case PlayerRigScriptCommand.RESET_POSE -> instance.resetFrame();
                case PlayerRigScriptCommand.RESET_BONE -> instance.resetBone(target);
                case PlayerRigScriptCommand.SET_TRANSLATION -> instance.setTranslation(target, arg(source,base,argc,0,0f),arg(source,base,argc,1,0f),arg(source,base,argc,2,0f));
                case PlayerRigScriptCommand.ADD_TRANSLATION -> instance.addTranslation(target, arg(source,base,argc,0,0f),arg(source,base,argc,1,0f),arg(source,base,argc,2,0f));
                case PlayerRigScriptCommand.SET_ROTATION -> instance.setRotation(target, arg(source,base,argc,0,0f),arg(source,base,argc,1,0f),arg(source,base,argc,2,0f));
                case PlayerRigScriptCommand.ADD_ROTATION -> instance.addRotation(target, arg(source,base,argc,0,0f),arg(source,base,argc,1,0f),arg(source,base,argc,2,0f));
                case PlayerRigScriptCommand.SET_QUATERNION -> instance.setRotationQuaternion(target, arg(source,base,argc,0,0f),arg(source,base,argc,1,0f),arg(source,base,argc,2,0f),arg(source,base,argc,3,0f));
                case PlayerRigScriptCommand.SET_SCALE -> instance.setScale(target, arg(source,base,argc,0,1f),arg(source,base,argc,1,1f),arg(source,base,argc,2,1f));
                case PlayerRigScriptCommand.SET_BEND -> instance.setBend(target, arg(source,base,argc,0,0f),arg(source,base,argc,1,1f));
                case PlayerRigScriptCommand.SET_TWIST -> instance.setTwist(target, arg(source,base,argc,0,0f),arg(source,base,argc,1,1f));
                case PlayerRigScriptCommand.CLEAR_DEFORM -> instance.clearDeform(target);
                case PlayerRigScriptCommand.REACH_HAND_TO_BONE -> instance.reachHandToBone(target,Math.round(arg(source,base,argc,0,-1f)),arg(source,base,argc,1,0f),arg(source,base,argc,2,0f),arg(source,base,argc,3,0f),arg(source,base,argc,4,0f),arg(source,base,argc,5,0f),arg(source,base,argc,6,0f),arg(source,base,argc,7,1f));
                case PlayerRigScriptCommand.PLACE_ITEM_TO_BONE -> instance.placeItemToBone(target,Math.round(arg(source,base,argc,0,-1f)),arg(source,base,argc,1,0f),arg(source,base,argc,2,0f),arg(source,base,argc,3,0f),arg(source,base,argc,4,0f),arg(source,base,argc,5,0f),arg(source,base,argc,6,0f),arg(source,base,argc,7,1f));
                default -> { }
            }
        }
    }

    private static void validate(ByteBuffer source, int count) {
        if (source == null || count < 0 || count > CAPACITY || source.capacity() < BYTE_COUNT) {
            throw new IllegalArgumentException("Invalid PlayerRig V8 command buffer");
        }
    }

    private static float arg(ByteBuffer source, int base, int count, int index, float fallback) {
        if (index >= count) return fallback;
        float value = (float) source.getDouble(base + (3 + index) * Double.BYTES);
        return Float.isFinite(value) ? value : fallback;
    }

}

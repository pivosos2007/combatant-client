/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.playeranimator.script;

import combatant.client.features.playeranimator.PlayerRigInstance;

import java.util.ArrayList;
import java.util.List;

/** Compact command decoded from one batched V8 result. Angles cross the boundary in radians. */
public record PlayerRigScriptCommand(int operation, int target, float[] arguments) {
    public static final int RESET_POSE = 0;
    public static final int RESET_BONE = 1;
    public static final int SET_TRANSLATION = 2;
    public static final int ADD_TRANSLATION = 3;
    public static final int SET_ROTATION = 4;
    public static final int ADD_ROTATION = 5;
    public static final int SET_QUATERNION = 6;
    public static final int SET_SCALE = 7;
    public static final int SET_BEND = 8;
    public static final int SET_TWIST = 9;
    public static final int CLEAR_DEFORM = 10;
    public static final int REACH_HAND_TO_BONE = 11;

    public static List<PlayerRigScriptCommand> decode(Object raw) {
        if (!(raw instanceof Iterable<?> iterable)) return List.of();
        List<PlayerRigScriptCommand> commands = new ArrayList<>();
        for (Object entry : iterable) {
            if (!(entry instanceof List<?> values) || values.isEmpty()) continue;
            if (!(values.getFirst() instanceof Number operation)) continue;
            int target = values.size() > 1 && values.get(1) instanceof Number n ? n.intValue() : -1;
            float[] arguments = new float[Math.max(0, values.size() - 2)];
            int count = 0;
            for (int i = 2; i < values.size(); i++) {
                if (values.get(i) instanceof Number number) arguments[count++] = number.floatValue();
            }
            if (count != arguments.length) arguments = java.util.Arrays.copyOf(arguments, count);
            commands.add(new PlayerRigScriptCommand(operation.intValue(), target, arguments));
        }
        return commands;
    }

    public void apply(PlayerRigInstance instance) {
        if (instance == null) return;
        switch (operation) {
            case RESET_POSE -> instance.resetFrame();
            case RESET_BONE -> instance.resetBone(target);
            case SET_TRANSLATION -> instance.setTranslation(target, arg(0), arg(1), arg(2));
            case ADD_TRANSLATION -> instance.addTranslation(target, arg(0), arg(1), arg(2));
            case SET_ROTATION -> instance.setRotation(target, arg(0), arg(1), arg(2));
            case ADD_ROTATION -> instance.addRotation(target, arg(0), arg(1), arg(2));
            case SET_QUATERNION -> instance.setRotationQuaternion(target, arg(0), arg(1), arg(2), arg(3));
            case SET_SCALE -> instance.setScale(target, arg(0, 1f), arg(1, 1f), arg(2, 1f));
            case SET_BEND -> instance.setBend(target, arg(0), arg(1, 1f));
            case SET_TWIST -> instance.setTwist(target, arg(0), arg(1, 1f));
            case CLEAR_DEFORM -> instance.clearDeform(target);
            case REACH_HAND_TO_BONE -> instance.reachHandToBone(
                    target,
                    Math.round(arg(0, -1f)),
                    arg(1), arg(2), arg(3),
                    arg(4), arg(5), arg(6),
                    arg(7, 1f)
            );
            default -> {
            }
        }
    }

    private float arg(int index) {
        return arg(index, 0f);
    }

    private float arg(int index, float fallback) {
        return index >= 0 && index < arguments.length && Float.isFinite(arguments[index])
                ? arguments[index]
                : fallback;
    }
}

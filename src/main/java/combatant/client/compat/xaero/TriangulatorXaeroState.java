/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.compat.xaero;

import combatant.client.features.gui.hud.draggable.DraggableHudElement;
import combatant.client.features.gui.hud.draggable.DraggableHudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

public enum TriangulatorXaeroState {
    ;
    public static final String NAME = "Stronghold";
    public static final String SYMBOL = "S";
    public static final String SET = "combatant";
    public static final int DEFAULT_Y = 64;

    @Nullable
    public static XaeroSnapshot snapshot() {
        DraggableHudElement triangulator = DraggableHudElementRegistry.getById("triangulator");
        if (triangulator == null) return null;
        try {
            Object raw = triangulator.getClass().getMethod("getXaeroSnapshot").invoke(triangulator);
            if (raw == null) return null;
            return new XaeroSnapshot(
                    readDouble(raw, "overworldX"),
                    readDouble(raw, "overworldZ"),
                    readBoolean(raw, "ready"),
                    readDouble(raw, "confidence"),
                    readInt(raw, "eyeCount")
            );
        } catch (ReflectiveOperationException | ClassCastException ignored) {
            return null;
        }
    }

    public static SyncPoint toCurrentDimension(XaeroSnapshot snapshot) {
        boolean nether = isNetherView();
        double factor = nether ? 0.125 : 1.0;
        return new SyncPoint(
                (int) Math.round(snapshot.overworldX() * factor),
                DEFAULT_Y,
                (int) Math.round(snapshot.overworldZ() * factor)
        );
    }

    public static boolean isNetherView() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.level != null && mc.level.dimension() == Level.NETHER;
    }

    private static double readDouble(Object target, String method) throws ReflectiveOperationException {
        Object value = invoke(target, method);
        if (value instanceof Number number) return number.doubleValue();
        throw new ClassCastException(method);
    }

    private static boolean readBoolean(Object target, String method) throws ReflectiveOperationException {
        Object value = invoke(target, method);
        if (value instanceof Boolean bool) return bool;
        throw new ClassCastException(method);
    }

    private static int readInt(Object target, String method) throws ReflectiveOperationException {
        Object value = invoke(target, method);
        if (value instanceof Number number) return number.intValue();
        throw new ClassCastException(method);
    }

    private static Object invoke(Object target, String method) throws ReflectiveOperationException {
        Method accessor = target.getClass().getMethod(method);
        return accessor.invoke(target);
    }

    public record XaeroSnapshot(double overworldX, double overworldZ, boolean ready, double confidence, int eyeCount) {
    }

    public record SyncPoint(int x, int y, int z) {
    }
}

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.rhi.backend.gl;

import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.system.FunctionProvider;
import org.lwjgl.system.MemoryUtil;

import java.util.Set;
import java.util.HashSet;
import java.util.function.IntFunction;

final class GlTestCapabilities {
    private GlTestCapabilities() {}

    static GLCapabilities create(Set<String> extensions, Set<String> missingFunctions) throws Exception {
        var constructor = GLCapabilities.class.getDeclaredConstructor(
                FunctionProvider.class, Set.class, boolean.class, IntFunction.class);
        constructor.setAccessible(true);
        // Synthetic addresses are only inspected by the capability resolver, never invoked.
        FunctionProvider provider = name -> missingFunctions.contains(MemoryUtil.memASCII(MemoryUtil.memAddress(name))) ? 0L : 1L;
        IntFunction<PointerBuffer> buffers = BufferUtils::createPointerBuffer;
        Set<String> available = new HashSet<>(extensions);
        int[] versions = {11, 12, 13, 14, 15, 20, 21, 30, 31, 32, 33, 40, 41, 42, 43, 44, 45, 46};
        for (int version : versions) {
            if (!extensions.contains("OpenGL" + version)) continue;
            for (int lower : versions) {
                if (lower <= version) available.add("OpenGL" + lower);
            }
        }
        return constructor.newInstance(provider, available, false, buffers);
    }
}

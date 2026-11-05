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
import combatant.client.render.engine.renderer.ui.runtime.script.JavetRuntimeBootstrap;
import org.junit.jupiter.api.Test;

import java.nio.ByteOrder;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Runs against the bundled native Javet, not a JS-only or fake ByteBuffer implementation. */
class PlayerRigNativePackedBridgeTest {
    @Test
    void commandsCrossTheNativeFloat64BufferAndPreserveOverflow() throws Exception {
        AutoCloseable owner = () -> { };
        V8Runtime runtime = JavetRuntimeBootstrap.createRuntime(owner);
        try {
            runtime.setConverter(new JavetObjectConverter());
            runtime.getExecutor(PlayerRigScriptRuntime.bootstrap()).executeVoid();
            runtime.getExecutor("playerRig.onPose((ctx, rig) => { "
                    + "for(let i=0;i<300;i++)rig.move('head',i,i+1,i+2); "
                    + "})").executeVoid();
            try (V8ValueTypedArray array = runtime.getGlobalObject().get("__combatant_rig_output_buffer");
                 V8ValueArrayBuffer backing = array.getBuffer()) {
                assertEquals(PlayerRigPackedCommands.VALUE_COUNT, array.getLength());
                assertTrue(backing.getByteBuffer().isDirect());
                var bytes = backing.getByteBuffer().asReadOnlyBuffer().order(ByteOrder.nativeOrder());
                runtime.getExecutor("__combatant_rig_enable_binary()").executeVoid();
                Object raw = PlayerRigScriptRuntime.invokePacked(runtime, new Object[0]);
                assertInstanceOf(List.class, raw);
                List<?> overflow = (List<?>) raw;
                assertEquals(256, ((Number) overflow.get(0)).intValue());
                var first = PlayerRigPackedCommands.decode(bytes, 256);
                var tail = PlayerRigScriptCommand.decode(overflow.get(1));
                assertEquals(44, tail.size());
                assertEquals(300, first.size() + tail.size());
                assertArrayEquals(new float[]{0,1,2}, first.getFirst().arguments());
                assertArrayEquals(new float[]{255,256,257}, first.getLast().arguments());
                assertArrayEquals(new float[]{256,257,258}, tail.getFirst().arguments());
                assertArrayEquals(new float[]{299,300,301}, tail.getLast().arguments());
            }
        } finally {
            JavetRuntimeBootstrap.closeRuntime(owner, runtime);
        }
    }
}

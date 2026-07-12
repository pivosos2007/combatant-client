/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.world;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.world.phys.Vec3;
import combatant.client.render.engine.RenderState;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.core.RenderFrameContext;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.render.engine.uniform.MeshBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Compiles public Renderer3D batch requests into ordered world draw commands.
 */
public final class WorldBatcher {
    private final List<WorldDrawCommand> commands = new ArrayList<>();
    private final Map<PoolKey, List<MeshBuilder>> pools = new HashMap<>();
    private final Map<PoolKey, Integer> poolCursor = new HashMap<>();

    private WorldDrawCommand lastCommand;

    private static boolean isLineMode(RenderPipeline pipeline) {
        com.mojang.blaze3d.PrimitiveTopology mode = pipeline.getPrimitiveTopology();
        return mode == com.mojang.blaze3d.PrimitiveTopology.LINES
                || mode == com.mojang.blaze3d.PrimitiveTopology.DEBUG_LINES
                || mode == com.mojang.blaze3d.PrimitiveTopology.DEBUG_LINE_STRIP;
    }

    public void beginFrame() {
        commands.clear();
        poolCursor.clear();
        lastCommand = null;
    }

    public MeshBuilder batch(RenderPipeline pipeline,
                             Renderer3D.DepthMode depthMode,
                             float lineWidth,
                             Renderer3D.BatchBindings bindings) {
        if (pipeline == null) return null;

        int lineBits = isLineMode(pipeline)
                ? Float.floatToIntBits(lineWidth > 0.0f ? lineWidth : 1.0f)
                : 0;
        Renderer3D.BatchBindings resolvedBindings = bindings != null ? bindings : Renderer3D.BatchBindings.none();

        if (lastCommand != null && lastCommand.mesh().isBuilding()) {
            WorldDrawCommand candidate = new WorldDrawCommand(pipeline, depthMode, lineBits, resolvedBindings, lastCommand.mesh());
            if (candidate.canMerge(lastCommand)) {
                return lastCommand.mesh();
            }
        }

        MeshBuilder mesh = acquireMesh(pipeline);
        if (mesh == null) return null;
        mesh.beginWorld(currentCameraAnchor());

        WorldDrawCommand command = new WorldDrawCommand(pipeline, depthMode, lineBits, resolvedBindings, mesh);
        commands.add(command);
        lastCommand = command;
        return mesh;
    }

    private static Vec3 currentCameraAnchor() {
        RenderFrameContext ctx = CombatantRenderSystem.currentContext();
        if (ctx != null && ctx.camera() != null && ctx.camera().position() != null) {
            return ctx.camera().position();
        }
        if (RenderState.cameraPos != null) {
            return RenderState.cameraPos;
        }
        return Vec3.ZERO;
    }

    public List<WorldDrawCommand> commands() {
        return commands;
    }

    public int commandCount() {
        return commands.size();
    }

    public void clearAfterSubmit() {
        commands.clear();
        lastCommand = null;
    }

    private MeshBuilder acquireMesh(RenderPipeline pipeline) {
        PoolKey key = new PoolKey(pipeline);
        List<MeshBuilder> pool = pools.computeIfAbsent(key, k -> new ArrayList<>());
        int cursor = poolCursor.getOrDefault(key, 0);
        MeshBuilder mesh;
        if (cursor < pool.size()) {
            mesh = pool.get(cursor);
        } else {
            mesh = new MeshBuilder(pipeline);
            pool.add(mesh);
        }
        poolCursor.put(key, cursor + 1);
        return mesh;
    }

    private record PoolKey(RenderPipeline pipeline) {
    }
}

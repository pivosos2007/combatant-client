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
import java.util.IdentityHashMap;
import java.util.List;

/**
 * Compiles public Renderer3D batch requests into ordered world draw commands.
 *
 * <p>{@link #batch} runs once per emitted primitive (every ESP box face, glyph run, billboard quad),
 * so it must not allocate on the merge path: the merge test compares against the previous command's
 * fields directly, and mesh pools are keyed by pipeline identity with a per-pool cursor.</p>
 */
public final class WorldBatcher {
    private final List<WorldDrawCommand> commands = new ArrayList<>();
    private final IdentityHashMap<RenderPipeline, MeshPool> pools = new IdentityHashMap<>();
    private WorldDrawCommand lastCommand;

    private static boolean isLineMode(RenderPipeline pipeline) {
        com.mojang.blaze3d.PrimitiveTopology mode = pipeline.getPrimitiveTopology();
        return mode == com.mojang.blaze3d.PrimitiveTopology.LINES
                || mode == com.mojang.blaze3d.PrimitiveTopology.DEBUG_LINES
                || mode == com.mojang.blaze3d.PrimitiveTopology.DEBUG_LINE_STRIP;
    }

    public void beginFrame() {
        commands.clear();
        for (MeshPool pool : pools.values()) pool.cursor = 0;
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
        Renderer3D.DepthMode resolvedDepth = depthMode != null ? depthMode : Renderer3D.DepthMode.MAIN;
        Renderer3D.BatchBindings resolvedBindings = bindings != null ? bindings : Renderer3D.BatchBindings.none();
        WorldDrawCommand previous = lastCommand;
        if (previous != null && previous.mesh().isBuilding()
                && previous.canMerge(pipeline, resolvedDepth, lineBits, resolvedBindings)) {
            return previous.mesh();
        }
        MeshBuilder mesh = acquireMesh(pipeline);
        if (mesh == null) return null;
        mesh.beginWorld(currentCameraAnchor());
        WorldDrawCommand command = new WorldDrawCommand(pipeline, resolvedDepth, lineBits, resolvedBindings, mesh);
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
        MeshPool pool = pools.get(pipeline);
        if (pool == null) {
            pool = new MeshPool();
            pools.put(pipeline, pool);
        }
        MeshBuilder mesh;
        if (pool.cursor < pool.meshes.size()) {
            mesh = pool.meshes.get(pool.cursor);
        } else {
            mesh = new MeshBuilder(pipeline);
            pool.meshes.add(mesh);
        }
        pool.cursor++;
        return mesh;
    }

    private static final class MeshPool {
        private final ArrayList<MeshBuilder> meshes = new ArrayList<>();
        private int cursor;
    }
}

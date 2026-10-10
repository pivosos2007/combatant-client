/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.helpers;

import combatant.client.render.engine.pipeline.CombatantRenderPipelines;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.render.engine.uniform.MeshBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PlacementPreviewRenderer {
    private static final long TRACK_MS = 1050L;
    private static final long CONFIRM_MS = 700L;
    private static final int MAX_TRACKED = 96;
    private static final int MAX_CONFIRMED = 24;
    private static final double FACE_OFFSET = 0.012;

    private final Map<BlockPos, Pending> pending = new LinkedHashMap<>();
    private static final Map<BlockPos, Confirmation> confirmed = new LinkedHashMap<>();
    private static final Map<BlockPos, Pending> settling = new LinkedHashMap<>();
    private static ClientLevel confirmedLevel;
    private int successColor = 0xBBA0FFFF;
    private long confirmationDurationMs = CONFIRM_MS;

    public void setConfirmationDuration(double seconds) {
        confirmationDurationMs = Math.max(50L, Math.min(2500L, Math.round(seconds * 1000.0)));
    }
    private ClientLevel lastLevel;

    public void reset() {
        if (lastLevel != null && lastLevel == confirmedLevel) {
            settling.putAll(pending);
        }
        pending.clear();
        lastLevel = null;
    }

    public void tick(ClientLevel level, Collection<BlockPos> targets, BlockState intendedState, int successColor) {
        this.successColor = successColor;
        if (level != lastLevel) {
            reset();
            lastLevel = level;
        }
        if (level != confirmedLevel) {
            confirmed.clear();
            settling.clear();
            confirmedLevel = level;
        }
        if (level == null) return;
        long now = System.currentTimeMillis();
        BlockState expected = intendedState;
        Set<BlockPos> active = new HashSet<>(targets);
        for (Iterator<Map.Entry<BlockPos, Pending>> iterator = pending.entrySet().iterator(); iterator.hasNext();) {
            Map.Entry<BlockPos, Pending> entry = iterator.next();
            BlockPos pos = entry.getKey();
            Pending p = entry.getValue();
            BlockState actual = level.getBlockState(pos);
            if (actual.is(p.expected.getBlock())) {
                if (p.solidSince == 0L) p.solidSince = now;
                if (now - p.solidSince >= 45L) {
                    confirmed.put(pos, new Confirmation(now, this.successColor, this.confirmationDurationMs));
                    iterator.remove();
                }
            } else if (!actual.canBeReplaced() || now - p.lastTargeted > TRACK_MS) {
                iterator.remove();
            } else {
                p.solidSince = 0L;
            }
            if (active.contains(pos)) p.lastTargeted = now;
        }
        if (expected != null) {
            for (BlockPos pos : active) {
                if (level.getBlockState(pos).canBeReplaced()) {
                    Pending p = pending.get(pos);
                    if (p == null || !p.expected.is(expected.getBlock())) {
                        pending.put(pos.immutable(), new Pending(expected, now, successColor, confirmationDurationMs));
                    } else {
                        p.lastTargeted = now;
                    }
                }
            }
        }
        confirmed.entrySet().removeIf(entry -> now - entry.getValue().atMs >= entry.getValue().durationMs);
        while (pending.size() > MAX_TRACKED) pending.remove(pending.keySet().iterator().next());
        while (confirmed.size() > MAX_CONFIRMED) confirmed.remove(confirmed.keySet().iterator().next());
    }

    public void render(Renderer3D renderer, ClientLevel level, Collection<BlockPos> targets,
                       BlockState intendedState, int fillColor, int lineColor, float lineWidth) {
        if (renderer == null || level == null || level != lastLevel) return;
        if (intendedState != null) {
            int ghostTint = (Math.min(170, Math.round((fillColor >>> 24) * 2.15f)) << 24) | (fillColor & 0x00FFFFFF);
            int borderTint = alpha(lineColor, 0.45f);
            for (BlockPos pos : targets) {
                if (!level.getBlockState(pos).canBeReplaced()) continue;
                renderModel(renderer, intendedState, pos, ghostTint);
                if ((borderTint >>> 24) != 0) {
                    for (AABB shape : intendedState.getShape(level, pos).toAabbs()) {
                        renderer.outlineBox(shape.move(pos), borderTint, Math.max(0.7f, lineWidth * 0.7f), Renderer3D.DepthMode.MAIN);
                    }
                }
            }
        }
    }

    public static void renderConfirmed(Renderer3D renderer, ClientLevel level) {
        if (renderer == null || level == null || level != confirmedLevel) return;
        long now = System.currentTimeMillis();
        for (Iterator<Map.Entry<BlockPos, Pending>> iterator = settling.entrySet().iterator(); iterator.hasNext();) {
            Map.Entry<BlockPos, Pending> entry = iterator.next();
            Pending p = entry.getValue();
            BlockState actual = level.getBlockState(entry.getKey());
            if (actual.is(p.expected.getBlock())) {
                if (p.solidSince == 0L) p.solidSince = now;
                if (now - p.solidSince >= 45L) {
                    confirmed.put(entry.getKey(), new Confirmation(now, p.color, p.durationMs));
                    iterator.remove();
                }
            } else if (!actual.canBeReplaced() || now - p.lastTargeted > TRACK_MS) {
                iterator.remove();
            } else {
                p.solidSince = 0L;
            }
        }
        while (settling.size() > MAX_TRACKED) settling.remove(settling.keySet().iterator().next());
        for (Map.Entry<BlockPos, Confirmation> entry : confirmed.entrySet()) {
            Confirmation effect = entry.getValue();
            if (now - effect.atMs >= effect.durationMs || level.getBlockState(entry.getKey()).canBeReplaced()) continue;
            renderHoneycomb(renderer, entry.getKey(), (now - effect.atMs) / (double) effect.durationMs, effect.color);
        }
    }

    public static void renderModel(Renderer3D renderer, BlockState state, BlockPos pos, int tint) {
        if ((tint >>> 24) == 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getModelManager() == null) return;
        BlockStateModel model = mc.getModelManager().getBlockStateModelSet().get(state);
        if (model == null) return;
        List<BlockStateModelPart> parts = new ArrayList<>();
        model.collectParts(RandomSource.create(pos.asLong()), parts);
        if (parts.isEmpty()) {
            if (mc.level != null) {
                for (AABB box : state.getShape(mc.level, pos).toAabbs()) {
                    renderer.filledBox(box.move(pos), alpha(tint, 0.55f), Renderer3D.DepthMode.MAIN);
                }
            }
            return;
        }
        Map<Identifier, MeshBuilder> byAtlas = new HashMap<>();
        for (BlockStateModelPart part : parts) {
            for (int face = 0; face <= 6; face++) {
                Direction side = face < 6 ? Direction.values()[face] : null;
                for (BakedQuad quad : part.getQuads(side)) {
                    Identifier atlas = quad.materialInfo().sprite().atlasLocation();
                    MeshBuilder mesh = byAtlas.computeIfAbsent(atlas,
                            id -> renderer.batchTextured(CombatantRenderPipelines.WORLD_TEXTURED_DEPTH, id, Renderer3D.DepthMode.MAIN));
                    if (mesh == null) continue;
                    mesh.ensureQuadCapacity();
                    int quadColor = tint;
                    if (quad.materialInfo().isTinted() && mc.getBlockColors() != null && mc.level != null) {
                        var source = mc.getBlockColors().getTintSource(state, quad.materialInfo().tintIndex());
                        if (source != null) {
                            int blockRgb = source.colorInWorld(state, mc.level, pos);
                            int r = ((tint >>> 16) & 255) * ((blockRgb >>> 16) & 255) / 255;
                            int g = ((tint >>> 8) & 255) * ((blockRgb >>> 8) & 255) / 255;
                            int b = (tint & 255) * (blockRgb & 255) / 255;
                            quadColor = (tint & 0xFF000000) | (r << 16) | (g << 8) | b;
                        }
                    }
                    int[] indices = new int[4];
                    for (int i = 0; i < 4; i++) {
                        Vector3fc v = quad.position(i);
                        long uv = quad.packedUV(i);
                        indices[i] = mesh.vec3(pos.getX() + v.x(), pos.getY() + v.y(), pos.getZ() + v.z())
                                .vec2(UVPair.unpackU(uv), UVPair.unpackV(uv)).colorArgb(quadColor).next();
                    }
                    mesh.quad(indices[0], indices[1], indices[2], indices[3]);
                }
            }
        }
    }

    private static void renderHoneycomb(Renderer3D renderer, BlockPos pos, double time, int argb) {
        double fade = Math.pow(Math.max(0.0, 1.0 - time), 1.6);
        MeshBuilder mesh = renderer.batch(CombatantRenderPipelines.WORLD_COLORED_LINES, Renderer3D.DepthMode.MAIN);
        if (mesh == null) return;
        final int columns = 4;
        final double radius = 0.138;
        for (Direction side : Direction.values()) {
            for (int row = 0; row < 4; row++) {
                for (int column = 0; column < columns; column++) {
                    double u = 0.14 + column * 0.24 + (row % 2) * 0.12;
                    double v = 0.14 + row * 0.22;
                    if (u > 0.92 || v > 0.93) continue;
                    double distance = Math.hypot(u - 0.5, v - 0.5);
                    double wave = Math.max(0.0, Math.min(1.0, (time * 1.35 - distance + 0.13) * 8.0));
                    double intensity = fade * wave * Math.max(0.25, 1.0 - distance * 0.65);
                    int color = alpha(argb, (float) (intensity * 0.85));
                    if ((color >>> 24) == 0) continue;
                    for (int edge = 0; edge < 6; edge++) {
                        double a0 = (edge + 0.5) * Math.PI / 3.0;
                        double a1 = (edge + 1.5) * Math.PI / 3.0;
                        double[] p0 = surface(pos, side, u + Math.cos(a0) * radius, v + Math.sin(a0) * radius);
                        double[] p1 = surface(pos, side, u + Math.cos(a1) * radius, v + Math.sin(a1) * radius);
                        mesh.ensureLineCapacity();
                        int i0 = mesh.vec3(p0[0], p0[1], p0[2]).colorArgb(color).next();
                        int i1 = mesh.vec3(p1[0], p1[1], p1[2]).colorArgb(color).next();
                        mesh.line(i0, i1);
                    }
                }
            }
        }
    }

    private static double[] surface(BlockPos pos, Direction side, double u, double v) {
        double x = pos.getX(), y = pos.getY(), z = pos.getZ();
        return switch (side) {
            case UP -> new double[]{x + u, y + 1 + FACE_OFFSET, z + v};
            case DOWN -> new double[]{x + u, y - FACE_OFFSET, z + v};
            case NORTH -> new double[]{x + u, y + v, z - FACE_OFFSET};
            case SOUTH -> new double[]{x + u, y + v, z + 1 + FACE_OFFSET};
            case WEST -> new double[]{x - FACE_OFFSET, y + v, z + u};
            case EAST -> new double[]{x + 1 + FACE_OFFSET, y + v, z + u};
        };
    }

    private static int alpha(int color, float scale) {
        int a = Math.max(0, Math.min(255, Math.round((color >>> 24) * scale)));
        return (a << 24) | (color & 0xFFFFFF);
    }

    private record Confirmation(long atMs, int color, long durationMs) {
    }

    private static final class Pending {
        private final BlockState expected;
        private final int color;
        private final long durationMs;
        private long lastTargeted;
        private long solidSince;

        private Pending(BlockState expected, long now, int color, long durationMs) {
            this.expected = expected;
            this.lastTargeted = now;
            this.color = color;
            this.durationMs = durationMs;
        }
    }
}

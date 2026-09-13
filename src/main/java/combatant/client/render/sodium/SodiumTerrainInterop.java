/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.sodium;

import com.mojang.blaze3d.textures.GpuSampler;
import combatant.client.render.engine.material.MaterialDomain;
import combatant.client.render.engine.scene.SceneDrawClass;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.caffeinemc.mods.sodium.client.util.FogParameters;

import java.util.concurrent.CopyOnWriteArrayList;

/** Central terrain submission boundary between Sodium and the Combatant world renderer. */
public final class SodiumTerrainInterop {
    private final SodiumRenderBridge bridge;
    private final CopyOnWriteArrayList<SodiumTerrainObserver> observers = new CopyOnWriteArrayList<>();

    private long terrainUpdatesScheduled;
    private long rebuildsScheduled;
    private long observedOpaqueDraws;
    private long observedCutoutDraws;
    private long observedTranslucentDraws;
    private long observedUnknownDraws;
    private long interopErrors;

    SodiumTerrainInterop(SodiumRenderBridge bridge) {
        this.bridge = bridge;
    }

    public void beginFrame() {
        terrainUpdatesScheduled = 0L;
        rebuildsScheduled = 0L;
        observedOpaqueDraws = 0L;
        observedCutoutDraws = 0L;
        observedTranslucentDraws = 0L;
        observedUnknownDraws = 0L;
        interopErrors = 0L;
    }

    /** Registers a non-cancelling observer. Closing the returned handle unregisters it. */
    public AutoCloseable observe(SodiumTerrainObserver observer) {
        if (observer == null) return () -> { };
        observers.addIfAbsent(observer);
        return () -> observers.remove(observer);
    }

    public void beforeTerrainDraw(ChunkRenderMatrices matrices,
                                  TerrainRenderPass pass,
                                  double cameraX,
                                  double cameraY,
                                  double cameraZ,
                                  FogParameters fog,
                                  GpuSampler sampler) {
        try {
            SodiumTerrainSubmission submission = submission(matrices, pass, cameraX, cameraY, cameraZ, fog, sampler);
            recordObserved(submission.drawClass());
            for (SodiumTerrainObserver observer : observers) {
                try {
                    observer.beforeTerrainDraw(submission);
                } catch (Throwable ignored) {
                    interopErrors++;
                }
            }
        } catch (Throwable ignored) {
            interopErrors++;
        }
    }

    public void afterTerrainDraw(ChunkRenderMatrices matrices,
                                 TerrainRenderPass pass,
                                 double cameraX,
                                 double cameraY,
                                 double cameraZ,
                                 FogParameters fog,
                                 GpuSampler sampler) {
        if (observers.isEmpty()) return;
        try {
            SodiumTerrainSubmission submission = submission(matrices, pass, cameraX, cameraY, cameraZ, fog, sampler);
            for (SodiumTerrainObserver observer : observers) {
                try {
                    observer.afterTerrainDraw(submission);
                } catch (Throwable ignored) {
                    interopErrors++;
                }
            }
        } catch (Throwable ignored) {
            interopErrors++;
        }
    }

    public void scheduleTerrainUpdate() {
        bridge.scheduleTerrainUpdate();
    }

    public void scheduleRebuildForBlockArea(int minX, int minY, int minZ,
                                            int maxX, int maxY, int maxZ,
                                            boolean important) {
        bridge.scheduleRebuildForBlockArea(minX, minY, minZ, maxX, maxY, maxZ, important);
    }

    public void recordTerrainUpdateScheduled() {
        terrainUpdatesScheduled++;
    }

    public void recordRebuildScheduled() {
        rebuildsScheduled++;
    }

    public void recordInteropError() {
        interopErrors++;
    }

    public SodiumTerrainInteropStatsSnapshot statsSnapshot() {
        return new SodiumTerrainInteropStatsSnapshot(
                terrainUpdatesScheduled,
                rebuildsScheduled,
                observedOpaqueDraws,
                observedCutoutDraws,
                observedTranslucentDraws,
                observedUnknownDraws,
                interopErrors
        );
    }

    private static SodiumTerrainSubmission submission(ChunkRenderMatrices matrices,
                                                      TerrainRenderPass pass,
                                                      double cameraX,
                                                      double cameraY,
                                                      double cameraZ,
                                                      FogParameters fog,
                                                      GpuSampler sampler) {
        SceneDrawClass drawClass = classifyDraw(pass);
        return new SodiumTerrainSubmission(
                drawClass,
                classifyMaterial(drawClass),
                matrices,
                pass,
                cameraX,
                cameraY,
                cameraZ,
                fog,
                sampler
        );
    }

    private static SceneDrawClass classifyDraw(TerrainRenderPass pass) {
        if (pass == DefaultTerrainRenderPasses.SOLID) return SceneDrawClass.TERRAIN_OPAQUE;
        if (pass == DefaultTerrainRenderPasses.CUTOUT) return SceneDrawClass.TERRAIN_CUTOUT;
        if (pass == DefaultTerrainRenderPasses.TRANSLUCENT) return SceneDrawClass.TERRAIN_TRANSLUCENT;
        return SceneDrawClass.UNKNOWN;
    }

    private static MaterialDomain classifyMaterial(SceneDrawClass drawClass) {
        return switch (drawClass) {
            case TERRAIN_OPAQUE -> MaterialDomain.OPAQUE;
            case TERRAIN_CUTOUT -> MaterialDomain.CUTOUT;
            case TERRAIN_TRANSLUCENT -> MaterialDomain.TRANSLUCENT;
            default -> MaterialDomain.UNKNOWN;
        };
    }

    private void recordObserved(SceneDrawClass drawClass) {
        switch (drawClass) {
            case TERRAIN_OPAQUE -> observedOpaqueDraws++;
            case TERRAIN_CUTOUT -> observedCutoutDraws++;
            case TERRAIN_TRANSLUCENT -> observedTranslucentDraws++;
            default -> observedUnknownDraws++;
        }
    }
}

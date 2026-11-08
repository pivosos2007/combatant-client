/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.compat.seedcracker;

import combatant.client.config.subsystem.SeedCrackerConfig;
import combatant.client.features.command.CommandOutput;
import combatant.client.util.logging.DebugLog;
import combatant.client.runtime.RuntimeGate;
import kaptainwutax.seedcrackerX.SeedCracker;
import kaptainwutax.seedcrackerX.config.Config;
import kaptainwutax.seedcrackerX.finder.FinderQueue;
import kaptainwutax.seedcrackerX.cracker.HashedSeedData;
import java.lang.reflect.InvocationTargetException;

/** Loaded ONLY after Fabric confirms SeedCrackerX is present. No rewritten cracking algorithms. */
final class SeedCrackerIntegration {
    private static String appliedVersion;
    private static Boolean appliedActive;
    private static Boolean appliedOverlay;
    private static String failedVersion = "";
    private static String failureReason = "";
    private static String effectiveWorldgen = "unknown";
    private static boolean fallbackWorldgen;
    private static boolean pendingFallbackNotice;
    private static Boolean appliedHashGuard;
    private static boolean nativeEndCity, nativeEndPillars, nativeEndGateway, nativeBiome, nativeDungeon;
    private static boolean nativeFindersCaptured;
    private SeedCrackerIntegration() { }

    static void onEntrypoint() {
        Config cfg = Config.get();
        captureNativeFinderDefaults(cfg);
        SeedCrackerNativeState.captureDefaults();
        cfg.databaseSubmits = false;
        cfg.anonymusSubmits = false;
        cfg.active = SeedCrackerConfig.get().enabled() && RuntimeGate.canRunClientLogic();
        cfg.render = SeedCrackerConfig.get().showNativeOverlay() ? Config.RenderType.XRAY : Config.RenderType.OFF;
        appliedActive = cfg.active;
        appliedOverlay = SeedCrackerConfig.get().showNativeOverlay();
        // The upstream packet seed may be replaced by a Paper-side plugin.
        configureHashGuard(cfg, SeedCrackerConfig.get().ignoreServerHash());
    }
    static void sync() {
        if (SeedCracker.get() == null) return;
        Config cfg = Config.get();
        captureNativeFinderDefaults(cfg);
        cfg.databaseSubmits = false;
        cfg.anonymusSubmits = false;
        boolean hashGuard = SeedCrackerConfig.get().ignoreServerHash();
        if (appliedHashGuard != null && appliedHashGuard != hashGuard) {
            // Never mix evidence collected under different trust assumptions.
            SeedCracker.get().reset();
            SeedCrackerChunkBackfill.clear();
        }
        configureHashGuard(cfg, hashGuard);
        String profile = SeedCrackerRuntime.profile();
        if (!profile.equals(appliedVersion)) {
            if (profile.equals(failedVersion)) {
                cfg.active = false;
                appliedActive = false;
                return;
            }
            // Cancel old-version work before switching the upstream feature tables.
            if (appliedVersion != null) SeedCracker.get().reset();
            if (switchVersion(cfg, profile)) {
                appliedVersion = profile;
                failedVersion = "";
                failureReason = "";
                pendingFallbackNotice = fallbackWorldgen;
            } else {
                failedVersion = profile;
                cfg.active = false;
                appliedActive = false;
                return;
            }
        }
        boolean enabled = SeedCrackerConfig.get().enabled() && RuntimeGate.canRunClientLogic();
        if (!Boolean.valueOf(enabled).equals(appliedActive) || cfg.active != enabled) {
            cfg.active = enabled;
            if (!enabled) SeedCracker.get().getDataStorage().getTimeMachine().shouldTerminate = true;
            else SeedCracker.get().getDataStorage().getTimeMachine().shouldTerminate = false;
            appliedActive = enabled;
        }
        if (enabled && pendingFallbackNotice) {
            CommandOutput.warning("SeedCrackerX: profile '" + profile
                    + "' is not defined by bundled mc_core; using latest model '" + effectiveWorldgen
                    + "'. Recovery is enabled, but results remain unverified candidates until confirmed."
                    + " If these chunks were generated earlier, set the matching @seed profile.");
            pendingFallbackNotice = false;
        }
        boolean overlay = SeedCrackerConfig.get().showNativeOverlay();
        if (!Boolean.valueOf(overlay).equals(appliedOverlay)) {
            cfg.render = overlay ? Config.RenderType.XRAY : Config.RenderType.OFF;
            appliedOverlay = overlay;
        }
    }
    /**
     * SeedCrackerX itself initializes its feature tables with MCVersion.latest().
     * Some builds of seedfinding's mc_core still label the latest Java generator
     * with a pre-26.x name, even when the upstream Fabric mod targets MC 26.3.
     * Do not disable chunk collection just because the protocol's release name
     * is missing from MCVersion. Preserve the actual model name and explicitly
     * mark recovered values provisional until independently verified.
     */
    private static boolean switchVersion(Config config, String requested) {
        fallbackWorldgen = false;
        effectiveWorldgen = "unknown";
        failureReason = "";
        try {
            Class<?> versionType = Class.forName("com.seedfinding.mccore.version.MCVersion", false,
                    SeedCracker.class.getClassLoader());
            Object resolved = versionType.getMethod("fromString", String.class).invoke(null, requested);
            if (resolved == null && requested.matches("26\\.\\d+(?:\\.\\d+)?")) {
                // Same fallback the unmodified SeedCrackerX uses at startup.
                resolved = versionType.getMethod("latest").invoke(null);
                fallbackWorldgen = true;
            }
            if (resolved == null) {
                failureReason = "mc_core does not define " + requested
                        + "; select another worldgen profile with @seed profile";
                DebugLog.warn("SeedCrackerX: %s", failureReason);
                return false;
            }
            Config.class.getMethod("setVersion", versionType).invoke(config, resolved);
            effectiveWorldgen = String.valueOf(config.getVersion());
            return true;
        } catch (ReflectiveOperationException | LinkageError error) {
            Throwable root = error instanceof InvocationTargetException invocation && invocation.getCause() != null
                    ? invocation.getCause() : error;
            failureReason = root.getClass().getSimpleName()
                    + (root.getMessage() == null ? "" : ": " + root.getMessage());
            DebugLog.error("SeedCrackerX worldgen initialization failed for " + requested, error);
            return false;
        }
    }

    static boolean isWorldgenFallback() { return fallbackWorldgen && appliedVersion != null; }

    /** Retry explicit user requests even after an earlier failed profile resolution. */
    static void allowProfileRetry() {
        failedVersion = "";
        failureReason = "";
    }

    static boolean active() {
        return SeedCracker.get() != null && Config.get().active && Boolean.TRUE.equals(appliedActive)
                && appliedVersion != null && !appliedVersion.equals(failedVersion);
    }
    static int baseBits() {
        return SeedCracker.get() == null ? 0 : (int) Math.round(SeedCracker.get().getDataStorage().getBaseBits());
    }
    static String status() {
        if (SeedCracker.get() == null) return "upstream=initializing";
        var data = SeedCracker.get().getDataStorage();
        var machine = data.getTimeMachine();
        String failure = failedVersion.isEmpty() ? "" : ", profileError=" + failedVersion
                + " (" + failureReason + ")";
        return "upstreamActive=" + active() + ", runtimeReady=" + RuntimeGate.canRunClientLogic()
                + ", enabled=" + SeedCrackerConfig.get().enabled() + failure
                + ", model=" + effectiveWorldgen + (fallbackWorldgen ? " [latest fallback; candidate-only]" : "")
                + ", detectors=" + FinderQueue.get().getActiveFinderTypes().size()
                + ", observations=" + data.baseSeedData.size()
                + ", structures=" + Math.round(data.getBaseBits()) + " bits"
                + ", lifting=" + Math.round(data.getLiftingBits()) + " bits"
                + ", decorators=" + Math.round(data.getDecoratorBits()) + " bits"
                + ", solving=" + machine.isRunning
                + ", structureCandidates=" + machine.structureSeeds.size()
                + ", worldCandidates=" + machine.worldSeeds.size()
                + ", hashGuard=" + SeedCrackerConfig.get().ignoreServerHash();
    }
    private static void captureNativeFinderDefaults(Config cfg) {
        if (nativeFindersCaptured) return;
        nativeFindersCaptured = true;
        nativeEndCity = cfg.endCity.get();
        nativeEndPillars = cfg.endPillars.get();
        nativeEndGateway = cfg.endGateway.get();
        nativeBiome = cfg.biome.get();
        nativeDungeon = cfg.dungeon.get();
    }
    private static void configureHashGuard(Config cfg, boolean enabled) {
        if (Boolean.valueOf(enabled).equals(appliedHashGuard)) return;
        cfg.endCity.set(!enabled && nativeEndCity);
        cfg.endPillars.set(!enabled && nativeEndPillars);
        cfg.endGateway.set(!enabled && nativeEndGateway);
        cfg.biome.set(!enabled && nativeBiome);
        cfg.dungeon.set(enabled || nativeDungeon);
        // A non-null zero hash is safe for upstream's login/database handling;
        // TimeMachine's hash branch explicitly excludes zero.
        if (enabled && SeedCracker.get() != null) {
            SeedCracker.get().getDataStorage().hashedSeedData = new HashedSeedData(0L);
        }
        appliedHashGuard = enabled;
    }
    /** The native cracker command may change Config.active directly. */
    static void refreshActiveState() { appliedActive = null; }

    static void stop() {
        if (SeedCracker.get() == null) return;
        Config.get().active = false;
        SeedCracker.get().getDataStorage().getTimeMachine().shouldTerminate = true;
        appliedActive = false;
    }
    static void reset() {
        if (SeedCracker.get() == null) return;
        SeedCracker.get().reset();
        FinderQueue.get().clear();
        SeedCrackerChunkBackfill.clear();
    }
}

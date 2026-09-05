/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.compat.sodiumextra;

import combatant.client.util.logging.DebugLog;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Optional Sodium Extra bridge for the NoRender subset implemented by both mods.
 *
 * <p>Combatant remains the UI/config owner: its NoRender controls are always
 * visible and its own suppression hooks remain authoritative. When a compatible
 * Sodium Extra runtime is present, Combatant mirrors the active NoRender state
 * into Sodium Extra's already-loaded options object so Sodium Extra cannot apply
 * the opposite decision to the same render path. The mirror is runtime only;
 * this class never calls Sodium Extra's config writer.</p>
 *
 * <p>Before the override starts, Sodium Extra's values are snapshotted and are
 * restored when NoRender is disabled. If the reflected contract fails, Combatant
 * continues using its own hooks; Sodium Extra simply stops being synchronized.</p>
 */
public final class SodiumExtraNoRenderCompat {
    private static final String MOD_ID = "sodium-extra";
    private static final String CLIENT_CLASS =
            "me/flashyreese/mods/sodiumextra/client/SodiumExtraClientMod.class";
    private static final String OPTIONS_CLASS =
            "me/flashyreese/mods/sodiumextra/client/config/SodiumExtraGameOptions.class";

    private static final boolean LOADED = FabricLoader.getInstance().isModLoaded(MOD_ID);
    private static final String VERSION = resolveVersion();
    private static final boolean COMPATIBLE_CONTRACT = LOADED && hasResource(CLIENT_CLASS) && hasResource(OPTIONS_CLASS);
    private static volatile boolean bridgeResolved;
    private static volatile Bridge bridge;

    private SodiumExtraNoRenderCompat() {
    }

    public static boolean isLoaded() {
        return LOADED;
    }

    public static String version() {
        return VERSION;
    }

    /**
     * Mirrors Combatant's current hide toggles into Sodium Extra's render-enable
     * booleans. NoRender=true maps to SodiumExtra render=false.
     */
    public static void syncMigratedOptions(
            Map<String, Boolean> world,
            Map<String, Boolean> entities,
            Map<String, Boolean> particles
    ) {
        Bridge current = resolveBridge();
        if (current == null || !current.usable()) {
            return;
        }
        current.sync(world, entities, particles);
    }

    /** Restore the Sodium Extra values that existed before Combatant took ownership. */
    public static void releaseMigratedOptions() {
        Bridge current = bridgeResolved ? bridge : null;
        if (current != null) {
            current.release();
        }
    }

    /**
     * Reflection is deliberately lazy. Mixin config/prepare code may load this
     * class, but it must never trigger Knot class loading while the transformer
     * selection lock is held. The bridge is resolved only from NoRender runtime
     * synchronization.
     */
    private static Bridge resolveBridge() {
        if (bridgeResolved) {
            return bridge;
        }
        synchronized (SodiumExtraNoRenderCompat.class) {
            if (bridgeResolved) {
                return bridge;
            }

            if (COMPATIBLE_CONTRACT) {
                bridge = Bridge.tryCreate();
            }
            bridgeResolved = true;

            if (LOADED) {
                if (bridge != null && bridge.usable()) {
                    DebugLog.infoOnce(
                            "norender-sodium-extra-owner",
                            "[NoRender] Sodium Extra %s detected; Combatant is authoritative and mirrors overlapping render switches at runtime",
                            VERSION
                    );
                } else {
                    DebugLog.warnOnce(
                            "norender-sodium-extra-contract",
                            "[NoRender] Sodium Extra %s detected with an unknown/incompatible contract; Combatant suppression remains authoritative",
                            VERSION
                    );
                }
            }
            return bridge;
        }
    }

    private static boolean hasResource(String path) {
        return SodiumExtraNoRenderCompat.class.getClassLoader().getResource(path) != null;
    }

    private static String resolveVersion() {
        return FabricLoader.getInstance()
                .getModContainer(MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("<not loaded>");
    }

    private static boolean hidden(Map<String, Boolean> values, String key) {
        return values != null && Boolean.TRUE.equals(values.get(key));
    }

    private static final class Bridge {
        private final Method optionsMethod;
        private final Field detailSettings;
        private final Field renderSettings;
        private final Field particleSettings;

        private final Field sky;
        private final Field sun;
        private final Field moon;
        private final Field stars;
        private final Field rainSnow;

        private final Field itemFrame;
        private final Field armorStand;
        private final Field painting;
        private final Field piston;
        private final Field beaconBeam;
        private final Field enchantingTableBook;
        private final Field itemFrameNameTag;
        private final Field playerNameTag;

        private final Field allParticles;
        private final Field rainSplash;
        private final Field blockBreak;
        private final Field blockBreaking;

        private boolean usable = true;
        private boolean overrideActive;
        private Snapshot snapshot;

        private Bridge(
                Method optionsMethod,
                Field detailSettings,
                Field renderSettings,
                Field particleSettings,
                Field sky,
                Field sun,
                Field moon,
                Field stars,
                Field rainSnow,
                Field itemFrame,
                Field armorStand,
                Field painting,
                Field piston,
                Field beaconBeam,
                Field enchantingTableBook,
                Field itemFrameNameTag,
                Field playerNameTag,
                Field allParticles,
                Field rainSplash,
                Field blockBreak,
                Field blockBreaking
        ) {
            this.optionsMethod = optionsMethod;
            this.detailSettings = detailSettings;
            this.renderSettings = renderSettings;
            this.particleSettings = particleSettings;
            this.sky = sky;
            this.sun = sun;
            this.moon = moon;
            this.stars = stars;
            this.rainSnow = rainSnow;
            this.itemFrame = itemFrame;
            this.armorStand = armorStand;
            this.painting = painting;
            this.piston = piston;
            this.beaconBeam = beaconBeam;
            this.enchantingTableBook = enchantingTableBook;
            this.itemFrameNameTag = itemFrameNameTag;
            this.playerNameTag = playerNameTag;
            this.allParticles = allParticles;
            this.rainSplash = rainSplash;
            this.blockBreak = blockBreak;
            this.blockBreaking = blockBreaking;
        }

        static Bridge tryCreate() {
            try {
                ClassLoader loader = SodiumExtraNoRenderCompat.class.getClassLoader();
                Class<?> clientClass = Class.forName(
                        "me.flashyreese.mods.sodiumextra.client.SodiumExtraClientMod",
                        false,
                        loader
                );
                Class<?> optionsClass = Class.forName(
                        "me.flashyreese.mods.sodiumextra.client.config.SodiumExtraGameOptions",
                        false,
                        loader
                );
                Class<?> detailClass = Class.forName(
                        "me.flashyreese.mods.sodiumextra.client.config.SodiumExtraGameOptions$DetailSettings",
                        false,
                        loader
                );
                Class<?> renderClass = Class.forName(
                        "me.flashyreese.mods.sodiumextra.client.config.SodiumExtraGameOptions$RenderSettings",
                        false,
                        loader
                );
                Class<?> particleClass = Class.forName(
                        "me.flashyreese.mods.sodiumextra.client.config.SodiumExtraGameOptions$ParticleSettings",
                        false,
                        loader
                );

                return new Bridge(
                        clientClass.getMethod("options"),
                        optionsClass.getField("detailSettings"),
                        optionsClass.getField("renderSettings"),
                        optionsClass.getField("particleSettings"),
                        detailClass.getField("sky"),
                        detailClass.getField("sun"),
                        detailClass.getField("moon"),
                        detailClass.getField("stars"),
                        detailClass.getField("rainSnow"),
                        renderClass.getField("itemFrame"),
                        renderClass.getField("armorStand"),
                        renderClass.getField("painting"),
                        renderClass.getField("piston"),
                        renderClass.getField("beaconBeam"),
                        renderClass.getField("enchantingTableBook"),
                        renderClass.getField("itemFrameNameTag"),
                        renderClass.getField("playerNameTag"),
                        particleClass.getField("particles"),
                        particleClass.getField("rainSplash"),
                        particleClass.getField("blockBreak"),
                        particleClass.getField("blockBreaking")
                );
            } catch (Throwable error) {
                DebugLog.warnOnce(
                        "norender-sodium-extra-reflection-init",
                        "[NoRender] Failed to initialize Sodium Extra %s runtime bridge: %s",
                        VERSION,
                        error.toString()
                );
                return null;
            }
        }

        synchronized boolean usable() {
            return usable;
        }

        synchronized void sync(
                Map<String, Boolean> world,
                Map<String, Boolean> entities,
                Map<String, Boolean> particles
        ) {
            if (!usable) return;

            try {
                RuntimeOptions options = resolveOptions();
                if (!overrideActive) {
                    snapshot = Snapshot.capture(this, options);
                    overrideActive = true;
                }

                // Sodium Extra values mean "render enabled". Combatant values mean
                // "hide/disable", hence the inversion below.
                sky.setBoolean(options.detail, !hidden(world, "sky"));
                sun.setBoolean(options.detail, !hidden(world, "sun"));
                moon.setBoolean(options.detail, !hidden(world, "moon"));
                stars.setBoolean(options.detail, !hidden(world, "stars"));
                rainSnow.setBoolean(options.detail, !hidden(world, "weather"));

                itemFrame.setBoolean(options.render, !hidden(entities, "item_frames"));
                armorStand.setBoolean(options.render, !hidden(entities, "armor_stands"));
                painting.setBoolean(options.render, !hidden(entities, "paintings"));
                piston.setBoolean(options.render, !hidden(entities, "moving_pistons"));
                beaconBeam.setBoolean(options.render, !hidden(entities, "beacon_beams"));
                enchantingTableBook.setBoolean(options.render, !hidden(entities, "enchanting_table_book"));
                itemFrameNameTag.setBoolean(options.render, !hidden(entities, "item_frame_name_tags"));
                playerNameTag.setBoolean(options.render, !hidden(entities, "player_name_tags"));

                allParticles.setBoolean(options.particles, !hidden(particles, "all_particles"));
                rainSplash.setBoolean(options.particles, !hidden(particles, "rain_splash_particles"));
                blockBreak.setBoolean(options.particles, !hidden(particles, "block_break_particles"));
                blockBreaking.setBoolean(options.particles, !hidden(particles, "block_breaking_particles"));
            } catch (Throwable error) {
                fail(error);
            }
        }

        synchronized void release() {
            if (!overrideActive || snapshot == null) {
                return;
            }
            try {
                snapshot.restore(this, resolveOptions());
            } catch (Throwable error) {
                DebugLog.warnOnce(
                        "norender-sodium-extra-restore",
                        "[NoRender] Failed to restore Sodium Extra %s runtime options: %s",
                        VERSION,
                        error.toString()
                );
            } finally {
                overrideActive = false;
                snapshot = null;
            }
        }

        private void fail(Throwable error) {
            // Best-effort restore. Combatant hooks remain authoritative regardless.
            release();
            usable = false;
            DebugLog.warnOnce(
                    "norender-sodium-extra-runtime",
                    "[NoRender] Sodium Extra %s runtime mirror failed; Combatant suppression remains authoritative: %s",
                    VERSION,
                    error.toString()
            );
        }

        private RuntimeOptions resolveOptions() throws ReflectiveOperationException {
            Object options = optionsMethod.invoke(null);
            if (options == null) throw new IllegalStateException("Sodium Extra options() returned null");
            Object detail = detailSettings.get(options);
            Object render = renderSettings.get(options);
            Object particles = particleSettings.get(options);
            if (detail == null || render == null || particles == null) {
                throw new IllegalStateException("Sodium Extra options contain null migrated settings group");
            }
            return new RuntimeOptions(detail, render, particles);
        }
    }

    private record RuntimeOptions(Object detail, Object render, Object particles) {
    }

    private record Snapshot(
            boolean sky,
            boolean sun,
            boolean moon,
            boolean stars,
            boolean weather,
            boolean itemFrames,
            boolean armorStands,
            boolean paintings,
            boolean movingPistons,
            boolean beaconBeams,
            boolean enchantingTableBook,
            boolean itemFrameNameTags,
            boolean playerNameTags,
            boolean allParticles,
            boolean rainSplashParticles,
            boolean blockBreakParticles,
            boolean blockBreakingParticles
    ) {
        static Snapshot capture(Bridge b, RuntimeOptions o) throws IllegalAccessException {
            return new Snapshot(
                    b.sky.getBoolean(o.detail),
                    b.sun.getBoolean(o.detail),
                    b.moon.getBoolean(o.detail),
                    b.stars.getBoolean(o.detail),
                    b.rainSnow.getBoolean(o.detail),
                    b.itemFrame.getBoolean(o.render),
                    b.armorStand.getBoolean(o.render),
                    b.painting.getBoolean(o.render),
                    b.piston.getBoolean(o.render),
                    b.beaconBeam.getBoolean(o.render),
                    b.enchantingTableBook.getBoolean(o.render),
                    b.itemFrameNameTag.getBoolean(o.render),
                    b.playerNameTag.getBoolean(o.render),
                    b.allParticles.getBoolean(o.particles),
                    b.rainSplash.getBoolean(o.particles),
                    b.blockBreak.getBoolean(o.particles),
                    b.blockBreaking.getBoolean(o.particles)
            );
        }

        void restore(Bridge b, RuntimeOptions o) throws IllegalAccessException {
            b.sky.setBoolean(o.detail, sky);
            b.sun.setBoolean(o.detail, sun);
            b.moon.setBoolean(o.detail, moon);
            b.stars.setBoolean(o.detail, stars);
            b.rainSnow.setBoolean(o.detail, weather);
            b.itemFrame.setBoolean(o.render, itemFrames);
            b.armorStand.setBoolean(o.render, armorStands);
            b.painting.setBoolean(o.render, paintings);
            b.piston.setBoolean(o.render, movingPistons);
            b.beaconBeam.setBoolean(o.render, beaconBeams);
            b.enchantingTableBook.setBoolean(o.render, enchantingTableBook);
            b.itemFrameNameTag.setBoolean(o.render, itemFrameNameTags);
            b.playerNameTag.setBoolean(o.render, playerNameTags);
            b.allParticles.setBoolean(o.particles, allParticles);
            b.rainSplash.setBoolean(o.particles, rainSplashParticles);
            b.blockBreak.setBoolean(o.particles, blockBreakParticles);
            b.blockBreaking.setBoolean(o.particles, blockBreakingParticles);
        }
    }
}

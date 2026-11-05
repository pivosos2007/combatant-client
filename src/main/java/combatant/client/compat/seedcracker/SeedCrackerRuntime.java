/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.compat.seedcracker;

import combatant.client.config.subsystem.SeedCrackerConfig;
import combatant.client.features.command.CommandOutput;
import combatant.client.features.gui.chat.actions.ChatMessageActions;
import combatant.client.features.gui.chat.actions.MessageActionRegistry;
import combatant.client.util.combat.protocol.ProtocolUtil;
import combatant.client.util.logging.DebugLog;
import combatant.client.runtime.RuntimeGate;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Public Combatant facade; never loads original SeedCrackerX classes when that mod is absent. */
public final class SeedCrackerRuntime {
    private static final boolean PRESENT = FabricLoader.getInstance().isModLoaded("seedcrackerx");
    private static final String ACTION_TYPE = "combatant:seedoffer";
    private static final String ACTION_OWNER = "combatant.seed";
    private static MessageActionRegistry.Registration registration;
    private static final Set<String> offered = new HashSet<>();
    private static String previousWorld = "";
    private static String previousContext = "";
    private static String previousProfile = "";
    private static int activeTicks;
    private static boolean warnedAboutNoData;
    private SeedCrackerRuntime() { }

    public static boolean ignoreServerHash() { return SeedCrackerConfig.get().ignoreServerHash(); }

    /** Invoked by the optional upstream FinderQueue hook. */
    public static void onUpstreamChunk(ChunkPos position) {
        if (PRESENT && position != null) SeedCrackerChunkBackfill.onChunkData(position);
    }

    private static void clearProgress() {
        activeTicks = 0;
        warnedAboutNoData = false;
        if (PRESENT) SeedCrackerChunkBackfill.clear();
    }

    public static boolean installed() { return PRESENT; }
    public static boolean running() {
        return PRESENT && SeedCrackerConfig.get().enabled() && RuntimeGate.canRunClientLogic()
                && SeedCrackerIntegration.active();
    }
    public static String profile() {
        String selected = SeedCrackerConfig.get().worldgen();
        if (!"auto".equals(selected)) return selected;
        String protocol = ProtocolUtil.getProtocolVersion().name();
        // Detection is a heuristic, never proof that already generated chunks use this version.
        if (protocol == null || protocol.isBlank()) return "26.3";
        String candidate = protocol.trim();
        if (candidate.matches("v\\d+(_\\d+)+")) candidate = candidate.substring(1).replace('_', '.');
        return candidate;
    }

    public static synchronized void bootstrap() {
        if (registration != null) return;
        registration = ChatMessageActions.register(ACTION_OWNER, new MessageActionRegistry.Provider() {
            @Override public List<MessageActionRegistry.Action> actions(MessageActionRegistry.Context context) {
                if (!ACTION_TYPE.equals(context.type())) return List.of();
                return List.of(new MessageActionRegistry.Action("start", "Start seed recovery",
                        MessageActionRegistry.Tone.NORMAL, PRESENT && !running()),
                    new MessageActionRegistry.Action("status", "Seed status", MessageActionRegistry.Tone.NORMAL, true));
            }
            @Override public void execute(MessageActionRegistry.Context context, String actionId) {
                if (!ACTION_TYPE.equals(context.type())) return;
                if ("start".equals(actionId)) start();
                else if ("status".equals(actionId)) CommandOutput.send(status());
            }
        });
    }

    /** Called when Combatant's chat action providers are released during runtime shutdown. */
    public static synchronized void shutdownActions() {
        if (registration != null) { registration.close(); registration = null; }
        offered.clear();
    }

    public static void tick(Minecraft mc) {
        if (mc == null) return;
        if (registration == null && RuntimeGate.canRunClientLogic()) bootstrap();
        // Settings are scoped to the connected server AND the active dimension.
        SeedCrackerConfig.get().refreshContext();
        String identity = mc.level == null ? "" : SeedSnapshots.server(mc);
        String context = mc.level == null ? "" : identity + "|" + SeedSnapshots.dimension(mc);
        String gen = profile();
        boolean serverChanged = !identity.equals(previousWorld);
        if (serverChanged || !context.equals(previousContext) || !gen.equals(previousProfile)) {
            // Upstream's structure observations may span dimensions (e.g. End pillars).
            // Only reset that pipeline on a server transition or a different worldgen profile.
            if (PRESENT && !previousWorld.isEmpty() && (serverChanged || !gen.equals(previousProfile))) {
                try { SeedCrackerIntegration.reset(); }
                catch (LinkageError | RuntimeException error) { DebugLog.error("SeedCrackerX session reset failed", error); }
            }
            // Session-only snapshots remain accessible across dimensions within a connection.
            // They must never leak into a different server or survive disconnect.
            if (serverChanged) SeedSnapshots.clearSession();
            previousWorld = identity;
            previousContext = context;
            previousProfile = gen;
            offered.clear();
            clearProgress();
        }
        if (!PRESENT) return;
        try {
            SeedCrackerIntegration.sync();
            SeedCrackerNativeState.sync();
            if (SeedCrackerIntegration.active() && mc.level != null) {
                SeedCrackerChunkBackfill.tick(mc, context + "|" + gen);
                activeTicks++;
                if (activeTicks == 200 && SeedCrackerChunkBackfill.submittedChunks() == 0 && !warnedAboutNoData) {
                    CommandOutput.warning(SeedCrackerChunkBackfill.backfilledCount() > 0
                            ? "SeedCrackerX: backfill is submitting chunks, but the upstream FinderQueue diagnostic hook is inactive. Check mixin warnings in latest.log."
                            : "SeedCrackerX: no chunk submissions after 10 seconds. Check the upstream chunk mixin and current world state; see @seed status.");
                    warnedAboutNoData = true;
                } else if (activeTicks == 300 && SeedCrackerIntegration.baseBits() == 0 && !warnedAboutNoData) {
                    CommandOutput.send("SeedCrackerX: scanning chunks, but no usable structures found yet. Explore shipwrecks, desert pyramids, igloos or other supported structures. @seed status shows scan counters.");
                    warnedAboutNoData = true;
                }
            } else activeTicks = 0;
        } catch (LinkageError | RuntimeException error) {
            DebugLog.error("SeedCrackerX integration disabled by incompatible runtime", error);
            if (!warnedAboutNoData) {
                CommandOutput.error("SeedCrackerX backend failed: " + error.getClass().getSimpleName() + ". Check latest.log.");
                warnedAboutNoData = true;
            }
        }
    }

    public static void propose(String module) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || !mc.isSameThread() || !SeedCrackerConfig.get().offerOnDemand()) return;
        if (!offered.add(SeedSnapshots.server(mc) + "|" + SeedSnapshots.dimension(mc) + "|" + profile() + "|" + module)) return;
        if (SeedSnapshots.currentStored().filter(s -> s.origin() == SeedSnapshot.Origin.CANDIDATE).isPresent()) {
            CommandOutput.warning(module + ": unverified SeedCrackerX candidate exists. Review @seed saved; "
                    + "after independent verification, use @seed set <number> to accept it.");
            return;
        }
        if (SeedSnapshots.worldSeed().isPresent()) {
            CommandOutput.warning(module + ": saved seed exists, but its worldgen profile is " + profile()
                    + "; this module currently simulates 26.3. Do not use it as a 26.3 seed.");
            return;
        }
        if (!PRESENT) {
            CommandOutput.warning(module + ": seed not set; install SeedCrackerX 2.16.2 for optional recovery, or use @seed set <number>.");
            return;
        }
        if (running()) { CommandOutput.send(module + ": SeedCrackerX is already collecting structures. Use @seed status."); return; }
        MessageActionRegistry.Context context = ChatMessageActions.publish(ACTION_TYPE, Map.of("module", module));
        var text = Component.literal(module + " requires a world seed. Recover it with SeedCrackerX? ")
                .append(ChatMessageActions.button(context.token(), ACTION_OWNER + ":start", "[Start]", 0x69D6B7))
                .append(Component.literal("  "))
                .append(ChatMessageActions.button(context.token(), ACTION_OWNER + ":status", "[Status]", 0x89C4FF));
        CommandOutput.send(text, CommandOutput.Tone.INFO, context);
    }

    public static void onRecovered(long seed) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        if (!mc.isSameThread()) { mc.execute(() -> onRecovered(seed)); return; }
        if (mc.level == null || !running()) return;
        boolean provisional = SeedCrackerIntegration.isWorldgenFallback();
        boolean autoSave = SeedCrackerConfig.get().saveRecovered();
        boolean saved = SeedSnapshots.save(seed,
                provisional ? SeedSnapshot.Origin.CANDIDATE : SeedSnapshot.Origin.CRACKED, autoSave);
        String storage = !saved ? "SeedCrackerX: found seed but could not store it. Check latest.log."
                : autoSave ? "SeedCrackerX: result stored for this server/dimension/profile [disk]."
                : "SeedCrackerX: result stored for this session. Use @seed persist to save it to disk.";
        if (provisional) storage += " Model fallback: validate before using in modules (@seed saved).";
        CommandOutput.send(storage, !saved || provisional ? CommandOutput.Tone.WARNING : CommandOutput.Tone.SUCCESS);
    }

    /** Exposes the original SeedCrackerX command objects through Combatant's @seed command. */
    public static boolean executeNative(List<String> args) {
        if (!PRESENT) {
            CommandOutput.warning("SeedCrackerX is not installed; native commands are unavailable.");
            return true;
        }
        if (args.isEmpty()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) {
            CommandOutput.warning("Join a world before using SeedCrackerX commands.");
            return true;
        }
        if (!mc.isSameThread()) {
            CommandOutput.error("SeedCrackerX commands must execute on the client thread.");
            return true;
        }
        return SeedCrackerNativeCommands.execute(args);
    }

    public static List<String> nativeSuggestions(List<String> args, String token) {
        if (!PRESENT) return List.of();
        return SeedCrackerNativeCommands.suggest(args, token);
    }

    public static List<String> nativeCommands() {
        if (!PRESENT) return List.of("cracker", "data", "finder", "render", "version", "gui", "database");
        return SeedCrackerNativeCommands.commandNames();
    }

    public static boolean start() {
        if (!PRESENT) { CommandOutput.warning("SeedCrackerX is not installed. Install version 2.16.2 for Minecraft 26.3."); return false; }
        if (Minecraft.getInstance().level == null) { CommandOutput.warning("Join a world first."); return false; }
        SeedCrackerConfig.get().setEnabled(true);
        try {
            SeedCrackerIntegration.allowProfileRetry();
            SeedCrackerIntegration.sync();
        }
        catch (LinkageError | RuntimeException error) {
            SeedCrackerConfig.get().setEnabled(false);
            DebugLog.error("SeedCrackerX startup failed", error);
            CommandOutput.error("SeedCrackerX version is incompatible; see log.");
            return false;
        }
        clearProgress();
        if (!SeedCrackerIntegration.active()) {
            CommandOutput.warning("SeedCrackerX enabled in settings, but upstream is not running. " + SeedCrackerIntegration.status());
            return false;
        }
        CommandOutput.success("SeedCrackerX started. Scanning already loaded chunks and future chunk packets; use @seed status for scan counters.");
        if (ignoreServerHash()) CommandOutput.send("SeedCrackerX hash guard: server hash excluded. Collect independent Overworld structures AND dungeons to lift the 48-bit structure seed. Modified salts/Paper feature-seeds are not automatically reversed.");
        return true;
    }
    public static void stop() {
        SeedCrackerConfig.get().setEnabled(false);
        if (PRESENT) SeedCrackerIntegration.stop();
        clearProgress();
        CommandOutput.success("SeedCrackerX recovery paused.");
    }
    public static void reset() {
        if (!PRESENT) { CommandOutput.warning("SeedCrackerX not installed."); return; }
        SeedCrackerIntegration.reset();
        clearProgress();
        CommandOutput.success("SeedCrackerX observations and pending candidates cleared.");
    }
    public static String status() {
        var known = SeedSnapshots.currentStored();
        return "SeedCrackerX: " + (PRESENT ? (running() ? "running" : "stopped") : "not installed")
                + ", context=" + SeedCrackerConfig.get().contextDescription() + ", profile=" + profile()
                + ", seed=" + known.map(s -> Long.toString(s.worldSeed()) + " [" + s.origin()
                        + ", " + (SeedSnapshots.currentIsPersisted() ? "disk" : "session") + "]").orElse("none")
                + (PRESENT ? ", " + SeedCrackerIntegration.status() + ", " + SeedCrackerChunkBackfill.diagnostics() : "");
    }
}

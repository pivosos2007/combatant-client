/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.misc;

import combatant.client.config.values.BooleanValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.PacketEvent;
import combatant.client.events.impl.WorldChangeEvent;
import combatant.client.features.command.CommandOutput;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.Notifier;
import combatant.client.features.module.modules.misc.serverutil.AntiCheatFingerprint;
import combatant.client.features.module.modules.misc.serverutil.AntiCheatFingerprint.Confidence;
import combatant.client.features.module.modules.misc.serverutil.AntiCheatFingerprint.Kind;
import combatant.client.features.module.modules.misc.serverutil.AntiCheatFingerprint.Verdict;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.protocol.common.ClientboundPingPacket;

import java.util.Locale;

/**
 * Guesses what kind of anti-cheat the server runs from the pattern of its ping ("transaction") packets and
 * reports it once per server, with a confidence. It is a heuristic over packet statistics, not a product
 * identification.
 */
@ModuleInfo(
        id = "anticheatdetect",
        displayName = "AntiCheatDetect",
        aliases = {"ACDetect", "DetectAntiCheat"},
        category = ModuleCategory.MISC,
        subcategory = ModuleSubcategory.UTILITY,
        description = "module.anticheatdetect.description")
public final class AntiCheatDetect extends Module {

    private static final int EVALUATE_INTERVAL = 20;

    private final Minecraft mc = Minecraft.getInstance();

    private final BooleanValue chatReport = bool("antiCheatDetectChat", "chat_report", true);
    private final BooleanValue reportSilence = bool("antiCheatDetectSilence", "report_silence", false);

    private final AntiCheatFingerprint fingerprint = new AntiCheatFingerprint();
    private Kind reportedKind = Kind.UNKNOWN;
    private Confidence reportedConfidence = Confidence.NONE;
    private int ticks;

    @Override
    public void onEnable() {
        resetState();
    }

    @Override
    public void onDisable() {
        resetState();
    }

    @EventHandler
    private void onReceive(PacketEvent.Receive event) {
        if (!isEnabled() || !(event.getPacket() instanceof ClientboundPingPacket ping)) return;
        synchronized (fingerprint) {
            fingerprint.observe(ping.getId(), System.currentTimeMillis());
        }
    }

    @EventHandler
    private void onWorldChange(WorldChangeEvent event) {
        if (event.reason() == WorldChangeEvent.Reason.DISCONNECT) resetState();
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;
        if (mc.player == null || mc.level == null) return;
        long now = System.currentTimeMillis();
        synchronized (fingerprint) {
            fingerprint.begin(now);
        }
        if (++ticks % EVALUATE_INTERVAL != 0) return;

        Verdict verdict;
        synchronized (fingerprint) {
            verdict = fingerprint.verdict(now);
        }
        if (!shouldReport(verdict)) return;

        reportedKind = verdict.kind();
        reportedConfidence = verdict.confidence();
        String text = I18n.get("notification.anticheatdetect.result",
                I18n.get("anticheatdetect.kind." + verdict.kind().name().toLowerCase(Locale.ROOT)),
                I18n.get("anticheatdetect.confidence." + verdict.confidence().name().toLowerCase(Locale.ROOT)),
                verdict.samples());
        Notifier.info(text);
        if (chatReport.get()) CommandOutput.send(text);
    }

    /** Report a new family, or the same family at higher confidence; ignore UNKNOWN and (unless enabled) silence. */
    private boolean shouldReport(Verdict verdict) {
        Kind kind = verdict.kind();
        if (kind == Kind.UNKNOWN || verdict.confidence() == Confidence.NONE) return false;
        if (kind == Kind.NO_PING_TRAFFIC && !reportSilence.get()) return false;
        if (kind != reportedKind) return true;
        return verdict.confidence().ordinal() > reportedConfidence.ordinal();
    }

    private void resetState() {
        synchronized (fingerprint) {
            fingerprint.reset();
        }
        reportedKind = Kind.UNKNOWN;
        reportedConfidence = Confidence.NONE;
        ticks = 0;
    }
}
